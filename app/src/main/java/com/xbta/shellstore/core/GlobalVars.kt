package com.xbta.shellstore.core

/**
 * 全局变量：`{{name}}` 在任何条目的命令里都能引用，执行前替换成实际值。
 *
 * 和条目参数的 `{key}` 是两套东西：
 * - `{key}` 是**这条命令自己的**参数，详情页会生成输入框，值随条目走；
 * - `{{name}}` 是**跨条目共享**的常量（设备序列号、常用路径、某个固定 token），
 *   在「设置 › 全局变量」里维护一处，所有命令都能用。
 *
 * 注意：变量名不要和条目参数同名，否则 `{{name}}` 的内层 `{name}` 会先被参数替换掉。
 */
object GlobalVars {

    /** 允许字母数字、下划线、点、连字符；两侧空白可有可无 */
    private val RE = Regex("""\{\{\s*([A-Za-z0-9_.\-]+)\s*\}\}""")

    const val NAME_RE = "^[A-Za-z0-9_.\\-]+\$"

    fun isValidName(name: String): Boolean = Regex(NAME_RE).matches(name.trim())

    /** 命令里引用到的变量名（去重、按出现顺序） */
    fun used(cmd: String): List<String> =
        RE.findAll(cmd).map { it.groupValues[1] }.distinct().toList()

    /** 引用了但没定义的变量名 */
    fun missing(cmd: String, vars: Map<String, String>): List<String> =
        used(cmd).filter { vars[it] == null }

    /**
     * 替换全部 `{{name}}`。
     * 未定义的变量原样保留（不去掉），这样命令执行报错时还能一眼看出漏了哪个。
     */
    fun apply(cmd: String, vars: Map<String, String>): String {
        if (vars.isEmpty() || !cmd.contains("{{")) return cmd
        return RE.replace(cmd) { m ->
            val name = m.groupValues[1]
            vars[name] ?: m.value
        }
    }
}
