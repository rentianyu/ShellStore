package com.xbta.shellstore.core

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 把 url scheme 拆成「协议 + 主机/路径 + 参数 + 锚点」，并把编解码做对。
 *
 * 规则对齐自用工具 tool.xbta.cc/url-editor：
 *   · 参数按 `&` 拆、按**第一个** `=` 分键值（值里再有 `=` 不截断）
 *   · 重复的键全部保留（很多 scheme 就是靠重复键传数组）
 *   · 解码时 `+` 视为空格，遇到非法 `%xx` 时**原样返回**，不抛异常
 *
 * 纯逻辑，不依赖 android，可用 kotlinc 在桌面跑单测。
 */
object SchemeParse {

    /** 一个查询参数；[value] 是**未解码**的原文，展示时再按需解 */
    data class Param(val key: String, val value: String)

    data class Parts(
        /** 冒号前的协议名，如 weixin、alipays、amstart */
        val scheme: String = "",
        /** 协议之后、参数之前的部分，如 //wx.tenpay.com/h5pay（原样，只用于展示） */
        val body: String = "",
        val params: List<Param> = emptyList(),
        /** `#` 之后的内容（未解码） */
        val hash: String = "",
        val ok: Boolean = false,
        val error: String? = null
    )

    private val SCHEME_RE = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

    /**
     * 容错解析：不校验 URL 合法性，只要能找到一个 `?` 之后的参数就拆出来。
     * scheme 条目里常见带中文、带空格、带 `{}` 的写法，用 java.net.URI 会直接抛异常。
     */
    fun parse(raw: String): Parts {
        val input = raw.trim()
        if (input.isEmpty()) return Parts(error = "内容为空")

        // 先按 # 切成「主段」与「锚点」（锚点里可能还有 ? ，不该算参数）
        val hashIdx = input.indexOf('#')
        val head = if (hashIdx >= 0) input.substring(0, hashIdx) else input
        val hash = if (hashIdx >= 0) input.substring(hashIdx + 1) else ""

        val qIdx = head.indexOf('?')
        val beforeQuery = if (qIdx >= 0) head.substring(0, qIdx) else head
        val query = if (qIdx >= 0) head.substring(qIdx + 1) else ""

        val scheme = SCHEME_RE.find(beforeQuery)?.groupValues?.get(1).orEmpty()
        val body = if (scheme.isNotEmpty()) beforeQuery.substring(scheme.length + 1) else beforeQuery

        return Parts(
            scheme = scheme,
            body = body,
            params = splitQuery(query),
            hash = hash,
            ok = true
        )
    }

    /** `k1=v1&k2=v2&flag` → 参数列表；空项、只有键的项都保留 */
    fun splitQuery(query: String): List<Param> {
        if (query.isBlank()) return emptyList()
        return query.split('&')
            .filter { it.isNotBlank() }
            .map { seg ->
                val eq = seg.indexOf('=')
                if (eq < 0) Param(decode(seg), "")
                else Param(decode(seg.substring(0, eq)), seg.substring(eq + 1))
            }
    }

    /** 查询参数解码：`+` 当空格，非法序列原样返回 */
    fun decode(v: String): String {
        if (v.isEmpty()) return v
        return runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
    }

    /** 通用解码：`+` 原样保留（路径、锚点、base64 里 `+` 有意义） */
    fun decodeStrict(v: String): String {
        if (v.isEmpty()) return v
        return runCatching { URLDecoder.decode(v.replace("+", "%2B"), "UTF-8") }.getOrDefault(v)
    }

    /** 编码：与解码配对，空格编成 %20 而不是 +，避免二次解码时歧义 */
    fun encode(v: String): String =
        runCatching { URLEncoder.encode(v, "UTF-8").replace("+", "%20") }.getOrDefault(v)

    /** 是否含有可解码的 %xx */
    fun hasPercent(v: String): Boolean = PERCENT_RE.containsMatchIn(v)

    private val PERCENT_RE = Regex("%[0-9a-fA-F]{2}")

    /**
     * 连续解码，最多 3 层（有的接口会二次编码，`%253A` → `%3A` → `:`）。
     * 解不动（非法序列）就停下，返回当前结果。
     */
    fun prettyDecode(v: String, rounds: Int = 3): String {
        var text = v
        repeat(rounds) {
            if (!hasPercent(text)) return text
            val next = decode(text)
            if (next == text) return text
            text = next
        }
        return text
    }

    /** 值是不是「含中文/空格等不便于阅读的编码」，用来决定要不要默认显示解码按钮 */
    fun looksEncoded(v: String): Boolean = hasPercent(v) || v.contains('+')

    // ---------------- 回写 ----------------

    /**
     * 只替换 query 段，其余原样保留（scheme、路径、锚点都不动）。
     *
     * [params] 里的值应当是**最终要写进串里的原文**——要不要编码由调用方决定：
     * 用户没动过的参数保持原样（已经是 `%E4%B8%AD` 的不会被二次编码），
     * 新输入/改过的值再按需要过 [encodeIfNeeded]。
     */
    fun rebuild(raw: String, params: List<Param>): String {
        val hashIdx = raw.indexOf('#')
        val head = if (hashIdx >= 0) raw.substring(0, hashIdx) else raw
        val tail = if (hashIdx >= 0) raw.substring(hashIdx) else ""
        val qIdx = head.indexOf('?')
        val base = if (qIdx >= 0) head.substring(0, qIdx) else head

        val query = params
            .filter { it.key.isNotBlank() }
            .joinToString("&") { p -> if (p.value.isEmpty()) p.key else "${p.key}=${p.value}" }

        return base + (if (query.isNotEmpty()) "?$query" else "") + tail
    }

    /**
     * 按需编码：只有含裸的 `&` `#` `?` 空格或非 ASCII 才编码。
     *
     * 刻意**不**因为出现 `%` 就编码 —— 用户很可能自己写好了 `%E4%B8%AD`，
     * 再编一次会变成 `%25E4%25B8%25AD`，等于把值弄坏。
     */
    fun encodeIfNeeded(v: String): String {
        if (v.isEmpty()) return v
        val need = v.any { c ->
            c == '&' || c == '#' || c == '?' || c == ' ' || c == '\u3000' || c.code > 127
        }
        return if (need) encode(v) else v
    }

    /**
     * 连续解码但**保留 `+`**：编辑框里显示用。
     *
     * 展示场景把 `+` 当空格更「像阅读」，但编辑场景不行——
     * base64 里 `+` 有含义，一改就悄悄改坏了用户的值。
     */
    fun prettyDecodeStrict(v: String, rounds: Int = 3): String {
        var text = v
        repeat(rounds) {
            if (!hasPercent(text)) return text
            val next = decodeStrict(text)
            if (next == text) return text
            text = next
        }
        return text
    }

    /** 参数值的可读形式（编辑框里显示用，`+` 不转空格） */
    fun readable(v: String): String = prettyDecodeStrict(v)
}
