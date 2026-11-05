import com.xbta.shellstore.core.*
import java.io.File

fun main(args: Array<String>) {
    val path = args[0]
    val name = if (args.size > 1) args[1] else "内置命令集"
    val r = SmdParser.parse(File(path).readText(), "seed", name)
    println("来源: ${r.source.name}  版本: ${r.source.meta.version}")
    println("条目: ${r.source.entries.size}")
    r.source.categories.forEach { c ->
        val n = r.source.entries.count { it.categoryId == c.name }
        println("  分类 ${c.name} → $n 条")
    }
    println("--- 明细 ---")
    r.source.entries.forEachIndexed { i, e ->
        val b = e.body.replace("\n", "\\n").let { if (it.length > 46) it.take(46) + "…" else it }
        println("${(i + 1).toString().padStart(2)} [${e.type.id.padEnd(9)}] ${e.title.padEnd(16)} r=${e.risk.id.padEnd(7)} n=${e.need.id.padEnd(6)} ${if (b.isEmpty()) "(字段表)" else b}")
    }
    if (r.warnings.isNotEmpty()) {
        println("--- 告警 ---")
        r.warnings.forEach { println("  行 ${it.line}: ${it.message}") }
    }
}
