import com.xbta.shellstore.core.Category
import com.xbta.shellstore.core.CategoryTree
import com.xbta.shellstore.core.SmdParser
import com.xbta.shellstore.core.SmdPath

/**
 * 目录（多级分类）规则的桌面单测：拆分、拼回、补父节点、折叠、SMD 解析。
 * 不依赖 android，可用 kotlinc 直接跑。
 */
private var pass = 0
private var fail = 0

private fun eq(what: String, want: Any?, got: Any?) {
    if (want == got) pass++
    else { fail++; println("  ✗ $what\n      期望: $want\n      实际: $got") }
}

private fun ok(what: String, cond: Boolean) {
    if (cond) pass++ else { fail++; println("  ✗ $what") }
}

fun main() {
    println("== 路径拆分 ==")
    eq("单层", listOf("系统"), SmdPath.split("系统"))
    eq("两级", listOf("系统", "网络"), SmdPath.split("系统 / 网络"))
    eq("无空格", listOf("系统", "网络"), SmdPath.split("系统/网络"))
    eq("三级带空格", listOf("系统", "网络", "DNS"), SmdPath.split("系统 / 网络 / DNS"))
    eq("全角斜杠", listOf("系统", "网络"), SmdPath.split("系统／网络"))
    eq("尖括号", listOf("系统", "网络"), SmdPath.split("系统>网络"))
    eq("全角尖括号", listOf("系统", "网络"), SmdPath.split("系统＞网络"))
    eq("箭头", listOf("系统", "网络"), SmdPath.split("系统→网络"))
    eq("混合分隔符", listOf("a", "b", "c"), SmdPath.split("a/b > c"))
    eq("空段被丢掉", listOf("系统", "DNS"), SmdPath.split("系统 // DNS"))
    eq("首尾分隔符", listOf("系统"), SmdPath.split("/ 系统 /"))
    eq("全是空白", emptyList<String>(), SmdPath.split("   "))
    eq("空串", emptyList<String>(), SmdPath.split(""))
    eq("超层被截断", 6, SmdPath.split("a/b/c/d/e/f/g/h").size)
    eq("段内空格保留", listOf("我的 工具"), SmdPath.split("我的 工具"))

    println("== 拼回与展示 ==")
    eq("join", "系统/网络/DNS", SmdPath.join(listOf("系统", "网络", "DNS")))
    eq("display", "系统 / 网络 / DNS", SmdPath.display(listOf("系统", "网络", "DNS")))
    eq("leaf", "DNS", SmdPath.leaf("系统/网络/DNS"))
    eq("leaf 单层", "系统", SmdPath.leaf("系统"))
    eq("shortLabel 两层原样", "系统/网络", SmdPath.shortLabel("系统/网络"))
    eq("shortLabel 深层只留两级", "网络/DNS", SmdPath.shortLabel("系统/网络/DNS"))
    eq("selfAndAncestors", listOf("系统", "系统/网络", "系统/网络/DNS"),
        SmdPath.selfAndAncestors("系统/网络/DNS"))

    println("== 子树判定 ==")
    ok("自己", SmdPath.isUnder("系统/网络", "系统/网络"))
    ok("子目录", SmdPath.isUnder("系统/网络/DNS", "系统/网络"))
    ok("父目录不算在子里", !SmdPath.isUnder("系统", "系统/网络"))
    ok("同前缀但不是子目录", !SmdPath.isUnder("系统工具", "系统"))
    ok("空 ancestor 表示全部", SmdPath.isUnder("随便", ""))

    println("== Category 派生属性 ==")
    val c3 = Category(id = "系统/网络/DNS", name = "DNS", path = listOf("系统", "网络", "DNS"))
    eq("depth", 2, c3.depth)
    eq("parentId", "系统/网络", c3.parentId)
    eq("title", "系统 / 网络 / DNS", c3.title)
    val c1 = Category(id = "系统", name = "系统")
    eq("单层 depth", 0, c1.depth)
    eq("单层 parentId", "", c1.parentId)
    eq("没传 path 时回退成 name", listOf("系统"), c1.segments)
    ok("isUnder 自己", c3.isUnder("系统"))

    println("== 目录树：补父节点与排序 ==")
    val declared = listOf(
        Category(id = "系统/网络/DNS", name = "DNS", path = listOf("系统", "网络", "DNS"), order = 1),
        Category(id = "系统/存储", name = "存储", path = listOf("系统", "存储"), order = 2),
        Category(id = "工具", name = "工具", order = 0)
    )
    val tree = CategoryTree.build(declared)
    eq("节点数（3 声明 + 2 补）", 5, tree.size)
    eq("顺序", listOf("工具", "系统", "系统/网络", "系统/网络/DNS", "系统/存储"),
        tree.map { it.id })
    val sys = tree.first { it.id == "系统" }
    ok("系统 是补出来的", sys.synthesized)
    ok("网络 是补出来的", tree.first { it.id == "系统/网络" }.synthesized)
    ok("DNS 不是补的", !tree.first { it.id == "系统/网络/DNS" }.synthesized)
    eq("补出来的父保留正确名字", "网络", tree.first { it.id == "系统/网络" }.name)
    ok("父排在子前面", tree.indexOfFirst { it.id == "系统" } < tree.indexOfFirst { it.id == "系统/网络" })

    println("== 目录树：子节点与计数 ==")
    eq("顶层子节点", setOf("系统", "工具"), CategoryTree.children(tree, "").map { it.id }.toSet())
    eq("系统 的子节点", setOf("系统/网络", "系统/存储"),
        CategoryTree.children(tree, "系统").map { it.id }.toSet())
    // 系统 + 系统/网络 + 系统/网络/DNS + 系统/存储
    eq("子树 id", 4, CategoryTree.subtreeIds(tree, "系统").size)

    println("== 折叠 ==")
    eq("不折叠全显示", 5, CategoryTree.visible(tree, emptySet()).size)
    eq("折叠系统后隐藏 3 个", 2, CategoryTree.visible(tree, setOf("系统")).size)
    eq("折叠网络只隐藏 DNS", 4, CategoryTree.visible(tree, setOf("系统/网络")).size)
    ok("折叠父不影响父自己显示",
        CategoryTree.visible(tree, setOf("系统")).any { it.id == "系统" })

    println("== SMD 解析：多级目录 ==")
    val smd = """
        # 测试源
        @version 1

        ## 系统 / 网络 / DNS
        @icon 🌐
        ### 清空 DNS 缓存
        重置本地解析缓存
        ```sh
        ndc resolver flushdefaultif
        ```

        ## 系统 / 存储
        ### 查看分区
        ```sh
        df -h
        ```

        ## 工具
        ### 查看电池
        ```sh
        dumpsys battery
        ```
    """.trimIndent()
    val r = SmdParser.parse(smd, "src1", "测试源")
    eq("无解析警告", 0, r.warnings.size)
    eq("分类数", 3, r.source.categories.size)
    eq("分类 id 是完整路径", setOf("系统/网络/DNS", "系统/存储", "工具"),
        r.source.categories.map { it.id }.toSet())
    val dnsCat = r.source.categories.first { it.id == "系统/网络/DNS" }
    eq("分类 path", listOf("系统", "网络", "DNS"), dnsCat.path)
    eq("分类 name 是叶子", "DNS", dnsCat.name)
    eq("分类 icon 挂在路径分类上", "🌐", dnsCat.icon)
    eq("条目 categoryId 是完整路径", "系统/网络/DNS", r.source.entries.first { it.title == "清空 DNS 缓存" }.categoryId)
    eq("条目数", 3, r.source.entries.size)

    println("== SMD 解析：兼容单层旧写法 ==")
    val old = """
        # 旧源
        ## 软件
        ### 打开微信
        ```sh
        am start -n com.tencent.mm/.ui.LauncherUI
        ```
    """.trimIndent()
    val r2 = SmdParser.parse(old, "src2")
    val oc = r2.source.categories.first()
    eq("单层 id", "软件", oc.id)
    eq("单层 name", "软件", oc.name)
    eq("单层 path", listOf("软件"), oc.path)
    eq("单层 depth", 0, oc.depth)
    eq("条目 categoryId", "软件", r2.source.entries.first().categoryId)
    eq("单层解析无警告", 0, r2.warnings.size)

    println("== SMD 解析：异常输入 ==")
    val bad = """
        ## 
        ### 没分类的条目
        ```sh
        echo 1
        ```
    """.trimIndent()
    val r3 = SmdParser.parse(bad, "src3")
    ok("空分类名报警告", r3.warnings.any { it.message.contains("分类名为空") })

    println("== 往返：导出再解析 ==")
    val cats = listOf(
        Category(id = "系统/网络", name = "网络", path = listOf("系统", "网络"), order = 0)
    )
    val rebuilt = com.xbta.shellstore.core.SmdWriter.source("t", "", listOf("系统/网络" to listOf("### 测试\n```sh\necho 1\n```")))
    ok("导出用带空格的路径写法", rebuilt.contains("## 系统 / 网络"))
    val back = SmdParser.parse(rebuilt, "src4")
    eq("往返后分类 id 一致", "系统/网络", back.source.categories.first().id)

    println()
    println("通过 $pass 项，失败 $fail 项")
    if (fail > 0) throw AssertionError("有 $fail 项断言失败")
}
