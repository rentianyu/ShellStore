package com.xbta.shellstore.data

import com.xbta.shellstore.core.SmdParser

/**
 * 「小贝塔订阅源」——一个 **SMD 格式的清单文件**，列出可以单独启用的源。
 *
 * 清单托管在 ai 仓库 `ShellStore/sources/index.md`，随 R2 同步到 f.xbta.cc。
 * 格式与普通源完全一致（能用同一套解析器与校验工具），约定是：
 *
 * ```markdown
 * ## 高德地图
 *
 * ### 高德地图
 * @desc 高德地图 URL 快捷入口
 * @count 115
 *
 * ```text
 * https://f.xbta.cc/ai/ShellStore/sources/gaode.md
 * ```
 * ```
 *
 * 即：**代码块里放地址的条目 = 一个可选源**，其它条目一律忽略。
 * 新增源只改这个文件，App 不用发版。AI 整理命令库（原官方源）也在这份清单里，
 * 是其中普通的一项。
 */
object SeriesSources {

    /** 系列标识，写进 SourceConfig.series */
    const val SERIES = "xbta"

    const val LABEL = "小贝塔订阅源"

    const val INDEX_URL = "https://f.xbta.cc/ai/ShellStore/sources/index.md"

    data class Item(
        val id: String,
        val name: String,
        val url: String,
        val desc: String = "",
        val count: Int = 0
    )

    /** 清单拉不到时的兜底：至少保证默认源还能加 */
    private val FALLBACK = listOf(
        Item(
            id = "official",
            name = "AI 整理命令库",
            url = "https://f.xbta.cc/ai/ShellStore/sources/official.md",
            desc = "Android 常用命令、Intent 与组件跳转，10 个分类",
            count = 133
        ),
        Item(
            id = "gaode",
            name = "高德地图",
            url = "https://f.xbta.cc/ai/ShellStore/sources/gaode.md",
            desc = "高德地图 URL 快捷入口",
            count = 115
        )
    )

    private fun isHttpUrl(s: String): Boolean =
        s.startsWith("http://") || s.startsWith("https://")

    /**
     * 用 SMD 解析器读清单：正文是 http(s) 地址的条目即为一个源。
     * 这样清单本身就是一份合法 SMD，能过 check.sh，也能被 App 当普通源导入。
     */
    fun parse(text: String): List<Item> {
        val parsed = SmdParser.parse(text, "series", LABEL)
        return parsed.source.entries.mapNotNull { e ->
            val url = e.body.trim().lines().firstOrNull { isHttpUrl(it.trim()) }?.trim()
                ?: return@mapNotNull null
            if (!isHttpUrl(url)) return@mapNotNull null
            Item(
                id = url,
                name = e.title.ifBlank { "未命名源" },
                url = url,
                desc = e.desc,
                count = e.extra["count"]?.trim()?.toIntOrNull() ?: 0
            )
        }
    }

    /**
     * 拉清单。返回 (清单, 错误信息)，错误为 null 表示拿到了最新版本。
     * 网络不通时返回内置兜底，错误信息供界面提示。
     */
    fun fetch(): Pair<List<Item>, String?> {
        val r = Net.get(INDEX_URL)
        val text = r.text
        if (!r.ok || text.isNullOrBlank()) return FALLBACK to (r.error ?: "清单拉取失败")
        val items = parse(text)
        if (items.isEmpty()) return FALLBACK to "清单为空或格式不对"
        return items to null
    }
}
