package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.core.Extra
import com.xbta.shellstore.core.ExtraType
import com.xbta.shellstore.core.IntentSpec
import com.xbta.shellstore.core.Need
import com.xbta.shellstore.core.Param
import com.xbta.shellstore.core.Risk
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 新建 / 编辑一条命令。
 * entryId 非空 = 编辑收藏里的快照；为空 = 新建本地条目（直接进收藏）。
 */
@Composable
fun EntryEditorScreen(
    entryId: String? = null,
    folderId: String? = null,
    draft: Entry? = null,
    onBack: () -> Unit
) {
    val existing = remember(entryId) { entryId?.let { Store.entryById(it) } }
    // draft 只用于预填（粘贴导入），不改变"这是新建"的语义
    val seed = existing ?: draft
    val isEditing = existing != null

    var type by remember { mutableStateOf(seed?.type ?: EntryType.SHELL) }
    var title by remember { mutableStateOf(seed?.title ?: "") }
    var desc by remember { mutableStateOf(seed?.desc ?: "") }
    var body by remember { mutableStateOf(seed?.body ?: "") }
    var risk by remember { mutableStateOf(seed?.risk ?: Risk.SAFE) }
    var need by remember { mutableStateOf(seed?.need ?: Need.NONE) }
    var tagText by remember { mutableStateOf(seed?.tags?.joinToString(" ") ?: "") }

    val it0 = seed?.intent
    var action by remember { mutableStateOf(it0?.action ?: "") }
    var component by remember { mutableStateOf(it0?.component ?: "") }
    var pkg by remember { mutableStateOf(it0?.packageName ?: "") }
    var data by remember { mutableStateOf(it0?.data ?: "") }
    var mime by remember { mutableStateOf(it0?.mime ?: "") }
    var cats by remember { mutableStateOf(it0?.categories?.joinToString(" ") ?: "") }

    val params = remember { mutableStateListOf<Param>().apply { seed?.params?.let { addAll(it) } } }
    val extras = remember { mutableStateListOf<Extra>().apply { it0?.extras?.let { addAll(it) } } }

    var tip by remember { mutableStateOf<String?>(null) }

    fun build(): Entry {
        val base = seed ?: Entry(id = "", type = type, title = "")
        val intentSpec = if (type.hasBody) null else IntentSpec(
            action = action.trim(),
            data = data.trim(),
            mime = mime.trim(),
            component = component.trim(),
            packageName = pkg.trim(),
            categories = cats.split(' ', ',').map { it.trim() }.filter { it.isNotBlank() },
            extras = extras.filter { it.key.isNotBlank() },
            flags = it0?.flags ?: emptyList()
        )
        return base.copy(
            type = type,
            title = title.trim(),
            desc = desc.trim(),
            tags = tagText.split(' ', ',', '，').map { it.trim() }.filter { it.isNotBlank() },
            risk = risk,
            need = need,
            body = if (type.hasBody) body.trim() else "",
            params = params.filter { it.key.isNotBlank() }.map { it.copy(key = it.key.trim()) },
            intent = intentSpec
        )
    }

    val favs by Store.favs.collectAsState()
    val isEditedFav = entryId != null && favs.items.firstOrNull { it.entryId == entryId }?.edited == true
    val canRevert = isEditedFav && entryId != null && !Store.isLocal(entryId) && Store.isAlive(entryId)

    fun save() {
        val e = build()
        when {
            e.title.isBlank() -> tip = "标题不能为空"
            e.type.hasBody && e.body.isBlank() -> tip = "命令内容不能为空"
            !e.type.hasBody && e.intent?.isActionable != true ->
                tip = "Intent 至少要填 Action 或 Component"
            else -> {
                if (isEditing) {
                    val id = entryId!!
                    // 未收藏过的源条目：先收藏再写快照，这样改动才留得住
                    if (!Store.isFav(id)) Store.addFav(e, folderId)
                    Store.updateFavEntry(id, e)
                } else {
                    Store.addLocalEntry(e, folderId)
                }
                onBack()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = if (isEditing) "编辑" else "新建",
            onBack = onBack,
            actions = {
                Text(
                    "保存",
                    fontSize = TypeScale.small,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { save() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
        ) {
            // ---------- 类型 ----------
            GroupTitle("类型")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EntryType.entries.forEach { t ->
                    FilterChip(typeLabel(t), type == t) { type = t }
                }
            }

            // ---------- 基本信息 ----------
            GroupTitle("基本信息")
            GroupCard {
                Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Field("标题", title, { title = it }, "给这条命令起个名")
                    Field("描述", desc, { desc = it }, "一句话说明它做什么（可留空）")
                }
            }

            // ---------- 内容 ----------
            GroupTitle(if (type.hasBody) "命令内容" else "Intent")
            GroupCard {
                Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (type.hasBody) {
                        Field(
                            if (type == EntryType.URLSCHEME) "URL" else "命令",
                            body, { body = it },
                            if (type == EntryType.URLSCHEME) "scheme://host/path?k=v"
                            else "am start -n ...  或  su -c '...'",
                            mono = true, minLines = 4
                        )
                        Text(
                            "不要写开头的 adb shell，App 会自动补",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Field("Action", action, { action = it }, "android.intent.action.VIEW")
                        Field("Component", component, { component = it }, "包名/类名")
                        Field("Package", pkg, { pkg = it }, "com.example.app")
                        Field("Data", data, { data = it }, "https://… 或 content://…")
                        Field("MIME", mime, { mime = it }, "text/plain")
                        Field("Category", cats, { cats = it }, "用空格分隔，如 HOME DEFAULT")

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Extras", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "+ 添加",
                                fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .clickable { extras.add(Extra("", ExtraType.STRING, "")) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                        if (extras.isEmpty()) {
                            Text(
                                "广播参数，一般不用填",
                                fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        extras.forEachIndexed { i, item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    Field("", item.key, { extras[i] = item.copy(key = it) }, "key")
                                }
                                Spacer(Modifier.width(4.dp))
                                Box(Modifier.width(84.dp)) {
                                    Text(
                                        item.type.name,
                                        fontSize = TypeScale.caption,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(Radius.sm))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                val all = ExtraType.entries
                                                extras[i] = item.copy(type = all[(all.indexOf(item.type) + 1) % all.size])
                                            }
                                            .padding(horizontal = 6.dp, vertical = 8.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Box(Modifier.weight(1.2f)) {
                                    Field("", item.value, { extras[i] = item.copy(value = it) }, "值")
                                }
                                Text(
                                    "✕", fontSize = TypeScale.caption,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier
                                        .clickable { extras.removeAt(i) }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---------- 参数 ----------
            GroupTitle("参数")
            GroupCard {
                Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (params.isEmpty()) {
                        Text(
                            "有参数的命令可以在这里定义变量，执行时自动弹出输入框",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    params.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                Field("", p.key, { params[i] = p.copy(key = it) }, "变量名")
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.weight(1f)) {
                                Field("", p.label, { params[i] = p.copy(label = it) }, "显示名")
                            }
                            Text(
                                "✕", fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .clickable { params.removeAt(i) }
                                    .padding(8.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                Field("", p.default, { params[i] = p.copy(default = it) }, "默认值")
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.weight(1.4f)) {
                                Field("", p.note, { params[i] = p.copy(note = it) }, "说明（可留空）")
                            }
                        }
                    }
                    Text(
                        "+ 添加参数",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { params.add(Param("", "", "", "")) }
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                    )
                }
            }

            // ---------- 属性 ----------
            GroupTitle("属性")
            GroupCard {
                Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text("风险", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Risk.entries.forEach { r ->
                            FilterChip(r.label, risk == r) { risk = r }
                        }
                    }
                    Text("运行条件", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Need.entries.forEach { n ->
                            FilterChip(n.label, need == n) { need = n }
                        }
                    }
                    Field("标签", tagText, { tagText = it }, "空格分隔，如 网络 调试")
                }
            }

            if (canRevert) {
                TextButton(
                    onClick = { Store.revertFavEntry(entryId!!); onBack() },
                    modifier = Modifier.padding(top = Space.xs)
                ) { Text("放弃改动，恢复源里的原始内容", fontSize = TypeScale.caption, color = Tones.danger) }
            }

            Spacer(Modifier.height(Space.lg))
        }
    }

    tip?.let { msg ->
        AlertDialog(
            onDismissRequest = { tip = null },
            text = { Text(msg, fontSize = TypeScale.body) },
            confirmButton = { TextButton(onClick = { tip = null }) { Text("好") } }
        )
    }
}

// ---------------- 输入组件 ----------------

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    mono: Boolean = false,
    minLines: Int = 1
) {
    Column {
        if (label.isNotBlank()) {
            Text(
                label,
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 9.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = minLines == 1,
                minLines = minLines,
                textStyle = TextStyle(
                    fontSize = TypeScale.small,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                fontSize = TypeScale.small,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = if (minLines > 1) 2 else 1
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}
