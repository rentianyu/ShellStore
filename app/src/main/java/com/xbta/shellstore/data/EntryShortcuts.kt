package com.xbta.shellstore.data

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import com.xbta.shellstore.CommandTileService
import com.xbta.shellstore.R
import com.xbta.shellstore.RunCommandActivity
import com.xbta.shellstore.core.ExportSpec
import java.io.File

/**
 * 把一条命令「送出去」的两个入口：桌面快捷方式、快捷设置磁贴。
 *
 * 两者都吃同一份 [ExportSpec]——和「导出 APK」用的是同一个规格对象，
 * 所以名称 / 图标 / 动作类型 / 详细内容 / 执行身份在三个出口语义完全一致。
 *
 * 真正的执行逻辑在 [ActionRunner]（RunCommandActivity 与 CommandTileService 都调它）。
 */
object EntryShortcuts {

    /** 图标最长边（快捷方式与磁贴都按这个尺寸生成，省内存也够清晰） */
    private const val ICON_PX = 192

    /** 按规格算出一张图标；用户选了图就用用户的，否则用图标文字生成 */
    fun iconFor(spec: ExportSpec): Bitmap? {
        val bytes = runCatching {
            val photo = spec.iconPng
                ?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
            val text = spec.iconText.ifBlank { spec.title.take(1) }
            IconMaker.renderText(text, spec.type, ICON_PX).let { plain ->
                if (photo != null) IconMaker.renderPhoto(photo, ICON_PX) else plain
            }
        }.getOrNull()
        return bytes?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }
    }

    /** 添加到桌面。返回 null 表示已发起（系统会弹「添加到主屏幕」确认框） */
    fun pinToHome(ctx: Context, spec: ExportSpec, icon: Bitmap? = null): String? {
        val mgr = runCatching { ctx.getSystemService(ShortcutManager::class.java) }.getOrNull()
            ?: return "当前系统不支持桌面快捷方式"
        if (!mgr.isRequestPinShortcutSupported) return "这个桌面不支持固定快捷方式"

        val title = spec.title.ifBlank { "Shell 命令" }
        val intent = Intent(ctx, RunCommandActivity::class.java).apply {
            action = RunCommandActivity.ACTION_RUN
            ActionRunner.writeSpec(this, spec)
        }
        // id 里带时间戳：同一条命令可以放多个快捷方式，互相不冲突
        val info = ShortcutInfo.Builder(ctx, "cmd-${System.currentTimeMillis()}")
            .setShortLabel(title.take(10))
            .setLongLabel(title.take(30))
            .setIcon(
                if (icon != null) Icon.createWithBitmap(icon)
                else Icon.createWithResource(ctx, R.mipmap.ic_launcher)
            )
            .setIntent(intent)
            .build()
        return runCatching {
            if (mgr.requestPinShortcut(info, null)) null else "系统拒绝了固定请求"
        }.getOrElse { it.message ?: "固定失败" }
    }

    /** 添加到控制中心磁贴。返回 null 表示已发起；返回文案表示「已保存但需要手动添加」或失败 */
    fun addTile(ctx: Context, spec: ExportSpec, icon: Bitmap? = null): String? {
        val title = spec.title.ifBlank { "Shell 命令" }
        // 图标落到 filesDir：磁贴每次 onStartListening 都要重设，存文件比存 base64 省事
        val iconPath = runCatching {
            val bmp = icon ?: return@runCatching ""
            val f = File(ctx.filesDir, "tile_icon.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f.absolutePath
        }.getOrDefault("")

        // 先落盘：即使系统不让主动添加，磁贴被手动加上后也能立刻用
        CommandTileService.save(ctx, spec, title, iconPath)
        CommandTileService.refresh(ctx)

        if (Build.VERSION.SDK_INT < 33) {
            return "已保存「$title」。Android 13 以下不能由 App 主动添加磁贴，" +
                "请下拉控制中心 → 编辑 → 添加「Shell 命令」"
        }
        val sbm = runCatching { ctx.getSystemService(StatusBarManager::class.java) }.getOrNull()
            ?: return "系统不支持磁贴"
        return runCatching {
            sbm.requestAddTileService(
                ComponentName(ctx, CommandTileService::class.java),
                title.take(12),
                if (icon != null) Icon.createWithBitmap(icon)
                else Icon.createWithResource(ctx, R.mipmap.ic_launcher),
                ctx.mainExecutor
            ) { result ->
                android.util.Log.i("ShellStore", "requestAddTileService result=$result")
            }
            null
        }.getOrElse { it.message ?: "添加失败" }
    }
}
