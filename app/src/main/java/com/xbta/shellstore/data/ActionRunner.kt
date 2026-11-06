package com.xbta.shellstore.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xbta.shellstore.core.ExportSpec

/**
 * 按一份「动作规格」（ExportSpec）在 App 内执行一次动作。
 *
 * ExportSpec 原本只服务于「导出成独立 App」，但桌面快捷方式与控制中心磁贴需要的
 * 恰恰是同一套东西：名称 / 图标 / 动作类型 / 详细内容 / 执行身份。三处共用一份规格，
 * 用户在哪配、配的都是同一组语义，不会出现「导出 APK 是打开链接、桌面图标却去跑 shell」。
 *
 * 执行分流：
 *   shell            → 按执行身份走 本机 / Root / Shizuku 命令通道
 *   url / activity / broadcast / service
 *                    → 默认直接发 Intent（最稳、最快，不经过 shell）
 *                      勾了执行身份则改用等价的 am 命令（给系统限制严的场景兜底）
 *
 * 返回值是给用户看的一句话（Toast / 磁贴副标题）。
 */
object ActionRunner {

    /** 执行并返回一句结果文案。不会抛异常。 */
    fun run(ctx: Context, spec: ExportSpec): String {
        val type = spec.type
        if (type == ExportSpec.TYPE_SHELL) {
            val cmd = spec.value.trim()
            if (cmd.isEmpty()) return "命令为空，请先在详情页填写"
            val r = when (spec.exec) {
                ExportSpec.EXEC_ROOT -> ShellExec.runViaRoot(cmd, spec.timeoutSec.toLong())
                ExportSpec.EXEC_SHIZUKU -> ShellExec.runViaShizuku(cmd, spec.timeoutSec.toLong())
                else -> ShellExec.run(cmd, spec.timeoutSec.toLong())
            }
            if (r.code == 0) {
                val first = r.out.lineSequence().firstOrNull().orEmpty().trim()
                return if (first.isNotBlank()) "已执行：${first.take(50)}" else "已执行"
            }
            val why = r.err.ifBlank { r.note }.ifBlank { "返回码 ${r.code}" }
            return "执行失败：${why.take(80)}"
        }

        // 非 shell：勾了执行身份就先试 am 命令，失败再退回直接发 Intent
        if (spec.exec.isNotBlank()) {
            val am = buildAmCommand(spec)
            if (am != null) {
                val r = runAsIdentity(ctx, spec.exec, am, spec.timeoutSec.toLong())
                if (r.code == 0) return "${typeLabel(spec.type)}成功（经 ${spec.exec} 执行 am 命令）"
            }
        }
        return fireIntent(ctx, spec)
    }

    /** 直接用系统接口发 Intent。返回错误文案，成功返回「xxx成功」 */
    fun fireIntent(ctx: Context, spec: ExportSpec): String {
        return try {
            val extras = parseExtras(spec.extras)
            val dataUri = spec.data.trim().ifBlank { if (spec.type == ExportSpec.TYPE_URL) spec.value.trim() else "" }
            when (spec.type) {
                ExportSpec.TYPE_URL -> {
                    val uri = dataUri.ifBlank { spec.value.trim() }
                    if (uri.isEmpty()) return "链接为空"
                    val it = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(it)
                    "打开成功"
                }
                ExportSpec.TYPE_BROADCAST -> {
                    val it = buildIntent(spec, extras)
                    ctx.sendBroadcast(it)
                    "发送成功"
                }
                ExportSpec.TYPE_SERVICE -> {
                    val it = buildIntent(spec, extras)
                    if (it.component == null) return "启动服务需要填写组件名（如 com.pkg/.Svc）"
                    ctx.startService(it)
                    "启动成功"
                }
                else -> { // activity，以及任何未知类型都按「启动界面」处理
                    val it = buildIntent(spec, extras)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(it)
                    "启动成功"
                }
            }
        } catch (e: Exception) {
            val hint = when {
                e is android.content.ActivityNotFoundException -> "系统里没有能处理它的应用"
                e is SecurityException -> "被系统拒绝（缺少权限或组件未导出）"
                else -> e.message ?: e.javaClass.simpleName
            }
            "$hint；可以勾一个执行身份改用 am 命令"
        }
    }

    /** 拼 Intent（不含 FLAG / 启动方式），broadcast 与 service 复用 */
    private fun buildIntent(spec: ExportSpec, extras: Map<String, String>): Intent {
        val it = Intent()
        val action = spec.action.trim()
        if (action.isNotEmpty()) it.action = action
        val comp = spec.component.trim()
        if (comp.isNotEmpty()) {
            val pkg = comp.substringBefore('/')
            val cls = comp.substringAfter('/', "")
            if (pkg.isNotEmpty()) {
                if (cls.isEmpty()) it.setPackage(pkg)
                else it.component = android.content.ComponentName(
                    pkg,
                    if (cls.startsWith(".")) pkg + cls else cls
                )
            }
        }
        val data = spec.data.trim()
        if (data.isNotEmpty()) it.data = Uri.parse(data)
        extras.forEach { (k, v) -> it.putExtra(k, v) }
        return it
    }

