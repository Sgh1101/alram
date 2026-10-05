package com.alram.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alram.mail.AppContainer
import com.alram.mail.core.BodyMode
import com.alram.mail.core.DeliveryMode
import com.alram.mail.core.LoopGuard
import com.alram.mail.data.AppPrefs
import com.alram.mail.data.AppRuleEntity
import com.alram.mail.ui.components.AppIcon
import com.alram.mail.ui.components.Banner
import com.alram.mail.ui.components.BannerTone
import com.alram.mail.ui.components.ChoicePill
import com.alram.mail.ui.components.FormField
import com.alram.mail.ui.components.GroupCard
import com.alram.mail.ui.components.Hint
import com.alram.mail.ui.components.RowDivider
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SubScreen
import com.alram.mail.ui.components.SwitchRow
import com.alram.mail.ui.containerViewModel
import com.alram.mail.ui.formatTime
import com.alram.mail.ui.theme.AlramTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDetailVm(private val c: AppContainer, private val pkg: String) : ViewModel() {
    val rule = c.db.appRules().observe(pkg).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val prefs = c.settings.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPrefs())

    fun edit(transform: (AppRuleEntity) -> AppRuleEntity) = viewModelScope.launch {
        val cur = c.db.appRules().get(pkg) ?: return@launch
        c.db.appRules().upsert(transform(cur))
    }
}

@Composable
fun AppDetailScreen(packageName: String, onBack: () -> Unit) {
    val vm = containerViewModel(key = "app:$packageName") { AppDetailVm(it, packageName) }
    val rule by vm.rule.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val r = rule

    SubScreen("앱 설정", onBack) { inner ->
        if (r == null) {
            Column(Modifier.padding(inner).padding(24.dp)) {
                Text("앱 정보를 불러오는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@SubScreen
        }
        val enabled = r.enabled ?: prefs.core.newAppsEnabled
        Column(
            Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp, bottom = 32.dp),
        ) {
            GroupCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(r.packageName, r.label, size = 52.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(r.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            r.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                RowDivider()
                SwitchRow(
                    title = "이 앱의 알림 전달",
                    checked = enabled,
                    onChange = { on -> vm.edit { it.copy(enabled = on) } },
                    subtitle = if (r.seenCount > 0) "지금까지 ${r.seenCount}건 · 마지막 ${formatTime(r.lastSeenAt)}" else "아직 받은 알림 없음",
                )
            }
            if (r.packageName in LoopGuard.MAIL_APPS) {
                Banner(
                    "메일 앱이라 처음엔 꺼 두었어요. 켜더라도 이 앱이 보낸 메일은 다시 전달하지 않아요.",
                    BannerTone.Info,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            val modeValue = r.mode?.let { runCatching { DeliveryMode.valueOf(it) }.getOrNull() }
            SectionHeader("전달 방식")
            GroupCard {
                PillRow(
                    options = listOf<Pair<DeliveryMode?, String>>(
                        null to "기본값", DeliveryMode.INSTANT to "즉시",
                        DeliveryMode.BATCH to "묶음", DeliveryMode.DAILY to "하루 요약",
                    ),
                    selected = modeValue,
                    onSelect = { m -> vm.edit { it.copy(mode = m?.name) } },
                )
                Hint(
                    when (modeValue ?: prefs.core.defaultMode) {
                        DeliveryMode.INSTANT -> "알림이 오면 몇 초 안에 보내요. 연달아 온 알림은 한 통으로 합쳐요."
                        DeliveryMode.BATCH -> "${prefs.core.batchMinutes}분마다 모아서 한 통으로 보내요."
                        DeliveryMode.DAILY -> "매일 정해진 시각에 하루치를 한 통으로 보내요."
                    } + if (modeValue == null) " (설정의 기본값)" else "",
                    Modifier.padding(bottom = 14.dp),
                )
            }

            val bodyValue = r.bodyMode?.let { runCatching { BodyMode.valueOf(it) }.getOrNull() }
            SectionHeader("메일에 담을 내용")
            GroupCard {
                PillRow(
                    options = listOf<Pair<BodyMode?, String>>(
                        null to "기본값", BodyMode.FULL to "제목 + 본문", BodyMode.TITLE_ONLY to "제목만",
                    ),
                    selected = bodyValue,
                    onSelect = { b -> vm.edit { it.copy(bodyMode = b?.name) } },
                )
                Spacer(Modifier.height(10.dp))
                RowDivider()
                Text(
                    "인증번호 가리기",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                )
                PillRow(
                    options = listOf<Pair<Boolean?, String>>(null to "기본값", true to "가리기", false to "그대로"),
                    selected = r.maskOtp,
                    onSelect = { m -> vm.edit { it.copy(maskOtp = m) } },
                )
                Hint(
                    "인증·코드·OTP 문구가 있는 알림의 4~8자리 숫자를 ••••로 바꿔서 보내요.",
                    Modifier.padding(bottom = 14.dp),
                )
            }

            SectionHeader("키워드 필터")
            GroupCard {
                KeywordEditor(
                    title = "이 단어가 있을 때만 전달",
                    hint = "비워 두면 모두 전달",
                    values = r.includeKeywords.lines().filter { it.isNotBlank() },
                    onChange = { list -> vm.edit { it.copy(includeKeywords = list.joinToString("\n")) } },
                )
                RowDivider()
                KeywordEditor(
                    title = "이 단어가 있으면 전달 안 함",
                    hint = "예: 광고, 이벤트",
                    values = r.excludeKeywords.lines().filter { it.isNotBlank() },
                    onChange = { list -> vm.edit { it.copy(excludeKeywords = list.joinToString("\n")) } },
                )
            }

            SectionHeader("이 앱만 다른 주소로 받기")
            GroupCard {
                RecipientsField(value = r.recipients, onCommit = { v -> vm.edit { it.copy(recipients = v) } })
                Hint(
                    "쉼표(,)로 여러 개를 적을 수 있어요. 비워 두면 설정의 받는 주소로 가요.",
                    Modifier.padding(bottom = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun <T> PillRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            ChoicePill(label, selected = value == selected, onClick = { onSelect(value) })
        }
    }
}

@Composable
private fun KeywordEditor(title: String, hint: String, values: List<String>, onChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    fun add() {
        val v = input.trim()
        if (v.isNotEmpty() && v !in values) onChange(values + v)
        input = ""
    }
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormField(
                value = input,
                onValueChange = { input = it },
                placeholder = hint,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { add() },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AlramTheme.colors.iconTile),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "추가", tint = AlramTheme.colors.iconTint)
            }
        }
        if (values.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                values.forEach { v -> KeywordChip(v) { onChange(values - v) } }
            }
        }
    }
}

@Composable
private fun KeywordChip(text: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(AlramTheme.colors.iconTile)
            .padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = AlramTheme.colors.iconTint)
        Icon(
            Icons.Rounded.Close,
            contentDescription = "$text 삭제",
            tint = AlramTheme.colors.iconTint,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(5.dp),
        )
    }
}

@Composable
private fun RecipientsField(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    LaunchedEffect(text) {
        if (text != value) {
            kotlinx.coroutines.delay(700)
            onCommit(text.trim())
        }
    }
    FormField(
        value = text,
        onValueChange = { text = it },
        placeholder = "someone@example.com",
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
