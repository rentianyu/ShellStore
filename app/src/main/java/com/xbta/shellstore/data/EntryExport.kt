package com.xbta.shellstore.data

import com.xbta.shellstore.core.AdbIntent
import com.xbta.shellstore.core.CommandRenderer
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.core.ExportSpec

/**
 * 「条目 → 导出项」的默认值推导，详情页的导出对话框和 deep link 的
 * shortcut / tile / export-apk 动作共用这一份，避免两条路径算出不同结果
 * （之前详情页里是 private fun，外部调不到只能各写一套）。
 */
object EntryExport {

    fun typeOf(e: Entry): String = when (e.type) {
        EntryType.SHELL, EntryType.SCRIPT -> ExportSpec.TYPE_SHELL
        EntryType.URLSCHEME -> ExportSpec.TYPE_URL
        EntryType.ACTIVITY, EntryType.INTENT -> ExportSpec.TYPE_ACTIVITY
        EntryType.BROADCAST -> ExportSpec.TYPE_BROADCAST
        EntryType.SERVICE -> ExportSpec.TYPE_SERVICE
    }

    private fun extrasOf(e: Entry): String = e.intent?.extras
        ?.filter { it.key.isNotBlank() }
        ?.joinToString("\n") { "${it.key}=${it.value}" }
        .orEmpty()

    /** 按目标动作类型算出导出的默认参数（把一条 am 命令拆成 value/action/component/data） */
    fun defaults(entry: Entry, text: String, type: String = typeOf(entry)): AdbIntent.Normalized =
        AdbIntent.normalize(
            type = type,
            value = text,
            action = entry.intent?.action.orEmpty(),
            component = entry.intent?.component.orEmpty(),
            extras = extrasOf(entry),
            data = entry.intent?.data.orEmpty()
        )

    /**
     * 外部（deep link）一键导出用的完整 spec：不弹表单，全部按默认值，
     * 可用 query 参数覆盖 title / pkg / type / exec / result。
     */
    fun spec(
        entry: Entry,
        values: Map<String, String>,
        typeOverride: String? = null,
        titleOverride: String? = null,
        pkg: String = "",
        execOverride: String? = null,
        resultMode: String? = null
    ): ExportSpec {
        val type = typeOverride?.trim()?.takeIf { it.isNotEmpty() } ?: typeOf(entry)
        val text = CommandRenderer.primaryText(entry, values, -1)
        val n = defaults(entry, text, type)
        return ExportSpec(
            title = titleOverride?.trim()?.takeIf { it.isNotEmpty() } ?: entry.title,
            packageName = pkg.trim(),
            type = type,
            value = n.value,
            action = n.action,
            component = n.component,
            data = n.data,
            extras = n.extras,
            exec = execOverride?.trim()?.takeIf { it.isNotEmpty() }
                ?: if (type == ExportSpec.TYPE_SHELL) ExportSpec.EXEC_SHELL else "",
            resultMode = resultMode?.trim()?.takeIf { it.isNotEmpty() } ?: ExportSpec.RESULT_TOAST,
            showOutput = (resultMode ?: ExportSpec.RESULT_TOAST) != ExportSpec.RESULT_NONE
        )
    }
}
