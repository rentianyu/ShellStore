package com.xbta.shellstore.data

import android.content.Context
import com.xbta.shellstore.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 全局状态。单例 object + StateFlow，不引 ViewModel。
 * 所有磁盘 IO 走 Repo，调用方负责切线程（首版数据量小，主线程读 30 条 < 5ms）。
 */
object Store {

    const val OFFICIAL_ID = "official"

    /** 官方命令库。直连不通时自动走 p.xbta.cc 反代，国内也能拉。 */
    const val OFFICIAL_URL = "https://raw.githubusercontent.com/rentianyu/ShellStore/main/sources/official.md"

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

    private val _favs = MutableStateFlow(FavData())
    val favs: StateFlow<FavData> = _favs.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _warnings = MutableStateFlow<List<ParseWarning>>(emptyList())
    val warnings: StateFlow<List<ParseWarning>> = _warnings.asStateFlow()

    private val _sources = MutableStateFlow<List<SourceConfig>>(emptyList())
    val sources: StateFlow<List<SourceConfig>> = _sources.asStateFlow()

    // ---------------- 初始化 ----------------

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        val c = ctx!!

        _settings.value = Repo.loadSettings(c)

        // 不再有内置源：内容全部来自在线 / 本地源，首次启动由欢迎页引导启用官方源
        _sources.value = Repo.loadSourceConfigs(c)

        reloadAll()

        val favData = Repo.loadFavs(c)
        _favs.value = favData

        _loaded.value = true
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
            if (!cfg.enabled) {
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

    fun addRemote(name: String, url: String): String? {
        val c = ctx ?: return "未初始化"
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
            count = parsed.source.entries.size
        )
        _sources.value = _sources.value + cfg
        Repo.saveSourceConfigs(c, _sources.value)
        reloadAll()
        return null
    }

    fun refresh(id: String): String? {
        val c = ctx ?: return "未初始化"
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

    fun hasOfficial(): Boolean = _sources.value.any { it.id == OFFICIAL_ID }

    /**
     * 启用（或刷新）官方命令库。返回 null 表示成功，否则是给用户看的失败原因。
     * 纯阻塞实现，调用方自己切到 IO 线程。
     */
    fun enableOfficial(): String? {
        val c = ctx ?: return "未初始化"
        val existing = _sources.value.firstOrNull { it.id == OFFICIAL_ID }
        val r = Mirrors.fetch(
            OFFICIAL_URL,
            prefer = _settings.value.sourceMirror,
            validate = { looksLikeSmd(it) }
        )
        if (!r.ok) return r.error ?: "所有线路都拉取失败，请检查网络"
        rememberMirror(r.via)
        val text = r.text.orEmpty()
        if (text.isBlank()) return "服务器返回了空内容"
        val parsed = SmdParser.parse(text, OFFICIAL_ID, "官方命令库")
        if (parsed.source.entries.isEmpty()) return "解析后没有条目，源格式可能不对"
        SourceCache.write(c, OFFICIAL_ID, text)

        val cfg = (existing ?: SourceConfig(id = OFFICIAL_ID, name = "官方命令库", type = SourceType.REMOTE, url = OFFICIAL_URL))
            .copy(
                enabled = true,
                url = OFFICIAL_URL,
                lastFetch = System.currentTimeMillis(),
                count = parsed.source.entries.size,
                etag = r.etag
            )
        _sources.value = if (existing == null) _sources.value + cfg
        else _sources.value.map { if (it.id == OFFICIAL_ID) cfg else it }
        Repo.saveSourceConfigs(c, _sources.value)
        reloadAll()
        return null
    }

    fun isAlive(entryId: String): Boolean = _entries.value.any { it.id == entryId }

    // ---------------- 查询 ----------------

    fun entryById(id: String): Entry? =
        _entries.value.firstOrNull { it.id == id }
            ?: _favs.value.items.firstOrNull { it.entryId == id }?.entry

    fun entriesInCategory(cat: String): List<Entry> =
        _entries.value.filter { it.categoryId == cat }

    fun countInCategory(cat: String): Int = entriesInCategory(cat).size

    fun search(query: String, types: Set<EntryType>, risks: Set<Risk>): List<Entry> {
        val kw = query.trim().lowercase()
        if (kw.isEmpty() && types.isEmpty() && risks.isEmpty()) return _entries.value
        return _entries.value.filter { e ->
            (types.isEmpty() || e.type in types) &&
                (risks.isEmpty() || e.risk in risks) &&
                (kw.isEmpty() ||
                    e.title.lowercase().contains(kw) ||
                    e.body.lowercase().contains(kw) ||
                    e.desc.lowercase().contains(kw) ||
                    e.tags.any { it.lowercase().contains(kw) } ||
                    e.categoryId.lowercase().contains(kw))
        }
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
        list.add(
            FavItem(
                entryId = entry.id,
                folderId = folderId,
                tags = tags,
                note = note,
                savedAt = System.currentTimeMillis(),
                entry = entry,
                sourceId = "builtin",
                sourceName = _sourceName.value
            )
        )
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

    // ---------------- 数据维护 ----------------

    fun clearFavorites() {
        val c = ctx ?: return
        _favs.value = FavData()
        Repo.saveFavs(c, _favs.value)
    }

    fun exportBackupJson(): String {
        val o = org.json.JSONObject()
        o.put("schema", "shellstore-backup/v1")
        o.put("app", "0.1.0")
        o.put("exportedAt", System.currentTimeMillis())
        o.put("favorites", Repo.favToJson(_favs.value))
        o.put("settings", _settings.value.toJson())
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
                fontScale = st.fontScale,
                copyPrefix = st.copyPrefix,
                confirmDanger = st.confirmDanger
            )
            Repo.saveSettings(c, _settings.value)
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

    /** 给收藏条目设置标签集合 */
    fun setFavTags(entryId: String, tags: List<String>) {
        val c = ctx ?: return
        _favs.value = _favs.value.copy(
            items = _favs.value.items.map { if (it.entryId == entryId) it.copy(tags = tags) else it }
        )
        Repo.saveFavs(c, _favs.value)
    }
}
