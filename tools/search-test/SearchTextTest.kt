import com.xbta.shellstore.core.*

var pass = 0
var fail = 0
fun ck(name: String, cond: Boolean, got: Any? = "") {
    if (cond) { pass++ } else { fail++; println("  ✗ $name  实际=$got") }
}

fun e(id: String, title: String, body: String = "", desc: String = "", tags: List<String> = emptyList(),
      cat: String = "系统") = Entry(id = id, type = EntryType.SHELL, title = title, body = body, desc = desc,
      tags = tags, categoryId = cat)

/**
 * 搜索核心的桌面单测（不参与 gradle 构建，改完搜索逻辑手动跑一次）。
 * 运行：见同目录 run.sh
 */
fun main() {
    // 分词
    ck("空格分词", SearchText.tokenize("蓝牙 关闭") == listOf("蓝牙", "关闭"), SearchText.tokenize("蓝牙 关闭"))
    ck("全角空格", SearchText.tokenize("蓝牙　关闭") == listOf("蓝牙", "关闭"), SearchText.tokenize("蓝牙　关闭"))
    ck("大小写归一", SearchText.tokenize("WiFi") == listOf("wifi"))
    ck("去重", SearchText.tokenize("a a b") == listOf("a", "b"))
    ck("空串", SearchText.tokenize("   ").isEmpty())

    // 拼音首字母
    ck("设置→sz", SearchText.initials("设置") == "sz", SearchText.initials("设置"))
    ck("查看电池→ckdc", SearchText.initials("查看电池") == "ckdc", SearchText.initials("查看电池"))
    ck("蓝牙→ly", SearchText.initials("蓝牙") == "ly", SearchText.initials("蓝牙"))
    ck("英文保留", SearchText.initials("am start") == "am start", SearchText.initials("am start"))
    ck("中英混排", SearchText.initials("关闭WiFi") == "gbwifi", SearchText.initials("关闭WiFi"))
    ck("生僻字不崩", SearchText.initials("龘靐齉").isNotEmpty())

    val e1 = e("1", "蓝牙开关", "svc bluetooth enable")
    val e2 = e("2", "查看电池电量", "dumpsys battery")
    val e3 = e("3", "设置屏幕亮度", "settings put system screen_brightness 100", desc = "手动调节")

    // 打分
    ck("标题命中 3 分", SearchText.score(e1, listOf("蓝牙")) == 3, SearchText.score(e1, listOf("蓝牙")))
    ck("拼音命中 2 分", SearchText.score(e1, listOf("ly")) == 2, SearchText.score(e1, listOf("ly")))
    ck("正文命中 1 分", SearchText.score(e2, listOf("dumpsys")) == 1, SearchText.score(e2, listOf("dumpsys")))
    ck("分类命中 1 分", SearchText.score(e2, listOf("系统")) == 1, SearchText.score(e2, listOf("系统")))
    ck("两词 AND 命中", SearchText.score(e1, listOf("蓝牙", "svc")) == 4, SearchText.score(e1, listOf("蓝牙", "svc")))
    ck("一词缺失即不匹配", SearchText.score(e1, listOf("蓝牙", "电池")) == null, SearchText.score(e1, listOf("蓝牙", "电池")))
    ck("全无命中", SearchText.score(e1, listOf("zzz")) == null)
    ck("空词返回 0", SearchText.score(e1, emptyList()) == 0)

    // 拼音子串（用户只打一半）
    ck("拼音子串 ly", SearchText.score(e1, listOf("ly")) == 2)
    ck("拼音子串 dc", SearchText.score(e2, listOf("dc")) == 2)   // 标题「查看电池电量」→ ckdcdl
    ck("拼音不误伤", SearchText.score(e1, listOf("ckdc")) == null, SearchText.score(e1, listOf("ckdc")))

    // 描述/标签
    ck("描述命中", SearchText.score(e3, listOf("手动")) == 1, SearchText.score(e3, listOf("手动")))
    val e4 = e("4", "重启", "reboot", tags = listOf("系统", "电源"))
    ck("标签命中", SearchText.score(e4, listOf("电源")) == 1, SearchText.score(e4, listOf("电源")))
    ck("标签拼音命中", SearchText.score(e4, listOf("dy")) == 2, SearchText.score(e4, listOf("dy")))

    // 大小写不敏感
    ck("大小写", SearchText.score(e3, listOf("SETTINGS")) == 1)

    // 高亮区间
    fun r(t: String, vararg k: String) = SearchText.hitRanges(t, k.toList()).map { it.first..it.last }
    ck("单词命中", r("蓝牙开关", "蓝牙") == listOf(0..1), r("蓝牙开关", "蓝牙"))
    ck("两词分离", r("蓝牙与关闭", "蓝牙", "关闭") == listOf(0..1, 3..4), r("蓝牙与关闭", "蓝牙", "关闭"))
    ck("两词相邻合并", r("蓝牙关闭", "蓝牙", "关闭") == listOf(0..3), r("蓝牙关闭", "蓝牙", "关闭"))
    ck("重叠合并", r("abcdef", "abcd", "cdef") == listOf(0..5), r("abcdef", "abcd", "cdef"))
    ck("重复命中", r("aa aa", "aa") == listOf(0..1, 3..4), r("aa aa", "aa"))
    ck("无命中", r("蓝牙", "zzz").isEmpty())
    ck("大小写", r("WiFi 设置", "wifi") == listOf(0..3), r("WiFi 设置", "wifi"))
    ck("空词忽略", r("蓝牙", "  ").isEmpty())

    println("\n通过 $pass / 失败 $fail")
    if (fail > 0) throw IllegalStateException("有 $fail 项失败")
}
