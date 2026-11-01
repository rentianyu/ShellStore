package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.*
import com.xbta.shellstore.ui.theme.LocalCodeFont
import com.xbta.shellstore.ui.theme.Tones

// ---------------- 类型图标 ----------------

fun typeGlyph(t: EntryType): String = when (t) {
    EntryType.SHELL -> ">_"
    EntryType.SCRIPT -> "≡"
    EntryType.URLSCHEME -> "↗"
    EntryType.INTENT -> "◎"
    EntryType.ACTIVITY -> "▣"
    EntryType.SERVICE -> "⚙"
    EntryType.BROADCAST -> "◉"
}

fun typeLabel(t: EntryType): String = when (t) {
    EntryType.SHELL -> "shell"
    EntryType.SCRIPT -> "script"
    EntryType.URLSCHEME -> "urlscheme"
    EntryType.INTENT -> "intent"
    EntryType.ACTIVITY -> "activity"
    EntryType.SERVICE -> "service"
    EntryType.BROADCAST -> "broadcast"
}

@Composable
fun TypeBadge(t: EntryType, size: Int = 20) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3.2f).dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            typeGlyph(t),
            fontSize = (size / 2f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ---------------- 徽章 ----------------

@Composable
fun Badge(text: String, fg: Color, bg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    ) {
        Text(text, fontSize = 9.5.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RiskBadge(risk: Risk) {
    val (fg, bg) = when (risk) {
        Risk.SAFE -> Tones.safe to Tones.safeBg
        Risk.CAUTION -> Tones.caution to Tones.cautionBg
        Risk.DANGER -> Tones.danger to Tones.dangerBg
    }
    Badge(risk.label, fg, bg)
}

@Composable
fun MutedBadge(text: String) = Badge(text, Tones.neutralFg, Tones.neutralBg)

// ---------------- 代码块 ----------------

@Composable
fun CodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    fontSize: Int = 12,
    background: Color? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background ?: Tones.codeBg)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            fontFamily = LocalCodeFont.current,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 6).sp,
            color = Tones.codeFg,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------- 列表卡片 ----------------

@Composable
fun EntryRow(
    entry: Entry,
    fav: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    showCategory: Boolean = true
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(entry.type)
            Spacer(Modifier.width(7.dp))
            Text(
                entry.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .clickable { onToggleFav() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (fav) "★" else "☆",
                    fontSize = 14.sp,
                    color = if (fav) Color(0xFFF2A93B) else MaterialTheme.colorScheme.outline
                )
            }
        }
        val preview = when {
            entry.body.isNotBlank() -> entry.body.replace("\n", " ⏎ ")
            !entry.adb.isNullOrBlank() -> entry.adb
            entry.intent?.action?.isNotBlank() == true -> entry.intent!!.action
            entry.intent?.component?.isNotBlank() == true -> entry.intent!!.component
            else -> ""
        }
        if (preview.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            CodeBlock(preview, maxLines = 1, fontSize = 11)
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MutedBadge(typeLabel(entry.type))
            Spacer(Modifier.width(5.dp))
            RiskBadge(entry.risk)
            if (entry.need != Need.NONE) {
                Spacer(Modifier.width(5.dp))
                MutedBadge(entry.need.label)
            }
            if (showCategory && entry.categoryId.isNotBlank()) {
                Spacer(Modifier.width(5.dp))
                MutedBadge(entry.categoryId)
            }
        }
    }
}

// ---------------- 顶栏 ----------------

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground) }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun IconBtn(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(glyph, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f)) }
}

// ---------------- 搜索框 ----------------

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "搜索命令、脚本、Scheme、Intent",
    autoFocus: Boolean = false,
    onCancel: (() -> Unit)? = null
) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { fr.requestFocus() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    if (autoFocus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(placeholder, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    "✕",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable { onQueryChange("") }.padding(4.dp)
                )
            }
        }
        if (onCancel != null) {
            Text(
                "取消",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 10.dp).clickable { onCancel() }
            )
        }
    }
}

// ---------------- 筛选 chip ----------------

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                RoundedCornerShape(7.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            fontSize = 11.5.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// ---------------- 空态 ----------------

@Composable
fun EmptyState(title: String, hint: String = "") {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(hint, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ---------------- 分组行 ----------------

@Composable
fun SettingRow(
    glyph: String,
    title: String,
    subtitle: String = "",
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled) { onClick() } else Modifier)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(glyph, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            fontSize = 13.sp,
            color = if (danger) Tones.danger else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        if (trailing != null) trailing()
        else if (onClick != null) Text("›", fontSize = 15.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(12.dp))
    ) { content() }
}

@Composable
fun GroupTitle(text: String) {
    Text(
        text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 3.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 13.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = .22f))
    )
}
