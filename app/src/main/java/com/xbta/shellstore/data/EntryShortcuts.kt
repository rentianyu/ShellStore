package com.xbta.shellstore.data

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.xbta.shellstore.CommandTileService
import com.xbta.shellstore.R
import com.xbta.shellstore.RunCommandActivity

/**
 * 把一条命令「送出去」的两个入口：桌面快捷方式、快捷设置磁贴。
 *
 * 两者都是「点一下直接执行」，不需要先打开 App，所以这里只负责注册/发起请求，
 * 真正的执行逻辑分别在 RunCommandActivity 和 CommandTileService 里。
 */
object EntryShortcuts {

    /** 添加到桌面。返回 null 表示已发起（系统会弹「添加到主屏幕」确认框） */
    fun pinToHome(
        ctx: Context,
        entryId: String,
        title: String,
        cmd: String,
        values: Map<String, String> = emptyMap()
    ): String? {
        val mgr = runCatching { ctx.getSystemService(ShortcutManager::class.java) }.getOrNull()
            ?: return "当前系统不支持桌面快捷方式"
        if (!mgr.isRequestPinShortcutSupported) return "这个桌面不支持固定快捷方式"

        val intent = Intent(ctx, RunCommandActivity::class.java).apply {
            action = RunCommandActivity.ACTION_RUN
            putExtra(RunCommandActivity.EXTRA_CMD, cmd)
            putExtra(RunCommandActivity.EXTRA_TITLE, title)
            putExtra(RunCommandActivity.EXTRA_ENTRY_ID, entryId)
            // 带上用户在详情页填的参数值：k=v 逐行拼，空值不写
            putExtra(
                RunCommandActivity.EXTRA_VALUES,
                values.filterValues { it.isNotBlank() }
                    .entries.joinToString("\n") { it.key + "=" + it.value }
            )
        }
        // id 里带时间戳：同一条命令可以放多个快捷方式，互相不冲突
        val info = ShortcutInfo.Builder(ctx, "cmd-$entryId-${System.currentTimeMillis()}")
            .setShortLabel(title.take(10))
            .setLongLabel(title.take(30))
            .setIcon(Icon.createWithResource(ctx, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        return runCatching {
            if (mgr.requestPinShortcut(info, null)) null else "系统拒绝了固定请求"
        }.getOrElse { it.message ?: "固定失败" }
    }

    /** 添加到控制中心磁贴。返回 null 表示已发起；返回文案表示「已保存但需要手动添加」或失败 */
    fun addTile(ctx: Context, entryId: String, label: String, cmd: String): String? {
        // 先落盘：即使系统不让主动添加，磁贴被手动加上后也能立刻用
        CommandTileService.save(ctx, entryId, cmd, label)
        CommandTileService.refresh(ctx)

        if (Build.VERSION.SDK_INT < 33) {
            return "已保存这条命令。Android 13 以下不能由 App 主动添加磁贴，" +
                "请下拉控制中心 → 编辑 → 添加「Shell 命令」"
        }
        val sbm = runCatching { ctx.getSystemService(StatusBarManager::class.java) }.getOrNull()
            ?: return "系统不支持磁贴"
        return runCatching {
            sbm.requestAddTileService(
                ComponentName(ctx, CommandTileService::class.java),
                label.take(12),
                Icon.createWithResource(ctx, R.mipmap.ic_launcher),
                ctx.mainExecutor
            ) { result ->
                android.util.Log.i("ShellStore", "requestAddTileService result=$result")
            }
            null
        }.getOrElse { it.message ?: "添加失败" }
    }
}
