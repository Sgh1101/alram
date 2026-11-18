package com.alram.mail

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alram.mail.data.AgentQueueEntity
import com.alram.mail.data.AlramDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v1(에이전트 기능 이전) DB 가 기록과 앱 설정을 잃지 않고 v2 로 올라가는지 확인한다. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun v1_to_v2_keeps_data() {
        val name = "migration-test.db"
        app.deleteDatabase(name)
        // v1 스키마 그대로 만든다. (destructive fallback 없이 열어서 마이그레이션이 실제로 맞는지 검사)
        SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(name), null).use { db ->
            db.execSQL(
                "CREATE TABLE `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sbnKey` TEXT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                    "`subText` TEXT NOT NULL, `conversation` TEXT NOT NULL, `category` TEXT NOT NULL, `postedAt` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `mode` TEXT NOT NULL, `dueAt` INTEGER NOT NULL, `recipients` TEXT NOT NULL, " +
                    "`fingerprint` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT, " +
                    "`sentAt` INTEGER, `mailId` INTEGER)",
            )
            db.execSQL("CREATE INDEX `index_notifications_status_dueAt` ON `notifications` (`status`, `dueAt`)")
            db.execSQL("CREATE INDEX `index_notifications_fingerprint_createdAt` ON `notifications` (`fingerprint`, `createdAt`)")
            db.execSQL("CREATE INDEX `index_notifications_packageName` ON `notifications` (`packageName`)")
            db.execSQL(
                "CREATE TABLE `app_rules` (`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, `isSystem` INTEGER NOT NULL, " +
                    "`enabled` INTEGER, `mode` TEXT, `bodyMode` TEXT, `maskOtp` INTEGER, `includeKeywords` TEXT NOT NULL, " +
                    "`excludeKeywords` TEXT NOT NULL, `recipients` TEXT NOT NULL, `lastSeenAt` INTEGER NOT NULL, " +
                    "`seenCount` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
            )
            db.execSQL(
                "CREATE TABLE `mails` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sentAt` INTEGER NOT NULL, " +
                    "`subject` TEXT NOT NULL, `itemCount` INTEGER NOT NULL, `recipients` TEXT NOT NULL)",
            )
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO app_rules VALUES ('com.kakao.talk','카카오톡',0,1,'BATCH',NULL,NULL,'','광고','',1,3)")
            db.execSQL(
                "INSERT INTO notifications (sbnKey,packageName,appLabel,title,text,subText,conversation,category,postedAt," +
                    "createdAt,mode,dueAt,recipients,fingerprint,status,attempts) " +
                    "VALUES ('k','com.kakao.talk','카카오톡','홍길동','안녕','','','',1,1,'INSTANT',1,'','fp','PENDING',0)",
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(app, AlramDb::class.java, name)
            .addMigrations(AlramDb.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            val rule = room.appRules().get("com.kakao.talk")!!
            assertEquals("BATCH", rule.mode)
            assertEquals("광고", rule.excludeKeywords)
            assertNull(rule.destination)
            assertEquals(1, room.notifications().due(Long.MAX_VALUE, 10).size)

            val id = room.agentQueue().insert(
                AgentQueueEntity(
                    packageName = "p", appLabel = "앱", title = "t", text = "x", subText = "", conversation = "",
                    category = "", postedAt = 1, createdAt = 1, fingerprint = "f", dueAt = 0,
                ),
            )
            assertEquals(listOf(id), room.agentQueue().due(10, 10).map { it.id })
        }
        room.close()
    }
}
