package com.alram.mail

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.alram.mail.service.FlushWorker
import java.util.concurrent.TimeUnit

class AlramApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.dispatcher.start()

        // 프로세스가 죽어도 15분마다 깨어나 밀린 알림을 확인하는 안전망.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "flush",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FlushWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as AlramApp).container
