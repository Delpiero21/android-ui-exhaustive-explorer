package com.exhaustive.explorer.tier4_probe

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.exhaustive.explorer.core.Candidate
import kotlinx.coroutines.delay

/**
 * 후보 좌표 탭 전후 화면 diff 로 실제 인터랙티브 영역 판별 (HOT / COLD).
 *
 * Phase 1-8. Tier 1/2/3 가 만든 후보 리스트를 검증 게이트로 통과시킨다.
 *
 * 동작:
 * 1. before bitmap 캡처
 * 2. 후보 탭 (GestureDispatcher)
 * 3. settle wait (가변 300/1000/3000 ms 단계)
 * 4. after bitmap 캡처
 * 5. perceptual hash 또는 pixel diff 로 변화 측정
 * 6. HOT (변화 있음) / COLD (변화 없음) 분류
 *
 * 동적 콘텐츠 (시계 / 광고 / 애니메이션) 오탐 방지:
 * - 시계 영역 (상단 우측 ~100px) 마스킹
 * - 페이지 인디케이터 (하단 ~50px) 마스킹
 *
 * Phase 1 구현 범위:
 * - 본 클래스: probe 함수 시그니처 + diff 계산 로직만
 * - 실제 탭 / 캡처 호출은 ExplorerEngine 의 autonomous loop 에서
 *
 * Phase 2 에서 추가 예정:
 * - perceptual hash (pHash / dHash) 정밀화
 * - 영역별 적응형 임계값
 */
class DifferentialProbe {

    /**
     * 두 bitmap 의 pixel diff 비율 (0.0 ~ 1.0). 마스킹 영역 제외.
     *
     * Phase 1 단순 구현: ARGB 픽셀 비교, 임계값 10 (작은 색차 무시).
     * 성능: 1080×2340 한 쌍 비교 ~50ms (downsample 안 한 경우).
     */
    fun diffRatio(
        before: Bitmap?,
        after: Bitmap?,
        maskedRegions: List<Rect> = DEFAULT_MASKED_REGIONS,
    ): Double {
        if (before == null || after == null) {
            Log.w(TAG, "diffRatio: one or both bitmaps null → assume no change")
            return 0.0
        }
        if (before.width != after.width || before.height != after.height) {
            // 회전·해상도 변경 — 큰 변화로 간주
            return 1.0
        }
        val w = before.width
        val h = before.height
        // downsample to speed up
        val step = SAMPLE_STEP
        var sampled = 0
        var diff = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                if (isMasked(x, y, maskedRegions)) {
                    x += step; continue
                }
                sampled++
                val pb = before.getPixel(x, y)
                val pa = after.getPixel(x, y)
                if (pixelDiff(pb, pa) > PIXEL_THRESHOLD) diff++
                x += step
            }
            y += step
        }
        return if (sampled == 0) 0.0 else diff.toDouble() / sampled
    }

    /**
     * HOT/COLD 판정.
     *
     * @return [ProbeResult]
     */
    fun classify(
        before: Bitmap?,
        after: Bitmap?,
        candidate: Candidate,
        maskedRegions: List<Rect> = DEFAULT_MASKED_REGIONS,
    ): ProbeResult {
        val ratio = diffRatio(before, after, maskedRegions)
        val cls = when {
            ratio >= HOT_THRESHOLD -> ProbeResult.Verdict.HOT
            ratio >= WARM_THRESHOLD -> ProbeResult.Verdict.WARM
            else -> ProbeResult.Verdict.COLD
        }
        Log.d(TAG, "probe ${candidate.shortLabel()} → $cls (ratio=${"%.3f".format(ratio)})")
        return ProbeResult(verdict = cls, diffRatio = ratio, candidate = candidate)
    }

    /**
     * 비동기 settle wait — 액션 후 화면이 안정될 때까지 대기.
     *
     * 가변 stage: 1단계 (즉시), 2단계 (1s 후 추가 캡처), 3단계 (3s 후 최종).
     * ExplorerEngine 이 [waitStage] 를 늘려가며 호출.
     */
    suspend fun settleDelay(stage: Int) {
        val ms = when (stage) {
            0 -> SETTLE_FAST_MS
            1 -> SETTLE_NORMAL_MS
            else -> SETTLE_SLOW_MS
        }
        delay(ms)
    }

    // ──────── helpers ────────

    private fun pixelDiff(a: Int, b: Int): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db)
    }

    private fun isMasked(x: Int, y: Int, regions: List<Rect>): Boolean {
        for (r in regions) {
            if (x in r.left until r.right && y in r.top until r.bottom) return true
        }
        return false
    }

    // ──────── data ────────

    data class ProbeResult(
        val verdict: Verdict,
        val diffRatio: Double,
        val candidate: Candidate,
    ) {
        enum class Verdict {
            HOT,    // 화면이 의미있게 변함 → 새 state edge 등록
            WARM,   // 살짝 변함 (애니메이션 / 잔향) — 통계용
            COLD    // 변화 없음 → 후보 폐기
        }
    }

    companion object {
        private const val TAG = "DifferentialProbe"

        private const val SAMPLE_STEP = 8  // 8px 단위 sampling → 64x 가속
        private const val PIXEL_THRESHOLD = 30  // r+g+b 차이 30 이하는 노이즈
        private const val HOT_THRESHOLD = 0.05   // 5% 이상 픽셀 변화 → HOT
        private const val WARM_THRESHOLD = 0.01

        private const val SETTLE_FAST_MS = 300L
        private const val SETTLE_NORMAL_MS = 1000L
        private const val SETTLE_SLOW_MS = 3000L

        /** 시계·인디케이터 영역 (1080×2340 기준) 기본 마스킹. */
        private val DEFAULT_MASKED_REGIONS = listOf(
            Rect(0, 0, 1080, 100),       // 상단 status bar (시계 + 알림)
            Rect(0, 2240, 1080, 2340),   // 하단 navigation bar
        )
    }
}
