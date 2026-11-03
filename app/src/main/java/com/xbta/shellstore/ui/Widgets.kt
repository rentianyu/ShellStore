package com.xbta.shellstore.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xbta.shellstore.core.*
import com.xbta.shellstore.data.ShellExec
import com.xbta.shellstore.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xbta.shellstore.ui.theme.LocalCodeFont
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Dimen
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale

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
fun TypeBadge(t: EntryType, size: Int = 24) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3f).dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            typeGlyph(t),
            fontSize = (size / 2.05f).sp,
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
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = TypeScale.caption, color = fg, fontWeight = FontWeight.Medium)
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

// ---------------- 卡片外壳 ----------------

@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = Radius.md,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surface),
        content = content
    )
}

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
            .clip(RoundedCornerShape(Radius.sm))
            .background(background ?: Tones.codeBg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
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

/**
 * 把 text 里命中 keyword 的片段标成主色 + 淡底，用于搜索结果。
 * 大小写不敏感，按下标切原串（不做 lowercase 后的下标换算，中文场景更安全）。
 */
@Composable
fun highlightSpan(text: String, keyword: String): AnnotatedString {
    val kw = keyword.trim()
    if (kw.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val lowerText = text.lowercase()
    val lowerKw = kw.lowercase()
    val hit = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val p = lowerText.indexOf(lowerKw, i)
            if (p < 0) {
                append(text.substring(i))
                break
            }
            if (p > i) append(text.substring(i, p))
            val end = (p + kw.length).coerceAtMost(text.length)
            withStyle(
                SpanStyle(
                    color = hit,
                    fontWeight = FontWeight.Bold,
                    background = hit.copy(alpha = .14f)
                )
            ) { append(text.substring(p, end)) }
            i = end
        }
    }
}


@Composable
fun EntryRow(
    entry: Entry,
    fav: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    onEdit: (() -> Unit)? = null,
    showCategory: Boolean = true,
    folded: Boolean = false,
    highlight: String = ""
) {
    val ctx = LocalContext.current
    val canOpen = IntentLauncher.canTrigger(entry)
    val canRun = entry.type == EntryType.SHELL || entry.type == EntryType.SCRIPT
    val settings by Store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var confirmExec by remember { mutableStateOf(false) }
    var execResult by remember { mutableStateOf<ShellExec.Result?>(null) }

    fun runNow() {
        val cmd = CommandRenderer.primaryText(
            entry,
            entry.params.associate { it.key to it.default },
            -1
        )
        scope.launch {
            running = true
            val r = withContext(Dispatchers.IO) { ShellExec.run(ctx, cmd) }
            running = false
            execResult = r
            Store.recordRecent(entry.id)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(entry.type)
            Spacer(Modifier.width(Space.sm))
            Text(
                highlightSpan(entry.title, highlight),
                fontSize = TypeScale.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (Store.isLocal(entry.id)) {
                Spacer(Modifier.width(Space.xs))
                Badge("本地", Tones.neutralFg, Tones.neutralBg)
            }
            if (folded) {
                RiskBadge(entry.risk)
                Spacer(Modifier.width(Space.xs))
            }
            Box(
                Modifier
                    .size(Dimen.iconButton)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onToggleFav() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (fav) "★" else "☆",
                    fontSize = IconSize.action,
                    color = if (fav) Color(0xFFF2A93B) else MaterialTheme.colorScheme.outline
                )
            }
            if (onEdit != null) {
                Box(
                    Modifier
                        .size(Dimen.iconButton)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✎", fontSize = IconSize.action, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        val preview = when {
            entry.body.isNotBlank() -> entry.body.replace("\n", " ⏎ ")
            entry.adb.isNotBlank() -> entry.adb
            entry.intent?.action?.isNotBlank() == true -> entry.intent!!.action
            entry.intent?.component?.isNotBlank() == true -> entry.intent!!.component
            else -> ""
        }
        if (folded) return@Column

        if (preview.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            CodeBlock(preview, maxLines = 1, fontSize = 12)
        }

        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MutedBadge(typeLabel(entry.type))
            Spacer(Modifier.width(Space.xs))
            RiskBadge(entry.risk)
            if (entry.need != Need.NONE) {
                Spacer(Modifier.width(Space.xs))
                MutedBadge(entry.need.label)
            }
            if (showCategory && entry.categoryId.isNotBlank()) {
                Spacer(Modifier.width(Space.xs))
                MutedBadge(entry.categoryId)
            }
            if (canRun || canOpen) {
                Spacer(Modifier.weight(1f))
                // 右下角快捷操作：命令类直接执行，Intent 类直接唤起
                val dim = canRun && running
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (dim) .06f else .10f)
                        )
                        .clickable(enabled = !dim) {
                            if (canRun) {
                                if (entry.risk == Risk.DANGER && settings.confirmDanger) confirmExec = true
                                else runNow()
                            } else {
                                val err = IntentLauncher.trigger(ctx, entry, emptyMap())
                                if (err == null) Store.recordRecent(entry.id)
                                Toast.makeText(
                                    ctx,
                                    err ?: "${IntentLauncher.actionLabel(entry)}成功",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        when {
                            canRun && running -> "执行中…"
                            canRun -> "执行"
                            else -> IntentLauncher.actionLabel(entry)
                        },
                        fontSize = TypeScale.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (dim) .5f else 1f)
                    )
                }
            }
        }
    }

    // 危险命令二次确认
    if (confirmExec) {
        AlertDialog(
            onDismissRequest = { confirmExec = false },
            title = { Text("执行危险命令？", fontSize = TypeScale.body) },
            text = {
                Text(
                    entry.title + "\n\n这条命令标记为「危险」，执行可能造成不可逆的改动。",
                    fontSize = TypeScale.small
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmExec = false; runNow() }) {
                    Text("仍然执行", color = Tones.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExec = false }) { Text("取消") }
            }
        )
    }

    execResult?.let { r ->
        ExecResultDialog(
            result = r,
            command = CommandRenderer.primaryText(
                entry,
                entry.params.associate { it.key to it.default },
                -1
            ),
            onCopy = {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
                cm.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "输出",
                        listOf(r.out, r.err).filter { it.isNotBlank() }.joinToString("\n")
                    )
                )
                Toast.makeText(ctx, "输出已复制", Toast.LENGTH_SHORT).show()
            },
            onOpenTermux = { ShellExec.openTermux(ctx) },
            onDismiss = { execResult = null }
        )
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
            .height(Dimen.topBar)
            .padding(start = if (onBack != null) Space.sm else Space.md, end = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(Dimen.iconButton)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", fontSize = IconSize.nav, color = MaterialTheme.colorScheme.onBackground) }
            Spacer(Modifier.width(Space.xs))
        }
        Text(
            title,
            fontSize = TypeScale.headline,
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
        Modifier
            .size(Dimen.iconButton)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(glyph, fontSize = IconSize.top, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f)) }
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
                .height(40.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    if (autoFocus) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = .9f),
                    RoundedCornerShape(Radius.md)
                )
                .padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", fontSize = IconSize.inline, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Space.sm))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = TypeScale.body, color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
            }
            if (query.isNotEmpty()) {
                // 热区给到 32dp：文字本身只有十几 dp，点起来容易落空
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✕",
                        fontSize = TypeScale.title,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        if (onCancel != null) {
            Text(
                "取消",
                fontSize = TypeScale.body,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = Space.md)
                    .clickable { onCancel() }
            )
        }
    }
}

