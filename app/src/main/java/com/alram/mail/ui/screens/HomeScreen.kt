package com.alram.mail.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alram.mail.AppContainer
import com.alram.mail.R
import com.alram.mail.core.DeliveryMode
import com.alram.mail.data.AppPrefs
import com.alram.mail.data.NotificationEntity
import com.alram.mail.data.Status
import com.alram.mail.ui.components.AppIcon
import com.alram.mail.ui.components.AppSwitch
import com.alram.mail.ui.components.Banner
import com.alram.mail.ui.components.BannerTone
import com.alram.mail.ui.components.EmptyState
import com.alram.mail.ui.components.GroupCard
import com.alram.mail.ui.components.LiveDot
import com.alram.mail.ui.components.Pill
import com.alram.mail.ui.components.RowDivider
import com.alram.mail.ui.components.SecondaryButton
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SettingRow
import com.alram.mail.ui.components.TextLink
import com.alram.mail.ui.components.groupItem
import com.alram.mail.ui.containerViewModel
import com.alram.mail.ui.formatMinute
import com.alram.mail.ui.formatTime
import com.alram.mail.ui.rememberSystemState
import com.alram.mail.ui.startOfToday
import com.alram.mail.ui.theme.AlramTheme
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
    val setupMissing = listOf(gmailReady, system.listenerEnabled, system.notificationsAllowed, system.batteryUnrestricted)
        .count { !it }
    val recipients = core.recipients.ifEmpty { listOf(prefs.gmailAddress) }.joinToString(", ")

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { HomeHeader() }
        item {
            HeroCard(
                running = running,
                paused = paused,
                pausedUntil = core.pausedUntil,
                gmailReady = gmailReady,
                from = prefs.gmailAddress,
                to = recipients,
                modeLabel = modeLabel(core.defaultMode, core.batchMinutes, core.dailyMinute),
                onToggle = vm::setMaster,
                onPause = vm::pauseFor,
                onPauseTomorrow = vm::pauseUntilTomorrowMorning,
                onResume = vm::resume,
                onConnect = onOpenSetup,
            )
        }
        if (setupMissing > 0) {
            item {
                Banner(
                    text = "설정이 ${setupMissing}개 남았어요. 마저 하면 알림이 끊기지 않아요.",
                    tone = BannerTone.Warn,
                    action = "설정하기",
                    onAction = onOpenSetup,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        dispatch.lastError?.let { err ->
            item { Banner(text = err, tone = BannerTone.Error, modifier = Modifier.padding(top = 12.dp)) }
        }
        dispatch.notice?.let { notice ->
            item { Banner(text = notice, tone = BannerTone.Info, modifier = Modifier.padding(top = 12.dp)) }
        }
        if (failed > 0) {
            item {
                Banner(
                    text = "전송에 실패한 알림이 ${failed}건 있어요.",
                    tone = BannerTone.Error,
                    action = "다시 시도",
                    onAction = vm::retryFailed,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        item {
            GroupCard(Modifier.padding(top = 12.dp)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 18.dp)) {
                    StatCell("$sentToday", "오늘 전달", Modifier.weight(1f))
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatCell("$pending", "대기 중", Modifier.weight(1f))
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatCell(
                        "$failed",
                        "실패",
                        Modifier.weight(1f),
                        color = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (pending > 0) {
                    RowDivider()
                    SettingRow(
                        title = "대기 중인 ${pending}건 지금 보내기",
                        icon = Icons.AutoMirrored.Rounded.Send,
                        onClick = vm::sendNow,
                    )
                }
            }
        }
        item {
            SectionHeader("최근 알림") { TextLink("전체 보기", onClick = onOpenHistory) }
        }
        if (recent.isEmpty()) {
            item {
                GroupCard {
                    EmptyState(
                        "아직 받은 알림이 없어요",
                        "다른 앱에 알림이 오면 여기에 나타나요.",
                        icon = Icons.Rounded.NotificationsNone,
                    )
                }
            }
        } else {
            itemsIndexed(recent, key = { _, n -> n.id }) { i, n ->
                Column(Modifier.groupItem(i, recent.size)) {
                    NotificationRow(n)
                    if (i < recent.size - 1) RowDivider(inset = 64.dp)
                }
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(30.dp)
        Spacer(Modifier.width(10.dp))
        Text("Alram Mail", style = MaterialTheme.typography.titleLarge)
    }
}

/** 앱 아이콘과 같은 그림(배경+전경 벡터)을 작게 잘라서 보여 준다. */
@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    // 아이콘 벡터는 108 단위 중 가운데 72 단위가 보이는 영역이다.
    val full = size * 1.5f
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(full))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(full))
    }
}

private fun modeLabel(mode: DeliveryMode, batchMinutes: Int, dailyMinute: Int) = when (mode) {
    DeliveryMode.INSTANT -> "즉시 전달"
    DeliveryMode.BATCH -> "${batchMinutes}분마다 모아서"
    DeliveryMode.DAILY -> "매일 ${formatMinute(dailyMinute)} 요약"
}

@Composable
private fun HeroCard(
    running: Boolean,
    paused: Boolean,
    pausedUntil: Long,
    gmailReady: Boolean,
    from: String,
    to: String,
    modeLabel: String,
    onToggle: (Boolean) -> Unit,
    onPause: (Int) -> Unit,
    onPauseTomorrow: () -> Unit,
    onResume: () -> Unit,
    onConnect: () -> Unit,
) {
    val x = AlramTheme.colors
    val cs = MaterialTheme.colorScheme
    val active = running && gmailReady
    val fg = if (active) x.onHero else cs.onSurface
    val muted = if (active) x.onHeroMuted else cs.onSurfaceVariant
    val background = if (active) {
        Modifier.background(Brush.linearGradient(listOf(x.heroStart, x.heroEnd)))
    } else {
        Modifier.background(x.card)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .then(background)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(
                active = active,
                color = when {
                    active -> Color.White
                    paused -> cs.tertiary
                    else -> cs.outline
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    !gmailReady -> "연결 필요"
                    active -> "실시간 전달 중 · $modeLabel"
                    paused -> "일시 중지"
                    else -> "꺼짐"
                },
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            AppSwitch(checked = running || paused, onCheckedChange = onToggle, onHero = active)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                !gmailReady -> "Gmail을 연결하면\n바로 시작돼요"
                active -> "알림을 메일로\n보내고 있어요"
                paused -> "${formatTime(pausedUntil)}까지\n잠시 쉬는 중"
                else -> "전달이\n꺼져 있어요"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = fg,
        )

        if (gmailReady) {
            Spacer(Modifier.height(16.dp))
            RouteLine("보내기", from, fg, muted)
            Spacer(Modifier.height(4.dp))
            RouteLine("받기", to, fg, muted)
        }

        when {
            !gmailReady -> {
                Spacer(Modifier.height(18.dp))
                SecondaryButton("Gmail 연결하기", onClick = onConnect)
            }
            active -> {
                Spacer(Modifier.height(18.dp))
                Text("잠시 쉬기", style = MaterialTheme.typography.labelSmall, color = muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroPill("30분") { onPause(30) }
                    HeroPill("1시간") { onPause(60) }
                    HeroPill("3시간") { onPause(180) }
                    HeroPill("내일 아침까지", onPauseTomorrow)
                }
            }
            paused -> {
                Spacer(Modifier.height(18.dp))
                SecondaryButton("지금 다시 시작", onClick = onResume)
            }
        }
    }
}

@Composable
private fun RouteLine(label: String, value: String, fg: Color, muted: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.width(44.dp))
        Text(
            value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NotificationRow(n: NotificationEntity, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(n.packageName, n.appLabel, size = 36.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.appLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when (n.status) {
                        Status.PENDING -> Pill("대기", MaterialTheme.colorScheme.primary, Modifier.padding(start = 6.dp))
                        Status.FAILED -> Pill("실패", MaterialTheme.colorScheme.error, Modifier.padding(start = 6.dp))
                    }
                }
                Text(
                    formatTime(n.postedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                n.title.ifBlank { "(제목 없음)" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (n.text.isNotBlank()) {
                Text(
                    n.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
