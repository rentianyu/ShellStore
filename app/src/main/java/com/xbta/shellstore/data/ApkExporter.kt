package com.xbta.shellstore.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 把一条命令导出成独立的 APK。
 *
 * 做法是「模板 + 现场改写」：
 *  1. CI 里先构建一个极简执行器（runner 模块）的**未签名** APK，放进主 App 的 assets
 *  2. 导出时把它复制出来，替换里面的 assets/cmd.txt（命令）与 title.txt（标题）
 *  3. 同时改包名，避免多个导出 APK 互相覆盖安装
 *  4. 用内置的「导出专用」密钥重新签名（不是发布密钥），写到 cache 供安装/分享
 *
 * 包名为什么能直接改：模板包名 `com.xbta.cmd.a0000000` 与导出包名 `com.xbta.cmd.a<8位hex>`
 * **长度相同**（都是 21 个 ASCII 字符），所以 resources.arsc 的 package chunk 与
 * AndroidManifest.xml 的字符串池都可以原地等长覆盖，不用重建字符串池——这是整套里最容易出错的一步，
 * 等长替换把风险降到最低。
 */
object ApkExporter {

    /** 必须是 21 个字符，与 [newPackageName] 的输出长度严格一致 */
    private const val TEMPLATE_PKG = "com.xbta.cmd.a0000000"

    private const val KEYSTORE_ASSET = "export.jks"
    private const val KEYSTORE_PASS = "shellstore-export"
    private const val KEY_ALIAS = "shellstore-export"

    data class Result(val file: File, val pkg: String)

    fun newPackageName(): String =
        "com.xbta.cmd.a" + UUID.randomUUID().toString().replace("-", "").take(8)

    /**
     * 阻塞调用，务必放在 IO 线程。
     * @param onStep 进度文案回调（会从子线程调用）
     */
    fun export(ctx: Context, title: String, cmd: String, onStep: (String) -> Unit): Result {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        // 清掉上一次的导出，避免缓存目录堆积
        dir.listFiles()?.forEach { runCatching { it.delete() } }

        onStep("读取模板")
        val raw = File(dir, "template.apk")
        ctx.assets.open("runner.apk").use { input ->
            raw.outputStream().use { out -> input.copyTo(out) }
        }

        val pkg = newPackageName()
        onStep("改写包名与命令")
        val patched = File(dir, "patched.apk")
        rewriteApk(raw, patched, pkg, title, cmd)

        onStep("签名")
        val finalFile = File(dir, "ShellStore-${pkg.substringAfterLast('.')}.apk")
        sign(ctx, patched, finalFile)

        raw.delete()
        patched.delete()
        onStep("完成")
        return Result(finalFile, pkg)
    }

    // ---------------- zip 重写 ----------------

    private fun rewriteApk(input: File, output: File, pkg: String, title: String, cmd: String) {
        ZipFile(input).use { zin ->
            ZipOutputStream(output.outputStream()).use { zout ->
                for (entry in zin.entries()) {
                    var bytes = zin.getInputStream(entry).readBytes()
                    when (entry.name) {
                        "AndroidManifest.xml" -> bytes = patchManifestPackage(bytes, pkg)
                        "resources.arsc" -> bytes = patchArscPackage(bytes, pkg)
                        "assets/cmd.txt" -> bytes = cmd.trim().toByteArray(Charsets.UTF_8)
                        "assets/title.txt" -> bytes = title.take(60).toByteArray(Charsets.UTF_8)
                    }
                    val ne = ZipEntry(entry.name)
                    ne.time = entry.time
                    if (entry.method == ZipEntry.STORED) {
                        ne.method = ZipEntry.STORED
                        ne.size = bytes.size.toLong()
                        ne.compressedSize = bytes.size.toLong()
                        ne.crc = CRC32().apply { update(bytes) }.value
                    } else {
                        ne.method = ZipEntry.DEFLATED
                    }
                    zout.putNextEntry(ne)
                    zout.write(bytes)
                    zout.closeEntry()
                }
            }
        }
    }

    // ---------------- 包名改写 ----------------

