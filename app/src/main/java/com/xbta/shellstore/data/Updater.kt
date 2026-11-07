package com.xbta.shellstore.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内检查更新：只认正式 Release（releases/latest + 固定 asset 名）。
 *
 * 检查：主通道抓 HTML 页面（API 未认证只有 60 次/小时，出口 IP 共享时很容易被限流），
 * 页面拿不到再走 API。
 *
 * 下载：逐条线路下到 `.part.apk`，**每条下完立即做完整校验**（zip 结构 + 能否解析 +
 * 版本号 + 签名），通过才改名成正式文件；不通过就删掉换下一条线路。
 * 只有文件头 PK\x03\x04 是不够的 —— 断流时头部完好、长度也可能好几 MB，
 * 只看头会把半个包当成功，用户点到安装才被系统告知「解析包时出现问题」。
 */
object Updater {

    const val REPO = "rentianyu/ShellStore"
    private const val ASSET_NAME = "app-release.apk"

    /** 抓页面/接口用的线路（这几条实测能返回 HTML 或 JSON） */
    private val PROXIES = listOf(
        "https://p.xbta.cc/",
        "https://gh-proxy.com/",
        "https://ghfast.top/"
    )

    /** 两次 read 之间的最长间隔，超时算断流 */
    private const val READ_TIMEOUT_MS = 25_000
    private const val CONNECT_TIMEOUT_MS = 12_000

    /** 整体下载预算：所有线路 + 重试加起来不超过这个时长 */
    private const val TOTAL_BUDGET_MS = 15L * 60 * 1000

    /** version 已去掉前缀 v，用于比较与展示 */
    data class Info(val version: String, val apkUrl: String)

    data class CheckResult(val info: Info?, val error: String? = null) {
        val ok: Boolean get() = info != null
    }

