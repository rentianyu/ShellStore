package com.xbta.shellstore.data

import android.content.Context
import com.xbta.shellstore.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ---------------- 设置 ----------------

enum class ThemeMode(val label: String) { LIGHT("浅色"), DARK("深色"), SYSTEM("跟随系统") }

enum class FontChoice(val label: String) {
    SYSTEM("系统默认"),
    CUSTOM("自定义字体")
}

enum class CopyPrefix(val label: String, val fn: (String) -> String) {
    NONE("无", { it }),
    ADB_SHELL("adb shell", { "adb shell ${if (it.contains('\n')) "\"$it\"" else it}" }),
    SU("su -c", { "su -c '${it.replace("'", "'\\''")}'" })
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.LIGHT,
    val fontScale: Float = 1.0f,
    val uiFont: FontChoice = FontChoice.SYSTEM,
    val codeFont: FontChoice = FontChoice.SYSTEM,
    val customFontPath: String = "",
    val copyPrefix: CopyPrefix = CopyPrefix.NONE,
    val confirmDanger: Boolean = true,
    val checkUpdateOnStart: Boolean = true,
    val copyAndReturn: Boolean = false,
    /** 列表只显示标题、收起命令预览 */
    val foldPreview: Boolean = false,
    /** 是否已经走过首次欢迎页（引导启用官方源） */
    val onboarded: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme.name)
        put("fontScale", fontScale.toDouble())
        put("uiFont", uiFont.name)
        put("codeFont", codeFont.name)
        put("customFontPath", customFontPath)
        put("copyPrefix", copyPrefix.name)
        put("confirmDanger", confirmDanger)
        put("checkUpdateOnStart", checkUpdateOnStart)
        put("copyAndReturn", copyAndReturn)
        put("foldPreview", foldPreview)
        put("onboarded", onboarded)
    }

    companion object {
        fun fromJson(o: JSONObject?): AppSettings {
            if (o == null) return AppSettings()
            fun <T : Enum<T>> e(n: String, d: T, all: Array<T>): T =
                all.firstOrNull { it.name == n } ?: d
            return AppSettings(
                theme = e(o.optString("theme"), ThemeMode.LIGHT, ThemeMode.entries.toTypedArray()),
                fontScale = o.optDouble("fontScale", 1.0).toFloat().coerceIn(0.8f, 1.5f),
                uiFont = e(o.optString("uiFont"), FontChoice.SYSTEM, FontChoice.entries.toTypedArray()),
                codeFont = e(o.optString("codeFont"), FontChoice.SYSTEM, FontChoice.entries.toTypedArray()),
                customFontPath = o.optString("customFontPath"),
                copyPrefix = e(o.optString("copyPrefix"), CopyPrefix.NONE, CopyPrefix.entries.toTypedArray()),
                confirmDanger = o.optBoolean("confirmDanger", true),
                checkUpdateOnStart = o.optBoolean("checkUpdateOnStart", true),
                copyAndReturn = o.optBoolean("copyAndReturn", false),
                foldPreview = o.optBoolean("foldPreview", false),
                onboarded = o.optBoolean("onboarded", false)
            )
        }
    }
}

// ---------------- 收藏 ----------------

data class Folder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val order: Int = 0
)

data class Tag(val id: String, val name: String, val colorIdx: Int = 0)

data class FavItem(
    val entryId: String,
    val folderId: String? = null,
    val tags: List<String> = emptyList(),
    val note: String = "",
    val savedAt: Long = 0L,
    /** 快照：源消失后仍能看 */
    val entry: Entry = Entry(id = "", type = EntryType.SHELL, title = ""),
    val sourceId: String = "",
    val sourceName: String = ""
) {
    /** 当前源里还能不能找到（由 Store 在加载后回填） */
    var alive: Boolean = true
}

