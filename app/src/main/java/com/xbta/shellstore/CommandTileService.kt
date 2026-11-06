package com.xbta.shellstore

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xbta.shellstore.core.ExportSpec
import com.xbta.shellstore.data.ActionRunner
import com.xbta.shellstore.ui.EntryRunner

/**
 * 快捷设置磁贴：点一下执行「最后添加到磁贴的那条命令」。
 *
 * 为什么不支持「每条命令一个磁贴各自独立」：同一个 TileService 被多次添加时，
 * 系统给的是多个 tile 实例、但都指向同一个组件，拿不到「我是第几个实例」的标识，
 * 只能靠 label 猜，不可靠。所以这里只保存最近一次添加的命令，标签上写明是哪条，
 * 换命令就重新添加一次。
 *
 * 2.2.0 起保存的是完整 [ExportSpec]（名称 / 类型 / 详细内容 / 执行身份都可自定义），
 * 执行交给 [ActionRunner]；老数据（只有 entryId + cmd）继续走 [EntryRunner] 兼容。
 */
class CommandTileService : TileService() {

    companion object {
        private const val PREFS = "command_tile"

        fun save(ctx: Context, spec: ExportSpec, label: String, iconPath: String) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("type", spec.type)
                .putString("value", spec.value)
                .putString("action", spec.action)
                .putString("component", spec.component)
                .putString("data", spec.data)
                .putString("extras", spec.extras)
                .putString("exec", spec.exec)
                .putString("result", spec.resultMode)
                .putInt("timeout", spec.timeoutSec)
                .putString("label", label)
                .putString("icon", iconPath)
                .putLong("at", System.currentTimeMillis())
                .apply()
        }

        /** 新版规格；老数据（没有 type 字段）返回 null */
        fun loadSpec(ctx: Context): ExportSpec? {
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val type = sp.getString("type", null) ?: return null
            return ExportSpec(
                title = sp.getString("label", "").orEmpty().ifBlank { "Shell 命令" },
                type = type,
                value = sp.getString("value", "").orEmpty(),
                action = sp.getString("action", "").orEmpty(),
                component = sp.getString("component", "").orEmpty(),
                data = sp.getString("data", "").orEmpty(),
                extras = sp.getString("extras", "").orEmpty(),
                exec = sp.getString("exec", "").orEmpty(),
                timeoutSec = sp.getInt("timeout", 30),
                resultMode = sp.getString("result", null) ?: ExportSpec.RESULT_TOAST
            )
        }

        fun loadLabel(ctx: Context): String =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("label", "Shell 命令") ?: "Shell 命令"

        fun loadIconPath(ctx: Context): String =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("icon", "").orEmpty()

        /** 老数据：entryId/cmd，用于 2.2.0 之前的磁贴继续可用 */
        private fun loadLegacy(ctx: Context): Triple<String, String, String>? {
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cmd = sp.getString("cmd", null) ?: return null
            return Triple(
                sp.getString("entryId", "").orEmpty(),
                cmd,
                sp.getString("label", "命令") ?: "命令"
            )
        }

        /** 磁贴图标：用户选的/生成的那张，没有就用 App 图标 */
        private fun iconOf(ctx: Context): Icon {
            val path = loadIconPath(ctx)
            if (path.isNotBlank()) {
                val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                if (bmp != null) return Icon.createWithBitmap(bmp)
            }
            return Icon.createWithResource(ctx, R.mipmap.ic_launcher)
        }

        /** 让系统刷新磁贴的显示状态（改完命令后调用） */
        fun refresh(ctx: Context) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 24) {
                    requestListeningState(
                        ctx,
                        ComponentName(ctx, CommandTileService::class.java)
                    )
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val spec = loadSpec(this)
        val legacy = if (spec == null) loadLegacy(this) else null
        if (spec == null && legacy == null) {
            tile.state = Tile.STATE_UNAVAILABLE
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.icon = iconOf(this)
            tile.label = (spec?.title ?: legacy!!.third).take(12)
            if (Build.VERSION.SDK_INT >= 29) {
                tile.subtitle = runningText.ifBlank { "Shell 商店" }
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val spec = loadSpec(this)
        val legacy = if (spec == null) loadLegacy(this) else null
        if (spec == null && legacy == null) return
        Thread {
            val text = if (spec != null) {
                // 按用户选的「结果提示方式」走：磁贴场景没有界面，弹窗会自动降级为通知
                runCatching { ActionRunner.runAndReport(applicationContext, spec) }
                    .getOrElse { "执行失败：${it.message ?: it.javaClass.simpleName}" }
            } else {
                EntryRunner.run(applicationContext, legacy!!.first, legacy.second)
            }
            runCatching {
                Handler(Looper.getMainLooper()).post {
                    runningText = text
                    val tile = qsTile
                    if (tile != null) {
                        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = text.take(24)
                        tile.state = Tile.STATE_INACTIVE
                        tile.updateTile()
                    }
                }
            }
        }.start()
    }

    /** 上一次执行结果，onStartListening 复用，避免磁贴显示回退成「Shell 商店」 */
    private var runningText: String = ""
}
