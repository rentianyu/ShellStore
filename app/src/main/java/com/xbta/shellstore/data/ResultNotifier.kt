package com.xbta.shellstore.data

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import com.xbta.shellstore.R
import com.xbta.shellstore.core.ExportSpec

/**
 * 执行结果的展示：Toast / 通知 / 弹窗 / 不提示。
 *
 * 三个出口（导出 APK、桌面快捷方式、控制中心磁贴）用的是同一组语义
 * （[ExportSpec.resultMode]），这里负责在「当前 App 内」的那两种场景落地。
 * 导出 APK 的执行器在独立模块里，有一份等价的实现（runner/RunActivity.java）。
 *
 * 两条降级规则（都是为了让用户不至于「点了没反应」）：
 *   1. 失败一定提示：选了「不提示」时失败仍发 Toast；
 *   2. 弹窗需要 Activity：磁贴/服务这类没有界面的场景降级为通知。
 */
object ResultNotifier {

    private const val CHANNEL_ID = "cmd_result"
    private const val CHANNEL_NAME = "命令执行结果"

    /**
     * @param onDone 展示动作结束后的回调（弹窗是关闭之后）。Activity 用它决定何时 finish
     */
    fun show(
        ctx: Context,
        spec: ExportSpec,
        message: String,
        ok: Boolean,
        onDone: (() -> Unit)? = null
    ) {
        val mode = resolveMode(ctx, spec, ok)
        // 统一切主线程：Toast / Dialog / Notification 都要求
        Handler(Looper.getMainLooper()).post {
            when (mode) {
                ExportSpec.RESULT_NONE -> onDone?.invoke()
                ExportSpec.RESULT_NOTIFICATION -> {
                    notify(ctx, spec, message)
                    onDone?.invoke()
                }
                ExportSpec.RESULT_DIALOG -> dialog(ctx, spec, message, onDone)
                else -> {
                    toast(ctx, message)
                    onDone?.invoke()
                }
            }
        }
    }

    /** 真正要用的展示方式（含降级） */
    private fun resolveMode(ctx: Context, spec: ExportSpec, ok: Boolean): String {
        val chosen = spec.resultMode.ifBlank { ExportSpec.RESULT_TOAST }
        // 选了「不提示」但执行失败：仍然给个可感知的提示
        if (chosen == ExportSpec.RESULT_NONE && !ok) return ExportSpec.RESULT_TOAST
        // 弹窗必须有 Activity，否则退成通知（磁贴场景）
        if (chosen == ExportSpec.RESULT_DIALOG && ctx !is Activity) return ExportSpec.RESULT_NOTIFICATION
        return chosen
    }

    fun toast(ctx: Context, message: String) {
        runCatching { Toast.makeText(ctx, message, Toast.LENGTH_LONG).show() }
    }

    /** 结果留在通知栏：长输出可以展开，随时回看 */
    fun notify(ctx: Context, spec: ExportSpec, message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // 没给通知权限就别静默失败，退成 Toast
            toast(ctx, message)
            return
        }
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26 &&
                nm.getNotificationChannel(CHANNEL_ID) == null
            ) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "命令与脚本的执行结果" }
                )
            }
            val title = spec.title.ifBlank { "Shell 命令" }
            val first = message.lineSequence().firstOrNull().orEmpty().trim()
            val n = Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(first.take(80))
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFY_ID, n)
        }.onFailure { toast(ctx, message) }
    }

    /** 弹窗：适合想直接复制输出的场景。关闭后调用 onDone（Activity 据此 finish） */
    private fun dialog(ctx: Context, spec: ExportSpec, message: String, onDone: (() -> Unit)?) {
        val act = ctx as? Activity ?: run {
            notify(ctx, spec, message)
            onDone?.invoke()
            return
        }
        runCatching {
            val tv = TextView(act).apply {
                text = message
                textSize = 13f
                setPadding(56, 40, 56, 24)
                setTextIsSelectable(true)
            }
            AlertDialog.Builder(act)
                .setTitle(spec.title.ifBlank { "执行结果" })
                .setView(tv)
                .setPositiveButton("复制") { _, _ ->
                    runCatching {
                        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("输出", message))
                        Toast.makeText(act, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    act.finish()
                }
                .setNegativeButton("关闭") { _, _ -> act.finish() }
                .setOnCancelListener { act.finish() }
                .show()
        }.onFailure {
            toast(ctx, message)
            onDone?.invoke()
        }
    }

    /** 同一条通知复用 id：不让通知栏堆满 */
    private const val NOTIFY_ID = 4201
}
