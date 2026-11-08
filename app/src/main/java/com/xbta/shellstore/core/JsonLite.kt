package com.xbta.shellstore.core

/**
 * 极简 JSON 解析器，只为「把参数值里的 JSON 展开成键值对」服务。
 *
 * 不用 org.json 的原因：它是 Android SDK 自带的，桌面 kotlinc 跑不了，
 * 而这块逻辑（转义、嵌套、尾逗号）恰恰最需要单测兜住。
 *
 * 解析口径刻意宽松：
 *   · 容忍尾逗号（`{"a":1,}`）、单引号字符串里的裸换行
 *   · 不校验数字格式的严格性（原样保留文本）
 *   · 只把对象/数组当作「可展开」的根，字符串/数字一律视为普通值
 *
 * 纯逻辑，不依赖 android。
 */
object JsonLite {

    sealed interface Node

    data class Obj(val fields: List<Pair<String, Node>>) : Node
    data class Arr(val items: List<Node>) : Node
    data class Str(val value: String) : Node
    data class Num(val raw: String) : Node
    data class Bool(val value: Boolean) : Node
    data object Null : Node

    private const val MAX_DEPTH = 64

    /**
     * 解析成对象或数组；不是 JSON（或解析失败）返回 null。
     * 只认 `{` / `[` 开头，避免把 `123`、`true`、普通文本当成 JSON。
     */
    fun parseOrNull(text: String): Node? {
        val t = text.trim()
        if (t.length < 2) return null
        if (t[0] != '{' && t[0] != '[') return null
        val p = P(t)
        return runCatching {
            val n = p.value(0)
            p.skipWs()
            if (p.i != t.length) null else n
        }.getOrNull()
    }

    /** 紧凑串（用于「展开前」在行里显示的预览） */
    fun compose(node: Node): String = buildString { write(node, this) }

