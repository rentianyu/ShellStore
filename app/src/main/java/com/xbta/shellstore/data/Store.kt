package com.xbta.shellstore.data

import android.content.Context
import com.xbta.shellstore.core.*
import com.xbta.shellstore.core.LegalText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** 启动时检测到的订阅源更新。text 是拉到的新内容，用户点确定才真正写入 */
data class SourceDiff(
    val id: String,
    val name: String,
    val oldCount: Int,
    val newCount: Int,
    val text: String,
    val via: String = ""
) {
    val delta: Int get() = newCount - oldCount
    val countChanged: Boolean get() = newCount != oldCount
}

/**
 * 全局状态。单例 object + StateFlow，不引 ViewModel。
 * 所有磁盘 IO 走 Repo，调用方负责切线程（首版数据量小，主线程读 30 条 < 5ms）。
 */
object Store {

    const val OFFICIAL_ID = "official"

    /** 本地自建条目的 id 前缀，带此前缀的条目只存在于收藏里，不属于任何源 */
    const val LOCAL_PREFIX = "local-"
    /** AI 整理命令库（原「官方命令库」）：托管在 ai 仓库 ShellStore/sources/ 下，随 R2 同步到 f.xbta.cc */
    const val OFFICIAL_URL = "https://f.xbta.cc/ai/ShellStore/sources/official.md"
    /** AI 整理命令库的显示名 */
    const val OFFICIAL_NAME = "AI 整理命令库"

    /** 系列源是否参与解析：总开关关着时，系列源保留配置但不加载 */
    private fun seriesActive(cfg: SourceConfig): Boolean =
        cfg.series.isBlank() || _settings.value.xbtaSeries

    /** 老地址，升级时用来识别需要迁移的已配置源 */
    private const val OFFICIAL_URL_LEGACY =
        "https://raw.githubusercontent.com/rentianyu/ShellStore/main/sources/official.md"

    /** 最近使用最多保留条数 */
    private const val RECENT_MAX = 60

    /** 搜索历史最多保留条数 */
    private const val SEARCH_MAX = 10

    private var ctx: Context? = null

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _sourceName = MutableStateFlow("内置命令集")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    private val _sourceVersion = MutableStateFlow("")
    val sourceVersion: StateFlow<String> = _sourceVersion.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** 用户点了「不同意」后进入本次会话的基础模式：离线可用，联网关闭 */
    private val _basicMode = MutableStateFlow(false)
    val basicMode: StateFlow<Boolean> = _basicMode.asStateFlow()

    /** 是否允许联网：同意协议且不在基础模式 */
    val netAllowed: Boolean
        get() = !_basicMode.value && _settings.value.agreedVersion >= LegalText.LEGAL_VERSION

    /** 同意协议（记版本号，条款升级后会再次要求同意） */
    fun agreeLegal() {
        _basicMode.value = false
        updateSettings(_settings.value.copy(agreedVersion = LegalText.LEGAL_VERSION))
    }

    /** 不同意：不退出 App，进入离线基础模式 */
    fun declineLegal() {
        _basicMode.value = true
    }

    /** 从基础模式恢复联网（设置页里的入口） */
    fun exitBasicMode() {
        agreeLegal()
    }

    private val _favs = MutableStateFlow(FavData())
    val favs: StateFlow<FavData> = _favs.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _pendingUpdates = MutableStateFlow<List<SourceDiff>>(emptyList())
    val pendingUpdates: StateFlow<List<SourceDiff>> = _pendingUpdates.asStateFlow()

    @Volatile private var updatesChecked = false

    private val _warnings = MutableStateFlow<List<ParseWarning>>(emptyList())
    val warnings: StateFlow<List<ParseWarning>> = _warnings.asStateFlow()

    private val _sources = MutableStateFlow<List<SourceConfig>>(emptyList())
    val sources: StateFlow<List<SourceConfig>> = _sources.asStateFlow()

    /** 「小贝塔订阅源」可选源清单（来自 ai 仓库的 index.md，本身就是一份 SMD） */
    private val _seriesItems = MutableStateFlow<List<SeriesSources.Item>>(emptyList())
    val seriesItems: StateFlow<List<SeriesSources.Item>> = _seriesItems.asStateFlow()

    /** 清单加载错误（空串表示正常） */
    private val _seriesError = MutableStateFlow("")
    val seriesError: StateFlow<String> = _seriesError.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentItem>>(emptyList())
    val recent: StateFlow<List<RecentItem>> = _recent.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    // ---------------- 初始化 ----------------

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        val c = ctx!!

        _settings.value = Repo.loadSettings(c)
        // WebDAV 密码单独用 Keystore 加密存，不进 settings.json
        webdavPass = SecretBox.load(c)

