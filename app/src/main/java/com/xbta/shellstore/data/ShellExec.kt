package com.xbta.shellstore.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * 命令执行通道。按「能直连就直连 → 有 Root 用 Root → 装了 Termux 就丢给 Termux」的顺序降级。
 *
 * 只读命令（getprop / dumpsys / wm size / df / ps 等）在 App 自身沙箱里就能跑出来，
 * 不需要任何提权；需要系统权限的（settings put、pm uninstall、svc 等）才走 Root。
 */
object ShellExec {

    enum class Channel(val label: String) {
        DIRECT("本机"),
        SHIZUKU("Shizuku"),
        ROOT("Root"),
        TERMUX("Termux")
    }

    data class Result(
        val channel: Channel,
        val code: Int,
        val out: String,
        val err: String,
        val note: String = ""
    ) {
        val ok: Boolean get() = code == 0
        val hasOutput: Boolean get() = out.isNotBlank() || err.isNotBlank()
    }

    @Volatile private var rootChecked = false
    @Volatile private var rootOk = false

    /** Root 检测会起一个 su 进程，结果缓存，避免每次点击都等一秒 */
    fun hasRoot(): Boolean {
        if (rootChecked) return rootOk
        synchronized(this) {
            if (!rootChecked) {
                rootOk = runCatching {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                    val text = p.inputStream.bufferedReader().readText()
                    p.waitFor(5, TimeUnit.SECONDS)
                    text.contains("uid=0")
                }.getOrDefault(false)
                rootChecked = true
            }
        }
        return rootOk
    }

    fun hasTermux(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo("com.termux", 0)
        true
    }.getOrDefault(false)

    /** 阻塞调用，务必放在 IO 线程 */
    fun run(ctx: Context, cmd: String, timeoutSec: Long = 60L): Result {
        val direct = exec(arrayOf("sh", "-c", cmd), timeoutSec)
        if (direct.code == 0) return direct

        // Shizuku 有授权时优先于 root：不必每次弹 su 授权框，权限等同 ADB shell
        if (ShizukuRunner.hasPermission()) {
            val s = runViaShizuku(cmd, timeoutSec)
            if (s.code == 0 || s.err.isNotBlank()) return s
        }

        if (hasRoot()) {
            val r = exec(arrayOf("su", "-c", cmd), timeoutSec)
            return r.copy(channel = Channel.ROOT)
        }

        if (looksLikePermissionIssue(direct) && hasTermux(ctx)) {
            val err = sendToTermux(ctx, cmd)
            if (err == null) {
                return Result(
                    Channel.TERMUX, 0, "", "",
                    "已交给 Termux 在后台执行。本应用拿不到它的输出，请打开 Termux 查看结果。"
                )
            }
            return direct.copy(note = err)
        }

        return direct.copy(note = hintFor(direct))
    }

    private fun looksLikePermissionIssue(r: Result): Boolean {
        val e = r.err.lowercase()
        return e.contains("permission denied") ||
            e.contains("operation not permitted") ||
            e.contains("not allowed") ||
            e.contains("requires permission") ||
            (r.out.isBlank() && r.err.isNotBlank())
    }

    private fun hintFor(r: Result): String = when {
        r.code == 124 -> "命令超时，可能卡在等待输入。"
        looksLikePermissionIssue(r) -> "这条命令需要系统权限：Root 或 Termux（并开启 allow-external-apps）任一可用时才能执行。"
        r.code != 0 && r.err.isBlank() && r.out.isBlank() -> "命令没有输出，返回码 ${r.code}。"
        else -> ""
    }

    /** 强制走 Root 执行（不先试直连），对应详情页的「Root 运行」 */
    fun runViaRoot(cmd: String, timeoutSec: Long = 60L): Result {
        if (!hasRoot()) {
            return Result(
                Channel.ROOT, -1, "", "没有可用的 Root",
                "设备没 root，或授权被拒绝。可以改用 Shizuku 运行，或复制到 Termux。"
            )
        }
        return exec(arrayOf("su", "-c", cmd), timeoutSec).copy(channel = Channel.ROOT)
    }

