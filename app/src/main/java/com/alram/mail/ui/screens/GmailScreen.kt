package com.alram.mail.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.alram.mail.ui.openUrl
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gmail 연결") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "알림을 보낼 Gmail 계정과 앱 비밀번호를 입력해 주세요. 비밀번호는 이 폰 안에서 암호화되어 저장되고, 구글 SMTP 서버 외에는 어디로도 보내지 않아요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Gmail 주소") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("앱 비밀번호 (16자리)") },
                placeholder = { Text(if (hasSaved) "저장되어 있어요 · 바꿀 때만 입력" else "abcd efgh ijkl mnop") },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { show = !show }) {
                        Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = "표시 전환")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }

            Button(onClick = { submit(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.padding(start = 8.dp))
                }
                Text(if (busy) "확인 중…" else "연결하고 테스트 메일 보내기")
            }
            OutlinedButton(onClick = { submit(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("테스트 없이 저장")
            }
            if (hasSaved) {
                TextButton(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("연결 해제", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("앱 비밀번호 만드는 방법", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "1. 구글 계정에서 2단계 인증을 켭니다.",
                    "2. 아래 버튼으로 '앱 비밀번호' 페이지를 엽니다.",
                    "3. 이름을 아무거나(예: Alram) 적고 만들기를 누릅니다.",
                    "4. 나오는 16자리를 위 칸에 붙여넣습니다. 띄어쓰기는 있어도 돼요.",
                ).forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(
                onClick = { openUrl(context, "https://myaccount.google.com/apppasswords") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("앱 비밀번호 페이지 열기") }
            Text(
                "회사·학교 계정은 관리자가 앱 비밀번호를 막아 두었을 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Gmail 연결 해제",
            message = "저장된 앱 비밀번호를 지웁니다. 다시 연결하기 전까지 알림은 폰에 쌓아 두기만 해요.",
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
