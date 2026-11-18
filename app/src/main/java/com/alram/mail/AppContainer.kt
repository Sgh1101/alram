package com.alram.mail

import android.app.Application
import androidx.room.Room
import com.alram.mail.agent.AgentDispatcher
import com.alram.mail.agent.AgentSender
import com.alram.mail.data.AlramDb
import com.alram.mail.data.AppCatalog
import com.alram.mail.data.SecretStore
import com.alram.mail.data.SettingsRepository
import com.alram.mail.engine.CaptureProcessor
import com.alram.mail.engine.Dispatcher
import com.alram.mail.mail.SmtpSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 의존성 주입 라이브러리 없이 앱 전역 객체를 한곳에서 만든다. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AlramDb = Room.databaseBuilder(app, AlramDb::class.java, "alram.db")
        .addMigrations(AlramDb.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
    val settings = SettingsRepository(app)
    val secrets = SecretStore(app)
    val sender = SmtpSender()
    val catalog = AppCatalog(app, db.appRules())
    val dispatcher = Dispatcher(app, db, settings, secrets, sender, scope)
    val agentToken = SecretStore(app, SecretStore.AGENT_PREF)
    val agent = AgentDispatcher(app, db, settings, agentToken, AgentSender(), scope)
    val processor = CaptureProcessor(app, db, settings, dispatcher, agent)
}