    /**
     * 强制走 Shizuku 执行（ADB 或 root 身份），对应详情页的「Shizuku 运行」。
     *
     * 注意：ShizukuRemoteProcess 的 stderr 是独立流，必须两个线程分别读，
     * 单线程顺序读会在缓冲区写满时卡死（实测过）。
     */
    fun runViaShizuku(cmd: String, timeoutSec: Long = 60L): Result {
        if (!ShizukuRunner.isRunning()) {
            return Result(
                Channel.SHIZUKU, -1, "", "Shizuku 服务没在运行",
                "先打开 Shizuku App 点「启动」（或用 adb 启动服务），再回来执行。"
            )
        }
        if (!ShizukuRunner.hasPermission()) {
            return Result(
                Channel.SHIZUKU, -1, "", "本应用还没获得 Shizuku 授权",
                "点一下会弹出授权请求，允许之后就能执行。"
            )
        }
        val process = runCatching {
            Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        }.getOrElse { e ->
            return Result(
                Channel.SHIZUKU, -1, "", e.message ?: e.javaClass.simpleName,
                "Shizuku 拒绝创建进程。"
            )
        }
        val out = StringBuilder()
        val err = StringBuilder()
        val tOut = Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
        }
        val tErr = Thread {
            runCatching { process.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
        }
        tOut.start()
        tErr.start()
        val finished = runCatching { process.waitForTimeout(timeoutSec, TimeUnit.SECONDS) }.getOrDefault(false)
        if (!finished) runCatching { process.destroy() }
        tOut.join(1500)
        tErr.join(1500)
        val code = runCatching { process.exitValue() }.getOrDefault(-1)
        return Result(
            Channel.SHIZUKU,
            if (finished) code else 124,
            out.toString().trim(),
            err.toString().trim(),
            if (finished) "" else "执行超时（${timeoutSec}s），已终止"
        )
    }

    private fun exec(cmd: Array<String>, timeoutSec: Long): Result {
        var proc: Process? = null
        return try {
            // 用局部 val 持有进程：var 的智能转换在 lambda 里不成立，会编译报错
            val process = Runtime.getRuntime().exec(cmd)
            proc = process
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = Thread {
                runCatching { process.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            }
            val tErr = Thread {
                runCatching { process.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
            }
            tOut.start()
            tErr.start()

            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                runCatching { process.destroy() }
                tOut.join(500)
                tErr.join(500)
                return Result(Channel.DIRECT, 124, out.toString().trim(), err.toString().trim())
            }
            tOut.join(1500)
            tErr.join(1500)
            val code = runCatching { process.exitValue() }.getOrDefault(-1)
            Result(Channel.DIRECT, code, out.toString().trim(), err.toString().trim())
        } catch (e: Exception) {
            Result(Channel.DIRECT, -1, "", (e.javaClass.simpleName + ": " + (e.message ?: "")).trim())
        } finally {
            runCatching { proc?.destroy() }
        }
    }

    /** 成功返回 null，失败返回原因 */
    private fun sendToTermux(ctx: Context, cmd: String): String? = try {
        val i = Intent().apply {
            action = "com.termux.RUN_COMMAND"
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        }
        ctx.startService(i)
        null
    } catch (e: SecurityException) {
        "Termux 拒绝了请求：请在 Termux 里执行「设置 → 允许外部应用调用」（allow-external-apps）后重试。"
    } catch (e: Exception) {
        "无法调用 Termux：${e.javaClass.simpleName}"
    }

    /** 打开 Termux 主界面，给用户一个手动粘贴的出口 */
    fun openTermux(ctx: Context): Boolean = runCatching {
        ctx.startActivity(Intent(Intent.ACTION_MAIN).apply {
            setClassName("com.termux", "com.termux.app.TermuxActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    }.getOrElse {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.termux"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
