import com.xbta.shellstore.core.GlobalVars

/** 全局变量替换规则 */
private var pass = 0
private var fail = 0

private fun eq(what: String, want: Any?, got: Any?) {
    if (want == got) pass++ else {
        fail++
        println("  ✗ $what\n      期望: $want\n      实际: $got")
    }
}

private val V = mapOf("serial" to "abc123", "dir" to "/sdcard/tmp", "token" to "t-9")

fun main() {
    println("== 基本替换 ==")
    eq("单个变量", "adb -s abc123 shell", GlobalVars.apply("adb -s {{serial}} shell", V))
    eq("多个变量", "cp /sdcard/tmp/a /sdcard/tmp/b",
        GlobalVars.apply("cp {{dir}}/a {{dir}}/b", V))
    eq("值里的斜杠与连字符", "echo t-9", GlobalVars.apply("echo {{token}}", V))
    eq("星号包裹", "echo abc123", GlobalVars.apply("echo {{ serial }}", V))
    eq("没有变量原样返回", "echo hi", GlobalVars.apply("echo hi", V))
    eq("空变量表原样返回", "echo {{x}}", GlobalVars.apply("echo {{x}}", emptyMap()))

    println("== 容错 ==")
    eq("未定义原样保留", "echo {{nope}}", GlobalVars.apply("echo {{nope}}", V))
    eq("半个花括号不动", "echo {serial}", GlobalVars.apply("echo {serial}", V))
    eq("参数占位符不受影响", "echo {key}", GlobalVars.apply("echo {key}", V))
    eq("非变量名的花括号不动", "echo {{a b}}", GlobalVars.apply("echo {{a b}}", V))
    eq("空名字不动", "echo {{}}", GlobalVars.apply("echo {{}}", V))
    eq("含点的变量名", "echo 1", GlobalVars.apply("echo {{a.b}}", mapOf("a.b" to "1")))

    println("== 引用检查 ==")
    eq("用到的变量去重", listOf("serial", "dir"),
        GlobalVars.used("adb -s {{serial}} pull {{dir}} {{serial}}"))
    eq("缺失的变量", listOf("nope"), GlobalVars.missing("echo {{serial}} {{nope}}", V))
    eq("全部都有则缺失为空", emptyList<String>(), GlobalVars.missing("echo {{serial}}", V))

    println("== 名称校验 ==")
    eq("合法：字母", true, GlobalVars.isValidName("serial"))
    eq("合法：下划线与数字", true, GlobalVars.isValidName("dev_1"))
    eq("合法：点与连字符", true, GlobalVars.isValidName("a.b-c"))
    eq("非法：空格", false, GlobalVars.isValidName("a b"))
    eq("非法：中文", false, GlobalVars.isValidName("设备"))
    eq("非法：空", false, GlobalVars.isValidName("  "))

    println()
    if (fail == 0) println("通过 $pass 项，失败 0 项") else println("通过 $pass 项，失败 $fail 项")
    if (fail > 0) kotlin.system.exitProcess(1)
}
