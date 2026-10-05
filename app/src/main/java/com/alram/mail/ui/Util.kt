package com.alram.mail.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alram.mail.AppContainer
import com.alram.mail.service.NotificationCaptureService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LocalContainer = compositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val c = LocalContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFmt = DateTimeFormatter.ofPattern("M/d HH:mm")

fun formatTime(ms: Long): String {
    if (ms <= 0) return "-"
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    return if (t.toLocalDate() == LocalDate.now(zone)) t.format(timeFmt) else t.format(dateTimeFmt)
}

fun formatMinute(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

fun startOfToday(): Long {
    val zone = ZoneId.systemDefault()
    return LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
}

/** 권한/설정 상태. 화면이 다시 보일 때마다 갱신된다. */
data class SystemState(
    val listenerEnabled: Boolean,
    val notificationsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
)

fun readSystemState(context: Context): SystemState {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return SystemState(
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName),
    )
}

@Composable
fun rememberSystemState(): SystemState {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    // tick 이 바뀔 때마다 다시 읽는다.
    return remember(tick) { readSystemState(context) }
}

fun openNotificationListenerSettings(context: Context) {
    val component = android.content.ComponentName(context, NotificationCaptureService::class.java)
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        if (Build.VERSION.SDK_INT >= 30) context.startActivity(detail) else context.startActivity(general)
    }.onFailure { runCatching { context.startActivity(general) } }
}

fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun requestIgnoreBatteryOptimizations(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun ProvideContainer(container: AppContainer, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContainer provides container, content = content)
}

private fun isHangul(c: Char): Boolean =
    c in '가'..'힣' || c in 'ᄀ'..'ᇿ' || c in '㄰'..'㆏'

private fun isAsciiAlnum(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

/**
 * 한글 문장이 단어 중간에서 줄바꿈되지 않게(CSS 의 keep-all) 한 단어 안의 글자 사이에
 * 보이지 않는 WORD JOINER(U+2060)를 넣는다. 안드로이드 버전·언어 설정과 상관없이 띄어쓰기에서만 줄이 바뀐다.
 * 이모지 등 다른 문자에는 손대지 않는다.
 */
fun String.keepAll(): String {
    if (length < 2) return this
    val sb = StringBuilder(length + length / 2)
    for (i in indices) {
        val c = this[i]
        sb.append(c)
        if (i == lastIndex) break
        val n = this[i + 1]
        val joinable = (isHangul(c) || isAsciiAlnum(c)) && (isHangul(n) || isAsciiAlnum(n))
        if (joinable && (isHangul(c) || isHangul(n))) sb.append('⁠')
    }
    return sb.toString()
}
