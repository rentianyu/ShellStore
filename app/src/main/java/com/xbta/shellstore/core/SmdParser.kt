package com.xbta.shellstore.core

/**
 * SMD（Shell Markdown）解析器。
 *
 * 语法四条：
 *   1. `## 分类名`            → 分类
 *   2. `### 标题` 或 `● 标题`  → 条目（● / • 兼容外部清单）
 *   3. `@键 值`               → 元信息
 *   4. 围栏代码块 ``` / ~~~   → 命令体（内容原样，不做任何解析）
 *
 * 铁律：代码块内永不解析。命令里出现 # : " \ { | 全部安全。
 */
object SmdParser {

    // ---------- 对外入口 ----------

    fun parse(text: String, sourceId: String, sourceName: String = ""): ParseResult {
        val warnings = mutableListOf<ParseWarning>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        // 纯清单文件（没有 ### / ● / •）时才把 `- ` 当条目定界符，
        // 避免 Markdown 列表被误判成条目。
        val strictMode = lines.any {
            val t = it.trimStart()
            t.startsWith("### ") || t.startsWith("●") || t.startsWith("•")
        }

        val b = Builder(sourceId)
        val rawAll = StringBuilder()

        var fence: String? = null
        var fenceLang = ""
        var fenceBuf: MutableList<String>? = null
        var fenceOpenedAt = 0
        var lineSink: ((String) -> Unit)? = null
        var curEntry: EntryBuilder? = null
        var curCategory: String = ""

        fun flushEntry() {
            curEntry?.let { e ->
                val built = e.build(curCategory, sourceId, warnings)
                if (built != null) b.entries.add(built) else {
                    warnings.add(ParseWarning(e.startLine, "条目「${e.title}」缺少可执行内容，已跳过"))
                }
            }
            curEntry = null
            lineSink = null
        }

        var idx = 0
        while (idx < lines.size) {
            val raw = lines[idx]
            val trimmed = raw.trim()
            val lineNo = idx + 1

            // ---- 代码块内：只认结束围栏，其余原样收 ----
            if (fence != null) {
                if (trimmed.startsWith(fence) && trimmed.removePrefix(fence).isBlank()) {
                    val content = fenceBuf?.joinToString("\n") ?: ""
                    curEntry?.addBlock(content, fenceLang)
                    fence = null
                    fenceBuf = null
                    fenceLang = ""
                } else {
                    fenceBuf?.add(raw)   // 保留原始缩进
                }
                idx++
                continue
            }

            // ---- 空行 ----
            if (trimmed.isEmpty()) {
                lineSink = null
                idx++
                continue
            }

            // ---- 代码块开始 ----
            val fenceMark = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (fenceMark != null) {
                fence = fenceMark
                fenceLang = trimmed.removePrefix(fenceMark).trim()
                fenceBuf = mutableListOf()
                fenceOpenedAt = lineNo
                lineSink = null
                idx++
                continue
            }

            // ---- 分类 ----
            if (trimmed.startsWith("## ") && !trimmed.startsWith("### ")) {
                flushEntry()
                curCategory = trimmed.removePrefix("## ").trim()
                b.touchCategory(curCategory)
                idx++
                continue
            }

            // ---- 条目 ----
            val isEntryLine =
                trimmed.startsWith("### ") ||
                trimmed.startsWith("●") ||
                trimmed.startsWith("•") ||
                (!strictMode && (trimmed.startsWith("- ") || trimmed.startsWith("* ")))

            if (isEntryLine) {
                flushEntry()
                val title = when {
                    trimmed.startsWith("### ") -> trimmed.removePrefix("### ").trim()
                    trimmed.startsWith("●") -> trimmed.removePrefix("●").trim()
                    trimmed.startsWith("•") -> trimmed.removePrefix("•").trim()
                    trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim()
                    else -> trimmed.removePrefix("* ").trim()
                }
                if (title.isNotEmpty()) {
                    curEntry = EntryBuilder(title, lineNo)
                }
                idx++
                continue
            }

            // ---- 元信息 ----
            if (trimmed.startsWith("@")) {
                val m = AT_LINE.find(trimmed)
                if (m == null) {
                    warnings.add(ParseWarning(lineNo, "无法识别的元信息行：$trimmed"))
                    idx++
                    continue
                }
                val key = m.groupValues[1].lowercase()
                val value = m.groupValues[2].trim()
                val target = curEntry
                if (target == null) {
                    b.applyFileMeta(key, value)
                } else {
                    lineSink = target.applyMeta(key, value, lineNo, warnings)
                }
                idx++
                continue
            }

            // ---- 缩进续行（@param 说明 / @example 多行值）----
            val sink = lineSink
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && sink != null) {
                sink.invoke(trimmed)
                idx++
                continue
            }
            lineSink = null

            // ---- 文件标题 `# xxx` ----
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                if (b.name.isBlank()) b.name = trimmed.removePrefix("# ").trim()
                idx++
                continue
            }

