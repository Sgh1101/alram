package com.alram.mail.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alram.mail.container

class FlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val retention = c.settings.current().retentionDays
        val cutoff = System.currentTimeMillis() - retention * 24L * 3600_000L
        c.db.notifications().deleteOlderThan(cutoff)
        c.db.mails().deleteOlderThan(cutoff)
        c.dispatcher.poke()
        return Result.success()
    }
}
