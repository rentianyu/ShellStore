package com.xbta.shellstore.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xbta.shellstore.core.CommandRenderer
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.data.ShellExec
import com.xbta.shellstore.data.Store

/**
 * 「从 App 外面执行一条条目」的统一入口：桌面快捷方式、控制中心磁贴都走这里。
 *
 * 关键：必须按条目类型分流，不能一律当 shell 命令跑。
 * url scheme / activity / intent / broadcast / service 要发 Intent —— 否则会把
 * `openapp.jdmobile://...` 当成命令丢给 sh，必然报「执行失败」；只有 shell / script
 * 才走命令通道（本机 → Shizuku → root → Termux）。
 *
 * 返回值是给用户看的一句话（Toast / 磁贴副标题）。
 */
object EntryRunner {

    /**
     * @param valuesOverride 用户在详情页填过的参数值（快捷方式/磁贴会带过来），
     *                       为空时用条目自带默认值
     */
    fun run(
        ctx: Context,
        entryId: String,
        fallbackCmd: String,
        valuesOverride: Map<String, String> = emptyMap()
    ): String {
        // 快捷方式与磁贴都可能是冷启动（进程新建），要先把 Store 里那份条目读回来
        runCatching { Store.init(ctx) }
        val entry = Store.entries.value.firstOrNull { it.id == entryId }
        if (entry == null) return runRaw(ctx, fallbackCmd)

        val values = if (valuesOverride.isNotEmpty()) valuesOverride
        else entry.params.associate { it.key to it.default }
        Store.recordRecent(entry.id)

        return when (entry.type) {
            EntryType.SHELL, EntryType.SCRIPT -> {
                val cmd = CommandRenderer.primaryText(entry, values, -1)
                val r = ShellExec.run(ctx, cmd)
                if (r.ok) {
                    val first = r.out.lineSequence().firstOrNull().orEmpty().trim()
                    if (first.isNotBlank()) "已执行：${first.take(50)}" else "已执行"
                } else {
                    "执行失败：" + r.err.ifBlank { r.note.ifBlank { "返回码 ${r.code}" } }.take(80)
                }
            }
            else -> {
                // newTask：磁贴是 Service 上下文，不加会抛 AndroidRuntimeException
                val err = IntentLauncher.trigger(ctx, entry, values, newTask = true)
                if (err != null) err else IntentLauncher.actionLabel(entry) + "成功"
            }
        }
    }

    /** 条目已从源里消失（换源 / 源更新删了这条）时的兜底：按字符串长相猜 */
    private fun runRaw(ctx: Context, cmd: String): String {
        if (cmd.isBlank()) return "条目已失效，请重新添加"
        if (cmd.contains("://")) {
            return runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(cmd))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "已打开"
            }.getOrElse { "条目已失效，且没有应用能处理这个链接" }
        }
        val r = ShellExec.run(ctx, cmd)
        return if (r.ok) "已执行"
        else "执行失败：" + r.err.ifBlank { r.note.ifBlank { "返回码 ${r.code}" } }.take(80)
    }
}
