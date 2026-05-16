package com.exhaustive.explorer.engine

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import com.exhaustive.explorer.core.Candidate
import com.exhaustive.explorer.core.RunRecorder
import com.exhaustive.explorer.core.RunUploader
import com.exhaustive.explorer.core.ScreenCapture
import com.exhaustive.explorer.core.ScreenFingerprint
import com.exhaustive.explorer.core.ScreenInfo
import com.exhaustive.explorer.core.ScreenNodeSummary
import com.exhaustive.explorer.core.StateGraph
import com.exhaustive.explorer.guard.DangerousActionGuard
import com.exhaustive.explorer.input.GestureDispatcher
import com.exhaustive.explorer.tier1_a11y.MultiWindowCollector
import com.exhaustive.explorer.tier4_probe.DifferentialProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Phase 1 통합 엔진 — passive + autonomous 두 모드.
 *
 * - **Passive mode** (`onEvent` 호출만): SRS 의 SQE4 시나리오 — 사용자가 만지는 동안 추적.
 * - **Autonomous mode** (`startAutonomous` 호출): SRS 의 AI Trial / IBS 시나리오 — 자율 DFS 탐색.
 *
 * 자율 모드 DFS 루프:
 * ```
 * loop until budget exhausted or coverage satisfied:
 *   1. collect screen + fingerprint
 *   2. dangerous guard 검사 (위험 화면이면 즉시 evacuate)
 *   3. dialog dismisser 처리 (popup 자동 닫기)
 *   4. upsert state graph
 *   5. pop next action from frontier
 *   6. (선택) Differential Probe before-capture
 *   7. perform action (GestureDispatcher)
 *   8. settle wait
 *   9. (선택) Differential Probe after-capture + classify
 *  10. addEdge
 * ```
 */
