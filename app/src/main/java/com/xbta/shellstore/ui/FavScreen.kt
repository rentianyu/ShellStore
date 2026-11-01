package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.data.FavItem
import com.xbta.shellstore.data.Folder
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale

private val TAG_COLORS = listOf(
    Color(0xFF2F6BFF), Color(0xFF12854A), Color(0xFFA9660A), Color(0xFFC3271F),
    Color(0xFF7C3AED), Color(0xFF0891B2), Color(0xFFBE185D), Color(0xFF4B5563)
)

private sealed interface FavDialog {
    data class NewFolder(val parentId: String?) : FavDialog
    data class RenameFolder(val id: String) : FavDialog
    data class FolderActions(val id: String) : FavDialog
    data class FavActions(val item: FavItem) : FavDialog
    data class MoveTo(val item: FavItem) : FavDialog
    data class EditNote(val item: FavItem) : FavDialog
    data class EditTags(val item: FavItem) : FavDialog
    data class BatchMove(val ids: Set<String>) : FavDialog
    data class TagActions(val id: String) : FavDialog
    data class RenameTag(val id: String) : FavDialog
}

@Composable
fun FavScreen(onOpen: (Page) -> Unit) {
    val favs by Store.favs.collectAsState()
    var view by remember { mutableIntStateOf(0) }
    var currentFolder by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<FavDialog?>(null) }
    var input by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var multiMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    val q = query.trim()
    fun hit(fi: FavItem): Boolean {
        if (q.isEmpty()) return true
        val e = fi.entry
        return e.title.contains(q, true) || e.body.contains(q, true) ||
            fi.note.contains(q, true) || e.tags.any { it.contains(q, true) }
    }

    Column(Modifier.fillMaxSize()) {
        if (searchMode) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索标题、命令、备注、标签",
                    autoFocus = true,
                    onCancel = { searchMode = false; query = "" }
                )
            }
        } else if (multiMode) {
            TopBar(
                title = "已选 ${selected.size}",
                onBack = { multiMode = false; selected = emptySet() },
                actions = {
                    Text(
                        "全选",
                        fontSize = TypeScale.body,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                selected = if (selected.size == favs.items.size) emptySet()
                                else favs.items.map { it.entryId }.toSet()
                            }
                            .padding(horizontal = Space.sm, vertical = Space.xs)
                    )
                }
            )
        } else {
            TopBar(
                title = "收藏",
                actions = {
                    IconBtn("⌕") { searchMode = true }
                    IconBtn("☑") { multiMode = true; selected = emptySet() }
                    IconBtn("+") { input = ""; dialog = FavDialog.NewFolder(currentFolder) }
                }
            )
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip("文件夹", view == 0) { view = 0; currentFolder = null }
            FilterChip("标签", view == 1) { view = 1 }
            FilterChip("全部", view == 2) { view = 2; currentFolder = null }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (view == 0) {
                val path = favs.pathOf(currentFolder)
                if (path.isNotEmpty()) {
                    item {
                        val tail = path.takeLast(3)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                            Text(
                                "收藏",
                                fontSize = TypeScale.small,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { currentFolder = null }
                            )
                            tail.forEachIndexed { i, f ->
                                Text(" / ", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.outline)
                                val last = i == tail.lastIndex
                                Text(
                                    f.name,
                                    fontSize = TypeScale.small,
                                    color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = if (!last) Modifier.clickable { currentFolder = f.id } else Modifier
                                )
                            }
                        }
                    }
                }
                val children = favs.childrenOf(currentFolder)
                items(children, key = { it.id }) { f ->
                    FolderRow(
                        folder = f,
                        count = favs.countOf(f.id),
                        onClick = { currentFolder = f.id },
                        onMenu = { dialog = FavDialog.FolderActions(f.id) }
                    )
                }
                val here = favs.itemsIn(currentFolder).filter { hit(it) }
                if (children.isNotEmpty() && here.isNotEmpty()) {
                    item {
                        Text(
                            "${path.lastOrNull()?.name ?: "收藏"} · ${here.size} 条",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                if (here.isEmpty() && children.isEmpty()) {
                    item {
                        EmptyState(
                            if (currentFolder == null) "还没有收藏" else "这个文件夹是空的",
                            "在商店里点卡片右侧的 ☆ 收藏，或按 + 新建文件夹"
                        )
                    }
                }
                items(here, key = { it.entryId }) { fi ->
                    FavRow(
                        fi,
                        onOpen = {
                            if (multiMode) selected = if (fi.entryId in selected) selected - fi.entryId else selected + fi.entryId
                            else onOpen(Page.Detail(fi.entryId))
                        },
                        onMenu = { dialog = FavDialog.FavActions(fi) },
                        multiMode = multiMode,
                        checked = fi.entryId in selected
                    )
                }
            }

            if (view == 1) {
                if (favs.tags.isEmpty()) {
                    item { EmptyState("还没有标签", "在收藏条目的「写备注」里可以管理标签") }
                } else {
                    items(favs.tags, key = { it.id }) { t ->
                        val n = favs.items.count { t.id in it.tags }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.md))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(Radius.md))
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(9.dp).clip(RoundedCornerShape(5.dp))
                                    .background(TAG_COLORS[t.colorIdx % TAG_COLORS.size])
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(t.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("$n", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (view == 2) {
                if (favs.items.isEmpty()) {
                    item { EmptyState("还没有收藏", "在商店里点卡片右侧的 ☆") }
                } else {
                    item {
                        Text(
                            "共 ${favs.items.size} 条",
                            fontSize = TypeScale.small,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(favs.items.sortedByDescending { it.savedAt }.filter { hit(it) }, key = { it.entryId }) { fi ->
                        FavRow(
                            fi,
                            onOpen = {
                                if (multiMode) selected = if (fi.entryId in selected) selected - fi.entryId else selected + fi.entryId
                                else onOpen(Page.Detail(fi.entryId))
                            },
                            onMenu = { dialog = FavDialog.FavActions(fi) },
                            multiMode = multiMode,
                            checked = fi.entryId in selected
                        )
                    }
                }
            }
        }
        if (multiMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                BatchBtn("移动到", selected.isNotEmpty()) { dialog = FavDialog.BatchMove(selected) }
                BatchBtn("取消收藏", selected.isNotEmpty(), danger = true) {
                    Store.removeFavs(selected)
                    selected = emptySet()
                    multiMode = false
                }
            }
        }
    }

    val d = dialog
    if (d != null) {
        when (d) {
            is FavDialog.NewFolder -> InputDialog(
                title = "新建文件夹",
                label = "文件夹名称",
                onConfirm = { name ->
                    if (name.isNotBlank()) Store.createFolder(name.trim(), d.parentId)
                    dialog = null
                },
                onDismiss = { dialog = null }
            )

            is FavDialog.RenameFolder -> {
                val f = favs.folders.firstOrNull { it.id == d.id }
                InputDialog(
                    title = "重命名文件夹",
                    label = "新名称",
                    initial = f?.name ?: "",
                    onConfirm = { name ->
                        if (name.isNotBlank()) Store.renameFolder(d.id, name.trim())
                        dialog = null
                    },
                    onDismiss = { dialog = null }
                )
            }

            is FavDialog.FolderActions -> {
                val f = favs.folders.firstOrNull { it.id == d.id }
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(f?.name ?: "文件夹", fontSize = 15.sp) },
                    text = {
                        Column {
                            MenuItem("新建子文件夹") { dialog = FavDialog.NewFolder(d.id) }
                            MenuItem("重命名") { dialog = FavDialog.RenameFolder(d.id) }
                            MenuItem("删除（子内容上移）", danger = true) {
                                Store.deleteFolder(d.id, cascade = false); dialog = null
                            }
                            MenuItem("删除（连同子内容）", danger = true) {
                                Store.deleteFolder(d.id, cascade = true); dialog = null
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } }
                )
            }

            is FavDialog.FavActions -> {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(d.item.entry.title, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column {
                            MenuItem("移动到…") { dialog = FavDialog.MoveTo(d.item) }
                            MenuItem("打标签") { dialog = FavDialog.EditTags(d.item) }
                            MenuItem("写备注") { dialog = FavDialog.EditNote(d.item) }
                            MenuItem("取消收藏", danger = true) {
                                Store.removeFav(d.item.entryId); dialog = null
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } }
                )
            }

            is FavDialog.MoveTo -> {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("移动到", fontSize = 15.sp) },
                    text = {
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            item {
                                MenuItem("根目录（不分类）") {
                                    Store.moveFav(d.item.entryId, null); dialog = null
                                }
                            }
                            val all = favs.folders.sortedBy { favs.depthOf(it.id) }
                            items(all, key = { it.id }) { f ->
                                MenuItem("　".repeat(favs.depthOf(f.id)) + f.name) {
                                    Store.moveFav(d.item.entryId, f.id); dialog = null
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("取消") } }
                )
            }

            is FavDialog.TagActions -> {
                val t = favs.tags.firstOrNull { it.id == d.id }
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(t?.name ?: "标签", fontSize = TypeScale.title) },
                    text = {
                        Column {
                            MenuItem("重命名") { dialog = FavDialog.RenameTag(d.id) }
                            MenuItem("删除标签", danger = true) { Store.deleteTag(d.id); dialog = null }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } }
                )
            }

            is FavDialog.RenameTag -> {
                val t = favs.tags.firstOrNull { it.id == d.id }
                InputDialog(
                    title = "重命名标签",
                    label = "新名称",
                    initial = t?.name ?: "",
                    onConfirm = { name ->
                        if (name.isNotBlank()) Store.renameTag(d.id, name.trim())
                        dialog = null
                    },
                    onDismiss = { dialog = null }
                )
            }

            is FavDialog.BatchMove -> {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("移动 ${d.ids.size} 条到", fontSize = TypeScale.title) },
                    text = {
                        LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            item {
                                MenuItem("根目录（不分类）") {
                                    Store.moveFavs(d.ids, null)
                                    selected = emptySet(); multiMode = false; dialog = null
                                }
                            }
                            val all = favs.folders.sortedBy { favs.depthOf(it.id) }
                            items(all, key = { it.id }) { f ->
                                MenuItem("　".repeat(favs.depthOf(f.id)) + f.name) {
                                    Store.moveFavs(d.ids, f.id)
                                    selected = emptySet(); multiMode = false; dialog = null
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("取消") } }
                )
            }

            is FavDialog.EditTags -> TagPickerDialog(
                item = d.item,
                allTags = favs.tags,
                onSave = { ids, newName ->
                    if (newName.isNotBlank()) {
                        val nid = Store.createTag(newName.trim(), favs.tags.size % TAG_COLORS.size)
                        Store.setFavTags(d.item.entryId, ids + nid)
                    } else {
                        Store.setFavTags(d.item.entryId, ids)
                    }
                    dialog = null
                },
                onDismiss = { dialog = null }
            )

            is FavDialog.EditNote -> InputDialog(
                title = "备注",
                label = "给自己看的备注",
                initial = d.item.note,
                onConfirm = { note ->
                    Store.updateFav(d.item.copy(note = note))
                    dialog = null
                },
                onDismiss = { dialog = null }
            )
        }
    }
}

