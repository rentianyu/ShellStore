package com.xbta.shellstore.core

/**
 * ShellStore 的 deep link 协议 —— App 的每一项功能都能从外部用 scheme 调起。
 *
 * 形式：
 * ```
 * shellstore://<动作>?<参数>          shellstore://run?id=abc123
 * shellstore://<组>/<动作>?<参数>     shellstore://source/add?url=https%3A%2F%2F...
 * ```
 * 组/动作形式只是给人读着顺，解析后一样是 `组-动作`（`source-add`）。
 *
 * 这个文件是**纯逻辑**，不 import 任何 android.*，可以直接用 kotlinc 在桌面跑单测
 * （tools/link-test）。动作清单 [ALL] 同时被 App 内的「scheme 用法」页使用，
 * 所以不会出现「文档写了、代码没实现」的脱节。
 */
object DeepLink {

    const val SCHEME = "shellstore"

    /** 一条协议说明。App 内的用法页直接按 [ALL] 渲染。 */
    data class Spec(
        /** 归一后的动作名（别名已经在解析阶段消化掉） */
        val name: String,
        /** 示例地址 */
        val usage: String,
        /** 做什么 */
        val desc: String,
        /** 分组：导航 / 动作 / 数据 / 设置 */
        val group: String,
        /** 会改动数据或执行命令，误点代价高 */
        val danger: Boolean = false,
        /** 必须带 id 参数 */
        val needsId: Boolean = false
    )

    val ALL: List<Spec> = listOf(
        // ---------- 导航 ----------
        Spec("shop", "shellstore://shop", "回到商店首页（并清空页面栈）", "导航"),
        Spec("favorite", "shellstore://favorite", "切到收藏标签页", "导航"),
        Spec("me", "shellstore://me", "切到我的标签页", "导航"),
        Spec("cats", "shellstore://cats", "全部分类", "导航"),
        Spec("cat", "shellstore://cat?name=软件", "打开某个分类", "导航"),
        Spec("tags", "shellstore://tags", "标签视图", "导航"),
        Spec("recent", "shellstore://recent", "最近使用", "导航"),
        Spec("favs", "shellstore://favs", "全部收藏", "导航"),
        Spec("folder", "shellstore://folder?id=f1", "某个收藏文件夹", "导航"),
        Spec("entry", "shellstore://entry?id=abc123", "打开条目详情", "导航", needsId = true),
        Spec("new", "shellstore://new?folder=f1", "新建条目（打开编辑器）", "导航"),
        Spec("edit", "shellstore://edit?id=abc123", "编辑本地条目", "导航", needsId = true),
        Spec("search", "shellstore://search?q=蓝牙", "搜索并直接进结果页", "导航"),
        Spec("settings", "shellstore://settings", "设置页", "导航"),
        Spec("sources", "shellstore://sources", "订阅源管理", "导航"),
        Spec("backup", "shellstore://backup", "备份与恢复", "导航"),
        Spec("cloud", "shellstore://cloud", "云备份配置", "导航"),
        Spec("about", "shellstore://about", "关于", "导航"),
        Spec("changelog", "shellstore://changelog", "更新日志", "导航"),
        Spec("help", "shellstore://help", "使用帮助", "导航"),
        Spec("sourcehelp", "shellstore://sourcehelp", "订阅源格式说明", "导航"),
        Spec("schemehelp", "shellstore://schemehelp", "本页：scheme 用法", "导航"),
        Spec("features", "shellstore://features", "全部功能总览", "导航"),
        Spec("legal", "shellstore://legal?kind=privacy", "用户协议 / 隐私政策（kind=user|privacy|summary）", "导航"),

        // ---------- 动作 ----------
        Spec("run", "shellstore://run?id=abc123", "直接执行条目；多余参数当参数值（&k=v）", "动作", danger = true, needsId = true),
        Spec("copy", "shellstore://copy?id=abc123&shell=1", "复制条目（shell=1 复制成命令）", "动作", needsId = true),
        Spec("fav", "shellstore://fav?id=abc123", "收藏 / 取消收藏（toggle）", "动作", needsId = true),
        Spec("unfav", "shellstore://unfav?id=abc123&confirm=1", "取消收藏", "动作", danger = true, needsId = true),
        Spec("pin", "shellstore://pin?id=abc123&on=1", "置顶 / 取消置顶", "动作", needsId = true),
        Spec("shortcut", "shellstore://shortcut?id=abc123", "创建桌面快捷方式", "动作", needsId = true),
        Spec("tile", "shellstore://tile?id=abc123", "添加控制中心磁贴", "动作", needsId = true),
        Spec("export-apk", "shellstore://export-apk?id=abc123", "导出成独立 APK", "动作", danger = true, needsId = true),

        // ---------- 数据 ----------
        Spec("clear", "shellstore://clear?what=recent", "清空 recent|search|favs|crash", "数据", danger = true),
        Spec("source-add", "shellstore://source-add?url=https%3A%2F%2F…&name=我的源", "添加订阅源", "数据"),
        Spec("source-del", "shellstore://source-del?id=s1&confirm=1", "删除订阅源", "数据", danger = true, needsId = true),
        Spec("source-toggle", "shellstore://source-toggle?id=s1&on=0", "启用 / 停用订阅源", "数据", needsId = true),
        Spec("source-refresh", "shellstore://source-refresh", "刷新全部源（带 id 只刷一个）", "数据"),
        Spec("source-check", "shellstore://source-check", "检查源更新", "数据"),
        Spec("source-apply", "shellstore://source-apply", "应用源更新", "数据"),
        Spec("source-dismiss", "shellstore://source-dismiss", "放弃这批源更新（不写入）", "数据"),
        Spec("favs-export", "shellstore://favs-export?title=我的收藏", "导出收藏为 SMD 文本（写剪贴板）", "数据"),
        Spec("series", "shellstore://series?on=1", "小贝塔订阅源总开关", "数据"),
        Spec("series-refresh", "shellstore://series-refresh", "重拉系列清单", "数据"),
        Spec("backup-now", "shellstore://backup-now", "立即云备份", "数据"),
        Spec("backup-ping", "shellstore://backup-ping", "测试云备份连接（不发数据）", "数据"),
        Spec("backup-export", "shellstore://backup-export", "导出备份 JSON（写剪贴板）", "数据"),
        Spec("backup-import", "shellstore://backup-import?data=…", "从 JSON 导入（贴到剪贴板再执行也可）", "数据", danger = true),
        Spec("restore-cloud", "shellstore://restore-cloud?confirm=1", "从云端恢复（覆盖本地）", "数据", danger = true),
        Spec("check-update", "shellstore://check-update", "检查自身更新", "数据"),
        Spec("download-update", "shellstore://download-update", "下载并提示安装更新", "数据"),

        // ---------- 设置 ----------
        Spec("set", "shellstore://set?key=theme&value=dark", "改设置项（见下方字段表）", "设置")
    )

