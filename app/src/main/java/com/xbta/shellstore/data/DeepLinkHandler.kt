package com.xbta.shellstore.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.xbta.shellstore.BuildConfig
import com.xbta.shellstore.core.CommandRenderer
import com.xbta.shellstore.core.DeepLink
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.ui.EntryRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 执行 deep link 里的「动作」类命令（导航类由 App.kt 直接改导航栈，不经过这里）。
 *
 * 约定：**主线程调用**（内部自己把重活丢到 IO），返回一句给用户看的提示，null = 不提示。
 */
object DeepLinkHandler {

    /** 这些动作会改数据或覆盖内容，必须显式带 confirm=1 才执行 */
    private val NEED_CONFIRM = setOf(
        "unfav", "source-del", "clear", "backup-import", "restore-cloud"
    )

    /** 这些动作要联网，未同意隐私政策时直接拒绝 */
    private val NET_ACTIONS = setOf(
        "source-add", "source-refresh", "source-check", "source-apply",
        "series", "series-refresh", "backup-now", "restore-cloud",
        "check-update", "download-update"
    )

    suspend fun handle(ctx: Context, cmd: DeepLink.Cmd): String? {
        if (!cmd.known) {
            return "未知动作「${cmd.name}」，看 shellstore://schemehelp"
        }
        if (cmd.name in NET_ACTIONS && !Store.netAllowed) {
            return "未同意隐私政策，联网功能已关闭"
        }
        if (cmd.name in NEED_CONFIRM && !cmd.confirmed()) {
            return "「${cmd.name}」会改动数据，确认请加 &confirm=1"
        }
        return when (cmd.name) {
            "run" -> doRun(ctx, cmd)
            "copy" -> doCopy(ctx, cmd)
            "fav" -> doFav(cmd)
            "unfav" -> doUnfav(cmd)
            "pin" -> doPin(cmd)
            "shortcut" -> doPin2Home(ctx, cmd, tile = false)
            "tile" -> doPin2Home(ctx, cmd, tile = true)
            "export-apk" -> doExportApk(ctx, cmd)
            "clear" -> doClear(ctx, cmd)
            "source-add" -> doSourceAdd(cmd)
            "source-del" -> doSourceDel(cmd)
            "source-toggle" -> doSourceToggle(cmd)
            "source-refresh" -> doSourceRefresh(cmd)
            "source-check" -> {
                withContext(Dispatchers.IO) { Store.checkSourceUpdates() }
                val n = Store.pendingUpdates.value.size
                if (n == 0) "源已是最新" else "发现 $n 个源有更新，去订阅源页确认"
            }
            "source-apply" -> {
                if (Store.pendingUpdates.value.isEmpty()) "没有待应用的更新"
                else { Store.applySourceUpdates(); "已应用源更新" }
            }
            "series" -> {
                val on = cmd.flag("on", !Store.settings.value.xbtaSeries)
                Store.setXbtaSeries(on)
                if (on) "小贝塔订阅源已开启" else "小贝塔订阅源已关闭"
            }
            "series-refresh" -> withContext(Dispatchers.IO) {
                val err = Store.refreshSeries()
                err ?: "清单已更新（${Store.seriesItems.value.size} 个源）"
            }
            "backup-now" -> withContext(Dispatchers.IO) {
                val r = Store.backupNow()
                if (r.ok) "已备份到云端" else "备份失败：${r.msg}"
            }
            "source-dismiss" -> {
                Store.dismissSourceUpdates()
                "已放弃这批源更新"
            }
            "favs-export" -> {
                val smd = Store.exportFavsSmd(cmd.s("title") ?: "我的收藏")
                copyText(ctx, "收藏 SMD", smd)
                "收藏已导出为 SMD（${Store.favs.value.items.size} 条，已复制）"
            }
            "backup-ping" -> withContext(Dispatchers.IO) {
                val r = Store.pingCloud()
                if (r.ok) "云端连接正常" else "连接失败：${r.msg}"
            }
            "backup-export" -> doBackupExport(ctx)
            "backup-import" -> doBackupImport(ctx, cmd)
            "restore-cloud" -> withContext(Dispatchers.IO) {
                val err = Store.restoreFromCloud(overwrite = true)
                err ?: "已从云端恢复"
            }
            "check-update" -> {
                UpdateCenter.checkAndDownload(ctx, BuildConfig.APP_VERSION, allowDownload = false)
                "已开始检查更新"
            }
            "download-update" -> {
                UpdateCenter.checkAndDownload(ctx, BuildConfig.APP_VERSION, allowDownload = true)
                "已开始检查更新（有新版会自动下载）"
            }
            "set" -> doSet(cmd)
            // 导航类在 App.kt 处理；能走到这里说明参数缺失（如 entry 没带 id）
            else -> "「${cmd.name}」需要参数，看 shellstore://schemehelp"
        }
    }

