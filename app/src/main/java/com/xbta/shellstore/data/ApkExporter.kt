package com.xbta.shellstore.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.android.apksig.ApkSigner
import com.xbta.shellstore.core.ApkEntry
import com.xbta.shellstore.core.ApkKit
import com.xbta.shellstore.core.ArscEditor
import com.xbta.shellstore.core.AxmlEditor
import com.xbta.shellstore.core.ExportSpec
import com.xbta.shellstore.core.PackageRules
import com.xbta.shellstore.core.ZipCheck
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * 把一条命令导出成独立的 APK。
 *
 * 做法是「CI 预构建模板 + 运行时改造」，改造点：
 *  1. **改名/改包名**：重建 AndroidManifest 的字符串池（变长替换，支持任意长度）
 *  2. **改包名（资源表）**：resources.arsc 的 package chunk 是 128 个 UTF-16 定长区，
 *     直接写入 + 余下清零，同样支持任意长度
 *  3. **换图标**：替换各密度 `res/mipmap-<density>-v4/ic_launcher.png`
 *  4. **写配置**：`assets/config.json` 存动作类型 / 内容 / 执行身份
 *  5. **重签名**：内置「导出专用」keystore（不是发布密钥），签 v1/v2/v3
 *
 * 全部算法在 `core/` 里（ApkKit / AxmlEditor / ArscEditor / ExportSpec），不依赖 android，
 * 所以能拿真实模板在桌面上跑同一份代码做验证。
 */
object ApkExporter {

    private const val KEYSTORE_ASSET = "export.jks"
    private const val KEYSTORE_PASS = "shellstore-export"
    private const val KEY_ALIAS = "shellstore-export"

    /** 模板里预埋的三个标记，导出时按字符串池替换 */
    private const val TPL_PKG = "com.xbta.cmd.tpl"
    private const val TPL_LABEL = "SSTPL_LABEL_PLACEHOLDER_000000"
    private const val TPL_AUTH = "com.xbta.cmd.tpl.shizuku"

    data class Result(val file: File, val pkg: String)

    /** 自动生成包名（表单留空时用） */
    fun autoPackage(): String = PackageRules.auto()

    /** 返回 null 表示合法 */
    fun validatePackage(pkg: String): String? = PackageRules.validate(pkg)

    /**
     * 阻塞调用，务必放在 IO 线程。
     * @param onStep 进度文案回调（会从子线程调用）
     */
    fun export(
        ctx: Context,
        spec: ExportSpec,
        onStep: (String) -> Unit
    ): Result = try {
        doExport(ctx, spec, onStep)
    } catch (t: Throwable) {
        CrashLog.record(ctx, "export-apk", t)
        throw t
    }

    private fun doExport(ctx: Context, spec: ExportSpec, onStep: (String) -> Unit): Result {
        val title = spec.title.trim().ifBlank { "Shell 命令" }.take(48)
        val pkg = spec.packageName.trim().ifBlank { autoPackage() }
        PackageRules.validate(pkg)?.let { error("包名校验未通过：$it") }
        if (spec.value.trim().isEmpty() && spec.type == ExportSpec.TYPE_SHELL) {
            error("命令内容为空")
        }

        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }

        onStep("读取模板")
        val raw = File(dir, "template.apk")
        ctx.assets.open("runner.apk").use { input ->
            raw.outputStream().use { out -> input.copyTo(out) }
        }

        onStep("生成图标")
        val photo = spec.iconPng?.let {
            runCatching {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }.getOrNull()
        }
        val icons = IconMaker.allSizes(spec.iconText, spec.type, photo)

        onStep("改写包名与内容")
        val entries = ApkKit.read(raw)
        val cfg = buildConfigJson(spec, title).toByteArray(Charsets.UTF_8)
        val rebuilt = ArrayList<ApkEntry>(entries.size)

        var manifestHits = 0
        for (e in entries) {
            val name = e.name
            when {
                name == "AndroidManifest.xml" -> {
                    val before = AxmlEditor.readStrings(e.data)
                    val edited = AxmlEditor.replaceStrings(
                        e.data,
                        mapOf(TPL_PKG to pkg, TPL_LABEL to title, TPL_AUTH to "$pkg.shizuku")
                    )
                    val after = AxmlEditor.readStrings(edited)
                    manifestHits = countHits(before, after)
                    rebuilt.add(ApkEntry(name, 8, edited))
                }

                name == "resources.arsc" -> rebuilt.add(
                    ApkEntry(name, 0, ArscEditor.setPackage(e.data, pkg))
                )

                name == "assets/config.json" -> rebuilt.add(ApkEntry(name, 8, cfg))

                isSignatureFile(name) -> Unit          // 模板自带的 v1 签名丢掉，后面统一重签
                name.startsWith("res/mipmap-anydpi") -> Unit  // 自适应图标 XML 会遮住 PNG，删掉

                isLauncherPng(name) -> {
                    val px = densityOf(name)
                    val png = if (px != null) icons[px] else icons[192]
                    if (png != null) rebuilt.add(ApkEntry(name, 0, png)) else rebuilt.add(e)
                }

                else -> rebuilt.add(e)
            }
        }
        if (manifestHits < 2) {
            error("模板 manifest 改写失败（只命中 $manifestHits 处），模板结构可能变了")
        }

        onStep("打包")
        val unsigned = File(dir, "unsigned.apk")
        ApkKit.write(rebuilt, unsigned)

        onStep("签名")
        val finalFile = File(dir, "ShellStore-${pkg.substringAfterLast('.')}.apk")
        sign(ctx, unsigned, finalFile)
        raw.delete()
        unsigned.delete()

        onStep("校验")
        verifyResult(finalFile, pkg, title, spec)

