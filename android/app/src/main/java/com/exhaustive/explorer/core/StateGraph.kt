package com.exhaustive.explorer.core

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 탐색 중 발견한 화면(노드) + 액션 전이(엣지) 그래프.
 *
 * - **노드 키**: [ScreenFingerprint.Composite.strict] (= 같은 화면 재방문 인식)
 * - **엣지**: (fromNode, action) → toNode
 *
 * Thread-safe — AccessibilityService 는 main thread 에서 이벤트 받지만,
 * 향후 worker thread 에서 직렬화/조회할 수 있도록 RW lock 사용.
 *
 * 직렬화는 본 클래스에서 직접 안 함 — 별도 storage 모듈 (Phase 1 후반) 에서 JSON 으로.
 */
class StateGraph {

    private val nodes: MutableMap<String, ScreenNode> = HashMap()
    private val lock = ReentrantReadWriteLock()

    /** 현재까지 발견한 unique 화면 수. */
    val nodeCount: Int
        get() = lock.read { nodes.size }

    /** 전체 엣지 수. */
    val edgeCount: Int
        get() = lock.read { nodes.values.sumOf { it.outgoingEdges.size } }

    /**
     * 화면을 그래프에 등록한다. 이미 있으면 lastSeen 만 갱신.
     *
     * @return (node, isNew). isNew=true 면 처음 보는 화면.
     */
    fun upsert(fp: ScreenFingerprint.Composite, screen: ScreenInfo): Pair<ScreenNode, Boolean> = lock.write {
        val existing = nodes[fp.strict]
        if (existing != null) {
            existing.lastSeen = screen.timestamp
            existing.visitCount++
            return@write existing to false
        }
        val node = ScreenNode(
            fingerprint = fp,
            firstSeen = screen.timestamp,
            lastSeen = screen.timestamp,
            visitCount = 1,
            foregroundPackage = screen.foregroundPackage,
            foregroundActivity = screen.foregroundActivity,
            candidateCount = screen.candidates.size,
            windowCount = screen.windows.size,
            // pending actions 는 NodeTraversal 이 추출한 후보 그대로 — DFS 에서 pop 하며 소진
            pendingActions = ArrayDeque(screen.candidates),
        )
        nodes[fp.strict] = node
        node to true
    }

    /**
     * 액션 수행 결과 전이를 기록한다.
     *
     * @param from   액션 수행 전 화면 fp.strict
     * @param action 수행한 후보
     * @param to     액션 후 화면 fp.strict (= 같으면 self-loop, 이는 cold action 일 수 있음)
     */
    fun addEdge(from: String, action: Candidate, to: String) = lock.write {
        val fromNode = nodes[from] ?: return@write
        val edge = StateEdge(action = action, toFp = to)
        fromNode.outgoingEdges.add(edge)
    }

    /** 다음에 실행할 action 을 pendingActions 에서 pop. 없으면 null. */
    fun popNextAction(fp: String): Candidate? = lock.write {
        nodes[fp]?.pendingActions?.removeFirstOrNull()
    }

    /** 해당 fp 가 frontier (남은 미시도 액션) 가 있는지. */
    fun hasFrontier(fp: String): Boolean = lock.read {
        nodes[fp]?.pendingActions?.isNotEmpty() ?: false
    }

    /** 노드 조회. */
    fun get(fp: String): ScreenNode? = lock.read { nodes[fp] }

    /** 모든 노드의 가벼운 요약. 직렬화 / 보고용. */
    fun snapshot(): List<ScreenNodeSummary> = lock.read {
        nodes.values.map { it.toSummary() }
    }

    /**
     * loose fp 가 같은 노드들 — "같은 화면 패밀리" 클러스터링.
     * 백트래킹 실패 시 fallback 후보 찾기 또는 동적 콘텐츠 안정화에 사용.
     */
    fun nodesWithSameLooseFp(looseFp: String): List<ScreenNode> = lock.read {
        nodes.values.filter { it.fingerprint.loose == looseFp }
    }

    /** 전부 비우기 — 새 run 시작 시. */
    fun clear() = lock.write { nodes.clear() }
}

/** 화면 그래프의 노드. */
class ScreenNode(
    val fingerprint: ScreenFingerprint.Composite,
    val firstSeen: Long,
    var lastSeen: Long,
    var visitCount: Int,
    val foregroundPackage: String?,
    val foregroundActivity: String?,
    val candidateCount: Int,
    val windowCount: Int,
    /** DFS 가 아직 시도하지 않은 액션 큐. ExplorerEngine 이 pop 하며 소진. */
    val pendingActions: ArrayDeque<Candidate>,
    /** 이 노드에서 시작한 전이 기록. */
    val outgoingEdges: MutableList<StateEdge> = mutableListOf(),
) {
    /** frontier 가 비어있으면 이 노드는 fully explored. */
    val isFullyExplored: Boolean get() = pendingActions.isEmpty()

    fun toSummary(): ScreenNodeSummary = ScreenNodeSummary(
        strict = fingerprint.strict,
        loose = fingerprint.loose,
        pkg = foregroundPackage,
        activity = foregroundActivity,
        visitCount = visitCount,
        candidateCount = candidateCount,
        pendingCount = pendingActions.size,
        outgoingCount = outgoingEdges.size,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
    )
}

/** 한 액션 전이. self-loop (from == to) 도 가능 (cold action). */
data class StateEdge(
    val action: Candidate,
    val toFp: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/** 직렬화 / 보고 친화적 요약. */
data class ScreenNodeSummary(
    val strict: String,
    val loose: String,
    val pkg: String?,
    val activity: String?,
    val visitCount: Int,
    val candidateCount: Int,
    val pendingCount: Int,
    val outgoingCount: Int,
    val firstSeen: Long,
    val lastSeen: Long,
)
