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

    // ---------------- 初始化 ----------------

    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        val c = ctx!!

        _settings.value = Repo.loadSettings(c)

        val parsed = Repo.loadBuiltin(c)
        _entries.value = parsed.source.entries
        _categories.value = parsed.source.categories
        _sourceName.value = parsed.source.name
        _sourceVersion.value = parsed.source.meta.version
        _warnings.value = parsed.warnings

        val favData = Repo.loadFavs(c)
        // 回填存活状态：源里找不到同 id 的条目 → 用快照，标失效
        val ids = parsed.source.entries.map { it.id }.toSet()
        favData.items.forEach { it.alive = it.entryId in ids }
        _favs.value = favData

        _loaded.value = true
    }

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
}