            // ---- 其余文本：作为当前条目的说明 ----
            curEntry?.descLines?.add(trimmed)
            idx++
        }

        if (fence != null) {
            warnings.add(ParseWarning(fenceOpenedAt, "代码块未闭合，已丢弃该段内容"))
        }
        flushEntry()

        val meta = SourceMeta(
            name = b.name.ifBlank { sourceName },
            version = b.version,
            author = b.author,
            updated = b.updated,
            schema = b.schema
        )
        val source = Source(
            id = sourceId,
            name = meta.name.ifBlank { sourceId },
            type = SourceType.BUILTIN,
            meta = meta,
            categories = b.buildCategories(),
            entries = b.entries,
            rawText = text
        )
        return ParseResult(source, warnings)
    }

    private val AT_LINE = Regex("""^@([A-Za-z0-9_.\-]+)\s*(.*)$""")

    // ---------- 内部构建 ----------

    private class Builder(val sourceId: String) {
        var name = ""
        var version = ""
        var author = ""
        var updated = ""
        var schema = 1
        val entries = mutableListOf<Entry>()
        private val catOrder = LinkedHashMap<String, Int>()
        private val catIcon = mutableMapOf<String, String>()
        private var pendingCategoryForIcon: String? = null

        fun touchCategory(name: String) {
            if (!catOrder.containsKey(name)) catOrder[name] = catOrder.size
            pendingCategoryForIcon = name
        }

        fun applyFileMeta(key: String, value: String) {
            when (key) {
                "name", "title" -> if (name.isBlank()) name = value
                "version" -> version = value
                "author" -> author = value
                "updated", "date" -> updated = value
                "schema" -> schema = value.toIntOrNull() ?: 1
                "icon" -> pendingCategoryForIcon?.let { catIcon[it] = value }
            }
        }

        fun buildCategories(): List<Category> =
            catOrder.entries.mapIndexed { i, (n, order) ->
                Category(id = n, name = n, icon = catIcon[n] ?: "", order = order)
            }
    }

    private class EntryBuilder(val title: String, val startLine: Int) {
        val descLines = mutableListOf<String>()
        var typeHint: EntryType? = null
        var riskHint: Risk? = null
        var needHint: Need? = null
        var tags = mutableListOf<String>()
        var doc = ""
        var exampleRaw: String? = null
        var adb = ""
        var origin = ""
        var pinned = false
        var verifiedAt = ""
        val params = mutableListOf<Param>()
        val variants = mutableListOf<Variant>()
        val extra = mutableMapOf<String, String>()

        val intent = IntentBuilder()
        private var body = ""
        private var variantName: String? = null
        private var blockCount = 0
        private var pendingParam: Int = -1
        private var exampleLines: MutableList<String>? = null

        fun addBlock(content: String, lang: String) {
            blockCount++
            if (variantName != null) {
                variants.add(Variant(variantName!!, content))
                variantName = null
            } else if (body.isBlank()) {
                body = content
            } else {
                // 没有 @v 标注的第二个代码块 → 自动命名
                variants.add(Variant("写法${variants.size + 2}", content))
            }
        }

        /** 返回续行接收器（缩进行归属），没有续行语义时返回 null */
        fun applyMeta(key: String, value: String, lineNo: Int, warns: MutableList<ParseWarning>): ((String) -> Unit)? {
            when (key) {
                "type" -> typeHint = EntryType.of(value)
                "risk" -> riskHint = Risk.of(value)
                "need" -> needHint = Need.of(value)
                "tag", "tags" -> tags.addAll(value.split(',', ' ', '，').map { it.trim() }.filter { it.isNotEmpty() })
                "desc" -> descLines.add(0, value)
                "doc", "link" -> doc = value
                "example" -> {
                    if (value.isNotEmpty()) exampleRaw = value
                    else {
                        val buf = mutableListOf<String>()
                        exampleLines = buf
                        return { s -> buf.add(s) }
                    }
                }
                "adb" -> adb = value
                "pinned" -> pinned = value.equals("true", true)
                "v", "variant" -> variantName = value
                "x.verified", "verified" -> verifiedAt = value
                "action" -> intent.action = value
                "data" -> intent.data = value
                "mime" -> intent.mime = value
                "component" -> intent.component = value
                "package", "pkg" -> intent.packageName = value
                "category", "cat" -> intent.categories = intent.categories + value
                "extra" -> parseExtra(value, lineNo, warns)
                "flag" -> intent.flags = intent.flags + value
                "param" -> {
                    val p = parseParam(value, lineNo, warns)
                    if (p != null) {
                        params.add(p)
                        val pi = params.size - 1
                        return { line ->
                            val old = params[pi].note
                            params[pi] = params[pi].copy(
                                note = if (old.isBlank()) line else "$old $line"
                            )
                        }
                    }
                }
                else -> extra[key] = value   // @x.* 与未知字段原样保留
            }
            pendingParam = if (key == "param") pendingParam else -1
            return null
        }

        private fun parseExtra(value: String, lineNo: Int, warns: MutableList<ParseWarning>) {
            val eq = value.indexOf('=')
            if (eq <= 0) {
                warns.add(ParseWarning(lineNo, "@extra 缺少「=」，应为 key:type=value"))
                return
            }
            val left = value.substring(0, eq).trim()
            val v = value.substring(eq + 1)
            val colon = left.indexOf(':')
            val k = if (colon > 0) left.substring(0, colon).trim() else left
            val t = if (colon > 0) left.substring(colon + 1).trim() else ""
            intent.extras = intent.extras + Extra(k, ExtraType.of(t), v)
        }

        private fun parseParam(value: String, lineNo: Int, warns: MutableList<ParseWarning>): Param? {
            val eq = value.indexOf('=')
            if (eq <= 0) {
                // 允许只写 @param 键名
                val k = value.trim()
                if (k.isEmpty()) {
                    warns.add(ParseWarning(lineNo, "@param 缺少键名"))
                    return null
                }
                return Param(k, k)
            }
            val k = value.substring(0, eq).trim()
            val right = value.substring(eq + 1).trim()
            val pipe = right.indexOf('|')
            val label = if (pipe >= 0) right.substring(0, pipe).trim() else right
            val def = if (pipe >= 0) right.substring(pipe + 1).trim() else ""
            return Param(k, label.ifBlank { k }, def)
        }

        fun build(categoryName: String, sourceId: String, warns: MutableList<ParseWarning>): Entry? {
            // 宽松模式：命令块没写、但有纯文本行 → 那些行就是命令
            if (body.isBlank() && blockCount == 0 && descLines.isNotEmpty() && intent.action.isBlank() && intent.component.isBlank()) {
                body = descLines.joinToString("\n")
                descLines.clear()
            }
            if (body.isBlank() && intent.action.isBlank() && intent.component.isBlank()) return null

            // 单行 body 若带 adb shell 前缀 → 剥离，原文留底
            var cleanBody = body
            var originText = ""
            var usedSu = false
            if (cleanBody.isNotBlank()) {
                val stripped = ShellSanitizer.strip(cleanBody)
                if (stripped.changed) {
                    originText = cleanBody
                    cleanBody = stripped.cleaned
                }
                usedSu = stripped.rooted
            }

            val type = typeHint ?: inferType(cleanBody, intent)
            val risk = riskHint ?: inferRisk(cleanBody)
            val need = needHint ?: if (usedSu) Need.ROOT else inferNeed(cleanBody)

            val example = exampleRaw ?: exampleLines?.joinToString("\n").orEmpty()

            return Entry(
                id = makeId(categoryName, title),
                type = type,
                title = title,
                desc = descLines.joinToString("\n").trim(),
                categoryId = categoryName,
                tags = tags,
                risk = risk,
                need = need,
                body = cleanBody,
                variants = variants,
                params = params,
                example = example,
                doc = doc,
                adb = adb,
                intent = if (intent.isEmpty()) null else intent.toSpec(),
                extra = extra,
                verifiedAt = verifiedAt,
                pinned = pinned,
                origin = originText
            )
        }
    }

    private class IntentBuilder {
        var action = ""
        var data = ""
        var mime = ""
        var component = ""
        var packageName = ""
        var categories = listOf<String>()
        var extras = listOf<Extra>()
        var flags = listOf<String>()

        fun isEmpty() = action.isBlank() && data.isBlank() && mime.isBlank() &&
            component.isBlank() && packageName.isBlank() &&
            categories.isEmpty() && extras.isEmpty() && flags.isEmpty()

        fun toSpec() = IntentSpec(action, data, mime, component, packageName, categories, extras, flags)
    }

    // ---------- 推断 ----------

    private val URL_SCHEME = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*://""")

    private fun inferType(body: String, intent: IntentBuilder): EntryType {
        if (body.isBlank()) return EntryType.ACTIVITY
        val first = body.lineSequence().firstOrNull()?.trim().orEmpty()
        if (first.contains("://") && !first.contains(' ')) return EntryType.URLSCHEME
        if (first.startsWith("am broadcast") || first.startsWith("am b ")) return EntryType.BROADCAST
        if (first.startsWith("am startservice") || first.startsWith("am stopservice")) return EntryType.SERVICE
        if (first.startsWith("am start")) return EntryType.ACTIVITY
        if (body.contains('\n')) return EntryType.SCRIPT
        return EntryType.SHELL
    }

    private val DANGER_TOKENS = listOf(
        "rm -rf", "rm -r ", "pm uninstall", "pm clear", "reboot", "shutdown",
        "format ", "mkfs", "dd if=", "wipe ", "pm disable", "pm suspend",
        "settings delete", "chmod 000", "mv /system", "fastboot "
    )
    private val CAUTION_TOKENS = listOf(
        "settings put", "setprop", "am force-stop", "am kill", "pm grant", "pm revoke",
        "pm hide", "pm block", "appops set", "svc ", "input ", "mount -o",
        "curl ", "wget ", "kill ", "am broadcast", "am startservice"
    )

    fun inferRisk(body: String): Risk {
        val low = body.lowercase()
        if (DANGER_TOKENS.any { low.contains(it.lowercase()) }) return Risk.DANGER
        if (CAUTION_TOKENS.any { low.contains(it.lowercase()) }) return Risk.CAUTION
        return Risk.SAFE
    }

    private val SHELL_TOKENS = listOf(
        "pm ", "am ", "dumpsys", "settings ", "cmd ", "content ", "getprop",
        "wm ", "svc ", "appops", "logcat", "input ", "monkey", "screencap",
        "uiautomator", "dpm ", "service call", "dumpsys"
    )

    fun inferNeed(body: String): Need {
        val low = body.lowercase()
        if (low.startsWith("su ") || low.contains(" su -c ") || low.startsWith("su -c")) return Need.ROOT
        if (SHELL_TOKENS.any { low.contains(it) }) return Need.SHELL
        return Need.NONE
    }

    fun makeId(category: String, title: String): String {
        val h = ("$category|$title").hashCode().toUInt().toString(16).padStart(8, '0')
        return "e$h"
    }
}

