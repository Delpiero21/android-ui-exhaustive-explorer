package com.exhaustive.explorer.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.exhaustive.explorer.engine.ExplorerEngine

/**
 * AccessibilityService 진입점 — Phase 1 완전판.
 *
 * 모드:
 * - **Passive (default)**: a11y 이벤트 수신 → ExplorerEngine.onEvent — SRS 의 SQE4 (탐색 추적)
 * - **Autonomous**: MainActivity / adb broadcast 로 trigger — SRS 의 AI Trial / IBS
 *
 * Autonomous 시작 방법:
 * ```bash
 * adb shell am broadcast \
 *   -a com.exhaustive.explorer.START_AUTONOMOUS \
 *   --es target_package com.samsung.android.app.notes \
 *   --el budget_ms 60000
 *
 * adb shell am broadcast -a com.exhaustive.explorer.STOP_AUTONOMOUS
 * ```
 *
 * 디버깅:
 * ```
 * adb logcat -s ExplorerA11y:V ExplorerEngine:V MultiWindowCollector:W \
 *            DangerousActionGuard:W DialogDismisser:I
 * ```
 */
class ExplorerAccessibilityService : AccessibilityService() {

    private val engine = ExplorerEngine()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected. Phase 1 — passive + autonomous available.")
        INSTANCE = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // autonomous mode 면 이벤트는 무시 (engine 의 worker thread 가 자체 collect)
        if (engine.isAutonomousRunning) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> engine.onEvent(this)
            else -> Unit
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted.")
    }

    override fun onDestroy() {
        Log.i(
            TAG,
            "Service destroyed. nodes=${engine.stateGraph.nodeCount} edges=${engine.stateGraph.edgeCount}",
        )
        engine.shutdown()
        INSTANCE = null
        super.onDestroy()
    }

    /** MainActivity / 외부 trigger 가 호출. */
    fun startAutonomous(targetPackage: String?, budgetMs: Long = 60_000L) {
        engine.startAutonomous(this, targetPackage, budgetMs)
    }

    fun stopAutonomous() {
        engine.stopAutonomous()
    }

    fun snapshot(): ExplorerEngine.EngineSnapshot = engine.snapshot()
    fun reset() = engine.reset()

    companion object {
        private const val TAG = "ExplorerA11y"

        /**
         * MainActivity 가 service 인스턴스에 접근하기 위한 임시 핸들.
         * 정식 IPC (LocalService / Messenger) 는 Phase 1 후반에 도입.
         */
        @Volatile
        var INSTANCE: ExplorerAccessibilityService? = null
            private set
    }
}
