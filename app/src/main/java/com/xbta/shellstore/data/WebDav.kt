package com.xbta.shellstore.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.xbta.shellstore.core.WebDavPath
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * WebDAV 备份客户端。
 *
 * 只用系统 API（HttpURLConnection），不引 OkHttp / Sardine：
 * 需要的动作只有 MKCOL 建目录 + PUT 上传 + GET 下载 + DELETE 探测，四十行就够。
 * 所有方法都是阻塞调用，调用方负责切 IO 线程。
 */
object WebDav {

    data class Config(
        val baseUrl: String,
        val user: String,
        val pass: String,
        val path: String
    ) {
        val ready: Boolean get() = baseUrl.isNotBlank() && user.isNotBlank()
    }

    data class Result(val ok: Boolean, val code: Int = 0, val msg: String = "") {
        companion object {
            fun ok(msg: String = "") = Result(true, 200, msg)
            fun fail(code: Int, msg: String) = Result(false, code, msg)
        }
    }

    // ---------- 纯逻辑走 core/WebDavPath（可在无 Android 环境里编译自测） ----------

    fun normalizeBase(raw: String): String = WebDavPath.normalizeBase(raw)

    fun normalizePath(raw: String): String = WebDavPath.normalizePath(raw)

    fun fileUrl(base: String, path: String): String = WebDavPath.fileUrl(base, path)

    fun dirUrls(base: String, path: String): List<String> = WebDavPath.dirUrls(base, path)

    fun explain(code: Int): String = WebDavPath.explain(code)

    // ---------- 网络 ----------

    private fun open(url: String, method: String, cfg: Config): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        if (cfg.user.isNotBlank()) {
            val token = Base64.encodeToString(
                "${cfg.user}:${cfg.pass}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP
            )
            c.setRequestProperty("Authorization", "Basic $token")
        }
        c.setRequestProperty("User-Agent", "ShellStore-WebDav/1.0")
        return c
    }

    /** MKCOL：201 新建成功、405 已存在，都算可用 */
    private fun ensureDirs(cfg: Config): Result {
        for (dir in dirUrls(cfg.baseUrl, cfg.path)) {
            var c: HttpURLConnection? = null
            try {
                c = open(dir, "MKCOL", cfg)
                val code = c.responseCode
                if (code !in 200..299 && code != 405) {
                    return Result.fail(code, "建目录失败：" + explain(code))
                }
            } catch (e: Exception) {
                return Result.fail(0, "建目录失败：" + (e.message ?: e.javaClass.simpleName))
            } finally {
                c?.disconnect()
            }
        }
        return Result.ok()
    }

    fun upload(cfg: Config, text: String): Result {
        if (!cfg.ready) return Result.fail(0, "还没配置服务器地址或账号")
        ensureDirs(cfg).let { if (!it.ok) return it }
        var c: HttpURLConnection? = null
        return try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            c = open(fileUrl(cfg.baseUrl, cfg.path), "PUT", cfg)
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            if (code in 200..299) Result.ok("已上传 ${bytes.size / 1024} KB")
            else Result.fail(code, explain(code))
        } catch (e: Exception) {
            Result.fail(0, e.message ?: e.javaClass.simpleName)
        } finally {
            c?.disconnect()
        }
    }

    /** 返回 (内容, 错误)。内容为 null 且没有错误 = 云端还没有备份 */
    fun download(cfg: Config): Pair<String?, String?> {
        if (!cfg.ready) return null to "还没配置服务器地址或账号"
        var c: HttpURLConnection? = null
        return try {
            c = open(fileUrl(cfg.baseUrl, cfg.path), "GET", cfg)
            val code = c.responseCode
            if (code == 404) return null to null
            if (code !in 200..299) return null to explain(code)
            val text = c.inputStream.bufferedReader().use { it.readText() }
            text to null
        } catch (e: Exception) {
            null to (e.message ?: e.javaClass.simpleName)
        } finally {
            c?.disconnect()
        }
    }

    /** 探测：先建目录，再 PUT 一个几十字节的探针文件，成功后删掉 */
    fun ping(cfg: Config): Result {
        if (!cfg.ready) return Result.fail(0, "先填服务器地址和账号")
        ensureDirs(cfg).let { if (!it.ok) return it }
        val probe = fileUrl(cfg.baseUrl, normalizePath(cfg.path) + ".probe")
        var c: HttpURLConnection? = null
        return try {
            val bytes = "shellstore".toByteArray(Charsets.UTF_8)
            c = open(probe, "PUT", cfg)
            c.doOutput = true
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            if (code !in 200..299) return Result.fail(code, explain(code))
            c.disconnect()
            var d: HttpURLConnection? = null
            try {
                d = open(probe, "DELETE", cfg)
                d.responseCode
            } finally {
                d?.disconnect()
            }
            Result.ok("连接正常，可读可写")
        } catch (e: Exception) {
            Result.fail(0, e.message ?: e.javaClass.simpleName)
        } finally {
            c?.disconnect()
        }
    }
}

/**
 * 云备份密码的本机加密存储（WebDAV 密码、S3 SecretKey 各自一份）。
 *
 * 用 Android Keystore 的 AES/GCM 密钥加密，密钥不出 TEE，明文只在内存里；
 * 备份文件、导出 JSON 一律不含密码。name 决定 Keystore 别名与文件名。
 * Keystore 不可用（个别魔改 ROM）时退回 base64 前缀标记，保证功能不残废。
 */
object SecretBox {

    private const val PLAIN_FLAG = "plain:"

    private fun aliasOf(name: String) = "shellstore-$name"

    private fun key(name: String): SecretKey {
        val alias = aliasOf(name)
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun file(ctx: android.content.Context, name: String) = File(ctx.filesDir, "$name.secret")

    fun save(ctx: android.content.Context, name: String, pass: String) {
        if (pass.isEmpty()) {
            runCatching { file(ctx, name).delete() }
            return
        }
        val encoded = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(name))
            val ct = cipher.doFinal(pass.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
        }.getOrElse {
            PLAIN_FLAG + Base64.encodeToString(pass.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        runCatching { file(ctx, name).writeText(encoded) }
    }

    fun load(ctx: android.content.Context, name: String): String {
        val raw = runCatching { if (file(ctx, name).exists()) file(ctx, name).readText() else "" }
            .getOrDefault("")
        if (raw.isBlank()) return ""
        if (raw.startsWith(PLAIN_FLAG)) {
            return runCatching {
                String(Base64.decode(raw.removePrefix(PLAIN_FLAG), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrDefault("")
        }
        return runCatching {
            val all = Base64.decode(raw, Base64.NO_WRAP)
            val iv = all.copyOfRange(0, 12)
            val ct = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(name), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
