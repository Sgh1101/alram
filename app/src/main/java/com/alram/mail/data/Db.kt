package com.alram.mail.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.alram.mail.core.AppRule
import com.alram.mail.core.BodyMode
import com.alram.mail.core.CapturedNotification
import com.alram.mail.core.DeliveryMode
import com.alram.mail.core.QueuedItem
import kotlinx.coroutines.flow.Flow

object Status {
    const val PENDING = "PENDING"
    const val SENT = "SENT"
    const val FAILED = "FAILED"
}

@Entity(
    tableName = "notifications",
    indices = [Index("status", "dueAt"), Index("fingerprint", "createdAt"), Index("packageName")],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sbnKey: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val subText: String,
    val conversation: String,
    val category: String,
    val postedAt: Long,
    val createdAt: Long,
    val mode: String,
    val dueAt: Long,
    /** 쉼표로 구분한 수신 주소. 비어 있으면 전역 설정을 따른다. */
    val recipients: String,
    val fingerprint: String,
    val status: String = Status.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val sentAt: Long? = null,
    val mailId: Long? = null,
) {
    fun toCaptured() = CapturedNotification(
        key = sbnKey, packageName = packageName, appLabel = appLabel, title = title, text = text,
        subText = subText, conversation = conversation, postedAt = postedAt, category = category,
    )

    fun toQueued() = QueuedItem(
        id = id,
        notification = toCaptured(),
        mode = runCatching { DeliveryMode.valueOf(mode) }.getOrDefault(DeliveryMode.INSTANT),
        dueAt = dueAt,
        recipients = recipients.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        attempts = attempts,
    )
}

@Entity(tableName = "app_rules")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
    val enabled: Boolean? = null,
    val mode: String? = null,
    val bodyMode: String? = null,
    val maskOtp: Boolean? = null,
    /** 줄바꿈으로 구분 */
    val includeKeywords: String = "",
    val excludeKeywords: String = "",
    /** 쉼표로 구분 */
    val recipients: String = "",
    val lastSeenAt: Long = 0,
    val seenCount: Int = 0,
) {
    fun toRule() = AppRule(
        packageName = packageName,
        enabled = enabled,
        mode = mode?.let { runCatching { DeliveryMode.valueOf(it) }.getOrNull() },
        bodyMode = bodyMode?.let { runCatching { BodyMode.valueOf(it) }.getOrNull() },
        maskOtp = maskOtp,
        includeKeywords = includeKeywords.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
        excludeKeywords = excludeKeywords.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
        recipients = recipients.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    )
}

@Entity(tableName = "mails")
data class MailLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sentAt: Long,
    val subject: String,
    val itemCount: Int,
    val recipients: String,
)

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(e: NotificationEntity): Long

    @Query("SELECT MIN(dueAt) FROM notifications WHERE status = 'PENDING'")
    suspend fun nextDueAt(): Long?

    @Query("SELECT * FROM notifications WHERE status = 'PENDING' AND dueAt <= :now ORDER BY postedAt, id LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<NotificationEntity>

    @Query("UPDATE notifications SET status = 'SENT', sentAt = :now, mailId = :mailId, lastError = NULL WHERE id IN (:ids)")
    suspend fun markSent(ids: List<Long>, now: Long, mailId: Long)

    @Query("UPDATE notifications SET attempts = attempts + 1, dueAt = :dueAt, lastError = :error WHERE id IN (:ids)")
    suspend fun markRetry(ids: List<Long>, dueAt: Long, error: String)

    @Query("UPDATE notifications SET status = 'FAILED' WHERE status = 'PENDING' AND attempts >= :max")
    suspend fun failExhausted(max: Int)

    @Query("SELECT COUNT(*) FROM notifications WHERE fingerprint = :fp AND createdAt >= :since")
    suspend fun countFingerprintSince(fp: String, since: Long): Int

    @Query("UPDATE notifications SET status = 'PENDING', attempts = 0, dueAt = :now, lastError = NULL WHERE id = :id")
    suspend fun retry(id: Long, now: Long)

    @Query("UPDATE notifications SET status = 'PENDING', attempts = 0, dueAt = :now, lastError = NULL WHERE status = 'FAILED'")
    suspend fun retryAllFailed(now: Long)

    @Query("UPDATE notifications SET dueAt = :now WHERE status = 'PENDING'")
    suspend fun sendAllPendingNow(now: Long)

    /** 이미 한 번 이상 실패한(백오프 중인) 대기 항목만 즉시 재시도 대상으로 돌린다. */
    @Query("UPDATE notifications SET dueAt = :now WHERE status = 'PENDING' AND attempts > 0 AND dueAt > :now")
    suspend fun retryPendingNow(now: Long)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notifications WHERE status = :status")
    suspend fun deleteByStatus(status: String)

    @Query("DELETE FROM notifications WHERE status != 'PENDING' AND createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("SELECT * FROM notifications WHERE status = :status ORDER BY postedAt DESC LIMIT :limit")
    fun observeByStatus(status: String, limit: Int): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE status = :status")
    fun observeCount(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE status = 'SENT' AND sentAt >= :since")
    fun observeSentSince(since: Long): Flow<Int>
}

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    suspend fun get(pkg: String): AppRuleEntity?

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    fun observe(pkg: String): Flow<AppRuleEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(e: AppRuleEntity)

    @Upsert
    suspend fun upsert(e: AppRuleEntity)

    @Query("UPDATE app_rules SET lastSeenAt = :now, seenCount = seenCount + 1, label = :label WHERE packageName = :pkg")
    suspend fun touch(pkg: String, label: String, now: Long)

    @Query("UPDATE app_rules SET enabled = :enabled")
    suspend fun setAllEnabled(enabled: Boolean)

    @Query("UPDATE app_rules SET enabled = :enabled WHERE isSystem = 1")
    suspend fun setSystemEnabled(enabled: Boolean)

    @Query("UPDATE app_rules SET enabled = :enabled WHERE packageName IN (:pkgs)")
    suspend fun setEnabled(pkgs: List<String>, enabled: Boolean)
}

@Dao
interface MailLogDao {
    @Insert
    suspend fun insert(e: MailLogEntity): Long

    @Query("SELECT COUNT(*) FROM mails WHERE sentAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM mails WHERE sentAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("DELETE FROM mails WHERE sentAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Database(
    entities = [NotificationEntity::class, AppRuleEntity::class, MailLogEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AlramDb : RoomDatabase() {
    abstract fun notifications(): NotificationDao
    abstract fun appRules(): AppRuleDao
    abstract fun mails(): MailLogDao
}