/** 剥离 adb shell / su -c / 终端提示符前缀，得到裸命令 */
object ShellSanitizer {

    data class Result(val cleaned: String, val changed: Boolean, val rooted: Boolean = false)

    private val PROMPT = Regex("""^[$#>❯]\s+""")
    private val ADB_SHELL = Regex("""^adb(?:\s+-(?:s|H|P|t)\s+\S+|\s+--?\w+)*\s+shell\s+""", RegexOption.IGNORE_CASE)
    private val SU_C = Regex("""^su(?:\s+-\w+)*\s+-c\s+""", RegexOption.IGNORE_CASE)

    fun strip(input: String): Result {
        var s = input
        var changed = false
        var rooted = false

        // 提示符
        val p = PROMPT.find(s)
        if (p != null) {
            s = s.substring(p.range.last + 1).trimStart()
            changed = true
        }

        val adb = ADB_SHELL.find(s)
        if (adb != null) {
            s = s.substring(adb.range.last + 1).trimStart()
            changed = true
            s = unquote(s)
        }

        val su = SU_C.find(s)
        if (su != null) {
            s = s.substring(su.range.last + 1).trimStart()
            changed = true
            rooted = true
            s = unquote(s)
        }

        return Result(s, changed, rooted)
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        if (t.length >= 2) {
            val a = t.first()
            val b = t.last()
            if (a == b && (a == '\'' || a == '"') && !t.substring(1, t.length - 1).contains(a)) {
                return t.substring(1, t.length - 1)
            }
        }
        return t
    }
}
