package com.xbta.shellstore.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.SearchText
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.data.ListStyle
import com.xbta.shellstore.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xbta.shellstore.ui.theme.Dimen
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

private val CAT_GLYPHS = listOf(">_", "▤", "◍", "⚙", "▦", "▶", "⚡", "◔", "⌨", "⚿", "◎", "↗", "⌘", "↻")

fun catGlyph(index: Int): String = CAT_GLYPHS.getOrElse(index) { "▪" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Page) -> Unit) {
    val entries by Store.entries.collectAsState()
    val cats by Store.categories.collectAsState()
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()
    val history by Store.searchHistory.collectAsState()

    // 这几个要跨"进详情再返回"保留，所以用 rememberSaveable 而不是 remember
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var viewOptions by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeSel by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() })
    ) { mutableStateOf<Set<String>>(emptySet()) }
    // 外部 deep link（shellstore://search?q=）要求搜索：把词灌进搜索框并进入搜索态
    val pendingSearch by Store.pendingSearch.collectAsState()
    LaunchedEffect(pendingSearch) {
        val kw = pendingSearch ?: return@LaunchedEffect
        query = kw
        searchMode = true
        Store.consumePendingSearch()
    }

    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 滑到底时手指常常还压在最后一张卡片上，会误触卡片里的「执行 / 打开」按钮，
    // 进而唤起别的 App 或被系统回收。这里记住滚动结束时刻，EntryRow 内部据此忽略
    // 滚动刚停下那一瞬间的点击（正常点击都在静止之后，不受影响）。
    val listState = rememberLazyListState()
    var scrollEndAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) scrollEndAt = System.currentTimeMillis()
    }

    // 搜索态按返回键先退出搜索，而不是直接退出 App
    BackHandler(enabled = searchMode) {
        searchMode = false
        query = ""
    }

    val results = remember(query, typeSel, entries, favs) {
        Store.search(
            query = query,
            types = typeSel.mapNotNull { EntryType.of(it) }.toSet(),
            risks = emptySet()
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (searchMode) {
            Box(Modifier.padding(12.dp, 8.dp)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索命令，空格分隔多个关键词",
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

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshScope.launch {
                    refreshing = true
                    val r = withContext(Dispatchers.IO) { Store.refreshAllSources() }
                    refreshing = false
                    Toast.makeText(
                        ctx,
                        if (r.second == 0) "已刷新 ${r.first} 个源"
                        else "已刷新 ${r.first} 个，${r.second} 个失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = Space.page, end = Space.page, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (settings.listStyle == ListStyle.LIST) 0.dp else 8.dp)
        ) {
            if (searchMode && query.isBlank() && history.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "最近搜索",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "清空",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { Store.clearSearchHistory() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        history.forEach { h -> FilterChip(h, false) { query = h } }
                    }
                }
            }
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
                    item {
                        EmptyState(
                            "没有找到匹配的命令",
                            "空格分隔的多个词是「同时包含」；中文可试拼音首字母（如 ly → 蓝牙）；也可去「我的 › 订阅源」添加更多来源"
                        )
                    }
                }
                items(results, key = { it.id }) { e ->
                    EntryRowAuto(style = settings.listStyle, entry = e,
                        fav = favs.isFav(e.id),
                        scrollEndAt = scrollEndAt,
                        onClick = {
                            // 点开某条结果才算一次「有效搜索」，避免逐字输入污染历史
                            Store.addSearchHistory(query)
                            onOpen(Page.Detail(e.id))
                        },
                        onToggleFav = { Store.toggleFavSmart(e) },
                        onEdit = { onOpen(Page.Editor(e.id)) },
                        folded = settings.foldPreview,
                        highlight = query
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
                            .clickable { onOpen(Page.AllCats) }
                            .padding(horizontal = Space.md, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("全部分类", fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("共 ${cats.size} 个分类", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Space.xs))
                        AppIcon("›", size = IconSize.chevron, color = MaterialTheme.colorScheme.outline)
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
                    EntryRowAuto(style = settings.listStyle, entry = e,
                        fav = favs.isFav(e.id),
                        onClick = { onOpen(Page.Detail(e.id)) },
                        onToggleFav = { Store.toggleFavSmart(e) },
                        onEdit = { onOpen(Page.Editor(e.id)) },
                        folded = settings.foldPreview
                    )
                }
            }
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
    // 分类网格与列表样式无关，间距固定
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
                            .clickable { onOpen(Page.CatList(c.name)) }
                            .padding(horizontal = Space.md, vertical = Space.md)
                    ) {
                        AppIcon(
                            catGlyph(idx),
                            size = 18.dp,
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
            contentPadding = PaddingValues(start = Space.page, end = Space.page, bottom = 16.dp),
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
                        AppIcon(
                            catGlyph(idx),
                            size = IconSize.card,
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
                    AppIcon("›", size = IconSize.chevron, color = MaterialTheme.colorScheme.outline)
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
    val listState = rememberLazyListState()
    var scrollEndAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) scrollEndAt = System.currentTimeMillis()
    }

    // 与全局搜索同一套规则：空格多词 AND、支持拼音首字母、标题命中优先
    val shown = remember(query, list) {
        val tokens = SearchText.tokenize(query)
        if (tokens.isEmpty()) list
        else list.mapNotNull { e -> SearchText.score(e, tokens)?.let { e to it } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    // 返回键：先把分类内的搜索清掉，再退出页面
    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    Column(Modifier.fillMaxSize()) {
        TopBar(cat, onBack = onBack, actions = { IconBtn("☰") { viewOptions = true } })
        Box(Modifier.padding(horizontal = Space.page, vertical = 4.dp)) {
            SearchField(query = query, onQueryChange = { query = it }, placeholder = "在「$cat」内搜索")
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = Space.page, end = Space.page, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (settings.listStyle == ListStyle.LIST) 0.dp else 8.dp)
        ) {
            item {
                Text("${shown.size} 条", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(shown, key = { it.id }) { e ->
                EntryRowAuto(style = settings.listStyle, entry = e,
                    fav = favs.isFav(e.id),
                    scrollEndAt = scrollEndAt,
                    onClick = { onOpen(Page.Detail(e.id)) },
                    onToggleFav = { Store.toggleFavSmart(e) },
                    onEdit = { onOpen(Page.Editor(e.id)) },
                    showCategory = false,
                    folded = settings.foldPreview,
                    highlight = query
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
