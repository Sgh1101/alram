package com.alram.mail.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.alram.mail.ui.components.Divider
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SettingRow
import com.alram.mail.ui.components.SwitchRow
import com.alram.mail.ui.components.TimePickerDialog
import com.alram.mail.ui.formatMinute
import com.alram.mail.ui.openNotificationListenerSettings
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
        Text(
            "설정",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        )

        SectionHeader("계정")
        SettingRow(
            "Gmail 계정",
            subtitle = prefs.gmailAddress.ifBlank { "연결되지 않음" },
            onClick = onOpenGmail,
        )
        SettingRow(
            "받는 주소",
            subtitle = core.recipients.joinToString(", ").ifBlank { "내 Gmail로 보내기" },
            onClick = { dialog = "recipients" },
        )
        SettingRow("설정 점검", subtitle = "알림 접근·배터리 등 필수 설정을 확인해요", onClick = onOpenSetup)

        SectionHeader("전달")
        SettingRow("기본 전달 방식", subtitle = modeSummary(core), onClick = { dialog = "mode" })
        SettingRow("묶음 간격", subtitle = "${core.batchMinutes}분마다", onClick = { dialog = "batch" })
        SettingRow("하루 요약 시각", subtitle = formatMinute(core.dailyMinute), onClick = { dialog = "daily" })
        SettingRow(
            "연속 알림 합치기",
            subtitle = "즉시 모드에서 ${core.burstSeconds}초 안에 오는 같은 대화는 한 통으로",
            onClick = { dialog = "burst" },
        )
        SwitchRow(
            "새로 설치한 앱도 전달",
            core.newAppsEnabled,
            { on -> setCore { it.copy(newAppsEnabled = on) } },
            subtitle = "끄면 앱 화면에서 직접 켠 앱만 전달돼요",
        )
        SettingRow(
            "방해금지 시간",
            subtitle = if (core.quiet.enabled) "${formatMinute(core.quiet.startMinute)} – ${formatMinute(core.quiet.endMinute)}" else "꺼짐",
            onClick = onOpenQuiet,
        )

        SectionHeader("필터")
        SwitchRow("진행 중 알림 제외", core.skipOngoing, { on -> setCore { it.copy(skipOngoing = on) } }, subtitle = "음악 재생, 다운로드, 통화 중 같은 알림")
        SwitchRow("묶음 요약 알림 제외", core.skipGroupSummary, { on -> setCore { it.copy(skipGroupSummary = on) } }, subtitle = "\"메시지 3개\"처럼 앱이 만든 요약 알림")
        SettingRow("같은 알림 중복 제거", subtitle = dedupeLabel(core.dedupeSeconds), onClick = { dialog = "dedupe" })

        SectionHeader("보안")
        SwitchRow("인증번호 가리기", core.maskOtp, { on -> setCore { it.copy(maskOtp = on) } }, subtitle = "모든 앱에 기본 적용 · 앱마다 따로 바꿀 수 있어요")
        SettingRow(
            "메일에 담을 내용",
            subtitle = if (core.defaultBodyMode == BodyMode.FULL) "제목 + 본문" else "제목만",
            onClick = { dialog = "body" },
        )

        SectionHeader("한도와 보관")
        SettingRow("하루 최대 메일 수", subtitle = "${core.dailyMailCap}통 (Gmail 개인 계정은 하루 약 500통까지)", onClick = { dialog = "cap" })
        SettingRow("기록 보관 기간", subtitle = "${prefs.retentionDays}일", onClick = { dialog = "retention" })

        SectionHeader("동작")
        SwitchRow(
            "항상 켜두기",
            prefs.keepAlive,
            { on -> setPrefs { it.copy(keepAlive = on) } },
            subtitle = "상단에 작은 상시 알림을 띄워 앱이 종료되는 걸 줄여요",
        )
        SettingRow(
            "테마",
            subtitle = when (prefs.theme) { ThemeMode.SYSTEM -> "시스템 설정 따르기"; ThemeMode.LIGHT -> "라이트"; ThemeMode.DARK -> "다크" },
            onClick = { dialog = "theme" },
        )
        SettingRow("알림 접근 권한 화면 열기", onClick = { openNotificationListenerSettings(context) })

        SectionHeader("정보")
        SettingRow("버전", subtitle = BuildConfig.VERSION_NAME)
        Divider()
        SettingRow("모든 기록 삭제", subtitle = "대기 중인 알림도 함께 지워져요", onClick = { dialog = "wipe" })
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
    DeliveryMode.BATCH -> "${core.batchMinutes}분마다 묶음"
    DeliveryMode.DAILY -> "매일 ${formatMinute(core.dailyMinute)} 요약"
}

private fun dedupeLabel(seconds: Int) = when {
    seconds <= 0 -> "사용 안 함"
    seconds < 60 -> "${seconds}초 안에 같은 알림은 한 번만"
    else -> "${seconds / 60}분 안에 같은 알림은 한 번만"
}

@Composable
private fun RecipientsDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("받는 주소") },
        text = {
            Column {
                Text(
                    "한 줄에 하나씩 적어 주세요. 비워 두면 내 Gmail 주소로 보냅니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 2,
                    maxLines = 5,
                    placeholder = { Text("me@gmail.com") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