    // ---------------- 条目 ----------------

    private fun doRun(ctx: Context, cmd: DeepLink.Cmd): String? {
        val id = cmd.id() ?: return "缺少 id（shellstore://run?id=…）"
        if (Store.entryById(id) == null) return "条目不存在：$id"
        return EntryRunner.run(ctx, id, "", cmd.entryParams())
    }

    private fun doCopy(ctx: Context, cmd: DeepLink.Cmd): String? {
        val id = cmd.id() ?: return "缺少 id"
        val e = Store.entryById(id) ?: return "条目不存在：$id"
        val values = valuesOf(e, cmd)
        val text = if (cmd.flag("shell")) {
            // 与详情页「复制为 Shell」一致：优先条目自带的 @adb，没有就按类型生成 am 命令
            CommandRenderer.toAdb(e, values)
        } else if (e.type == EntryType.SHELL || e.type == EntryType.SCRIPT) {
            CommandRenderer.primaryText(e, values, -1)
        } else {
            CommandRenderer.contentText(e, values)
        }
        if (text.isBlank()) return "这条没有可复制的内容"
        copyText(ctx, "ShellStore", text)
        Store.recordRecent(e.id)
        return "已复制 ${text.lineSequence().firstOrNull().orEmpty().take(40)}"
    }

    private fun doFav(cmd: DeepLink.Cmd): String? {
        val id = cmd.id() ?: return "缺少 id"
        val e = Store.entryById(id) ?: return "条目不存在：$id"
        val added = Store.toggleFav(e)
        return if (added) "已收藏：${e.title}" else "已取消收藏：${e.title}"
    }

    private fun doUnfav(cmd: DeepLink.Cmd): String? {
        val id = cmd.id() ?: return "缺少 id"
        if (!Store.isFav(id)) return "这条不在收藏里"
        Store.removeFav(id)
        return "已从收藏移除"
    }

    private fun doPin(cmd: DeepLink.Cmd): String? {
        val id = cmd.id() ?: return "缺少 id"
        val e = Store.entryById(id) ?: return "条目不存在：$id"
        if (!Store.isFav(id)) return "置顶只对收藏条目生效，先用 fav 收藏这条"
        val on = cmd.flag("on", !e.pinned)
        Store.updateFavEntry(id, e.copy(pinned = on))
        return if (on) "已置顶" else "已取消置顶"
    }

    private suspend fun doPin2Home(ctx: Context, cmd: DeepLink.Cmd, tile: Boolean): String? {
        val id = cmd.id() ?: return "缺少 id"
        val e = Store.entryById(id) ?: return "条目不存在：$id"
        val spec = EntryExport.spec(
            e,
            valuesOf(e, cmd),
            typeOverride = cmd.s("type"),
            titleOverride = cmd.s("title"),
            pkg = cmd.s("pkg").orEmpty()
        )
        val icon = withContext(Dispatchers.IO) { EntryShortcuts.iconFor(spec) }
        val err = if (tile) EntryShortcuts.addTile(ctx, spec, icon)
        else EntryShortcuts.pinToHome(ctx, spec, icon)
        return err ?: if (tile) "已添加磁贴：${spec.title}" else "已添加到桌面：${spec.title}"
    }

