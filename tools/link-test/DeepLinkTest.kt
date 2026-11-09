import com.xbta.shellstore.core.DeepLink

/**
 * DeepLink 解析的桌面单测（不依赖 android）。
 * 断言失败直接抛 AssertionError，进程退出码非 0。
 */
private var pass = 0
private var fail = 0

private fun eq(what: String, want: Any?, got: Any?) {
    if (want == got) {
        pass++
    } else {
        fail++
        println("  ✗ $what\n      期望: $want\n      实际: $got")
    }
}

private fun ok(what: String, cond: Boolean) {
    if (cond) pass++ else { fail++; println("  ✗ $what") }
}

fun main() {
    println("== 基本解析 ==")
    DeepLink.parse("shellstore://shop")!!.let {
        eq("shop 名称", "shop", it.name)
        ok("shop 无参数", it.args.isEmpty())
        ok("shop known", it.known)
    }
    eq("空串不解析", null, DeepLink.parse(""))
    eq("null 不解析", null, DeepLink.parse(null))
    eq("别的 scheme 不管", null, DeepLink.parse("https://example.com/x"))
    eq("没有冒号不管", null, DeepLink.parse("shellstore"))
    eq("大小写不敏感", "run", DeepLink.parse("ShellStore://RUN")?.name)

    println("== 组/动作两段式 ==")
    eq("source/add → source-add", "source-add", DeepLink.parse("shellstore://source/add")?.name)
    eq("source/refresh 别名", "source-refresh", DeepLink.parse("shellstore://source/refresh")?.name)
    ok("source-add 已知", DeepLink.parse("shellstore://source/add")!!.known)

    println("== 别名 ==")
    eq("home → shop", "shop", DeepLink.parse("shellstore://home")?.name)
    eq("favorites → favorite", "favorite", DeepLink.parse("shellstore://favorites")?.name)
    eq("mine → me", "me", DeepLink.parse("shellstore://mine")?.name)
    eq("detail → entry", "entry", DeepLink.parse("shellstore://detail")?.name)
    eq("apk → export-apk", "export-apk", DeepLink.parse("shellstore://apk")?.name)
    eq("下划线归一", "export-apk", DeepLink.parse("shellstore://export_apk")?.name)
    eq("空路径 → shop", "shop", DeepLink.parse("shellstore://")?.name)
    eq("只有 scheme → shop", "shop", DeepLink.parse("shellstore:/")?.name)
    eq("未知动作保留原样", "nosuch", DeepLink.parse("shellstore://nosuch")?.name)
    ok("未知动作 known=false", !DeepLink.parse("shellstore://nosuch")!!.known)

    println("== 参数 ==")
    DeepLink.parse("shellstore://run?id=abc123")!!.let {
        eq("id", "abc123", it.id())
        ok("run 需要 id", it.danger)
    }
    DeepLink.parse("shellstore://entry?entryId=xyz")!!.let {
        eq("entryId 别名", "xyz", it.id())
    }
    DeepLink.parse("shellstore://entry?e=zz")!!.let { eq("e 别名", "zz", it.id()) }
    DeepLink.parse("shellstore://search?q=%E8%93%9D%E7%89%99")!!.let {
        eq("q 解码成中文", "蓝牙", it.s("q"))
    }
    DeepLink.parse("shellstore://search?query=abc")!!.let { eq("query 别名", "abc", it.s("q")) }
    DeepLink.parse("shellstore://search?q=a+b")!!.let {
        eq("+ 不当空格（decodeStrict）", "a+b", it.s("q"))
    }
    DeepLink.parse("shellstore://source-add?url=https%3A%2F%2Ff.xbta.cc%2Fa.md&name=%E6%88%91%E7%9A%84")!!.let {
        eq("url 解码", "https://f.xbta.cc/a.md", it.s("url"))
        eq("name 解码", "我的", it.s("name"))
    }
    DeepLink.parse("shellstore://set?key=theme&value=dark")!!.let {
        eq("key 别名 k", "theme", it.s("key"))
        eq("value 别名 v", "dark", it.s("value"))
    }
    DeepLink.parse("shellstore://set?k=uiLevel&v=2")!!.let {
        eq("k 别名", "uiLevel", it.s("key"))
        eq("v 别名", "2", it.s("value"))
    }

    println("== 布尔 ==")
    ok("on=1 真", DeepLink.parse("shellstore://pin?id=x&on=1")!!.flag("on"))
    ok("on=0 假", !DeepLink.parse("shellstore://pin?id=x&on=0")!!.flag("on"))
    ok("on=true 真", DeepLink.parse("shellstore://pin?id=x&on=true")!!.flag("on"))
    ok("on=off 假", !DeepLink.parse("shellstore://pin?id=x&on=off")!!.flag("on"))
    ok("缺省取默认 false", !DeepLink.parse("shellstore://pin?id=x")!!.flag("on"))
    ok("缺省取默认 true", DeepLink.parse("shellstore://pin?id=x")!!.flag("on", true))
    ok("confirm=1 确认", DeepLink.parse("shellstore://unfav?id=x&confirm=1")!!.confirmed())
    ok("没 confirm 不算确认", !DeepLink.parse("shellstore://unfav?id=x")!!.confirmed())

    println("== 条目参数（run 的额外键） ==")
    val run = DeepLink.parse("shellstore://run?id=jd&pkg=com.jd&action=VIEW&confirm=1")!!
    val extra = run.entryParams()
    eq("额外参数个数", 2, extra.size)
    eq("pkg 保留", "com.jd", extra["pkg"])
    eq("action 保留", "VIEW", extra["action"])
    ok("id 不进参数", !extra.containsKey("id"))
    ok("confirm 不进参数", !extra.containsKey("confirm"))

    println("== 边界 ==")
    DeepLink.parse("shellstore://entry?id=a%26b")!!.let { eq("转义的 & 不切分", "a&b", it.id()) }
    DeepLink.parse("shellstore://shop?")!!.let { ok("空查询串", it.args.isEmpty()) }
    DeepLink.parse("shellstore://shop?&&")!!.let { ok("全是分隔符", it.args.isEmpty()) }
    DeepLink.parse("shellstore://shop#frag")!!.let {
        eq("锚点被丢掉", "shop", it.name)
        ok("锚点后不解析成参数", it.args.isEmpty())
    }
    DeepLink.parse("shellstore://run?id=x&q=1#frag")!!.let {
        eq("有锚点时参数仍解析", "x", it.id())
    }
    ok("不认识的 scheme 前缀不误判", DeepLink.parse("xshellstore://shop") == null)

    println("== build 往返 ==")
    val built = DeepLink.build("source-add", "url" to "https://f.xbta.cc/a b.md", "name" to "我的源")
    val back = DeepLink.parse(built)!!
    eq("build 后 name", "source-add", back.name)
    eq("build 后 url 往返", "https://f.xbta.cc/a b.md", back.s("url"))
    eq("build 后中文往返", "我的源", back.s("name"))
    val built2 = DeepLink.build("shop")
    eq("无参数 build", "shellstore://shop", built2)
    eq("无参数往返", "shop", DeepLink.parse(built2)?.name)

    println("== 协议表自检 ==")
    ok("动作不重复", DeepLink.ALL.map { it.name }.let { it.size == it.toSet().size })
    ok("每个 name 都能被解析回来", DeepLink.ALL.all { DeepLink.parse("shellstore://" + it.name)?.name == it.name })
    ok("危险动作都有标注", DeepLink.ALL.count { it.danger } >= 6)
    ok("设置项非空", DeepLink.SETTING_KEYS.size >= 20)
    ok("每组都有动作", DeepLink.ALL.groupBy { it.group }.keys
            .containsAll(listOf("导航", "动作", "数据", "设置")))

    println()
    println("通过 $pass 项，失败 $fail 项")
    if (fail > 0) throw AssertionError("有 $fail 项断言失败")
}
