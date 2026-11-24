package com.kaotoface.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kaotoface.app.KaoToFaceApp
import com.kaotoface.app.MainActivity
import com.kaotoface.app.R
import com.kaotoface.app.core.EnginePhase
import com.kaotoface.app.core.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the microphone session alive when the app is not in front. */
class TranslationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private lateinit var app: KaoToFaceApp

    override fun onCreate() {
        super.onCreate()
        app = application as KaoToFaceApp
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            app.engine.stopSession()
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification(app.engine.state.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            stopSelf()
            return START_NOT_STICKY
        }
        if (collectJob == null) {
            collectJob = scope.launch {
                app.engine.state
                    .map { Signature(it.phase, it.lines.size, it.room?.code, it.translating) }
                    .distinctUntilChanged()
                    .collect {
                        runCatching {
                            NotificationManagerCompat.from(this@TranslationService)
                                .notify(NOTIFICATION_ID, buildNotification(app.engine.state.value))
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        app.engine.stopSession()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        collectJob?.cancel()
        collectJob = null
        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "实时双语转写与翻译"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: UiState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TranslationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val room = state.room
        val title = when {
            state.demo -> "实时翻译 · 演示中"
            room != null -> "实时翻译 · 房间 ${room.code}"
            state.phase == EnginePhase.PREPARING -> "实时翻译 · 正在准备"
            else -> getString(R.string.notification_title)
        }
        val text = buildString {
            append("英 ↔ 中")
            if (state.lines.isNotEmpty()) append(" · ${state.lines.size} 句")
            if (state.translating > 0) append(" · 翻译中")
            if (room != null) append(" · ${room.memberCount} 人")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private data class Signature(
        val phase: EnginePhase,
        val count: Int,
        val room: String?,
        val translating: Int,
    )

    companion object {
        private const val TAG = "KaoService"
        const val ACTION_STOP = "com.kaotoface.app.STOP"
        private const val CHANNEL_ID = "kaotoface_live"
        private const val NOTIFICATION_ID = 4801

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TranslationService::class.java),
                )
            }.onFailure { Log.w(TAG, "cannot start service", it) }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, TranslationService::class.java))
            }
        }
    }
}
