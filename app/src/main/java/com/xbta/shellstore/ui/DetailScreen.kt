package com.xbta.shellstore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.*
import com.xbta.shellstore.data.CopyPrefix
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Tones

@Composable
fun DetailScreen(entryId: String, onBack: () -> Unit) {
    val entry = Store.entryById(entryId)
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()
    val ctx = LocalContext.current

    if (entry == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("未找到", onBack = onBack)
            EmptyState("条目不存在", "可能已被源移除")
        }
        return
    }

    var values by remember(entry.id) {
        mutableStateOf(entry.params.associate { it.key to it.default })
    }
    var variantIndex by remember(entry.id) { mutableIntStateOf(-1) }

    val text = remember(entry, values, variantIndex) {
        CommandRenderer.primaryText(entry, values, variantIndex)
    }
    val fav = favs.isFav(entry.id)

    fun copy(silent: Boolean = false) {
        val out = settings.copyPrefix.fn(text)
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("命令", out))
        Toast.makeText(ctx, if (settings.copyPrefix == CopyPrefix.NONE) "已复制" else "已复制（含 ${settings.copyPrefix.label} 前缀）", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = entry.title,
            onBack = onBack,
            actions = {
                IconBtn(if (fav) "★" else "☆") { Store.toggleFav(entry) }
            }
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MutedBadge(typeLabel(entry.type))
                    Spacer(Modifier.width(5.dp))
                    RiskBadge(entry.risk)
                    if (entry.need != Need.NONE) {
                        Spacer(Modifier.width(5.dp))
                        MutedBadge(entry.need.label)
                    }
                    if (entry.categoryId.isNotBlank()) {
                        Spacer(Modifier.width(5.dp))
                        MutedBadge(entry.categoryId)
                    }
                }
            }

            if (entry.risk == Risk.DANGER) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(Tones.dangerBg)
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "⚠ 高风险操作。执行前请确认命令内容确实符合预期，误操作可能造成数据丢失或系统异常。",
                            fontSize = 11.sp,
                            color = Tones.danger,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            if (entry.variants.isNotEmpty()) {
                item { SectionLabel("变体") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip("默认", variantIndex == -1) { variantIndex = -1 }
                        entry.variants.forEachIndexed { i, v ->
                            FilterChip(v.name, variantIndex == i) { variantIndex = i }
                        }
                    }
                }
            }

            item {
                if (entry.type.hasBody || entry.type == EntryType.URLSCHEME) {
                    SectionLabel(if (entry.type == EntryType.SCRIPT) "命令（脚本）" else "命令")
                } else {
                    SectionLabel("等价 adb 命令")
                }
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(11.dp))
                        .clickable { copy() }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text.ifBlank { "（无内容）" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = Tones.codeFg
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "点击复制",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text("${text.lines().size} 行 · ${text.length} 字符", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            if (!entry.type.hasBody && entry.intent != null) {
                item { SectionLabel("Intent 字段") }
                item { IntentFieldTable(entry.intent) }
            }

            if (entry.params.isNotEmpty()) {
                item { SectionLabel("参数") }
                entry.params.forEach { p ->
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(9.dp))
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(9.dp))
                                BasicTextField(
                                    value = values[p.key] ?: "",
                                    onValueChange = { values = values + (p.key to it) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                if ((values[p.key] ?: "").isNotEmpty()) {
                                    Text(
                                        "✕",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.clickable { values = values + (p.key to "") }.padding(4.dp)
                                    )
                                }
                            }
                            if (p.note.isNotBlank()) {
                                Text(
                                    p.note,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (entry.example.isNotBlank()) {
                item { SectionLabel("示例") }
                item { CodeBlock(entry.example, fontSize = 11) }
            }

            if (entry.desc.isNotBlank()) {
                item { SectionLabel("说明") }
                item {
                    Text(
                        entry.desc,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.origin.isNotBlank()) {
                item { SectionLabel("原始写法") }
                item { CodeBlock(entry.origin, fontSize = 11) }
            }

            item { SectionLabel("来源") }
            item {
                val meta = Store.sourceName.collectAsState().value
                val ver = Store.sourceVersion.collectAsState().value
                Text(
                    "来源　$meta" + (if (ver.isNotBlank()) " · v$ver" else "") + "\n" +
                        "条目　${entry.id}" +
                        (if (entry.verifiedAt.isNotBlank()) "\n验证　${entry.verifiedAt}" else ""),
                    fontSize = 10.5.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // 底部操作栏
        val openable = IntentLauncher.canOpen(entry)
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (openable) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            val err = IntentLauncher.open(ctx, entry, values)
                            if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "打开",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { copy() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "复制",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { copy() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (settings.copyPrefix == CopyPrefix.NONE) entry.primaryActionLabel
                        else "${entry.primaryActionLabel}（${settings.copyPrefix.label}）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { Store.toggleFav(entry) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (fav) "★" else "☆",
                    fontSize = 16.sp,
                    color = if (fav) androidx.compose.ui.graphics.Color(0xFFF2A93B) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
    )
}

@Composable
private fun IntentFieldTable(spec: IntentSpec) {
    val rows = buildList {
        add("action" to spec.action)
        add("component" to spec.component)
        add("data" to spec.data)
        add("mime" to spec.mime)
        add("package" to spec.packageName)
        add("category" to spec.categories.joinToString(", "))
        add("extras" to spec.extras.joinToString("\n") {
            "${it.key} (${it.type.name.lowercase()}) = ${it.value}"
        })
        add("flags" to spec.flags.joinToString(", "))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(11.dp))
    ) {
        rows.forEachIndexed { i, pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp)) {
                Text(
                    pair.first,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp)
                )
                Text(
                    pair.second.ifBlank { "—" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (pair.second.isBlank()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            if (i != rows.lastIndex) Divider()
        }
    }
}
