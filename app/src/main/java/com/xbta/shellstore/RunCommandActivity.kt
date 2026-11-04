package com.xbta.shellstore

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.xbta.shellstore.data.ShellExec
import com.xbta.shellstore.data.Store

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cmd = intent?.getStringExtra(EXTRA_CMD)
        if (cmd.isNullOrBlank()) {
            finish()
            return
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID).orEmpty()

        Thread {
            runCatching { Store.init(applicationContext) }
            val r = ShellExec.run(applicationContext, cmd)
            if (entryId.isNotBlank()) runCatching { Store.recordRecent(entryId) }
            val msg = when {
                r.ok && r.hasOutput ->
                    "已执行：" + r.out.lineSequence().firstOrNull().orEmpty().take(60)
                r.ok -> "已执行" + if (title.isNotBlank()) "：$title" else ""
                else ->
                    "执行失败：" + r.err.ifBlank { r.note.ifBlank { "返回码 ${r.code}" } }.take(80)
            }
            runOnUiThread {
                runCatching { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
                finish()
            }
        }.start()
    }
}