    private suspend fun doExportApk(ctx: Context, cmd: DeepLink.Cmd): String? {
        val id = cmd.id() ?: return "缺少 id"
        val e = Store.entryById(id) ?: return "条目不存在：$id"
        val spec = EntryExport.spec(
            e,
            valuesOf(e, cmd),
            typeOverride = cmd.s("type"),
            titleOverride = cmd.s("title"),
            pkg = cmd.s("pkg").orEmpty(),
            execOverride = cmd.s("exec"),
            resultMode = cmd.s("result")
        )
        val pkgErr = ApkExporter.validatePackage(spec.packageName.ifBlank { "com.xbta.cmd.a00000000" })
        if (spec.packageName.isNotBlank() && pkgErr != null) return "包名不合法：$pkgErr"
        val r = runCatching {
            withContext(Dispatchers.IO) { ApkExporter.export(ctx, spec) { } }
        }.getOrElse { t -> return "导出失败：${t.message.orEmpty().take(80)}" }
        // 用户设置了导出目录就再存一份留档
        val saveDir = Store.settings.value.exportDirUri
        if (saveDir.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { ApkExporter.saveToFolder(ctx, r.file, saveDir, spec.title) }
            }
        }
        return "已导出：${r.file.name}"
    }

    private fun doClear(ctx: Context, cmd: DeepLink.Cmd): String? = when (cmd.s("what")?.lowercase()) {
        "recent" -> { Store.clearRecent(); "已清空最近使用" }
        "search" -> { Store.clearSearchHistory(); "已清空搜索历史" }
        "favs" -> { Store.clearFavorites(); "已清空收藏" }
        "crash" -> { CrashLog.clear(ctx); "已清空崩溃日志" }
        else -> "what 只能是 recent / search / favs"
    }

    // ---------------- 订阅源 ----------------

    private suspend fun doSourceAdd(cmd: DeepLink.Cmd): String? {
        val url = cmd.s("url") ?: return "缺少 url"
        val name = cmd.s("name") ?: "外部添加的源"
        val err = withContext(Dispatchers.IO) { Store.addRemote(name, url) }
        return err ?: "已添加订阅源：$name"
    }

    private fun doSourceDel(cmd: DeepLink.Cmd): String? {
        val id = cmd.id() ?: return "缺少 id"
        val cfg = Store.sourceOf(id) ?: return "源不存在：$id"
        Store.removeSource(id)
        return "已删除订阅源：${cfg.name}"
    }

    private fun doSourceToggle(cmd: DeepLink.Cmd): String? {
        val id = cmd.id() ?: return "缺少 id"
        val cfg = Store.sourceOf(id) ?: return "源不存在：$id"
        val on = cmd.flag("on", !cfg.enabled)
        Store.toggleSource(id, on)
        return "「${cfg.name}」已${if (on) "启用" else "停用"}"
    }

    private suspend fun doSourceRefresh(cmd: DeepLink.Cmd): String? {
        val id = cmd.id()
        if (id != null) {
            if (Store.sourceOf(id) == null) return "源不存在：$id"
            val err = withContext(Dispatchers.IO) { Store.refresh(id) }
            return err ?: "已刷新"
        }
        val (ok, bad) = withContext(Dispatchers.IO) { Store.refreshAllSources() }
        return if (bad == 0) "全部刷新完成（$ok 个源）" else "刷新完成：成功 $ok，失败 $bad"
    }

    // ---------------- 备份 ----------------

    private fun doBackupExport(ctx: Context): String? {
        val json = Store.exportBackupJson()
        if (json.isBlank()) return "备份内容为空"
        copyText(ctx, "ShellStore 备份", json)
        return "备份 JSON 已复制到剪贴板（${json.length} 字节）"
    }

    private fun doBackupImport(ctx: Context, cmd: DeepLink.Cmd): String? {
        val json = cmd.s("data") ?: clipboardText(ctx)
        if (json.isNullOrBlank()) return "没拿到 JSON：用 data= 传入，或先复制到剪贴板"
        val err = Store.importBackup(json, overwrite = cmd.flag("overwrite", true))
        return err ?: "已导入备份"
    }

    // ---------------- 设置 ----------------

    private fun doSet(cmd: DeepLink.Cmd): String? {
        val key = cmd.s("key") ?: return "缺少 key"
        val value = cmd.args["value"]?.trim().orEmpty()
        applySetting(key, value)?.let { return it }
        return null
    }

    /**
     * 改单个设置项。返回 null = 成功，非空 = 错误提示。
     * 字段名见 DeepLink.SETTING_KEYS。
     */
    fun applySetting(rawKey: String, value: String): String? {
        val key = rawKey.trim()
        // 密码类字段存 Keystore、只能走界面输入框，deep link 里不接收明文
        if (key.lowercase() in setOf("webdavpass", "webdavpassword", "s3secret")) {
            return "密码类字段请在 App 内填写（不入 deep link）"
        }
        val s = Store.settings.value
        fun on(): Boolean? = when (value.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
        val next = when (key.lowercase()) {
            "theme" -> when (value.lowercase()) {
                "light", "浅色" -> s.copy(theme = ThemeMode.LIGHT)
                "dark", "深色" -> s.copy(theme = ThemeMode.DARK)
                "system", "跟随系统", "auto" -> s.copy(theme = ThemeMode.SYSTEM)
                else -> return "theme 只能是 light / dark / system"
            }
            "fontlevel" -> {
                val v = value.toIntOrNull() ?: return "fontLevel 是 0-9 的整数"
                if (v !in 0..9) return "fontLevel 是 0-9 的整数"
                s.copy(fontLevel = v)
            }
            "uilevel" -> {
                val v = value.toIntOrNull() ?: return "uiLevel 是 0-4 的整数"
                if (v !in 0..4) return "uiLevel 是 0-4 的整数"
                s.copy(uiLevel = v)
            }
            "liststyle" -> when (value.lowercase()) {
                "card", "卡片" -> s.copy(listStyle = ListStyle.CARD)
                "list", "列表" -> s.copy(listStyle = ListStyle.LIST)
                else -> return "listStyle 只能是 card / list"
            }
            "foldpreview" -> s.copy(foldPreview = on() ?: return "foldPreview 需要 true/false")
            "copyprefix" -> when (value.lowercase()) {
                "none", "无" -> s.copy(copyPrefix = CopyPrefix.NONE)
                "adb", "adb_shell", "adb shell" -> s.copy(copyPrefix = CopyPrefix.ADB_SHELL)
                "su", "root" -> s.copy(copyPrefix = CopyPrefix.SU)
                else -> return "copyPrefix 只能是 none / adb / su"
            }
            "confirmdanger" -> s.copy(confirmDanger = on() ?: return "confirmDanger 需要 true/false")
            "copyandreturn" -> s.copy(copyAndReturn = on() ?: return "copyAndReturn 需要 true/false")
            "favaskfolder" -> s.copy(favAskFolder = on() ?: return "favAskFolder 需要 true/false")
            "checkupdateonstart" -> s.copy(checkUpdateOnStart = on() ?: return "checkUpdateOnStart 需要 true/false")
            "wifionlyupdate" -> s.copy(wifiOnlyUpdate = on() ?: return "wifiOnlyUpdate 需要 true/false")
            "cloudbackend" -> when (value.lowercase()) {
                "webdav" -> s.copy(cloudBackend = CloudBackend.WEBDAV)
                "s3" -> s.copy(cloudBackend = CloudBackend.S3)
                else -> return "cloudBackend 只能是 webdav / s3"
            }
            "webdavauto" -> s.copy(webdavAuto = on() ?: return "webdavAuto 需要 true/false")
            "webdavurl" -> s.copy(webdavUrl = value)
            "webdavuser" -> s.copy(webdavUser = value)
            "webdavpath" -> s.copy(webdavPath = value.ifBlank { "shellstore/shellstore-backup.json" })
            "s3endpoint" -> s.copy(s3Endpoint = value)
            "s3region" -> s.copy(s3Region = value.ifBlank { "auto" })
            "s3bucket" -> s.copy(s3Bucket = value)
            "s3key" -> s.copy(s3Key = value.ifBlank { "shellstore/shellstore-backup.json" })
            "s3pathstyle" -> s.copy(s3PathStyle = on() ?: return "s3PathStyle 需要 true/false")
            "uifont" -> when (value.lowercase()) {
                "system", "默认" -> s.copy(uiFont = FontChoice.SYSTEM)
                "custom", "自定义" -> s.copy(uiFont = FontChoice.CUSTOM)
                else -> return "uiFont 只能是 system / custom"
            }
            "codefont" -> when (value.lowercase()) {
                "system", "默认" -> s.copy(codeFont = FontChoice.SYSTEM)
                "custom", "自定义" -> s.copy(codeFont = FontChoice.CUSTOM)
                else -> return "codeFont 只能是 system / custom"
            }
            "exportdiruri" -> s.copy(exportDirUri = value)
            else -> return "不认识的设置项：$key（看 shellstore://schemehelp）"
        }
        Store.updateSettings(next)
        return null
    }

    // ---------------- 工具 ----------------

    /** 条目参数值：deep link 带了的优先，否则用条目默认值 */
    private fun valuesOf(e: Entry, cmd: DeepLink.Cmd): Map<String, String> {
        val given = cmd.entryParams()
        return if (given.isNotEmpty()) given else e.params.associate { it.key to it.default }
    }

    private fun copyText(ctx: Context, label: String, text: String) {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    private fun clipboardText(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
    }.getOrNull()

}
