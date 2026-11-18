package com.alram.mail.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.core.Destination
import com.alram.mail.data.AppPrefs
import com.alram.mail.ui.LocalContainer
import com.alram.mail.ui.components.IconRowInset
import com.alram.mail.ui.components.RowDivider
import com.alram.mail.ui.components.SettingRow
import com.alram.mail.ui.components.SwitchRow
import kotlinx.coroutines.launch

/**
 * 메일 / 에이전트를 각각 켜고 끄는 스위치 두 개. 기본 보낼 곳(MAIL/AGENT/BOTH)과 agentEnabled 를 바꾼다.
 * 둘 다 꺼지면 알림이 아무 데도 안 가므로 마지막 하나는 끌 수 없다.
 * 앱별로 따로 정한 보낼 곳은 그대로 둔다(에이전트를 끄면 그 앱들도 메일로만 간다).
 *
 * @param onOpenAgent 서버가 아직 연결되지 않았을 때 에이전트 행을 누르면 열 화면. null 이면 행을 숨긴다.
 */
@Composable
fun DeliverySwitches(onOpenAgent: (() -> Unit)?) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val core = prefs.core
    val agentConnected = prefs.agentUrl.isNotBlank() && remember(prefs.agentUrl) { c.agentToken.has() }
    val agentActive = agentConnected && core.agentEnabled
    val agentOn = agentActive && core.defaultDestination != Destination.MAIL
    val mailOn = !(agentActive && core.defaultDestination == Destination.AGENT)

    SwitchRow(
        title = "메일로 보내기",
        checked = mailOn,
        onChange = { on ->
            scope.launch {
                c.settings.updateCore { it.copy(defaultDestination = if (on) Destination.BOTH else Destination.AGENT) }
                c.dispatcher.poke()
            }
        },
        subtitle = when {
            mailOn && !agentOn -> "에이전트를 켜야 메일을 끌 수 있어요"
            mailOn -> prefs.gmailAddress.ifBlank { "Gmail 연결이 필요해요" }
            else -> "꺼짐 · 앱별로 따로 정한 앱은 그대로"
        },
        icon = Icons.Rounded.Mail,
        enabled = !mailOn || agentOn,
    )
    if (agentConnected) {
        RowDivider(inset = IconRowInset)
        SwitchRow(
            title = "에이전트로 보내기",
            checked = agentOn,
            onChange = { on ->
                scope.launch {
                    c.settings.updateCore {
                        if (on) it.copy(agentEnabled = true, defaultDestination = Destination.BOTH)
                        else it.copy(agentEnabled = false)
                    }
                    c.agent.poke()
                }
            },
            subtitle = when {
                agentOn && !mailOn -> "메일을 켜야 에이전트를 끌 수 있어요"
                agentOn -> prefs.agentUrl.removePrefix("https://").substringBefore('/')
                else -> "꺼짐"
            },
            icon = Icons.Rounded.SmartToy,
            enabled = !agentOn || mailOn,
        )
    } else if (onOpenAgent != null) {
        RowDivider(inset = IconRowInset)
        SettingRow(
            title = "에이전트로 보내기",
            subtitle = "서버를 먼저 연결해 주세요",
            icon = Icons.Rounded.SmartToy,
            onClick = onOpenAgent,
        )
    }
}