    /** 与「导出 APK」的 runner 完全一致的 am 命令 */
    fun buildAmCommand(spec: ExportSpec): String? {        val verb = when (spec.type) {
            ExportSpec.TYPE_BROADCAST -> "broadcast"
            ExportSpec.TYPE_SERVICE -> "startservice"
            ExportSpec.TYPE_ACTIVITY, ExportSpec.TYPE_URL -> "start"
            else -> return null
        }
        val sb = StringBuilder("am ").append(verb)
        val action = spec.action.trim()
        // am start 不带 -a 时会不可靠甚至直接拒绝，VIEW 类补上默认值
        if (action.isNotEmpty()) sb.append(" -a ").append(q(action))
        else if (spec.type == ExportSpec.TYPE_URL) sb.append(" -a ").append(q(Intent.ACTION_VIEW))
        val data = spec.data.trim().ifBlank { if (spec.type == ExportSpec.TYPE_URL) spec.value.trim() else "" }
        if (data.isNotEmpty()) sb.append(" -d ").append(q(data))
        val comp = spec.component.trim()
        if (comp.isNotEmpty()) sb.append(" -n ").append(q(comp))
        parseExtras(spec.extras).forEach { (k, v) ->
            sb.append(" --es ").append(q(k)).append(' ').append(q(v))
        }
        return sb.toString()
    }

    /** k=v 每行一个；值里允许出现 = */
    fun parseExtras(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        raw.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            val i = t.indexOf('=')
            if (i <= 0) return@forEach
            out[t.substring(0, i).trim()] = t.substring(i + 1).trim()
        }
        return out
    }

    private fun runAsIdentity(ctx: Context, exec: String, cmd: String, timeoutSec: Long): ShellExec.Result =
        when (exec) {
            ExportSpec.EXEC_ROOT -> ShellExec.runViaRoot(cmd, timeoutSec)
            ExportSpec.EXEC_SHIZUKU -> ShellExec.runViaShizuku(cmd, timeoutSec)
            else -> ShellExec.run(cmd, timeoutSec)
        }

    private fun typeLabel(t: String): String = when (t) {
        ExportSpec.TYPE_URL -> "打开"
        ExportSpec.TYPE_BROADCAST -> "发送"
        ExportSpec.TYPE_SERVICE -> "启动"
        else -> "启动"
    }

    // ---------------- 规格的 Intent 打包 / 解包 ----------------
    // 桌面快捷方式把它塞进启动 Intent，进程冷启动时再读回来。
    // 逐字段 putExtra 而不是序列化 JSON：少一个解析依赖，也不会被字段顺序坑到。

    private const val K_TITLE = "spec.title"
    private const val K_TYPE = "spec.type"
    private const val K_VALUE = "spec.value"
    private const val K_ACTION = "spec.action"
    private const val K_COMPONENT = "spec.component"
    private const val K_DATA = "spec.data"
    private const val K_EXTRAS = "spec.extras"
    private const val K_EXEC = "spec.exec"
    private const val K_TIMEOUT = "spec.timeout"

    fun writeSpec(intent: Intent, spec: ExportSpec) {
        intent.putExtra(K_TITLE, spec.title)
        intent.putExtra(K_TYPE, spec.type)
        intent.putExtra(K_VALUE, spec.value)
        intent.putExtra(K_ACTION, spec.action)
        intent.putExtra(K_COMPONENT, spec.component)
        intent.putExtra(K_DATA, spec.data)
        intent.putExtra(K_EXTRAS, spec.extras)
        intent.putExtra(K_EXEC, spec.exec)
        intent.putExtra(K_TIMEOUT, spec.timeoutSec)
    }

    /** 没带规格（旧版快捷方式）时返回 null，由调用方走兼容路径 */
    fun readSpec(intent: Intent?): ExportSpec? {
        if (intent == null) return null
        val type = intent.getStringExtra(K_TYPE) ?: return null
        return ExportSpec(
            title = intent.getStringExtra(K_TITLE).orEmpty().ifBlank { "Shell 命令" },
            type = type,
            value = intent.getStringExtra(K_VALUE).orEmpty(),
            action = intent.getStringExtra(K_ACTION).orEmpty(),
            component = intent.getStringExtra(K_COMPONENT).orEmpty(),
            data = intent.getStringExtra(K_DATA).orEmpty(),
            extras = intent.getStringExtra(K_EXTRAS).orEmpty(),
            exec = intent.getStringExtra(K_EXEC).orEmpty(),
            timeoutSec = intent.getIntExtra(K_TIMEOUT, 30)
        )
    }

    /** shell 引号白名单：只有确定安全的字符不进引号，其余一律单引号包裹 */
    private fun q(s: String): String {
        val safe = s.all { it.isLetterOrDigit() || it in "-_.:/:@=%+," }
        if (safe && s.isNotEmpty()) return s
        return "'" + s.replace("'", "'\\''") + "'"
    }
}
