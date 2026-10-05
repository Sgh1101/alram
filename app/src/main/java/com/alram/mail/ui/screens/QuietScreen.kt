package com.alram.mail.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.core.QuietHours
import com.alram.mail.core.QuietPolicy
import com.alram.mail.data.AppPrefs
import com.alram.mail.ui.LocalContainer
import com.alram.mail.ui.components.ChoiceDialog
import com.alram.mail.ui.components.Divider
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SettingRow
import com.alram.mail.ui.components.SwitchRow
import com.alram.mail.ui.components.TimePickerDialog
import com.alram.mail.ui.formatMinute
import kotlinx.coroutines.launch

@Composable
fun QuietScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val q = prefs.core.quiet
    var dialog by remember { mutableStateOf<String?>(null) }

    fun update(t: (QuietHours) -> QuietHours) {
        scope.launch { c.settings.updateCore { it.copy(quiet = t(it.quiet)) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("방해금지 시간") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState())) {
            SwitchRow(
                "방해금지 사용",
                q.enabled,
                { on -> update { it.copy(enabled = on) } },
                subtitle = "정해진 시간에는 메일을 보내지 않아요",
            )
            Divider()
            SettingRow("시작", subtitle = formatMinute(q.startMinute), onClick = { dialog = "start" })
            SettingRow("끝", subtitle = formatMinute(q.endMinute), onClick = { dialog = "end" })
            SettingRow(
                "방해금지 중 알림",
                subtitle = if (q.policy == QuietPolicy.HOLD) "끝난 뒤 한꺼번에 보내기" else "보내지 않고 버리기",
                onClick = { dialog = "policy" },
            )

            SectionHeader("적용 요일")
            Text(
                "밤을 넘기는 시간대는 시작하는 요일 기준이에요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "월", 2 to "화", 3 to "수", 4 to "목", 5 to "금", 6 to "토", 7 to "일").forEach { (d, label) ->
                    FilterChip(
                        selected = d in q.days,
                        onClick = { update { it.copy(days = if (d in it.days) it.days - d else it.days + d) } },
                        label = { Text(label) },
                    )
                }
            }
        }
    }

    when (dialog) {
        "start" -> TimePickerDialog("시작 시각", q.startMinute, { m -> update { it.copy(startMinute = m) } }) { dialog = null }
        "end" -> TimePickerDialog("끝나는 시각", q.endMinute, { m -> update { it.copy(endMinute = m) } }) { dialog = null }
        "policy" -> ChoiceDialog(
            title = "방해금지 중 알림",
            options = listOf(
                QuietPolicy.HOLD to "끝난 뒤 한꺼번에 보내기",
                QuietPolicy.DROP to "보내지 않고 버리기",
            ),
            selected = q.policy,
            onSelect = { p -> update { it.copy(policy = p) } },
            onDismiss = { dialog = null },
        )
    }
}