        onStep("完成")
        return Result(finalFile, pkg)
    }

    /** 读回产物做三道校验：zip 结构、manifest 包名/名称、arsc 包名 */
    private fun verifyResult(file: File, pkg: String, title: String, spec: ExportSpec) {
        ZipCheck.verify(file, minSize = 1024L)?.let { error("产物自检未通过：$it") }

        val manifest = ApkKit.entry(file, "AndroidManifest.xml")
            ?: error("产物里找不到 AndroidManifest.xml")
        val strings = AxmlEditor.readStrings(manifest)
        if (!strings.contains(pkg)) error("产物 manifest 里没有新包名 $pkg，改写失败")
        if (!strings.contains(title)) error("产物里没有应用名「$title」，名称替换失败")
        if (strings.any { it == TPL_LABEL || it == TPL_PKG || it == TPL_AUTH }) {
            error("产物里还残留模板占位符")
        }

        ApkKit.entry(file, "resources.arsc")?.let { arsc ->
            val arscPkg = ArscEditor.readPackage(arsc)
            if (arscPkg.isNotEmpty() && arscPkg != pkg) {
                error("resources.arsc 里的包名没改成功：$arscPkg")
            }
        }
        val cfg = ApkKit.entry(file, "assets/config.json")?.toString(Charsets.UTF_8)
            ?: error("产物里找不到 assets/config.json")
        if (!cfg.contains(spec.type)) error("配置文件写入异常")
    }

    private fun buildConfigJson(spec: ExportSpec, title: String): String {
        val o = JSONObject()
        o.put("title", title)
        o.put("type", spec.type)
        o.put("value", spec.value.trim())
        if (spec.action.isNotBlank()) o.put("action", spec.action.trim())
        if (spec.component.isNotBlank()) o.put("component", spec.component.trim())
        if (spec.data.isNotBlank()) o.put("data", spec.data.trim())
        if (spec.extras.isNotBlank()) o.put("extras", spec.extras)
        o.put("exec", spec.exec)
        o.put("showOutput", spec.showOutput)
        o.put("timeoutSec", spec.timeoutSec.coerceIn(1, 600))
        return o.toString(2)
    }

    private fun countHits(before: List<String>, after: List<String>): Int {
        var n = 0
        for (i in before.indices) {
            if (i < after.size && before[i] != after[i]) n++
        }
        return n
    }

    private fun isSignatureFile(name: String): Boolean {
        if (!name.startsWith("META-INF/")) return false
        val lower = name.lowercase()
        return lower.endsWith(".rsa") || lower.endsWith(".dsa") ||
            lower.endsWith(".ec") || lower.endsWith(".sf") || lower.endsWith(".mf")
    }

    /** 模板的启动图标 PNG（mipmap 下、非 .9、非 anydpi 的 png） */
    private fun isLauncherPng(name: String): Boolean =
        name.startsWith("res/mipmap") && name.endsWith(".png") && !name.contains("anydpi")

    private fun densityOf(name: String): Int? = when {
        name.contains("mipmap-mdpi") -> 48
        name.contains("mipmap-hdpi") -> 72
        name.contains("mipmap-xhdpi") && !name.contains("xxhdpi") -> 96
        name.contains("mipmap-xxhdpi") -> 144
        name.contains("mipmap-xxxhdpi") -> 192
        else -> null
    }

    // ---------------- 签名 ----------------

    private fun sign(ctx: Context, input: File, output: File) {
        val ks = KeyStore.getInstance("PKCS12")
        ctx.assets.open(KEYSTORE_ASSET).use { ks.load(it, KEYSTORE_PASS.toCharArray()) }
        val alias = if (ks.containsAlias(KEY_ALIAS)) KEY_ALIAS else ks.aliases().nextElement()
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

    // ---------------- 保存到用户选的目录（SAF） ----------------

    /**
     * 把导出好的 APK 复制一份到用户选的目录（SAF tree uri）。
     * 文件名带时间戳，避免覆盖同名的旧版本。成功返回给人看的路径，失败返回 null。
     *
     * 注意：发给系统安装器的仍然是缓存里那一份（FileProvider），
     * 这里保存的是「留档」副本 —— 有些 ROM 的安装器不认 SAF 的 content:// 地址。
     */
    fun saveToFolder(ctx: Context, file: File, treeUri: String, baseName: String): String? {
        if (treeUri.isBlank()) return null
        return runCatching {
            val tree = Uri.parse(treeUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val name = safeName(baseName) + "-" + stamp() + ".apk"
            val doc = DocumentsContract.createDocument(
                ctx.contentResolver, parent, "application/vnd.android.package-archive", name
            ) ?: return null
            ctx.contentResolver.openOutputStream(doc)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return null
            folderLabel(treeUri) + "/" + name
        }.getOrNull()
    }

    /** SAF 目录的展示名：primary:Download/foo → 内部存储/Download/foo */
    fun folderLabel(treeUri: String): String {
        if (treeUri.isBlank()) return ""
        return runCatching {
            val id = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
            val p = id.split(":", limit = 2)
            val root = if (p[0] == "primary") "内部存储" else p[0]
            if (p.size > 1 && p[1].isNotBlank()) "$root/${p[1].trim('/')}" else root
        }.getOrDefault("")
    }

    private fun safeName(s: String): String {
        val cleaned = s.trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_')
        return cleaned.ifBlank { "shellstore" }.take(40)
    }

    private fun stamp(): String {
        val tz = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        val f = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.CHINA)
        f.timeZone = tz
        return f.format(java.util.Date())
    }

    // ---------------- 安装 / 分享 ----------------

    fun installIntent(ctx: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareIntent(ctx: Context, file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
