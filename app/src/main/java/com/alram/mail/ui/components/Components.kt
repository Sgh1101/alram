package com.alram.mail.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.alram.mail.ui.theme.AlramTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────── 화면 틀

/** 탭 화면 맨 위의 큰 제목. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        actions()
    }
}

/** 뒤로 가기가 있는 하위 화면 틀. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        content = content,
    )
}

// ─────────────────────────────────────────────── 그룹 카드

/** 흰 카드 안에 행을 모아 보여 주는 그룹 리스트. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(AlramTheme.colors.card),
        content = content,
    )
}

/** LazyColumn 항목을 하나의 그룹 카드처럼 이어 붙인다. 첫/마지막 항목만 모서리를 둥글린다. */
@Composable
fun Modifier.groupItem(index: Int, count: Int): Modifier {
    val r = 18.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp,
        topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp,
        bottomEnd = if (index == count - 1) r else 0.dp,
    )
    return this
        .padding(horizontal = 16.dp)
        .clip(shape)
        .background(AlramTheme.colors.card)
}

/** 카드 위의 작은 섹션 제목. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 16.dp, top = 22.dp, bottom = 8.dp)
            .heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier, inset: Dp = 16.dp) {
    HorizontalDivider(
        modifier.padding(start = inset),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 설정 행 앞에 붙는 둥근 사각 아이콘. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AlramTheme.colors.iconTint,
    background: Color = AlramTheme.colors.iconTile,
    size: Dp = 34.dp,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** 아이콘을 쓰는 행의 구분선 들여쓰기(아이콘 오른쪽부터). */
val IconRowInset = 64.dp

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    titleColor: Color = Color.Unspecified,
    iconTint: Color? = null,
    iconBackground: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 58.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(
                icon,
                tint = iconTint ?: AlramTheme.colors.iconTint,
                background = iconBackground ?: AlramTheme.colors.iconTile,
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 170.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (showChevron && trailing == null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        showChevron = false,
        trailing = { AppSwitch(checked = checked, onCheckedChange = if (enabled) onChange else null) },
    )
}

// ─────────────────────────────────────────────── 입력 요소

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    onHero: Boolean = false,
) {
    val colors = if (onHero) {
        SwitchDefaults.colors(
            checkedThumbColor = AlramTheme.colors.heroEnd,
            checkedTrackColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color.White.copy(alpha = 0.28f),
            uncheckedBorderColor = Color.Transparent,
        )
    } else {
        SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AlramTheme.colors.switchOn,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            uncheckedBorderColor = Color.Transparent,
        )
    }
    // thumbContent 를 주면 꺼짐 상태에서도 손잡이 크기가 같아져 더 단정해 보인다.
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = colors,
        thumbContent = {},
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AlramTheme.colors.iconTile,
            contentColor = AlramTheme.colors.iconTint,
        ),
        elevation = null,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 15.sp)
    }
}

/** 줄 높이를 늘리지 않는 작은 텍스트 버튼. */
@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 작은 알약 모양 선택 버튼(필터·선택지). */
@Composable
fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else AlramTheme.colors.card
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
        )
    }
}

/** iOS 스타일 세그먼트 탭. */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AlramTheme.colors.segmentTrack)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (isSel) Modifier.shadow(1.5.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) AlramTheme.colors.segmentThumb else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 채워진 입력창(테두리 없음). 회색 바탕 위에서는 흰색, 흰 카드/대화상자 안에서는 회색으로 쓴다. */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    container: Color = AlramTheme.colors.card,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = if (label != null) {
            { Text(label) }
        } else null,
        placeholder = if (placeholder != null) {
            { Text(placeholder, color = MaterialTheme.colorScheme.outline) }
        } else null,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    FormField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "지우기", tint = MaterialTheme.colorScheme.outline)
                }
            }
        } else null,
    )
}

// ─────────────────────────────────────────────── 표시 요소

/** 앱 아이콘. 가져올 수 없으면 이름 첫 글자로 대신한다. */
@Composable
fun AppIcon(packageName: String, label: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(112, 112).asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size * 0.28f)
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(AlramTheme.colors.iconTile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = AlramTheme.colors.iconTint,
            )
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** 실시간 동작 중임을 알리는 깜빡이는 점. */
@Composable
fun LiveDot(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val alpha = if (active) {
        val t = rememberInfiniteTransition(label = "live")
        val a by t.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "liveAlpha",
        )
        a
    } else 1f
    Box(
        modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color),
    )
}

enum class BannerTone { Info, Warn, Error }

@Composable
fun Banner(
    text: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        BannerTone.Info -> cs.primaryContainer to cs.onPrimaryContainer
        BannerTone.Warn -> cs.tertiaryContainer to cs.onTertiaryContainer
        BannerTone.Error -> cs.errorContainer to cs.onErrorContainer
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (tone == BannerTone.Error) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
        )
        if (action != null) {
            TextButton(onClick = onAction) {
                Text(action, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Inbox,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(AlramTheme.colors.iconTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AlramTheme.colors.iconTint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────── 대화상자

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AlramTheme.colors.card,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                options.forEach { (value, label) ->
                    val isSel = value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSel) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "선택됨",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
fun TimePickerDialog(
    title: String,
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AlramTheme.colors.card,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute); onDismiss() }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AlramTheme.colors.card,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    fontWeight = FontWeight.SemiBold,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HSpace(width: Dp) = Spacer(Modifier.width(width))

/** 카드 안의 짧은 안내 문구. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
