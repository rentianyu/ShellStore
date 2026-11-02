package com.xbta.shellstore.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xbta.shellstore.data.FavData
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 导出的范围 */
sealed interface ExportPick {
    /** 全部收藏 */
    data object All : ExportPick

    /** 指定文件夹（导出时自动带上子文件夹）；includeRoot = 是否连"未分类"一起导 */
    data class Folders(val ids: Set<String>, val includeRoot: Boolean, val label: String) : ExportPick
}

/** 建议文件名：shellstore-<标签>-2026.11.02.md */
fun suggestSmdName(pick: ExportPick): String {
    val date = SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date())
    val base = when (pick) {
        is ExportPick.All -> "全部收藏"
        is ExportPick.Folders -> pick.label
    }
    return "shellstore-${safeFileName(base)}-$date.md"
}

private fun safeFileName(s: String): String =
    s.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(40).ifBlank { "收藏" }

/**
 * 返回一个函数：传入导出范围 → 弹系统文件选择器（可改目录与文件名）→ 写文件。
 * 真正的过滤在选中文件之后才做，避免用户取消还要重算。
 */
@Composable
fun rememberSmdExporter(): (ExportPick) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ExportPick?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val pick = pending
        pending = null
        if (uri == null || pick == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { buildSmd(pick) }
            if (text.isEmpty()) {
                Toast.makeText(ctx, "没有可导出的条目", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    true
                }.getOrDefault(false)
            }
            Toast.makeText(
                ctx,
                if (ok) "已导出 ${text.length} 字符" else "写入文件失败",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    return remember {
        { pick: ExportPick ->
            pending = pick
            launcher.launch(suggestSmdName(pick))
        }
    }
}

/** 按范围生成 SMD 文本（子文件夹在调用时展开，避免 UI 层反复算） */
fun buildSmd(pick: ExportPick): String {
    val d = Store.favs.value
    return when (pick) {
        is ExportPick.All -> Store.exportFavsSmd()
        is ExportPick.Folders -> {
            val expanded = pick.ids.flatMap { d.subtreeIds(it) }.toSet()
            Store.exportFavsSmd(title = pick.label) { fi ->
                (fi.folderId != null && fi.folderId in expanded) ||
                    (pick.includeRoot && fi.folderId == null)
            }
        }
    }
}

/** 统计某范围内有多少条（对话框里实时显示） */
private fun countOf(favs: FavData, ids: Set<String>, includeRoot: Boolean): Int {
    val expanded = ids.flatMap { favs.subtreeIds(it) }.toSet()
    return favs.items.count {
        (it.folderId != null && it.folderId in expanded) || (includeRoot && it.folderId == null)
    }
}

/**
 * 选文件夹导出。默认全不选，勾了才有条数。
 */
@Composable
fun ExportFolderDialog(
    favs: FavData,
    onDismiss: () -> Unit,
    onConfirm: (ExportPick) -> Unit
) {
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var includeRoot by remember { mutableStateOf(false) }
    val rootCount = favs.items.count { it.folderId == null }
    val picked = countOf(favs, selected, includeRoot)
    val allFolders = favs.folders.sortedBy { favs.depthOf(it.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出哪些文件夹", fontSize = TypeScale.body) },
        text = {
            Column {
                if (rootCount > 0) {
                    PickRow(
                        label = "未分类",
                        count = rootCount,
                        indent = 0,
                        checked = includeRoot,
                        onClick = { includeRoot = !includeRoot }
                    )
                }
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    allFolders.forEach { f ->
                        val c = favs.countOf(f.id)
                        PickRow(
                            label = f.name,
                            count = c,
                            indent = favs.depthOf(f.id),
                            checked = f.id in selected,
                            onClick = {
                                selected = if (f.id in selected) selected - f.id else selected + f.id
                            }
                        )
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "勾选的文件夹会连同子文件夹一起导出；确定后还能选保存位置和文件名",
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val label = when {
                        selected.size == 1 && !includeRoot ->
                            favs.folders.firstOrNull { it.id == selected.first() }?.name ?: "收藏"
                        else -> "收藏${selected.size + (if (includeRoot) 1 else 0)}个文件夹"
                    }
                    onConfirm(ExportPick.Folders(selected, includeRoot, label))
                },
                enabled = picked > 0
            ) { Text(if (picked > 0) "导出 $picked 条" else "导出") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(ExportPick.All) }) { Text("全部导出") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun PickRow(
    label: String,
    count: Int,
    indent: Int,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = (indent * 14).dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(checked)
        Spacer(Modifier.width(Space.sm))
        Text(
            label,
            fontSize = TypeScale.small,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text("$count", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.outline)
    }
}
