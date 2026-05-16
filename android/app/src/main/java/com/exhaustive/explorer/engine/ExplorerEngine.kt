package com.exhaustive.explorer.engine

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import com.exhaustive.explorer.core.ScreenFingerprint
import com.exhaustive.explorer.core.ScreenInfo
import com.exhaustive.explorer.core.ScreenNodeSummary
import com.exhaustive.explorer.core.StateGraph
import com.exhaustive.explorer.tier1_a11y.MultiWindowCollector

/**
 * Phase 1 의 통합 DFS 루프.
 *
 * 본 클래스는 [AccessibilityService] 의 onAccessibilityEvent / 별도 worker thread 양쪽에서 호출된다.
 *
 * **현재 (Phase 1-4 skeleton)**:
 * - 화면 collect → fingerprint → StateGraph 등록까지만 자동
 * - 액션 수행 (`GestureDispatcher`, `performAction`) 은 Phase 1 후반 모듈 작성 후 부착
 * - 백트래킹 / replay 는 Phase 1 후반에 [PathReplayer] 와 결합
 *
 * 따라서 현 단계에서 본 엔진을 띄우면:
 * - 사용자가 단말을 탭/스와이프 할 때마다 fingerprint 가 갱신되고 StateGraph 가 누적된다.
 * - "탐색 추적" (passive observation) 모드만 동작 — SRS 의 SQE4 시나리오에 해당.
 *
 * 자율 탐색 (AI Trial / IBS) 은 Phase 1 후반의 [ActionExecutor] / [PathReplayer] 부착 후.
 */
class ExplorerEngine(
    private val collector: MultiWindowCollector = MultiWindowCollector(),
    val stateGraph: StateGraph = StateGraph(),
) {
    private var lastFp: ScreenFingerprint.Composite? = null
    private var lastTickAt: Long = 0L
    private var debounceMs: Long = 150L

    /**
     * a11y 이벤트 발생 시 호출. 변화 없을 때 과도한 collect 호출 방지 위해 [debounceMs] 적용.
     *
     * @return 본 tick 에서 새 노드 발견 시 [TickResult.NewScreen], 같은 화면 재방문이면 [TickResult.SameScreen],
     *         debounce 되어 skip 됐으면 [TickResult.Skipped].
     */
    fun onEvent(service: AccessibilityService): TickResult {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickAt < debounceMs) return TickResult.Skipped
        lastTickAt = now

        val screen = collector.collect(service)
        if (!screen.hasActiveWindow) {
            Log.d(TAG, "no active window — skipping")
            return TickResult.Skipped
        }

        val fp = ScreenFingerprint.composite(screen)
        val (node, isNew) = stateGraph.upsert(fp, screen)
        lastFp = fp

        if (isNew) {
            Log.i(
                TAG,
                "[NEW] fp=${fp.strict.take(8)} pkg=${node.foregroundPackage} act=${node.foregroundActivity} " +
                    "candidates=${node.candidateCount} windows=${node.windowCount}",
            )
        } else {
            Log.v(TAG, "[REVISIT] fp=${fp.strict.take(8)} visit#${node.visitCount}")
        }
        return if (isNew) TickResult.NewScreen(fp, screen) else TickResult.SameScreen(fp)
    }

    /** 외부 (UI / FastAPI) 에서 상태 조회. */
    fun snapshot(): EngineSnapshot = EngineSnapshot(
        nodeCount = stateGraph.nodeCount,
        edgeCount = stateGraph.edgeCount,
        lastFp = lastFp,
        nodes = stateGraph.snapshot(),
    )

    /** 새 run 시작 시 초기화. */
    fun reset() {
        stateGraph.clear()
        lastFp = null
        lastTickAt = 0L
        Log.i(TAG, "reset")
    }

    /** 화면 변화 없는 빈 이벤트 폭주 방지용 debounce. */
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
    )

    companion object {
        private const val TAG = "ExplorerEngine"
    }
}
