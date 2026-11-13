package com.xbta.shellstore.core

/**
 * 分类路径（SMD 的「目录」规则）。
 *
 * 一条 `## 系统 / 网络 / DNS` 就是三层目录。这一段逻辑单独放，是为了让
 * 解析器、导出器、校验工具（tools/Probe）用的是同一套拆分规则 —— 三处各写一遍
 * 迟早在「全角斜杠」「多余空格」这类边缘上分叉。
 *
 * 分隔符：`/`、全角 `／`、`>`、全角 `＞`、箭头 `→`。
 * 之所以把 `>` 也算进去，是因为手写订阅源的人常写「系统>网络」。
 */
object SmdPath {

    /** 目录层数上限，防止有人写出 20 层的怪东西把界面撑爆 */
    const val MAX_DEPTH = 6

    /** 规范分隔符（写回 SMD 时统一用这个，前后带空格便于阅读） */
    const val SEP = " / "

    private val SPLITTERS = charArrayOf('/', '／', '>', '＞', '→')

    /**
     * 拆成路径段。空段会被丢掉（`系统 // DNS` → ["系统","DNS"]），
     * 全空则返回空列表（调用方按「无分类」处理）。
     */
    fun split(raw: String): List<String> {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split(*SPLITTERS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_DEPTH)
    }

    /** 段拼回 id（不对片段做任何编码：分类名里本来就不允许出现分隔符） */
    fun join(segments: List<String>): String = segments.joinToString("/")

    /** 展示用：`系统 / 网络 / DNS` */
    fun display(segments: List<String>): String = segments.joinToString(SEP)

    /**
     * 列表徽章用的短名：两层以内显示全路径，更深只显示「上级/叶子」，
     * 避免 `系统/网络/DNS` 这种长串把列表挤变形。
     */
    fun shortLabel(id: String): String {
        val segs = split(id)
        return when {
            segs.isEmpty() -> id
            segs.size <= 2 -> segs.joinToString("/")
            else -> segs.takeLast(2).joinToString("/")
        }
    }

    /** 从完整 id 取叶子名 */
    fun leaf(id: String): String = id.substringAfterLast('/')

    /** id 的前缀集合（含自己），用于「进入父目录能看到全部子目录条目」 */
    fun selfAndAncestors(id: String): List<String> {
        val segs = split(id)
        return (1..segs.size).map { segs.take(it) }.map { join(it) }
    }

    /**
     * [id] 是否在 [ancestor] 这棵子树里（含自身）。
     * ancestor 为空表示「全部」。
     */
    fun isUnder(id: String, ancestor: String): Boolean =
        ancestor.isEmpty() || id == ancestor || id.startsWith(ancestor + "/")
}
