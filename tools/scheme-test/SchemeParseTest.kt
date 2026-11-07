import com.xbta.shellstore.core.SchemeParse

var pass = 0; var fail = 0
fun ck(n: String, c: Boolean, got: Any? = "") { if (c) pass++ else { fail++; println("  ✗ $n  实际=$got") } }

/**
 * url scheme 解析与编解码的桌面单测（不参与 gradle 构建）。
 * 运行：见同目录 run.sh
 */
fun main() {
    // 基本
    var p = SchemeParse.parse("weixin://wx.tenpay.com/h5pay?k1=v1&k2=v2")
    ck("scheme", p.scheme == "weixin", p.scheme)
    ck("body", p.body == "//wx.tenpay.com/h5pay", p.body)
    ck("参数数", p.params.size == 2, p.params)
    ck("参数 1", p.params[0] == SchemeParse.Param("k1", "v1"), p.params[0])

    // 值里带 =
    p = SchemeParse.parse("app://x?a=1&token=abc==&b=2")
    ck("值里带等号", p.params[1].value == "abc==", p.params[1])

    // 重复 key
    p = SchemeParse.parse("app://x?id=1&id=2&id=3")
    ck("重复键保留", p.params.count { it.key == "id" } == 3, p.params)

    // 只有 key
    p = SchemeParse.parse("app://x?flag&a=1")
    ck("无值键", p.params[0].key == "flag" && p.params[0].value == "", p.params[0])

    // hash
    p = SchemeParse.parse("app://x?a=1#frag?b=2")
    ck("hash 里 ? 不算参数", p.params.size == 1 && p.hash == "frag?b=2", "${p.params} / ${p.hash}")

    // 无 query
    p = SchemeParse.parse("alipays://platformapi/startapp")
    ck("无参数", p.params.isEmpty() && p.ok, p)
    ck("无参数 scheme", p.scheme == "alipays", p.scheme)

    // 非标准：带空格/中文（URL 解析器会抛异常的场景）
    p = SchemeParse.parse("intent:#Intent;action=android.intent.action.VIEW;end")
    ck("intent 型不崩", p.ok, p)

    // 中文原文
    p = SchemeParse.parse("app://x?name=张三&city=泰安")
    ck("中文原文", p.params[0].value == "张三", p.params[0])

    // 解码
    ck("percent 解码", SchemeParse.decode("%E4%B8%AD%E6%96%87") == "中文", SchemeParse.decode("%E4%B8%AD%E6%96%87"))
    ck("+ 当空格", SchemeParse.decode("a+b") == "a b", SchemeParse.decode("a+b"))
    ck("非法序列原样", SchemeParse.decode("%zz%") == "%zz%", SchemeParse.decode("%zz%"))
    ck("strict 保留 +", SchemeParse.decodeStrict("a+b") == "a+b", SchemeParse.decodeStrict("a+b"))
    ck("strict 解 percent", SchemeParse.decodeStrict("%E4%B8%AD") == "中")

    // 编码
    ck("编码空格", SchemeParse.encode("a b") == "a%20b", SchemeParse.encode("a b"))
    ck("编码往返", SchemeParse.decode(SchemeParse.encode("中文 a&b")) == "中文 a&b", SchemeParse.encode("中文 a&b"))

    // 多层解码
    ck("两层", SchemeParse.prettyDecode("%2520") == " ", SchemeParse.prettyDecode("%2520"))
    ck("三层", SchemeParse.prettyDecode("%25253A") == ":", SchemeParse.prettyDecode("%25253A"))
    ck("纯文本不动", SchemeParse.prettyDecode("abc") == "abc")

    // 判断
    ck("looksEncoded 真", SchemeParse.looksEncoded("a%20b"))
    ck("looksEncoded 假", !SchemeParse.looksEncoded("abc"))

    // 空
    ck("空串", !SchemeParse.parse("").ok)

    // 真实样本（微信支付）
    val real = "weixin://wxpay/bizpayurl?pr=abcdefg&appid=wx1234567890abcdef"
    p = SchemeParse.parse(real)
    ck("真实样本 2 参数", p.params.size == 2, p.params)

    // 长 base64 值
    val b64 = "app://x?data=" + "a".repeat(500) + "=="
    p = SchemeParse.parse(b64)
    ck("长值完整", p.params[0].value.length == 502, p.params[0].value.length)

    println("\n通过 $pass / 失败 $fail")
    if (fail > 0) throw IllegalStateException("$fail 项失败")
}
