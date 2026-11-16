import com.xbta.shellstore.core.RunHost
import com.xbta.shellstore.core.RunProfile
import com.xbta.shellstore.core.RunTemplate

/** 运行模板渲染的桌面单测（纯逻辑，不依赖 android） */
private var pass = 0
private var fail = 0

private fun eq(what: String, want: Any?, got: Any?) {
    if (want == got) pass++ else {
        fail++
        println("  ✗ $what\n      期望: $want\n      实际: $got")
    }
}

private fun ok(what: String, cond: Boolean) {
    if (cond) pass++ else { fail++; println("  ✗ $what") }
}

fun main() {
    println("== 单引号引用 ==")
    eq("普通命令", "'ls -l'", RunTemplate.quoteSingle("ls -l"))
    eq("含单引号", "'it'\\''s'", RunTemplate.quoteSingle("it's"))
    eq("空命令", "''", RunTemplate.quoteSingle(""))
    eq("含换行不被破坏", "'a\nb'", RunTemplate.quoteSingle("a\nb"))

    println("== 双引号内转义 ==")
    eq("普通命令", "ls -l", RunTemplate.quoteDoubleInner("ls -l"))
    eq("含双引号", "echo \\\"hi\\\"", RunTemplate.quoteDoubleInner("echo \"hi\""))
    eq("含美元符", "echo \\\$HOME", RunTemplate.quoteDoubleInner("echo \$HOME"))
    eq("含反引号", "echo \\`id\\`", RunTemplate.quoteDoubleInner("echo `id`"))
    eq("含反斜杠", "a\\\\b", RunTemplate.quoteDoubleInner("a\\b"))

    println("== 模板渲染 ==")
    eq("只有占位符", "'ls'", RunTemplate.render("\$command", "ls"))
    eq("空模板视作只有命令", "'ls'", RunTemplate.render("", "ls"))
    eq("su -c 裸占位符", "su -c 'ls'", RunTemplate.render("su -c \$command", "ls"))
    eq("su -c 双引号占位符", "su -c \"ls\"", RunTemplate.render("su -c \"\$command\"", "ls"))
    eq(
        "python 解释器",
        "/sdcard/bin/python -c 'print(1)'",
        RunTemplate.render("/sdcard/bin/python -c \$command", "print(1)")
    )
    eq("没有占位符则原样", "echo hi", RunTemplate.render("echo hi", "ls"))
    eq(
        "多个占位符都替换",
        "echo 'a' && echo 'a'",
        RunTemplate.render("echo \$command && echo \$command", "a")
    )
    eq(
        "占位符在中间",
        "a 'x' b",
        RunTemplate.render("a \$command b", "x")
    )
    // 双引号内的命令带 $ 时必须转义，否则外层 sh 会提前展开
    eq(
        "双引号内带美元符",
        "su -c \"echo \\\$HOME\"",
        RunTemplate.render("su -c \"\$command\"", "echo \$HOME")
    )
    // 单引号场景保持原样即可，外层不会解释
    eq(
        "单引号内带美元符",
        "su -c 'echo \$HOME'",
        RunTemplate.render("su -c \$command", "echo \$HOME")
    )

    println("== 内置方案 ==")
    val d = RunProfile.defaults()
    eq("内置 4 项", 4, d.size)
    eq("顺序：系统/Shizuku/Root/Termux", listOf("sys", "shizuku", "root", "termux"), d.map { it.id })
    eq("默认启用前三项", listOf(true, true, true, false), d.map { it.enabled })
    eq("都是内置", true, d.all { it.builtin })
    eq("Root 用本机宿主 + su 模板", RunHost.DIRECT, d[2].host)
    eq("Root 模板", "su -c \$command", d[2].template)
    eq("Shizuku 宿主", RunHost.SHIZUKU, d[1].host)
    eq("Termux 宿主", RunHost.TERMUX, d[3].host)
    eq("摘要格式", "本机 › su -c \$command", d[2].summary)

    println("== 配置兜底 ==")
    eq("空列表回到默认", 4, RunProfile.sanitize(emptyList()).size)
    eq(
        "重复 id 去重",
        listOf("a", "b"),
        RunProfile.sanitize(
            listOf(
                RunProfile("a", "A"),
                RunProfile("a", "A2"),
                RunProfile("b", "B")
            )
        ).map { it.id }
    )
    eq(
        "空名用 id 顶替",
        "x",
        RunProfile.sanitize(listOf(RunProfile("x", " ")))[0].name.trim().ifBlank { "x" }
    )
    eq("空模板按占位符处理", "\$command", RunProfile("c", "C", template = " ").templateOrPlain)

    println()
    if (fail == 0) println("通过 $pass 项，失败 0 项") else println("通过 $pass 项，失败 $fail 项")
    if (fail > 0) kotlin.system.exitProcess(1)
}