    private fun write(n: Node, sb: StringBuilder) {
        when (n) {
            is Obj -> {
                sb.append('{')
                n.fields.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    sb.append('"').append(escape(k)).append('"').append(':')
                    write(v, sb)
                }
                sb.append('}')
            }
            is Arr -> {
                sb.append('[')
                n.items.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    write(v, sb)
                }
                sb.append(']')
            }
            is Str -> sb.append('"').append(escape(n.value)).append('"')
            is Num -> sb.append(n.raw)
            is Bool -> sb.append(if (n.value) "true" else "false")
            Null -> sb.append("null")
        }
    }

    private fun escape(s: String): String = buildString(s.length + 8) {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
        }
    }

    /** 节点的一行展示文本：字符串不带引号（方便直接看编码后的内容） */
    fun plain(n: Node): String = when (n) {
        is Str -> n.value
        is Num -> n.raw
        is Bool -> if (n.value) "true" else "false"
        Null -> "null"
        is Obj, is Arr -> compose(n)
    }

    /** 是否是「可以展开成一堆键值」的节点 */
    fun expandable(n: Node): Boolean = n is Obj || n is Arr

    // ---------------- 递归下降 ----------------

    private class P(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(depth: Int): Node {
            if (depth > MAX_DEPTH) throw IllegalStateException("嵌套过深")
            skipWs()
            if (i >= s.length) throw IllegalStateException("意外结束")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> Str(str())
                '\'' -> Str(strSingle())
                't', 'f' -> bool()
                'n' -> nul()
                else -> {
                    if (c == '-' || c.isDigit()) num() else throw IllegalStateException("非法字符 $c")
                }
            }
        }

        fun obj(depth: Int): Obj {
            expect('{')
            val out = ArrayList<Pair<String, Node>>(8)
            skipWs()
            if (peek() == '}') { i++; return Obj(out) }
            while (true) {
                skipWs()
                if (peek() == '}') { i++; break }          // 容忍尾逗号
                val k = when (peek()) {
                    '"' -> str()
                    '\'' -> strSingle()
                    else -> throw IllegalStateException("键必须是字符串")
                }
                skipWs()
                expect(':')
                val v = value(depth + 1)
                out.add(k to v)
                skipWs()
                when (peek()) {
                    ',' -> { i++; continue }
                    '}' -> { i++; break }
                    else -> throw IllegalStateException("对象未闭合")
                }
            }
            return Obj(out)
        }

        fun arr(depth: Int): Arr {
            expect('[')
            val out = ArrayList<Node>(8)
            skipWs()
            if (peek() == ']') { i++; return Arr(out) }
            while (true) {
                skipWs()
                if (peek() == ']') { i++; break }          // 容忍尾逗号
                out.add(value(depth + 1))
                skipWs()
                when (peek()) {
                    ',' -> { i++; continue }
                    ']' -> { i++; break }
                    else -> throw IllegalStateException("数组未闭合")
                }
            }
            return Arr(out)
        }

        fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) break
                        val e = s[i++]
                        when (e) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 <= s.length) {
                                    val hex = s.substring(i, i + 4)
                                    sb.append(hex.toInt(16).toChar())
                                    i += 4
                                }
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalStateException("字符串未闭合")
        }

        /** 宽松容忍单引号（部分手写 JSON） */
        fun strSingle(): String {
            expect('\'')
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                when (c) {
                    '\'' -> return sb.toString()
                    '\\' -> if (i < s.length) sb.append(s[i++])
                    else -> sb.append(c)
                }
            }
            throw IllegalStateException("字符串未闭合")
        }

        fun num(): Num {
            val st = i
            if (peek() == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' ||
                        s[i] == '+' || s[i] == '-')
            ) i++
            if (i == st) throw IllegalStateException("数字为空")
            return Num(s.substring(st, i))
        }

        fun bool(): Bool {
            return when {
                s.startsWith("true", i) -> { i += 4; Bool(true) }
                s.startsWith("false", i) -> { i += 5; Bool(false) }
                else -> throw IllegalStateException("非法字面量")
            }
        }

        fun nul(): Node {
            if (s.startsWith("null", i)) { i += 4; return Null }
            throw IllegalStateException("非法字面量")
        }

        fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        fun expect(c: Char) {
            if (i >= s.length || s[i] != c) throw IllegalStateException("期望 $c")
            i++
        }
    }

    // ---------------- 按路径改值 ----------------

    /**
     * 把 [path] 指向的叶子节点替换成 [text]。
     *
     * path 是「从根往下每一步的序号」：对象按字段顺序、数组按下标。
     * 空 path 表示替换根节点本身。
     *
     * 类型尽量保持原样：原来是数字且新文本仍是数字就还是数字，
     * 原来是布尔且新文本是 true/false 就还是布尔，否则降级成字符串——
     * 免得把 `{"id":1}` 里的 1 改成 `"2"` 之后变成字符串类型。
     */
    fun setText(root: Node, path: List<Int>, text: String): Node {
        if (path.isEmpty()) return leafLike(root, text)
        return when (root) {
            is Obj -> {
                val i = path[0]
                if (i !in root.fields.indices) return root
                val list = root.fields.toMutableList()
                val (k, v) = list[i]
                list[i] = k to setText(v, path.drop(1), text)
                Obj(list)
            }
            is Arr -> {
                val i = path[0]
                if (i !in root.items.indices) return root
                val list = root.items.toMutableList()
                list[i] = setText(list[i], path.drop(1), text)
                Arr(list)
            }
            else -> root
        }
    }

    /** 按旧节点类型决定新文本该是什么节点 */
    private fun leafLike(old: Node, text: String): Node {
        val t = text.trim()
        return when (old) {
            is Num -> if (t.toDoubleOrNull() != null) Num(t) else Str(text)
            is Bool -> when (t.lowercase()) {
                "true" -> Bool(true)
                "false" -> Bool(false)
                else -> Str(text)
            }
            Null -> if (t == "null") Null else Str(text)
            // 结构节点不通过「改值」替换，避免误把一棵树压成字符串
            is Obj, is Arr -> old
            is Str -> Str(text)
        }
    }

    /** 节点个数（对象=字段数，数组=元素数），用于展示 `{ N 个键 }` */
    fun size(n: Node): Int = when (n) {
        is Obj -> n.fields.size
        is Arr -> n.items.size
        else -> 0
    }
}
