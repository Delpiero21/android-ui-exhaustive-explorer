package com.exhaustive.explorer.core

import android.graphics.Rect

/**
 * 한 시점의 단말 화면 상태를 통합한 스냅샷.
 *
 * 본 클래스는 [tier1_a11y.MultiWindowCollector] 가 단일 sweep 으로 생성한다.
 * 다른 Tier (2~5) 가 후보를 보강해 [Candidate] 리스트를 늘릴 수 있다.
 *
 * [ScreenFingerprint] 가 이 객체를 입력으로 받아 hash 를 계산하고,
 * [StateGraph] 가 fingerprint → ScreenInfo 매핑을 유지한다.
 *
 * @property timestamp           ms (System.currentTimeMillis())
 * @property foregroundPackage   가장 위 active window 의 package
 * @property foregroundActivity  resumed activity (사용 가능할 때)
 * @property windows             모든 활성 window — [AccessibilityService.getWindows] 결과 변환
 * @property candidates          전 Tier 통합 후보 리스트
 * @property orientation         [android.content.res.Configuration.ORIENTATION_PORTRAIT] / LANDSCAPE
 * @property imeVisible          IME (키보드) window 가 떠 있는지 (fingerprint 보조 키)
 * @property screenBounds        화면 전체 크기. 좌표 정규화 시 분모로 사용.
 */
data class ScreenInfo(
    val timestamp: Long,
    val foregroundPackage: String?,
    val foregroundActivity: String?,
    val windows: List<WindowInfo>,
    val candidates: List<Candidate>,
    val orientation: Int,
    val imeVisible: Boolean,
    val screenBounds: Rect,
) {
    /** 활성 / focused window 가 1개라도 있는지. dump 가 비어있으면 false. */
    val hasActiveWindow: Boolean
        get() = windows.any { it.isActive || it.isFocused }

    /** main Activity window — 가장 큰 application-type window. */
    fun mainAppWindow(): WindowInfo? = windows
        .filter { it.type == WindowInfo.TYPE_APPLICATION }
        .maxByOrNull { it.bounds.width() * it.bounds.height() }

    /** popup / overlay window 들 — 본 도구의 Case 6/10 핵심 처리 대상. */
    fun overlayWindows(): List<WindowInfo> = windows.filter {
        it.type != WindowInfo.TYPE_APPLICATION && it.type != WindowInfo.TYPE_INPUT_METHOD
    }
}

/**
 * 단일 window 의 메타 정보.
 *
 * [type] 은 [android.view.accessibility.AccessibilityWindowInfo] 의 TYPE_* 상수와 동일 매핑.
 */
data class WindowInfo(
    val id: Int,
    val type: Int,
    val layer: Int,
    val bounds: Rect,
    val isActive: Boolean,
    val isFocused: Boolean,
    val packageName: String?,
    /** 본 window 의 후보 노드 개수. fingerprint 보조 키. */
    val nodeCount: Int,
) {
    companion object {
        // AccessibilityWindowInfo.TYPE_* 와 동일한 값으로 wrapping 해두는 게 의존성 격리 측면에서 안전.
        const val TYPE_APPLICATION = 1
        const val TYPE_INPUT_METHOD = 2
        const val TYPE_SYSTEM = 3
        const val TYPE_ACCESSIBILITY_OVERLAY = 4
        const val TYPE_SPLIT_SCREEN_DIVIDER = 5
    }

    val isInputMethod: Boolean get() = type == TYPE_INPUT_METHOD
    val isApplication: Boolean get() = type == TYPE_APPLICATION
    val isSystem: Boolean get() = type == TYPE_SYSTEM
}
