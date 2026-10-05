package com.alram.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alram.mail.AppContainer
import com.alram.mail.core.DeliveryMode
import com.alram.mail.data.AppPrefs
import com.alram.mail.data.NotificationEntity
import com.alram.mail.data.Status
import com.alram.mail.engine.DispatchState
import com.alram.mail.ui.LocalContainer
import com.alram.mail.ui.components.Divider
import com.alram.mail.ui.components.EmptyState
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.StatBlock
import com.alram.mail.ui.containerViewModel
import com.alram.mail.ui.formatMinute
import com.alram.mail.ui.formatTime
import com.alram.mail.ui.rememberSystemState
import com.alram.mail.ui.startOfToday
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HomeVm(private val c: AppContainer) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val prefs = c.settings.prefs.state(AppPrefs())
    val pending = c.db.notifications().observeCount(Status.PENDING).state(0)
    val failed = c.db.notifications().observeCount(Status.FAILED).state(0)
    val sentToday = c.db.notifications().observeSentSince(startOfToday()).state(0)
    val recent = c.db.notifications().observeRecent(6).state(emptyList())
    val dispatch = c.dispatcher.state

    fun setMaster(on: Boolean) = viewModelScope.launch {
        c.settings.updateCore { it.copy(masterEnabled = on, pausedUntil = 0L) }
        if (on) c.dispatcher.poke()
    }

    fun pauseFor(minutes: Int) = viewModelScope.launch {
        c.settings.updateCore { it.copy(pausedUntil = System.currentTimeMillis() + minutes * 60_000L) }
    }

    fun pauseUntilTomorrowMorning() = viewModelScope.launch {
        val zone = ZoneId.systemDefault()
        val t = LocalDate.now(zone).plusDays(1).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        c.settings.updateCore { it.copy(pausedUntil = t) }
    }

    fun resume() = viewModelScope.launch {
        c.settings.updateCore { it.copy(pausedUntil = 0L) }
    }

    fun retryFailed() = viewModelScope.launch {
        c.db.notifications().retryAllFailed(System.currentTimeMillis())
        c.dispatcher.poke()
    }

    fun sendNow() = viewModelScope.launch {
        c.db.notifications().sendAllPendingNow(System.currentTimeMillis())
        c.dispatcher.poke()
    }

    fun hasPassword(): Boolean = c.secrets.has()
}

@Composable
fun HomeScreen(onOpenSetup: () -> Unit, onOpenHistory: () -> Unit) {
    val vm = containerViewModel { HomeVm(it) }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val sentToday by vm.sentToday.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val dispatch by vm.dispatch.collectAsStateWithLifecycle()
    val system = rememberSystemState()

    val now = System.currentTimeMillis()
    val core = prefs.core
    val paused = core.masterEnabled && now < core.pausedUntil
    val running = core.masterEnabled && !paused
    val gmailReady = prefs.gmailAddress.isNotBlank() && remember(prefs.gmailAddress) { vm.hasPassword() }
    val setupMissing = listOf(gmailReady, system.listenerEnabled, system.notificationsAllowed, system.batteryUnrestricted).count { !it }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Alram Mail",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            )
        }
        item {
            StatusCard(
                running = running,
                paused = paused,
                pausedUntil = core.pausedUntil,
                subtitle = when {
                    !gmailReady -> "Gmail을 연결하면 전달이 시작돼요"
                    else -> "${prefs.gmailAddress} · ${modeLabel(core.defaultMode, core.batchMinutes, core.dailyMinute)}"
                },
                onToggle = vm::setMaster,
                onPause = vm::pauseFor,
                onPauseTomorrow = vm::pauseUntilTomorrowMorning,
                onResume = vm::resume,
            )
        }
        if (setupMissing > 0) {
            item {
                Banner(
                    text = "설정이 ${setupMissing}개 남았어요. 완료해야 알림이 끊기지 않고 전달돼요.",
                    action = "설정하기",
                    onAction = onOpenSetup,
                    tone = BannerTone.Warn,
                )
            }
        }
        dispatch.lastError?.let { err ->
            item { Banner(text = err, action = null, onAction = {}, tone = BannerTone.Error) }
        }
        if (failed > 0) {
            item {
                Banner(
                    text = "전송에 실패한 알림이 ${failed}건 있어요.",
                    action = "다시 시도",
                    onAction = vm::retryFailed,
                    tone = BannerTone.Error,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                StatBlock("$sentToday", "오늘 전달")
                StatBlock("$pending", "대기 중")
                StatBlock("$failed", "실패", emphasize = failed > 0)
            }
        }
        if (pending > 0) {
            item {
                TextButton(onClick = vm::sendNow, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("대기 중인 ${pending}건 지금 보내기")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("최근 알림", Modifier.weight(1f))
                TextButton(onClick = onOpenHistory) { Text("전체 보기") }
            }
        }
        if (recent.isEmpty()) {
            item { EmptyState("아직 받은 알림이 없어요", "다른 앱에 알림이 오면 여기에 나타납니다.") }
        } else {
            items(recent, key = { it.id }) { n ->
                NotificationRow(n)
                Divider(Modifier.padding(start = 72.dp))
            }
        }
    }
}

private fun modeLabel(mode: DeliveryMode, batchMinutes: Int, dailyMinute: Int) = when (mode) {
    DeliveryMode.INSTANT -> "즉시 전달"
    DeliveryMode.BATCH -> "${batchMinutes}분마다 모아서"
    DeliveryMode.DAILY -> "매일 ${formatMinute(dailyMinute)} 요약"
}

@Composable
private fun StatusCard(
    running: Boolean,
    paused: Boolean,
    pausedUntil: Long,
    subtitle: String,
    onToggle: (Boolean) -> Unit,
    onPause: (Int) -> Unit,
    onPauseTomorrow: () -> Unit,
    onResume: () -> Unit,
) {
    val container = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = if (running) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = onContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            running -> "알림을 전달하고 있어요"
                            paused -> "${formatTime(pausedUntil)}까지 쉬는 중"
                            else -> "전달이 꺼져 있어요"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.75f))
                }
                Switch(checked = running || paused, onCheckedChange = onToggle)
            }
            if (running) {
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onPause(30) }, label = { Text("30분 쉬기") })
                    AssistChip(onClick = { onPause(60) }, label = { Text("1시간") })
                    AssistChip(onClick = { onPause(180) }, label = { Text("3시간") })
                    AssistChip(onClick = onPauseTomorrow, label = { Text("내일 아침까지") })
                }
            } else if (paused) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onResume) { Text("지금 다시 시작") }
            }
        }
    }
}

enum class BannerTone { Warn, Error }

@Composable
fun Banner(text: String, action: String?, onAction: () -> Unit, tone: BannerTone) {
    val bg = if (tone == BannerTone.Error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (tone == BannerTone.Error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        contentColor = fg,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            if (action != null) TextButton(onClick = onAction) { Text(action, color = fg, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun NotificationRow(n: NotificationEntity, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        com.alram.mail.ui.components.AppIcon(n.packageName, n.appLabel, size = 36.dp)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n.appLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(formatTime(n.postedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(
                n.title.ifBlank { "(제목 없음)" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (n.text.isNotBlank()) {
                Text(
                    n.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.padding(start = 8.dp))
        val dot = when (n.status) {
            Status.SENT -> MaterialTheme.colorScheme.primary
            Status.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        }
        Spacer(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(dot, androidx.compose.foundation.shape.CircleShape),
        )
    }
}
