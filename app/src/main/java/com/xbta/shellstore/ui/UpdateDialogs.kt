package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xbta.shellstore.data.SourceDiff
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale

/** 新版本已下载完成，问用户要不要现在装 */
@Composable
fun UpdateReadyDialog(
    tag: String,
    currentVersion: String,
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Space.lg)
        ) {
            Text("新版本已就绪", fontSize = TypeScale.title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.sm))
            Text(
                "v$currentVersion → ${tag.removePrefix("v")}",
                fontSize = TypeScale.body,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "安装包已经在后台下载完成，点击安装后系统会弹出覆盖安装界面。",
                fontSize = TypeScale.small,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onLater() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("稍后", fontSize = TypeScale.body, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onInstall() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("立即安装", fontSize = TypeScale.body, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** 订阅源有新版本时的差异确认框 */
@Composable
fun SourceUpdateDialog(
    diffs: List<SourceDiff>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val totalDelta = diffs.sumOf { it.delta }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Space.lg)
        ) {
            Text("订阅源有更新", fontSize = TypeScale.title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (totalDelta > 0) "共 ${diffs.size} 个源有变化，合计新增 $totalDelta 条"
                else if (totalDelta < 0) "共 ${diffs.size} 个源有变化，合计减少 ${-totalDelta} 条"
                else "共 ${diffs.size} 个源内容有变化",
                fontSize = TypeScale.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Space.md))
            Box(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    diffs.forEach { d -> SourceDiffRow(d) }
                }
            }

            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", fontSize = TypeScale.body, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onConfirm() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("更新", fontSize = TypeScale.body, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun SourceDiffRow(d: SourceDiff) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.name, fontSize = TypeScale.body, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                if (d.countChanged) "${d.oldCount} → ${d.newCount} 条"
                else "${d.newCount} 条 · 内容有更新",
                fontSize = TypeScale.caption,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (d.countChanged) {
            Spacer(Modifier.width(Space.sm))
            DeltaBadge(d.delta)
        }
    }
}

@Composable
private fun DeltaBadge(delta: Int) {
    val fg = if (delta > 0) Tones.safe else Tones.danger
    val bg = if (delta > 0) Tones.safeBg else Tones.dangerBg
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            if (delta > 0) "+$delta" else "$delta",
            fontSize = TypeScale.caption,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

/** 下载中的顶部细进度条，不挡内容 */
@Composable
fun DownloadBar(pct: Int) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (pct <= 0) 0.02f else (pct / 100f).coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("新版本下载中 $pct%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