data class FavData(
    val folders: List<Folder> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val items: List<FavItem> = emptyList()
) {
    val rootCount: Int get() = items.count { it.folderId == null }
    fun childrenOf(parentId: String?): List<Folder> =
        folders.filter { it.parentId == parentId }.sortedBy { it.order }

    fun depthOf(folderId: String): Int {
        var d = 0
        var cur = folders.firstOrNull { it.id == folderId }
        while (cur?.parentId != null && d < 20) {
            cur = folders.firstOrNull { it.id == cur!!.parentId }
            d++
        }
        return d
    }

    fun pathOf(folderId: String?): List<Folder> {
        if (folderId == null) return emptyList()
        val out = mutableListOf<Folder>()
        var cur = folders.firstOrNull { it.id == folderId }
        var guard = 0
        while (cur != null && guard++ < 20) {
            out.add(0, cur)
            cur = cur.parentId?.let { pid -> folders.firstOrNull { it.id == pid } }
        }
        return out
    }

    fun isDescendant(candidateId: String, ofFolderId: String): Boolean {
        var cur = folders.firstOrNull { it.id == candidateId }
        var guard = 0
        while (cur?.parentId != null && guard++ < 20) {
            if (cur!!.parentId == ofFolderId) return true
            cur = folders.firstOrNull { it.id == cur!!.parentId }
        }
        return false
    }

    /** 累计条数（含所有子孙） */
    fun countOf(folderId: String): Int {
        val ids = mutableSetOf(folderId)
        var changed = true
        while (changed) {
            changed = false
            folders.forEach { f ->
                val p = f.parentId
                if (p != null && p in ids && f.id !in ids) {
                    ids.add(f.id); changed = true
                }
            }
        }
        return items.count { it.folderId != null && it.folderId in ids }
    }

    fun itemsIn(folderId: String?): List<FavItem> =
        items.filter { it.folderId == folderId }.sortedByDescending { it.savedAt }

    fun isFav(entryId: String): Boolean = items.any { it.entryId == entryId }
}

// ---------------- 读写 ----------------

