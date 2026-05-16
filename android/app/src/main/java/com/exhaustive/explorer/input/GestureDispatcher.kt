package com.exhaustive.explorer.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.exhaustive.explorer.core.Candidate
import com.exhaustive.explorer.core.CandidateAction
import com.exhaustive.explorer.core.NodeRef
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 합성 입력 (tap / longpress / swipe) primitive.
 *
 * Phase 1-9. ExplorerEngine 이 액션 수행 시 본 클래스에 위임.
 *
 * 정책:
 * - [Candidate.nodeRef] 가 [NodeRef.ByA11y] 면 [android.view.accessibility.AccessibilityNodeInfo.performAction] 우선
 *   → 좌표 의존성 0, Case 4 같은 동일 resource-id 색상 swatch 도 노드 인덱스로 정확
 * - 실패 시 또는 [NodeRef.ByCoordinate] 면 [GestureDescription] 으로 합성 터치 fallback
 *
 * 한계 (docs/LIMITATIONS.md §10):
 * - 시스템 UI (Quick panel, 잠금) 일부 차단
 * - 멀티터치 정밀도 한계
 * - 손글씨 곡선 보간 한계
 *
 * 비동기 — coroutine 으로 wrap. dispatchGesture 가 callback 기반이라 suspendCoroutine 패턴.
 */
class GestureDispatcher(
    private val service: AccessibilityService,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 단일 후보에 적용 가능한 모든 primitive action 수행.
     *
     * @param candidate 수행 대상
     * @param action    선택 액션. [Candidate.actions] 에 포함되어야 함.
     * @return 액션 발송 성공 여부 (실제 효과는 [DifferentialProbe] 가 측정).
     */
    suspend fun perform(candidate: Candidate, action: CandidateAction): Boolean {
        return when (action) {
            CandidateAction.CLICK -> click(candidate)
            CandidateAction.LONG_CLICK -> longClick(candidate)
            CandidateAction.SCROLL_FORWARD -> scroll(candidate, forward = true)
            CandidateAction.SCROLL_BACKWARD -> scroll(candidate, forward = false)
            CandidateAction.FOCUS -> focus(candidate)
            CandidateAction.TEXT_INPUT -> {
                // 텍스트 입력은 GestureDispatcher 가 아니라 TextInputSampler 가 처리
                Log.w(TAG, "TEXT_INPUT not handled by GestureDispatcher; route to TextInputSampler")
                false
            }
            CandidateAction.SWIPE -> {
                Log.w(TAG, "Generic SWIPE not implemented as candidate action yet")
                false
            }
        }
    }

    // ─────────────── public primitive ───────────────

    /** 단일 tap. */
    suspend fun click(candidate: Candidate): Boolean {
        if (!candidate.hasValidBounds) {
            Log.w(TAG, "click skipped — invalid bounds: $candidate")
            return false
        }
        // Tier 1 후보면 a11y performAction 시도 (좌표 무관, 정확)
        // 단, AccessibilityNodeInfo 객체 자체를 들고 있지 않으므로 좌표 fallback 으로만 구현 (Phase 1 후반)
        // TODO Phase 1 후반: NodeRef.ByA11y 일 때 NodeInfo cache 에서 lookup 후 performAction 호출
        return tap(candidate.centerX, candidate.centerY)
    }

    /** 짧은 longpress (~500ms). */
    suspend fun longClick(candidate: Candidate): Boolean {
        if (!candidate.hasValidBounds) return false
        return tapAndHold(candidate.centerX, candidate.centerY, LONGPRESS_DURATION_MS)
    }

    /** [forward]=true 면 위로 (다음 콘텐츠 보기), false 면 아래로. */
    suspend fun scroll(candidate: Candidate, forward: Boolean): Boolean {
        if (!candidate.hasValidBounds) return false
        val b = candidate.bounds
        val cx = b.centerX()
        val startY: Int
        val endY: Int
        if (forward) {
            startY = b.bottom - (b.height() * 0.2).toInt()
            endY = b.top + (b.height() * 0.2).toInt()
        } else {
            startY = b.top + (b.height() * 0.2).toInt()
            endY = b.bottom - (b.height() * 0.2).toInt()
        }
        return swipe(cx, startY, cx, endY, SWIPE_DURATION_MS)
    }

    /** 포커스 이동 (텍스트 입력 전 단계). */
    suspend fun focus(candidate: Candidate): Boolean {
        // 좌표 탭으로 focus 도 발생함 (성능 동일)
        return click(candidate)
    }

    /** 임의 좌표 tap. */
    suspend fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    /** 임의 좌표 longpress. */
    suspend fun tapAndHold(x: Int, y: Int, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    /** 직선 swipe. */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    /** 단말 BACK 키. AccessibilityService 의 global action 으로. */
    fun pressBack(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    /** 단말 HOME 키. */
    fun pressHome(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

    /** 단말 RECENTS 키. */
    fun pressRecents(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)

    // ─────────────── private ───────────────

    private suspend fun dispatch(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(g: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val ok = service.dispatchGesture(gesture, callback, mainHandler)
            if (!ok && cont.isActive) {
                Log.w(TAG, "dispatchGesture returned false (service not ready?)")
                cont.resume(false)
            }
        }

    companion object {
        private const val TAG = "GestureDispatcher"
        private const val TAP_DURATION_MS = 60L
        private const val LONGPRESS_DURATION_MS = 700L
        private const val SWIPE_DURATION_MS = 250L
    }
}
