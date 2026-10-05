package com.alram.mail.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.alram.mail.MainActivity
import com.alram.mail.R
import com.alram.mail.container
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 제조사 절전 기능에 앱이 종료되는 것을 줄이기 위한 상시 알림(선택 기능). */
class KeepAliveService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIF_ID, build("알림을 메일로 전달하는 중"))
        job?.cancel()
        val c = container
        job = c.scope.launch {
            combine(c.db.notifications().observeCount("PENDING"), c.dispatcher.state) { pending, st -> pending to st }
                .collect { (pending, st) ->
                    val text = when {
                        st.lastError != null -> "문제가 있어요: ${st.lastError}"
                        pending > 0 -> "대기 중 ${pending}건"
                        else -> "알림을 메일로 전달하는 중"
                    }
                    getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(text))
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alram)
            .setContentTitle("Alram Mail")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "상시 실행", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "알림 전달이 중단되지 않도록 유지합니다"
                    setShowBadge(false)
                },
            )
        }
    }

    companion object {
        private const val CHANNEL = "keepalive"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
