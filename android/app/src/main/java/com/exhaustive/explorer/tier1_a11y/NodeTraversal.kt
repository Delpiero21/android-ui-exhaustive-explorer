package com.exhaustive.explorer.tier1_a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.exhaustive.explorer.core.Candidate
import com.exhaustive.explorer.core.CandidateAction
import com.exhaustive.explorer.core.CandidateSource
import com.exhaustive.explorer.core.NodeRef

/**
 * 단일 window root 에서 클릭/스크롤 가능 후보를 BFS 로 추출한다.
 *
 * 한계 (의도된 것):
 * - 한 window 만 처리. 여러 window 통합은 [MultiWindowCollector] 가 담당.
 * - a11y semantics 가 없는 view (Compose `semantics` 미설정, Custom View) 는 누락.
 *   → Tier 2~5 가 보강.
 */
object NodeTraversal {

    /** BFS 시 한 트리당 최대 노드 수. 무한 트리 방지 (현실적으로 5000 넘으면 의심). */
    private const val MAX_NODES_PER_TREE = 5000

    /**
     * [root] 노드를 BFS 로 순회하며 액션 가능 후보를 추출한다.
     *
     * @return 후보 리스트. clickable / long-clickable / scrollable / editable 중 하나 이상.
     */
    fun walk(root: AccessibilityNodeInfo?, windowId: Int): List<Candidate> {
        if (root == null) return emptyList()

        val candidates = mutableListOf<Candidate>()
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.addLast(root)

        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_PER_TREE) {
            val node = queue.removeFirst()
            visited++

            extractCandidate(node, windowId)?.let { candidates += it }

            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                queue.addLast(child)
            }
        }
        return candidates
    }

    /**
     * 단일 노드에서 [Candidate] 를 추출한다.
     * 어떤 액션도 없으면 null.
     */
    private fun extractCandidate(node: AccessibilityNodeInfo, windowId: Int): Candidate? {
        val actions = inferActions(node)
        if (actions.isEmpty()) return null

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        // 화면 밖 / 0 크기 노드는 의미 없음 (예: GONE View)
        if (bounds.isEmpty) return null

        return Candidate(
            nodeRef = NodeRef.ByA11y(
                nodeId = nodeUniqueId(node, windowId),
                windowId = windowId,
                fallbackBounds = bounds,
            ),
            bounds = bounds,
            resourceId = node.viewIdResourceName,
            contentDesc = node.contentDescription?.toString(),
            text = node.text?.toString(),
            className = node.className?.toString(),
            windowId = windowId,
            packageName = node.packageName?.toString(),
            actions = actions,
            source = CandidateSource.A11Y_NODE,
        )
    }

    /**
     * 노드의 a11y 속성에서 가능한 액션 집합을 추론한다.
     *
     * 주의: 자식 노드만 clickable=true 이고 부모가 보여주는 경우 흔함 (Case 4 색상 swatch).
     * 따라서 long-clickable / scrollable 등도 별도로 체크.
     */
    private fun inferActions(node: AccessibilityNodeInfo): Set<CandidateAction> {
        val actions = mutableSetOf<CandidateAction>()
        if (node.isClickable) actions += CandidateAction.CLICK
        if (node.isLongClickable) actions += CandidateAction.LONG_CLICK
        if (node.isFocusable) actions += CandidateAction.FOCUS
        if (node.isScrollable) {
            actions += CandidateAction.SCROLL_FORWARD
            actions += CandidateAction.SCROLL_BACKWARD
        }
        if (node.isEditable) {
            actions += CandidateAction.TEXT_INPUT
        }
        return actions
    }

    /**
     * 노드를 식별할 unique long.
     *
     * `getSourceNodeId()` 는 hidden API 라 reflection 없이는 못 부른다.
     * 따라서 (windowId, viewIdResourceName-hash, bounds-center) 조합으로 생성.
     * 같은 window 내 동일 위치 + 동일 id 의 노드를 안정적으로 식별하기 충분.
     */
    private fun nodeUniqueId(node: AccessibilityNodeInfo, windowId: Int): Long {
        val rect = Rect().also { node.getBoundsInScreen(it) }
        val id = node.viewIdResourceName?.hashCode() ?: 0
        val center = (rect.centerX().toLong() shl 16) or rect.centerY().toLong()
        return (windowId.toLong() shl 48) or (id.toLong() and 0xFFFF shl 32) or (center and 0xFFFFFFFFL)
    }
}
