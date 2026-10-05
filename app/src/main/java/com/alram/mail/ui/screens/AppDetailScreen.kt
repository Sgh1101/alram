package com.alram.mail.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alram.mail.AppContainer
import com.alram.mail.core.BodyMode
import com.alram.mail.core.DeliveryMode
import com.alram.mail.data.AppPrefs
import com.alram.mail.data.AppRuleEntity
import com.alram.mail.ui.components.AppIcon
import com.alram.mail.ui.components.Divider
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SwitchRow
import com.alram.mail.ui.containerViewModel
import com.alram.mail.ui.formatTime
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("앱 설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { inner ->
        if (r == null) {
            Column(Modifier.padding(inner).padding(24.dp)) { Text("앱 정보를 불러오는 중…") }
            return@Scaffold
        }
        val enabled = r.enabled ?: prefs.core.newAppsEnabled
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(r.packageName, r.label, size = 56.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(r.label, style = MaterialTheme.typography.titleLarge)
                    Text(r.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (r.seenCount > 0) "알림 ${r.seenCount}건 수신 · 마지막 ${formatTime(r.lastSeenAt)}" else "아직 받은 알림 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SwitchRow("이 앱의 알림 전달", enabled, { on -> vm.edit { it.copy(enabled = on) } })
            Divider()

            val modeValue = r.mode?.let { runCatching { DeliveryMode.valueOf(it) }.getOrNull() }
            SectionHeader("전달 방식")
            ChipChoices(
                options = listOf<Pair<DeliveryMode?, String>>(
                    null to "기본값", DeliveryMode.INSTANT to "즉시",
                    DeliveryMode.BATCH to "묶음", DeliveryMode.DAILY to "하루 요약",
                ),
                selected = modeValue,
                onSelect = { m -> vm.edit { it.copy(mode = m?.name) } },
            )
            Hint(
                when (modeValue ?: prefs.core.defaultMode) {
                    DeliveryMode.INSTANT -> "알림이 오면 몇 초 안에 보냅니다. 연달아 온 알림은 한 통으로 합쳐요."
                    DeliveryMode.BATCH -> "${prefs.core.batchMinutes}분마다 모아서 한 통으로 보냅니다."
                    DeliveryMode.DAILY -> "매일 정해진 시각에 하루치를 한 통으로 보냅니다."
                } + if (modeValue == null) " (설정의 기본값을 따르는 중)" else "",
            )

            val bodyValue = r.bodyMode?.let { runCatching { BodyMode.valueOf(it) }.getOrNull() }
            SectionHeader("메일에 담을 내용")
            ChipChoices(
                options = listOf<Pair<BodyMode?, String>>(null to "기본값", BodyMode.FULL to "제목 + 본문", BodyMode.TITLE_ONLY to "제목만"),
                selected = bodyValue,
                onSelect = { b -> vm.edit { it.copy(bodyMode = b?.name) } },
            )

            SectionHeader("인증번호 가리기")
            ChipChoices(
                options = listOf<Pair<Boolean?, String>>(null to "기본값", true to "가리기", false to "그대로"),
                selected = r.maskOtp,
                onSelect = { m -> vm.edit { it.copy(maskOtp = m) } },
            )
            Hint("인증·코드·OTP 같은 문구가 있는 알림의 4~8자리 숫자를 ••••로 바꿔서 보냅니다.")

            SectionHeader("키워드 필터")
            KeywordEditor(
                title = "이 단어가 있을 때만 전달",
                hint = "비워 두면 모든 알림을 전달해요",
                values = r.includeKeywords.lines().filter { it.isNotBlank() },
                onChange = { list -> vm.edit { it.copy(includeKeywords = list.joinToString("\n")) } },
            )
            Spacer(Modifier.height(8.dp))
            KeywordEditor(
                title = "이 단어가 있으면 전달 안 함",
                hint = "예: 광고, 이벤트, 쿠폰",
                values = r.excludeKeywords.lines().filter { it.isNotBlank() },
                onChange = { list -> vm.edit { it.copy(excludeKeywords = list.joinToString("\n")) } },
            )

            SectionHeader("이 앱만 다른 주소로 받기")
            RecipientsField(
                value = r.recipients,
                onCommit = { v -> vm.edit { it.copy(recipients = v) } },
            )
            Hint("쉼표(,)로 여러 개를 적을 수 있어요. 비워 두면 설정의 수신 주소로 갑니다.")
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun <T> ChipChoices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
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
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(title) },
            placeholder = { Text(hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (values.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                values.forEach { v ->
                    InputChip(
                        selected = false,
                        onClick = { onChange(values - v) },
                        label = { Text(v) },
                        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "삭제", modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
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
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("someone@example.com") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}
