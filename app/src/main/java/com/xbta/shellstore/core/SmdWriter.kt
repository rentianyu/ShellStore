package com.xbta.shellstore.core

/**
 * 把内存里的条目重新写成 SMD 文本（导出用）。
 * 和 SmdParser 互为逆过程：解析出来什么，这里就要能原样写回去。
 */
object SmdWriter {

    private fun extraTypeName(t: ExtraType): String = when (t) {
        ExtraType.STRING -> "string"
        ExtraType.BOOL -> "bool"
        ExtraType.INT -> "int"
        ExtraType.LONG -> "long"
        ExtraType.FLOAT -> "float"
        ExtraType.NULL -> "null"
        ExtraType.STRING_ARRAY -> "string-arr"
        ExtraType.INT_ARRAY -> "int-arr"
    }

    /**
     * @param entry       条目
     * @param note        收藏备注，会并入 @desc
     * @param tagNames    已经解析成名称的标签（Entry.tags 里存的是名称，可直接传）
     */
    fun entry(entry: Entry, note: String = "", tagNames: List<String> = entry.tags): String {
        val sb = StringBuilder()
        sb.append("### ").append(entry.title.trim()).append('\n')
        sb.append("@type ").append(entry.type.id).append('\n')
        sb.append("@risk ").append(entry.risk.id).append('\n')
        sb.append("@need ").append(entry.need.id).append('\n')

        val tags = (tagNames + entry.tags).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (tags.isNotEmpty()) sb.append("@tag ").append(tags.joinToString(" ")).append('\n')

        val desc = buildString {
            if (entry.desc.isNotBlank()) append(entry.desc.replace('\n', ' ').trim())
            if (note.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append("备注：").append(note.replace('\n', ' ').trim())
            }
        }
        if (desc.isNotBlank()) sb.append("@desc ").append(desc).append('\n')

        entry.params.forEach { p ->
            sb.append("@param ").append(p.key).append('=').append(p.label)
            if (p.default.isNotBlank()) sb.append('|').append(p.default)
            sb.append('\n')
        }

        val i = entry.intent
        if (i != null) {
            if (i.action.isNotBlank()) sb.append("@action ").append(i.action).append('\n')
            if (i.component.isNotBlank()) sb.append("@component ").append(i.component).append('\n')
            if (i.data.isNotBlank()) sb.append("@data ").append(i.data).append('\n')
            if (i.mime.isNotBlank()) sb.append("@mime ").append(i.mime).append('\n')
            if (i.packageName.isNotBlank()) sb.append("@package ").append(i.packageName).append('\n')
            i.categories.filter { it.isNotBlank() }.forEach { sb.append("@category ").append(it).append('\n') }
            i.flags.filter { it.isNotBlank() }.forEach { sb.append("@flag ").append(it).append('\n') }
            i.extras.forEach { ex ->
                sb.append("@extra ").append(ex.key).append(':').append(extraTypeName(ex.type))
                    .append('=').append(ex.value.replace('\n', ' ')).append('\n')
            }
        }

        if (entry.body.isNotBlank()) {
            val lang = when (entry.type) {
                EntryType.URLSCHEME -> "text"
                EntryType.SCRIPT -> "sh"
                else -> "sh"
            }
            sb.append('\n')
            sb.append("```").append(lang).append('\n')
            sb.append(entry.body.trim()).append('\n')
            sb.append("```").append('\n')
        } else if (entry.adb.isNotBlank() && entry.type.hasBody) {
            sb.append('\n')
            sb.append("```sh\n").append(entry.adb.trim()).append("\n```\n")
        }
        return sb.toString()
    }

    /** 整个源：文件头 + 若干分类 */
    fun source(name: String, desc: String, groups: List<Pair<String, List<String>>>): String {
        val sb = StringBuilder()
        sb.append("# ").append(name).append("\n\n")
        sb.append("@author 小贝塔\n")
        sb.append("@updated ").append(java.time.LocalDate.now().toString()).append('\n')
        if (desc.isNotBlank()) sb.append("@desc ").append(desc.replace('\n', ' ')).append('\n')
        sb.append('\n')
        groups.forEach { (cat, entries) ->
            // cat 是完整路径（"系统/网络"）；写回时用带空格的 " / " 更易读，
            // 解析端两种写法等价
            val shown = SmdPath.split(cat).joinToString(SmdPath.SEP).ifBlank { cat }
            sb.append("## ").append(shown).append("\n\n")
            entries.forEach { sb.append(it).append('\n') }
        }
        return sb.toString()
    }
}
