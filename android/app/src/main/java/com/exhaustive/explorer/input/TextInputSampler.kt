package com.exhaustive.explorer.input

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.exhaustive.explorer.core.Candidate
import com.exhaustive.explorer.core.NodeRef

/**
 * EditText 등 입력 필드에 합성 텍스트를 직접 주입.
 *
 * Phase 1-9 후반. IME 우회로 키보드 없이 텍스트 입력 가능.
 *
 * 작동 방식:
 * - [AccessibilityNodeInfo.ACTION_SET_TEXT] 를 우선 시도 (Android 5.0+)
 * - 실패 시 (예: password field) skip — 위험 액션 차단 (DangerousActionGuard 에서 별도 처리)
 *
 * 한계 (docs/LIMITATIONS.md §10.5):
 * - 일부 EditText 는 SET_TEXT 거부 (예: secure password input)
 * - autocomplete suggestion 가 사라지는 케이스 있음
 * - IME 자체가 띄워지지 않은 상태에서 입력 → 일부 앱 동작 깨질 수 있음
 */
class TextInputSampler {

    /** 입력 후보 텍스트 — 짧고 의미 없는 값. 위험 단어 회피. */
    private val sampleTexts = listOf(
        "테스트",
        "a",
        "abc123",
        "1",
        "hello",
    )

    /**
     * [candidate] 가 가리키는 EditText 에 sample 텍스트를 주입한다.
     *
     * Phase 1 현재는 NodeInfo 객체를 직접 들고 있지 않으므로
     * [com.exhaustive.explorer.tier1_a11y.MultiWindowCollector] 가 collect 한
     * NodeInfo 를 lookup 하는 메커니즘이 필요 — Phase 1 후반 [NodeInfoCache] 추가 예정.
     *
     * 본 메서드는 직접 [AccessibilityNodeInfo] 를 받는 버전을 우선 구현하고,
     * candidate-based wrapper 는 NodeInfoCache 도입 후.
     */
    fun setText(node: AccessibilityNodeInfo, text: String? = null): Boolean {
        if (!node.isEditable) {
            Log.d(TAG, "node not editable, skipping")
            return false
        }
        val actualText = text ?: sampleTexts.first()
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, actualText)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) {
            Log.w(TAG, "ACTION_SET_TEXT failed (possibly password field)")
        }
        return ok
    }

    /**
     * [Candidate] 기반 입력. NodeInfoCache 가 없으면 false 리턴.
     * Phase 1 후반에 cache 도입 후 동작.
     */
    fun setText(candidate: Candidate, service: AccessibilityService, text: String? = null): Boolean {
        if (candidate.nodeRef !is NodeRef.ByA11y) {
            Log.d(TAG, "candidate is not a11y-backed, cannot setText via API")
            return false
        }
        // TODO Phase 1 후반: NodeInfoCache 로 lookup 후 setText(node, text) 호출
        Log.d(TAG, "setText(candidate) not yet wired — needs NodeInfoCache")
        return false
    }

    /** 다음 샘플 텍스트 가져오기 (라운드 로빈). */
    fun nextSample(seed: Int = 0): String = sampleTexts[seed.mod(sampleTexts.size)]

    companion object {
        private const val TAG = "TextInputSampler"
    }
}