    /** 按数字段比较本地与远端版本，返回 true 表示远端更新 */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { s -> s.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun releaseUrl(tag: String) =
        "https://github.com/$REPO/releases/download/$tag/$ASSET_NAME"

    fun checkLatest(): CheckResult {
        // 1) 页面通道：跟随 302 后 HTML 里带着真实 tag
        for (p in PROXIES) {
            val r = Net.get(p + "https://github.com/$REPO/releases/latest", timeoutMs = 20000)
            if (!r.ok) continue
            val html = r.text.orEmpty()
            if (html.isBlank()) continue
            val tag = Regex("""releases/tag/(v?[0-9][A-Za-z0-9._\-]*)""")
                .find(html)?.groupValues?.get(1)
            if (!tag.isNullOrBlank()) {
                return CheckResult(Info(tag.removePrefix("v"), releaseUrl(tag)))
            }
        }
        // 2) API 通道
        for (p in PROXIES) {
            val r = Net.get(p + "https://api.github.com/repos/$REPO/releases/latest", timeoutMs = 20000)
            if (!r.ok) continue
            val text = r.text.orEmpty()
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            if (!tag.isNullOrBlank()) {
                return CheckResult(Info(tag.removePrefix("v"), releaseUrl(tag)))
            }
        }
        return CheckResult(null, "检查失败：网络不可达或发布页结构变化")
    }

    /** 正式安装包路径（文件名带版本，避免不同版本互相覆盖） */
    fun cacheFile(ctx: Context, version: String): File =
        File(ctx.cacheDir, "update-$version.apk")

    /** 下载中的半成品；后缀保留 .apk，个别 ROM 的包解析对后缀敏感 */
    private fun partFile(ctx: Context, version: String): File =
        File(ctx.cacheDir, "update-$version.part.apk")

    /** 清掉已下载的包与半成品，供「重新下载」用 */
    fun clearCache(ctx: Context, version: String) {
        runCatching { cacheFile(ctx, version).delete() }
        runCatching { partFile(ctx, version).delete() }
    }

    /** 本地是否已有可用安装包：null 表示可用，否则返回原因（未下载 / 校验不通过） */
    fun localCheck(ctx: Context, version: String): String? {
        val f = cacheFile(ctx, version)
        if (!f.exists()) return "尚未下载"
        return ApkVerify.check(ctx, f, version)
    }

    /** 一次线路尝试的结果 */
    private class Attempt(
        val err: String? = null,
        val keepPart: Boolean = true,
        val fatal: Boolean = false
    )

    /**
     * 下载并校验安装包。成功返回 null，失败返回可读原因。
     *
     * 两个关键点（都是「一直重复下载」的直接原因）：
     *
     * 1. **半成品保留 + 断点续传**。8.9MB 在移动网络下往往要几分钟，以前的实现每换一条
     *    线路、每次启动 App 都把 `.part.apk` 删掉从 0 开始，用户看到的就是进度条反复归零；
     *    现在先发 `Range: bytes=<已下>-`，服务端回 206 就接着写。
     * 2. **确定性失败不重试**。签名与本机不一致这类错误，换 11 条线路下的还是同一个包，
     *    以前会白下 20 多遍才报错；现在第一遍就停。
     */
    fun download(
        ctx: Context,
        info: Info,
        onProgress: (Int) -> Unit = {},
        onDetail: (line: String, index: Int, lines: Int, bytes: Long, total: Long) -> Unit =
            { _, _, _, _, _ -> }
    ): String? {
        val dest = cacheFile(ctx, info.version)
        if (dest.exists()) {
            if (ApkVerify.check(ctx, dest, info.version) == null) {
                onProgress(100)
                return null
            }
            runCatching { dest.delete() }
        }

        val part = partFile(ctx, info.version)
        val lines = Mirrors.apkUrls(info.apkUrl)
        val started = System.currentTimeMillis()
        var last = "所有下载线路都失败了"
        var rounds = 0

        while (rounds < 3 && System.currentTimeMillis() - started < TOTAL_BUDGET_MS) {
            rounds++
            // 半成品可能已经下完了（上次改名没来得及 / 进程被杀）：先验一次，通过就直接收工
            if (part.exists() && ApkVerify.check(ctx, part, info.version) == null) {
                if (part.renameTo(dest)) {
                    onProgress(100)
                    return null
                }
            }
            for ((i, entry) in lines.withIndex()) {
                if (System.currentTimeMillis() - started >= TOTAL_BUDGET_MS) break
                val name: String = entry.first
                val url: String = entry.second
                val have = if (part.exists()) part.length() else 0L
                onDetail(name, i + 1, lines.size, have, 0)

                val r = fetchTo(url, part, have) { bytes, total ->
                    if (total > 0) {
                        onProgress((bytes * 100 / total).toInt().coerceIn(0, 99))
                    }
                    onDetail(name, i + 1, lines.size, bytes, total)
                }

                if (r.fatal) {
                    runCatching { part.delete() }
                    return r.err
                }
                if (r.err != null) {
                    last = "$name：${r.err}"
                    if (!r.keepPart) runCatching { part.delete() }
                    continue
                }

                when (val v = ApkVerify.verdict(ctx, part, info.version)) {
                    ApkVerify.Verdict.Ok -> {
                        if (part.renameTo(dest)) {
                            onProgress(100)
                            return null
                        }
                        last = "无法保存安装包到缓存目录"
                        runCatching { part.delete() }
                    }
                    is ApkVerify.Verdict.Fatal -> {
                        runCatching { part.delete() }
                        return v.why
                    }
                    is ApkVerify.Verdict.Retryable -> {
                        last = "$name：${v.why}"
                        // 下完了但内容不对，续传没有意义，删掉重来
                        runCatching { part.delete() }
                    }
                }
            }
        }
        return if (rounds > 1) "$last（已重试 $rounds 轮）" else last
    }

    /**
     * 单条线路下载，支持断点续传。
     *
     * have > 0 时请求 `Range: bytes=have-`：
     *   · 回 206 → 追加写，进度接着上次；
     *   · 回 200 → 该节点不支持 Range，只能从头覆盖（进度会归零，但内容是对的）；
     *   · 回 416 → 已下的比远端还多，说明残留文件不对，删了重来。
     */
    private fun fetchTo(
        url: String,
        out: File,
        have: Long,
        onBytes: (Long, Long) -> Unit
    ): Attempt {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "ShellStore (Android)")
                setRequestProperty("Accept", "application/octet-stream, */*")
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            val code = conn.responseCode
            if (code == 416) return Attempt("已下内容与服务端不一致", keepPart = false)
            if (code !in 200..299) return Attempt("HTTP $code")

            val append = code == 206 && have > 0
            val segTotal = conn.contentLengthLong
            val total = if (append && segTotal > 0) have + segTotal else segTotal
            var written = if (append) have else 0L
            var lastTick = written
            onBytes(written, total)

            conn.inputStream.use { input ->
                FileOutputStream(out, append).use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        o.write(buf, 0, n)
                        written += n
                        if (written - lastTick >= 256 * 1024) {
                            lastTick = written
                            onBytes(written, total)
                        }
                    }
                    o.flush()
                }
            }
            onBytes(written, total)
            // 有 Content-Length 就要求字节数对得上，断了保留已下部分等下次续传
            if (segTotal > 0 && written != total) return Attempt("下载中断（$written/$total 字节）")
            if (written < 100_000) return Attempt("内容太短（$written 字节）", keepPart = false)
            Attempt()
        } catch (e: Exception) {
            Attempt(e.javaClass.simpleName + (e.message?.let { "：$it" } ?: ""))
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * 唤起系统安装器。装之前再校验一遍：缓存里的包可能被系统清理工具破坏，
     * 校验不过就顺手删掉，让用户走「重新下载」而不是看着系统报解析失败。
     */
    fun install(ctx: Context, info: Info): String? {
        val f = cacheFile(ctx, info.version)
        if (!f.exists()) return "安装包不存在，请先下载"
        ApkVerify.check(ctx, f, info.version)?.let { why ->
            runCatching { f.delete() }
            return "安装包校验未通过（$why），已清除，请重新下载"
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(i)
            null
        }.getOrElse { "无法唤起安装器：${it.javaClass.simpleName}" }
    }
}