object Repo {

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "data").apply { if (!exists()) mkdirs() }

    private fun file(ctx: Context, name: String) = File(dir(ctx), name)

    // -- 设置 --
    fun loadSettings(ctx: Context): AppSettings = try {
        val f = file(ctx, "settings.json")
        if (f.exists()) AppSettings.fromJson(JSONObject(f.readText())) else AppSettings()
    } catch (e: Exception) {
        AppSettings()
    }

    fun saveSettings(ctx: Context, s: AppSettings) {
        runCatching { file(ctx, "settings.json").writeText(s.toJson().toString()) }
    }

    // -- 数据源配置 --
    fun loadSourceConfigs(ctx: Context): List<SourceConfig> = try {
        val f = file(ctx, "sources.json")
        if (!f.exists()) emptyList() else SourceConfig.listFromJson(f.readText())
    } catch (e: Exception) {
        emptyList()
    }

    fun saveSourceConfigs(ctx: Context, list: List<SourceConfig>) {
        runCatching { file(ctx, "sources.json").writeText(SourceConfig.listToJson(list)) }
    }

    // -- 收藏 --
    fun loadFavs(ctx: Context): FavData = try {
        val f = file(ctx, "favorites.json")
        if (!f.exists()) FavData() else favFromJson(JSONObject(f.readText()))
    } catch (e: Exception) {
        FavData()
    }

    fun saveFavs(ctx: Context, d: FavData) {
        runCatching {
            val tmp = file(ctx, "favorites.json.tmp")
            tmp.writeText(favToJson(d).toString())
            val dst = file(ctx, "favorites.json")
            if (dst.exists()) dst.delete()
            tmp.renameTo(dst)
        }
    }

    // ---------------- JSON 转换 ----------------

    fun favToJson(d: FavData): JSONObject = JSONObject().apply {
        put("schema", "shellstore-fav/v1")
        put("folders", JSONArray().apply {
            d.folders.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("name", it.name)
                    put("parentId", it.parentId ?: JSONObject.NULL)
                    put("order", it.order)
                })
            }
        })
        put("tags", JSONArray().apply {
            d.tags.forEach {
                put(JSONObject().apply {
                    put("id", it.id); put("name", it.name); put("colorIdx", it.colorIdx)
                })
            }
        })
        put("items", JSONArray().apply {
            d.items.forEach {
                put(JSONObject().apply {
                    put("entryId", it.entryId)
                    put("folderId", it.folderId ?: JSONObject.NULL)
                    put("tags", JSONArray(it.tags))
                    put("note", it.note)
                    put("savedAt", it.savedAt)
                    put("sourceId", it.sourceId)
                    put("sourceName", it.sourceName)
                    put("entry", entryToJson(it.entry))
                })
            }
        })
    }

    fun favFromJson(o: JSONObject): FavData {
        val folders = mutableListOf<Folder>()
        o.optJSONArray("folders")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.optJSONObject(i) ?: continue
                folders.add(
                    Folder(
                        id = f.optString("id"),
                        name = f.optString("name"),
                        parentId = if (f.isNull("parentId")) null else f.optString("parentId"),
                        order = f.optInt("order")
                    )
                )
            }
        }
        val tags = mutableListOf<Tag>()
        o.optJSONArray("tags")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.optJSONObject(i) ?: continue
                tags.add(Tag(t.optString("id"), t.optString("name"), t.optInt("colorIdx")))
            }
        }
        val items = mutableListOf<FavItem>()
        o.optJSONArray("items")?.let { a ->
            for (i in 0 until a.length()) {
                val it2 = a.optJSONObject(i) ?: continue
                items.add(
                    FavItem(
                        entryId = it2.optString("entryId"),
                        folderId = if (it2.isNull("folderId")) null else it2.optString("folderId"),
                        tags = it2.optJSONArray("tags")?.let { arr ->
                            (0 until arr.length()).map { k -> arr.optString(k) }
                        } ?: emptyList(),
                        note = it2.optString("note"),
                        savedAt = it2.optLong("savedAt"),
                        entry = it2.optJSONObject("entry")?.let { e -> entryFromJson(e) }
                            ?: Entry(id = it2.optString("entryId"), type = EntryType.SHELL, title = it2.optString("entryId")),
                        sourceId = it2.optString("sourceId"),
                        sourceName = it2.optString("sourceName")
                    )
                )
            }
        }
        return FavData(folders, tags, items)
    }

    fun entryToJson(e: Entry): JSONObject = JSONObject().apply {
        put("id", e.id); put("type", e.type.id); put("title", e.title)
        put("desc", e.desc); put("categoryId", e.categoryId)
        put("tags", JSONArray(e.tags)); put("risk", e.risk.id); put("need", e.need.id)
        put("body", e.body)
        put("variants", JSONArray().apply {
            e.variants.forEach {
                put(JSONObject().apply { put("name", it.name); put("body", it.body) })
            }
        })
        put("params", JSONArray().apply {
            e.params.forEach {
                put(JSONObject().apply {
                    put("key", it.key); put("label", it.label)
                    put("default", it.default); put("note", it.note)
                })
            }
        })
        put("example", e.example); put("doc", e.doc); put("adb", e.adb)
        put("verifiedAt", e.verifiedAt); put("pinned", e.pinned); put("origin", e.origin)
        put("sourceId", e.sourceId)
        put("extra", JSONObject(e.extra as Map<*, *>))
        e.intent?.let { s ->
            put("intent", JSONObject().apply {
                put("action", s.action); put("data", s.data); put("mime", s.mime)
                put("component", s.component); put("packageName", s.packageName)
                put("categories", JSONArray(s.categories)); put("flags", JSONArray(s.flags))
                put("extras", JSONArray().apply {
                    s.extras.forEach {
                        put(JSONObject().apply {
                            put("key", it.key); put("type", it.type.name); put("value", it.value)
                        })
                    }
                })
            })
        }
    }

    fun entryFromJson(o: JSONObject): Entry {
        val intent = o.optJSONObject("intent")?.let { s ->
            IntentSpec(
                action = s.optString("action"),
                data = s.optString("data"),
                mime = s.optString("mime"),
                component = s.optString("component"),
                packageName = s.optString("packageName"),
                categories = s.optJSONArray("categories")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                } ?: emptyList(),
                flags = s.optJSONArray("flags")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }
                } ?: emptyList(),
                extras = s.optJSONArray("extras")?.let { a ->
                    (0 until a.length()).mapNotNull { i ->
                        a.optJSONObject(i)?.let {
                            Extra(it.optString("key"), ExtraType.valueOf(it.optString("type", "STRING")), it.optString("value"))
                        }
                    }
                } ?: emptyList()
            )
        }
        return Entry(
            id = o.optString("id"),
            type = EntryType.of(o.optString("type")) ?: EntryType.SHELL,
            title = o.optString("title"),
            desc = o.optString("desc"),
            categoryId = o.optString("categoryId"),
            tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            risk = Risk.of(o.optString("risk")) ?: Risk.SAFE,
            need = Need.of(o.optString("need")) ?: Need.NONE,
            body = o.optString("body"),
            variants = o.optJSONArray("variants")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let { Variant(it.optString("name"), it.optString("body")) }
                }
            } ?: emptyList(),
            params = o.optJSONArray("params")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONObject(i)?.let {
                        Param(it.optString("key"), it.optString("label"), it.optString("default"), it.optString("note"))
                    }
                }
            } ?: emptyList(),
            example = o.optString("example"),
            doc = o.optString("doc"),
            adb = o.optString("adb"),
            intent = intent,
            extra = o.optJSONObject("extra")?.let { x ->
                val m = mutableMapOf<String, String>()
                x.keys().forEach { k -> m[k] = x.optString(k) }
                m
            } ?: emptyMap(),
            verifiedAt = o.optString("verifiedAt"),
            pinned = o.optBoolean("pinned"),
            origin = o.optString("origin"),
            sourceId = o.optString("sourceId")
        )
    }
}
