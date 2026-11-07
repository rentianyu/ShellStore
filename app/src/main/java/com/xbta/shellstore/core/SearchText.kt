package com.xbta.shellstore.core

import java.nio.charset.Charset

/**
 * 搜索用的文本处理：分词、拼音首字母、命中打分。
 *
 * 三个能力都放在纯逻辑层（不 import android.*），所以能用 kotlinc 在桌面直接跑测试，
 * 不用等 CI —— 拼音边界表这类东西靠肉眼是验不出来的。
 *
 * 拼音只做**首字母**（「设置」→ sz），不做全拼：全拼需要一张几千字的映射表，
 * 而首字母用 GB2312 一级汉字「按拼音排序」这个特性，只要 23 个边界码位就够了。
 */
object SearchText {

    /** 空格（含全角空格、制表符、换行）分隔的多个关键词，全部转小写并去重 */
    fun tokenize(q: String): List<String> =
        q.trim()
            .split(' ', '\u3000', '\t', '\n')
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .distinct()

    /** 关键词是否构成「有效查询」（有内容） */
    fun hasQuery(q: String): Boolean = tokenize(q).isNotEmpty()

    // ---------------- 拼音首字母 ----------------

    /** GB2312 一级汉字按拼音排序，各声母的起始码位（已实测 23/23 边界全部对齐） */
    private val LETTERS = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z'
    )
    private val STARTS = intArrayOf(
        0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C0, 0xB9FE, 0xBBAA, 0xBFA6, 0xC0AC, 0xC2E8,
        0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA, 0xC8BB, 0xC8F6, 0xCBFD, 0xCDDA, 0xCEF4, 0xD1B9, 0xD4D1
    )

    /** 一级汉字区结束码位（之后是二级汉字，按部首排序，首字母不可推） */
    private const val END = 0xD7F9

    private val gb2312: Charset? = runCatching { Charset.forName("GB2312") }.getOrNull()

    /** 单个汉字的拼音首字母；非汉字、二级汉字（生僻字）、拿不到编码时返回 null */
    private fun letterOf(c: Char): Char? {
        if (c.code !in 0x4E00..0x9FA5) return null
        val cs = gb2312 ?: return null
        val b = runCatching { c.toString().toByteArray(cs) }.getOrNull() ?: return null
        if (b.size != 2) return null
        val code = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
        if (code < STARTS[0] || code > END) return null
        var idx = 0
        for (i in STARTS.indices) {
            if (code >= STARTS[i]) idx = i else break
        }
        return LETTERS[idx].lowercaseChar()
    }

    /** 整串首字母：汉字取拼音声母，其余字符原样转小写 */
    fun initials(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length + 8)
        for (c in s) sb.append(letterOf(c) ?: c.lowercaseChar())
        return sb.toString()
    }

    /** 首字母缓存：每次输入都会遍历全部条目，不缓存等于反复做编码转换 */
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 3000
    }

    @Synchronized
    fun initialsCached(s: String): String = cache.getOrPut(s) { initials(s) }

    // ---------------- 匹配与打分 ----------------

    private fun fieldText(e: Entry): String = buildString(128) {
        append(e.body).append('\n')
        append(e.desc).append('\n')
        append(e.doc).append('\n')
        append(e.example).append('\n')
        append(e.categoryId).append('\n')
        for (t in e.tags) append(t).append('\n')
        // 参数名也搜得到：「imei」「battery」这类词常出现在参数里
        for (p in e.params) append(p.key).append(' ').append(p.label).append(' ').append(p.note).append('\n')
    }.lowercase()

    /**
     * 多关键词 AND 匹配 + 打分，分越高越靠前。
     *
     * 标题命中 3 分 > 拼音首字母命中 2 分 > 其余字段命中 1 分。
     * 任一关键词在所有字段里都找不到 → 返回 null（整条不匹配）。
     * 没有关键词（只按类型/风险筛选）时返回 0。
     */
    fun score(e: Entry, tokens: List<String>): Int? {
        if (tokens.isEmpty()) return 0
        val title = e.title.lowercase()
        var pin: String? = null
        var rest: String? = null
        var total = 0
        for (raw in tokens) {
            // score 可能被直接调用（不经过 tokenize），这里再兜一次小写
            val t = raw.lowercase()
            if (t.isEmpty()) continue
            if (title.contains(t)) {
                total += 3
                continue
            }
            val p = pin ?: initialsCached(e.title + " " + e.tags.joinToString(" ")).also { pin = it }
            if (p.contains(t)) {
                total += 2
                continue
            }
            val f = rest ?: fieldText(e).also { rest = it }
            if (f.contains(t)) {
                total += 1
                continue
            }
            return null
        }
        return total
    }

    // ---------------- 高亮区间 ----------------

    /**
     * 关键词在文本里的命中区间（已排序并合并重叠/相邻）。
     *
     * 抽到纯逻辑层是为了能测：「蓝牙 关闭」两个词挨在一起时，
     * 不合并会叠出两层半透明背景，看起来比周围更黑一块。
     */
    fun hitRanges(text: String, keywords: List<String>): List<IntRange> {
        if (text.isEmpty() || keywords.isEmpty()) return emptyList()
        val lower = text.lowercase()
        val raw = ArrayList<IntRange>(4)
        for (k in keywords) {
            val lk = k.trim().lowercase()
            if (lk.isEmpty()) continue
            var i = 0
            while (i <= text.length - lk.length) {
                val p = lower.indexOf(lk, i)
                if (p < 0) break
                raw.add(p until (p + lk.length).coerceAtMost(text.length))
                i = p + lk.length
            }
        }
        if (raw.isEmpty()) return emptyList()
        raw.sortBy { it.first }
        val merged = ArrayList<IntRange>(raw.size)
        for (r in raw) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
            } else {
                merged.add(r)
            }
        }
        return merged
    }
}
