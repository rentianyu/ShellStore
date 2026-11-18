import com.xbta.shellstore.core.CopyPrefixItem
import com.xbta.shellstore.core.CopyPrefixes
import com.xbta.shellstore.core.RunHost
import com.xbta.shellstore.core.RunProfile

private var pass = 0
private var fail = 0

private fun eq(actual: Any?, expected: Any?, name: String) {
    if (actual == expected) {
        pass++
        println("  ok   $name")
    } else {
        fail++
        println("  FAIL $name\n       期望：$expected\n       实际：$actual")
    }
}

private fun truth(v: Boolean, name: String) = eq(v, true, name)

fun main() {
    println("[1] 默认三档")
    val d = CopyPrefixes.defaults()
    eq(d.size, 3, "默认 3 项")
    eq(d.map { it.id }, listOf("none", "adb", "su"), "id 顺序")
    eq(d.map { it.name }, listOf("无", "adb shell", "su -c"), "名称")
    truth(d.all { it.builtin }, "默认项都是内置（可改不可删）")
    truth(d[0].isPlain, "「无」= 原样")
    eq(d[0].summary, "原样复制", "「无」摘要")
    eq(d[1].summary, "adb shell \$command", "adb 摘要")

    println("[2] 渲染")
    val cmd = "pm list packages"
    eq(CopyPrefixes.render(null, cmd), cmd, "null → 原样")
    eq(CopyPrefixes.render(d[0], cmd), cmd, "空模板 → 原样")
    eq(CopyPrefixes.render(CopyPrefixItem("x", "x", "  \$command  "), cmd), cmd, "只有占位符 → 原样")
    eq(CopyPrefixes.render(d[1], cmd), "adb shell 'pm list packages'", "adb shell → 单引号包裹")
    eq(CopyPrefixes.render(d[2], cmd), "su -c 'pm list packages'", "su -c → 单引号包裹")
    eq(
        CopyPrefixes.render(CopyPrefixItem("q", "q", "su -c \$command"), "echo 'hi'"),
        "su -c 'echo '\\''hi'\\'''",
        "命令里的单引号被转义"
    )
    eq(
        CopyPrefixes.render(CopyPrefixItem("p", "p", "adb -s 123456 shell"), cmd),
        "adb -s 123456 shell pm list packages",
        "无占位符 → 前缀 + 空格 + 命令"
    )
    eq(
        CopyPrefixes.render(CopyPrefixItem("p", "p", "sh -c \"\$command\""), cmd),
        "sh -c \"pm list packages\"",
        "占位符贴双引号 → 双引号内规则"
    )
    eq(
        CopyPrefixes.render(CopyPrefixItem("m", "m", "adb shell \$command"), "line1\nline2"),
        "adb shell 'line1\nline2'",
        "多行命令整体单引号包裹（不拆到本地 shell）"
    )

    println("[3] 老配置迁移")
    eq(CopyPrefixes.fromLegacy("NONE"), "none", "NONE → none")
    eq(CopyPrefixes.fromLegacy("ADB_SHELL"), "adb", "ADB_SHELL → adb")
    eq(CopyPrefixes.fromLegacy("SU"), "su", "SU → su")
    eq(CopyPrefixes.fromLegacy("su"), "su", "小写 su → su")
    eq(CopyPrefixes.fromLegacy("乱写"), "none", "未知值 → none")

    println("[4] 清洗与回退")
    val dirty = listOf(
        CopyPrefixItem("", "空 id"),
        CopyPrefixItem("a", " 甲 ", "  adb shell \$command  "),
        CopyPrefixItem("a", "重复", "x"),
    )
    val clean = CopyPrefixes.sanitize(dirty)
    eq(clean.size, 1, "去空 id 与重复")
    eq(clean[0].name, "甲", "名称 trim")
    eq(clean[0].template, "adb shell \$command", "模板 trim")
    eq(CopyPrefixes.sanitize(null).size, 3, "空清单回退默认")
    eq(CopyPrefixes.sanitize(emptyList()).size, 3, "空 list 回退默认")
    eq(CopyPrefixes.pick(d, "adb")?.name, "adb shell", "pick 命中")
    eq(CopyPrefixes.pick(d, "不存在")?.id, "none", "pick 失效 → 回退「无」")
    val noNone = listOf(CopyPrefixItem("z", "z", "\$command"))
    eq(CopyPrefixes.pick(noNone, "不存在"), null, "没有「无」时返回 null（宁可原样，不乱加前缀）")

    println("[5] 从运行方式同步")
    val sys = RunProfile.sys()
    val shizuku = RunProfile.shizuku()
    val root = RunProfile.root()
    val termux = RunProfile.termux()
    eq(CopyPrefixes.fromRunProfile(sys), null, "系统（无前缀）不产生项")
    eq(CopyPrefixes.fromRunProfile(termux), null, "Termux 不产生项")
    eq(CopyPrefixes.fromRunProfile(shizuku)?.template, "adb shell \$command", "Shizuku → adb shell 模板")
    eq(CopyPrefixes.fromRunProfile(root)?.template, "su -c \$command", "Root → su -c 模板")

    val custom = RunProfile("mine", "受限 shell", RunHost.DIRECT, "sh -c \$command")
    val merged = CopyPrefixes.syncFromRunProfiles(CopyPrefixes.defaults(), listOf(sys, shizuku, root, termux, custom))
    eq(merged.size, 4, "只补真正新增的一条")
    eq(merged.last().id, "run_mine", "新增项 id 带 run_ 前缀")
    eq(merged.last().name, "受限 shell", "新增项沿用运行方式名称")
    eq(
        CopyPrefixes.syncFromRunProfiles(merged, listOf(custom)).size,
        4,
        "重复同步不再增加"
    )
    // 模板相同但 id 不同：不该重复加（db 里已有 su -c）
    eq(
        CopyPrefixes.syncFromRunProfiles(CopyPrefixes.defaults(), listOf(root)).size,
        3,
        "模板与已有项相同 → 跳过"
    )

    println("[6] 选中项的渲染衔接")
    val s = CopyPrefixes.pick(d, "su")
    eq(CopyPrefixes.render(s, "ls /data"), "su -c 'ls /data'", "设置里选 su 后复制结果")

    println()
    println(if (fail == 0) "PASS $pass 项" else "FAIL：$pass 通过 / $fail 失败")
    if (fail > 0) kotlin.system.exitProcess(1)
}