    /** 设置项字段表：key → 说明。值按字段类型解析。 */
    val SETTING_KEYS: List<Pair<String, String>> = listOf(
        "theme" to "light / dark / system",
        "fontLevel" to "字号档位 0-9",
        "uiLevel" to "界面缩放档位 0-4",
        "listStyle" to "card / list",
        "foldPreview" to "true / false（列表收起命令预览）",
        "copyPrefix" to "none / adb / su",
        "confirmDanger" to "true / false（危险动作二次确认）",
        "copyAndReturn" to "true / false（复制后自动返回）",
        "favAskFolder" to "true / false（收藏时问文件夹）",
        "checkUpdateOnStart" to "true / false",
        "wifiOnlyUpdate" to "true / false",
        "cloudBackend" to "webdav / s3",
        "webdavAuto" to "true / false（变更后自动备份）",
        "webdavUrl" to "WebDAV 地址",
        "webdavUser" to "WebDAV 用户名",
        "webdavPath" to "远端路径",
        "s3Endpoint" to "S3 Endpoint",
        "s3Region" to "S3 Region",
        "s3Bucket" to "S3 桶名",
        "s3Key" to "对象键",
        "s3PathStyle" to "true / false（路径风格）",
        "uiFont" to "system / custom",
        "codeFont" to "system / custom",
        "exportDirUri" to "导出目录（SAF tree uri）"
    )

    /** 解析得到的一条命令 */
    data class Cmd(val name: String, val args: Map<String, String>) {

