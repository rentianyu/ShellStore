import com.xbta.shellstore.core.JsonLite
import com.xbta.shellstore.core.JsonLite.Obj
import com.xbta.shellstore.core.JsonLite.Arr
import com.xbta.shellstore.core.JsonLite.Str
import com.xbta.shellstore.core.JsonLite.Num
import com.xbta.shellstore.core.JsonLite.Bool
import com.xbta.shellstore.core.JsonLite.Null

var pass = 0; var fail = 0
fun ck(n: String, c: Boolean, got: Any? = "") { if (c) pass++ else { fail++; println("  ✗ $n  实际=$got") } }

/**
 * JsonLite（极简 JSON 解析）的桌面单测，供参数值的递归展开使用。
 * 运行：见同目录 run.sh
 */
fun main() {
    // 基本
    var n = JsonLite.parseOrNull("""{"a":1,"b":"x"}""")
    ck("对象字段数", (n as? Obj)?.fields?.size == 2, n)
    ck("字段名", (n as Obj).fields[0].first == "a")
    ck("数字原样", ((n as Obj).fields[0].second as Num).raw == "1")

    // 非 JSON
    ck("普通文本不算 JSON", JsonLite.parseOrNull("hello") == null)
    ck("数字不算 JSON", JsonLite.parseOrNull("123") == null)
    ck("数组算 JSON", JsonLite.parseOrNull("[1,2,3]") is Arr)

    // 嵌套
    n = JsonLite.parseOrNull("""{"a":{"b":[1,{"c":"d"}]}}""")
    ck("三层嵌套", n is Obj && ((n as Obj).fields[0].second as Obj).fields[0].second is Arr)

    // 转义
    n = JsonLite.parseOrNull("""{"s":"line1\nline2 \"q\" \\ / \u4e2d"}""")
    ck("转义解码", (n as Obj).fields[0].second.let { it as Str }.value == "line1\nline2 \"q\" \\ / 中",
        (n.fields[0].second as Str).value)

    // 尾逗号容忍
    ck("对象尾逗号", JsonLite.parseOrNull("""{"a":1,}""") is Obj)
    ck("数组尾逗号", JsonLite.parseOrNull("[1,2,]") is Arr)

    // 单引号容忍
    ck("单引号", JsonLite.parseOrNull("""{'a':'b'}""").let { it is Obj && (it.fields[0].second as Str).value == "b" })

    // 各种值
    n = JsonLite.parseOrNull("""{"t":true,"f":false,"z":null,"e":1.5e-3}""")
    val o = n as Obj
    ck("true", (o.fields[0].second as Bool).value)
    ck("false", !(o.fields[1].second as Bool).value)
    ck("null", o.fields[2].second is Null || o.fields[2].second == Null)
    ck("指数数字", (o.fields[3].second as Num).raw == "1.5e-3", (o.fields[3].second as Num).raw)

    // 不完整
    ck("未闭合对象", JsonLite.parseOrNull("""{"a":1""") == null)
    ck("未闭合数组", JsonLite.parseOrNull("[1,2") == null)
    ck("多余尾巴", JsonLite.parseOrNull("""{"a":1}xyz""") == null)
    ck("空对象", JsonLite.parseOrNull("{}").let { it is Obj && it.fields.isEmpty() })

    // compose / plain
    n = JsonLite.parseOrNull("""{ "a" : 1 , "b" : "文" }""")
    ck("compose 紧凑", JsonLite.compose(n!!) == """{"a":1,"b":"文"}""", JsonLite.compose(n))
    ck("plain 字符串去引号", JsonLite.plain(Str("x")) == "x")
    ck("plain 对象是紧凑串", JsonLite.plain(n) == """{"a":1,"b":"文"}""")
    ck("expandable 对象", JsonLite.expandable(n))
    ck("expandable 字符串否", !JsonLite.expandable(Str("x")))

    // 往返
    val src = """{"name":"张三","tags":["a","b"],"n":3,"ok":true}"""
    ck("往返一致", JsonLite.compose(JsonLite.parseOrNull(src)!!) == src, JsonLite.compose(JsonLite.parseOrNull(src)!!))

    // 深嵌套保护（1000 层）
    val deep = "[".repeat(1000) + "]".repeat(1000)
    ck("深嵌套不崩", JsonLite.parseOrNull(deep) == null, "null 预期")

    // 真实场景：URL 编码的 JSON 解码后再解析
    val encoded = "%7B%22a%22%3A1%7D"
    val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
    ck("解码后是 JSON", JsonLite.parseOrNull(decoded) is Obj, decoded)

    println("\n通过 $pass / 失败 $fail")
    if (fail > 0) throw IllegalStateException("$fail 项失败")
}
