package com.alram.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.core.QuietHours
import com.alram.mail.core.QuietPolicy
import com.alram.mail.data.AppPrefs
import com.alram.mail.ui.LocalContainer
import com.alram.mail.ui.components.ChoiceDialog
import com.alram.mail.ui.components.GroupCard
import com.alram.mail.ui.components.Hint
import com.alram.mail.ui.components.RowDivider
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SettingRow
import com.alram.mail.ui.components.SubScreen
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

    SubScreen("방해금지 시간", onBack) { inner ->
        Column(Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 32.dp)) {
            GroupCard {
                SwitchRow(
                    "방해금지 사용",
                    q.enabled,
                    { on -> update { it.copy(enabled = on) } },
                    subtitle = "정해 둔 시간에는 메일을 보내지 않아요",
                    icon = Icons.Rounded.NightsStay,
                )
            }

            val detailAlpha = if (q.enabled) 1f else 0.45f
            SectionHeader("시간")
            GroupCard(Modifier.alpha(detailAlpha)) {
                SettingRow("시작", value = formatMinute(q.startMinute), onClick = { dialog = "start" })
                RowDivider()
                SettingRow("끝", value = formatMinute(q.endMinute), onClick = { dialog = "end" })
                RowDivider()
                SettingRow(
                    "그동안 온 알림",
                    value = if (q.policy == QuietPolicy.HOLD) "끝나면 모아서" else "버리기",
                    onClick = { dialog = "policy" },
                )
            }

            SectionHeader("요일")
            GroupCard(Modifier.alpha(detailAlpha)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf(1 to "월", 2 to "화", 3 to "수", 4 to "목", 5 to "금", 6 to "토", 7 to "일").forEach { (d, label) ->
                        DayToggle(label, selected = d in q.days) {
                            update { it.copy(days = if (d in it.days) it.days - d else it.days + d) }
                        }
                    }
                }
                Hint("밤을 넘기는 시간대는 시작하는 요일 기준이에요.", Modifier.padding(bottom = 14.dp))
            }
        }
    }

    when (dialog) {
        "start" -> TimePickerDialog("시작 시각", q.startMinute, { m -> update { it.copy(startMinute = m) } }) { dialog = null }
        "end" -> TimePickerDialog("끝나는 시각", q.endMinute, { m -> update { it.copy(endMinute = m) } }) { dialog = null }
        "policy" -> ChoiceDialog(
            title = "방해금지 중에 온 알림",
            options = listOf(
                QuietPolicy.HOLD to "끝나면 모아서 한 번에 보내기",
                QuietPolicy.DROP to "보내지 않고 버리기",
            ),
            selected = q.policy,
            onSelect = { p -> update { it.copy(policy = p) } },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun DayToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) cs.primary else cs.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
        )
    }
}
