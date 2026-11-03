package com.xbta.shellstore.core

/**
 * 更新日志解析。
 *
 * 数据源是仓库根的 CHANGELOG.md（构建时复制到 assets/changelog.md，不再另存一份手写版本，
 * 避免「代码里写了一份、文档又写了一份，两边对不上」）。
 *
 * 只解析本仓库实际用到的 Markdown 子集：
 *   ## [1.3.0] - 2026-11-03 【正式版】   → 一个版本
 *   ### 新增                            → 一个分组
 *   - 条目 / 两个空格以上缩进的 - 子条目   → 正文
 *   非空且无前缀的行                     → 上一条目的续行
 */
object Changelog {

    data class Entry(val text: String, val sub: Boolean)

    data class Section(val title: String, val entries: List<Entry>)

    data class Version(
        val version: String,
        val date: String,
        val tag: String,
        val lead: String,
        val sections: List<Section>
    )

    /** 行内标记：`**粗体**` 与 `` `代码` `` */
    data class Span(val text: String, val bold: Boolean, val code: Boolean)

    private val VERSION_RE = Regex("""^##\s*\[([^\]]+)]\s*-\s*([0-9]{4}-[0-9]{2}-[0-9]{2})\s*(【[^】]*】)?""")
    private val SECTION_RE = Regex("""^###\s+(.+)$""")
    private val SUB_ITEM_RE = Regex("""^\s{2,}-\s+(.+)$""")
    private val ITEM_RE = Regex("""^-\s+(.+)$""")

    fun parse(md: String): List<Version> {
        val versions = mutableListOf<Version>()
        var ver = ""
        var date = ""
        var tag = ""
        val lead = StringBuilder()
        val sections = mutableListOf<Section>()
        var secTitle: String? = null
        val entries = mutableListOf<Entry>()

        fun flushSection() {
            val t = secTitle
            if (t != null && entries.isNotEmpty()) {
                sections.add(Section(t, entries.toList()))
            }
            entries.clear()
            secTitle = null
        }

        fun flushVersion() {
            flushSection()
            if (ver.isNotBlank()) {
                versions.add(Version(ver, date, tag, lead.toString().trim(), sections.toList()))
            }
            lead.clear()
            sections.clear()
            ver = ""; date = ""; tag = ""
        }

        for (raw in md.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue

            val vm = VERSION_RE.find(line)
            if (vm != null) {
                flushVersion()
                ver = vm.groupValues[1]
                date = vm.groupValues[2]
                tag = vm.groupValues[3]
                continue
            }
            if (ver.isBlank()) continue          // 文件头说明段，不是版本内容
            if (line.trim() == "---") continue

            val sm = SECTION_RE.find(line)
            if (sm != null) {
                flushSection()
                secTitle = sm.groupValues[1].trim()
                continue
            }

            val sub = SUB_ITEM_RE.find(line)
            if (sub != null) {
                entries.add(Entry(sub.groupValues[1].trim(), sub = true))
                continue
            }
            val item = ITEM_RE.find(line)
            if (item != null) {
                entries.add(Entry(item.groupValues[1].trim(), sub = false))
                continue
            }

            // 续行：接到上一条正文后面（中文直接相连，英文单词之间补一个空格）
            val text = line.trim()
            if (entries.isNotEmpty()) {
                val last = entries.removeAt(entries.size - 1)
                entries.add(last.copy(text = join(last.text, text)))
            } else if (secTitle != null) {
                // 分组标题下的说明句，挂成一条无前缀的正文
                entries.add(Entry(text, sub = false))
            } else {
                lead.append(text)
            }
        }
        flushVersion()
        return versions
    }

    private fun join(left: String, right: String): String {
        if (left.isEmpty()) return right
        val needSpace = left.last().isAsciiWord() && right.first().isAsciiWord()
        return if (needSpace) "$left $right" else left + right
    }

    private fun Char.isAsciiWord(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

    /** 拆出行内加粗与代码片段，供 UI 逐段着色 */
    fun inline(text: String): List<Span> {
        val out = mutableListOf<Span>()
        val sb = StringBuilder()
        var bold = false
        var code = false
        fun flush() {
            if (sb.isNotEmpty()) {
                out.add(Span(sb.toString(), bold, code))
                sb.clear()
            }
        }
        var i = 0
        while (i < text.length) {
            if (!code && text.startsWith("**", i)) {
                flush(); bold = !bold; i += 2; continue
            }
            if (!bold && text[i] == '`') {
                flush(); code = !code; i += 1; continue
            }
            sb.append(text[i]); i++
        }
        flush()
        return out
    }
}
