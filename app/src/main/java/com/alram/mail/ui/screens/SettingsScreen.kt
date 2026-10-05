package com.alram.mail.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.BuildConfig
import com.alram.mail.core.BodyMode
import com.alram.mail.core.DeliveryMode
import com.alram.mail.core.GlobalSettings
import com.alram.mail.data.AppPrefs
import com.alram.mail.data.ThemeMode
import com.alram.mail.ui.LocalContainer
import com.alram.mail.ui.components.ChoiceDialog
import com.alram.mail.ui.components.ConfirmDialog
import com.alram.mail.ui.components.FormField
import com.alram.mail.ui.components.GroupCard
import com.alram.mail.ui.components.IconRowInset
import com.alram.mail.ui.components.RowDivider
import com.alram.mail.ui.components.ScreenTitle
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SettingRow
import com.alram.mail.ui.components.SwitchRow
import com.alram.mail.ui.components.TimePickerDialog
import com.alram.mail.ui.formatMinute
import com.alram.mail.ui.openNotificationListenerSettings
import com.alram.mail.ui.theme.AlramTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onOpenGmail: () -> Unit, onOpenQuiet: () -> Unit, onOpenSetup: () -> Unit) {
    val c = LocalContainer.current
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val core = prefs.core
    var dialog by remember { mutableStateOf<String?>(null) }

    fun setCore(t: (GlobalSettings) -> GlobalSettings) {
        scope.launch { c.settings.updateCore(t) }
    }
    fun setPrefs(t: (AppPrefs) -> AppPrefs) {
        scope.launch { c.settings.update(t) }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        ScreenTitle("설정")

        GroupCard {
            SettingRow(
                title = prefs.gmailAddress.ifBlank { "Gmail 연결하기" },
                subtitle = if (prefs.gmailAddress.isBlank()) "알림을 보낼 계정을 연결해 주세요" else "보내는 계정",
                icon = Icons.Rounded.AccountCircle,
                onClick = onOpenGmail,
            )
            RowDivider(inset = IconRowInset)
            SettingRow(
                title = "받는 주소",
                subtitle = core.recipients.joinToString(", ").ifBlank { "내 Gmail로 보내기" },
                icon = Icons.Rounded.AlternateEmail,
                onClick = { dialog = "recipients" },
            )
            RowDivider(inset = IconRowInset)
            SettingRow(
                title = "설정 점검",
                subtitle = "알림 접근·배터리 같은 필수 설정 확인",
                icon = Icons.Rounded.CheckCircle,
                onClick = onOpenSetup,
            )
        }

        SectionHeader("전달")
        GroupCard {
            SettingRow("기본 전달 방식", icon = Icons.AutoMirrored.Rounded.Send, value = modeSummary(core), onClick = { dialog = "mode" })
            RowDivider(inset = IconRowInset)
            SettingRow("묶음 간격", icon = Icons.Rounded.Timer, value = minutesLabel(core.batchMinutes), onClick = { dialog = "batch" })
            RowDivider(inset = IconRowInset)
            SettingRow("하루 요약 시각", icon = Icons.Rounded.Schedule, value = formatMinute(core.dailyMinute), onClick = { dialog = "daily" })
            RowDivider(inset = IconRowInset)
            SettingRow(
                "연속 알림 합치기",
                subtitle = "같은 대화에서 연달아 오면 한 통으로",
                icon = Icons.Rounded.FlashOn,
                value = if (core.burstSeconds <= 0) "안 함" else "${core.burstSeconds}초",
                onClick = { dialog = "burst" },
            )
            RowDivider(inset = IconRowInset)
            SwitchRow(
                "새로 설치한 앱도 전달",
                core.newAppsEnabled,
                { on -> setCore { it.copy(newAppsEnabled = on) } },
                icon = Icons.Rounded.Apps,
            )
            RowDivider(inset = IconRowInset)
            SettingRow(
                "방해금지 시간",
                icon = Icons.Rounded.NightsStay,
                value = if (core.quiet.enabled) "${formatMinute(core.quiet.startMinute)}–${formatMinute(core.quiet.endMinute)}" else "꺼짐",
                onClick = onOpenQuiet,
            )
        }

        SectionHeader("거르기")
        GroupCard {
            SwitchRow(
                "진행 중 알림 제외",
                core.skipOngoing,
                { on -> setCore { it.copy(skipOngoing = on) } },
                subtitle = "음악 재생, 다운로드, 통화 중",
                icon = Icons.Rounded.Tune,
            )
            RowDivider(inset = IconRowInset)
            SwitchRow(
                "요약 알림 제외",
                core.skipGroupSummary,
                { on -> setCore { it.copy(skipGroupSummary = on) } },
                subtitle = "\"메시지 3개\"처럼 앱이 묶어 보여 주는 알림",
                icon = Icons.Rounded.Inbox,
            )
            RowDivider(inset = IconRowInset)
            SettingRow("같은 알림 중복 제거", icon = Icons.Rounded.FilterList, value = dedupeLabel(core.dedupeSeconds), onClick = { dialog = "dedupe" })
        }

        SectionHeader("보안")
        GroupCard {
            SwitchRow(
                "인증번호 가리기",
                core.maskOtp,
                { on -> setCore { it.copy(maskOtp = on) } },
                subtitle = "모든 앱 기본값 · 앱마다 바꿀 수 있어요",
                icon = Icons.Rounded.Lock,
            )
            RowDivider(inset = IconRowInset)
            SettingRow(
                "메일에 담을 내용",
                icon = Icons.Rounded.Security,
                value = if (core.defaultBodyMode == BodyMode.FULL) "제목 + 본문" else "제목만",
                onClick = { dialog = "body" },
            )
        }

        SectionHeader("한도와 보관")
        GroupCard {
            SettingRow(
                "하루 최대 메일 수",
                subtitle = "Gmail 개인 계정은 하루 약 500통까지",
                icon = Icons.Rounded.Speed,
                value = "${core.dailyMailCap}통",
                onClick = { dialog = "cap" },
            )
            RowDivider(inset = IconRowInset)
            SettingRow("기록 보관 기간", icon = Icons.Rounded.Storage, value = "${prefs.retentionDays}일", onClick = { dialog = "retention" })
        }

        SectionHeader("동작")
        GroupCard {
            SwitchRow(
                "항상 켜두기",
                prefs.keepAlive,
                { on -> setPrefs { it.copy(keepAlive = on) } },
                subtitle = "상단에 작은 알림을 띄워 앱이 종료되지 않게 해요",
                icon = Icons.Rounded.BatteryChargingFull,
            )
            RowDivider(inset = IconRowInset)
            SettingRow(
                "테마",
                icon = Icons.Rounded.Palette,
                value = when (prefs.theme) {
                    ThemeMode.SYSTEM -> "시스템"
                    ThemeMode.LIGHT -> "라이트"
                    ThemeMode.DARK -> "다크"
                },
                onClick = { dialog = "theme" },
            )
            RowDivider(inset = IconRowInset)
            SettingRow(
                "알림 접근 권한",
                icon = Icons.Rounded.Notifications,
                onClick = { openNotificationListenerSettings(context) },
            )
        }

        SectionHeader("정보")
        GroupCard {
            SettingRow("버전", icon = Icons.Rounded.Info, value = BuildConfig.VERSION_NAME)
            RowDivider(inset = IconRowInset)
            SettingRow(
                "모든 기록 삭제",
                icon = Icons.Rounded.DeleteOutline,
                titleColor = MaterialTheme.colorScheme.error,
                iconTint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.errorContainer,
                onClick = { dialog = "wipe" },
                showChevron = false,
            )
        }
    }

    when (dialog) {
        "mode" -> ChoiceDialog(
            "기본 전달 방식",
            listOf(DeliveryMode.INSTANT to "즉시", DeliveryMode.BATCH to "묶음 (${core.batchMinutes}분마다)", DeliveryMode.DAILY to "하루 요약"),
            core.defaultMode, { m -> setCore { it.copy(defaultMode = m) } }, { dialog = null },
            message = "앱마다 따로 정하지 않은 경우에 적용돼요.",
        )
        "batch" -> ChoiceDialog(
            "묶음 간격",
            listOf(5, 10, 15, 30, 60, 120).map { it to if (it >= 60) "${it / 60}시간" else "${it}분" },
            core.batchMinutes, { m -> setCore { it.copy(batchMinutes = m) } }, { dialog = null },
        )
        "burst" -> ChoiceDialog(
            "연속 알림 합치기",
            listOf(0 to "합치지 않기 (가장 빠름)", 2 to "2초", 4 to "4초", 8 to "8초", 15 to "15초"),
            core.burstSeconds, { m -> setCore { it.copy(burstSeconds = m) } }, { dialog = null },
            message = "길수록 메일 수가 줄고, 짧을수록 더 빨리 도착해요.",
        )
        "daily" -> TimePickerDialog("하루 요약 시각", core.dailyMinute, { m -> setCore { it.copy(dailyMinute = m) } }) { dialog = null }
        "dedupe" -> ChoiceDialog(
            "같은 알림 중복 제거",
            listOf(0 to "사용 안 함", 30 to "30초", 60 to "1분", 300 to "5분", 1800 to "30분"),
            core.dedupeSeconds, { m -> setCore { it.copy(dedupeSeconds = m) } }, { dialog = null },
        )
        "body" -> ChoiceDialog(
            "메일에 담을 내용",
            listOf(BodyMode.FULL to "제목 + 본문", BodyMode.TITLE_ONLY to "제목만"),
            core.defaultBodyMode, { m -> setCore { it.copy(defaultBodyMode = m) } }, { dialog = null },
        )
        "cap" -> ChoiceDialog(
            "하루 최대 메일 수",
            listOf(100, 200, 300, 400, 480).map { it to "${it}통" },
            core.dailyMailCap, { m -> setCore { it.copy(dailyMailCap = m) } }, { dialog = null },
            message = "한도에 가까워지면 즉시 알림이 묶음 메일로 합쳐져서 한도를 넘지 않게 해요.",
        )
        "retention" -> ChoiceDialog(
            "기록 보관 기간",
            listOf(7, 30, 90, 365).map { it to "${it}일" },
            prefs.retentionDays, { m -> setPrefs { it.copy(retentionDays = m) } }, { dialog = null },
        )
        "theme" -> ChoiceDialog(
            "테마",
            listOf(ThemeMode.SYSTEM to "시스템 설정 따르기", ThemeMode.LIGHT to "라이트", ThemeMode.DARK to "다크"),
            prefs.theme, { m -> setPrefs { it.copy(theme = m) } }, { dialog = null },
        )
        "recipients" -> RecipientsDialog(
            initial = core.recipients.joinToString("\n"),
            onSave = { text ->
                val list = text.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                setCore { it.copy(recipients = list) }
            },
            onDismiss = { dialog = null },
        )
        "wipe" -> ConfirmDialog(
            title = "모든 기록 삭제",
            message = "발송 기록과 아직 보내지 않은 알림이 모두 지워집니다.",
            confirmLabel = "삭제",
            destructive = true,
            onConfirm = { scope.launch { c.db.notifications().deleteAll() } },
            onDismiss = { dialog = null },
        )
    }
}

private fun modeSummary(core: GlobalSettings) = when (core.defaultMode) {
    DeliveryMode.INSTANT -> "즉시"
    DeliveryMode.BATCH -> "${core.batchMinutes}분 묶음"
    DeliveryMode.DAILY -> "하루 요약"
}

private fun minutesLabel(m: Int) = if (m >= 60 && m % 60 == 0) "${m / 60}시간" else "${m}분"

private fun dedupeLabel(seconds: Int) = when {
    seconds <= 0 -> "안 함"
    seconds < 60 -> "${seconds}초"
    else -> "${seconds / 60}분"
}

@Composable
private fun RecipientsDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AlramTheme.colors.card,
        title = { Text("받는 주소", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "한 줄에 하나씩 적어 주세요. 비워 두면 내 Gmail 주소로 보내요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 5,
                    placeholder = "me@gmail.com",
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
