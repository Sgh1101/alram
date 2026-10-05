package com.alram.mail.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alram.mail.core.BodyMode
import com.alram.mail.core.DeliveryMode
import com.alram.mail.core.GlobalSettings
import com.alram.mail.core.QuietHours
import com.alram.mail.core.QuietPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppPrefs(
    val core: GlobalSettings = GlobalSettings(),
    val gmailAddress: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val keepAlive: Boolean = false,
    val retentionDays: Int = 30,
    val setupSeen: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val master = booleanPreferencesKey("master")
        val pausedUntil = longPreferencesKey("pausedUntil")
        val newApps = booleanPreferencesKey("newAppsEnabled")
        val mode = stringPreferencesKey("defaultMode")
        val body = stringPreferencesKey("defaultBody")
        val otp = booleanPreferencesKey("maskOtp")
        val batch = intPreferencesKey("batchMinutes")
        val daily = intPreferencesKey("dailyMinute")
        val burst = intPreferencesKey("burstSeconds")
        val dedupe = intPreferencesKey("dedupeSeconds")
        val ongoing = booleanPreferencesKey("skipOngoing")
        val summary = booleanPreferencesKey("skipSummary")
        val qEnabled = booleanPreferencesKey("quietEnabled")
        val qStart = intPreferencesKey("quietStart")
        val qEnd = intPreferencesKey("quietEnd")
        val qDays = stringPreferencesKey("quietDays")
        val qPolicy = stringPreferencesKey("quietPolicy")
        val recipients = stringPreferencesKey("recipients")
        val cap = intPreferencesKey("dailyMailCap")
        val perMail = intPreferencesKey("maxItemsPerMail")
        val gmail = stringPreferencesKey("gmail")
        val theme = stringPreferencesKey("theme")
        val keepAlive = booleanPreferencesKey("keepAlive")
        val retention = intPreferencesKey("retentionDays")
        val setupSeen = booleanPreferencesKey("setupSeen")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { read(it) }

    suspend fun current(): AppPrefs = prefs.first()

    suspend fun update(transform: (AppPrefs) -> AppPrefs) {
        context.dataStore.edit { p -> write(p, transform(read(p))) }
    }

    suspend fun updateCore(transform: (GlobalSettings) -> GlobalSettings) =
        update { it.copy(core = transform(it.core)) }

    private fun read(p: Preferences): AppPrefs {
        val d = GlobalSettings()
        val dq = d.quiet
        val quiet = QuietHours(
            enabled = p[K.qEnabled] ?: dq.enabled,
            startMinute = p[K.qStart] ?: dq.startMinute,
            endMinute = p[K.qEnd] ?: dq.endMinute,
            days = p[K.qDays]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: dq.days,
            policy = enumOr(p[K.qPolicy], dq.policy),
        )
        val core = GlobalSettings(
            masterEnabled = p[K.master] ?: d.masterEnabled,
            pausedUntil = p[K.pausedUntil] ?: d.pausedUntil,
            newAppsEnabled = p[K.newApps] ?: d.newAppsEnabled,
            defaultMode = enumOr(p[K.mode], d.defaultMode),
            defaultBodyMode = enumOr(p[K.body], d.defaultBodyMode),
            maskOtp = p[K.otp] ?: d.maskOtp,
            batchMinutes = p[K.batch] ?: d.batchMinutes,
            dailyMinute = p[K.daily] ?: d.dailyMinute,
            burstSeconds = p[K.burst] ?: d.burstSeconds,
            dedupeSeconds = p[K.dedupe] ?: d.dedupeSeconds,
            skipOngoing = p[K.ongoing] ?: d.skipOngoing,
            skipGroupSummary = p[K.summary] ?: d.skipGroupSummary,
            quiet = quiet,
            recipients = p[K.recipients]?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.recipients,
            dailyMailCap = p[K.cap] ?: d.dailyMailCap,
            maxItemsPerMail = p[K.perMail] ?: d.maxItemsPerMail,
        )
        return AppPrefs(
            core = core,
            gmailAddress = p[K.gmail] ?: "",
            theme = enumOr(p[K.theme], ThemeMode.SYSTEM),
            keepAlive = p[K.keepAlive] ?: false,
            retentionDays = p[K.retention] ?: 30,
            setupSeen = p[K.setupSeen] ?: false,
        )
    }

    private fun write(p: MutablePreferences, v: AppPrefs) {
        val c = v.core
        p[K.master] = c.masterEnabled
        p[K.pausedUntil] = c.pausedUntil
        p[K.newApps] = c.newAppsEnabled
        p[K.mode] = c.defaultMode.name
        p[K.body] = c.defaultBodyMode.name
        p[K.otp] = c.maskOtp
        p[K.batch] = c.batchMinutes
        p[K.daily] = c.dailyMinute
        p[K.burst] = c.burstSeconds
        p[K.dedupe] = c.dedupeSeconds
        p[K.ongoing] = c.skipOngoing
        p[K.summary] = c.skipGroupSummary
        p[K.qEnabled] = c.quiet.enabled
        p[K.qStart] = c.quiet.startMinute
        p[K.qEnd] = c.quiet.endMinute
        p[K.qDays] = c.quiet.days.sorted().joinToString(",")
        p[K.qPolicy] = c.quiet.policy.name
        p[K.recipients] = c.recipients.joinToString("\n")
        p[K.cap] = c.dailyMailCap
        p[K.perMail] = c.maxItemsPerMail
        p[K.gmail] = v.gmailAddress
        p[K.theme] = v.theme.name
        p[K.keepAlive] = v.keepAlive
        p[K.retention] = v.retentionDays
        p[K.setupSeen] = v.setupSeen
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
