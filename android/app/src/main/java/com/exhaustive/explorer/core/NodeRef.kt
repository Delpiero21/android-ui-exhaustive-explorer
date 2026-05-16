package com.exhaustive.explorer.core

import android.graphics.Rect

/**
 * [Candidate] 가 어떻게 동작 수행되는지 식별하는 reference.
 *
 * - [ByA11y] — Tier 1 (a11y) 가 추출한 후보. [AccessibilityNodeInfo.performAction] 으로 정확한 노드 클릭.
 *              좌표 의존성이 없어 Case 4 의 동일 resource-id 9 개 색상 swatch 같은 케이스도 인덱스로 정확.
 *
 * - [ByCoordinate] — Tier 2~5 가 추출한 좌표 기반 후보.
 *                    [GestureDispatcher] 로 좌표 탭. a11y 미커버 영역 (Canvas / OpenGL) 의 유일한 방법.
 *
 * 두 가지 모두 같은 [Candidate.bounds] 정보를 갖지만,
 * 액션 수행 시점에 어느 경로를 탈지가 다르다.
 */
sealed class NodeRef {

    /** a11y node 직접 참조. live API 가 살아있을 때만 유효. */
    data class ByA11y(
        /** [AccessibilityNodeInfo.getSourceNodeId] 또는 자체 부여한 unique id. */
        val nodeId: Long,
        /** 소속 window. 같은 nodeId 라도 window 다르면 다른 노드. */
        val windowId: Int,
        /** 디버깅 / replay 시 좌표 fallback 용. */
        val fallbackBounds: Rect,
    ) : NodeRef()

    /** 좌표 기반. Tier 2~5 의 후보. */
    data class ByCoordinate(
        val x: Int,
        val y: Int,
        /** 어느 픽셀 영역에서 후보가 만들어졌는지. CV 검출이면 검출 박스, grid 면 격자 셀. */
        val sourceBounds: Rect,
    ) : NodeRef()
}
