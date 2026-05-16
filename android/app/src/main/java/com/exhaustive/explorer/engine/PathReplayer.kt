package com.exhaustive.explorer.engine

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.exhaustive.explorer.core.Candidate
import com.exhaustive.explorer.input.GestureDispatcher
import kotlinx.coroutines.delay

/**
 * 백트래킹 + 경로 재생 (Phase 1-5).
 *
 * 동작 우선순위:
 * 1. **BACK N회** — 가벼운 backtrack. 가장 빠름.
 * 2. **Home → 타겟 앱 재진입 → action 시퀀스 replay** — BACK 으로 안 돌아오거나 외부 앱으로 이탈했을 때.
 * 3. **앱 강제 종료 → 재시작 + replay** — replay 도 실패할 때.
 *
 * Phase 1 단순 구현:
 * - replay 는 좌표 기반. resource-id 우선 매칭은 Phase 2 (NodeInfoCache 도입 후).
 * - 사전조건 / 인증 시퀀스 우회는 Phase 3.
 */
class PathReplayer(
    private val service: AccessibilityService,
    private val gesture: GestureDispatcher,
) {

    /**
     * BACK 키를 [count] 회 누른다. 매 누름 후 [settleMs] 대기.
     */
    suspend fun pressBack(count: Int = 1, settleMs: Long = 500L): Boolean {
        repeat(count) {
            val ok = gesture.pressBack()
            if (!ok) {
                Log.w(TAG, "pressBack returned false (i=$it)")
                return false
            }
            delay(settleMs)
        }
        return true
    }

    /** Home 으로 이탈. */
    suspend fun goHome(settleMs: Long = 800L): Boolean {
        val ok = gesture.pressHome()
        delay(settleMs)
        return ok
    }

    /**
     * 타겟 앱 재시작. launcher intent 로 실행.
     *
     * @param packageName 예: com.samsung.android.app.notes
     */
    fun relaunchApp(packageName: String): Boolean {
        val pm = service.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "no launch intent for $packageName — package not installed?")
            return false
        }
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        Log.i(TAG, "relaunchApp $packageName → intent=$intent component=${intent.component}")

        // Try 1: service.startActivity — 일반 경로
        try {
            service.startActivity(intent)
            Log.i(TAG, "relaunchApp: service.startActivity OK")
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "relaunchApp: service.startActivity 실패 — ${t.javaClass.simpleName} ${t.message}")
        }

        // Try 2: applicationContext — 가끔 context 차이로 동작
        try {
            service.applicationContext.startActivity(intent)
            Log.i(TAG, "relaunchApp: applicationContext.startActivity OK")
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "relaunchApp: applicationContext 실패 — ${t.javaClass.simpleName} ${t.message}")
        }

        // Try 3: PendingIntent — BAL 제한 다른 룰
        try {
            val pi = PendingIntent.getActivity(
                service.applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            pi.send()
            Log.i(TAG, "relaunchApp: PendingIntent.send OK")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "relaunchApp: PendingIntent 도 실패 — ${t.javaClass.simpleName} ${t.message}", t)
        }

        return false
    }

    /**
     * 액션 시퀀스를 좌표 기반으로 재생.
     *
     * @param actions 순서대로 수행할 candidate 들. Phase 1 은 nodeRef 무시하고 좌표만 사용.
     * @param betweenMs 액션 사이 대기.
     * @return 모두 발송 성공 여부 (실제 효과 검증은 호출자 책임).
     */
    suspend fun replay(actions: List<Candidate>, betweenMs: Long = 800L): Boolean {
        Log.i(TAG, "replay ${actions.size} actions")
        for ((i, a) in actions.withIndex()) {
            // Phase 1: 항상 CLICK 으로 단순화
            val ok = gesture.click(a)
            if (!ok) {
                Log.w(TAG, "replay step $i failed: ${a.shortLabel()}")
                return false
            }
            delay(betweenMs)
        }
        return true
    }

    /**
     * 통합 recovery — BACK → 재진입 → replay 의 ladder.
     */
    suspend fun recover(
        targetPackage: String,
        path: List<Candidate>,
    ): RecoveryResult {
        // 1) BACK 1번
        if (pressBack(1)) {
            // 호출자가 fp 비교해서 OK 면 종료
            return RecoveryResult(method = Method.SINGLE_BACK)
        }
        // 2) Home → relaunch → replay
        goHome()
        if (relaunchApp(targetPackage)) {
            delay(2000L)  // 앱 로드 대기
            val replayed = replay(path)
            return RecoveryResult(
                method = if (replayed) Method.HOME_RELAUNCH_REPLAY else Method.HOME_RELAUNCH_NO_REPLAY,
                replayedSteps = if (replayed) path.size else 0,
            )
        }
        return RecoveryResult(method = Method.FAILED)
    }

    data class RecoveryResult(
        val method: Method,
        val replayedSteps: Int = 0,
    )

    enum class Method {
        SINGLE_BACK,
        HOME_RELAUNCH_REPLAY,
        HOME_RELAUNCH_NO_REPLAY,
        FAILED,
    }

    companion object {
        private const val TAG = "PathReplayer"
    }
}
