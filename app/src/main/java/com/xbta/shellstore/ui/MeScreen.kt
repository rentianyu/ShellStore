package com.xbta.shellstore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.BuildConfig
import com.xbta.shellstore.R
import com.xbta.shellstore.data.AppSettings
import com.xbta.shellstore.data.CopyPrefix
import com.xbta.shellstore.data.FontChoice
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.data.ThemeMode
import com.xbta.shellstore.data.Updater
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MeScreen(onOpen: (Page) -> Unit) {
    val entries by Store.entries.collectAsState()
    val srcList by Store.sources.collectAsState()
    val favs by Store.favs.collectAsState()
    val cats by Store.categories.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showUpdate by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<Updater.Info?>(null) }
    var updateMsg by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(-1) }
    var downloaded by remember { mutableStateOf(false) }
    var updateSub by remember { mutableStateOf("v${BuildConfig.APP_VERSION}") }

    Column(Modifier.fillMaxSize()) {
        TopBar("我的")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 与桌面图标同一套图形：深色渐变底 + 青绿提示符
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp))) {
                    Image(
                        painterResource(R.drawable.ic_launcher_background),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().scale(1.55f)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Shell 商店", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "v${BuildConfig.APP_VERSION} (${BuildConfig.APP_VERSION_CODE}) · ${srcList.count { it.enabled }} 个源 · ${entries.size} 条",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(12.dp))
                    .padding(vertical = 13.dp)
            ) {
                StatCell("${favs.items.size}", "已收藏", Modifier.weight(1f))
                StatCell("${favs.folders.size}", "收藏夹", Modifier.weight(1f))
                StatCell("${cats.size}", "分类", Modifier.weight(1f))
            }

            GroupTitle("内容")
            GroupCard {
                SettingRow(
                    "▤", "订阅源",
                    "${srcList.count { it.enabled }} 个启用 · 共 ${srcList.size} 个",
                    onClick = { onOpen(Page.Sources) }
                )
                Divider()
                SettingRow("⇅", "备份与恢复", "导出收藏与设置", onClick = { onOpen(Page.Backup) })
            }

            GroupTitle("应用")
            GroupCard {
                SettingRow("↻", "检查更新", updateSub, onClick = {
                    showUpdate = true
                    if (!checking) {
                        checking = true
                        updateMsg = "正在检查…"
                        latest = null
                        downloaded = false
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Updater.checkLatest() }
                            checking = false
                            val info = r.info
                            if (info == null) {
                                updateMsg = r.error ?: "检查失败"
                                updateSub = "检查失败"
                            } else if (Updater.isNewer(info.tag, BuildConfig.APP_VERSION)) {
                                latest = info
                                updateMsg = ""
                                updateSub = "发现 ${info.tag}"
                            } else {
                                updateMsg = "已是最新版本（${info.tag}）"
                                updateSub = "已是最新"
                            }
                        }
                    }
                })
                Divider()
                SettingRow("⚙", "设置", "主题 · 字号 · 复制前缀", onClick = { onOpen(Page.Settings) })
                Divider()
                SettingRow("ⓘ", "关于", "开源地址 · 许可", onClick = { onOpen(Page.About) })
            }

            GroupTitle("即将支持")
            GroupCard {
                SettingRow("▶", "执行通道", "Shizuku / Termux", enabled = false)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "首版仅支持复制到剪贴板，不申请任何执行权限。\nShizuku / Termux 执行通道在后续版本提供。",
                fontSize = TypeScale.caption,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (showUpdate) {
        AlertDialog(
            onDismissRequest = { if (progress < 0) showUpdate = false },
            title = { Text("检查更新", fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        "当前版本 v${BuildConfig.APP_VERSION} (${BuildConfig.APP_VERSION_CODE})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        updateMsg.isNotBlank() -> Text(updateMsg, fontSize = 12.sp, lineHeight = 19.sp)
                        latest != null && !downloaded -> {
                            Text(
                                "发现新版本 ${latest!!.tag}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "下载后交由系统安装器处理，需允许「安装未知应用」。",
                                fontSize = TypeScale.small,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        downloaded -> Text("安装包已就绪", fontSize = 12.sp)
                    }
                    if (progress >= 0) {
                        Spacer(Modifier.height(10.dp))
                        Text("下载中 $progress%", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                val l = latest
                if (l != null) {
                    TextButton(
                        enabled = progress < 0 && !checking,
                        onClick = {
                            if (downloaded) {
                                val err = Updater.install(ctx, l)
                                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show()
                                showUpdate = false
                            } else {
                                progress = 0
                                scope.launch {
                                    val err = withContext(Dispatchers.IO) {
                                        Updater.download(ctx, l) { p -> progress = p }
                                    }
                                    progress = -1
                                    if (err == null) {
                                        downloaded = true
                                        updateSub = "待安装 ${l.tag}"
                                    } else {
                                        updateMsg = err
                                    }
                                }
                            }
                        }
                    ) {
                        Text(if (downloaded) "立即安装" else "下载（${latest!!.tag}）")
                    }
                } else {
                    TextButton(onClick = { showUpdate = false }) { Text("关闭") }
                }
            },
            dismissButton = {
                if (latest != null) {
                    TextButton(onClick = { showUpdate = false }, enabled = progress < 0) { Text("稍后") }
                }
            }
        )
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- 设置 ----------------

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings by Store.settings.collectAsState()
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var fontDialog by remember { mutableStateOf(0) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val dir = java.io.File(ctx.filesDir, "fonts").apply { mkdirs() }
                val dest = java.io.File(dir, "custom.ttf")
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                dest.absolutePath
            }.onSuccess { path ->
                Store.updateSettings(Store.settings.value.copy(customFontPath = path))
                Toast.makeText(ctx, "字体已导入", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(ctx, "导入失败：${it.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun update(s: AppSettings) = Store.updateSettings(s)

    Column(Modifier.fillMaxSize()) {
        TopBar("设置", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 20.dp)
        ) {
            GroupTitle("外观")
            GroupCard {
                Column(Modifier.padding(13.dp)) {
                    Text("主题", fontSize = 13.sp)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(m.label, settings.theme == m) { update(settings.copy(theme = m)) }
                        }
                    }
                }
                Divider()
                Column(Modifier.padding(13.dp)) {
                    Text("字号", fontSize = 13.sp)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(0.9f to "小", 1.0f to "标准", 1.15f to "大").forEach { (f, l) ->
                            FilterChip(l, kotlin.math.abs(settings.fontScale - f) < 0.01f) {
                                update(settings.copy(fontScale = f))
                            }
                        }
                    }
                }
            }

            GroupTitle("字体")
            GroupCard {
                SettingRow(
                    "字", "界面字体", settings.uiFont.label,
                    onClick = { fontDialog = 1 }
                )
                Divider()
                SettingRow(
                    ">_", "代码字体", settings.codeFont.label,
                    onClick = { fontDialog = 2 }
                )
                Divider()
                SettingRow(
                    "⇩", "导入字体文件",
                    if (settings.customFontPath.isBlank()) "支持 .ttf / .otf"
                    else java.io.File(settings.customFontPath).name,
                    onClick = { picker.launch(arrayOf("*/*")) }
                )
                if (settings.customFontPath.isNotBlank()) {
                    Divider()
                    SettingRow("✕", "清除自定义字体", "", danger = true, onClick = {
                        update(settings.copy(customFontPath = ""))
                    })
                }
            }

            GroupTitle("复制")
            GroupCard {
                Column(Modifier.padding(13.dp)) {
                    Text("复制前缀", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "贴到电脑终端时选 adb shell；手机上用 Shizuku / Termux 保持「无」",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CopyPrefix.entries.forEach { p ->
                            FilterChip(p.label, settings.copyPrefix == p) { update(settings.copy(copyPrefix = p)) }
                        }
                    }
                }
            }

            GroupTitle("安全")
            GroupCard {
                SettingRow(
                    "⚠", "危险操作二次确认", if (settings.confirmDanger) "已开启" else "已关闭",
                    trailing = {
                        Toggle(settings.confirmDanger) { update(settings.copy(confirmDanger = it)) }
                    }
                )
            }

            GroupTitle("其他")
            GroupCard {
                SettingRow(
                    "↻", "启动时检查更新", "",
                    trailing = {
                        Toggle(settings.checkUpdateOnStart) { update(settings.copy(checkUpdateOnStart = it)) }
                    }
                )
                Divider()
                SettingRow("⊘", "清除收藏数据", "不可恢复", danger = true, onClick = { confirmClear = true })
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除收藏数据", fontSize = 15.sp) },
            text = {
                Text(
                    "将删除全部收藏、文件夹与标签，此操作不可恢复。\n订阅源与设置不受影响。",
                    fontSize = 12.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.clearFavorites()
                    confirmClear = false
                    Toast.makeText(ctx, "已清除", Toast.LENGTH_SHORT).show()
                }) { Text("确认删除", color = Tones.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }

    if (fontDialog != 0) {
        val isUi = fontDialog == 1
        AlertDialog(
            onDismissRequest = { fontDialog = 0 },
            title = { Text(if (isUi) "界面字体" else "代码字体", fontSize = 15.sp) },
            text = {
                Column {
                    FontChoice.entries.forEach { fc ->
                        val cur = if (isUi) settings.uiFont else settings.codeFont
                        val usable = fc != FontChoice.CUSTOM || settings.customFontPath.isNotBlank()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = usable) {
                                    if (isUi) update(settings.copy(uiFont = fc))
                                    else update(settings.copy(codeFont = fc))
                                    fontDialog = 0
                                }
                                .padding(vertical = 11.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                fc.label,
                                fontSize = 13.sp,
                                color = if (usable) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline
                            )
                            if (fc == FontChoice.CUSTOM && settings.customFontPath.isBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text("未导入", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.weight(1f))
                            if (cur == fc) Text("✓", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isUi) "默认跟随手机系统字体，中文显示最稳。导入 .ttf 后界面对中英文同时生效。"
                        else "代码块默认用系统等宽字体。可导入自己喜欢的等宽 ttf 替换。",
                        fontSize = TypeScale.caption,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = { TextButton(onClick = { fontDialog = 0 }) { Text("关闭") } }
        )
    }
}

// ---------------- 订阅源 ----------------

@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val sources by Store.sources.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }
    var addUrl by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    fun runAsync(tag: String, block: suspend () -> String?) {
        scope.launch {
            busy = tag
            val msg = withContext(Dispatchers.IO) { block() }
            busy = ""
            Toast.makeText(ctx, msg ?: "$tag 完成", Toast.LENGTH_SHORT).show()
        }
    }

    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runAsync("导入") {
                val text = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (text.isNullOrBlank()) return@runAsync "读取文件失败"
                var fname = "本地源"
                runCatching {
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                        val idx = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cur.moveToFirst()) fname = cur.getString(idx)
                    }
                }
                val display = fname.substringBeforeLast('.')
                Store.importLocal(display, text, fname)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("订阅源", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 20.dp)
        ) {
            if (busy.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${busy}中…", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (!Store.hasOfficial()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .3f), RoundedCornerShape(Radius.md))
                        .clickable(enabled = busy.isBlank()) {
                            runAsync("启用") { Store.enableOfficial()?.let { "启用失败：$it" } ?: "官方命令库已启用" }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启用官方命令库", fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${Store.OFFICIAL_URL.takeLast(28)} · 按分类整理，支持刷新",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", fontSize = IconSize.chevron, color = MaterialTheme.colorScheme.primary)
                }
            }

            sources.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), RoundedCornerShape(Radius.md))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            MutedBadge(s.typeLabel)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                !s.enabled -> "已停用"
                                s.error.isNotBlank() -> s.error
                                else -> "${s.count} 条" + (if (s.url.isNotBlank()) " · ${s.url.take(48)}" else "")
                            },
                            fontSize = TypeScale.caption,
                            color = if (s.error.isNotBlank()) Tones.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    run {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                if (s.type == com.xbta.shellstore.core.SourceType.REMOTE) {
                                    runAsync("刷新") { Store.refresh(s.id)?.let { "刷新失败：$it" } ?: "刷新完成" }
                                } else {
                                    Toast.makeText(ctx, "本地源无需刷新", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) { Text("⟳", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary) }
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                pendingDelete = s.id
                            },
                            contentAlignment = Alignment.Center
                        ) { Text("✕", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline) }
                    }
                    Toggle(s.enabled) { Store.toggleSource(s.id, it) }
                }
                Spacer(Modifier.height(8.dp))
            }

            GroupTitle("添加")
            GroupCard {
                SettingRow("↗", "添加在线源", "从 URL 拉取 SMD", onClick = {
                    addName = ""
                    addUrl = ""
                    showAdd = true
                })
                Divider()
                SettingRow("▣", "导入本地文件", "选择 .md / .txt / .json", onClick = {
                    importer.launch(arrayOf("*/*"))
                })
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "SMD 格式：## 分类 / ### 条目 / @risk safe / 代码块装命令。\n" +
                    "也支持 ● 开头的清单直接粘贴成文件导入。\n" +
                    "在线源支持 ETag 增量更新，内容未变时不重复下载。",
                fontSize = TypeScale.caption,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加在线源", fontSize = 15.sp) },
            text = {
                Column {
                    Field("名称（可留空）", name) { name = it }
                    Spacer(Modifier.height(8.dp))
                    Field("SMD 文件地址", url) { url = it }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "例如 https://f.xbta.cc/shellstore/01-system.md",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (url.isBlank()) {
                        Toast.makeText(ctx, "请填写地址", Toast.LENGTH_SHORT).show()
                    } else {
                        showAdd = false
                        runAsync("拉取") { Store.addRemote(name, url.trim()) ?: "已添加" }
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除订阅源", fontSize = 15.sp) },
            text = { Text("将移除该源及其缓存，收藏里的快照不受影响。", fontSize = 12.sp, lineHeight = 19.sp) },
            confirmButton = {
                TextButton(onClick = {
                    Store.removeSource(id)
                    pendingDelete = null
                }) { Text("删除", color = Tones.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------- 备份 ----------------

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val favs by Store.favs.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    var pendingName by remember { mutableStateOf("") }

    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text.isNullOrBlank()) {
                    Toast.makeText(ctx, "读取文件失败", Toast.LENGTH_SHORT).show()
                } else {
                    pending = text
                    pendingName = "文件"
                }
            }
        }
    }

    fun doImport(json: String, overwrite: Boolean) {
        scope.launch {
            val err = withContext(Dispatchers.IO) { Store.importBackup(json, overwrite) }
            Toast.makeText(ctx, if (err == null) "导入完成" else "导入失败：$err", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("备份与恢复", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 20.dp)
        ) {
            GroupTitle("导出")
            GroupCard {
                SettingRow("⇧", "复制到剪贴板", "${favs.items.size} 条收藏 · JSON", onClick = {
                    val json = Store.exportBackupJson()
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("shellstore-backup", json))
                    Toast.makeText(ctx, "已复制 ${json.length} 字符", Toast.LENGTH_SHORT).show()
                })
            }

            GroupTitle("导入")
            GroupCard {
                SettingRow("⇩", "从文件导入", "选择备份的 .json", onClick = {
                    importer.launch(arrayOf("*/*"))
                })
                Divider()
                SettingRow("⧉", "从剪贴板导入", "粘贴备份 JSON", onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
                    if (text.isNullOrBlank() || !text.contains("shellstore-backup")) {
                        Toast.makeText(ctx, "剪贴板里没有备份数据", Toast.LENGTH_SHORT).show()
                    } else {
                        pending = text
                        pendingName = "剪贴板"
                    }
                })
            }

            Spacer(Modifier.height(Space.lg))
            Text(
                "备份包含：收藏（含快照）、文件夹、标签、备注，以及主题/字号/复制前缀等偏好。\n" +
                    "内置源由 App 自带，不进备份；在线源只保存地址，恢复后重新拉取。\n" +
                    "合并 = 按 id 去重、保留本地；覆盖 = 清空现有收藏后重建。",
                fontSize = TypeScale.small,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    pending?.let { json ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("导入备份", fontSize = TypeScale.title) },
            text = {
                Text(
                    "来源：$pendingName（${json.length} 字符）\n\n" +
                        "合并：保留现有数据，只补充没有的条目\n" +
                        "覆盖：清空现有收藏与文件夹后重建",
                    fontSize = TypeScale.small,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { val j = json; pending = null; doImport(j, overwrite = false) }) {
                    Text("合并")
                }
            },
            dismissButton = {
                TextButton(onClick = { val j = json; pending = null; doImport(j, overwrite = true) }) {
                    Text("覆盖", color = Tones.danger)
                }
            }
        )
    }
}

// ---------------- 关于 ----------------

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val entries by Store.entries.collectAsState()
    val cats by Store.categories.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopBar("关于", onBack = onBack)
        Column(
            Modifier
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(">_", color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Shell 商店", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "v${BuildConfig.APP_VERSION} (${BuildConfig.APP_VERSION_CODE})",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            GroupCard {
                SettingRow("▤", "当前内容", "${cats.size} 个分类 · ${entries.size} 条")
                Divider()
                SettingRow("⌘", "数据格式", "SMD v1")
                Divider()
                SettingRow("⚖", "开源协议", "MIT")
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "命令与脚本均来自订阅源，App 自身不做任何联网上传。\n" +
                    "标注为「危险」的条目在执行前请自行确认内容，误操作造成的后果由使用者承担。",
                fontSize = TypeScale.caption,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
