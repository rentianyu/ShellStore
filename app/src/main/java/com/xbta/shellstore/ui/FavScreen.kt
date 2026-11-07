package com.xbta.shellstore.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.data.FavItem
import com.xbta.shellstore.data.ListStyle
import com.xbta.shellstore.data.Folder
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Dimen
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale

private val TAG_COLORS = listOf(
    Color(0xFF2F6BFF), Color(0xFF12854A), Color(0xFFA9660A), Color(0xFFC3271F),
    Color(0xFF7C3AED), Color(0xFF0891B2), Color(0xFFBE185D), Color(0xFF4B5563)
)

private sealed interface FavDialog {
    data object QuickAdd : FavDialog
    data class PasteImport(val raw: String) : FavDialog
    data class NewFolder(val parentId: String?) : FavDialog
    data class RenameFolder(val id: String) : FavDialog
    data class FolderActions(val id: String) : FavDialog
    data class FavActions(val item: FavItem) : FavDialog
    data class ConfirmDelete(val item: FavItem) : FavDialog
    data class ConfirmDeleteMany(val ids: Set<String>) : FavDialog
    data class MoveTo(val item: FavItem) : FavDialog
    data class EditNote(val item: FavItem) : FavDialog
    data class EditTags(val item: FavItem) : FavDialog
    data class BatchMove(val ids: Set<String>) : FavDialog
    data class BatchTag(val ids: Set<String>) : FavDialog
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
    var viewOptions by remember { mutableStateOf(false) }
    val settings by Store.settings.collectAsState()
    val exporter = rememberSmdExporter()
    val ctx = LocalContext.current

