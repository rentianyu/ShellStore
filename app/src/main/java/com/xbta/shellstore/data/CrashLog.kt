package com.xbta.shellstore.data

import android.content.Context
import android.os.Build
import com.xbta.shellstore.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃现场记录。
 *
 * App 没有接任何第三方崩溃收集（零依赖原则），真机上闪退时拿不到堆栈，
 * 只能靠猜。这里把未捕获异常写到 filesDir 下的 crash 目录（每份一个 txt），保留最近 5 份，
 * 「我的 › 关于 › 崩溃日志」可直接查看并复制。
 *
 * 注意：只捕获 Java/Kotlin 未捕获异常；native 崩溃（SIGSEGV）与 OOM 被系统
 * 直接杀进程的情况抓不到。写文件本身要 runCatching 包住，不能让 handler 再抛。
 */
object CrashLog {

    private const val MAX_FILES = 5
    private const val DIR = "crash"

    private fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }

    /** 在 Application/Activity 最早的时机调用一次 */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(app, t, e) }
            // 交回系统默认处理（或自己杀掉进程），不要吞掉异常让 App 处于半死状态
            if (prev != null) prev.uncaughtException(t, e)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun write(ctx: Context, t: Thread, e: Throwable) {
        val now = Date()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(now)
        val f = File(dir(ctx), "crash-$stamp.txt")
        val sb = StringBuilder()
        sb.appendLine("time=" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(now))
        sb.appendLine("version=" + BuildConfig.APP_VERSION + " (" + BuildConfig.APP_VERSION_CODE + ")")
        runCatching {
            sb.appendLine("entries=" + Store.entries.value.size +
                " categories=" + Store.categories.value.size +
                " sources=" + Store.sources.value.size +
                " enabledSources=" + Store.sources.value.count { it.enabled })
            sb.appendLine("sources=" + Store.sources.value.joinToString("; ") { "${it.name}#${it.id}${if (it.enabled) "" else "(off)"}" })
        }
        sb.appendLine("android=" + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT +
            ") device=" + Build.MANUFACTURER + " " + Build.MODEL)
        sb.appendLine("thread=" + t.name)
        sb.appendLine("---")
        sb.append(e.stackTraceToString())
        f.writeText(sb.toString())
        // 只留最近 MAX_FILES 份
        dir(ctx).listFiles()
            ?.filter { it.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    fun all(ctx: Context): List<File> =
        dir(ctx).listFiles()
            ?.filter { it.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun latest(ctx: Context): File? = all(ctx).firstOrNull()

    fun read(ctx: Context): String =
        latest(ctx)?.let { runCatching { it.readText() }.getOrNull() }.orEmpty()

    fun clear(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
