package com.exhaustive.explorer.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.exhaustive.explorer.R
import com.exhaustive.explorer.engine.ExplorerEngine
import com.exhaustive.explorer.ui.MainActivity

/**
 * AccessibilityService 진입점 — Phase 1 + Foreground 격상.
 *
 * 자율 모드 시작 시 [startForeground] 를 호출해 service 를 **foreground state** 로 격상.
 * 이로써 Android 12+ 의 background activity launch 제한을 우회 — Notes 등 타겟 앱
 * 자동 실행 가능.
 *
 * 알림 채널: 단말 상태 바에 항상 표시되는 작은 알림. autonomous 모드 중에만.
 */
class ExplorerAccessibilityService : AccessibilityService() {

    private val engine = ExplorerEngine()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected. Phase 1 — passive + autonomous + foreground available.")
        INSTANCE = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // autonomous 모드 면 이벤트는 무시 (engine 의 worker thread 가 자체 collect)
        if (engine.isAutonomousRunning) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> engine.onEvent(this)
            else -> Unit
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted.")
    }

    override fun onDestroy() {
        Log.i(
            TAG,
            "Service destroyed. nodes=${engine.stateGraph.nodeCount} edges=${engine.stateGraph.edgeCount}",
        )
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        engine.shutdown()
        INSTANCE = null
        super.onDestroy()
    }

    /** MainActivity / 외부 trigger 가 호출. */
    fun startAutonomous(targetPackage: String?, budgetMs: Long = 60_000L) {
        // background activity launch 우회를 위해 foreground 격상
        startForegroundIfNeeded(targetPackage, budgetMs)
        engine.startAutonomous(this, targetPackage, budgetMs)
    }

    fun stopAutonomous() {
        engine.stopAutonomous()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    fun snapshot(): ExplorerEngine.EngineSnapshot = engine.snapshot()
    fun reset() = engine.reset()

    // ──────── foreground service ────────

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "UI Explorer Autonomous Mode",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "자율 탐색 모드 동작 중 알림"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun startForegroundIfNeeded(targetPackage: String?, budgetMs: Long) {
        val notification = buildNotification(targetPackage, budgetMs)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "foreground service started")
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed (계속 시도): ${t.javaClass.simpleName} ${t.message}")
        }
    }

    private fun buildNotification(targetPackage: String?, budgetMs: Long): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val budgetSec = (budgetMs / 1000).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("자율 탐색 진행 중")
            .setContentText(
                "target: ${targetPackage ?: "(any)"} · ${budgetSec}s budget"
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    companion object {
        private const val TAG = "ExplorerA11y"
        private const val CHANNEL_ID = "explorer_autonomous"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var INSTANCE: ExplorerAccessibilityService? = null
            private set
    }
}
