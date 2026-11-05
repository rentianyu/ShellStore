import com.xbta.shellstore.core.*
import java.io.File

var pass = 0
var fail = 0
val failures = mutableListOf<String>()

fun check(name: String, cond: Boolean, detail: String = "") {
    if (cond) {
        pass++
    } else {
        fail++
        failures.add("$name ${if (detail.isNotEmpty()) "→ $detail" else ""}")
        println("FAIL  $name  $detail")
    }
}

fun main(args: Array<String>) {
    val dir = if (args.isNotEmpty()) args[0] else "tests/samples"
    val f = File(dir)
    if (!f.isDirectory) {
        println("样本目录不存在: $dir")
        return
    }

    // ===== 01 基础 =====
    run {
        val r = SmdParser.parse(File(f, "01-basic.md").readText(), "t1", "测试源")
        val s = r.source
        check("01 条目数=2", s.entries.size == 2, "实际 ${s.entries.size}")
        check("01 作者", s.meta.author == "小贝塔", s.meta.author)
        check("01 版本", s.meta.version == "2026.11.1", s.meta.version)
        check("01 分类存在", s.categories.any { it.name == "应用管理" })
        val e0 = s.entries[0]
        check("01 e0 标题", e0.title == "列出第三方应用", e0.title)
        check("01 e0 类型", e0.type == EntryType.SHELL, e0.type.name)
        check("01 e0 风险", e0.risk == Risk.SAFE, e0.risk.name)
        check("01 e0 权限", e0.need == Need.SHELL, e0.need.name)
        check("01 e0 body", e0.body == "pm list packages -3", "[${e0.body}]")
        check("01 e0 参数数=1", e0.params.size == 1, "${e0.params.size}")
        check("01 e0 参数键", e0.params[0].key == "filter", e0.params[0].key)
        check("01 e0 参数名", e0.params[0].label == "范围", e0.params[0].label)
        check("01 e0 参数默认", e0.params[0].default == "-3", e0.params[0].default)
        check("01 e0 参数注释", e0.params[0].note.contains("只列第三方"), e0.params[0].note)
        check("01 e0 示例", e0.example == "pm list packages -3", e0.example)
        check("01 e0 标签", e0.tags.contains("pm"), "${e0.tags}")
        val e1 = s.entries[1]
        check("01 e1 风险", e1.risk == Risk.CAUTION, e1.risk.name)
        check("01 e1 占位符", e1.body.contains("{pkg}"), e1.body)
        check("01 无告警", r.warnings.isEmpty(), "${r.warnings}")
    }

    // ===== 02 ● 清单 =====
    run {
        val r = SmdParser.parse(File(f, "02-list.md").readText(), "t2")
        val s = r.source
        check("02 条目数=2", s.entries.size == 2, "实际 ${s.entries.size}")
        val a = s.entries[0]
        check("02 a 标题", a.title == "高德地图_点亮城市", a.title)
        check("02 a 类型=activity", a.type == EntryType.ACTIVITY, a.type.name)
        check("02 a body 含 JSON", a.body.contains("\"cardName\":\"city\""), a.body)
        check("02 a body 单行", !a.body.contains("\n"), "多行了")
        val b = s.entries[1]
        check("02 b 标题", b.title == "获取当前无障碍服务", b.title)
        check("02 b 类型=script", b.type == EntryType.SCRIPT, b.type.name)
        check("02 b 两行", b.body.lines().size == 2, "${b.body.lines().size}")
        check("02 b 保留命令替换", b.body.contains("\$(settings get"), b.body)
        check("02 b 风险=caution", b.risk == Risk.CAUTION, b.risk.name)
    }

    // ===== 03 adb 前缀剥离 =====
    run {
        val r = SmdParser.parse(File(f, "03-adb.md").readText(), "t3")
        val s = r.source
        check("03 条目数=5", s.entries.size == 5, "实际 ${s.entries.size}")
        check("03 adb shell 剥离", s.entries[0].body == "pm list packages -3", "[${s.entries[0].body}]")
        check("03 记原文", s.entries[0].origin.startsWith("adb shell"), s.entries[0].origin)
        check("03 -s 剥离", s.entries[1].body == "dumpsys battery", "[${s.entries[1].body}]")
        check("03 提示符剥离", s.entries[2].body == "pm list packages -s", "[${s.entries[2].body}]")
        check("03 adb install 保留", s.entries[3].body == "adb install app.apk", "[${s.entries[3].body}]")
        check("03 su 剥离", s.entries[4].body == "pm list packages", "[${s.entries[4].body}]")
        check("03 su 判 root", s.entries[4].need == Need.ROOT, s.entries[4].need.name)
    }

    // ===== 04 引号地狱 =====
    run {
        val r = SmdParser.parse(File(f, "04-quotes.md").readText(), "t4")
        val s = r.source
        check("04 条目数=2", s.entries.size == 2, "实际 ${s.entries.size}")
        val e = s.entries[0]
        check("04 风险=danger", e.risk == Risk.DANGER, e.risk.name)
        check("04 保留三层引号", e.body.contains("'\"\$qqq\"'"), "未找到三层引号")
        check("04 保留反斜杠续行", e.body.contains(" \\\n"), "续行丢失")
        check("04 保留 JSON body", e.body.contains("\"msgtype\": \"text\""), "JSON 段丢失")
        check("04 行数=13", e.body.lines().size == 13, "${e.body.lines().size}")
        val e2 = s.entries[1]
        check("04 特殊字符", e2.body.contains("hash:#"), e2.body)
        check("04 花括号管道", e2.body.contains("[{}|]"), e2.body)
    }

    // ===== 05 intent =====
    run {
        val r = SmdParser.parse(File(f, "05-intent.md").readText(), "t5")
        val s = r.source
        check("05 跳过空条目后=3", s.entries.size == 3, "实际 ${s.entries.size}")
        val a = s.entries[0]
        check("05 activity 类型", a.type == EntryType.ACTIVITY, a.type.name)
        check("05 action", a.intent?.action == "android.settings.WIFI_SETTINGS", "${a.intent?.action}")
        check("05 flag", a.intent?.flags?.contains("NEW_TASK") == true, "${a.intent?.flags}")
        check("05 无 body", a.body.isBlank(), a.body)
        val b = s.entries[1]
        check("05 intent 类型", b.type == EntryType.INTENT, b.type.name)
        check("05 extras 数=3", b.intent?.extras?.size == 3, "${b.intent?.extras?.size}")
        check("05 extra string", b.intent?.extras?.get(0)?.type == ExtraType.STRING)
        check("05 extra bool", b.intent?.extras?.get(1)?.type == ExtraType.BOOL)
        check("05 extra int", b.intent?.extras?.get(2)?.type == ExtraType.INT)
        check("05 mime", b.intent?.mime == "text/plain", "${b.intent?.mime}")
        val c = s.entries[2]
        check("05 urlscheme", c.type == EntryType.URLSCHEME, c.type.name)
        check("05 package", c.intent?.packageName == "com.tencent.mm", "${c.intent?.packageName}")
        check("05 scheme body", c.body == "weixin://scanqrcode", c.body)
        check("05 有告警(空条目)", r.warnings.any { it.message.contains("广播") || it.message.contains("缺少") },
            "${r.warnings}")
    }

    // ===== ShellSanitizer 单元 =====
    run {
        check("ss adb", ShellSanitizer.strip("adb shell pm list").cleaned == "pm list")
        check("ss 多层", ShellSanitizer.strip("adb -s X shell am start").cleaned == "am start")
        check("ss 引号", ShellSanitizer.strip("adb shell \"pm list\"").cleaned == "pm list")
        check("ss 不变", !ShellSanitizer.strip("echo hi").changed)
        check("ss 管道保留", ShellSanitizer.strip("pm list | grep a").cleaned == "pm list | grep a")
    }

    // ===== 推断 =====
    run {
        check("infer 危险", SmdParser.inferRisk("pm uninstall -k --user 0 com.x") == Risk.DANGER)
        check("infer 谨慎", SmdParser.inferRisk("settings put global x 1") == Risk.CAUTION)
        check("infer 安全", SmdParser.inferRisk("pm list packages") == Risk.SAFE)
        check("infer need shell", SmdParser.inferNeed("dumpsys battery") == Need.SHELL)
        check("infer need none", SmdParser.inferNeed("echo hi") == Need.NONE)
        check("id 稳定", SmdParser.makeId("应用管理", "列出应用") == SmdParser.makeId("应用管理", "列出应用"))
        check("id 不同", SmdParser.makeId("应用管理", "A") != SmdParser.makeId("应用管理", "B"))
    }

    // ===== 边界 =====
    run {
        val r1 = SmdParser.parse("", "empty")
        check("边界 空文件", r1.source.entries.isEmpty())
        val r2 = SmdParser.parse("```sh\npm list\n", "nofence")
        check("边界 未闭合告警", r2.warnings.any { it.message.contains("未闭合") }, "${r2.warnings}")
        val r3 = SmdParser.parse("## 分类\n\n### 条目\n@action android.intent.action.VIEW\n", "intent-only")
        check("边界 intent 无 body", r3.source.entries.size == 1, "${r3.source.entries.size}")
        check("边界 intent 类型", r3.source.entries[0].type == EntryType.ACTIVITY,
            r3.source.entries[0].type.name)
        val r4 = SmdParser.parse("### 标题\n```sh\nline1\n```\n```sh\nline2\n```\n", "multi")
        check("边界 双代码块变体", r4.source.entries[0].variants.size == 1)
        check("边界 变体内容", r4.source.entries[0].variants[0].body == "line2")
    }

    // ===== CommandRenderer =====
    run {
        val e = Entry(
            id = "x", type = EntryType.SHELL, title = "t",
            body = "am force-stop {pkg}",
            params = listOf(Param("pkg", "包名", "com.tencent.mm"),
                Param("mode", "模式", "-3"))
        )
        check("cr 默认值替换",
            CommandRenderer.fill(e.body, e, emptyMap()) == "am force-stop com.tencent.mm",
            CommandRenderer.fill(e.body, e, emptyMap()))
        check("cr 用户值替换",
            CommandRenderer.fill(e.body, e, mapOf("pkg" to "com.a.b")) == "am force-stop com.a.b",
            CommandRenderer.fill(e.body, e, mapOf("pkg" to "com.a.b")))
        check("cr 空值回落默认",
            CommandRenderer.fill(e.body, e, mapOf("pkg" to "")) == "am force-stop com.tencent.mm")

        val act = Entry(
            id = "y", type = EntryType.ACTIVITY, title = "wifi",
            intent = IntentSpec(action = "android.settings.WIFI_SETTINGS", flags = listOf("NEW_TASK"))
        )
        check("cr activity 生成",
            CommandRenderer.toAdb(act, emptyMap()) == "am start -a android.settings.WIFI_SETTINGS -f 0x10000000",
            CommandRenderer.toAdb(act, emptyMap()))

        val br = Entry(
            id = "z", type = EntryType.BROADCAST, title = "b",
            intent = IntentSpec(
                action = "android.intent.action.VIEW",
                data = "file:///sdcard/a.jpg",
                extras = listOf(Extra("k", ExtraType.STRING, "v 1"))
            )
        )
        check("cr broadcast 动词", CommandRenderer.toAdb(br, emptyMap()).startsWith("am broadcast"))
        check("cr data 引号", CommandRenderer.toAdb(br, emptyMap()).contains("-d file:///sdcard/a.jpg"))
        check("cr extra 引号", CommandRenderer.toAdb(br, emptyMap()).contains("--es k 'v 1'"),
            CommandRenderer.toAdb(br, emptyMap()))

        val comp = Entry(
            id = "c", type = EntryType.ACTIVITY, title = "c",
            intent = IntentSpec(component = "com.x/.Main", packageName = "com.x")
        )
        check("cr component 补全",
            CommandRenderer.toAdb(comp, emptyMap()).contains("-n com.x/com.x.Main"),
            CommandRenderer.toAdb(comp, emptyMap()))

        val scheme = Entry(id = "s", type = EntryType.URLSCHEME, title = "s", body = "weixin://scanqrcode")
        check("cr urlscheme",
            CommandRenderer.toAdb(scheme, emptyMap()) == "am start -a android.intent.action.VIEW -d weixin://scanqrcode",
            CommandRenderer.toAdb(scheme, emptyMap()))

        val adbEntry = Entry(
            id = "a", type = EntryType.ACTIVITY, title = "a",
            adb = "am start -d 'x://a?b={\"k\":\"v\"}'",
            intent = IntentSpec(action = "wrong")
        )
        check("cr @adb 优先", CommandRenderer.toAdb(adbEntry, emptyMap()).contains("x://a?b="),
            CommandRenderer.toAdb(adbEntry, emptyMap()))

        check("cr 引号 无空格", CommandRenderer.q("abc") == "abc")
        check("cr 引号 有空格", CommandRenderer.q("a b") == "'a b'")
        check("cr 引号 含单引号", CommandRenderer.q("a'b c") == "'a'\\''b c'", CommandRenderer.q("a'b c"))
        check("cr flag 数值", CommandRenderer.flagValue("0x40000000") == "0x40000000")
    }

    println()
    println("===== 结果: PASS $pass / FAIL $fail =====")
    if (fail > 0) {
        println("失败项:")
        failures.forEach { println("  - $it") }
    }
}
