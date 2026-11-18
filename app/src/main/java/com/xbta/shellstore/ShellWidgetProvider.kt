package com.xbta.shellstore

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.xbta.shellstore.core.CommandRenderer
import com.xbta.shellstore.data.Store

/**
 * 桌面小部件：一个格子里绑一条命令，点一下就跑。
 *
 * 绑定关系由 [WidgetConfigActivity]（添加小部件时）或 App 内「桌面小部件」页写入，
 * 存的是 widgetId → entryId；点击时直接把 entryId 交给已有的 [RunCommandActivity]，
 * 那条透明执行通道已经处理了类型分流、参数默认值与结果提示。
 */
class ShellWidgetProvider : AppWidgetProvider() {

    companion object {
        fun component(ctx: Context) = ComponentName(ctx, ShellWidgetProvider::class.java)

        /** 当前桌面上的全部小部件 id */
        fun ids(ctx: Context): IntArray =
            runCatching {
                AppWidgetManager.getInstance(ctx).getAppWidgetIds(component(ctx)) ?: IntArray(0)
            }.getOrDefault(IntArray(0))

        fun update(ctx: Context, id: Int) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(ctx)
                mgr.updateAppWidget(id, buildViews(ctx, id))
            }
        }

        fun updateAll(ctx: Context) {
            ids(ctx).forEach { update(ctx, it) }
        }

        private fun buildViews(ctx: Context, id: Int): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_shell)
            // 小部件可能在 Store 初始化之前被系统刷新，这里兜底一次
            runCatching { Store.init(ctx) }
            val entryId = Store.settings.value.widgetEntries[id.toString()].orEmpty()
            val entry = if (entryId.isBlank()) null else Store.entries.value.firstOrNull { it.id == entryId }

            if (entry == null) {
                views.setTextViewText(R.id.widget_title, if (entryId.isBlank()) "未指定命令" else "命令已失效")
                views.setTextViewText(
                    R.id.widget_cmd,
                    if (entryId.isBlank()) "点这里选择" else entryId
                )
                views.setTextViewText(R.id.widget_hint, if (entryId.isBlank()) "点击选择" else "点击重新选择")
            } else {
                views.setTextViewText(R.id.widget_title, entry.title)
                val cmd = runCatching {
                    CommandRenderer.primaryText(entry, entry.params.associate { it.key to it.default }, -1)
                }.getOrElse { entry.body }
                views.setTextViewText(R.id.widget_cmd, cmd.lineSequence().firstOrNull().orEmpty())
                views.setTextViewText(R.id.widget_hint, "点击执行")
            }

            views.setOnClickPendingIntent(R.id.widget_root, clickIntent(ctx, id, entryId))
            return views
        }

        private fun clickIntent(ctx: Context, id: Int, entryId: String): PendingIntent {
            // 未指定（或条目失效）→ 打开 App 里的小部件设置页去选
            if (entryId.isBlank()) {
                val open = Intent(ctx, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = android.net.Uri.parse("shellstore://widgets")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                return PendingIntent.getActivity(
                    ctx, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val run = Intent(ctx, RunCommandActivity::class.java).apply {
                action = RunCommandActivity.ACTION_RUN
                putExtra(RunCommandActivity.EXTRA_ENTRY_ID, entryId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(
                ctx, id, run,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, buildViews(ctx, it)) }
    }

    /** 小部件被删掉时，顺手把绑定清掉，避免设置里堆无用项 */
    override fun onDeleted(ctx: Context, ids: IntArray) {
        super.onDeleted(ctx, ids)
        val cur = Store.settings.value.widgetEntries.toMutableMap()
        var changed = false
        ids.forEach { id ->
            if (cur.remove(id.toString()) != null) changed = true
        }
        if (changed) Store.updateSettings(Store.settings.value.copy(widgetEntries = cur))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        // 条目/设置变化后由 App 主动刷新一次（见 Store.updateSettings 的调用方不需要关心，这里只兜系统广播）
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) updateAll(ctx)
    }
}
