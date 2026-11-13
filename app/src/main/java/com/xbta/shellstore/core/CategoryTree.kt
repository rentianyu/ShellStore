package com.xbta.shellstore.core

/**
 * 把各源声明的分类拼成一棵可展示的目录树。
 *
 * 两件事：
 *  1. **补父节点**：源里只写了 `## 系统 / 网络 / DNS` 时，「系统」和「系统/网络」
 *     这两级并不存在，但界面要能点进去，所以自动补成虚拟节点（[Category.synthesized]）。
 *  2. **先序排序**：父在前、子紧随，同层按源里出现的顺序（order），保证列表读起来是棵树。
 */
object CategoryTree {

    fun build(declared: Collection<Category>): List<Category> {
        if (declared.isEmpty()) return emptyList()

        // 同一个 id 可能被多个源声明（多源合并），保留先出现的；但要合并 icon/order
        val byId = LinkedHashMap<String, Category>()
        for (c in declared) {
            val exist = byId[c.id]
            byId[c.id] = if (exist == null) c else exist.copy(
                icon = exist.icon.ifBlank { c.icon },
                order = minOf(exist.order, c.order)
            )
        }

        // 反过来给合成父节点一个合理排序：取子节点里最小的 order
        val synthOrder = HashMap<String, Int>()
        for (c in byId.values) {
            val segs = c.segments
            for (i in 1 until segs.size) {
                val pid = segs.take(i).joinToString("/")
                synthOrder[pid] = minOf(synthOrder[pid] ?: Int.MAX_VALUE, c.order)
            }
        }

        // 补父节点
        val filled = LinkedHashMap<String, Category>()
        for (c in byId.values) {
            val segs = c.segments
            for (i in 1 until segs.size) {
                val pid = segs.take(i).joinToString("/")
                if (!byId.containsKey(pid) && !filled.containsKey(pid)) {
                    filled[pid] = Category(
                        id = pid,
                        name = segs[i - 1],
                        path = segs.take(i),
                        order = synthOrder[pid] ?: Int.MAX_VALUE,
                        synthesized = true
                    )
                }
            }
            filled[c.id] = c
        }

        val children = filled.values.groupBy { it.parentId }
        val out = ArrayList<Category>(filled.size)
        fun walk(parent: String, guard: Int) {
            if (guard > SmdPath.MAX_DEPTH + 1) return
            children[parent].orEmpty()
                .sortedWith(compareBy({ it.order }, { it.name }))
                .forEach { c ->
                    out.add(c)
                    walk(c.id, guard + 1)
                }
        }
        walk("", 0)
        return out
    }

    /** [parentId] 的直接子目录 */
    fun children(all: List<Category>, parentId: String): List<Category> =
        all.filter { it.parentId == parentId }

    /** 该分类及其所有后代的 id（含自己） */
    fun subtreeIds(all: List<Category>, id: String): List<String> =
        all.filter { it.isUnder(id) }.map { it.id }

    /**
     * 折叠后的可见列表：某个目录被折叠时，它的后代全部隐藏。
     * [collapsed] 里放被折叠的分类 id。
     */
    fun visible(all: List<Category>, collapsed: Set<String>): List<Category> {
        if (collapsed.isEmpty()) return all
        return all.filter { c ->
            val segs = c.segments
            // 任一祖级被折叠，自己就不显示（自己是折叠项本身时照常显示）
            (1 until segs.size).none { i -> segs.take(i).joinToString("/") in collapsed }
        }
    }

    /** 某个分类下的条目总数（含子目录），用于列表里显示「N 条」 */
    fun countUnder(entries: List<Entry>, id: String): Int =
        entries.count { SmdPath.isUnder(it.categoryId, id) }
}
