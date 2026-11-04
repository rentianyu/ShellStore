package com.xbta.shellstore

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.xbta.shellstore.ui.EntryRunner

/**
 * 桌面快捷方式的执行入口。
 *
 * 透明、无界面：拿到命令 → 执行 → Toast 结果 → 结束自己。
 * 不做成页面，是因为从桌面点进来只想「跑一下就完」，多一层界面反而多一次跳转。
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
        val cmd = intent?.getStringExtra(EXTRA_CMD)
        if (cmd.isNullOrBlank()) {
            finish()
            return
        }
        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID).orEmpty()
        val values = parseIntentValues(intent?.getStringExtra(EXTRA_VALUES))

        Thread {
            // 按条目类型分流：scheme/intent 类发 Intent，shell 类走命令通道
            val msg = EntryRunner.run(applicationContext, entryId, cmd, values)
            runOnUiThread {
                runCatching { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
                finish()
            }
        }.start()
    }
}
