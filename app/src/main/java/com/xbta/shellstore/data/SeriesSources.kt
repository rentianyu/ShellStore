package com.xbta.shellstore.data

import org.json.JSONObject

/**
 * 「小贝塔系列」订阅源。
 *
 * 清单托管在 ai 仓库 `ShellStore/sources/index.json`，随 R2 同步到 f.xbta.cc，
 * App 拉这一个 json 就知道系列里有哪些源——以后新增源不用发版。
 *
 * 官方源（official）**不在**清单里，它走 Store.OFFICIAL_ID 那套独立逻辑。
 */
object SeriesSources {

    /** 系列标识，写进 SourceConfig.series */
    const val SERIES = "xbta"

    /** 系列显示名 */
    const val LABEL = "小贝塔系列"

    const val INDEX_URL = "https://f.xbta.cc/ai/ShellStore/sources/index.json"

    data class Item(
        val id: String,
        val name: String,
        val url: String,
        val desc: String = "",
        val count: Int = 0
    )

    /** 清单拉不到时的兜底：至少保证已知的源还能加 */
    private val FALLBACK = listOf(
        Item(
            id = "gaode",
            name = "高德地图",
            url = "https://f.xbta.cc/ai/ShellStore/sources/gaode.md",
            desc = "高德地图 URL Scheme",
            count = 115
        )
    )

    fun parse(text: String): List<Item> = runCatching {
        val root = JSONObject(text)
        val arr = root.optJSONArray("sources") ?: return@runCatching emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").trim()
            if (url.isEmpty()) return@mapNotNull null
            Item(
                id = o.optString("id"),
                name = o.optString("name").ifBlank { "未命名源" },
                url = url,
                desc = o.optString("desc"),
                count = o.optInt("count", 0)
            )
        }
    }.getOrDefault(emptyList())

    /**
     * 拉清单。返回 (清单, 错误信息)，错误为 null 表示从网络拿到了最新。
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
