package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Dimen
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

private val CAT_GLYPHS = listOf(">_", "▤", "◍", "⚙", "▦", "▶", "⚡", "◔", "⌨", "⚿", "◎", "↗", "⌘", "↻")

fun catGlyph(index: Int): String = CAT_GLYPHS.getOrElse(index) { "▪" }

@Composable
fun HomeScreen(onOpen: (Page) -> Unit) {
    val entries by Store.entries.collectAsState()
    val cats by Store.categories.collectAsState()
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()

    var searchMode by remember { mutableStateOf(false) }
    var viewOptions by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var typeSel by remember { mutableStateOf<Set<String>>(emptySet()) }

    val results = remember(query, typeSel, entries) {
        val kw = query.trim().lowercase()
        entries.filter { e ->
            (typeSel.isEmpty() || e.type.id in typeSel) &&
                (kw.isEmpty() ||
                    e.title.lowercase().contains(kw) ||
                    e.body.lowercase().contains(kw) ||
                    e.desc.lowercase().contains(kw) ||
                    e.tags.any { it.lowercase().contains(kw) } ||
                    e.categoryId.lowercase().contains(kw))
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (searchMode) {
            Box(Modifier.padding(12.dp, 8.dp)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    autoFocus = true,
                    onCancel = { searchMode = false; query = "" }
                )
            }
        } else {
            TopBar("Shell 商店", actions = {
                IconBtn("☰") { viewOptions = true }
                IconBtn("⌕") { searchMode = true }
            })
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchMode) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 4.dp)) {
                        FilterChip("全部", typeSel.isEmpty()) { typeSel = emptySet() }
                        FilterChip("shell", "shell" in typeSel) {
                            typeSel = if ("shell" in typeSel) typeSel - "shell" else typeSel + "shell"
                        }
                        FilterChip("intent", "intent" in typeSel) {
                            typeSel = if ("intent" in typeSel) typeSel - "intent" else typeSel + "intent"
                        }
                        FilterChip("activity", "activity" in typeSel) {
                            typeSel = if ("activity" in typeSel) typeSel - "activity" else typeSel + "activity"
                        }
                    }
                }
                item {
                    Text(
                        if (query.isBlank()) "共 ${entries.size} 条"
                        else "找到 ${results.size} 条",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                if (results.isEmpty()) {
                    item { EmptyState("没有找到匹配的命令", "换个关键词，或去「我的 › 订阅源」添加更多来源") }
                }
                items(results, key = { it.id }) { e ->
                    EntryRow(
                        e,
                        fav = favs.isFav(e.id),
                        onClick = { onOpen(Page.Detail(e.id)) },
                        onToggleFav = { Store.toggleFav(e) },
                        folded = settings.foldPreview
                    )
                }
            } else {
                item { CategoryGrid(cats, entries, onOpen) }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(Radius.md))
                            .clickable { onOpen(Page.AllCats) }
                            .padding(horizontal = Space.md, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("全部分类", fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("共 ${cats.size} 个分类", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Space.xs))
                        Text("›", fontSize = IconSize.chevron, color = MaterialTheme.colorScheme.outline)
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("全部命令", fontSize = TypeScale.title, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${entries.size} 条", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(entries, key = { it.id }) { e ->
                    EntryRow(
                        e,
                        fav = favs.isFav(e.id),
                        onClick = { onOpen(Page.Detail(e.id)) },
                        onToggleFav = { Store.toggleFav(e) },
                        folded = settings.foldPreview
                    )
                }
            }
        }
    }

    if (viewOptions) {
        ViewOptionsDialog(
            foldPreview = settings.foldPreview,
            onFoldChange = { Store.updateSettings(settings.copy(foldPreview = it)) },
            onDismiss = { viewOptions = false }
        )
    }
}

@Composable
private fun CategoryGrid(
    cats: List<com.xbta.shellstore.core.Category>,
    entries: List<Entry>,
    onOpen: (Page) -> Unit
) {
    val shown = cats.take(6)
    // 分类少时用 2 列，避免第二行只剩一个卡片、右边留一大片空白
    val cols = if (shown.size <= 4) 2 else 3
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.chunked(cols).forEach { rowCats ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowCats.forEach { c ->
                    val idx = cats.indexOf(c)
                    val count = entries.count { it.categoryId == c.name }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(Radius.md))
                            .clickable { onOpen(Page.CatList(c.name)) }
                            .padding(horizontal = Space.md, vertical = Space.md)
                    ) {
                        Text(
                            catGlyph(idx),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            c.name,
                            fontSize = TypeScale.body,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("$count 条", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AllCatsScreen(onBack: () -> Unit, onOpenCat: (String) -> Unit) {
    val cats by Store.categories.collectAsState()
    val entries by Store.entries.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopBar("全部分类", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Text(
                    "共 ${cats.size} 个分类 · ${entries.size} 条命令",
                    fontSize = TypeScale.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.xs, start = Space.xs)
                )
            }
            items(cats) { c ->
                val idx = cats.indexOf(c)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(Radius.md))
                        .clickable { onOpenCat(c.name) }
                        .padding(horizontal = Space.md, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            catGlyph(idx),
                            fontSize = IconSize.card,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(Space.md))
                    Text(c.name, fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "${entries.count { it.categoryId == c.name }}",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Space.xs))
                    Text("›", fontSize = IconSize.chevron, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
fun CatListScreen(cat: String, onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val entries by Store.entries.collectAsState()
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()
    var viewOptions by remember { mutableStateOf(false) }
    val list = remember(cat, entries) { entries.filter { it.categoryId == cat } }
    var query by remember { mutableStateOf("") }

    val shown = remember(query, list) {
        if (query.isBlank()) list
        else list.filter {
            it.title.contains(query, true) || it.body.contains(query, true) || it.tags.any { t -> t.contains(query, true) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(cat, onBack = onBack, actions = { IconBtn("☰") { viewOptions = true } })
        Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            SearchField(query = query, onQueryChange = { query = it }, placeholder = "在「$cat」内搜索")
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("${shown.size} 条", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(shown, key = { it.id }) { e ->
                EntryRow(
                    e,
                    fav = favs.isFav(e.id),
                    onClick = { onOpen(Page.Detail(e.id)) },
                    onToggleFav = { Store.toggleFav(e) },
                    showCategory = false,
                    folded = settings.foldPreview
                )
            }
        }
    }

    if (viewOptions) {
        ViewOptionsDialog(
            foldPreview = settings.foldPreview,
            onFoldChange = { Store.updateSettings(settings.copy(foldPreview = it)) },
            onDismiss = { viewOptions = false }
        )
    }
}
