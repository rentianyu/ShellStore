import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.EntryFilter
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.core.FilterFlag
import com.xbta.shellstore.core.FilterSort
import com.xbta.shellstore.core.FilterState
import com.xbta.shellstore.core.Need
import com.xbta.shellstore.core.Param
import com.xbta.shellstore.core.Risk
import com.xbta.shellstore.core.TagMode
import com.xbta.shellstore.core.Variant

/** EntryFilter 的桌面单测（纯逻辑，不依赖 android） */
private var pass = 0
private var fail = 0

private fun eq(what: String, want: Any?, got: Any?) {
    if (want == got) pass++ else { fail++; println("  ✗ $what\n      期望: $want\n      实际: $got") }
}

private fun ok(what: String, cond: Boolean) {
    if (cond) pass++ else { fail++; println("  ✗ $what") }
}

private fun e(
    id: String,
    title: String,
    cat: String = "工具",
    type: EntryType = EntryType.SHELL,
    risk: Risk = Risk.SAFE,
    need: Need = Need.NONE,
    tags: List<String> = emptyList(),
    params: List<Param> = emptyList(),
    variants: List<Variant> = emptyList(),
    adb: String = "",
    verified: String = "",
    sourceId: String = "s1"
) = Entry(
    id = id, type = type, title = title, categoryId = cat, tags = tags, risk = risk,
    need = need, params = params, variants = variants, adb = adb,
    verifiedAt = verified, sourceId = sourceId
)