// ---------------- 筛选 chip ----------------

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = .9f),
                RoundedCornerShape(Radius.sm)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            fontSize = TypeScale.small,
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
        Text(title, fontSize = TypeScale.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Text(hint, fontSize = TypeScale.small, color = MaterialTheme.colorScheme.outline)
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
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(glyph, fontSize = IconSize.card, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(Space.md))
        Text(
            title,
            fontSize = TypeScale.body,
            fontWeight = FontWeight.Medium,
            color = if (danger) Tones.danger else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.xs))
        }
        if (trailing != null) trailing()
        else if (onClick != null) Text("›", fontSize = IconSize.chevron, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
    ) { content() }
}

@Composable
fun GroupTitle(text: String) {
    Text(
        text,
        fontSize = TypeScale.caption,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.xs, top = Space.lg, bottom = Space.sm)
    )
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = Space.md)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

// ---------------- 开关 ----------------

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f))
            .clickable { onChange(!on) }
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(20.dp)
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Color.White)
        )
    }
}

// ---------------- 多选勾选框 ----------------

@Composable
fun CheckCircle(checked: Boolean) {
    Box(
        Modifier
            .size(Dimen.checkbox)
            .clip(CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text(
                "✓",
                fontSize = IconSize.check,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ---------------- 视图设置弹层 ----------------

/**
 * 列表显示设置。入口是顶栏右上角的视图图标，点开后是一个开关面板，
 * 而不是每张卡片各挂一个折叠按钮。
 */
@Composable
fun ViewOptionsDialog(
    foldPreview: Boolean,
    onFoldChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.lg))
                .padding(Space.lg)
        ) {
            Text("列表显示", fontSize = TypeScale.title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.lg))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("折叠命令预览", fontSize = TypeScale.body, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "只显示标题，一屏最多看 3 倍条目",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(Space.md))
                Toggle(foldPreview, onFoldChange)
            }

            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("完成", fontSize = TypeScale.body) }
            }
        }
    }
}
