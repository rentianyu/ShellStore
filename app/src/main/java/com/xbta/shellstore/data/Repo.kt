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

/**
 * 字号 10 级（仅放大 sp，不影响 dp），第 4 级 = 100%。
 * 取等差数列 0.80 → 1.40，每级约 +6.7%，相邻级肉眼可辨、又不会一步跳太多。
 */
val FONT_SCALES: List<Float> = listOf(0.80f, 0.87f, 0.93f, 1.00f, 1.07f, 1.13f, 1.20f, 1.27f, 1.33f, 1.40f)

/** 界面缩放 5 级（dp 与 sp 一起缩放），第 2 级 = 100% */
val UI_SCALES: List<Float> = listOf(0.85f, 0.92f, 1.00f, 1.10f, 1.20f)

const val FONT_LEVEL_DEFAULT = 3
const val UI_LEVEL_DEFAULT = 2

/** 老版本只存了连续 fontScale，导入/升级时就近吸附到某一档 */
fun nearestFontLevel(scale: Float): Int =
    FONT_SCALES.indices.minByOrNull { kotlin.math.abs(FONT_SCALES[it] - scale) } ?: FONT_LEVEL_DEFAULT

/** 云备份走的通道。自动备份只走选中的这一个，避免双份流量与两份状态难以解释 */
enum class CloudBackend(val label: String) {
    WEBDAV("WebDAV"),
    S3("S3")
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.LIGHT,
    /** 字号档位 0..9，对应 FONT_SCALES */
    val fontLevel: Int = FONT_LEVEL_DEFAULT,
    /** 界面缩放档位 0..4，对应 UI_SCALES */
    val uiLevel: Int = UI_LEVEL_DEFAULT,
    /** 云备份后端；「变更后自动备份」只走这一个 */
    val cloudBackend: CloudBackend = CloudBackend.WEBDAV,
    /** 云备份自动开关（变更后 30 秒防抖上传，只走 cloudBackend 选中的后端）。字段名沿用旧名以兼容已有配置 */
    val webdavAuto: Boolean = false,
    /** WebDAV 服务地址，留空 = 未配置 */
    val webdavUrl: String = "",
    val webdavUser: String = "",
    /** 远端相对路径，默认 shellstore/shellstore-backup.json */
    val webdavPath: String = "shellstore/shellstore-backup.json",
    /** S3 兼容对象存储：Endpoint 可带可不带协议 */
    val s3Endpoint: String = "",
    val s3Region: String = "auto",
    val s3Bucket: String = "",
    val s3AccessKey: String = "",
    /** 对象键，默认 shellstore/shellstore-backup.json */
    val s3Key: String = "shellstore/shellstore-backup.json",
    /** 路径风格（endpoint/bucket/key）；MinIO、部分自建服务要开 */
    val s3PathStyle: Boolean = false,
    val uiFont: FontChoice = FontChoice.SYSTEM,
    val codeFont: FontChoice = FontChoice.SYSTEM,
    val customFontPath: String = "",
    val copyPrefix: CopyPrefix = CopyPrefix.NONE,
    val confirmDanger: Boolean = true,
    val checkUpdateOnStart: Boolean = true,
    val copyAndReturn: Boolean = false,
    /** 列表只显示标题、收起命令预览 */
    val foldPreview: Boolean = false,
    /** 是否完成过首次初始化（自动加载默认源） */
    val onboarded: Boolean = false,
    /** 上次拉取成功的线路名，空串表示直连。下次优先用它 */
    val sourceMirror: String = "",
    /** 「小贝塔订阅源」总开关。关闭时系列源保留配置但不参与解析 */
    val xbtaSeries: Boolean = false,
    /** 已同意的协议版本，0 = 未同意。低于 LegalText.LEGAL_VERSION 时重新征得同意 */
    val agreedVersion: Int = 0,
    /** 导出 APK 的保存目录（SAF tree uri），空串 = 只留在应用缓存里供安装/分享 */
    val exportDirUri: String = "",
    /** 上次已经给用户看过的版本号（versionCode）。用来判断「这次更新后要不要弹更新内容」 */
    val lastSeenVersionCode: Int = 0
) {
    /** 实际生效的字号倍率（只放大 sp） */
    val fontScale: Float get() = FONT_SCALES[fontLevel.coerceIn(0, FONT_SCALES.lastIndex)]
    /** 实际生效的界面倍率（dp 与 sp 一起放大） */
    val uiScale: Float get() = UI_SCALES[uiLevel.coerceIn(0, UI_SCALES.lastIndex)]

    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme.name)
        put("fontLevel", fontLevel)
        put("uiLevel", uiLevel)
        put("cloudBackend", cloudBackend.name)
        put("webdavAuto", webdavAuto)
        put("webdavUrl", webdavUrl)
        put("webdavUser", webdavUser)
        put("webdavPath", webdavPath)
        put("s3Endpoint", s3Endpoint)
        put("s3Region", s3Region)
        put("s3Bucket", s3Bucket)
        put("s3AccessKey", s3AccessKey)
        put("s3Key", s3Key)
        put("s3PathStyle", s3PathStyle)
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
        put("sourceMirror", sourceMirror)
        put("xbtaSeries", xbtaSeries)
        put("agreedVersion", agreedVersion)
        put("exportDirUri", exportDirUri)
        put("lastSeenVersionCode", lastSeenVersionCode)
    }

    companion object {
        fun fromJson(o: JSONObject?): AppSettings {
            if (o == null) return AppSettings()
            fun <T : Enum<T>> e(n: String, d: T, all: Array<T>): T =
                all.firstOrNull { it.name == n } ?: d
            return AppSettings(
                theme = e(o.optString("theme"), ThemeMode.LIGHT, ThemeMode.entries.toTypedArray()),
                // 老数据只有连续 fontScale，没有 fontLevel：就近吸附，不丢用户已调过的大小
                fontLevel = if (o.has("fontLevel")) o.optInt("fontLevel", FONT_LEVEL_DEFAULT)
                else nearestFontLevel(o.optDouble("fontScale", 1.0).toFloat()),
                uiLevel = o.optInt("uiLevel", UI_LEVEL_DEFAULT),
                cloudBackend = e(o.optString("cloudBackend"), CloudBackend.WEBDAV, CloudBackend.entries.toTypedArray()),
                webdavAuto = o.optBoolean("webdavAuto", false),
                webdavUrl = o.optString("webdavUrl", ""),
                webdavUser = o.optString("webdavUser", ""),
                webdavPath = o.optString("webdavPath", "shellstore/shellstore-backup.json"),
                s3Endpoint = o.optString("s3Endpoint", ""),
                s3Region = o.optString("s3Region", "auto"),
                s3Bucket = o.optString("s3Bucket", ""),
                s3AccessKey = o.optString("s3AccessKey", ""),
                s3Key = o.optString("s3Key", "shellstore/shellstore-backup.json"),
                s3PathStyle = o.optBoolean("s3PathStyle", false),
                uiFont = e(o.optString("uiFont"), FontChoice.SYSTEM, FontChoice.entries.toTypedArray()),
                codeFont = e(o.optString("codeFont"), FontChoice.SYSTEM, FontChoice.entries.toTypedArray()),
                customFontPath = o.optString("customFontPath"),
                copyPrefix = e(o.optString("copyPrefix"), CopyPrefix.NONE, CopyPrefix.entries.toTypedArray()),
                confirmDanger = o.optBoolean("confirmDanger", true),
                checkUpdateOnStart = o.optBoolean("checkUpdateOnStart", true),
                copyAndReturn = o.optBoolean("copyAndReturn", false),
                foldPreview = o.optBoolean("foldPreview", false),
                onboarded = o.optBoolean("onboarded", false),
                sourceMirror = o.optString("sourceMirror", ""),
                xbtaSeries = o.optBoolean("xbtaSeries", false),
                agreedVersion = o.optInt("agreedVersion", 0),
                exportDirUri = o.optString("exportDirUri", ""),
                lastSeenVersionCode = o.optInt("lastSeenVersionCode", 0)
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
    val sourceName: String = "",
    /** 手动排序位次，越大越靠前；0 表示未手动排过（此时按 savedAt 排） */
    val order: Int = 0,
    /**
     * 用户手动改过内容。为 true 时详情页用快照而不是源里的原始条目，
     * 且刷新订阅源不会覆盖用户改动。本地新建的条目恒为 true。
     */
    val edited: Boolean = false
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

    /** 该文件夹及其所有子孙文件夹的 id；不存在时返回空集合 */
    fun subtreeIds(folderId: String): Set<String> {
        val out = mutableSetOf<String>()
        fun walk(id: String) {
            if (!out.add(id)) return
            childrenOf(id).forEach { walk(it.id) }
        }
        walk(folderId)
        return out
    }

    fun itemsIn(folderId: String?): List<FavItem> =
        items.filter { it.folderId == folderId }
            .sortedWith(
                compareByDescending<FavItem> { it.order }.thenByDescending { it.savedAt }
            )

    fun isFav(entryId: String): Boolean = items.any { it.entryId == entryId }
}

// ---------------- 最近使用 ----------------

/** 只存 id 与时间：条目本身随源刷新会变，展示时用当前的最新内容 */
data class RecentItem(val entryId: String, val at: Long)

fun recentFromJson(text: String): List<RecentItem> = try {
    val a = JSONArray(text)
    (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id")
        if (id.isBlank()) null else RecentItem(id, o.optLong("at"))
    }
} catch (e: Exception) {
    emptyList()
}

fun recentToJson(list: List<RecentItem>): String {
    val a = JSONArray()
    list.forEach { r ->
        a.put(JSONObject().apply {
            put("id", r.entryId)
            put("at", r.at)
        })
    }
    return a.toString()
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

    // -- 订阅源配置 --
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

    // -- 最近使用 --
    fun loadRecent(ctx: Context): List<RecentItem> = try {
        val f = file(ctx, "recent.json")
        if (f.exists()) recentFromJson(f.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun saveRecent(ctx: Context, list: List<RecentItem>) {
        runCatching { file(ctx, "recent.json").writeText(recentToJson(list)) }
    }

    // -- 搜索历史 --
    fun loadSearchHistory(ctx: Context): List<String> = try {
        val f = file(ctx, "search.json")
        if (!f.exists()) emptyList() else {
            val a = JSONArray(f.readText())
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveSearchHistory(ctx: Context, list: List<String>) {
        runCatching {
            val a = JSONArray()
            list.forEach { a.put(it) }
            file(ctx, "search.json").writeText(a.toString())
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
                    put("order", it.order)
                    put("edited", it.edited)
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
                        sourceName = it2.optString("sourceName"),
                        order = it2.optInt("order"),
                        edited = it2.optBoolean("edited", false)
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
