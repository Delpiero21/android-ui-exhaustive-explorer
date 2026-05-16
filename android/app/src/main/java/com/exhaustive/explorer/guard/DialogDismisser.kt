package com.exhaustive.explorer.guard

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.exhaustive.explorer.core.ScreenInfo
import com.exhaustive.explorer.core.WindowInfo
import java.text.Normalizer

/**
 * 권한 다이얼로그 / 알림 / 인터럽트 자동 처리.
 *
 * Phase 1-6. 백그라운드에서 상시 가동되며 탐색 흐름과 무관하게 dialog 가 뜨면 즉시 dismiss.
 *
 * 매칭:
 * - 시스템 popup (permission controller, system_ui) 의 버튼 텍스트로 매칭
 * - 한국어 + 영어 양쪽
 * - NFC(유니코드 정규화) 적용 — `허용` 이 NFD 로 분리된 경우 매칭 실패 회피
 *
 * 정책:
 * - **권한 다이얼로그는 "허용" 으로 자동 부여** (검증 단말이라 가정)
 * - 업데이트 / 알림 / 광고 다이얼로그는 "나중에" / "거부"
 * - 앱 자체 다이얼로그 (예: "저장하시겠습니까") 는 건드리지 않음 (상태 손실 위험)
 *
 * docs/LIMITATIONS.md §12.2 의 한글 NFC 정규화는 본 클래스가 정공 처리.
 */
class DialogDismisser {

    /**
     * 화면에서 dismiss 대상 popup 이 있는지 + 어떤 노드를 클릭해야 하는지.
     *
     * @return 클릭할 [AccessibilityNodeInfo]. null 이면 처리할 popup 없음.
     */
    fun findDismissTarget(screen: ScreenInfo, allWindows: List<RootedWindow>): AccessibilityNodeInfo? {
        // 우선순위 1: 시스템 권한 다이얼로그
        for (rooted in allWindows) {
            if (!isSystemDialog(rooted.window)) continue
            val target = findButton(rooted.root, PERMISSION_ALLOW_LABELS)
            if (target != null) {
                Log.i(TAG, "permission dialog detected → allow")
                return target
            }
        }
        // 우선순위 2: 업데이트 / 알림 / 광고
        for (rooted in allWindows) {
            if (!isSystemDialog(rooted.window)) continue
            val target = findButton(rooted.root, LATER_DENY_LABELS)
            if (target != null) {
                Log.i(TAG, "system notification detected → later/deny")
                return target
            }
        }
        return null
    }

    /**
     * window 가 시스템 popup 인지. 시스템 UI / permission controller 패키지 또는
     * TYPE_SYSTEM / TYPE_ACCESSIBILITY_OVERLAY.
     */
    private fun isSystemDialog(window: WindowInfo): Boolean {
        val pkg = window.packageName ?: return window.isSystem
        return pkg in SYSTEM_DIALOG_PACKAGES ||
            window.type == WindowInfo.TYPE_SYSTEM ||
            window.type == WindowInfo.TYPE_ACCESSIBILITY_OVERLAY
    }

    /**
     * tree 를 BFS 로 walk 하며 [labels] 중 하나에 매칭되는 clickable 노드를 찾는다.
     */
    private fun findButton(
        root: AccessibilityNodeInfo?,
        labels: Set<String>,
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++

            if (node.isClickable) {
                val combined = buildString {
                    node.text?.let { append(it); append(' ') }
                    node.contentDescription?.let { append(it) }
                }
                if (combined.isNotBlank()) {
                    val n = normalize(combined)
                    if (labels.any { n.contains(it) }) {
                        return node
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                queue.addLast(child)
            }
        }
        return null
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC)
            .lowercase()
            .replace(WHITESPACE_PATTERN, " ")
            .trim()

    /** [com.exhaustive.explorer.tier1_a11y.MultiWindowCollector] 가 들고 있는 (window, root) 페어. */
    data class RootedWindow(
        val window: WindowInfo,
        val root: AccessibilityNodeInfo?,
    )

    companion object {
        private const val TAG = "DialogDismisser"
        private const val MAX_NODES = 200

        private val WHITESPACE_PATTERN = Regex("""\s+""")

        private val SYSTEM_DIALOG_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.samsung.android.permissioncontroller",
            "com.samsung.android.systemui",
            "android",  // 일부 안드로이드 자체 다이얼로그
        )

        /** 권한 dialog 의 "허용" 계열 라벨. */
        private val PERMISSION_ALLOW_LABELS = setOf(
            "허용", "허용하기", "사용 중에만 허용", "앱 사용 중에만 허용",
            "이번만 허용", "한 번만 허용",
            "allow", "allow only this time", "while using the app",
            "ok", "yes",
        )

        /** 업데이트 / 알림 / 광고 의 "나중에/거부" 계열. */
        private val LATER_DENY_LABELS = setOf(
            "나중에", "다음에", "취소", "거부", "거절", "허용 안 함",
            "지금은 안 함", "건너뛰기", "닫기",
            "later", "skip", "deny", "no thanks", "not now", "cancel", "dismiss",
        )
    }
}
