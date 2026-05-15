package com.exhaustive.explorer.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService 진입점 (Phase 0 스캐폴드).
 *
 * 현 시점에는 이벤트 수신 로깅만 수행한다. 실제 탐색 로직은 Phase 1 에서
 * [com.exhaustive.explorer.engine.ExplorerEngine] 가 채워질 때 부착된다.
 *
 * 가장 먼저 만들 모듈은 [com.exhaustive.explorer.tier1_a11y.MultiWindowCollector]
 * — `getWindows()` 를 반복 호출해 popup / IME window 까지 모두 수집하는 것.
 * 그 근거는 docs/SAMSUNG_NOTES_HARD_CASES.md 의 Case 1 / 6 / 10 실측.
 */
class ExplorerAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected. Phase 0 — no exploration logic attached yet.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO Phase 1: dispatch to ExplorerEngine.onEvent(event)
        if (event != null) {
            Log.v(
                TAG,
                "event type=${event.eventType} package=${event.packageName} class=${event.className}",
            )
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted.")
    }

    companion object {
        private const val TAG = "ExplorerA11y"
    }
}
