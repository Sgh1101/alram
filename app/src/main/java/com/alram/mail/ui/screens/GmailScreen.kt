package com.alram.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.data.AppPrefs
import com.alram.mail.mail.MailException
import com.alram.mail.ui.LocalContainer
import com.alram.mail.ui.components.ConfirmDialog
import com.alram.mail.ui.components.FormField
import com.alram.mail.ui.components.GroupCard
import com.alram.mail.ui.components.PrimaryButton
import com.alram.mail.ui.components.SecondaryButton
import com.alram.mail.ui.components.SectionHeader
import com.alram.mail.ui.components.SubScreen
import com.alram.mail.ui.openUrl
import com.alram.mail.ui.theme.AlramTheme
import kotlinx.coroutines.launch

@Composable
fun GmailScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle(initialValue = AppPrefs())

    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val hasSaved = remember(prefs.gmailAddress, busy) { c.secrets.has() }

    LaunchedEffect(prefs.gmailAddress) {
        if (address.isEmpty()) address = prefs.gmailAddress
    }

    suspend fun persist(addr: String, pw: String) {
        c.secrets.save(pw)
        c.settings.update { it.copy(gmailAddress = addr) }
        c.db.notifications().retryPendingNow(System.currentTimeMillis())
        c.dispatcher.poke()
    }

    fun submit(testFirst: Boolean) {
        val addr = address.trim()
        val pw = password.filter { !it.isWhitespace() }.ifEmpty { c.secrets.load().orEmpty() }
        if (!addr.contains('@')) { message = "Gmail 주소를 입력해 주세요."; isError = true; return }
        if (pw.length < 16) { message = "앱 비밀번호는 공백을 뺀 16자리예요. 일반 로그인 비밀번호가 아니에요."; isError = true; return }
        scope.launch {
            busy = true
            message = null
            if (testFirst) {
                val r = c.dispatcher.sendTest(addr, pw)
                if (r.isSuccess) {
                    persist(addr, pw)
                    password = ""
                    isError = false
                    message = "연결됐어요! 받은편지함에서 테스트 메일을 확인해 보세요."
                } else {
                    isError = true
                    message = (r.exceptionOrNull() as? MailException)?.userMessage ?: "연결에 실패했어요."
                }
            } else {
                persist(addr, pw)
                password = ""
                isError = false
                message = "저장했어요."
            }
            busy = false
        }
    }

    SubScreen("Gmail 연결", onBack) { inner ->
        Column(
            Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Text(
                "알림을 보낼 Gmail 계정과 앱 비밀번호를 입력해 주세요. 비밀번호는 이 폰 안에서만 암호화해 보관하고, Gmail 서버 말고는 어디에도 보내지 않아요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            )

            Column(Modifier.padding(horizontal = 16.dp)) {
                FormField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Gmail 주소",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = password,
                    onValueChange = { password = it },
                    label = "앱 비밀번호 16자리",
                    placeholder = if (hasSaved) "저장되어 있어요 · 바꿀 때만 입력" else "abcd efgh ijkl mnop",
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
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tint,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = if (busy) "확인하는 중…" else "연결하고 테스트 메일 보내기",
                    onClick = { submit(true) },
                    loading = busy,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("테스트 없이 저장", onClick = { submit(false) }, enabled = !busy)
                if (hasSaved) {
                    TextButton(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("연결 해제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            SectionHeader("앱 비밀번호 만드는 방법")
            GroupCard {
                listOf(
                    "구글 계정에서 2단계 인증을 켜요.",
                    "아래 버튼으로 '앱 비밀번호' 페이지를 열어요.",
                    "이름을 아무거나(예: Alram) 적고 만들기를 눌러요.",
                    "나온 16자리를 위 칸에 붙여넣어요. 띄어쓰기는 있어도 괜찮아요.",
                ).forEachIndexed { i, step -> StepLine(i + 1, step) }
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
                    SecondaryButton("앱 비밀번호 페이지 열기", onClick = { openUrl(context, "https://myaccount.google.com/apppasswords") })
                }
            }
            Text(
                "회사·학교 계정은 관리자가 앱 비밀번호를 막아 두었을 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, end = 20.dp, top = 10.dp),
            )
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Gmail 연결 해제",
            message = "저장된 앱 비밀번호를 지워요. 다시 연결하기 전까지 알림은 폰에 쌓아 두기만 해요.",
            confirmLabel = "해제",
            destructive = true,
            onConfirm = {
                c.secrets.clear()
                scope.launch { c.settings.update { it.copy(gmailAddress = "") } }
                address = ""
                message = null
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun StepLine(number: Int, text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(AlramTheme.colors.iconTile),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall, color = AlramTheme.colors.iconTint)
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp, top = 1.dp),
        )
    }
}
