package com.xbta.shellstore.core

/**
 * 把条目渲染成可直接使用的一行命令。
 * 纯 Kotlin，无 android 依赖，可用 kotlinc 验证。
 */
object CommandRenderer {

    private val FLAG_MAP = mapOf(
        "NEW_TASK" to "0x10000000",
        "CLEAR_TOP" to "0x04000000",
        "SINGLE_TOP" to "0x20000000",
        "CLEAR_TASK" to "0x00008000",
        "NO_HISTORY" to "0x40000000",
        "EXCLUDE_FROM_RECENTS" to "0x00800000",
        "GRANT_READ_URI_PERMISSION" to "0x00000001",
        "GRANT_WRITE_URI_PERMISSION" to "0x00000002",
        "MULTIPLE_TASK" to "0x08000000",
        "REORDER_TO_FRONT" to "0x02000000"
    )

    /** 把 {key} 占位符替换成用户填的值 */
    fun fill(template: String, entry: Entry, values: Map<String, String>): String {
        if (template.isBlank()) return template
        var s = template
        entry.params.forEach { p ->
            val v = values[p.key]?.takeIf { it.isNotBlank() } ?: p.default
            s = s.replace("{${p.key}}", v)
        }
        return s
    }

    /** 条目最终的文本形态：有 body 用 body，Intent 类用生成的 am 命令 */
    fun primaryText(entry: Entry, values: Map<String, String>, variantIndex: Int): String {
        if (entry.type.hasBody) {
            val raw = if (variantIndex >= 0 && variantIndex < entry.variants.size)
                entry.variants[variantIndex].body else entry.body
            return fill(raw, entry, values)
        }
        return toAdb(entry, values)
    }

    /** 生成等价 adb 命令；@adb 存在时优先使用它 */
    fun toAdb(entry: Entry, values: Map<String, String>): String {
        if (entry.adb.isNotBlank()) return fill(entry.adb, entry, values)

        val spec = entry.intent
        if (entry.type == EntryType.URLSCHEME && spec == null) {
            val d = fill(entry.body, entry, values)
            return "am start -a android.intent.action.VIEW -d ${q(d)}"
        }
        if (spec == null) return ""
        if (!spec.isActionable && spec.data.isBlank()) return ""

        val verb = when (entry.type) {
            EntryType.BROADCAST -> "am broadcast"
            EntryType.SERVICE -> "am startservice"
            else -> "am start"
        }
        val sb = StringBuilder(verb)
        if (spec.action.isNotBlank()) sb.append(" -a ").append(q(spec.action))
        val data = fill(spec.data, entry, values)
        if (data.isNotBlank()) sb.append(" -d ").append(q(data))
        if (spec.mime.isNotBlank()) sb.append(" -t ").append(q(spec.mime))
        val comp = normalizeComponent(spec.component, spec.packageName)
        if (comp.isNotBlank()) sb.append(" -n ").append(q(comp))
        if (spec.packageName.isNotBlank()) sb.append(" -p ").append(q(spec.packageName))
        spec.categories.filter { it.isNotBlank() }.forEach { sb.append(" -c ").append(q(it)) }
        spec.extras.forEach { ex ->
            val v = fill(ex.value, entry, values)
            when (ex.type) {
                ExtraType.STRING -> sb.append(" --es ").append(ex.key).append(" ").append(q(v))
                ExtraType.BOOL -> sb.append(" --ez ").append(ex.key).append(" ").append(v)
                ExtraType.INT -> sb.append(" --ei ").append(ex.key).append(" ").append(v)
                ExtraType.LONG -> sb.append(" --el ").append(ex.key).append(" ").append(v)
                ExtraType.FLOAT -> sb.append(" --ef ").append(ex.key).append(" ").append(v)
                ExtraType.NULL -> sb.append(" --esn ").append(ex.key)
                ExtraType.STRING_ARRAY -> sb.append(" --esa ").append(ex.key).append(" ").append(q(v))
                ExtraType.INT_ARRAY -> sb.append(" --eia ").append(ex.key).append(" ").append(q(v))
            }
        }
        spec.flags.filter { it.isNotBlank() }.forEach { sb.append(" -f ").append(flagValue(it)) }
        return sb.toString()
    }

    fun flagValue(raw: String): String {
        val key = raw.trim().uppercase()
        FLAG_MAP[key]?.let { return it }
        if (key.startsWith("0X")) return key.lowercase().replace("0x", "0x")
        return key
    }

    /** `.MainActivity` 补全成 `包名.MainActivity` */
    fun normalizeComponent(component: String, pkg: String): String {
        val c = component.trim()
        if (c.isEmpty()) return c
        if (!c.contains('/')) return c
        val left = c.substringBefore('/')
        val right = c.substringAfter('/')
        return if (right.startsWith(".")) {
            val base = left.ifBlank { pkg }
            if (base.isBlank()) c else "$base/$base$right"
        } else {
            c
        }
    }

    /** 含空白/引号时加单引号，内部单引号用 '\'' 转义（POSIX 兼容） */
    fun q(s: String): String {
        if (s.isEmpty()) return "''"
        val need = s.any { it.isWhitespace() || it == '\'' || it == '"' || it == '&' || it == '|' || it == '>' || it == '<' }
        if (!need) return s
        if (!s.contains('\'')) return "'$s'"
        return "'" + s.replace("'", "'\\''") + "'"
    }
}
