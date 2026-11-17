package com.xbta.shellstore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.data.ExecRecord
import com.xbta.shellstore.data.ShellExec
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 执行历史：每条命令跑过什么、哪个通道、退出码、耗时、输出。
 *
 * 之前这些只存在结果弹窗里，关掉就没了；排查「为什么昨天那条失败」只能重跑。
 */
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenEntry: (String) -> Unit) {
    val history by Store.execHistory.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var openAt by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var runningAt by remember { mutableStateOf<Long?>(null) }

    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ShellStore", text))
        Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
    }

    fun rerun(rec: ExecRecord) {
        if (runningAt != null) return
        runningAt = rec.at
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                ShellExec.runProfiles(
                    ctx,
                    rec.cmd,
                    Store.runProfilesFor(rec.entryId.ifBlank { null }),
                    entryId = rec.entryId,
                    title = rec.title
                )
            }
            runningAt = null
            Toast.makeText(
                ctx,
                if (r.ok) "重跑成功 · ${r.profileName.ifBlank { r.channel.label }}"
                else "重跑失败 · " + (r.err.ifBlank { r.note }.ifBlank { "返回码 ${r.code}" }).take(60),
                Toast.LENGTH_SHORT
            ).show()
            openAt = System.currentTimeMillis()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("执行历史", onBack = onBack, actions = {
            if (history.isNotEmpty()) {
                Text(
                    "清空",
                    fontSize = TypeScale.small,
                    color = Tones.danger,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { confirmClear = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        })
        if (history.isEmpty()) {
            EmptyState("还没有执行记录", "执行过的命令会记在这里：命令、执行方式、退出码、耗时与输出，最多保留 200 条")
            return@Column
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = 20.dp)
        ) {
            Text(
                "共 ${history.size} 条 · 点一条看输出",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.sm)
            )
            history.forEach { rec ->
                val expanded = openAt == rec.at
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { openAt = if (expanded) null else rec.at }
                        .padding(horizontal = Space.md, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (rec.ok) "成功" else "失败",
                            fontSize = TypeScale.caption,
                            fontWeight = FontWeight.SemiBold,
                            color = if (rec.ok) MaterialTheme.colorScheme.primary else Tones.danger
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            rec.profile.ifBlank { "—" },
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!rec.ok) {
                            Spacer(Modifier.width(8.dp))
                            Text("返回码 ${rec.code}", fontSize = TypeScale.caption, color = Tones.danger)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            fmtTime(rec.at) + if (rec.ms > 0) " · ${fmtMs(rec.ms)}" else "",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        rec.title.ifBlank { rec.cmd },
                        fontSize = TypeScale.small,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!expanded) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            rec.cmd,
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        HistoryBlock("命令", rec.cmd)
                        if (rec.out.isNotBlank()) HistoryBlock("输出", rec.out)
                        if (rec.err.isNotBlank()) HistoryBlock("错误", rec.err)
                        if (rec.note.isNotBlank()) HistoryBlock("说明", rec.note)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(if (runningAt == rec.at) "重跑中…" else "重跑", false) { rerun(rec) }
                            FilterChip("复制命令", false) { copy(rec.cmd) }
                            FilterChip("复制输出", false) {
                                copy(listOf(rec.out, rec.err).filter { it.isNotBlank() }.joinToString("\n"))
                            }
                            if (rec.entryId.isNotBlank()) {
                                FilterChip("打开条目", false) { onOpenEntry(rec.entryId) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空执行历史", fontSize = TypeScale.body) },
            text = { Text("将删除全部 ${history.size} 条记录，不影响条目与设置。", fontSize = 12.sp, lineHeight = 19.sp) },
            confirmButton = {
                TextButton(onClick = {
                    Store.clearExecHistory()
                    confirmClear = false
                }) { Text("清空", color = Tones.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun HistoryBlock(label: String, body: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(label, fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(2.dp))
        Text(
            body,
            fontSize = TypeScale.caption,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
                .padding(8.dp)
        )
    }
}

private fun fmtTime(at: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at))

private fun fmtMs(ms: Long): String =
    if (ms < 1000) "${ms}ms" else String.format(Locale.CHINA, "%.1fs", ms / 1000f)
