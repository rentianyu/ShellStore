package com.xbta.shellstore.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内检查更新。
 * 主通道抓 releases/latest 页面取 tag（API 未认证只有 60 次/小时，且出口 IP 共享时很容易被限流），
 * 下载走多线路代理，落地后校验文件头。
 */
object Updater {

    const val REPO = "rentianyu/ShellStore"
    private const val ASSET_NAME = "app-release.apk"

    private val PROXIES = listOf(
        "https://p.xbta.cc/",
        "https://gh-proxy.com/",
        "https://ghfast.top/"
    )

    data class Info(val tag: String, val apkUrl: String)

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
                return CheckResult(Info(tag, "https://github.com/$REPO/releases/download/$tag/$ASSET_NAME"))
            }
        }
        // 2) API 通道
        for (p in PROXIES) {
            val r = Net.get(p + "https://api.github.com/repos/$REPO/releases/latest", timeoutMs = 20000)
            if (!r.ok) continue
            val text = r.text.orEmpty()
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            if (!tag.isNullOrBlank()) {
                return CheckResult(Info(tag, "https://github.com/$REPO/releases/download/$tag/$ASSET_NAME"))
            }
        }
        return CheckResult(null, "检查失败：网络不可达或发布页结构变化")
    }

    /** 下载 APK 到缓存目录；成功返回 null，失败返回原因 */
    fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit = {}): String? {
        val dest = File(ctx.cacheDir, "update-${info.tag}.apk")
        if (dest.exists() && dest.length() > 1_000_000) return null

        for (p in PROXIES) {
            val ok = runCatching {
                val conn = (URL(p + info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "ShellStore/0.4 (Android)")
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        var read = 0L
                        var last = 0
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct / 5 != last / 5) { last = pct; onProgress(pct) }
                            }
                        }
                    }
                }
                conn.disconnect()
                dest.length() > 1_000_000
            }.getOrDefault(false)
            if (ok) {
                // 校验 zip 文件头 PK\x03\x04，防止代理返回 HTML 错误页
                val head = ByteArray(4)
                runCatching { dest.inputStream().use { it.read(head) } }
                if (head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                    head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
                ) {
                    return null
                }
                runCatching { dest.delete() }
            }
        }
        return "所有下载线路都失败了"
    }

    fun install(ctx: Context, info: Info): String? {
        val f = File(ctx.cacheDir, "update-${info.tag}.apk")
        if (!f.exists() || f.length() < 1_000_000) return "安装包不存在，请先下载"
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