    // 滑到底时手指还压在卡片上，容易误触右下角的「执行 / 打开」→ 记住滚动结束时刻，
    // FavRow 内部据此忽略刚停下那一瞬间的点击
    val listState = rememberLazyListState()
    var scrollEndAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) scrollEndAt = System.currentTimeMillis()
    }

    // 返回键优先退选择模式 / 关弹窗 / 退搜索，最后才交给系统
    BackHandler(enabled = multiMode) {
        multiMode = false
        selected = emptySet()
    }
    BackHandler(enabled = !multiMode && dialog != null) { dialog = null }
    BackHandler(enabled = !multiMode && dialog == null && searchMode) {
        searchMode = false
        query = ""
    }
    // 进了文件夹/切到标签视图后，返回键要回上一层，而不是冒泡到「再按一次退出」
    BackHandler(enabled = !multiMode && dialog == null && !searchMode && (currentFolder != null || view != 0)) {
        if (currentFolder != null) currentFolder = null else view = 0
    }
    val q = query.trim()
    fun hit(fi: FavItem): Boolean {
        if (q.isEmpty()) return true
        val e = fi.entry
        return e.title.contains(q, true) || e.body.contains(q, true) ||
            fi.note.contains(q, true) || e.tags.any { it.contains(q, true) }
    }

    // 「全选」只作用于当前视图看得见的条目，不跨文件夹
    val visibleIds: List<String> = when (view) {
        0 -> favs.itemsIn(currentFolder).filter { hit(it) }.map { it.entryId }
        2 -> favs.items.filter { hit(it) }.map { it.entryId }
        else -> emptyList()
    }

    Box(Modifier.fillMaxSize()) {
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
                    if (view != 1) {
                        val allSel = visibleIds.isNotEmpty() && selected.containsAll(visibleIds)
                        Text(
                            if (allSel) "取消全选" else "全选",
                            fontSize = TypeScale.body,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    selected = if (allSel) selected - visibleIds.toSet()
                                    else selected + visibleIds
                                }
                                .padding(horizontal = Space.sm, vertical = Space.xs)
                        )
                    }
                }
            )
        } else {
            TopBar(
                title = "收藏",
                actions = {
                    IconBtn("☰") { viewOptions = true }
                    IconBtn("⌕") { searchMode = true }
                    IconBtn("☑") { multiMode = true; selected = emptySet() }
                }
            )
        }
        Row(Modifier.padding(horizontal = Space.page, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip("文件夹", view == 0) { view = 0; currentFolder = null }
            FilterChip("标签", view == 1) { view = 1 }
            FilterChip("全部", view == 2) { view = 2; currentFolder = null }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = Space.page, end = Space.page, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(if (settings.listStyle == ListStyle.LIST) 0.dp else 7.dp)
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
                    FavRowAuto(
                        style = settings.listStyle,
                        item = fi,
                        onOpen = {
                            if (multiMode) selected = if (fi.entryId in selected) selected - fi.entryId else selected + fi.entryId
                            else onOpen(Page.Detail(fi.entryId))
                        },
                        onMenu = { dialog = FavDialog.FavActions(fi) },
                        multiMode = multiMode,
                        checked = fi.entryId in selected,
                        folded = settings.foldPreview,
                        scrollEndAt = scrollEndAt
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
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(9.dp).clip(RoundedCornerShape(5.dp))
                                    .background(TAG_COLORS[t.colorIdx % TAG_COLORS.size])
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(t.name, fontSize = TypeScale.small, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                        FavRowAuto(
                            style = settings.listStyle,
                            item = fi,
                            onOpen = {
                                if (multiMode) selected = if (fi.entryId in selected) selected - fi.entryId else selected + fi.entryId
                                else onOpen(Page.Detail(fi.entryId))
                            },
                            onMenu = { dialog = FavDialog.FavActions(fi) },
                            multiMode = multiMode,
                            checked = fi.entryId in selected,
                            folded = settings.foldPreview,
                            scrollEndAt = scrollEndAt
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
                    .padding(horizontal = Space.page, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                BatchBtn("打标签", selected.isNotEmpty()) { dialog = FavDialog.BatchTag(selected) }
                BatchBtn("移动到", selected.isNotEmpty()) { dialog = FavDialog.BatchMove(selected) }
                BatchBtn("取消收藏", selected.isNotEmpty(), danger = true) {
                    if (selected.any { Store.isLocal(it) }) {
                        dialog = FavDialog.ConfirmDeleteMany(selected)
                    } else {
                        Store.removeFavs(selected)
                        selected = emptySet()
                        multiMode = false
                    }
                }
            }
        }
    }

        // 右下角浮动新建：不遮挡列表，搜索/多选时自动隐藏
        if (!multiMode && !searchMode) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    // 稍微往里收：贴太近容易顶到屏幕边缘，也容易和列表最后一项的操作区抢位置
                    .padding(end = 30.dp, bottom = 34.dp)
                    .size(52.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { dialog = FavDialog.QuickAdd },
                contentAlignment = Alignment.Center
            ) {
                AppIcon("+", size = 28.dp, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    if (viewOptions) {
        ViewOptionsDialog(
            foldPreview = settings.foldPreview,
            onFoldChange = { Store.updateSettings(settings.copy(foldPreview = it)) },
            onDismiss = { viewOptions = false },
            listStyle = settings.listStyle,
            onStyleChange = { Store.updateSettings(settings.copy(listStyle = it)) }
        )
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
                    title = { Text(f?.name ?: "文件夹", fontSize = TypeScale.body) },
                    text = {
                        Column {
                            MenuItem("导出此文件夹") {
                                dialog = null
                                favs.folders.firstOrNull { it.id == d.id }?.let { f ->
                                    exporter(
                                        ExportPick.Folders(
                                            ids = favs.subtreeIds(f.id),
                                            includeRoot = false,
                                            label = f.name
                                        )
                                    )
                                }
                            }
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

            is FavDialog.QuickAdd -> {
                AddMenuDialog(
                    folderName = favs.folders.firstOrNull { it.id == currentFolder }?.name,
                    onDismiss = { dialog = null },
                    onNewEntry = {
                        dialog = null
                        onOpen(Page.Editor(null, currentFolder))
                    },
                    onNewFolder = {
                        input = ""
                        dialog = FavDialog.NewFolder(currentFolder)
                    },
                    onAddSource = {
                        dialog = null
                        onOpen(Page.Sources)
                    },
                    onPaste = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString().orEmpty()
                        dialog = FavDialog.PasteImport(text)
                    }
                )
            }

            is FavDialog.PasteImport -> {
                PasteImportDialog(
                    raw = d.raw,
                    folderId = currentFolder,
                    onDismiss = { dialog = null },
                    onOpenEditor = { draft ->
                        dialog = null
                        onOpen(Page.Editor(folderId = currentFolder, draft = draft))
                    },
                    onOpenSources = {
                        dialog = null
                        onOpen(Page.Sources)
                    }
                )
            }

            is FavDialog.ConfirmDelete -> {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("删除本地条目", fontSize = TypeScale.body) },
                    text = {
                        Text(
                            "「${d.item.entry.title}」是你自己新建的，不属于任何订阅源，" +
                                "删掉就再也找不回来了。",
                            fontSize = TypeScale.small
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            Store.removeFav(d.item.entryId)
                            dialog = null
                        }) { Text("仍然删除", color = Tones.danger) }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) { Text("取消") }
                    }
                )
            }

            is FavDialog.ConfirmDeleteMany -> {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("删除本地条目", fontSize = TypeScale.body) },
                    text = {
                        Text(
                            "所选的 ${d.ids.count { Store.isLocal(it) }} 条是你自己新建的，" +
                                "不属于任何订阅源，删掉就找不回来了。",
                            fontSize = TypeScale.small
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            Store.removeFavs(d.ids)
                            selected = emptySet()
                            multiMode = false
                            dialog = null
                        }) { Text("仍然删除", color = Tones.danger) }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) { Text("取消") }
                    }
                )
            }

            is FavDialog.FavActions -> {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(d.item.entry.title, fontSize = TypeScale.body, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column {
                            MenuItem("编辑内容") {
                                dialog = null
                                onOpen(Page.Editor(d.item.entryId, null))
                            }
                            MenuItem("移动到…") { dialog = FavDialog.MoveTo(d.item) }
                            MenuItem("打标签") { dialog = FavDialog.EditTags(d.item) }
                            MenuItem("写备注") { dialog = FavDialog.EditNote(d.item) }
                            MenuItem(
                                if (Store.isLocal(d.item.entryId)) "删除（本地条目）" else "取消收藏",
                                danger = true
                            ) {
                                if (Store.isLocal(d.item.entryId)) {
                                    dialog = FavDialog.ConfirmDelete(d.item)
                                } else {
                                    Store.removeFav(d.item.entryId); dialog = null
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } }
                )
            }

            is FavDialog.MoveTo -> {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text("移动到", fontSize = TypeScale.body) },
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

            is FavDialog.BatchTag -> BatchTagDialog(
                count = d.ids.size,
                allTags = favs.tags,
                onSave = { names ->
                    val ids = names.map { name ->
                        favs.tags.firstOrNull { it.name == name }?.id ?: Store.createTag(name)
                    }
                    Store.addTagsTo(d.ids, ids)
                    dialog = null
                    selected = emptySet()
                    multiMode = false
                },
                onDismiss = { dialog = null }
            )

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
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { AppIcon("▣", size = IconSize.card, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(10.dp))
        Text(
            folder.name, fontSize = TypeScale.small, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text("$count", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { onMenu() }, contentAlignment = Alignment.Center) {
            AppIcon("⋯", size = IconSize.action, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun FavRow(
    item: FavItem,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    multiMode: Boolean = false,
    checked: Boolean = false,
    folded: Boolean = false,
    /** 列表滚动结束时刻（毫秒）。刚停下 350ms 内的点击忽略，防滑动误触右下角的执行/打开 */
    scrollEndAt: Long = 0L
) {
    val e: Entry = item.entry
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = .07f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                if (checked) 1.5.dp else 1.dp,
                if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = .7f),
                RoundedCornerShape(Radius.md)
            )
            .clickable { onOpen() }
            .padding(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (multiMode) {
                CheckCircle(checked)
                Spacer(Modifier.width(Space.sm))
            }
            TypeBadge(e.type)
            Spacer(Modifier.width(Space.sm))
            Text(
                e.title, fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (Store.isLocal(item.entryId)) {
                Spacer(Modifier.width(Space.xs))
                Badge("本地", Tones.neutralFg, Tones.neutralBg)
            }
            if (folded && !multiMode) {
                RiskBadge(e.risk)
                Spacer(Modifier.width(Space.xs))
            }
            if (!multiMode) {
                Box(
                    Modifier
                        .size(Dimen.iconButton)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { onMenu() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon("⋯", size = IconSize.action, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (folded) return@Column
        val preview = if (e.body.isNotBlank()) e.body.replace("\n", " ⏎ ")
        else if (e.adb.isNotBlank()) e.adb else e.intent?.action ?: ""
        if (preview.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            CodeBlock(preview, maxLines = 1, fontSize = 12)
        }
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MutedBadge(typeLabel(e.type))
            Spacer(Modifier.width(Space.xs))
            RiskBadge(e.risk)
            if (!item.alive && !Store.isAlive(item.entryId) && !Store.isLocal(item.entryId)) {
                Spacer(Modifier.width(Space.xs))
                Badge("源已失效", Tones.danger, Tones.dangerBg)
            }
            if (item.note.isNotBlank()) {
                Spacer(Modifier.width(Space.xs))
                Text(
                    "备注：${item.note}", fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.outline, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            // 右下角快捷动作：与商店列表同一套逻辑（命令类执行、Intent 类打开）
            if (!multiMode) {
                if (item.note.isBlank()) Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(Space.xs))
                EntryQuickAction(e, scrollEndAt)
            }
        }
    }
}

/**
 * 收藏页的紧凑行：与 [com.xbta.shellstore.ui.EntryListRow] 同一套视觉（无卡片、细分隔线），
 * 但右侧是「⋯」菜单而不是收藏/编辑按钮——收藏页里的条目本来就都已收藏。
 */
@Composable
fun FavListRow(
    item: FavItem,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    multiMode: Boolean = false,
    checked: Boolean = false,
    folded: Boolean = false,
    scrollEndAt: Long = 0L,
    showDivider: Boolean = true
) {
    val e: Entry = item.entry
    fun tapAllowed(): Boolean = System.currentTimeMillis() - scrollEndAt >= 350L
    val dead = !item.alive && !Store.isAlive(item.entryId) && !Store.isLocal(item.entryId)
    val preview = when {
        item.note.isNotBlank() -> "备注：${item.note}"
        e.body.isNotBlank() -> e.body.replace("\n", " ⏎ ")
        e.adb.isNotBlank() -> e.adb
        e.intent?.action?.isNotBlank() == true -> e.intent!!.action
        else -> ""
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = .07f)
                else Color.Transparent)
                .clickable { if (tapAllowed()) onOpen() }
                .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiMode) {
                CheckCircle(checked)
                Spacer(Modifier.width(8.dp))
            }
            TypeBadge(e.type, size = 22)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (dead) {
                        Spacer(Modifier.width(6.dp))
                        Text("失效", fontSize = 9.sp, color = Tones.danger)
                    }
                    if (Store.isLocal(item.entryId)) {
                        Spacer(Modifier.width(5.dp))
                        Text("本地", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
                if (!folded && preview.isNotBlank()) {
                    Text(
                        preview,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!multiMode) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { if (tapAllowed()) onMenu() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon("⋯", size = 16.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (multiMode) 70.dp else 44.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = .14f))
            )
        }
    }
}

/** 按设置里的列表样式选一套画法 */
@Composable
fun FavRowAuto(
    style: String,
    item: FavItem,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    multiMode: Boolean = false,
    checked: Boolean = false,
    folded: Boolean = false,
    scrollEndAt: Long = 0L
) {
    if (style == ListStyle.LIST) {
        FavListRow(item, onOpen, onMenu, multiMode, checked, folded, scrollEndAt)
    } else {
        FavRow(item, onOpen, onMenu, multiMode, checked, folded, scrollEndAt)
    }
}

/**
 * 「新建」菜单：分组 + 每项一句说明 + 更大的行高。
 *
 * 之前是一列四个平铺文案、点哪算哪，「新建文件夹」还写成「收藏夹」，
 * 既容易误触也看不出这几项不是一类事。现在拆成「新建」与「导入」两组，
 * 取消按钮放在右下角，整行可点区域高 52dp。
 */
@Composable
private fun AddMenuDialog(
    folderName: String?,
    onDismiss: () -> Unit,
    onNewEntry: () -> Unit,
    onNewFolder: () -> Unit,
    onAddSource: () -> Unit,
    onPaste: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建", fontSize = TypeScale.body, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (!folderName.isNullOrBlank()) {
                    Text(
                        "将放进「$folderName」",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(6.dp))
                }
                AddMenuGroup("新建")
                AddMenuItem("新建命令", "命令 / 脚本 / Scheme / Intent", onNewEntry)
                AddMenuItem("新建文件夹", "用来分组存放收藏", onNewFolder)
                Spacer(Modifier.height(10.dp))
                AddMenuGroup("导入")
                AddMenuItem("添加订阅源", "从在线地址引入一整套命令", onAddSource)
                AddMenuItem("粘贴导入", "剪贴板里的 SMD / JSON / 命令", onPaste)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddMenuGroup(title: String) {
    Text(
        title,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun AddMenuItem(title: String, hint: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = TypeScale.small, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(hint, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun MenuItem(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = TypeScale.small,
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
        title = { Text(title, fontSize = TypeScale.body) },
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
                        textStyle = TextStyle(fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurface),
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
    val settings by Store.settings.collectAsState()
    val items = favs.itemsIn(folderId)
    // 拖动中列表会因为重排而重组，pointerInput 的闭包必须读到最新顺序，用这个兜住
    val live by rememberUpdatedState(items)
    val state = rememberLazyListState()
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var lastDragEnd by remember { mutableLongStateOf(0L) }

    fun move(from: Int, to: Int) {
        val ids = live.map { it.entryId }.toMutableList()
        if (from !in ids.indices || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        Store.reorderFavs(folderId, ids)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            favs.folders.firstOrNull { it.id == folderId }?.name ?: "收藏",
            onBack = onBack,
            actions = {
                if (items.size > 1) {
                    Text(
                        "长按拖动排序",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        if (items.isEmpty()) {
            EmptyState("这里是空的", "在条目详情页点 ☆ 就能收藏到这里")
            return@Column
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Space.page),
            verticalArrangement = Arrangement.spacedBy(if (settings.listStyle == ListStyle.LIST) 0.dp else 7.dp)
        ) {
            itemsIndexed(items, key = { _, fi -> fi.entryId }) { _, fi ->
                val dragging = fi.entryId == dragId
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            alpha = if (dragging) 0.95f else 1f
                            shadowElevation = if (dragging) 8f else 0f
                        }
                        .pointerInput(fi.entryId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragId = fi.entryId
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragId = null
                                    dragOffset = 0f
                                    lastDragEnd = System.currentTimeMillis()
                                },
                                onDragEnd = {
                                    dragId = null
                                    dragOffset = 0f
                                    lastDragEnd = System.currentTimeMillis()
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragOffset += delta.y
                                    val cur = live.indexOfFirst { it.entryId == fi.entryId }
                                    if (cur >= 0) {
                                        val next = state.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == cur + 1 }
                                        val prev = state.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == cur - 1 }
                                        // 越过相邻项一半高度就交换，并把偏移补偿回来，视觉上保持连续
                                        if (next != null && dragOffset > next.size / 2f) {
                                            move(cur, cur + 1)
                                            dragOffset -= next.size.toFloat()
                                        } else if (prev != null && dragOffset < -prev.size / 2f) {
                                            move(cur, cur - 1)
                                            dragOffset += prev.size.toFloat()
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    FavRowAuto(
                        style = settings.listStyle,
                        item = fi,
                        onOpen = {
                            // 松手瞬间的那次抬起不要当成点击打开详情
                            if (System.currentTimeMillis() - lastDragEnd > 300) {
                                onOpen(Page.Detail(fi.entryId))
                            }
                        },
                        onMenu = { onOpen(Page.Editor(fi.entryId, null)) }
                    )
                }
            }
        }
    }
}

@Composable
fun TagScreen(onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()
    var chosen by remember { mutableStateOf<String?>(null) }

    // 进入某个标签后，返回键先退回标签列表，而不是直接退出这一页
    BackHandler(enabled = chosen != null) { chosen = null }

    val tag = favs.tags.firstOrNull { it.id == chosen }
    if (tag == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("标签", onBack = onBack, actions = {
                Text("${favs.tags.size} 个", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            })
            if (favs.tags.isEmpty()) {
                EmptyState("还没有标签", "在收藏里选几条，点「打标签」就能创建")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.page, end = Space.page, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (settings.listStyle == ListStyle.LIST) 0.dp else 7.dp)
                ) {
                    items(favs.tags, key = { it.id }) { t ->
                        val n = favs.items.count { t.id in it.tags }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.md))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { chosen = t.id }
                                .padding(horizontal = Space.md, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(TAG_COLORS[t.colorIdx % TAG_COLORS.size])
                            )
                            Spacer(Modifier.width(Space.md))
                            Text(t.name, fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("$n 条", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(Space.xs))
                            AppIcon("›", size = IconSize.chevron, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    } else {
        val list = favs.items.filter { tag.id in it.tags }
        Column(Modifier.fillMaxSize()) {
            TopBar("#${tag.name}", onBack = { chosen = null }, actions = {
                Text("${list.size} 条", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            })
            if (list.isEmpty()) {
                EmptyState("这个标签下没有条目", "给收藏打上「${tag.name}」后就会出现在这里")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.page, end = Space.page, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (settings.listStyle == ListStyle.LIST) 0.dp else 7.dp)
                ) {
                    items(list, key = { it.entryId }) { fi ->
                        FavRowAuto(style = settings.listStyle, item = fi, onOpen = { onOpen(Page.Detail(fi.entryId)) }, onMenu = { onOpen(Page.Editor(fi.entryId, null)) })
                    }
                }
            }
        }
    }
}

@Composable
fun AllFavScreen(onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("全部收藏", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Space.page),
            verticalArrangement = Arrangement.spacedBy(if (settings.listStyle == ListStyle.LIST) 0.dp else 7.dp)
        ) {
            items(favs.items, key = { it.entryId }) { fi ->
                FavRowAuto(style = settings.listStyle, item = fi, onOpen = { onOpen(Page.Detail(fi.entryId)) }, onMenu = { onOpen(Page.Editor(fi.entryId, null)) })
            }
        }
    }
}

/** 批量打标签：勾选已有标签、可顺手新建。语义是「追加」，不覆盖原有标签 */
@Composable
private fun BatchTagDialog(
    count: Int,
    allTags: List<com.xbta.shellstore.data.Tag>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var checked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("给 $count 条打标签", fontSize = TypeScale.title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (allTags.isEmpty()) {
                    Text(
                        "还没有标签，在下面输入名称新建。",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                allTags.forEach { t ->
                    val on = t.id in checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable {
                                checked = if (on) checked - t.id else checked + t.id
                            }
                            .padding(vertical = 8.dp, horizontal = Space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.5.dp,
                                    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(5.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) AppIcon("✓", size = 11.dp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.width(Space.sm))
                        Text(t.name, fontSize = TypeScale.body, modifier = Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(Space.sm))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = TypeScale.body, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "输入名称可新建标签，与勾选的标签一起加到这 $count 条上（不覆盖原有）。",
                    fontSize = TypeScale.caption,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val names = checked.mapNotNull { id -> allTags.firstOrNull { it.id == id }?.name }.toMutableList()
                val extra = newName.trim()
                if (extra.isNotEmpty() && extra !in names) names.add(extra)
                if (names.isNotEmpty()) onSave(names) else onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