class ExplorerEngine(
    private val collector: MultiWindowCollector = MultiWindowCollector(),
    val stateGraph: StateGraph = StateGraph(),
    private val guard: DangerousActionGuard = DangerousActionGuard(),
    private val screenCapture: ScreenCapture = ScreenCapture(),
    private val probe: DifferentialProbe = DifferentialProbe(),
) {
    private var lastFp: ScreenFingerprint.Composite? = null
    private var lastTickAt: Long = 0L
    private var debounceMs: Long = 150L

    // autonomous mode state
    private var autonomousJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // run recorder (autonomous mode 동안만 active)
    private var recorder: RunRecorder? = null
    val currentRunId: String? get() = recorder?.id

    // run 종료 시 PC server 로 자동 업로드 (127.0.0.1:8000 via adb reverse)
    private val uploader = RunUploader()

    // ────────── passive mode ──────────

    /**
     * a11y 이벤트 발생 시 호출. passive 모드.
     */
    fun onEvent(service: AccessibilityService): TickResult {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickAt < debounceMs) return TickResult.Skipped
        lastTickAt = now

        val screen = collector.collect(service)
        if (!screen.hasActiveWindow) {
            return TickResult.Skipped
        }
        val fp = ScreenFingerprint.composite(screen)
        val (node, isNew) = stateGraph.upsert(fp, screen)
        lastFp = fp

        if (isNew) {
            Log.i(
                TAG,
                "[NEW] fp=${fp.strict.take(8)} pkg=${node.foregroundPackage} " +
                    "candidates=${node.candidateCount} windows=${node.windowCount}",
            )
        }
        return if (isNew) TickResult.NewScreen(fp, screen) else TickResult.SameScreen(fp)
    }

    // ────────── autonomous mode ──────────

    /**
     * 자율 탐색 시작.
     *
     * @param service       live AccessibilityService
     * @param targetPackage 탐색 대상 앱 package. null 이면 어떤 앱이든 OK.
     * @param budgetMs      시간 예산 (ms). 기본 60s — SRS 의 앱당 1분.
     * @param maxScreens    발견 화면 상한 — 무한루프 방지.
     */
    fun startAutonomous(
        service: AccessibilityService,
        targetPackage: String? = null,
        budgetMs: Long = 300_000L,   // 기본 5 분. 0 이면 무제한 (탐색 종료 조건만 따름)
        maxScreens: Int = 0,         // 0 이면 무제한 (모든 frontier 소진 시 종료)
    ) {
        if (autonomousJob?.isActive == true) {
            Log.w(TAG, "autonomous already running")
            return
        }
        val gesture = GestureDispatcher(service)
        val replayer = PathReplayer(service, gesture)

        // RunRecorder 생성 — /sdcard/Android/data/com.exhaustive.explorer.debug/files/runs/{id}/
        recorder = RunRecorder(service).also { rec ->
            rec.recordRunStart(targetPackage, budgetMs)
            Log.i(TAG, "RunRecorder created: ${rec.outputDir.absolutePath}")
        }

        autonomousJob = scope.launch {
            val unlimitedBudget = budgetMs <= 0
            val unlimitedScreens = maxScreens <= 0
            val deadline = if (unlimitedBudget) Long.MAX_VALUE else SystemClock.uptimeMillis() + budgetMs
            Log.i(
                TAG,
                "autonomous start — pkg=$targetPackage budget=${if (unlimitedBudget) "∞" else "${budgetMs}ms"} " +
                    "maxScreens=${if (unlimitedScreens) "∞" else "$maxScreens"}"
            )
            var screensExplored = 0
            var actionsExecuted = 0
            var noProgressCount = 0
            var fullyExploredChecks = 0

            // ⭐ 무한 반복 가지치기:
            // 같은 loose fp (구조만 동일) 가 N 회 연속 → 발산 의심 → 강제 BACK
            // 키보드 50글자 toast / cursor blinking 등으로 strict fp 미세 변동해도 loose 는 같음
            var sameLooseCount = 0
            var prevLooseFp = ""

            try {
                while (
                    isActive &&
                    SystemClock.uptimeMillis() < deadline &&
                    (unlimitedScreens || screensExplored < maxScreens)
                ) {
                    // 1. collect + fingerprint
                    val screen = collector.collect(service)
                    if (!screen.hasActiveWindow) {
                        delay(300L)
                        continue
                    }

                    // 2. target package 이탈 감지
                    if (targetPackage != null && screen.foregroundPackage != targetPackage) {
                        val fg = screen.foregroundPackage ?: ""
                        val isLauncher = fg.contains("launcher") || fg == "com.sec.android.app.launcher"
                        val isOurApp = fg.startsWith("com.exhaustive.explorer")

                        if (isLauncher || isOurApp) {
                            // 진짜 빠져나옴 (launcher 또는 우리 앱) → relaunch 필요
                            Log.w(TAG, "out-of-target [HARD]: $fg → relaunch $targetPackage")
                            replayer.relaunchApp(targetPackage)
                            delay(1500L)
                        } else {
                            // 시스템 dialog / 다른 앱 → BACK 으로 돌아가기 우선
                            Log.w(TAG, "out-of-target [SOFT]: $fg → try BACK x3")
                            var recovered = false
                            for (i in 1..3) {
                                replayer.pressBack(1)
                                delay(500L)
                                val again = collector.collect(service)
                                if (again.foregroundPackage == targetPackage) {
                                    Log.i(TAG, "recovered to target after BACK x$i")
                                    recovered = true
                                    break
                                }
                            }
                            if (!recovered) {
                                Log.w(TAG, "BACK 실패 → relaunch fallback")
                                replayer.relaunchApp(targetPackage)
                                delay(1500L)
                            }
                        }
                        continue
                    }

                    // 3. 위험 화면 evacuate
                    val texts = screen.candidates.flatMap { listOfNotNull(it.text, it.contentDesc) }
                    val evacuate = guard.shouldEvacuateScreen(texts)
                    if (evacuate.isEvacuate) {
                        Log.w(TAG, "EVACUATE: ${evacuate.reason} — pressing HOME")
                        recorder?.recordEvacuate(evacuate.reason ?: "?")
                        replayer.goHome()
                        if (targetPackage != null) replayer.relaunchApp(targetPackage)
                        delay(2000L)
                        continue
                    }

                    // 4. upsert
                    val fp = ScreenFingerprint.composite(screen)
                    val (node, isNew) = stateGraph.upsert(fp, screen)
                    lastFp = fp
                    if (isNew) {
                        screensExplored++
                        Log.i(
                            TAG,
                            "[NEW#$screensExplored] fp=${fp.strict.take(8)} candidates=${node.candidateCount}",
                        )
                        recorder?.recordNewScreen(fp, screen)
                        // 새 화면마다 스크린샷 저장 (최대 MAX_SCREENSHOTS 까지 — sdcard 압박 방지)
                        if (screensExplored <= MAX_SCREENSHOTS) {
                            val bitmap = screenCapture.capture(service)
                            recorder?.saveScreenshot(fp.strict, bitmap)
                            bitmap?.recycle()
                        }
                    }

                    // 4.5. ⭐ 무한 반복 가지치기 (loose fp 기준)
                    //   같은 loose fp 가 N 회 연속 = 같은 구조의 화면에서 미세 변동만 → 발산 의심
                    //   예: SIP 키보드에서 키 누르며 text 변경 (strict fp 매번 다름, loose 동일)
                    //   → 현재 fp 의 남은 액션 가지치기 + BACK
                    if (fp.loose == prevLooseFp) {
                        sameLooseCount++
                    } else {
                        sameLooseCount = 0
                        prevLooseFp = fp.loose
                    }
                    if (sameLooseCount > MAX_SAME_LOOSE_REPEATS) {
                        val remaining = node.pendingActions.size
                        Log.w(
                            TAG,
                            "STUCK (loose): 같은 구조 ${sameLooseCount}회 연속 (loose=${fp.loose.take(8)}) " +
                                "— 남은 액션 ${remaining}개 가지치기 + BACK",
                        )
                        while (stateGraph.popNextAction(fp.strict) != null) {}
                        replayer.pressBack(1)
                        delay(700L)
                        sameLooseCount = 0
                        continue
                    }

                    // 5. frontier 확인
                    val nextAction = stateGraph.popNextAction(fp.strict)
                    if (nextAction == null) {
                        // 모든 액션 소진. 전체 그래프에서도 frontier 0 이면 완전탐색 종료
                        val anyFrontier = stateGraph.snapshot().any { it.pendingCount > 0 }
                        if (!anyFrontier) {
                            fullyExploredChecks++
                            if (fullyExploredChecks >= FULLY_EXPLORED_CONFIRM) {
                                Log.i(
                                    TAG,
                                    "FULLY EXPLORED — 모든 노드의 frontier=0 ($fullyExploredChecks 회 연속 확인). 종료.",
                                )
                                break
                            } else {
                                Log.d(TAG, "fully-explored check $fullyExploredChecks/$FULLY_EXPLORED_CONFIRM")
                            }
                        } else {
                            fullyExploredChecks = 0
                        }
                        // 모든 액션 소진 — 신중한 backtrack
                        // 너무 깊이까지 BACK 하지 않게 — Notes root 에서 BACK 하면 launcher 로 빠짐
                        Log.d(TAG, "frontier=0 for ${fp.strict.take(8)} — pressing BACK (cautious)")
                        replayer.pressBack(1)
                        delay(500L)
                        // BACK 후 launcher 로 빠졌는지 즉시 검사 → 빠졌으면 다음 iteration 의 §2 가 relaunch
                        noProgressCount++
                        if (noProgressCount > MAX_NO_PROGRESS) {
                            Log.w(TAG, "no progress $noProgressCount → home + relaunch")
                            replayer.goHome()
                            if (targetPackage != null) replayer.relaunchApp(targetPackage)
                            noProgressCount = 0
                            delay(2000L)
                        }
                        continue
                    }

                    // 6. 위험 액션 차단
                    if (!guard.isSafe(nextAction)) {
                        recorder?.recordBlock(
                            reason = "dangerous_keyword",
                            label = nextAction.shortLabel(),
                        )
                        continue  // 다음 액션으로
                    }

                    // 7. (선택) Differential probe — Tier 1 후보는 보통 신뢰. probe 는 grid/cv 후보용.
                    //    Phase 1 에선 모든 후보를 probe 없이 수행. Phase 2 에서 활성화.

                    // 8. action 수행
                    actionsExecuted++
                    recorder?.recordAction(nextAction, fp.strict)
                    val ok = gesture.perform(nextAction, nextAction.actions.first())
                    if (!ok) {
                        Log.d(TAG, "action failed: ${nextAction.shortLabel()}")
                        continue
                    }

                    // 9. settle + edge 등록
                    delay(700L)
                    val newScreen = collector.collect(service)
                    val newFp = ScreenFingerprint.composite(newScreen)
                    val changed = newFp.strict != fp.strict
                    stateGraph.addEdge(fp.strict, nextAction, newFp.strict)
                    recorder?.recordEdge(fp.strict, newFp.strict, nextAction, changed)

                    if (changed) noProgressCount = 0
                }
            } finally {
                Log.i(
                    TAG,
                    "autonomous end — screens=$screensExplored actions=$actionsExecuted " +
                        "nodes=${stateGraph.nodeCount} edges=${stateGraph.edgeCount}",
                )
                val rec = recorder
                rec?.recordRunEnd()
                recorder = null

                // ⭐ PC server 로 자동 업로드 (127.0.0.1:8000 via adb reverse)
                if (rec != null) {
                    Log.i(TAG, "auto-uploading run to PC server...")
                    val ok = uploader.uploadRun(rec.outputDir)
                    if (ok) {
                        Log.i(TAG, "auto-upload SUCCESS — Dashboard Runs 탭에 자동 노출")
                    } else {
                        Log.w(TAG, "auto-upload FAILED — server 안 떴거나 adb reverse 미설정. " +
                            "수동 회수: .\\scripts\\pull_runs.ps1")
                    }
                }
            }
        }
    }

    /** 자율 탐색 중단. */
    fun stopAutonomous() {
        autonomousJob?.cancel()
        autonomousJob = null
        Log.i(TAG, "autonomous stop requested")
    }

    val isAutonomousRunning: Boolean
        get() = autonomousJob?.isActive == true

    // ────────── 기타 ──────────

    fun snapshot(): EngineSnapshot = EngineSnapshot(
        nodeCount = stateGraph.nodeCount,
        edgeCount = stateGraph.edgeCount,
        lastFp = lastFp,
        nodes = stateGraph.snapshot(),
        autonomousRunning = isAutonomousRunning,
    )

    fun reset() {
        stopAutonomous()
        stateGraph.clear()
        lastFp = null
        lastTickAt = 0L
        Log.i(TAG, "reset")
    }

    fun shutdown() {
        stopAutonomous()
        scope.cancel()
        screenCapture.shutdown()
    }

    fun setDebounceMs(ms: Long) {
        debounceMs = ms.coerceIn(0, 2000)
    }

    sealed class TickResult {
        object Skipped : TickResult()
        data class SameScreen(val fp: ScreenFingerprint.Composite) : TickResult()
        data class NewScreen(val fp: ScreenFingerprint.Composite, val screen: ScreenInfo) : TickResult()
    }

    data class EngineSnapshot(
        val nodeCount: Int,
        val edgeCount: Int,
        val lastFp: ScreenFingerprint.Composite?,
        val nodes: List<ScreenNodeSummary>,
        val autonomousRunning: Boolean,
    )

    companion object {
        private const val TAG = "ExplorerEngine"
        private const val MAX_NO_PROGRESS = 5

        /** 한 run 당 저장할 최대 스크린샷 수. sdcard 압박 + 회수 시간 방지. */
        private const val MAX_SCREENSHOTS = 50

        /**
         * "전체 frontier=0" 을 N 회 연속 확인하면 완전탐색 종료.
         * 일시적 frontier 비움 (BACK 직후 등) 에 속지 않기 위한 안전 마진.
         */
        private const val FULLY_EXPLORED_CONFIRM = 3

        /**
         * 같은 loose fp (구조만 동일) 가 연속 N 회 → 발산 의심 → 가지치기.
         *
         * 키보드 시나리오:
         *   text 변경마다 strict fp 다르지만 (cursor / toast / counter 변동) loose 는 같음
         *   = 의미 있는 새 화면 발견 없이 같은 구조 반복 = 발산
         *
         * 25 회 정도면 키보드 키 약 1/3 시도 후 자동 탈출 — 충분히 sampling 도 하면서 발산도 방지.
         */
        private const val MAX_SAME_LOOSE_REPEATS = 25
    }
}
