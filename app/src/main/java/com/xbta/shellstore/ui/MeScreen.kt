package com.xbta.shellstore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.BuildConfig
import com.xbta.shellstore.data.AppSettings
import com.xbta.shellstore.data.CopyPrefix
import com.xbta.shellstore.data.FontChoice
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.data.ThemeMode
import com.xbta.shellstore.ui.theme.Tones

@Composable
fun MeScreen(onOpen: (Page) -> Unit) {
    val entries by Store.entries.collectAsState()
    val favs by Store.favs.collectAsState()
    val cats by Store.categories.collectAsState()
    val ctx = LocalContext.current
    var showUpdate by remember { mutableStateOf(false) }

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
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(">_", color = MaterialTheme.colorScheme.onPrimary, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Shell 商店", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "v${BuildConfig.APP_VERSION} (${BuildConfig.APP_VERSION_CODE}) · 1 个源 · ${entries.size} 条",
                        fontSize = 11.sp,
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
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(12.dp))
                    .padding(vertical = 13.dp)
            ) {
                StatCell("${favs.items.size}", "已收藏", Modifier.weight(1f))
                StatCell("${favs.folders.size}", "收藏夹", Modifier.weight(1f))
                StatCell("${cats.size}", "分类", Modifier.weight(1f))
            }

            GroupTitle("内容")
            GroupCard {
                SettingRow("▤", "数据源", "1 个 · 内置", onClick = { onOpen(Page.Sources) })
                Divider()
                SettingRow("⇅", "备份与恢复", "导出收藏与设置", onClick = { onOpen(Page.Backup) })
            }

            GroupTitle("应用")
            GroupCard {
                SettingRow("↻", "检查更新", "v${BuildConfig.APP_VERSION}", onClick = { showUpdate = true })
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
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (showUpdate) {
        AlertDialog(
            onDismissRequest = { showUpdate = false },
            title = { Text("检查更新", fontSize = 15.sp) },
            text = {
                Text(
                    "当前版本 v${BuildConfig.APP_VERSION}。\n自动检查更新在网络模块接入后启用，届时会从发布页读取最新版本号。",
                    fontSize = 12.sp, lineHeight = 19.sp
                )
            },
            confirmButton = { TextButton(onClick = { showUpdate = false }) { Text("知道了") } }
        )
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        fontSize = 10.sp,
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
                    "将删除全部收藏、文件夹与标签，此操作不可恢复。\n数据源与设置不受影响。",
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
                                Text("未导入", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.weight(1f))
                            if (cur == fc) Text("✓", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isUi) "「系统默认」用手机自带字体，中文显示最稳；导入的字体对中英文同时生效。"
                        else "代码块使用等宽字体。「系统等宽」跟随系统，字形各家差异较大。",
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = { TextButton(onClick = { fontDialog = 0 }) { Text("关闭") } }
        )
    }
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 38.dp, height = 21.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f))
            .clickable { onChange(!on) }
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(17.dp)
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(RoundedCornerShape(9.dp))
                .background(androidx.compose.ui.graphics.Color.White)
        )
    }
}

// ---------------- 数据源 ----------------

@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val entries by Store.entries.collectAsState()
    val name by Store.sourceName.collectAsState()
    val ver by Store.sourceVersion.collectAsState()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopBar("数据源", onBack = onBack)
        Column(Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(11.dp))
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text("▤", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "内置 · ${entries.size} 条" + (if (ver.isNotBlank()) " · v$ver" else ""),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Badge("已启用", Tones.safe, Tones.safeBg)
            }

            Spacer(Modifier.height(12.dp))
            GroupCard {
                SettingRow("↗", "添加在线源", "填 URL 拉取 SMD", onClick = {
                    Toast.makeText(ctx, "在线源在下一版接入", Toast.LENGTH_SHORT).show()
                })
                Divider()
                SettingRow("▣", "导入本地文件", "选择 .md / .json", onClick = {
                    Toast.makeText(ctx, "本地导入在下一版接入", Toast.LENGTH_SHORT).show()
                })
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "数据源格式为 SMD（Markdown 超集）：\n" +
                    "## 分类名　### 条目名　@risk safe　代码块装命令。\n" +
                    "也支持 ● 开头的清单直接粘贴导入。",
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ---------------- 备份 ----------------

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopBar("备份与恢复", onBack = onBack)
        Column(Modifier.padding(horizontal = 14.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(4.dp))
            GroupCard {
                SettingRow("⇧", "导出到剪贴板", "${favs.items.size} 条收藏 · JSON", onClick = {
                    val json = Store.exportBackupJson()
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("shellstore-backup", json))
                    Toast.makeText(ctx, "已复制 ${json.length} 字符", Toast.LENGTH_SHORT).show()
                })
            }
            Spacer(Modifier.height(10.dp))
            GroupCard {
                SettingRow("⇩", "从剪贴板导入", "合并模式", enabled = false)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "当前备份包含：收藏（含快照）、文件夹、标签、设置。\n" +
                    "内置源由 App 自带，不进入备份；在线源的地址与本地导入源的原文会一并保存。\n" +
                    "导入支持「合并」（按 id 去重）与「覆盖」两种模式。",
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
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
                        fontSize = 11.sp,
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
                "命令与脚本均来自数据源，App 自身不做任何联网上传。\n" +
                    "标注为「危险」的条目在执行前请自行确认内容，误操作造成的后果由使用者承担。",
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
