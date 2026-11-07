import com.xbta.shellstore.core.SchemeParse

var pass = 0; var fail = 0
fun ck(n: String, c: Boolean, got: Any? = "") { if (c) pass++ else { fail++; println("  ✗ $n  实际=$got") } }

/**
 * url scheme 解析、编解码与回写的桌面单测（不参与 gradle 构建）。
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

    // 按需编码
    ck("纯 ASCII 不动", SchemeParse.encodeIfNeeded("abc") == "abc", SchemeParse.encodeIfNeeded("abc"))
    ck("空格编码", SchemeParse.encodeIfNeeded("a b") == "a%20b", SchemeParse.encodeIfNeeded("a b"))
    ck("中文编码", SchemeParse.encodeIfNeeded("中文") == "%E4%B8%AD%E6%96%87", SchemeParse.encodeIfNeeded("中文"))
    ck("已是编码不二次编", SchemeParse.encodeIfNeeded("%E4%B8%AD") == "%E4%B8%AD", SchemeParse.encodeIfNeeded("%E4%B8%AD"))
    ck("& 编码", SchemeParse.encodeIfNeeded("a&b") == "a%26b", SchemeParse.encodeIfNeeded("a&b"))
    ck("空串不动", SchemeParse.encodeIfNeeded("") == "")
    ck("全角空格编码", SchemeParse.encodeIfNeeded("a\u3000b") == "a%E3%80%80b", SchemeParse.encodeIfNeeded("a\u3000b"))

    // 回写
    val src = "app://host/path?a=1&b=2#frag"
    var ps = SchemeParse.parse(src).params
    ck("rebuild 不变", SchemeParse.rebuild(src, ps) == src, SchemeParse.rebuild(src, ps))
    ps = ps + SchemeParse.Param("c", SchemeParse.encodeIfNeeded("中文"))
    ck("rebuild 加参数", SchemeParse.rebuild(src, ps) == "app://host/path?a=1&b=2&c=%E4%B8%AD%E6%96%87#frag", SchemeParse.rebuild(src, ps))
    ps = listOf(SchemeParse.Param("only", "1"))
    ck("rebuild 减参数", SchemeParse.rebuild(src, ps) == "app://host/path?only=1#frag", SchemeParse.rebuild(src, ps))
    ck("rebuild 清空参数", SchemeParse.rebuild(src, emptyList()) == "app://host/path#frag", SchemeParse.rebuild(src, emptyList()))
    ck("rebuild 无 hash", SchemeParse.rebuild("app://x?a=1", listOf(SchemeParse.Param("b", "2"))) == "app://x?b=2")
    ck("rebuild 保留空值键", SchemeParse.rebuild("app://x?f", listOf(SchemeParse.Param("f", ""))) == "app://x?f")

    // 可读形式
    ck("readable 解码", SchemeParse.readable("%E4%B8%AD%E6%96%87") == "中文", SchemeParse.readable("%E4%B8%AD%E6%96%87"))
    ck("readable 纯文本", SchemeParse.readable("abc") == "abc")

    println("\n通过 $pass / 失败 $fail")
    if (fail > 0) throw IllegalStateException("$fail 项失败")
}
