import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.core.ShareDraft

/** 分享导入的解析规则（纯逻辑） */
private var pass = 0
private var fail = 0

private fun eq(what: String, want: Any?, got: Any?) {
    if (want == got) pass++ else {
        fail++
        println("  ✗ $what\n      期望: $want\n      实际: $got")
    }
}

fun main() {
    println("== 前缀清理 ==")
    eq("adb shell 前缀", "getprop ro.product.device", ShareDraft.normalize("adb shell getprop ro.product.device"))
    eq("adb shell 单独出现", "ls", ShareDraft.normalize("adb shell ls"))
    eq("$ 前缀", "ls -l", ShareDraft.normalize("$ ls -l"))
    eq("# 前缀", "ls -l", ShareDraft.normalize("# ls -l"))
    eq("没有前缀保持原样", "echo hi", ShareDraft.normalize("echo hi"))
    eq("首尾空白与 CRLF", "a\nb", ShareDraft.normalize("  a\r\nb  "))

    println("== 类型猜测 ==")
    eq("单行 scheme", EntryType.URLSCHEME, ShareDraft.guessType("openapp.jdmobile://virtual?params=x"))
    eq("https 也算 scheme", EntryType.URLSCHEME, ShareDraft.guessType("https://example.com/a"))
    eq("多行不猜 scheme", EntryType.SHELL, ShareDraft.guessType("echo hi\nopenapp.jdmobile://x"))
    eq("am 命令当 shell", EntryType.SHELL, ShareDraft.guessType("am start -n a.b/c"))
    eq("普通命令", EntryType.SHELL, ShareDraft.guessType("getprop ro.product.device"))

    println("== 生成草稿 ==")
    val d = ShareDraft.build("adb shell getprop ro.product.device")
    eq("标题取首行", "getprop ro.product.device", d?.title)
    eq("正文已剥前缀", "getprop ro.product.device", d?.body)
    eq("类型 shell", EntryType.SHELL, d?.type)
    eq("来源标记", "分享导入", d?.origin)
    eq("id 留空（新建）", "", d?.id)

    val long = ShareDraft.build("x".repeat(80))
    eq("标题截 30 字", 30, long?.title?.length)

    val multi = ShareDraft.build("""
        # 清理缓存脚本
        rm -rf /data/local/tmp/*
        echo done
    """.trimIndent())
    eq("多行保留全文", 3, multi?.body?.lineSequence()?.count() ?: 0)
    eq("多行标题为首行", "清理缓存脚本", multi?.title)

    eq("空文本返回 null", null, ShareDraft.build("   "))
    eq("只有前缀也返回 null", null, ShareDraft.build("adb shell "))

    println()
    if (fail == 0) println("通过 $pass 项，失败 0 项") else println("通过 $pass 项，失败 $fail 项")
    if (fail > 0) kotlin.system.exitProcess(1)
}
