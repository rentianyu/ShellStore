package com.xbta.shellstore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 最近使用：复制 / 执行 / 发送过的条目，最新在前。
 * 只存 id，展示时用当前源的实时内容——源更新后这里也跟着变，不会留旧快照。
 */
@Composable
fun RecentScreen(onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val recent by Store.recent.collectAsState()
    val entries by Store.entries.collectAsState()
    val favs by Store.favs.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    val list = remember(recent, entries) {
        val byId = entries.associateBy { it.id }
        recent.mapNotNull { byId[it.entryId] }
    }
    val gone = recent.size - list.size

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "最近使用",
            onBack = onBack,
            actions = {
                if (list.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { confirmClear = true }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "清空",
                            fontSize = TypeScale.small,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )

        if (list.isEmpty()) {
            EmptyState("还没有使用记录", "复制、执行或发送过的条目会出现在这里")
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.page, end = Space.page, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (gone > 0) {
                item {
                    Text(
                        "另有 $gone 条已从订阅源移除，不再显示",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            items(list, key = { it.id }) { e ->
                EntryRow(
                    entry = e,
                    fav = favs.isFav(e.id),
                    onClick = { onOpen(Page.Detail(e.id)) },
                    onToggleFav = { Store.toggleFav(e) }
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空最近使用", fontSize = TypeScale.title) },
            text = {
                Text(
                    "只删除使用记录，不会影响订阅源和收藏。",
                    fontSize = TypeScale.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.clearRecent()
                    confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}
