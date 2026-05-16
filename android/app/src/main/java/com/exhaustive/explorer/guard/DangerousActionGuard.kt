package com.exhaustive.explorer.guard

import android.util.Log
import com.exhaustive.explorer.core.Candidate
import java.text.Normalizer

/**
 * 비가역 / 위험 액션 진입 차단.
 *
 * Phase 1-7. ExplorerEngine 이 후보 popping 직전에 본 클래스로 필터링한다.
 *
 * 차단 대상:
 * 1. 결제 / 송금 / 인증 / SMS 전송 — 실제 금전·통신 사고
 * 2. 전체 삭제 / 공장 초기화 — 데이터 손실
 * 3. 로그아웃 / 계정 삭제 — 단말 상태 복구 불가
 * 4. 외부 앱 호출 (전화 걸기, 카메라 촬영 등) — 트랩
 *
 * 매칭 전략:
 * - content-desc / text / resource-id 에 대해 NFC 유니코드 정규화 후 키워드 매칭
 * - 한국어 + 영어 키워드 양쪽
 * - 보수적으로 — 의심스러우면 차단 (false-block 보다 진짜 사고가 훨씬 위험)
 *
 * 단점:
 * - 보수적이라 정상 케이스도 일부 차단 가능. 운영 통계 보고 키워드 조정 필요.
 */
class DangerousActionGuard {

    /**
     * @return true 면 위험하지 않음 (탐색 OK). false 면 차단.
     */
    fun isSafe(candidate: Candidate): Boolean {
        val combined = buildString {
            candidate.text?.let { append(it); append(' ') }
            candidate.contentDesc?.let { append(it); append(' ') }
            candidate.resourceId?.substringAfterLast('/')?.let { append(it) }
        }
        if (combined.isBlank()) {
            // 라벨이 비면 매칭 불가 — 일단 통과 (좌표 후보는 별도 책임)
            return true
        }
        val normalized = normalize(combined)
        val danger = DANGEROUS_KEYWORDS.firstOrNull { normalized.contains(it) }
        if (danger != null) {
            Log.w(TAG, "BLOCKED [$danger]: ${candidate.shortLabel()}")
            return false
        }
        return true
    }

    /**
     * 화면 단위 — 현재 화면에 진입 자체를 차단해야 하는지 검사.
     * 예: "공장 초기화 진행 중" 화면이 떴으면 즉시 BACK / HOME.
     */
    fun shouldEvacuateScreen(allTexts: Collection<String>): EvacuateDecision {
        val normalized = allTexts.joinToString(" ").let { normalize(it) }
        // 더 보수적인 키워드 (화면 단위는 한 단어만으로 못 결정)
        for (severe in SEVERE_SCREEN_KEYWORDS) {
            if (normalized.contains(severe)) {
                return EvacuateDecision(reason = severe, action = EvacuateAction.PRESS_HOME)
            }
        }
        return EvacuateDecision(reason = null, action = EvacuateAction.NONE)
    }

    // ──────── normalization ────────

    private fun normalize(s: String): String {
        val nfc = Normalizer.normalize(s, Normalizer.Form.NFC)
        return nfc.lowercase()
            .replace(WHITESPACE_PATTERN, " ")
            .trim()
    }

    // ──────── data ────────

    data class EvacuateDecision(
        val reason: String?,
        val action: EvacuateAction,
    ) {
        val isEvacuate: Boolean get() = action != EvacuateAction.NONE
    }

    enum class EvacuateAction { NONE, PRESS_BACK, PRESS_HOME, FORCE_KILL }

    companion object {
        private const val TAG = "DangerousActionGuard"

        private val WHITESPACE_PATTERN = Regex("""\s+""")

        /** 후보 단위 차단 키워드. 정규화 후 contains 매칭. */
        private val DANGEROUS_KEYWORDS = setOf(
            // 결제 / 금전
            "결제", "결재", "지급", "송금", "결제하기", "구매", "구매하기",
            "pay", "payment", "purchase", "checkout",

            // 인증 / SMS
            "인증번호", "본인 인증", "본인인증", "휴대폰 인증",
            "verify", "verification",

            // SMS / 통화
            "전송", "발송", "보내기",
            "send", "sms", "call",

            // 삭제 / 초기화
            "전체 삭제", "모두 삭제", "모두 지우기", "공장 초기화", "초기화",
            "delete all", "factory reset", "erase all", "wipe",

            // 계정
            "로그아웃", "계정 삭제", "탈퇴", "회원 탈퇴",
            "sign out", "log out", "delete account",

            // 외부 앱 진입 (탐색 트랩)
            "play 스토어", "삼성 계정", "samsung account",

            // 권한
            "fingerprint", "지문 인증", "pin 인증",
        )

        /** 화면 단위 — 진입했으면 즉시 빠져나가야 할 키워드. */
        private val SEVERE_SCREEN_KEYWORDS = setOf(
            "공장 초기화", "factory reset",
            "데이터 삭제 중", "erasing",
            "결제 진행", "payment in progress",
        )
    }
}
