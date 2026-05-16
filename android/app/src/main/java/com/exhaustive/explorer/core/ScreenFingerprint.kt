package com.exhaustive.explorer.core

import java.security.MessageDigest

/**
 * [ScreenInfo] → 두 단계 hash.
 *
 * docs/LIMITATIONS.md §7 의 Strict-vs-Loose 트레이드오프 해결:
 * - **strict**: 같은 화면 재방문 식별. 너무 strict 하면 동적 콘텐츠로 매번 신규 → 무한 신규.
 * - **loose**: 같은 레이아웃 클러스터링. 너무 loose 하면 다른 화면도 같은 fp → 누락.
 *
 * 본 클래스는 **둘을 동시에 산출**하여 [StateGraph] 가 양쪽을 활용한다.
 *
 * @see com.exhaustive.explorer.tier1_a11y.MultiWindowCollector  fingerprint 의 입력 source
 */
object ScreenFingerprint {

    private const val ALGO = "SHA-256"
    private const val HEX_LEN = 16  // 64-bit prefix 만 사용 (충돌 확률 무시 가능, 가독성)

    /**
     * 화면을 "강하게" 식별. 텍스트·시간·숫자는 정규화하지만 구조 + resource-id + 핵심 라벨은 포함.
     *
     * 같은 fp = "같은 화면 재방문" 으로 간주.
     */
    fun strict(screen: ScreenInfo): String {
        val md = MessageDigest.getInstance(ALGO)
        // 1) Activity / package
        md.update("pkg:${screen.foregroundPackage ?: "?"}\n".toByteArray())
        md.update("act:${screen.foregroundActivity ?: "?"}\n".toByteArray())
        // 2) Window stack signature (popup 별도 인식)
        val windowSig = screen.windows.sortedBy { it.layer }.joinToString(",") {
            "${windowTypeShort(it.type)}:${it.nodeCount}"
        }
        md.update("win:$windowSig\n".toByteArray())
        // 3) Orientation + IME
        md.update("ori:${screen.orientation}|ime:${screen.imeVisible}\n".toByteArray())
        // 4) Candidate signatures — 정렬 후 직렬화
        val candSig = screen.candidates
            .asSequence()
            .map { strictCandidateSig(it) }
            .sorted()
            .joinToString("|")
        md.update("cand:$candSig".toByteArray())

        return md.digest().toHex(HEX_LEN)
    }

    /**
     * 화면을 "약하게" 식별. 텍스트 / 카운트 / 좌표 모두 무시하고 구조 만으로 클러스터링.
     *
     * 같은 fp = "같은 레이아웃 종류" 로 간주. 동적 콘텐츠 (리스트, 채팅, 갤러리) 안정화에 사용.
     */
    fun loose(screen: ScreenInfo): String {
        val md = MessageDigest.getInstance(ALGO)
        md.update("pkg:${screen.foregroundPackage ?: "?"}\n".toByteArray())
        md.update("act:${screen.foregroundActivity ?: "?"}\n".toByteArray())
        // window 구조만 (개수 무시)
        val windowSig = screen.windows
            .sortedBy { it.layer }
            .joinToString(",") { windowTypeShort(it.type) }
        md.update("winType:$windowSig\n".toByteArray())
        // candidate 의 class 분포만
        val classDist = screen.candidates
            .groupingBy { it.className?.substringAfterLast('.') ?: "?" }
            .eachCount()
            .toSortedMap()
            .toString()
        md.update("classDist:$classDist".toByteArray())

        return md.digest().toHex(HEX_LEN)
    }

    /**
     * 두 fp 를 합친 composite key. State graph 노드 식별에 사용.
     * Loose 가 같으면서 strict 가 다른 화면을 "같은 패밀리" 로 보고 backtracking 시 fallback 후보로 활용 가능.
     */
    fun composite(screen: ScreenInfo): Composite = Composite(
        strict = strict(screen),
        loose = loose(screen),
    )

    /** 단일 candidate 의 strict 시그니처. 텍스트는 정규화하여 동적 콘텐츠 안정화. */
    private fun strictCandidateSig(c: Candidate): String {
        val cls = c.className?.substringAfterLast('.') ?: "?"
        val id = c.resourceId?.substringAfterLast('/') ?: "-"
        val desc = normalizeText(c.contentDesc)
        val text = normalizeText(c.text)
        val acts = c.actions.sortedBy { it.name }.joinToString("+") { it.name }
        // bounds 는 정규화 좌표 (해상도 독립) — 8 grid 셀 단위로 quantize
        val nx = quantize(c.bounds.centerX())
        val ny = quantize(c.bounds.centerY())
        return "$cls#$id@$nx,$ny[$desc|$text]<$acts>"
    }

    /**
     * 텍스트 정규화:
     * - null/empty → "-"
     * - 숫자 시퀀스 → `N`
     * - 시간 패턴 `HH:MM` / `HH:MM:SS` → `T`
     * - 공백 압축
     *
     * 목적: "현재 시간 10:23" / "현재 시간 10:24" 가 다른 화면으로 인식되지 않게.
     */
    private fun normalizeText(s: String?): String {
        if (s.isNullOrBlank()) return "-"
        var out = s
        // 시간 먼저 (숫자 정규화보다 먼저)
        out = TIME_PATTERN.replace(out, "T")
        out = NUMBER_PATTERN.replace(out, "N")
        out = WHITESPACE_PATTERN.replace(out, " ").trim()
        return out
    }

    /** 좌표 양자화 — 8px 단위로 묶어 작은 흔들림 흡수. */
    private fun quantize(coord: Int): Int = coord and 0x7FFFFFF8

    private fun windowTypeShort(type: Int): String = when (type) {
        WindowInfo.TYPE_APPLICATION -> "app"
        WindowInfo.TYPE_INPUT_METHOD -> "ime"
        WindowInfo.TYPE_SYSTEM -> "sys"
        WindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "a11y_ov"
        WindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "split"
        else -> "win$type"
    }

    private fun ByteArray.toHex(maxLen: Int): String {
        val sb = StringBuilder(maxLen)
        for (i in 0 until (maxLen / 2)) {
            sb.append(HEX_CHARS[(this[i].toInt() shr 4) and 0xF])
            sb.append(HEX_CHARS[this[i].toInt() and 0xF])
        }
        return sb.toString()
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    private val TIME_PATTERN = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")
    private val NUMBER_PATTERN = Regex("""\d+""")
    private val WHITESPACE_PATTERN = Regex("""\s+""")

    /** Strict + loose 페어. */
    data class Composite(val strict: String, val loose: String) {
        override fun toString(): String = "S:$strict / L:$loose"
    }
}
