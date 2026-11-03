package com.xbta.shellstore.data

import com.xbta.shellstore.core.S3Sign
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * S3 兼容对象存储的备份客户端（PUT / GET / HEAD）。
 *
 * 兼容面：AWS S3、Cloudflare R2（region 填 auto）、MinIO、阿里 OSS/腾讯 COS 的 S3 兼容端点、
 * 七牛 Kodo 的 S3 端点等。虚拟主机风格（bucket.endpoint）与路径风格（endpoint/bucket）都支持。
 *
 * 只用 sigv4 必需的三个签名头（host / x-amz-content-sha256 / x-amz-date），
 * 不签 Content-Type 与 Content-Length，避免 HttpURLConnection 自行改动头部导致签名不一致。
 */
object S3Backup {

    data class Config(
        val endpoint: String,
        val region: String,
        val bucket: String,
        val accessKey: String,
        val secretKey: String,
        val key: String,
        val pathStyle: Boolean = false
    ) {
        val ready: Boolean
            get() = endpoint.isNotBlank() && bucket.isNotBlank() &&
                accessKey.isNotBlank() && secretKey.isNotBlank()
    }

    data class Result(val ok: Boolean, val code: Int = 0, val msg: String = "") {
        companion object {
            fun ok(msg: String = "") = Result(true, 200, msg)
            fun fail(code: Int, msg: String) = Result(false, code, msg)
        }
    }

    // ---------- 纯逻辑 ----------

    // 地址拼装全部委托 core/S3Sign，那里能在桌面环境编译自测

    fun splitEndpoint(endpoint: String): Pair<String, String> = S3Sign.splitEndpoint(endpoint)

    fun host(cfg: Config): String = S3Sign.host(cfg.endpoint, cfg.pathStyle, cfg.bucket)

    fun objectPath(cfg: Config): String = S3Sign.objectPath(cfg.pathStyle, cfg.bucket, cfg.key)

    fun objectUrl(cfg: Config): String =
        S3Sign.objectUrl(cfg.endpoint, cfg.pathStyle, cfg.bucket, cfg.key)

    /** 从 S3 的 XML 错误体里抠出 Code/Message，取不到就用通用文案 */
    fun explain(code: Int, body: String?): String {
        val codeTag = Regex("<Code>([^<]{1,80})</Code>").find(body ?: "")?.groupValues?.get(1)
        val msgTag = Regex("<Message>([^<]{1,160})</Message>").find(body ?: "")?.groupValues?.get(1)
        val detail = listOfNotNull(codeTag, msgTag).joinToString(" · ").ifBlank { null }
        val base = when (code) {
            0 -> "网络异常"
            301, 307 -> "区域或 Endpoint 不对（Region 与桶所在地域要一致）"
            400 -> "请求被拒绝"
            403 -> "认证失败或无权限（检查 AccessKey/SecretKey 与桶权限）"
            404 -> "桶或路径不存在"
            411 -> "缺少长度信息"
            412 -> "前置条件不满足"
            413 -> "备份文件超出单次上传限制"
            500, 502, 503 -> "服务端错误 $code"
            else -> "HTTP $code"
        }
        return if (detail != null) "$base（$detail）" else base
    }

    // ---------- 签名 ----------

    private fun amzDate(now: Date): String =
        SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)

    private fun dateStamp(now: Date): String =
        SimpleDateFormat("yyyyMMdd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(now)

    /** 建好带签名的连接（method 固定，payloadHash 由调用方给） */
    private fun open(cfg: Config, method: String, payloadHash: String): HttpURLConnection {
        val now = Date()
        val amz = amzDate(now)
        val ds = dateStamp(now)
        val region = cfg.region.trim().ifBlank { "auto" }
        val url = objectUrl(cfg)
        val uri = S3Sign.canonicalUri(objectPath(cfg))
        val headers = listOf(
            "host" to host(cfg),
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amz
        )
        val cr = S3Sign.canonicalRequest(method, uri, "", headers, payloadHash)
        val auth = S3Sign.authorization(
            accessKey = cfg.accessKey.trim(),
            secret = cfg.secretKey,
            amzDate = amz,
            dateStamp = ds,
            region = region,
            headers = headers,
            canonicalRequest = cr
        )
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 45_000
        c.instanceFollowRedirects = false   // 3xx 通常意味着 region/endpoint 不对，让用户看到真实码
        c.setRequestProperty("x-amz-content-sha256", payloadHash)
        c.setRequestProperty("x-amz-date", amz)
        c.setRequestProperty("Authorization", auth)
        return c
    }

    private fun errBody(c: HttpURLConnection): String? =
        runCatching {
            c.errorStream?.bufferedReader()?.use { it.readText().take(600) }
        }.getOrNull()

    // ---------- 动作 ----------

    fun upload(cfg: Config, text: String): Result {
        if (!cfg.ready) return Result.fail(0, "还没配齐 Endpoint / 桶 / 密钥")
        val bytes = text.toByteArray(Charsets.UTF_8)
        val hash = S3Sign.sha256Hex(bytes)
        var c: HttpURLConnection? = null
        return try {
            c = open(cfg, "PUT", hash)
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            if (code in 200..299) Result.ok("已上传 ${bytes.size / 1024} KB")
            else Result.fail(code, explain(code, errBody(c)))
        } catch (e: Exception) {
            Result.fail(0, e.message ?: e.javaClass.simpleName)
        } finally {
            c?.disconnect()
        }
    }

    /** 返回 (内容, 错误)；内容 null 且无错误 = 云端还没有备份 */
    fun download(cfg: Config): Pair<String?, String?> {
        if (!cfg.ready) return null to "还没配齐 Endpoint / 桶 / 密钥"
        var c: HttpURLConnection? = null
        return try {
            c = open(cfg, "GET", S3Sign.EMPTY_SHA256)
            val code = c.responseCode
            if (code == 404) return null to null
            if (code !in 200..299) return null to explain(code, errBody(c))
            val text = c.inputStream.bufferedReader().use { it.readText() }
            text to null
        } catch (e: Exception) {
            null to (e.message ?: e.javaClass.simpleName)
        } finally {
            c?.disconnect()
        }
    }

    /** 探测：HEAD 对象。200 = 已有备份可读可写；404 = 认证通过但对象还没上传 */
    fun ping(cfg: Config): Result {
        if (!cfg.ready) return Result.fail(0, "先填 Endpoint、桶名和密钥")
        var c: HttpURLConnection? = null
        return try {
            c = open(cfg, "HEAD", S3Sign.EMPTY_SHA256)
            val code = c.responseCode
            when {
                code in 200..299 -> Result.ok("连接正常，已有备份文件可读写")
                code == 404 -> Result.ok("连接正常（该对象还没有，首次备份会自动创建）")
                else -> Result.fail(code, explain(code, errBody(c)))
            }
        } catch (e: Exception) {
            Result.fail(0, e.message ?: e.javaClass.simpleName)
        } finally {
            c?.disconnect()
        }
    }
}
