package com.xbta.shellstore.core

/**
 * 数据模型：所有源（内置/在线/本地）解析后都归一到这里。
 * 纯 Kotlin，不依赖 android.*，可以用 kotlinc 本地编译验证。
 */

enum class EntryType(val id: String) {
    SHELL("shell"),
    SCRIPT("script"),
    URLSCHEME("urlscheme"),
    INTENT("intent"),
    ACTIVITY("activity"),
    SERVICE("service"),
    BROADCAST("broadcast");

    /** 有 body 的类型走代码块 + 复制；其余走字段表 */
    val hasBody: Boolean get() = this == SHELL || this == SCRIPT || this == URLSCHEME

    companion object {
        fun of(raw: String?): EntryType? = entries.firstOrNull { it.id.equals(raw?.trim(), true) }
    }
}

enum class Risk(val id: String, val label: String) {
    SAFE("safe", "安全"),
    CAUTION("caution", "谨慎"),
    DANGER("danger", "危险");

    companion object {
        fun of(raw: String?): Risk? = entries.firstOrNull { it.id.equals(raw?.trim(), true) }
    }
}

enum class Need(val id: String, val label: String) {
    NONE("none", "无需权限"),
    SHELL("shell", "需 shell"),
    ROOT("root", "需 root"),
    TERMUX("termux", "需 Termux");

    companion object {
        fun of(raw: String?): Need? = entries.firstOrNull { it.id.equals(raw?.trim(), true) }
    }
}

enum class SourceType { BUILTIN, REMOTE, LOCAL }

enum class ExtraType {
    STRING, BOOL, INT, LONG, FLOAT, NULL, STRING_ARRAY, INT_ARRAY;

    companion object {
        fun of(raw: String?): ExtraType = when (raw?.trim()?.lowercase()) {
            "bool", "boolean", "z" -> BOOL
            "int", "integer", "i" -> INT
            "long", "l" -> LONG
            "float", "f" -> FLOAT
            "null" -> NULL
            "arr", "array", "strarr", "stringarray" -> STRING_ARRAY
            "intarr", "intarray" -> INT_ARRAY
            else -> STRING
        }
    }
}

data class Extra(val key: String, val type: ExtraType, val value: String)

data class IntentSpec(
    val action: String = "",
    val data: String = "",
    val mime: String = "",
    val component: String = "",
    val packageName: String = "",
    val categories: List<String> = emptyList(),
    val extras: List<Extra> = emptyList(),
    val flags: List<String> = emptyList()
) {
    /** 是否具备构造一个可用 Intent 的最低条件 */
    val isActionable: Boolean
        get() = component.isNotBlank() || action.isNotBlank()
}

data class Param(
    val key: String,
    val label: String,
    val default: String = "",
    val note: String = ""
)

data class Variant(val name: String, val body: String)

data class Category(
    val id: String,
    val name: String,
    val icon: String = "",
    val order: Int = 0
)

data class Entry(
    val id: String,
    val type: EntryType,
    val title: String,
    val desc: String = "",
    val categoryId: String = "",
    val tags: List<String> = emptyList(),
    val risk: Risk = Risk.SAFE,
    val need: Need = Need.NONE,
    /** shell / script / urlscheme 的命令原文（已剥离 adb shell 前缀） */
    val body: String = "",
    val variants: List<Variant> = emptyList(),
    val params: List<Param> = emptyList(),
    val example: String = "",
    val doc: String = "",
    /** @adb 保真命令，非空时优先使用 */
    val adb: String = "",
    val intent: IntentSpec? = null,
    /** @x.* 与未知字段，原样保留 */
    val extra: Map<String, String> = emptyMap(),
    val verifiedAt: String = "",
    val pinned: Boolean = false,
    /** 导入时的原始写法（含 adb shell 前缀），仅供详情页折叠展示 */
    val origin: String = "",
    /** 来自哪个源，多源聚合时用于回溯 */
    val sourceId: String = ""
) {
    /** 详情页主按钮文案 */
    val primaryActionLabel: String
        get() = when (type) {
            EntryType.SHELL, EntryType.SCRIPT -> "复制命令"
            EntryType.URLSCHEME -> "复制链接"
            else -> "复制 adb 命令"
        }
}

data class SourceMeta(
    val name: String = "",
    val version: String = "",
    val author: String = "",
    val updated: String = "",
    val schema: Int = 1
)

data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val meta: SourceMeta = SourceMeta(),
    val categories: List<Category> = emptyList(),
    val entries: List<Entry> = emptyList(),
    /** 源的原始文本（本地导入源需要能随备份一起走） */
    val rawText: String = "",
    val enabled: Boolean = true
) {
    val count: Int get() = entries.size
}

data class ParseWarning(val line: Int, val message: String)

data class ParseResult(
    val source: Source,
    val warnings: List<ParseWarning> = emptyList()
) {
    val ok: Boolean get() = source.entries.isNotEmpty()
}
