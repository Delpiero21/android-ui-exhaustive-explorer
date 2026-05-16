package com.exhaustive.explorer.core

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.util.Log
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * a11y service 기반 화면 스크린샷 추출.
 *
 * Tier 2 (Pixel Grid), Tier 3 (CV), Tier 4 (Differential Probe), Tier 5 (VLM) 의 입력 source.
 *
 * 본 도구는 [AccessibilityService.takeScreenshot] (API 30+) 사용:
 * - MediaProjection 과 달리 사용자 권한 동의 dialog 불필요
 * - a11y 권한 한 번 부여 받으면 무제한 호출
 *
 * 한계:
 * - 일부 보안 화면 (지문 / 결제) 은 blank 또는 거부
 * - API 30 미만은 미지원 (compileSdk=35, minSdk=31 이라 OK)
 */
class ScreenCapture {

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 단일 스크린샷.
     *
     * @return Bitmap. 캡처 실패 시 null.
     */
    suspend fun capture(service: AccessibilityService): Bitmap? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "takeScreenshot requires API 30+. current=${Build.VERSION.SDK_INT}")
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    try {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        // wrapHardwareBuffer 가 null 반환할 수도 있음 — fallback
                        val safeCopy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        if (cont.isActive) cont.resume(safeCopy)
                    } catch (t: Throwable) {
                        Log.e(TAG, "screenshot decode failed", t)
                        if (cont.isActive) cont.resume(null)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot onFailure errorCode=$errorCode")
                    if (cont.isActive) cont.resume(null)
                }
            },
        )
    }

    /** dispose 시 호출. */
    fun shutdown() {
        runCatching { executor.shutdown() }
    }

    companion object {
        private const val TAG = "ScreenCapture"
    }
}
