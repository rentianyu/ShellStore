package com.xbta.shellstore.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xbta.shellstore.core.CommandRenderer
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.core.ExtraType

/**
 * 把条目转成真实 Intent 并触发。
 * activity / urlscheme / intent → startActivity
 * broadcast → sendBroadcast（运行时接收者收得到；manifest 接收者受 Android 8 隐式广播限制）
 * service → startService（App 在前台时可用，后台启动会被系统拒绝）
 */
object IntentLauncher {

    private val OPENABLE = setOf(EntryType.ACTIVITY, EntryType.URLSCHEME, EntryType.INTENT)
    private val TRIGGERABLE = setOf(
        EntryType.ACTIVITY, EntryType.URLSCHEME, EntryType.INTENT,
        EntryType.BROADCAST, EntryType.SERVICE
    )

    /** 这个条目能不能一键触发（打开 / 发送 / 启动） */
    fun canTrigger(entry: Entry): Boolean {
        if (entry.type !in TRIGGERABLE) return false
        if (entry.type == EntryType.URLSCHEME) return entry.body.isNotBlank() || entry.intent != null
        val spec = entry.intent ?: return false
        return when (entry.type) {
            EntryType.BROADCAST -> spec.action.isNotBlank() || spec.component.isNotBlank()
            EntryType.SERVICE -> spec.action.isNotBlank() || spec.component.isNotBlank()
            else -> spec.isActionable
        }
    }

    /** 按钮文案，按类型区分动作语义 */
    fun actionLabel(entry: Entry): String = when (entry.type) {
        EntryType.BROADCAST -> "发送广播"
        EntryType.SERVICE -> "启动服务"
        else -> "打开"
    }

    /** 返回 null 表示成功，否则返回给用户看的原因 */
    fun trigger(ctx: Context, entry: Entry, values: Map<String, String>): String? = when (entry.type) {
        EntryType.BROADCAST -> sendBroadcast(ctx, entry, values)
        EntryType.SERVICE -> startService(ctx, entry, values)
        else -> open(ctx, entry, values)
    }

    private fun sendBroadcast(ctx: Context, entry: Entry, values: Map<String, String>): String? {
        val intent = build(ctx, entry, values) ?: return "这个条目没有可用的 action / component"
        return try {
            ctx.sendBroadcast(intent)
            null
        } catch (e: SecurityException) {
            "系统拒绝了这次广播（缺少发送权限）"
        } catch (e: Exception) {
            "发送失败：${e.javaClass.simpleName}"
        }
    }

    private fun startService(ctx: Context, entry: Entry, values: Map<String, String>): String? {
        val intent = build(ctx, entry, values) ?: return "这个条目没有可用的 action / component"
        if (intent.component == null && intent.action.isNullOrBlank()) return "启动服务至少要 action 或 component"
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                // App 在前台时走 startService；系统认为是后台启动会抛 IllegalStateException
                ctx.startService(intent)
            } else {
                ctx.startService(intent)
            }
            null
        } catch (e: SecurityException) {
            "没有启动该服务的权限（多数系统服务不对第三方开放）"
        } catch (e: IllegalStateException) {
            "系统拒绝了本次启动（服务不允许从应用侧直接启动）"
        } catch (e: Exception) {
            "启动失败：${e.javaClass.simpleName}"
        }
    }

    /** 返回 null 表示成功，否则返回给用户看的原因 */
    fun open(ctx: Context, entry: Entry, values: Map<String, String>): String? {
        val intent = build(ctx, entry, values) ?: return "这个条目没有可用的 Intent 字段"
        return try {
            ctx.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "没有应用能响应该 Intent（目标可能未安装）"
        } catch (e: SecurityException) {
            "权限不足，系统拒绝启动"
        } catch (e: Exception) {
            "打开失败：${e.javaClass.simpleName}"
        }
    }

    fun build(ctx: Context, entry: Entry, values: Map<String, String>): Intent? {
        val spec = entry.intent

        // urlscheme：直接 VIEW，body 就是 URI
        if (entry.type == EntryType.URLSCHEME) {
            val raw = CommandRenderer.fill(entry.body, entry, values)
            if (raw.isBlank()) return null
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(raw))
            spec?.packageName?.takeIf { it.isNotBlank() }?.let { i.setPackage(it) }
            spec?.flags?.forEach { addFlag(i, it) }
            return i
        }

        if (spec == null) return null
        if (!spec.isActionable) return null

        val i = Intent()
        if (spec.action.isNotBlank()) i.action = spec.action

        val data = CommandRenderer.fill(spec.data, entry, values)
        if (data.isNotBlank() && spec.mime.isNotBlank()) {
            i.setDataAndType(Uri.parse(data), spec.mime)
        } else if (data.isNotBlank()) {
            i.data = Uri.parse(data)
        } else if (spec.mime.isNotBlank()) {
            i.type = spec.mime
        }

        if (spec.component.isNotBlank()) {
            val norm = CommandRenderer.normalizeComponent(spec.component, spec.packageName)
            if (norm.contains('/')) {
                val pkg = norm.substringBefore('/')
                val cls = norm.substringAfter('/')
                val full = if (cls.startsWith(".")) "$pkg$cls" else cls
                if (pkg.isNotBlank() && full.isNotBlank()) i.component = ComponentName(pkg, full)
            }
        }
        if (spec.packageName.isNotBlank()) i.setPackage(spec.packageName)
        spec.categories.filter { it.isNotBlank() }.forEach { i.addCategory(it) }

        spec.extras.forEach { ex ->
            val v = CommandRenderer.fill(ex.value, entry, values)
            when (ex.type) {
                ExtraType.STRING -> i.putExtra(ex.key, v)
                ExtraType.BOOL -> i.putExtra(ex.key, v.equals("true", true) || v == "1")
                ExtraType.INT -> i.putExtra(ex.key, v.toIntOrNull() ?: 0)
                ExtraType.LONG -> i.putExtra(ex.key, v.toLongOrNull() ?: 0L)
                ExtraType.FLOAT -> i.putExtra(ex.key, v.toFloatOrNull() ?: 0f)
                ExtraType.NULL -> Unit
                ExtraType.STRING_ARRAY ->
                    i.putExtra(ex.key, v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
                ExtraType.INT_ARRAY ->
                    i.putExtra(ex.key, v.split(',').mapNotNull { it.trim().toIntOrNull() }.toIntArray())
            }
        }
        spec.flags.filter { it.isNotBlank() }.forEach { addFlag(i, it) }
        return i
    }

    private fun addFlag(i: Intent, raw: String) {
        val key = raw.trim().uppercase()
        val v = when {
            key == "NEW_TASK" -> Intent.FLAG_ACTIVITY_NEW_TASK
            key == "CLEAR_TOP" -> Intent.FLAG_ACTIVITY_CLEAR_TOP
            key == "SINGLE_TOP" -> Intent.FLAG_ACTIVITY_SINGLE_TOP
            key == "CLEAR_TASK" -> Intent.FLAG_ACTIVITY_CLEAR_TASK
            key == "NO_HISTORY" -> Intent.FLAG_ACTIVITY_NO_HISTORY
            key == "EXCLUDE_FROM_RECENTS" -> Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            key == "GRANT_READ_URI_PERMISSION" -> Intent.FLAG_GRANT_READ_URI_PERMISSION
            key == "GRANT_WRITE_URI_PERMISSION" -> Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            else -> key.removePrefix("0X").toLongOrNull(16)?.toInt() ?: 0
        }
        if (v != 0) i.addFlags(v)
    }
}
