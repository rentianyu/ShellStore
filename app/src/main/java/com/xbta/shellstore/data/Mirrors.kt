package com.xbta.shellstore.data

/**
 * GitHub 拉取的多线路降级。
 *
 * 每个线路默认 10 秒超时，失败换下一个；上次成功的线路会被提到最前面，
 * 所以正常情况下从第二次起只要 1~2 秒。
 *
 * 注意：不同代理对路径的拼法不一样 —— 有的要求带上 `https://` 前缀，
 * 有的必须把它去掉，混用会直接 429/403。下面每个节点的 form 是实测出来的。
 */
object Mirrors {

    enum class Form {
        /** {base}https://raw.githubusercontent.com/... */
        FULL,

        /** {base}raw.githubusercontent.com/... */
        BARE
    }

    data class Node(val name: String, val base: String, val form: Form)

    const val DIRECT = "direct"

    /** 顺序即尝试顺序。自建反代最稳，其后是实测能返回完整内容的公共代理。 */
    private val NODES = listOf(
        Node("p.xbta.cc", "https://p.xbta.cc/", Form.FULL),
        Node("gh.noki.icu", "https://gh.noki.icu/", Form.BARE),
        Node("gh-proxy.com", "https://gh-proxy.com/", Form.BARE),
        Node("ghproxy.imciel.com", "https://ghproxy.imciel.com/", Form.FULL),
        Node("proxy.vvvv.ee", "https://proxy.vvvv.ee/", Form.BARE),
        Node("github.boki.moe", "https://github.boki.moe/", Form.BARE),
        Node("gh-proxy.at9.net", "https://gh-proxy.at9.net/", Form.FULL),
        Node("cors.isteed.cc", "https://cors.isteed.cc/", Form.FULL),
        Node("ghfast.top", "https://ghfast.top/", Form.FULL),
        Node("ghproxy.net", "https://ghproxy.net/", Form.FULL),
        Node("gh.llkk.cc", "https://gh.llkk.cc/", Form.FULL),
        Node("gh.ddlc.top", "https://gh.ddlc.top/", Form.FULL),
        Node("ghfile.geekertao.top", "https://ghfile.geekertao.top/", Form.FULL),
        Node("gh.idayer.com", "https://gh.idayer.com/", Form.FULL),
        Node("gh.h233.eu.org", "https://gh.h233.eu.org/", Form.FULL),
        Node("ghproxy.homeboyc.cn", "https://ghproxy.homeboyc.cn/", Form.FULL),
        Node("gh.zwy.one", "https://gh.zwy.one/", Form.FULL)
    )

    fun nodeCount(): Int = NODES.size

    /** 只有 GitHub 相关地址才值得走这些代理，其它域名直连 */
    fun isGitHub(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("raw.githubusercontent.com") ||
            u.contains("githubusercontent.com") ||
            u.contains("github.com/") ||
            u.contains("github.io/") ||
            u.contains("objects.githubusercontent.com")
    }

    fun urlOf(node: Node, target: String): String = when (node.form) {
        Form.FULL -> node.base + target
        Form.BARE -> node.base + target.removePrefix("https://").removePrefix("http://")
    }

    private fun nodeOf(name: String): Node? = NODES.firstOrNull { it.name == name }

    /**
     * 大文件（APK）下载的线路顺序：按实测吞吐从快到慢排，
     * 慢的排后面兜底，避免一上来就在 7KB/s 的线路上耗十分钟。
     */
    private val APK_ORDER = listOf(
        "p.xbta.cc",
        "ghfast.top",
        "gh-proxy.com",
        "gh.noki.icu",
        "gh-proxy.at9.net",
        "cors.isteed.cc",
        "github.boki.moe",
        "proxy.vvvv.ee",
        "ghproxy.imciel.com",
        "gh.llkk.cc",
        "gh.h233.eu.org"
    )

    /** 给 APK 下载用：返回 (线路名, 完整 URL) 列表 */
    fun apkUrls(target: String): List<Pair<String, String>> =
        APK_ORDER.mapNotNull { name ->
            nodeOf(name)?.let { name to urlOf(it, target) }
        }

    /** 线路总数（诊断用） */
    fun apkLineCount(): Int = apkUrls("https://github.com/x/y").size

    /**
     * 依次尝试：直连 → 上次成功的线路 → 其余节点。
     * 返回的 Result.via 记录最终走的是哪条（空串表示直连）。
     */
    fun fetch(
        target: String,
        prefer: String = "",
        perTryMs: Int = 10_000,
        maxTries: Int = 8,
        validate: (String) -> Boolean = { it.isNotBlank() }
    ): Net.Result {
        val order = buildList {
            add(DIRECT)
            if (prefer.isNotBlank() && prefer != DIRECT) add(prefer)
            NODES.forEach { if (it.name != prefer) add(it.name) }
        }

        var last: Net.Result = Net.Result(null, error = "没有可用线路")
        var tried = 0
        for (name in order) {
            if (tried >= maxTries) break
            // 非 GitHub 地址只有直连这一条路
            if (name != DIRECT && !isGitHub(target)) break

            tried++
            val r = if (name == DIRECT) {
                Net.get(target, timeoutMs = perTryMs).copy(via = "")
            } else {
                val node = nodeOf(name) ?: continue
                Net.get(urlOf(node, target), timeoutMs = perTryMs).copy(via = name)
            }
            if (r.ok && validate(r.text.orEmpty())) return r
            last = r
        }
        return last
    }
}
