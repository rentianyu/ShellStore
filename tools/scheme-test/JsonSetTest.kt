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
 * JsonLite.setText（按路径改 JSON 叶子）的桌面单测。
 * 运行：见同目录 run.sh
 */
fun main() {
    val root = JsonLite.parseOrNull("""{"a":1,"b":{"c":"x","d":[10,20]},"ok":true}""")!!

    // 改对象顶层字段
    var n = JsonLite.setText(root, listOf(0), "42")
    ck("改数字保持数字", JsonLite.compose(n) == """{"a":42,"b":{"c":"x","d":[10,20]},"ok":true}""", JsonLite.compose(n))

    // 非数字降级为字符串
    n = JsonLite.setText(root, listOf(0), "abc")
    ck("数字改文本变字符串", JsonLite.compose(n).contains(""""a":"abc""""), JsonLite.compose(n))

    // 改嵌套深层
    n = JsonLite.setText(root, listOf(1, 0), "你好")
    ck("改嵌套字段", JsonLite.compose(n).contains(""""c":"你好""""), JsonLite.compose(n))

    // 改数组元素
    n = JsonLite.setText(root, listOf(1, 1, 1), "99")
    ck("改数组元素", JsonLite.compose(n).contains(""""d":[10,99]"""), JsonLite.compose(n))

    // 布尔保持
    n = JsonLite.setText(root, listOf(2), "false")
    ck("布尔保持布尔", JsonLite.compose(n).endsWith(""""ok":false}"""), JsonLite.compose(n))
    n = JsonLite.setText(root, listOf(2), "yes")
    ck("布尔改文本变字符串", JsonLite.compose(n).contains(""""ok":"yes""""), JsonLite.compose(n))

    // 越界不改
    ck("越界", JsonLite.compose(JsonLite.setText(root, listOf(9), "x")) == JsonLite.compose(root))
    ck("深层越界", JsonLite.compose(JsonLite.setText(root, listOf(1, 9), "x")) == JsonLite.compose(root))

    // 结构节点不被压成字符串
    ck("不给结构节点改名", JsonLite.compose(JsonLite.setText(root, emptyList(), "x")) == JsonLite.compose(root))

    // 空路径改叶子（数组根）
    val arr = JsonLite.parseOrNull("[1,2]")!!
    ck("根数组改元素", JsonLite.compose(JsonLite.setText(arr, listOf(0), "7")) == "[7,2]")

    // size
    ck("size 对象", JsonLite.size(root) == 3)
    ck("size 数组", JsonLite.size(JsonLite.parseOrNull("[1,2,3]")!!) == 3)

    // 原节点不变（不可变性）
    JsonLite.setText(root, listOf(0), "999")
    ck("原树未被修改", JsonLite.compose(root).contains(""""a":1"""))

    println("\n通过 $pass / 失败 $fail")
    if (fail > 0) throw IllegalStateException("$fail 项失败")
}
