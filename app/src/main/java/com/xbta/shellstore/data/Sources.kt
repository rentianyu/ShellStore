package com.xbta.shellstore.data

import com.xbta.shellstore.core.SourceType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 一个订阅源的配置与状态（内容存在 files/sources/<id>.md 缓存里） */
data class SourceConfig(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String = "",
    val enabled: Boolean = true,
    val lastFetch: Long = 0L,
    val etag: String = "",
    val count: Int = 0,
    val error: String = "",
    /** 「小贝塔订阅源」标识（如 "xbta"）。空串 = 用户自己加的源。系列源受总开关门控 */
    val series: String = ""
) {
    val typeLabel: String
        get() = when (type) {
            SourceType.BUILTIN -> "内置"
            SourceType.REMOTE -> "在线"
            SourceType.LOCAL -> "本地"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type.name)
        put("url", url); put("enabled", enabled); put("lastFetch", lastFetch)
        put("etag", etag); put("count", count)
        if (series.isNotBlank()) put("series", series)
    }

    companion object {
        fun fromJson(o: JSONObject) = SourceConfig(
            id = o.optString("id"),
            name = o.optString("name"),
            type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrDefault(SourceType.REMOTE),
            url = o.optString("url"),
            enabled = o.optBoolean("enabled", true),
            lastFetch = o.optLong("lastFetch"),
            etag = o.optString("etag"),
            count = o.optInt("count"),
            series = o.optString("series", "")
        )

        fun listToJson(list: List<SourceConfig>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String): List<SourceConfig> = runCatching {
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { fromJson(it) }
        }.getOrDefault(emptyList())
    }
}

/** 极简 HTTP 客户端：只用 HttpURLConnection，不引 OkHttp */
object Net {

    data class Result(
        val text: String?,
        val etag: String = "",
        val notModified: Boolean = false,
        val error: String? = null,
        /** 实际走通的线路名，空串表示直连 */
        val via: String = ""
    ) {
        val ok: Boolean get() = error == null
    }

    /**
     * 直连 → 上次成功的线路 → 其余 GitHub 代理，每条 10 秒。
     * 具体线路表在 Mirrors 里，这里只做单次请求。
     */
    fun getWithFallback(url: String, prefer: String = "", validate: (String) -> Boolean = { it.isNotBlank() }): Result =
        Mirrors.fetch(url, prefer = prefer, validate = validate)

    fun get(url: String, etag: String = "", timeoutMs: Int = 15000): Result {
        var conn: HttpURLConnection? = null
        return try {
            val u = URL(url)
            conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // 连接给短一点：连不上就赶紧换线路，别耗在握手上
                connectTimeout = minOf(timeoutMs, 6_000)
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ShellStore/0.10 (Android)")
                setRequestProperty("Accept", "text/markdown, text/plain, */*")
                if (etag.isNotBlank()) setRequestProperty("If-None-Match", etag)
            }
            val code = conn.responseCode
            when {
                code == 304 -> Result(null, etag, notModified = true)
                code !in 200..299 -> Result(null, error = "HTTP $code")
                else -> {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    Result(body, conn.getHeaderField("ETag") ?: "")
                }
            }
        } catch (e: Exception) {
            Result(null, error = e.javaClass.simpleName + ": " + (e.message ?: "网络错误"))
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    fun sha256(text: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }
}

/** 源内容缓存的读写 */
object SourceCache {

    private fun dir(ctx: android.content.Context): File =
        File(ctx.filesDir, "sources").apply { if (!exists()) mkdirs() }

    fun fileOf(ctx: android.content.Context, id: String): File = File(dir(ctx), "$id.md")

    fun write(ctx: android.content.Context, id: String, text: String) {
        runCatching { fileOf(ctx, id).writeText(text) }
    }

    fun read(ctx: android.content.Context, id: String): String = runCatching {
        val f = fileOf(ctx, id)
        if (f.exists()) f.readText() else ""
    }.getOrDefault("")

    fun remove(ctx: android.content.Context, id: String) {
        runCatching { fileOf(ctx, id).delete() }
    }
}
