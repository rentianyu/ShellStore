package com.xbta.shellstore.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xbta.shellstore.core.ZipCheck
import java.io.File

/**
 * 下载回来的更新包校验。
 *
 * 分四级，越往后越能拦住「装的时候才报错」的情况：
 *   1. zip 结构完整（ZipCheck：体积 / EOCD / 逐条目 CRC / 含 manifest）
 *   2. 系统能真正解析出包信息（PackageManager.getPackageArchiveInfo）
 *   3. 包里的 versionName 与远端 tag 对得上（防代理返回旧包）
 *   4. 签名证书与本机已装版本一致（不一致系统会拒装）
 *
 * 3、4 在拿不到数据时按「放行」处理（软失败），宁可放过也不误拦正常包。
 */
object ApkVerify {

    /**
     * 校验结论。区分「可重试」与「确定性失败」很关键：
     *
     *   · Retryable —— 文件没下全 / 代理给了旧包，换线路再下一次有意义；
     *   · Fatal —— 签名与本机不一致这类问题，同一个 tag 换 11 条线路下的还是同一个包，
     *     以前会白下 20 多遍才报错，现在第一遍就该停。
     */
    sealed interface Verdict {
        data object Ok : Verdict
        data class Retryable(val why: String) : Verdict
        data class Fatal(val why: String) : Verdict
    }

    fun verdict(ctx: Context, f: File, expectVersion: String = ""): Verdict {
        ZipCheck.verify(f)?.let { return Verdict.Retryable(it) }

        val pm = ctx.packageManager
        val info = runCatching {
            pm.getPackageArchiveInfo(f.absolutePath, archiveFlags())
        }.getOrNull() ?: return Verdict.Retryable("无法解析安装包（文件损坏或不是 APK）")

        if (info.packageName.isNullOrBlank()) return Verdict.Retryable("安装包缺少包名信息")

        if (expectVersion.isNotBlank()) {
            val got = info.versionName?.removePrefix("v").orEmpty()
            if (got.isNotBlank() && !sameVersion(got, expectVersion)) {
                return Verdict.Retryable("下载到的版本是 $got，与目标 $expectVersion 不符")
            }
        }

        return signerProblem(ctx, f)?.let { Verdict.Fatal(it) } ?: Verdict.Ok
    }

    /** 通过返回 null，失败返回可读原因 */
    fun check(ctx: Context, f: File, expectVersion: String = ""): String? =
        when (val v = verdict(ctx, f, expectVersion)) {
            Verdict.Ok -> null
            is Verdict.Retryable -> v.why
            is Verdict.Fatal -> v.why
        }

    /** 安装包签名是否与本机已装版本一致；无法判断时返回 null（不阻断） */
    private fun signerProblem(ctx: Context, f: File): String? {
        val apk = runCatching {
            ctx.packageManager.getPackageArchiveInfo(f.absolutePath, archiveFlags())
        }.getOrNull() ?: return null
        val mine = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, archiveFlags())
        }.getOrNull() ?: return null
        val a = signatures(mine)
        val b = signatures(apk)
        if (a.isEmpty() || b.isEmpty()) return null
        val same = a.any { x -> b.any { y -> x.contentEquals(y) } }
        return if (same) null else "安装包签名与本机版本不一致，系统会拒绝安装"
    }

    @Suppress("DEPRECATION")
    private fun archiveFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun signatures(pi: PackageInfo): List<ByteArray> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            pi.signatures?.map { it.toByteArray() }.orEmpty()
        }
    }.getOrDefault(emptyList())

    private fun sameVersion(a: String, b: String): Boolean {
        fun parts(v: String) = v.trim().split('.', '-', '+')
            .map { s -> s.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            if (x.getOrElse(i) { 0 } != y.getOrElse(i) { 0 }) return false
        }
        return true
    }
}