    private fun patchManifestPackage(data: ByteArray, newPkg: String): ByteArray {
        val buf = data.copyOf()
        // 二进制 XML 头：type(2) headerSize(2) size(4)，子 chunk 从 headerSize 开始
        var pos = readU16(buf, 2)
        while (pos + 8 <= buf.size) {
            val type = readU16(buf, pos)
            val size = readI32(buf, pos + 4)
            if (type == 0x0001) {              // RES_STRING_POOL_TYPE
                patchStringPool(buf, pos, readU16(buf, pos + 2), TEMPLATE_PKG, newPkg)
                return buf
            }
            if (size <= 0) break
            pos += size
        }
        return buf
    }

    private fun patchStringPool(buf: ByteArray, chunkStart: Int, headerSize: Int, old: String, new: String) {
        if (old.length != new.length) return   // 只支持等长替换
        val stringCount = readI32(buf, chunkStart + 8)
        val flags = readI32(buf, chunkStart + 16)
        val stringsStart = readI32(buf, chunkStart + 20)
        val isUtf8 = (flags and 0x100) != 0     // FLAG_UTF8
        val base = chunkStart + stringsStart
        for (i in 0 until stringCount) {
            val off = readI32(buf, chunkStart + headerSize + i * 4)
            val p = base + off
            if (p < 0 || p >= buf.size) continue
            if (isUtf8) {
                var idx = p
                var len = buf[idx].toInt() and 0xFF
                if (len and 0x80 != 0) {
                    len = ((len and 0x7F) shl 8) or (buf[idx + 1].toInt() and 0xFF)
                    idx += 1
                }
                val start = idx + 1
                if (start + len > buf.size) continue
                if (String(buf, start, len, Charsets.UTF_8) == old) {
                    val n = new.toByteArray(Charsets.UTF_8)
                    System.arraycopy(n, 0, buf, start, minOf(n.size, len))
                    return
                }
            } else {
                val len = readU16(buf, p)
                val start = p + 2
                if (start + len * 2 > buf.size) continue
                if (String(buf, start, len * 2, Charsets.UTF_16LE) == old) {
                    val n = new.toByteArray(Charsets.UTF_16LE)
                    System.arraycopy(n, 0, buf, start, minOf(n.size, len * 2))
                    return
                }
            }
        }
    }

    private fun patchArscPackage(data: ByteArray, newPkg: String): ByteArray {
        val buf = data.copyOf()
        var pos = readU16(buf, 2)   // RES_TABLE headerSize（通常 12）
        while (pos + 8 <= buf.size) {
            val type = readU16(buf, pos)
            val size = readI32(buf, pos + 4)
            if (type == 0x0200) {   // RES_TABLE_PACKAGE_TYPE
                val nameOff = pos + 12                  // 包名区：128 个 UTF-16 字符定长 256 字节
                val bytes = newPkg.toByteArray(Charsets.UTF_16LE)
                if (nameOff + 256 <= buf.size) {
                    System.arraycopy(bytes, 0, buf, nameOff, bytes.size)
                    for (i in nameOff + bytes.size until nameOff + 256) buf[i] = 0
                }
                return buf
            }
            if (size <= 0) break
            pos += size
        }
        return buf
    }

    // ---------------- 签名 ----------------

    private fun sign(ctx: Context, input: File, output: File) {
        val ks = KeyStore.getInstance("PKCS12")
        ctx.assets.open(KEYSTORE_ASSET).use { ks.load(it, KEYSTORE_PASS.toCharArray()) }
        val alias = ks.aliases().nextElement()
        val key = ks.getKey(alias, KEYSTORE_PASS.toCharArray()) as PrivateKey
        val cert = ks.getCertificate(alias) as X509Certificate

        val signer = ApkSigner.SignerConfig.Builder(alias, key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setMinSdkVersion(26)
            .build()
            .sign()
    }

    // ---------------- 安装 / 分享 ----------------

    fun installIntent(ctx: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return i
    }

    fun shareIntent(ctx: Context, file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(i, "分享 APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ---------------- 小端读取 ----------------

    private fun readU16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun readI32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
}
