package com.alram.mail.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alram.mail.AppContainer
import com.alram.mail.data.NotificationEntity
import com.alram.mail.data.Status
import com.alram.mail.ui.components.ConfirmDialog
import com.alram.mail.ui.components.EmptyState
import com.alram.mail.ui.components.GroupCard
import com.alram.mail.ui.components.Pill
import com.alram.mail.ui.components.RowDivider
import com.alram.mail.ui.components.ScreenTitle
import com.alram.mail.ui.components.SearchField
import com.alram.mail.ui.components.SegmentedTabs
import com.alram.mail.ui.components.groupItem
import com.alram.mail.ui.containerViewModel
import com.alram.mail.ui.formatTime
import com.alram.mail.ui.theme.AlramTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryVm(private val c: AppContainer) : ViewModel() {
    val tab = MutableStateFlow(Status.PENDING)
    val query = MutableStateFlow("")

    val items = combine(tab.flatMapLatest { c.db.notifications().observeByStatus(it, 300) }, query) { list, q ->
        if (q.isBlank()) list else list.filter {
            it.title.contains(q, true) || it.text.contains(q, true) || it.appLabel.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount = c.db.notifications().observeCount(Status.PENDING).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val failedCount = c.db.notifications().observeCount(Status.FAILED).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun retry(id: Long) = viewModelScope.launch {
        c.db.notifications().retry(id, System.currentTimeMillis())
        c.dispatcher.poke()
    }

    fun retryAllFailed() = viewModelScope.launch {
        c.db.notifications().retryAllFailed(System.currentTimeMillis())
        c.dispatcher.poke()
    }

    fun delete(id: Long) = viewModelScope.launch { c.db.notifications().delete(id) }

    fun clearTab() = viewModelScope.launch { c.db.notifications().deleteByStatus(tab.value) }
}

@Composable
fun HistoryScreen() {
    val vm = containerViewModel { HistoryVm(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    val failedCount by vm.failedCount.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<NotificationEntity?>(null) }

    val tabs = listOf(
        Status.PENDING to "대기${if (pendingCount > 0) " $pendingCount" else ""}",
        Status.SENT to "발송됨",
        Status.FAILED to "실패${if (failedCount > 0) " $failedCount" else ""}",
    )

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("기록") {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "더보기") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (tab == Status.FAILED) {
                        DropdownMenuItem(text = { Text("실패한 알림 모두 다시 시도") }, onClick = { menu = false; vm.retryAllFailed() })
                    }
                    DropdownMenuItem(text = { Text("이 목록 비우기") }, onClick = { menu = false; confirmClear = true })
                }
            }
        }
        SearchField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = "제목·내용·앱 검색",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        SegmentedTabs(
            options = tabs.map { it.second },
            selected = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0),
            onSelect = { vm.tab.value = tabs[it].first },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        )
        if (items.isEmpty()) {
            GroupCard {
                EmptyState(
                    when (tab) {
                        Status.PENDING -> "대기 중인 알림이 없어요"
                        Status.SENT -> "보낸 알림이 없어요"
                        else -> "실패한 알림이 없어요"
                    },
                    if (query.isNotBlank()) "검색 결과가 없어요." else "알림이 오면 여기에서 상태를 볼 수 있어요.",
                    icon = when (tab) {
                        Status.PENDING -> Icons.Rounded.Schedule
                        Status.SENT -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.ErrorOutline
                    },
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(items, key = { _, n -> n.id }) { i, n ->
                    Column(Modifier.groupItem(i, items.size)) {
                        NotificationRow(n, onClick = { selected = n })
                        if (i < items.size - 1) RowDivider(inset = 64.dp)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "목록 비우기",
            message = "지금 보고 있는 탭의 기록을 모두 지워요. 이미 보낸 메일에는 영향이 없어요.",
            confirmLabel = "지우기",
            destructive = true,
            onConfirm = { vm.clearTab() },
            onDismiss = { confirmClear = false },
        )
    }

    selected?.let { n ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = AlramTheme.colors.card,
            title = { Text(n.title.ifBlank { "(제목 없음)" }, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "${n.appLabel} · ${formatTime(n.postedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (n.text.isNotBlank()) {
                        Text(n.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                    }
                    val (label, color) = when (n.status) {
                        Status.SENT -> "보냄 ${formatTime(n.sentAt ?: 0)}" to MaterialTheme.colorScheme.primary
                        Status.FAILED -> "실패" to MaterialTheme.colorScheme.error
                        else -> "예약 ${formatTime(n.dueAt)}" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(Modifier.padding(top = 14.dp)) { Pill(label, color) }
                    n.lastError?.let {
                        Text(
                            if (n.status == Status.PENDING) "재시도 ${n.attempts}회 · $it" else it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.retry(n.id); selected = null }) {
                    Text(if (n.status == Status.SENT) "다시 보내기" else "지금 보내기", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.delete(n.id); selected = null }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { selected = null }) { Text("닫기") }
                }
            },
        )
    }
}
