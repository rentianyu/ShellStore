package com.xbta.shellstore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xbta.shellstore.core.CommandRenderer
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 小部件选条目：添加小部件时由系统调起，也可以在 App 内「桌面小部件」页改绑时复用。
 */
@Composable
fun WidgetPickerScreen(
    title: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit
) {
    val entries by Store.entries.collectAsState()
    var query by remember { mutableStateOf("") }

    val shown = remember(query, entries) {
        if (query.isBlank()) entries else Store.search(query = query, types = emptySet(), risks = emptySet())
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack = onBack)
        Box(Modifier.padding(horizontal = Space.page, vertical = 6.dp)) {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "搜索条目",
                autoFocus = false
            )
        }
        if (shown.isEmpty()) {
            EmptyState(
                if (query.isBlank()) "还没有命令" else "没找到匹配的命令",
                if (query.isBlank()) "先去商店添加一条，再回来指定给小部件" else "换个关键词试试"
            )
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.page, end = Space.page, top = 4.dp, bottom = 20.dp)
        ) {
            items(shown, key = { it.id }) { e ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .clickable { onPick(e.id) }
                        .padding(horizontal = Space.md, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.title,
                            fontSize = TypeScale.small,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(e.type.id, fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        runCatching {
                            CommandRenderer.primaryText(e, e.params.associate { it.key to it.default }, -1)
                        }.getOrElse { e.body }.lineSequence().firstOrNull().orEmpty(),
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
