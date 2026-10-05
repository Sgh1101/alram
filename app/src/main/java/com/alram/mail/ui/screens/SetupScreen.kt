package com.alram.mail.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alram.mail.data.AppPrefs
import com.alram.mail.ui.LocalContainer
import com.alram.mail.ui.openAppDetails
import com.alram.mail.ui.openNotificationListenerSettings
import com.alram.mail.ui.rememberSystemState
import com.alram.mail.ui.requestIgnoreBatteryOptimizations
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(onOpenGmail: () -> Unit, onDone: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val system = rememberSystemState()
    val gmailDone = prefs.gmailAddress.isNotBlank() && c.secrets.has()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    val done = listOf(gmailDone, system.listenerEnabled, system.notificationsAllowed, system.batteryUnrestricted)
    val doneCount = done.count { it }

    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
    ) {
        Text("시작하기 전에", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "네 가지만 해 두면 알림이 끊기지 않고 메일로 도착해요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { doneCount / 4f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
        )
        Text(
            "$doneCount / 4 완료",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        Step(
            number = 1,
            title = "Gmail 연결",
            body = "알림을 받을 Gmail 주소와 앱 비밀번호를 등록해요.",
            done = gmailDone,
            actionLabel = if (gmailDone) "다시 설정" else "연결하기",
            onAction = onOpenGmail,
        )
        Step(
            number = 2,
            title = "알림 접근 허용",
            body = "다른 앱의 알림을 읽으려면 꼭 필요해요. 목록에서 Alram Mail을 켜 주세요.",
            done = system.listenerEnabled,
            actionLabel = "설정 열기",
            onAction = { openNotificationListenerSettings(context) },
            extra = if (!system.listenerEnabled) {
                {
                    Text(
                        "스위치가 회색이거나 \"제한된 설정\" 안내가 나오면: 앱 정보 → 오른쪽 위 ⋮ → 제한된 설정 허용 후 다시 시도하세요. (직접 설치한 앱이라 나타나는 안내예요)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { openAppDetails(context) }) { Text("앱 정보 열기") }
                }
            } else null,
        )
        Step(
            number = 3,
            title = "알림 표시 허용",
            body = "오류가 생겼을 때 알려 드리는 용도예요.",
            done = system.notificationsAllowed,
            actionLabel = "허용하기",
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openAppDetails(context)
            },
        )
        Step(
            number = 4,
            title = "배터리 제한 해제",
            body = "폰이 앱을 잠재우지 않아야 알림이 바로 전달돼요.",
            done = system.batteryUnrestricted,
            actionLabel = "제한 해제",
            onAction = { requestIgnoreBatteryOptimizations(context) },
            extra = if (!system.batteryUnrestricted) {
                {
                    Text(
                        "삼성 폰은 추가로: 설정 → 배터리 → 백그라운드 사용 제한 → \"절전 예외 앱\"에 Alram Mail을 넣어 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else null,
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                scope.launch { c.settings.update { it.copy(setupSeen = true) } }
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (doneCount == 4) "시작하기" else "나중에 마저 하기") }
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Outlined.Check, contentDescription = "완료", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra?.invoke()
            if (done) {
                TextButton(onClick = onAction, modifier = Modifier.padding(start = 0.dp)) { Text(actionLabel) }
            } else {
                FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) { Text(actionLabel) }
            }
        }
    }
}
