package com.exhaustive.explorer.tier1_a11y

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import com.exhaustive.explorer.core.Candidate
import com.exhaustive.explorer.core.ScreenInfo
import com.exhaustive.explorer.core.WindowInfo

/**
 * Phase 1 의 핵심 모듈 — `uiautomator dump` 의존을 제거하고
 * 모든 활성 window 의 a11y tree 를 통합한다.
 *
 * 근거: docs/SAMSUNG_NOTES_HARD_CASES.md 의 실측 5 케이스.
 * `rootInActiveWindow` 만 사용하면 어떤 popup 은 잡히고 어떤 popup 은 놓치는지 예측 불가능
 * (Case 6 ↔ 10 정반대 결과).
 *
 * 본 클래스는 [AccessibilityService.getWindows] 를 사용해 main + popup + IME 모두를 한 번에 수집한다.
 *
 * 비교:
 * | source              | windows | 액션 | 속도   |
 * |---------------------|---------|------|--------|
 * | uiautomator dump    | 1       | ❌    | 1~3s   |
 * | 본 Collector        | 모두    | ✅    | ~50ms  |
 *
 * 사용:
 * ```kotlin
 * class ExplorerAccessibilityService : AccessibilityService() {
 *     private val collector = MultiWindowCollector()
 *
 *     override fun onAccessibilityEvent(event: AccessibilityEvent) {
 *         val screen = collector.collect(this)
 *         // screen.candidates 가 모든 window 의 통합 후보
 *     }
 * }
 * ```
 */
class MultiWindowCollector {

    /**
     * 한 시점의 단말 화면을 수집한다.
     *
     * @param service        live AccessibilityService 인스턴스
     * @return 통합 [ScreenInfo]. `getWindows()` 가 비어있으면 빈 candidates 의 ScreenInfo 반환.
     */
    fun collect(service: AccessibilityService): ScreenInfo {
        val t0 = SystemClock.uptimeMillis()
        val windows = service.windows ?: emptyList()
        val screenBounds = computeScreenBounds(windows)

        val windowInfos = mutableListOf<WindowInfo>()
        val allCandidates = mutableListOf<Candidate>()
        var imeVisible = false
        var foregroundPackage: String? = null
        var foregroundActivityHint: String? = null

        for (win in windows) {
            val root = win.root
            val bounds = Rect().also { win.getBoundsInScreen(it) }
            val type = win.type

            val candidates = NodeTraversal.walk(root, win.id)
            allCandidates += candidates

            windowInfos += WindowInfo(
                id = win.id,
                type = type,
                layer = win.layer,
                bounds = bounds,
                isActive = win.isActive,
                isFocused = win.isFocused,
                packageName = root?.packageName?.toString(),
                nodeCount = candidates.size,
            )

            if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) imeVisible = true

            // foreground 추론: focused + application window 가 우선
            if (foregroundPackage == null && win.isFocused && type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                foregroundPackage = root?.packageName?.toString()
                foregroundActivityHint = inferActivityHint(root?.className?.toString())
            }
        }

        // focused 가 없으면 active application window 로 fallback
        if (foregroundPackage == null) {
            val main = windowInfos
                .filter { it.isApplication }
                .maxByOrNull { it.bounds.width() * it.bounds.height() }
            foregroundPackage = main?.packageName
        }

        val orientation = if (screenBounds.width() >= screenBounds.height()) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }

        val elapsed = SystemClock.uptimeMillis() - t0
        if (elapsed > SLOW_THRESHOLD_MS) {
            Log.w(
                TAG,
                "Slow collect: ${elapsed}ms, windows=${windowInfos.size}, candidates=${allCandidates.size}",
            )
        }

        return ScreenInfo(
            timestamp = System.currentTimeMillis(),
            foregroundPackage = foregroundPackage,
            foregroundActivity = foregroundActivityHint,
            windows = windowInfos,
            candidates = allCandidates,
            orientation = orientation,
            imeVisible = imeVisible,
            screenBounds = screenBounds,
        )
    }

    /** 모든 window 의 bounds 를 합쳐 화면 전체 영역을 추정. */
    private fun computeScreenBounds(windows: List<AccessibilityWindowInfo>): Rect {
        val out = Rect()
        for (win in windows) {
            val r = Rect()
            win.getBoundsInScreen(r)
            if (out.isEmpty) out.set(r) else out.union(r)
        }
        return out
    }

    /**
     * className 으로부터 activity 이름 추측 (실제 resumed activity 가 아니라 hint 수준).
     * 정확한 값은 [com.exhaustive.explorer.engine] 에서 dumpsys 로 추출 (Phase 3).
     */
    private fun inferActivityHint(rootClassName: String?): String? {
        if (rootClassName == null) return null
        // root 의 className 이 ViewGroup 류면 null 반환. Activity 후보 className 만 채택.
        if (rootClassName.contains("Activity", ignoreCase = true)) return rootClassName
        return null
    }

    companion object {
        private const val TAG = "MultiWindowCollector"
        private const val SLOW_THRESHOLD_MS = 200L
    }
}
