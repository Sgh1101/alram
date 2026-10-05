package com.alram.mail.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import com.alram.mail.ui.components.AppSwitch
import com.alram.mail.ui.components.ChoicePill
import com.alram.mail.ui.components.ConfirmDialog
import com.alram.mail.ui.components.EmptyState
import com.alram.mail.ui.components.GroupCard
import com.alram.mail.ui.components.RowDivider
import com.alram.mail.ui.components.ScreenTitle
import com.alram.mail.ui.components.SearchField
import com.alram.mail.ui.components.groupItem
import com.alram.mail.ui.containerViewModel
import com.alram.mail.ui.formatTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppFilter(val label: String) { ALL("전체"), ON("켜짐"), OFF("꺼짐"), RECENT("알림 온 앱"), USER("설치한 앱") }

data class AppRow(val rule: AppRuleEntity, val enabled: Boolean)

class AppsVm(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(AppFilter.ALL)

    private val prefs = c.settings.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, AppPrefs())

    val rows = combine(c.db.appRules().observeAll(), prefs, query, filter) { rules, p, q, f ->
        val default = p.core.newAppsEnabled
        rules.map { AppRow(it, it.enabled ?: default) }
            .filter { row ->
                (q.isBlank() || row.rule.label.contains(q.trim(), ignoreCase = true) ||
                    row.rule.packageName.contains(q.trim(), ignoreCase = true)) &&
                    when (f) {
                        AppFilter.ALL -> true
                        AppFilter.ON -> row.enabled
                        AppFilter.OFF -> !row.enabled
                        AppFilter.RECENT -> row.rule.seenCount > 0
                        AppFilter.USER -> !row.rule.isSystem
                    }
            }
            .sortedWith(
                if (f == AppFilter.RECENT) compareByDescending<AppRow> { it.rule.lastSeenAt }
                else compareBy<AppRow, String>(String.CASE_INSENSITIVE_ORDER) { it.rule.label },
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val defaultMode = prefs

    val enabledCount = combine(c.db.appRules().observeAll(), prefs) { rules, p ->
        rules.count { it.enabled ?: p.core.newAppsEnabled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch { c.catalog.sync() }
    }

    fun setEnabled(pkg: String, on: Boolean) = viewModelScope.launch {
        c.db.appRules().setEnabled(listOf(pkg), on)
    }

    fun setAll(on: Boolean) = viewModelScope.launch {
        c.catalog.sync()
        c.db.appRules().setAllEnabled(on)
        c.settings.updateCore { it.copy(newAppsEnabled = on) }
    }

    fun setSystem(on: Boolean) = viewModelScope.launch {
        c.db.appRules().setSystemEnabled(on)
    }
}

@Composable
fun AppsScreen(onOpenApp: (String) -> Unit) {
    val vm = containerViewModel { AppsVm(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val prefs by vm.defaultMode.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledCount.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf<Boolean?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("앱", subtitle = "${enabledCount}개 앱의 알림을 메일로 보내고 있어요") {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "더보기") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("모든 앱 켜기") }, onClick = { menu = false; confirmAll = true })
                    DropdownMenuItem(text = { Text("모든 앱 끄기") }, onClick = { menu = false; confirmAll = false })
                    DropdownMenuItem(text = { Text("시스템 앱 모두 끄기") }, onClick = { menu = false; vm.setSystem(false) })
                }
            }
        }

        SearchField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = "앱 이름 검색",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AppFilter.entries.toList()) { f ->
                ChoicePill(f.label, selected = filter == f, onClick = { vm.filter.value = f })
            }
        }

        if (rows.isEmpty()) {
            GroupCard {
                EmptyState("표시할 앱이 없어요", "검색어나 필터를 바꿔 보세요.", icon = Icons.Rounded.Apps)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(rows, key = { _, row -> row.rule.packageName }) { i, row ->
                    Column(Modifier.groupItem(i, rows.size)) {
                        AppListItem(
                            row,
                            prefs,
                            onOpen = { onOpenApp(row.rule.packageName) },
                            onToggle = { vm.setEnabled(row.rule.packageName, it) },
                        )
                        if (i < rows.size - 1) RowDivider(inset = 68.dp)
                    }
                }
            }
        }
    }

    confirmAll?.let { on ->
        ConfirmDialog(
            title = if (on) "모든 앱 켜기" else "모든 앱 끄기",
            message = if (on) "설치된 모든 앱과 앞으로 설치할 앱의 알림이 전달됩니다."
            else "모든 앱의 전달을 끕니다. 필요한 앱만 다시 켜 주세요. 앞으로 설치할 앱도 꺼진 상태로 시작합니다.",
            confirmLabel = if (on) "모두 켜기" else "모두 끄기",
            destructive = !on,
            onConfirm = { vm.setAll(on) },
            onDismiss = { confirmAll = null },
        )
    }
}

fun modeText(mode: DeliveryMode?, prefs: AppPrefs): String {
    val m = mode ?: prefs.core.defaultMode
    return when (m) {
        DeliveryMode.INSTANT -> "즉시"
        DeliveryMode.BATCH -> "${prefs.core.batchMinutes}분 묶음"
        DeliveryMode.DAILY -> "하루 요약"
    }
}

private fun keywordCount(raw: String) = raw.lines().count { it.isNotBlank() }

@Composable
private fun AppListItem(row: AppRow, prefs: AppPrefs, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val r = row.rule
    val details = buildList {
        if (row.enabled) add(modeText(r.mode?.let { runCatching { DeliveryMode.valueOf(it) }.getOrNull() }, prefs)) else add("꺼짐")
        if (r.bodyMode == BodyMode.TITLE_ONLY.name) add("제목만")
        if (r.maskOtp == true) add("인증번호 가림")
        val kw = keywordCount(r.includeKeywords) + keywordCount(r.excludeKeywords)
        if (kw > 0) add("키워드 $kw")
        if (r.recipients.isNotBlank()) add("별도 수신")
        if (r.seenCount > 0) add("최근 ${formatTime(r.lastSeenAt)}")
    }.joinToString(" · ")

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(r.packageName, r.label, size = 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(r.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = if (row.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppSwitch(checked = row.enabled, onCheckedChange = onToggle)
    }
}
