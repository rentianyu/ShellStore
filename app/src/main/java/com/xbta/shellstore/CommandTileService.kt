package com.xbta.shellstore

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xbta.shellstore.data.ShellExec
import android.os.Handler
import android.os.Looper

/**
 * 快捷设置磁贴：点一下执行「最后添加到磁贴的那条命令」。
 *
 * 为什么不支持「每条命令一个磁贴各自独立」：同一个 TileService 被多次添加时，
 * 系统给的是多个 tile 实例、但都指向同一个组件，拿不到「我是第几个实例」的标识，
 * 只能靠 label 猜，不可靠。所以这里只保存最近一次添加的命令，标签上写明是哪条，
 * 换命令就重新添加一次。
 */
class CommandTileService : TileService() {

    companion object {
        private const val PREFS = "command_tile"

        fun save(ctx: Context, cmd: String, label: String) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("cmd", cmd)
                .putString("label", label)
                .putLong("at", System.currentTimeMillis())
                .apply()
        }

        fun load(ctx: Context): Pair<String, String>? {
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cmd = sp.getString("cmd", null) ?: return null
            return cmd to (sp.getString("label", "命令") ?: "命令")
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
        val t = load(this)
        val tile = qsTile ?: return
        if (t == null) {
            tile.state = Tile.STATE_UNAVAILABLE
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = t.second.take(12)
            if (Build.VERSION.SDK_INT >= 29) tile.subtitle = "Shell 商店"
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val t = load(this) ?: return
        Thread {
            val r = ShellExec.run(applicationContext, t.first)
            runCatching {
                val text = when {
                    r.ok && r.hasOutput -> r.out.lineSequence().firstOrNull().orEmpty().take(40)
                    r.ok -> "成功"
                    else -> "失败"
                }
                Handler(Looper.getMainLooper()).post {
                    runningText = text
                    val tile = qsTile
                    if (tile != null) {
                        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = text
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
