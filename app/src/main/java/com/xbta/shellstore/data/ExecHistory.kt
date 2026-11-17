package com.xbta.shellstore.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 一次执行的留痕。
 *
 * 只存「跑过什么、结果怎样」，不含敏感处理：命令与输出原样，但长度有上限，
 * 免得 `dumpsys` 之类一次写进去几 MB。
 */
data class ExecRecord(
    val at: Long,
    val entryId: String = "",
    val title: String = "",
    val cmd: String = "",
    /** 实际用的执行方式名（系统 / Shizuku / Root / 自定义） */
    val profile: String = "",
    val code: Int = 0,
    val ok: Boolean = false,
    val ms: Long = 0,
    val out: String = "",
    val err: String = "",
    val note: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("at", at)
        put("entryId", entryId)
        put("title", title)
        put("cmd", cmd)
        put("profile", profile)
        put("code", code)
        put("ok", ok)
        put("ms", ms)
        put("out", out)
        put("err", err)
        put("note", note)
    }

    companion object {
        /** 最多留这么多条，再多就丢最旧的 */
        const val MAX = 200
        /** 单条输出上限（字符） */
        const val OUTPUT_LIMIT = 4000

        fun fromJson(o: JSONObject?): ExecRecord? {
            if (o == null) return null
            val at = o.optLong("at", 0L)
            if (at <= 0L) return null
            return ExecRecord(
                at = at,
                entryId = o.optString("entryId"),
                title = o.optString("title"),
                cmd = o.optString("cmd"),
                profile = o.optString("profile"),
                code = o.optInt("code", 0),
                ok = o.optBoolean("ok", false),
                ms = o.optLong("ms", 0L),
                out = o.optString("out"),
                err = o.optString("err"),
                note = o.optString("note")
            )
        }

        fun listFromJson(o: JSONObject?): List<ExecRecord> {
            val arr = o?.optJSONArray("items") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
        }
    }
}

object ExecHistoryRepo {
    private fun file(ctx: Context, name: String): File = File(ctx.filesDir, name)

    fun load(ctx: Context): List<ExecRecord> = try {
        val f = file(ctx, "exec-history.json")
        if (!f.exists()) emptyList()
        else ExecRecord.listFromJson(JSONObject(f.readText()))
    } catch (e: Exception) {
        emptyList()
    }

    fun save(ctx: Context, list: List<ExecRecord>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            val tmp = file(ctx, "exec-history.json.tmp")
            tmp.writeText(JSONObject().put("items", arr).toString())
            val dst = file(ctx, "exec-history.json")
            if (dst.exists()) dst.delete()
            tmp.renameTo(dst)
        }
    }
}