        var loadedSources = Repo.loadSourceConfigs(c)
        // 老配置迁移：官方源地址换成 f.xbta.cc，并归入「小贝塔订阅源」、改名为 AI 整理命令库。
        // 原来源是启用的，就把系列总开关一并打开，否则迁移完商店会变空。
        val oldOfficial = loadedSources.firstOrNull { it.id == OFFICIAL_ID }
        if (oldOfficial != null) {
            loadedSources = loadedSources.map {
                if (it.id == OFFICIAL_ID) it.copy(
                    url = OFFICIAL_URL,
                    name = OFFICIAL_NAME,
                    series = SeriesSources.SERIES
                ) else it
            }
            Repo.saveSourceConfigs(c, loadedSources)
            if (oldOfficial.enabled && !_settings.value.xbtaSeries) {
                _settings.value = _settings.value.copy(xbtaSeries = true)
                Repo.saveSettings(c, _settings.value)
            }
        }
        _sources.value = loadedSources

        reloadAll()

        val favData = Repo.loadFavs(c)
        _favs.value = favData

        _recent.value = Repo.loadRecent(c)
        _searchHistory.value = Repo.loadSearchHistory(c)

        _loaded.value = true
    }

    // ---------------- 最近使用 ----------------

    /** 复制 / 执行 / 发送成功后调用。同一条只保留最新一次，最多 60 条。 */
    fun recordRecent(entryId: String) {
        if (entryId.isBlank()) return
        val now = System.currentTimeMillis()
        val next = (listOf(RecentItem(entryId, now)) + _recent.value.filter { it.entryId != entryId })
            .take(RECENT_MAX)
        _recent.value = next
        ctx?.let { Repo.saveRecent(it, next) }
    }

    fun clearRecent() {
        _recent.value = emptyList()
        ctx?.let { Repo.saveRecent(it, emptyList()) }
    }

    // ---------------- 搜索历史 ----------------

    /** 用户点开某条搜索结果时记一次，避免逐字输入把半截词写进历史 */
    fun addSearchHistory(q: String) {
        val kw = q.trim()
        if (kw.length < 2) return
        val next = (listOf(kw) + _searchHistory.value.filter { !it.equals(kw, ignoreCase = true) })
            .take(SEARCH_MAX)
        _searchHistory.value = next
        ctx?.let { Repo.saveSearchHistory(it, next) }
    }

    fun removeSearchHistory(q: String) {
        val next = _searchHistory.value.filter { it != q }
        _searchHistory.value = next
        ctx?.let { Repo.saveSearchHistory(it, next) }
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        ctx?.let { Repo.saveSearchHistory(it, emptyList()) }
    }

    /** 重新解析所有启用的源，合并成一份条目与分类 */
    fun reloadAll() {
        val c = ctx ?: return
        val allEntries = mutableListOf<Entry>()
        val allCats = LinkedHashMap<String, Category>()
        val warns = mutableListOf<ParseWarning>()
        var mainName = "命令库"
        var mainVer = ""
        val updated = mutableListOf<SourceConfig>()

        _sources.value.forEach { cfg ->
            if (!cfg.enabled || !seriesActive(cfg)) {
                updated.add(cfg.copy(count = 0))
                return@forEach
            }
            val text = SourceCache.read(c, cfg.id)
            if (text.isBlank()) {
                updated.add(cfg.copy(count = 0, error = "内容为空，请刷新"))
                return@forEach
            }
            val r = SmdParser.parse(text, cfg.id, cfg.name)
            allEntries.addAll(r.source.entries)
            r.source.categories.forEach { cat ->
                if (!allCats.containsKey(cat.name)) allCats[cat.name] = cat
            }
            warns.addAll(r.warnings)
            if (mainVer.isBlank()) {
                mainName = r.source.name
                mainVer = r.source.meta.version
            }
            updated.add(cfg.copy(count = r.source.entries.size, error = ""))
        }

        _entries.value = allEntries
        _categories.value = allCats.values.toList()
        _sourceName.value = mainName
        _sourceVersion.value = mainVer
        _warnings.value = warns
        _sources.value = updated
        Repo.saveSourceConfigs(c, updated)
    }

    // ---------------- 源管理 ----------------

    // ---------------- 小贝塔订阅源 ----------------

    /** 拉系列清单（会联网，调用方切 IO 线程）。返回错误信息，null 表示成功 */
    fun loadSeriesCatalog(): String? {
        val (items, err) = SeriesSources.fetch()
        _seriesItems.value = items
        _seriesError.value = err.orEmpty()
        return err
    }

    /**
     * 刷新系列：重新拉清单，再逐个刷新系列下已启用的源。
     * 界面上的条数、错误状态由 refresh() 内部更新。会联网，调用方切 IO 线程。
     */
    fun refreshSeries(): String? {
        val err = loadSeriesCatalog()
        var ok = 0
        var fail = 0
        _sources.value
            .filter { it.series == SeriesSources.SERIES && it.enabled }
            .forEach { cfg ->
                if (refresh(cfg.id) == null) ok++ else fail++
            }
        return when {
            err != null && ok == 0 && fail == 0 -> err
            fail > 0 -> "清单已更新，$ok 个源刷新成功、$fail 个失败"
            ok == 0 -> "清单已更新"
            else -> "已刷新：清单 + $ok 个源"
        }
    }

    /** 总开关：关掉后系列源保留配置但不参与解析，重新打开立即恢复 */
    fun setXbtaSeries(on: Boolean) {
        if (_settings.value.xbtaSeries == on) return
        updateSettings(_settings.value.copy(xbtaSeries = on))
        reloadAll()
    }

    /**
     * 系列清单里某一项的当前配置（没添加过就返回 null）。
     * 按 url 认源而不是按 series 标记——用户之前手动加过的同一个源也要能对上，
     * 否则会重复添加出两条一样的源。
     */
    fun seriesConfigOf(item: SeriesSources.Item): SourceConfig? {
        val u = item.url.trim()
        return _sources.value.firstOrNull { it.url.trim() == u }
    }

    /**
     * 打开/关闭系列里的某个源。还没添加过时，打开会先拉取内容再添加。
     * 之前手动加过的同一个源会被就地归入系列，不会重复添加。
     * 会联网，调用方切 IO 线程。
     */
    fun toggleSeriesItem(item: SeriesSources.Item, on: Boolean): String? {
        val exist = seriesConfigOf(item)
        if (exist == null) {
            return if (on) addRemote(item.name, item.url, SeriesSources.SERIES) else null
        }
        // 老的手动源：补上 series 标记，让它回到系列清单里统一管理
        if (exist.series != SeriesSources.SERIES) {
            _sources.value = _sources.value.map {
                if (it.id == exist.id) it.copy(series = SeriesSources.SERIES) else it
            }
            ctx?.let { Repo.saveSourceConfigs(it, _sources.value) }
        }
        if (exist.enabled != on) toggleSource(exist.id, on)
        return null
    }

    fun addRemote(name: String, url: String, series: String = ""): String? {
        val c = ctx ?: return "未初始化"
        if (!netAllowed) return "请先同意《用户协议》和《隐私政策》"
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "地址必须以 http:// 或 https:// 开头"
        val id = "r" + UUID.randomUUID().toString().replace("-", "").take(8)
        val r = if (Mirrors.isGitHub(url)) {
            Mirrors.fetch(url, prefer = _settings.value.sourceMirror, validate = { looksLikeSmd(it) })
        } else {
            Net.get(url)
        }
        if (!r.ok) return r.error ?: "拉取失败"
        if (r.via.isNotBlank()) rememberMirror(r.via)
        val text = r.text.orEmpty()
        if (text.isBlank()) return "服务器返回空内容"
        val parsed = SmdParser.parse(text, id, name)
        if (parsed.source.entries.isEmpty()) return "解析后没有条目，确认链接指向 SMD 文件"
        SourceCache.write(c, id, text)
        val cfg = SourceConfig(
            id = id,
            name = name.ifBlank { parsed.source.name.ifBlank { "在线源" } },
            type = SourceType.REMOTE,
            url = url,
            enabled = true,
            lastFetch = System.currentTimeMillis(),
            etag = r.etag,
            count = parsed.source.entries.size,
            series = series
        )
        _sources.value = _sources.value + cfg
        Repo.saveSourceConfigs(c, _sources.value)
        reloadAll()
        return null
    }

    fun refresh(id: String): String? {
        val c = ctx ?: return "未初始化"
        if (!netAllowed) return "请先同意《用户协议》和《隐私政策》"
        val cfg = _sources.value.firstOrNull { it.id == id } ?: return "源不存在"
        if (cfg.type != SourceType.REMOTE) {
            reloadAll(); return null
        }
        val r = if (Mirrors.isGitHub(cfg.url) && cfg.etag.isBlank()) {
            val f = Mirrors.fetch(cfg.url, prefer = _settings.value.sourceMirror, validate = { looksLikeSmd(it) })
            if (f.ok) rememberMirror(f.via)
            f
        } else {
            Net.get(cfg.url, cfg.etag)
        }
        if (r.notModified) {
            _sources.value = _sources.value.map {
                if (it.id == id) it.copy(lastFetch = System.currentTimeMillis(), error = "") else it
            }
            Repo.saveSourceConfigs(c, _sources.value)
            return null
        }
        if (!r.ok) {
            val msg = r.error ?: "拉取失败"
            _sources.value = _sources.value.map { if (it.id == id) it.copy(error = msg) else it }
            return msg
        }
        val text = r.text.orEmpty()
        if (text.isBlank()) return "服务器返回空内容"
        SourceCache.write(c, id, text)
        val parsed = SmdParser.parse(text, id, cfg.name)
        _sources.value = _sources.value.map {
            if (it.id == id) it.copy(
                lastFetch = System.currentTimeMillis(),
                etag = r.etag,
                count = parsed.source.entries.size,
                error = ""
            ) else it
        }
        Repo.saveSourceConfigs(c, _sources.value)
        reloadAll()
        return null
    }

    /**
     * 刷新所有已启用的在线源（商店页下拉刷新用）。
     * 返回 (成功数, 失败数)；单个源失败不影响其它源。
     */
    fun refreshAllSources(): Pair<Int, Int> {
        var ok = 0
        var bad = 0
        _sources.value.filter { it.enabled && it.type == SourceType.REMOTE }.forEach { cfg ->
            if (refresh(cfg.id) == null) ok++ else bad++
        }
        reloadAll()
        return ok to bad
    }

    fun importLocal(name: String, text: String, fileName: String = ""): String? {
        val c = ctx ?: return "未初始化"
        if (text.isBlank()) return "文件内容为空"
        val id = "l" + UUID.randomUUID().toString().replace("-", "").take(8)
        val parsed = SmdParser.parse(text, id, name.ifBlank { fileName })
        if (parsed.source.entries.isEmpty()) return "解析后没有条目，确认是 SMD / Markdown 清单"
        SourceCache.write(c, id, text)
        val cfg = SourceConfig(
            id = id,
            name = name.ifBlank { parsed.source.name.ifBlank { fileName.ifBlank { "本地源" } } },
            type = SourceType.LOCAL,
            enabled = true,
            lastFetch = System.currentTimeMillis(),
            count = parsed.source.entries.size
        )
        _sources.value = _sources.value + cfg
        Repo.saveSourceConfigs(c, _sources.value)
        reloadAll()
        return null
    }

    fun removeSource(id: String) {
        val c = ctx ?: return
        _sources.value = _sources.value.filterNot { it.id == id }
        SourceCache.remove(c, id)
        Repo.saveSourceConfigs(c, _sources.value)
        reloadAll()
    }

    fun toggleSource(id: String, on: Boolean) {
        val c = ctx ?: return
        _sources.value = _sources.value.map { if (it.id == id) it.copy(enabled = on) else it }
        Repo.saveSourceConfigs(c, _sources.value)
        reloadAll()
    }

    /**
     * 启动时检查订阅源是否有新版本：拉下来先不写入，只算出差异，等用户确认。
     * 阻塞调用，放 IO 线程。
     */
    fun checkSourceUpdates() {
        if (updatesChecked) return
        updatesChecked = true
        val c = ctx ?: return
        val diffs = mutableListOf<SourceDiff>()
        _sources.value
            .filter { it.enabled && it.type == SourceType.REMOTE && it.url.isNotBlank() }
            .forEach { cfg ->
                val r = Mirrors.fetch(
                    cfg.url,
                    prefer = _settings.value.sourceMirror,
                    validate = { looksLikeSmd(it) }
                )
                if (!r.ok) return@forEach
                val newText = r.text.orEmpty()
                if (newText.isBlank()) return@forEach
                val oldText = SourceCache.read(c, cfg.id)
                if (newText == oldText) return@forEach
                val newCount = SmdParser.parse(newText, cfg.id, cfg.name).source.entries.size
                val oldCount = if (oldText.isBlank()) 0
                else SmdParser.parse(oldText, cfg.id, cfg.name).source.entries.size
                diffs.add(SourceDiff(cfg.id, cfg.name, oldCount, newCount, newText, r.via))
                if (r.via.isNotBlank()) rememberMirror(r.via)
            }
        _pendingUpdates.value = diffs
    }

    /** 用户点了确定：把候选内容写进缓存并重新解析 */
    fun applySourceUpdates() {
        val c = ctx ?: return
        val list = _pendingUpdates.value
        if (list.isEmpty()) return
        list.forEach { SourceCache.write(c, it.id, it.text) }
        _pendingUpdates.value = emptyList()
        reloadAll()
    }

    /** 用户点了取消：丢弃候选内容，不写任何文件 */
    fun dismissSourceUpdates() {
        _pendingUpdates.value = emptyList()
    }

    fun resetSourceCheck() {
        updatesChecked = false
    }

    private fun rememberMirror(via: String) {
        if (_settings.value.sourceMirror == via) return
        updateSettings(_settings.value.copy(sourceMirror = via))
    }

    fun sourceOf(id: String): SourceConfig? = _sources.value.firstOrNull { it.id == id }


    /** 判断一份内容是不是完整的 SMD 源，避免代理返回半截 HTML */
    private fun looksLikeSmd(text: String): Boolean {
        if (text.length < 80) return false
        val head = text.take(400)
        if (head.contains("<html", true) || head.contains("<!doctype", true)) return false
        if (!text.contains("### ") && !text.contains("● ") && !text.contains("• ")) return false
        return SmdParser.parse(text, "_probe", "_probe").source.entries.isNotEmpty()
    }

    /**
     * 首次启动的自动准备：打开「小贝塔订阅源」总开关，并把清单里的默认项（AI 整理命令库）加进来。
     * 返回 null 表示成功，否则是失败原因（下次启动会重试）。会联网，调用方切 IO 线程。
     */
    fun bootstrapOnboarding(): String? {
        if (!netAllowed) return "未同意协议"
        if (!_settings.value.xbtaSeries) setXbtaSeries(true)
        val err = loadSeriesCatalog()
        val item = _seriesItems.value.firstOrNull { it.url.endsWith("/official.md") }
            ?: return err ?: "清单里没有默认源"
        if (seriesConfigOf(item) == null) {
            return toggleSeriesItem(item, true)
        }
        if (!seriesConfigOf(item)!!.enabled) toggleSeriesItem(item, true)
        return null
    }

    fun isAlive(entryId: String): Boolean = _entries.value.any { it.id == entryId }

    // ---------------- 查询 ----------------

    fun entryById(id: String): Entry? {
        val fav = _favs.value.items.firstOrNull { it.entryId == id }
        // 用户改过的（含本地新建）以快照为准，不跟随源
        if (fav != null && fav.edited) return fav.entry
        return _entries.value.firstOrNull { it.id == id } ?: fav?.entry
    }

    /** 本地自建条目（id 前缀 local-），不存在于任何源里 */
    fun isLocal(entryId: String): Boolean = entryId.startsWith(LOCAL_PREFIX)

    private fun newLocalId(): String = LOCAL_PREFIX + UUID.randomUUID().toString().take(8)

    fun entriesInCategory(cat: String): List<Entry> =
        _entries.value.filter { it.categoryId == cat }

    fun countInCategory(cat: String): Int = entriesInCategory(cat).size

    fun search(query: String, types: Set<EntryType>, risks: Set<Risk>): List<Entry> {
        val kw = query.trim().lowercase()
        if (kw.isEmpty() && types.isEmpty() && risks.isEmpty()) return _entries.value

        fun match(e: Entry): Boolean =
            (types.isEmpty() || e.type in types) &&
                (risks.isEmpty() || e.risk in risks) &&
                (kw.isEmpty() ||
                    e.title.lowercase().contains(kw) ||
                    e.body.lowercase().contains(kw) ||
                    e.desc.lowercase().contains(kw) ||
                    e.tags.any { it.lowercase().contains(kw) } ||
                    e.categoryId.lowercase().contains(kw))

        val fromSources = _entries.value.filter(::match)
        // 本地自建条目不属于任何源，只在搜索里并入，免得混进分类浏览
        val local = _favs.value.items.asSequence()
            .filter { isLocal(it.entryId) }
            .map { it.entry }
            .filter(::match)
            .toList()
        return fromSources + local
    }

    // ---------------- 收藏 ----------------

    fun isFav(entryId: String): Boolean = _favs.value.isFav(entryId)

    fun addFav(
        entry: Entry,
        folderId: String? = null,
        tags: List<String> = emptyList(),
        note: String = ""
    ) {
        val c = ctx ?: return
        val list = _favs.value.items.filterNot { it.entryId == entry.id }.toMutableList()
        // 新收藏排在同级最前面：位次取同级最大值 +1（未手动排序过时 order 全是 0）
        val top = list.filter { it.folderId == folderId }.maxOfOrNull { it.order } ?: 0
        list.add(
            FavItem(
                entryId = entry.id,
                folderId = folderId,
                tags = tags,
                note = note,
                savedAt = System.currentTimeMillis(),
                entry = entry,
                sourceId = "builtin",
                sourceName = _sourceName.value,
                order = top + 1
            )
        )
        _favs.value = _favs.value.copy(items = list)
        Repo.saveFavs(c, _favs.value)
    }

    /**
     * 按给定顺序重排某个收藏夹。位次从大到小赋（第一个最大），与 itemsIn 的降序排序一致。
     * 越界/不存在的 id 自动忽略。
     */
    fun reorderFavs(folderId: String?, orderedIds: List<String>) {
        val c = ctx ?: return
        if (orderedIds.isEmpty()) return
        val pos = orderedIds.withIndex().associate { (i, id) -> id to (orderedIds.size - i) }
        val list = _favs.value.items.map { fi ->
            val p = pos[fi.entryId]
            if (p != null && fi.folderId == folderId) fi.copy(order = p) else fi
        }
        _favs.value = _favs.value.copy(items = list)
        Repo.saveFavs(c, _favs.value)
    }

    /**
     * 批量加收藏（粘贴导入用）。返回 (新增, 跳过)。
     * 已收藏过的 entryId 直接跳过，不覆盖原有的备注与标签。
     */
    fun addFavs(entries: List<Entry>, folderId: String? = null): Pair<Int, Int> {
        val c = ctx ?: return 0 to 0
        if (entries.isEmpty()) return 0 to 0
        val list = _favs.value.items.toMutableList()
        var top = list.filter { it.folderId == folderId }.maxOfOrNull { it.order } ?: 0
        var added = 0
        var skipped = 0
        entries.forEach { e ->
            if (list.any { it.entryId == e.id }) {
                skipped++
                return@forEach
            }
            top++
            list.add(
                FavItem(
                    entryId = e.id,
                    folderId = folderId,
                    savedAt = System.currentTimeMillis(),
                    entry = e,
                    sourceId = e.sourceId,
                    sourceName = "导入",
                    order = top
                )
            )
            added++
        }
        if (added > 0) {
            _favs.value = _favs.value.copy(items = list)
            Repo.saveFavs(c, _favs.value)
        }
        return added to skipped
    }

    /** 本地新建一条命令，直接进收藏。返回新条目 id */
    fun addLocalEntry(entry: Entry, folderId: String? = null): String {
        val c = ctx ?: return ""
        val id = if (entry.id.startsWith(LOCAL_PREFIX)) entry.id else newLocalId()
        val item = entry.copy(id = id)
        val list = _favs.value.items.filterNot { it.entryId == id }.toMutableList()
        val top = list.filter { it.folderId == folderId }.maxOfOrNull { it.order } ?: 0
        list.add(
            FavItem(
                entryId = id,
                folderId = folderId,
                savedAt = System.currentTimeMillis(),
                entry = item,
                sourceId = "",
                sourceName = "本地",
                order = top + 1,
                edited = true
            )
        )
        _favs.value = _favs.value.copy(items = list)
        Repo.saveFavs(c, _favs.value)
        return id
    }

    /**
     * 改收藏条目的内容（只改本地快照，不动源）。
     * 保留原条目的文件夹 / 标签 / 备注 / 排序位次。
     */
    fun updateFavEntry(entryId: String, entry: Entry) {
        val c = ctx ?: return
        val list = _favs.value.items.map { fi ->
            if (fi.entryId == entryId) fi.copy(entry = entry.copy(id = entryId), edited = true) else fi
        }
        _favs.value = _favs.value.copy(items = list)
        Repo.saveFavs(c, _favs.value)
    }

    /** 放弃手动改动，回到源的原始内容（本地条目无源，调用会被忽略） */
    fun revertFavEntry(entryId: String) {
        if (isLocal(entryId)) return
        val c = ctx ?: return
        val src = _entries.value.firstOrNull { it.id == entryId } ?: return
        val list = _favs.value.items.map { fi ->
            if (fi.entryId == entryId) fi.copy(entry = src, edited = false) else fi
        }
        _favs.value = _favs.value.copy(items = list)
        Repo.saveFavs(c, _favs.value)
    }

    fun removeFav(entryId: String) {
        val c = ctx ?: return
        _favs.value = _favs.value.copy(items = _favs.value.items.filterNot { it.entryId == entryId })
        Repo.saveFavs(c, _favs.value)
    }

    /** 返回 true = 已收藏 */
    fun toggleFav(entry: Entry): Boolean {
        return if (isFav(entry.id)) {
            removeFav(entry.id); false
        } else {
            addFav(entry); true
        }
    }

    fun updateFav(item: FavItem) {
        val c = ctx ?: return
        _favs.value = _favs.value.copy(
            items = _favs.value.items.map { if (it.entryId == item.entryId) item else it }
        )
        Repo.saveFavs(c, _favs.value)
    }

    // ---------------- 文件夹 ----------------

    fun createFolder(name: String, parentId: String?): String {
        val id = "f" + UUID.randomUUID().toString().replace("-", "").take(8)
        val c = ctx ?: return id
        val order = _favs.value.childrenOf(parentId).size
        _favs.value = _favs.value.copy(
            folders = _favs.value.folders + Folder(id, name, parentId, order)
        )
        Repo.saveFavs(c, _favs.value)
        return id
    }

    fun renameFolder(id: String, name: String) {
        val c = ctx ?: return
        _favs.value = _favs.value.copy(
            folders = _favs.value.folders.map { if (it.id == id) it.copy(name = name) else it }
        )
        Repo.saveFavs(c, _favs.value)
    }

    /** cascade=true 连带子文件夹与收藏；false 子内容上移到父级 */
    fun deleteFolder(id: String, cascade: Boolean) {
        val c = ctx ?: return
        val d = _favs.value
        if (cascade) {
            val doomed = mutableSetOf(id)
            var changed = true
            while (changed) {
                changed = false
                d.folders.forEach { f ->
                    val p = f.parentId
                    if (p != null && p in doomed && f.id !in doomed) { doomed.add(f.id); changed = true }
                }
            }
            _favs.value = d.copy(
                folders = d.folders.filterNot { it.id in doomed },
                items = d.items.filterNot { it.folderId != null && it.folderId in doomed }
            )
        } else {
            val target = d.folders.firstOrNull { it.id == id }
            val parent = target?.parentId
            _favs.value = d.copy(
                folders = d.folders.filterNot { it.id == id }
                    .map { if (it.parentId == id) it.copy(parentId = parent) else it },
                items = d.items.map { if (it.folderId == id) it.copy(folderId = parent) else it }
            )
        }
        Repo.saveFavs(c, _favs.value)
    }

    fun moveFav(entryId: String, folderId: String?) {
        val c = ctx ?: return
        _favs.value = _favs.value.copy(
            items = _favs.value.items.map { if (it.entryId == entryId) it.copy(folderId = folderId) else it }
        )
        Repo.saveFavs(c, _favs.value)
    }

    // ---------------- 标签 ----------------

    fun createTag(name: String, colorIdx: Int = 0): String {
        val id = "t" + UUID.randomUUID().toString().replace("-", "").take(8)
        val c = ctx ?: return id
        _favs.value = _favs.value.copy(tags = _favs.value.tags + Tag(id, name, colorIdx))
        Repo.saveFavs(c, _favs.value)
        return id
    }

    fun renameTag(id: String, name: String) {
        val c = ctx ?: return
        _favs.value = _favs.value.copy(
            tags = _favs.value.tags.map { if (it.id == id) it.copy(name = name) else it }
        )
        Repo.saveFavs(c, _favs.value)
    }

    fun deleteTag(id: String) {
        val c = ctx ?: return
        _favs.value = _favs.value.copy(
            tags = _favs.value.tags.filterNot { it.id == id },
            items = _favs.value.items.map { it.copy(tags = it.tags.filterNot { t -> t == id }) }
        )
        Repo.saveFavs(c, _favs.value)
    }

    fun tagOf(id: String): Tag? = _favs.value.tags.firstOrNull { it.id == id }

    // ---------------- 设置 ----------------

    fun updateSettings(s: AppSettings) {
        val c = ctx ?: return
        _settings.value = s
        Repo.saveSettings(c, s)
    }

    // ---------------- 云备份（WebDAV） ----------------

    data class WebDavState(
        val running: Boolean = false,
        val lastAt: Long = 0L,
        val lastOk: Boolean = false,
        val lastMsg: String = ""
    )

    private val _webdav = MutableStateFlow(WebDavState())
    val webdav: StateFlow<WebDavState> = _webdav.asStateFlow()

    /** 密码不落 JSON，只在内存 + Keystore 加密文件里；导出备份也不含它 */
    @Volatile
    private var webdavPass: String = ""

    fun webdavPassOf(): String = webdavPass

    fun setWebdavPass(plain: String) {
        webdavPass = plain
        ctx?.let { SecretBox.save(it, plain) }
    }

    fun webdavReady(): Boolean = webdavConfig().ready

    private fun webdavConfig() = WebDav.Config(
        baseUrl = _settings.value.webdavUrl,
        user = _settings.value.webdavUser,
        pass = webdavPass,
        path = _settings.value.webdavPath.ifBlank { "shellstore/shellstore-backup.json" }
    )

    suspend fun pingWebdav(): WebDav.Result =
        withContext(Dispatchers.IO) { WebDav.ping(webdavConfig()) }

    /** 上传整份备份，手动与自动共用同一条路径 */
    suspend fun backupNow(): WebDav.Result = withContext(Dispatchers.IO) {
        if (!webdavReady()) return@withContext WebDav.Result.fail(0, "还没配置 WebDAV")
        if (_webdav.value.running) return@withContext WebDav.Result.fail(0, "备份正在进行")
        _webdav.value = _webdav.value.copy(running = true)
        val r = WebDav.upload(webdavConfig(), exportBackupJson())
        _webdav.value = WebDavState(
            running = false,
            lastAt = System.currentTimeMillis(),
            lastOk = r.ok,
            lastMsg = r.msg
        )
        r
    }

    /**
     * 变更触发的自动备份。
     * 开关关着 / 没配置 / 处于离线基础模式时静默跳过，不打扰用户。
     */
    suspend fun autoBackup(force: Boolean = false) {
        val s = _settings.value
        if (!force && (!s.webdavAuto || !webdavReady() || !netAllowed)) return
        if (_webdav.value.running) return
        val r = backupNow()
        if (!r.ok) {
            _webdav.value = WebDavState(
                lastAt = System.currentTimeMillis(),
                lastOk = false,
                lastMsg = r.msg
            )
        }
    }

    /** 从云端拉回来覆盖/合并本地；返回 null 表示成功 */
    suspend fun restoreFromCloud(overwrite: Boolean): String? = withContext(Dispatchers.IO) {
        if (!webdavReady()) return@withContext "还没配置 WebDAV"
        val (text, err) = WebDav.download(webdavConfig())
        if (err != null) return@withContext err
        if (text.isNullOrBlank()) return@withContext "云端还没有备份文件"
        importBackup(text, overwrite)
    }

    // ---------------- 数据维护 ----------------

    fun clearFavorites() {
        val c = ctx ?: return
        _favs.value = FavData()
        Repo.saveFavs(c, _favs.value)
    }

    /**
     * 导出完整备份。
     * v2 起包含订阅源配置与最近使用记录（v1 只有收藏 + 设置），
     * 恢复时按 schema 判断，v1 旧文件仍可导入。
     * 注意：不导出 WebDAV 密码——密码只在本机 Keystore 里，永不进备份文件。
     */
    fun exportBackupJson(): String {
        val o = org.json.JSONObject()
        o.put("schema", "shellstore-backup/v2")
        o.put("app", "ShellStore")
        o.put("exportedAt", System.currentTimeMillis())
        o.put("favorites", Repo.favToJson(_favs.value))
        o.put("settings", _settings.value.toJson())
        o.put("sources", org.json.JSONArray().apply {
            _sources.value.forEach { put(it.toJson()) }
        })
        o.put("recent", org.json.JSONArray().apply {
            _recent.value.forEach {
                put(org.json.JSONObject().apply { put("id", it.entryId); put("at", it.at) })
            }
        })
        return o.toString(2)
    }

    /**
     * 导入备份。overwrite=true 清空后重建；false 按 id 去重合并（本地优先）。
     * 返回 null 表示成功，否则返回错误原因。
     */
    fun importBackup(json: String, overwrite: Boolean): String? {
        val c = ctx ?: return "未初始化"
        val root = runCatching { org.json.JSONObject(json) }.getOrNull()
            ?: return "不是合法的 JSON"
        val favObj = root.optJSONObject("favorites") ?: return "备份里没有收藏数据"
        val incoming = runCatching { Repo.favFromJson(favObj) }.getOrNull()
            ?: return "收藏数据结构无法识别"

        if (overwrite) {
            _favs.value = incoming
        } else {
            val cur = _favs.value
            val folderIds = cur.folders.map { it.id }.toSet()
            val tagIds = cur.tags.map { it.id }.toSet()
            val itemIds = cur.items.map { it.entryId }.toSet()
            _favs.value = FavData(
                folders = cur.folders + incoming.folders.filterNot { it.id in folderIds },
                tags = cur.tags + incoming.tags.filterNot { it.id in tagIds },
                items = cur.items + incoming.items.filterNot { it.entryId in itemIds }
            )
        }
        Repo.saveFavs(c, _favs.value)

        root.optJSONObject("settings")?.let { s ->
            val st = AppSettings.fromJson(s)
            // 只合并可迁移的偏好，字体路径这类本机相关的不覆盖
            _settings.value = _settings.value.copy(
                theme = st.theme,
                fontLevel = st.fontLevel,
                uiLevel = st.uiLevel,
                copyPrefix = st.copyPrefix,
                confirmDanger = st.confirmDanger
            )
            Repo.saveSettings(c, _settings.value)
        }

        // ---- v2：订阅源配置 ----
        // 内置源由本机自带，永远不参与覆盖；同 id 或同 url 视为同一个源
        root.optJSONArray("sources")?.let { arr ->
            val incoming = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .map { SourceConfig.fromJson(it) }
                .filter { it.type != SourceType.BUILTIN && it.url.isNotBlank() }
            if (incoming.isNotEmpty()) {
                val keep = if (overwrite) _sources.value.filter { it.type == SourceType.BUILTIN }
                else _sources.value
                val ids = keep.map { it.id }.toMutableSet()
                val urls = keep.map { it.url }.filter { it.isNotBlank() }.toMutableSet()
                val added = incoming.filter { ids.add(it.id) && (it.url.isBlank() || urls.add(it.url)) }
                if (added.isNotEmpty()) {
                    _sources.value = keep + added
                    Repo.saveSourceConfigs(c, _sources.value)
                }
            }
        }

        // ---- v2：最近使用 ----
        root.optJSONArray("recent")?.let { arr ->
            val incoming = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { o2 ->
                    val id = o2.optString("id")
                    if (id.isBlank()) null else RecentItem(id, o2.optLong("at"))
                }
            }
            if (incoming.isNotEmpty()) {
                val merged = if (overwrite) incoming
                else (_recent.value + incoming)
                    .groupBy { it.entryId }
                    .map { (_, v) -> v.maxByOrNull { it.at } ?: v.first() }
                _recent.value = merged.sortedByDescending { it.at }.take(RECENT_MAX)
                Repo.saveRecent(c, _recent.value)
            }
        }
        return null
    }

    fun removeFavs(ids: Collection<String>) {
        val c = ctx ?: return
        if (ids.isEmpty()) return
        _favs.value = _favs.value.copy(items = _favs.value.items.filterNot { it.entryId in ids })
        Repo.saveFavs(c, _favs.value)
    }

    fun moveFavs(ids: Collection<String>, folderId: String?) {
        val c = ctx ?: return
        if (ids.isEmpty()) return
        _favs.value = _favs.value.copy(
            items = _favs.value.items.map {
                if (it.entryId in ids) it.copy(folderId = folderId) else it
            }
        )
        Repo.saveFavs(c, _favs.value)
    }

    fun setTagsFor(ids: Collection<String>, tags: List<String>) {
        val c = ctx ?: return
        if (ids.isEmpty()) return
        _favs.value = _favs.value.copy(
            items = _favs.value.items.map { if (it.entryId in ids) it.copy(tags = tags) else it }
        )
        Repo.saveFavs(c, _favs.value)
    }

    /** 批量追加标签：保留每个条目原有的标签，只往里加 */
    fun addTagsTo(ids: Collection<String>, tags: List<String>) {
        val c = ctx ?: return
        if (ids.isEmpty() || tags.isEmpty()) return
        _favs.value = _favs.value.copy(
            items = _favs.value.items.map {
                if (it.entryId in ids) it.copy(tags = (it.tags + tags).distinct()) else it
            }
        )
        Repo.saveFavs(c, _favs.value)
    }

    /**
     * 把收藏导出成一份 SMD 源文本，按收藏夹分组。
     * filter 为 null 导出全部；给定谓词时只导出命中的条目（用于指定文件夹）。
     */
    fun exportFavsSmd(title: String = "我的收藏", filter: ((FavItem) -> Boolean)? = null): String {
        val d = _favs.value
        val picked = if (filter == null) d.items else d.items.filter(filter)
        if (picked.isEmpty()) return ""
        val tagName = d.tags.associate { it.id to it.name }
        val groups = LinkedHashMap<String, MutableList<String>>()
        picked.forEach { fi ->
            val cat = d.folders.firstOrNull { it.id == fi.folderId }?.name ?: "未分类"
            val names = fi.tags.mapNotNull { tagName[it] }
            groups.getOrPut(cat) { mutableListOf() }
                .add(SmdWriter.entry(fi.entry, note = fi.note, tagNames = names))
        }
        return SmdWriter.source(
            name = title,
            desc = "由 Shell 商店导出的收藏（${picked.size} 条）",
            groups = groups.map { it.key to it.value }
        )
    }

    /** 给收藏条目设置标签集合 */
    fun setFavTags(entryId: String, tags: List<String>) {
        val c = ctx ?: return
        _favs.value = _favs.value.copy(
            items = _favs.value.items.map { if (it.entryId == entryId) it.copy(tags = tags) else it }
        )
        Repo.saveFavs(c, _favs.value)
    }
}
