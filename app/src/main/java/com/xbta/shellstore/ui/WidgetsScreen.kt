package com.xbta.shellstore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xbta.shellstore.ShellWidgetProvider
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 桌面小部件管理：列出桌面上已有的实例，各自绑定哪条命令、点一下改绑。
 * 新加小部件时由系统的配置页完成绑定，这里管「已放上去的想换一条」。
 */
@Composable
fun WidgetsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings by Store.settings.collectAsState()
    val entries by Store.entries.collectAsState()
    var picking by remember { mutableStateOf<Int?>(null) }

    val ids = remember(settings.widgetEntries) {
        runCatching { ShellWidgetProvider.ids(ctx).toList() }.getOrDefault(emptyList())
    }

    picking?.let { id ->
        WidgetPickerScreen(
            title = "小部件要执行哪条命令",
            onPick = { entryId ->
                Store.setWidgetEntry(id, entryId)
                ShellWidgetProvider.update(ctx, id)
                picking = null
            },
            onBack = { picking = null }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("桌面小部件", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "长按桌面 → 小组件 → 找到 ShellStore，拖一个到桌面并选命令；已经放上去的想换命令就点下面改。",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.sm)
            )

            if (ids.isEmpty()) {
                GroupCard {
                    Column(Modifier.padding(Space.md)) {
                        Text("桌面上还没有小部件", fontSize = TypeScale.small)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "放上去之后这里会列出每个小部件当前绑定的命令",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                GroupCard {
                    ids.forEachIndexed { i, id ->
                        if (i > 0) Divider()
                        val entryId = Store.widgetEntryOf(id)
                        val entry = entries.firstOrNull { it.id == entryId }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picking = id }
                                .padding(horizontal = Space.md, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "小部件 #$id",
                                    fontSize = TypeScale.caption,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.weight(1f))
                                Text("改", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when {
                                    entryId.isBlank() -> "未指定命令"
                                    entry == null -> "命令已失效（源里删了或换了源）"
                                    else -> entry.title
                                },
                                fontSize = TypeScale.small,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
