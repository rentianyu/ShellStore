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
import androidx.compose.foundation.lazy.LazyRow
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
import com.xbta.shellstore.core.CategoryTree
import com.xbta.shellstore.core.EntryFilter
import com.xbta.shellstore.core.FilterState
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.SmdPath
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
    // 筛选状态放在 Store 里：切 Tab 再回来还在，商店页与收藏页各用各的
    val filter by Store.shopFilter.collectAsState()
    var filterOpen by remember { mutableStateOf(false) }
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

    // 搜索命中集（未套筛选）：筛选面板的选项从它来推导，避免列出「选了必 0 条」的项
    val searchHits = remember(query, entries) {
        Store.search(query = query, types = emptySet(), risks = emptySet())
    }

    // 搜索结果先按关键词排好相关度，再套筛选与排序
    val results = remember(searchHits, filter, favs) {
        EntryFilter.apply(
            searchHits,
            filter,
            isFav = { favs.isFav(it) },
            recentRank = Store.recentRankMap(),
            keepOrder = true
        )
    }

    // 非搜索态「全部命令」也受筛选影响
    val shownEntries = remember(entries, filter, favs) {
        EntryFilter.apply(
            entries,
            filter,
            isFav = { favs.isFav(it) },
            recentRank = Store.recentRankMap(),
            keepOrder = true
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (searchMode) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索命令，空格分隔多个关键词",
                    autoFocus = true,
                    onCancel = { searchMode = false; query = "" },
                    modifier = Modifier.weight(1f)
                )
                // 搜索完照样能筛：入口和首页顶栏那个漏斗是一个东西
                IconBtn("▽", badge = filter.activeCount > 0) { filterOpen = true }
            }
        } else {
            TopBar("Shell 商店", actions = {
                IconBtn("▽", badge = filter.activeCount > 0) { filterOpen = true }
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
                if (filter.activeCount > 0) {
                    item {
                        FilterBar(
                            state = filter,
                            onOpen = { filterOpen = true },
                            onClear = { Store.setShopFilter(FilterState()) },
                            total = entries.size,
                            shown = results.size
                        )
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
                item { CategoryTreePreview(cats, { c -> CategoryTree.countUnder(entries, c.id) }, { onOpen(Page.CatList(it)) }) }
                // 没筛选时不占位，入口只在顶栏漏斗；筛了就插一条状态条，免得以为列表是全部
                if (filter.activeCount > 0) {
                    item {
                        Box(Modifier.padding(top = Space.sm)) {
                            FilterBar(
                                state = filter,
                                onOpen = { filterOpen = true },
                                onClear = { Store.setShopFilter(FilterState()) },
                                total = entries.size,
                                shown = shownEntries.size
                            )
                        }
                    }
                }
                item {
                    Row(
                        Modifier
                            // 分类预览是「内部 5dp 密排」的一组，这里单独留白分组，
                            // 否则列表样式（item spacing = 0）下会跟上面的分类行粘成一片
                            .padding(top = Space.sm)
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
                        Text(
                            if (filter.activeCount > 0) "${shownEntries.size} / ${entries.size} 条"
                            else "${entries.size} 条",
                            fontSize = TypeScale.small,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(shownEntries, key = { it.id }) { e ->
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

    if (filterOpen) {
        FilterSheet(
            state = filter,
            onChange = { Store.setShopFilter(it) },
            onDismiss = { filterOpen = false },
            categories = if (searchMode && query.isNotBlank())
                cats.filter { c -> searchHits.any { SmdPath.isUnder(it.categoryId, c.id) } }
            else cats,
            sources = Store.activeSourceConfigs(),
            tags = EntryFilter.collectTags(if (searchMode) searchHits else entries),
            showFavFlag = true,
            showFavFlags = false
        )
    }
}

@Composable
fun AllCatsScreen(onBack: () -> Unit, onOpenCat: (String) -> Unit) {
    val cats by Store.categories.collectAsState()
    val entries by Store.entries.collectAsState()

    // 折叠状态：只存被折叠的分类 id（记得住、可保存）
    var collapsed by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() })
    ) { mutableStateOf<Set<String>>(emptySet()) }
    val shown = remember(cats, collapsed) { CategoryTree.visible(cats, collapsed) }
    val depthMax = remember(cats) { cats.maxOfOrNull { it.depth } ?: 0 }

    Column(Modifier.fillMaxSize()) {
        TopBar("全部分类", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.page, end = Space.page, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Text(
                    if (depthMax > 0)
                        "共 ${cats.size} 个分类（最深 ${depthMax + 1} 层）· ${entries.size} 条命令"
                    else
                        "共 ${cats.size} 个分类 · ${entries.size} 条命令",
                    fontSize = TypeScale.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.xs, start = Space.xs)
                )
            }
            items(shown, key = { it.id }) { c ->
                CategoryRow(
                    cat = c,
                    count = CategoryTree.countUnder(entries, c.id),
                    index = cats.indexOf(c),
                    hasChild = cats.any { it.parentId == c.id },
                    collapsed = c.id in collapsed,
                    onToggle = {
                        collapsed = if (c.id in collapsed) collapsed - c.id else collapsed + c.id
                    },
                    onClick = { onOpenCat(c.id) }
                )
            }
            item { Spacer(Modifier.height(Space.sm)) }
        }
    }
}

@Composable
fun CatListScreen(cat: String, onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val entries by Store.entries.collectAsState()
    val cats by Store.categories.collectAsState()
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()
    var viewOptions by remember { mutableStateOf(false) }
    // 分类即目录：这一层和它下面所有子目录的条目都算
    val list = remember(cat, entries) { entries.filter { SmdPath.isUnder(it.categoryId, cat) } }
    val catObj = remember(cat) { Store.categoryById(cat) }
    val children = remember(cat) { Store.childCategories(cat) }
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
        val catTitle = catObj?.name ?: SmdPath.leaf(cat)
        TopBar(catTitle, onBack = onBack, actions = { IconBtn("☰") { viewOptions = true } })
        // 面包屑：说明当前在目录树的哪一层，上几级可点
        if (catObj != null) {
            Box(Modifier.padding(horizontal = Space.page, vertical = 2.dp)) {
                CategoryBreadcrumb(
                    cat = catObj,
                    onOpenCat = { onOpen(Page.CatList(it)) },
                    onOpenAll = { onOpen(Page.AllCats) }
                )
            }
        }
        // 子目录：横向排列，点了进下一层
        if (children.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = Space.page),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(children, key = { it.id }) { ch ->
                    val n = CategoryTree.countUnder(entries, ch.id)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(Radius.md))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onOpen(Page.CatList(ch.id)) }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            ch.icon.ifBlank { catGlyph(cats.indexOf(ch)) },
                            size = 12.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(ch.name, fontSize = TypeScale.caption, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(4.dp))
                        Text("$n", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = Space.page, vertical = 4.dp)) {
            SearchField(query = query, onQueryChange = { query = it }, placeholder = "在「${catObj?.name ?: cat}」内搜索")
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
