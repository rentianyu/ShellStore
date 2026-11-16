package com.xbta.shellstore.ui

import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.data.AppSettings
import com.xbta.shellstore.data.FONT_SCALES
import com.xbta.shellstore.data.FontChoice
import com.xbta.shellstore.data.ListStyle
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.data.ThemeMode
import com.xbta.shellstore.data.UI_SCALES
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale
import kotlin.math.roundToInt

/**
 * 界面与显示：从「设置」里拆出来的二级页。
 * 只放影响观感的项（主题 / 字号 / 缩放 / 列表样式 / 字体），
 * 设置首页因此能一屏看完：界面、复制、安全、其他、法律。
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val settings by Store.settings.collectAsState()
    val ctx = LocalContext.current
    var fontDialog by remember { mutableStateOf(0) }

    fun update(s: AppSettings) = Store.updateSettings(s)

    // 导入自定义字体：复制到 files/fonts/custom.ttf 后再记账，避免权限失效
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

    Column(Modifier.fillMaxSize()) {
        TopBar("界面与显示", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = 20.dp)
        ) {
            GroupTitle("外观")
            GroupCard {
                Column(Modifier.padding(Space.md)) {
                    Text("主题", fontSize = TypeScale.small)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(m.label, settings.theme == m) { update(settings.copy(theme = m)) }
                        }
                    }
                }
                Divider()
                Column(Modifier.padding(Space.md)) {
                    AsSliderHeader("字号", "${(settings.fontScale * 100).roundToInt()}%")
                    Spacer(Modifier.height(9.dp))
                    // 10 级：拆成两行各 5 个，等宽等距，一眼能扫完也点得准
                    FONT_SCALES.chunked(5).forEachIndexed { rowIdx, row ->
                        if (rowIdx > 0) Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { colIdx, f ->
                                val level = rowIdx * 5 + colIdx
                                FilterChip(
                                    "${(f * 100).roundToInt()}",
                                    settings.fontLevel == level,
                                    Modifier.weight(1f)
                                ) { update(settings.copy(fontLevel = level)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "只放大文字，控件尺寸不变",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Divider()
                Column(Modifier.padding(Space.md)) {
                    AsSliderHeader("界面缩放", "${(settings.uiScale * 100).roundToInt()}%")
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UI_SCALES.forEachIndexed { level, f ->
                            FilterChip(
                                "${(f * 100).roundToInt()}",
                                settings.uiLevel == level,
                                Modifier.weight(1f)
                            ) { update(settings.copy(uiLevel = level)) }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "文字与控件一起缩放，屏幕上显示的内容随之增减",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Divider()
                Column(Modifier.padding(Space.md)) {
                    AsSliderHeader("列表样式", ListStyle.label(settings.listStyle))
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ListStyle.ALL.forEach { st ->
                            FilterChip(
                                ListStyle.label(st),
                                settings.listStyle == st,
                                Modifier.weight(1f)
                            ) { update(settings.copy(listStyle = st)) }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (settings.listStyle == ListStyle.LIST)
                            "无卡片背景、行与行紧挨、只用细分隔线：单条更矮，一屏能多看一倍左右"
                        else "白色卡片 + 圆角，单条信息更舒展，适合逐条细看",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.outline
                    )
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
        }
    }

    if (fontDialog != 0) {
        val isUi = fontDialog == 1
        AlertDialog(
            onDismissRequest = { fontDialog = 0 },
            title = { Text(if (isUi) "界面字体" else "代码字体", fontSize = TypeScale.body) },
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
                                fontSize = TypeScale.small,
                                color = if (usable) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline
                            )
                            if (fc == FontChoice.CUSTOM && settings.customFontPath.isBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text("未导入", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.weight(1f))
                            if (cur == fc) AppIcon("✓", size = 14.dp, color = MaterialTheme.colorScheme.primary)
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

/** 「字号  100%」这种一行标题：左边名称、右边当前值 */
@Composable
private fun AsSliderHeader(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = TypeScale.small)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = TypeScale.small,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
