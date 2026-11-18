package com.alram.mail.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.agent.AgentException
import com.alram.mail.core.Destination
import com.alram.mail.data.AppPrefs
import com.alram.mail.data.Status
import com.alram.mail.ui.LocalContainer
import com.alram.mail.ui.components.Banner
import com.alram.mail.ui.components.BannerTone
import com.alram.mail.ui.components.ChoiceDialog
import com.alram.mail.ui.components.ConfirmDialog
import com.alram.mail.ui.components.FormField
import com.alram.mail.ui.components.GroupCard
import com.alram.mail.ui.components.IconRowInset
import com.alram.mail.ui.components.PrimaryButton
import com.alram.mail.ui.components.RowDivider
import com.alram.mail.ui.components.SecondaryButton
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SettingRow
import com.alram.mail.ui.components.SubScreen
import com.alram.mail.ui.components.SwitchRow
import com.alram.mail.ui.formatTime
import com.alram.mail.ui.keepAll
import kotlinx.coroutines.launch

fun destinationLabel(d: Destination) = when (d) {
    Destination.MAIL -> "메일만"
    Destination.AGENT -> "에이전트만"
    Destination.BOTH -> "메일 + 에이전트"
}

@Composable
fun AgentScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val state by c.agent.state.collectAsStateWithLifecycle()
    val pending by c.db.agentQueue().observeCount(Status.PENDING).collectAsStateWithLifecycle(initialValue = 0)
    val failed by c.db.agentQueue().observeCount(Status.FAILED).collectAsStateWithLifecycle(initialValue = 0)
    val lastSent by c.db.agentQueue().observeLastSentAt().collectAsStateWithLifecycle(initialValue = null)

    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val hasToken = remember(prefs.agentUrl, busy) { c.agentToken.has() }
    val connected = prefs.agentUrl.isNotBlank() && hasToken

    LaunchedEffect(prefs.agentUrl) {
        if (url.isEmpty()) url = prefs.agentUrl
    }

    fun submit() {
        val u = url.trim().trimEnd('/')
        val t = token.trim().ifEmpty { c.agentToken.load().orEmpty() }
        if (!u.startsWith("https://")) { message = "https:// 로 시작하는 서버 주소를 입력해 주세요."; isError = true; return }
        if (t.length < 16) { message = "기기 토큰은 16자 이상이어야 해요. 서버에 넣은 INGEST_TOKEN 을 그대로 붙여넣어 주세요."; isError = true; return }
        scope.launch {
            busy = true
            message = null
            val r = c.agent.test(u, t)
            if (r.isSuccess) {
                c.agentToken.save(t)
                c.settings.update { it.copy(agentUrl = u, core = it.core.copy(agentEnabled = true)) }
                c.agent.poke()
                token = ""
                isError = false
                message = "연결됐어요! 이제 알림이 에이전트 서버로도 올라가요."
            } else {
                isError = true
                message = (r.exceptionOrNull() as? AgentException)?.userMessage ?: "연결에 실패했어요."
            }
            busy = false
        }
    }

    SubScreen("에이전트 연결", onBack) { inner ->
        Column(
            Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Text(
                "수집한 알림을 메일과 별도로 내 서버에 JSON 으로 올려요. 에이전트(Claude 등)가 서버의 API·MCP 로 알림을 검색하고 처리 표시를 할 수 있어요.".keepAll(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            )

            if (connected) {
                GroupCard {
                    SwitchRow(
                        "에이전트로 보내기",
                        prefs.core.agentEnabled,
                        { on ->
                            scope.launch {
                                c.settings.updateCore { it.copy(agentEnabled = on) }
                                c.agent.poke()
                            }
                        },
                        subtitle = prefs.agentUrl.removePrefix("https://"),
                        icon = Icons.Rounded.SmartToy,
                    )
                    RowDivider(inset = IconRowInset)
                    SettingRow(
                        "기본으로 보낼 곳",
                        subtitle = "앱마다 따로 정할 수 있어요",
                        icon = Icons.AutoMirrored.Rounded.Send,
                        value = destinationLabel(prefs.core.defaultDestination),
                        onClick = { dialog = "dest" },
                    )
                }

                state.lastError?.takeIf { prefs.core.agentEnabled }?.let {
                    Banner(it, BannerTone.Error, modifier = Modifier.padding(top = 12.dp))
                }

                SectionHeader("전송 상태")
                GroupCard {
                    SettingRow(
                        "마지막 전송",
                        icon = Icons.Rounded.CloudDone,
                        value = lastSent?.let { formatTime(it) } ?: "아직 없음",
                    )
                    RowDivider(inset = IconRowInset)
                    SettingRow("대기 중", icon = Icons.Rounded.Schedule, value = "${pending}건")
                    RowDivider(inset = IconRowInset)
                    SettingRow(
                        "실패",
                        subtitle = if (failed > 0) "눌러서 다시 보내기" else null,
                        icon = Icons.Rounded.ErrorOutline,
                        value = "${failed}건",
                        onClick = if (failed > 0) ({
                            scope.launch {
                                c.db.agentQueue().retryAllFailed(System.currentTimeMillis())
                                c.agent.poke()
                            }
                        }) else null,
                    )
                }
                SectionHeader("서버 설정 바꾸기")
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                FormField(
                    value = url,
                    onValueChange = { url = it },
                    label = "서버 주소",
                    placeholder = "https://alram-agent.내계정.workers.dev",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = token,
                    onValueChange = { token = it },
                    label = "기기 토큰 (INGEST_TOKEN)",
                    placeholder = if (hasToken) "저장되어 있어요 · 바꿀 때만 입력" else "서버에 넣은 긴 토큰",
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(
                                if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (show) "숨기기" else "보기",
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    },
                )

                message?.let {
                    Row(Modifier.padding(top = 12.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        Icon(
                            if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            it.keepAll(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tint,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = if (busy) "확인하는 중…" else if (connected) "다시 확인하고 저장" else "연결 테스트하고 켜기",
                    onClick = { submit() },
                    loading = busy,
                )
                if (connected) {
                    TextButton(onClick = { dialog = "disconnect" }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("연결 해제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            SectionHeader("알아 두기")
            GroupCard {
                listOf(
                    "서버는 저장소의 server/ 폴더(Cloudflare Worker)를 배포해서 만들어요. 방법은 server/README.md 에 있어요.",
                    "앱 설정의 키워드 필터, 인증번호 가리기, 제목만 보내기는 에이전트로 보내는 알림에도 똑같이 적용돼요.",
                    "방해금지 시간은 메일에만 적용돼요. 에이전트에는 바로 올라가요.",
                    "인터넷이 끊기면 폰에 쌓아 두었다가 다시 연결되면 순서대로 올려요.",
                ).forEach { line ->
                    Text(
                        "• ${line.keepAll()}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    when (dialog) {
        "dest" -> ChoiceDialog(
            "기본으로 보낼 곳",
            Destination.entries.map { it to destinationLabel(it) },
            prefs.core.defaultDestination,
            { d -> scope.launch { c.settings.updateCore { it.copy(defaultDestination = d) } } },
            { dialog = null },
            message = "앱마다 따로 정하지 않은 경우에 적용돼요. '에이전트만'으로 두면 메일은 오지 않아요.",
        )
        "disconnect" -> ConfirmDialog(
            title = "에이전트 연결 해제",
            message = "저장된 토큰을 지우고 에이전트로 보내기를 꺼요. 알림은 다시 메일로만 가요.",
            confirmLabel = "해제",
            destructive = true,
            onConfirm = {
                c.agentToken.clear()
                scope.launch {
                    c.settings.update { it.copy(agentUrl = "", core = it.core.copy(agentEnabled = false)) }
                    c.db.agentQueue().deleteAll()
                }
                url = ""
                message = null
            },
            onDismiss = { dialog = null },
        )
    }
}