@Composable
private fun FolderRow(folder: Folder, count: Int, onClick: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(Radius.md))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text("▣", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(10.dp))
        Text(
            folder.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text("$count", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { onMenu() }, contentAlignment = Alignment.Center) {
            Text("⋯", fontSize = 15.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun FavRow(
    item: FavItem,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    multiMode: Boolean = false,
    checked: Boolean = false
) {
    val e: Entry = item.entry
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(Radius.md))
            .clickable { onOpen() }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(e.type)
            Spacer(Modifier.width(7.dp))
            Text(
                e.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { onMenu() }, contentAlignment = Alignment.Center) {
                Text("⋯", fontSize = 15.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        val preview = if (e.body.isNotBlank()) e.body.replace("\n", " ⏎ ")
        else if (e.adb.isNotBlank()) e.adb else e.intent?.action ?: ""
        if (preview.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            CodeBlock(preview, maxLines = 1, fontSize = 11)
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MutedBadge(typeLabel(e.type))
            Spacer(Modifier.width(5.dp))
            RiskBadge(e.risk)
            if (!item.alive && !Store.isAlive(item.entryId)) {
                Spacer(Modifier.width(5.dp))
                Badge("源已失效", Tones.danger, Tones.dangerBg)
            }
            if (item.note.isNotBlank()) {
                Spacer(Modifier.width(5.dp))
                Text(
                    "备注：${item.note}", fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        color = if (danger) Tones.danger else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 4.dp)
    )
}

@Composable
private fun InputDialog(
    title: String,
    label: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp) },
        text = {
            Column {
                Text(label, fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = v,
                        onValueChange = { v = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(v) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------- 二级页 ----------

@Composable
fun FavFolderScreen(folderId: String?, onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val favs by Store.favs.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(favs.folders.firstOrNull { it.id == folderId }?.name ?: "收藏", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(favs.itemsIn(folderId), key = { it.entryId }) { fi ->
                FavRow(fi, onOpen = { onOpen(Page.Detail(fi.entryId)) }, onMenu = {})
            }
        }
    }
}

@Composable
fun TagScreen(onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val favs by Store.favs.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("标签", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp)) {
            items(favs.tags, key = { it.id }) { t ->
                val n = favs.items.count { t.id in it.tags }
                Text("${t.name}  ·  $n 条", fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
fun AllFavScreen(onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val favs by Store.favs.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("全部收藏", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(favs.items, key = { it.entryId }) { fi ->
                FavRow(fi, onOpen = { onOpen(Page.Detail(fi.entryId)) }, onMenu = {})
            }
        }
    }
}

@Composable
private fun TagPickerDialog(
    item: FavItem,
    allTags: List<com.xbta.shellstore.data.Tag>,
    onSave: (List<String>, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(item.entryId) { mutableStateOf(item.tags.toSet()) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打标签", fontSize = TypeScale.title) },
        text = {
            Column {
                if (allTags.isEmpty()) {
                    Text(
                        "还没有标签，在下方输入名称即可新建",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    allTags.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            modifier = Modifier.padding(bottom = Space.sm)
                        ) {
                            row.forEach { t ->
                                FilterChip(t.name, t.id in selected) {
                                    selected = if (t.id in selected) selected - t.id else selected + t.id
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.md))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Space.md, vertical = 10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        if (newName.isBlank()) {
                            Text("新建标签…", fontSize = TypeScale.body, color = MaterialTheme.colorScheme.outline)
                        }
                        BasicTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = TypeScale.body, color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.toList(), newName) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}


@Composable
private fun RowScope.BatchBtn(text: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(40.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (danger) Tones.dangerBg else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = TypeScale.body,
            fontWeight = FontWeight.Medium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.outline
                danger -> Tones.danger
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
