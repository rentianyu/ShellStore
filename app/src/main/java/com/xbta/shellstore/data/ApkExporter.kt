package com.xbta.shellstore.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.xbta.shellstore.core.ApkPatcher
import com.xbta.shellstore.core.ZipCheck
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * 把一条命令导出成独立的 APK。
 *
 * 做法是「模板 + 现场改写」：
 *  1. CI 里先构建一个极简执行器（runner 模块）的**未签名** APK，放进主 App 的 assets
 *  2. 导出时把它复制出来，替换里面的 assets/cmd.txt（命令）与 title.txt（标题）
 *  3. 同时改包名，避免多个导出 APK 互相覆盖安装
 *  4. 用内置的「导出专用」密钥重新签名（不是发布密钥），写到 cache 供安装/分享
 *
 * 包名为什么能直接改：模板包名 `com.xbta.cmd.a0000000` 与导出包名 `com.xbta.cmd.a<7位hex>`
 * **长度相同**（都是 21 个 ASCII 字符，生成时由模板长度反推），所以 resources.arsc 的 package chunk 与
 * AndroidManifest.xml 的字符串池都可以原地等长覆盖，不用重建字符串池——这是整套里最容易出错的一步，
 * 等长替换把风险降到最低。
 */
object ApkExporter {

    private const val KEYSTORE_ASSET = "export.jks"
    private const val KEYSTORE_PASS = "shellstore-export"
    private const val KEY_ALIAS = "shellstore-export"

    data class Result(val file: File, val pkg: String)

    /** 包名长度由模板反推（见 ApkPatcher.newPackage），这里不再手写长度 */
    fun newPackageName(): String = ApkPatcher.newPackage()

    /**
     * 阻塞调用，务必放在 IO 线程。
     * @param onStep 进度文案回调（会从子线程调用）
     */
    fun export(ctx: Context, title: String, cmd: String, onStep: (String) -> Unit): Result =
        try {
            doExport(ctx, title, cmd, onStep)
        } catch (t: Throwable) {
            // 导出失败很少见，但真机上只看一句 message 定位不了，整段堆栈留进崩溃日志
            CrashLog.record(ctx, "export-apk", t)
            throw t
        }

    private fun doExport(ctx: Context, title: String, cmd: String, onStep: (String) -> Unit): Result {
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

        // 产物自检：签名会重写 zip，结构坏了也要当场发现，而不是让用户装的时候才报错
        ZipCheck.verify(finalFile, minSize = 1024L)?.let { why ->
            runCatching { finalFile.delete() }
            error("导出的安装包自检未通过：$why")
        }

        raw.delete()
        patched.delete()
        onStep("完成")
        return Result(finalFile, pkg)
    }

    // ---------------- zip 与包名改写（算法在 core/ApkPatcher，纯 Kotlin 可桌面验证） ----------------

    private fun rewriteApk(input: File, output: File, pkg: String, title: String, cmd: String) {
        ApkPatcher.rewrite(input, output, pkg, cmd, title)
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
}
