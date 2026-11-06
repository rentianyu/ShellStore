package com.xbta.shellstore

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.xbta.shellstore.data.ActionRunner
import com.xbta.shellstore.ui.EntryRunner

/**
 * 桌面快捷方式的执行入口。
 *
 * 透明、无界面：拿到规格 → 执行 → Toast 结果 → 结束自己。
 * 不做成页面，是因为从桌面点进来只想「跑一下就完」，多一层界面反而多一次跳转。
 *
 * 两种来源：
 *   1. 新版快捷方式（2.2.0 起）带完整 ExportSpec（名称/类型/详细内容/执行身份都可自定义）；
 *   2. 老版快捷方式只有 cmd + entryId，继续按条目类型分流（[EntryRunner]），升级不失效。
 *
 * 注意：进度与结果都在子线程里跑，只把 Toast 与 finish 切回主线程；
 * 这条路径上任何异常都不能让快捷方式卡在透明页面上（所以全程 runCatching）。
 */
class RunCommandActivity : Activity() {

    companion object {
        const val ACTION_RUN = "com.xbta.shellstore.action.RUN_CMD"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_VALUES = "values"
    }

    /** 参数值用 k=v 换行拼在 intent extra 里，省一个 JSON 解析依赖 */
    private fun parseIntentValues(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
            }
            .toMap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spec = ActionRunner.readSpec(intent)
        val cmd = intent?.getStringExtra(EXTRA_CMD)
        if (spec == null && cmd.isNullOrBlank()) {
            finish()
            return
        }
        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID).orEmpty()
        val values = parseIntentValues(intent?.getStringExtra(EXTRA_VALUES))

        Thread {
            if (spec != null) {
                // 新版：完全按用户在表单里配的类型、执行身份、结果提示方式走
                // 注意传 this（Activity）：选「弹窗」时需要 Activity 才能弹，
                // 交给应用上下文会被降级成通知
                runCatching {
                    ActionRunner.runAndReport(this, spec) {
                        runOnUiThread { runCatching { finish() } }
                    }
                }.onFailure { e ->
                    val msg = "执行失败：${e.message ?: e.javaClass.simpleName}"
                    runOnUiThread {
                        runCatching { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
                        finish()
                    }
                }
            } else {
                // 老版快捷方式（1.x 建的），继续按条目类型分流
                val msg = EntryRunner.run(applicationContext, entryId, cmd!!, values)
                runOnUiThread {
                    runCatching { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
                    finish()
                }
            }
        }.start()
    }
}
