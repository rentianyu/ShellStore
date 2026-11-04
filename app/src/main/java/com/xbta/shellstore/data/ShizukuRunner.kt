package com.xbta.shellstore.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import rikka.shizuku.Shizuku

/**
 * Shizuku 的可用性判断与授权请求。
 *
 * 三层状态要分开看，UI 提示也因此不同：
 *  1. 装没装 Shizuku App（没装 → 引导去装）
 *  2. 服务起没起（pingBinder，没起 → 引导去启动）
 *  3. 授权没授权（checkSelfPermission，没授权 → 弹授权）
 *
 * 注意：binder 没存活时调用 Shizuku 的方法会抛 IllegalStateException，
 * 所以这里全部用 runCatching 兜住，返回值一律给保守结果。
 */
object ShizukuRunner {

    /** 授权结果回调用的 requestCode，与监听器里比对 */
    const val REQ_CODE = 0x5A17

    private const val PKG = "moe.shizuku.privileged.api"

    fun isInstalled(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(PKG, 0)
        true
    }.getOrDefault(false)

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        isRunning() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun uid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    /** 0 = root 启动的 Shizuku；2000 = ADB 启动的 Shizuku */
    fun privilegeLabel(): String = when {
        !isRunning() -> "未运行"
        uid() == 0 -> "root 身份"
        uid() == 2000 -> "ADB 身份"
        uid() < 0 -> "未知身份"
        else -> "uid ${uid()}"
    }

    /** 一条给用户看的当前状态，用在按钮下方的说明里 */
    fun statusText(ctx: Context): String = when {
        !isInstalled(ctx) -> "未安装"
        !isRunning() -> "未启动"
        !hasPermission() -> "待授权"
        else -> privilegeLabel()
    }

    fun requestPermission() {
        runCatching {
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(REQ_CODE)
        }
    }

    fun openApp(ctx: Context): Boolean = runCatching {
        val i = ctx.packageManager.getLaunchIntentForPackage(PKG)
            ?: return@runCatching false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    }.getOrDefault(false)

    /** 没装 Shizuku 时跳应用商店 */
    fun openStore(ctx: Context): Boolean = runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PKG"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}