fun main() {
    val a = e("a", "查看电池", "系统/电源")
    val b = e("b", "清空 DNS", "系统/网络/DNS", risk = Risk.CAUTION, need = Need.ROOT,
        tags = listOf("dns", "网络"), verified = "2026-11-01")
    val c = e("c", "打开微信", "应用", type = EntryType.ACTIVITY, risk = Risk.DANGER,
        tags = listOf("社交"), params = listOf(Param("x", "参数", "1")))
    val d = e("local_1", "我的脚本", "本地", variants = listOf(Variant("", ""), Variant("v2", "")),
        adb = "am start -n a/b", sourceId = "")
    val all = listOf(a, b, c, d)

    println("== 空条件 ==")
    ok("空筛选全通过", FilterState().isEmpty)
    eq("空筛选不改变列表", all.map { it.id }, EntryFilter.apply(all, FilterState()).map { it.id })
    eq("activeCount 0", 0, FilterState().activeCount)

    println("== 类型 ==")
    eq("只留 activity", listOf("c"),
        EntryFilter.apply(all, FilterState(types = setOf(EntryType.ACTIVITY))).map { it.id })
    eq("多类型 OR", listOf("a", "b", "local_1"),
        EntryFilter.apply(all, FilterState(types = setOf(EntryType.SHELL))).map { it.id })
    eq("类型 activeCount=1", 1, FilterState(types = setOf(EntryType.SHELL)).activeCount)

    println("== 风险 ==")
    eq("只看危险", listOf("c"),
        EntryFilter.apply(all, FilterState(risks = setOf(Risk.DANGER))).map { it.id })
    eq("危险+谨慎", listOf("b", "c"),
        EntryFilter.apply(all, FilterState(risks = setOf(Risk.DANGER, Risk.CAUTION))).map { it.id })

    println("== 权限 ==")
    eq("只看需 root", listOf("b"),
        EntryFilter.apply(all, FilterState(needs = setOf(Need.ROOT))).map { it.id })

    println("== 分类（目录前缀匹配）==")
    eq("选父目录带出子目录", listOf("a", "b"),
        EntryFilter.apply(all, FilterState(cats = setOf("系统"))).map { it.id })
    eq("选深层只留该层", listOf("b"),
        EntryFilter.apply(all, FilterState(cats = setOf("系统/网络/DNS"))).map { it.id })
    eq("多分类 OR", listOf("a", "b", "c"),
        EntryFilter.apply(all, FilterState(cats = setOf("系统", "应用"))).map { it.id })

    println("== 来源 ==")
    eq("按源过滤", listOf("a"), EntryFilter.apply(all, FilterState(sources = setOf("s1"))).map { it.id }
        .filter { it != "b" && it != "c" })
    eq("只留本地自建", listOf("local_1"),
        EntryFilter.apply(all, FilterState(sources = setOf(EntryFilter.LOCAL_SOURCE))).map { it.id })

    println("== 标签 ==")
    eq("任一命中", listOf("b"), EntryFilter.apply(all, FilterState(tags = setOf("dns"))).map { it.id })
    eq("标签大小写不敏感", listOf("b"),
        EntryFilter.apply(all, FilterState(tags = setOf("DNS"))).map { it.id })
    eq("默认 ANY：命中一个就留", listOf("b", "c"),
        EntryFilter.apply(all, FilterState(tags = setOf("dns", "社交"))).map { it.id })
    eq("ALL：两个都要有", emptyList<String>(),
        EntryFilter.apply(all, FilterState(tags = setOf("dns", "社交"), tagMode = TagMode.ALL)).map { it.id })
    eq("ALL 命中", listOf("b"),
        EntryFilter.apply(all, FilterState(tags = setOf("dns", "网络"), tagMode = TagMode.ALL)).map { it.id })
    eq("收藏标签参与判断", listOf("a"),
        EntryFilter.apply(all, FilterState(tags = setOf("常驻")),
            fav = { if (it.id == "a") EntryFilter.FavExtra(listOf("常驻"), false, true) else null })
            .map { it.id })

    println("== 属性 flags ==")
    eq("有参数", listOf("c"), EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.HAS_PARAMS))).map { it.id })
    eq("多写法", listOf("local_1"), EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.HAS_VARIANTS))).map { it.id })
    eq("有 @adb", listOf("local_1"), EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.HAS_ADB))).map { it.id })
    eq("已验证", listOf("b"), EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.VERIFIED))).map { it.id })
    eq("没验证过", listOf("a", "c", "local_1"),
        EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.UNVERIFIED))).map { it.id })
    eq("本地自建", listOf("local_1"), EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.LOCAL))).map { it.id })
    eq("已收藏", listOf("a", "c"),
        EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.FAVORITED)),
            isFav = { it == "a" || it == "c" }).map { it.id })
    eq("改过内容（收藏页）", listOf("b"),
        EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.EDITED)),
            fav = { if (it.id == "b") EntryFilter.FavExtra(emptyList(), true, true) else null })
            .map { it.id })
    eq("多 flag 是 AND", listOf("local_1"),
        EntryFilter.apply(all, FilterState(flags = setOf(FilterFlag.LOCAL, FilterFlag.HAS_ADB))).map { it.id })

    println("== 排序 ==")
    // 中文按拼音首字母排：ckdc(查看) < dkwx(打开) < qkdns(清空) < wdjb(我的)
    eq("按标题（拼音序）", listOf("a", "c", "b", "local_1"),
        EntryFilter.apply(all, FilterState(sort = FilterSort.TITLE)).map { it.id })
    // bd(本地) < xt/dy(系统/电源) < xt/wl(系统/网络) < yy(应用)
    eq("按分类（拼音序）", listOf("local_1", "a", "b", "c"),
        EntryFilter.apply(all, FilterState(sort = FilterSort.CATEGORY)).map { it.id })
    eq("风险高→低", listOf("c", "b", "local_1", "a"),
        EntryFilter.apply(all, FilterState(sort = FilterSort.RISK_HIGH)).map { it.id })
    eq("最近用过", listOf("c", "a", "b", "local_1"),
        EntryFilter.apply(all, FilterState(sort = FilterSort.RECENT),
            recentRank = mapOf("c" to 0, "a" to 1, "b" to 2)).map { it.id })
    eq("排序也算生效", 1, FilterState(sort = FilterSort.TITLE).activeCount)
    ok("排序后 isEmpty=false", !FilterState(sort = FilterSort.TITLE).isEmpty)

    println("== 组合 ==")
    eq("分类+风险+排序", listOf("b"),
        EntryFilter.apply(all, FilterState(cats = setOf("系统"), risks = setOf(Risk.CAUTION),
            sort = FilterSort.TITLE)).map { it.id })
    eq("条件互斥则为空", emptyList<String>(),
        EntryFilter.apply(all, FilterState(cats = setOf("应用"), risks = setOf(Risk.SAFE))).map { it.id })

    println("== 标签池 ==")
    eq("去重并排序", listOf("dns", "社交", "网络"),
        EntryFilter.collectTags(all))
    eq("合并收藏标签", listOf("dns", "常驻", "社交", "网络"),
        EntryFilter.collectTags(all, listOf("常驻")))
    eq("忽略空白标签", listOf("a"),
        EntryFilter.collectTags(listOf(e("x", "t", tags = listOf(" a ", "", "  ")))))

    println()
    println("通过 $pass 项，失败 $fail 项")
    if (fail > 0) throw AssertionError("有 $fail 项断言失败")
}
