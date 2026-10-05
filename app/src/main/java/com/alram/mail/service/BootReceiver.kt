package com.alram.mail.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alram.mail.container
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        c.dispatcher.start()
        val pending = goAsync()
        c.scope.launch {
            try {
                if (c.settings.current().keepAlive) KeepAliveService.start(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