        /** 取第一个非空值 */
        fun s(key: String, vararg alt: String): String? {
            args[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            for (a in alt) args[a]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            return null
        }

        fun id(): String? = s("id")

        /** 布尔：1/true/yes/on 为真，0/false/no/off 为假；缺省取 [def] */
        fun flag(key: String, def: Boolean = false): Boolean = when (args[key]?.trim()?.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> def
        }

        fun int(key: String): Int? = args[key]?.trim()?.toIntOrNull()

        /** 危险动作是否被显式确认 */
        fun confirmed(): Boolean = flag("confirm")

        /** 协议里登记过这个动作 */
        val known: Boolean get() = ALL.any { it.name == name }

        val danger: Boolean get() = ALL.firstOrNull { it.name == name }?.danger ?: false

        /** run 动作里除保留键之外的参数，都当作条目参数值 */
        fun entryParams(): Map<String, String> =
            args.filterKeys { it !in RESERVED }
    }

    /** 只作为协议控制用、不当条目参数值的键 */
    private val RESERVED = setOf(
        "id", "confirm", "shell", "q", "name", "url", "on", "key", "value",
        "kind", "what", "data", "title"
    )

    /** 常见别名 → 规范名 */
    private val ALIAS = mapOf(
        "home" to "shop", "store" to "shop", "index" to "shop", "open" to "shop",
        "favorites" to "favorite", "star" to "favorite", "fav-tab" to "favorite",
        "mine" to "me", "profile" to "me",
        "detail" to "entry", "item" to "entry",
        "category" to "cat", "categories" to "cats",
        "tag" to "tags",
        "favourite" to "favorite",
        "newentry" to "new", "create" to "new",
        "apk" to "export-apk", "export" to "export-apk",
        "refresh" to "source-refresh",
        "src-add" to "source-add", "src-del" to "source-del",
        "src-toggle" to "source-toggle", "src-refresh" to "source-refresh",
        "legal-user" to "legal", "legal-privacy" to "legal",
        "usage" to "schemehelp"
    )

    /** 参数名别名 → 规范名 */
    private val ARG_ALIAS = mapOf(
        "entry" to "id", "entryid" to "id", "e" to "id", "media" to "id",
        "query" to "q", "kw" to "q", "keyword" to "q",
        "src" to "id", "source" to "id", "folder" to "id", "f" to "id",
        "enable" to "on", "state" to "on",
        "k" to "key", "v" to "value",
        "type" to "what", "target" to "what",
        "json" to "data"
    )

    /**
     * 解析一条 deep link。
     *
     * - scheme 不是 shellstore 或串为空 → null（交给系统按普通链接处理）
     * - 动作不认识 → 仍返回 Cmd（`known == false`），由调用方给出「未知动作」提示，
     *   比笼统的解析失败更有用
     */
    fun parse(raw: String?): Cmd? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val ci = text.indexOf(':')
        if (ci <= 0) return null
        if (text.substring(0, ci).lowercase() != SCHEME) return null

        var rest = text.substring(ci + 1)
        rest = if (rest.startsWith("//")) rest.substring(2) else rest.trimStart('/')

        // 去掉锚点，再拆路径与查询串
        val hash = rest.indexOf('#')
        if (hash >= 0) rest = rest.substring(0, hash)
        val qi = rest.indexOf('?')
        val pathPart = if (qi >= 0) rest.substring(0, qi) else rest
        val queryPart = if (qi >= 0) rest.substring(qi + 1) else ""

        val segs = pathPart.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val rawName = segs.joinToString("-")
        val name = normalize(if (rawName.isEmpty()) "shop" else rawName)
        return Cmd(name, parseArgs(queryPart))
    }

    private fun normalize(n: String): String {
        val base = n.lowercase().replace('_', '-')
        return ALIAS[base] ?: base
    }

    /** `a=1&b=%E4%B8%AD&flag` → map。值用 decodeStrict，`+` 不当空格（值里可能是 URL/base64） */
    fun parseArgs(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (seg in query.split('&')) {
            if (seg.isEmpty()) continue
            val eq = seg.indexOf('=')
            val rawKey = if (eq < 0) seg else seg.substring(0, eq)
            val rawVal = if (eq < 0) "" else seg.substring(eq + 1)
            val key = SchemeParse.decodeStrict(rawKey).trim().lowercase()
            if (key.isEmpty()) continue
            out[ARG_ALIAS[key] ?: key] = SchemeParse.decodeStrict(rawVal)
        }
        return out
    }

    /** 生成一条可点的链接（参数自动编码） */
    fun build(name: String, vararg args: Pair<String, String>): String {
        val q = args.joinToString("&") { (k, v) -> "$k=" + SchemeParse.encode(v) }
        return "$SCHEME://$name" + if (q.isEmpty()) "" else "?$q"
    }
}
