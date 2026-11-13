package com.xbta.shellstore.core

/**
 * 筛选条件（商店页与收藏页共用）。
 *
 * 纯数据 + 纯函数，不依赖 android，可桌面单测。UI 只负责收集这些值。
 * 各维度之间是 **AND**；同一维度内的多选是 **OR**（标签可切成「全部」）。
 */
data class FilterState(
    val types: Set<EntryType> = emptySet(),
    val risks: Set<Risk> = emptySet(),
    val needs: Set<Need> = emptySet(),
    /** 分类路径，前缀匹配（选「系统」也包含「系统/网络」里的条目） */
    val cats: Set<String> = emptySet(),
    /** 来源 id；[EntryFilter.LOCAL_SOURCE] 代表本地自建条目 */
    val sources: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val tagMode: TagMode = TagMode.ANY,
    val flags: Set<FilterFlag> = emptySet(),
    val sort: FilterSort = FilterSort.DEFAULT
) {
    val isEmpty: Boolean
        get() = types.isEmpty() && risks.isEmpty() && needs.isEmpty() && cats.isEmpty() &&
            sources.isEmpty() && tags.isEmpty() && flags.isEmpty() && sort == FilterSort.DEFAULT

    /** 生效的维度数，用于标题上显示「N 项」 */
    val activeCount: Int
        get() = listOf(
            types.isNotEmpty(), risks.isNotEmpty(), needs.isNotEmpty(), cats.isNotEmpty(),
            sources.isNotEmpty(), tags.isNotEmpty(), flags.isNotEmpty(),
            sort != FilterSort.DEFAULT
        ).count { it }

    /** 只换排序也算改动，用这个给「重置」按钮判断是否可点 */
    val isDefault: Boolean get() = isEmpty
}

enum class TagMode(val label: String) { ANY("任一"), ALL("全部") }

/** 属性类筛选：不是互斥分类，而是「有没有某个特征」 */
enum class FilterFlag(val label: String) {
    HAS_PARAMS("有参数"),
    HAS_VARIANTS("多写法"),
    HAS_ADB("有 @adb"),
    PINNED("已置顶"),
    VERIFIED("已验证"),
    UNVERIFIED("没验证过"),
    LOCAL("本地自建"),
    EDITED("改过内容"),
    ALIVE("源里还在"),
    FAVORITED("已收藏")
}

enum class FilterSort(val label: String) {
    DEFAULT("默认"),
    TITLE("标题"),
    CATEGORY("分类"),
    RISK_HIGH("风险高→低"),
    SOURCE("来源"),
    RECENT("最近用过")
}

object EntryFilter {

    const val LOCAL_SOURCE = "__local__"
    const val LOCAL_PREFIX = "local_"

    /**
     * 单条是否通过筛选。
     *
     * @param favTags 收藏项自己打的标签（与条目自带的 @tag 合并判断）
     * @param favEdited 收藏项被手动改过内容
     * @param favAlive 收藏项在当前源里还找得到
     * @param isFav 「已收藏」筛选用
     */
    fun match(
        e: Entry,
        f: FilterState,
        favTags: List<String> = emptyList(),
        favEdited: Boolean = false,
        favAlive: Boolean = true,
        isFav: (String) -> Boolean = { false }
    ): Boolean {
        if (f.types.isNotEmpty() && e.type !in f.types) return false
        if (f.risks.isNotEmpty() && e.risk !in f.risks) return false
        if (f.needs.isNotEmpty() && e.need !in f.needs) return false

        if (f.cats.isNotEmpty() && f.cats.none { SmdPath.isUnder(e.categoryId, it) }) return false

        if (f.sources.isNotEmpty()) {
            val src = e.sourceId
            val key = if (src.isBlank()) LOCAL_SOURCE else src
            if (key !in f.sources) return false
        }

        if (f.tags.isNotEmpty()) {
            val pool = (e.tags + favTags)
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            val wanted = f.tags.map { it.trim().lowercase() }
            val ok = when (f.tagMode) {
                TagMode.ANY -> wanted.any { it in pool }
                TagMode.ALL -> wanted.all { it in pool }
            }
            if (!ok) return false
        }

        for (flag in f.flags) {
            val pass = when (flag) {
                FilterFlag.HAS_PARAMS -> e.params.isNotEmpty()
                FilterFlag.HAS_VARIANTS -> e.variants.size > 1
                FilterFlag.HAS_ADB -> e.adb.isNotBlank()
                FilterFlag.PINNED -> e.pinned
                FilterFlag.VERIFIED -> e.verifiedAt.isNotBlank()
                FilterFlag.UNVERIFIED -> e.verifiedAt.isBlank()
                FilterFlag.LOCAL -> e.id.startsWith(LOCAL_PREFIX)
                FilterFlag.EDITED -> favEdited
                FilterFlag.ALIVE -> favAlive
                FilterFlag.FAVORITED -> isFav(e.id)
            }
            if (!pass) return false
        }
        return true
    }

    /**
     * 过滤 + 排序。[keepOrder] 为 true 时默认排序保持传入顺序（搜索相关度已经排好）。
     */
    fun apply(
        list: List<Entry>,
        f: FilterState,
        isFav: (String) -> Boolean = { false },
        recentRank: Map<String, Int> = emptyMap(),
        keepOrder: Boolean = true,
        fav: ((Entry) -> FavExtra?)? = null
    ): List<Entry> {
        if (f.isEmpty) return if (keepOrder) list else sortList(list, f, recentRank)
        val out = list.filter { e ->
            val x = fav?.invoke(e)
            match(e, f, x?.tags ?: emptyList(), x?.edited ?: false, x?.alive ?: true, isFav)
        }
        return sortList(out, f, recentRank)
    }

    /** 收藏页需要额外提供的三项信息，避免 core 直接依赖 data 层的 FavItem */
    data class FavExtra(val tags: List<String>, val edited: Boolean, val alive: Boolean)

    fun sortList(
        list: List<Entry>,
        f: FilterState,
        recentRank: Map<String, Int> = emptyMap()
    ): List<Entry> = when (f.sort) {
        FilterSort.DEFAULT -> list
        // 中文按 Unicode 码位排是乱的（我/打/查/清 毫无规律），改按拼音首字母排
        FilterSort.TITLE -> list.sortedWith(
            compareBy({ SearchText.initialsCached(it.title) }, { it.title })
        )
        FilterSort.CATEGORY -> list.sortedWith(
            compareBy({ SearchText.initialsCached(it.categoryId) }, { it.categoryId }, { it.title })
        )
        FilterSort.RISK_HIGH -> list.sortedWith(
            compareByDescending<Entry> { it.risk.ordinal }.thenBy { it.title }
        )
        FilterSort.SOURCE -> list.sortedWith(compareBy({ it.sourceId }, { it.title }))
        FilterSort.RECENT -> list.sortedWith(compareBy { recentRank[it.id] ?: Int.MAX_VALUE })
    }

    /** 从一批条目里收集可用的标签池（给筛选面板出选项） */
    fun collectTags(entries: List<Entry>, favTags: List<String> = emptyList()): List<String> =
        (entries.flatMap { it.tags } + favTags)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()
}
