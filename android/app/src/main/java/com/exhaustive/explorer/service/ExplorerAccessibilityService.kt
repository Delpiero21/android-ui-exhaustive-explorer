package com.exhaustive.explorer.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.exhaustive.explorer.engine.ExplorerEngine

/**
 * AccessibilityService 진입점.
 *
 * Phase 0 → Phase 1 갱신:
 * - Phase 0: 이벤트 로깅만
 * - **Phase 1 (현재)**: [ExplorerEngine] 에 위임. 화면 변화마다 fingerprint 계산 + StateGraph 누적.
 *
 * 현 시점 한계:
 * - **자율 탐색 X** — passive observation 만 동작 (사용자가 단말을 만져야 변화 감지)
 * - 액션 수행 / 백트래킹 / replay 는 Phase 1 후반 모듈 부착 후
 *
 * 즉 현재는 "사용자가 검증하는 동안 우리가 옆에서 기록" 하는 모드.
 * SRS 의 SQE4 (탐색 추적) 시나리오에 해당.
 *
 * 디버깅:
 * ```
 * adb logcat -s ExplorerA11y:V ExplorerEngine:V MultiWindowCollector:W
 * ```
 *
 * 외부 (server) 가 엔진 상태에 접근할 필요가 생기면 (Phase 1 후반):
 *  - LocalBroadcastManager 또는 IBinder Service 패턴으로 노출
 *  - 또는 file (events.jsonl) 기록 후 adb pull
 */
class ExplorerAccessibilityService : AccessibilityService() {

    private val engine = ExplorerEngine()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected. Phase 1 — passive observation mode (engine attached).")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // a11y 이벤트는 frequency 가 높아 (스크롤 시 초당 수십 개) 빠짐없이 처리하면 부담.
        // ExplorerEngine 내부에서 debounce 처리.
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                val result = engine.onEvent(this)
                if (result is ExplorerEngine.TickResult.NewScreen) {
                    // Phase 1 후반: 여기서 ActionExecutor 가 다음 액션 수행
                    // Phase 1 현재: 로깅만
                }
            }
            else -> {
                // 그 외 이벤트 (scroll, focus 등) 는 무시
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted.")
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed. node=${engine.stateGraph.nodeCount} edge=${engine.stateGraph.edgeCount}")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ExplorerA11y"
    }
}
