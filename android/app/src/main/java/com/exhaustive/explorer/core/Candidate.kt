package com.exhaustive.explorer.core

import android.graphics.Rect

/**
 * 탐색 알고리즘이 다음 액션 후보로 고려하는 단일 UI 요소.
 *
 * Tier 1 (a11y) 가 추출한 것 / Tier 2 (grid) 가 격자에서 만든 것 /
 * Tier 3 (CV) 가 검출한 것 모두 동일한 [Candidate] 로 통합된다.
 *
 * @property nodeRef        실제 액션 수행 시 노드 식별. [NodeRef.ByA11y] 면 [AccessibilityNodeInfo.performAction],
 *                          [NodeRef.ByCoordinate] 면 [GestureDispatcher] 로 좌표 탭.
 * @property bounds         화면 상 영역. 좌표 기반 액션 fallback 및 시각화에 사용.
 * @property resourceId     `view_id` 패키지 prefix 포함 (`com.samsung...:id/foo`). 없으면 null.
 * @property contentDesc    `content-desc`. a11y 라벨.
 * @property text           표시 텍스트 (TextView 등).
 * @property className      `android.widget.Button` 같은 class FQN.
 * @property windowId       소속 window 의 [AccessibilityWindowInfo.getId]. 다중 window 환경 식별용.
 * @property packageName    소속 앱 패키지.
 * @property actions        해당 후보에 적용 가능한 액션 집합.
 * @property source         어느 Tier 가 발견했는지. cross-validation / 통계용.
 */
data class Candidate(
    val nodeRef: NodeRef,
    val bounds: Rect,
    val resourceId: String?,
    val contentDesc: String?,
    val text: String?,
    val className: String?,
    val windowId: Int,
    val packageName: String?,
    val actions: Set<CandidateAction>,
    val source: CandidateSource,
) {
    /** 영역이 비어 있거나 화면 밖이면 false. */
    val hasValidBounds: Boolean
        get() = !bounds.isEmpty && bounds.left >= 0 && bounds.top >= 0

    /** 액션 수행을 위한 좌표 (bounds 중심). */
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()

    /** 사람이 읽을 수 있는 짧은 라벨 — 로그에만 사용. */
    fun shortLabel(): String {
        val label = contentDesc ?: text ?: resourceId?.substringAfter('/') ?: className?.substringAfterLast('.') ?: "?"
        return "$label@[$centerX,$centerY]"
    }
}

/** [Candidate] 의 액션 종류. 단일 후보는 여러 액션을 가질 수 있다 (예: clickable + long-clickable). */
enum class CandidateAction {
    CLICK,
    LONG_CLICK,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    FOCUS,
    TEXT_INPUT,
    SWIPE,
}

/** [Candidate] 의 발견 출처 Tier. cross-validation 통계 + 디버깅용. */
enum class CandidateSource {
    A11Y_NODE,        // Tier 1
    PIXEL_GRID,       // Tier 2
    CV_DETECTION,     // Tier 3
    VLM_PROPOSAL,     // Tier 5
}
