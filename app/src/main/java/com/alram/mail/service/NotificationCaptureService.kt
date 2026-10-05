package com.alram.mail.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.alram.mail.container
import com.alram.mail.core.CapturedNotification
import com.alram.mail.data.AppCatalog
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NotificationCaptureService : NotificationListenerService() {
    private val labels = ConcurrentHashMap<String, String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        container.dispatcher.start()
        container.scope.launch {
            if (container.settings.current().keepAlive) KeepAliveService.start(applicationContext)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // 시스템이 연결을 끊었을 때 다시 붙는다.
        runCatching { requestRebind(ComponentName(this, NotificationCaptureService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = extract(sbn) ?: return
        container.scope.launch { container.processor.handle(n) }
    }

    private fun extract(sbn: StatusBarNotification): CapturedNotification? = runCatching {
        val notif = sbn.notification
        val extras = notif.extras
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }.orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            .ifBlank { lines }
        CapturedNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = labels.getOrPut(sbn.packageName) { AppCatalog.labelOf(packageManager, sbn.packageName) },
            title = title,
            text = text,
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty(),
            postedAt = sbn.postTime,
            category = notif.category.orEmpty(),
            isOngoing = (notif.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE)) != 0,
            isGroupSummary = (notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
        )
    }.getOrNull()
}
