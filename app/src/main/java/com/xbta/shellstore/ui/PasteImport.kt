package com.xbta.shellstore.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.core.IntentSpec
import com.xbta.shellstore.core.Risk
import com.xbta.shellstore.core.SmdParser
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale
import org.json.JSONObject

/** 粘贴内容的识别结果 */
sealed interface PasteKind {
    data object Empty : PasteKind
    data class Backup(val json: String, val favs: Int, val folders: Int) : PasteKind
    data class Smd(val text: String, val count: Int, val name: String) : PasteKind
    data class Single(val entry: Entry) : PasteKind
    data object Unknown : PasteKind
}

/**
 * 猜剪贴板里的东西是什么：
 * JSON 备份 → SMD 源 → 单条命令 / URL / am 命令。
 */
fun detectPaste(raw: String): PasteKind {
    val t = raw.trim()
    if (t.isEmpty()) return PasteKind.Empty

    if (t.startsWith("{")) {
        val b = runCatching {
            val o = JSONObject(t)
            if (o.has("items") && o.has("folders")) {
                PasteKind.Backup(
                    json = t,
                    favs = o.optJSONArray("items")?.length() ?: 0,
                    folders = o.optJSONArray("folders")?.length() ?: 0
                )
            } else null
        }.getOrNull()
        if (b != null) return b
    }

    val hasCat = Regex("(?m)^##\\s+\\S").containsMatchIn(t)
    val hasEntry = Regex("(?m)^###\\s+\\S").containsMatchIn(t)
    if (hasCat && hasEntry) {
        val parsed = SmdParser.parse(t, "_paste", "粘贴导入")
        val n = parsed.source.entries.size
        if (n > 0) return PasteKind.Smd(t, n, parsed.source.name.ifBlank { "粘贴的源" })
    }

    if (t.length > 20000) return PasteKind.Unknown
    return PasteKind.Single(guessSingle(t))
}

private fun guessSingle(t: String): Entry {
    val firstLine = t.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val clean = t.trim().removePrefix("adb shell ").removePrefix("adb ").trim()
    val type = when {
        Regex("^(am|cmd)\\s").containsMatchIn(clean) -> EntryType.INTENT
        Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://\\S*$").containsMatchIn(clean) -> EntryType.URLSCHEME
        else -> EntryType.SHELL
    }
    val intent = if (type == EntryType.INTENT) guessIntent(clean) else null
    val title = when (type) {
        EntryType.URLSCHEME -> clean.substringAfter("://").ifBlank { clean }.take(40)
        EntryType.INTENT -> (intent?.component?.ifBlank { null }
            ?: intent?.action?.ifBlank { null }
            ?: "Intent").take(40)
        else -> firstLine.take(40)
    }
    return Entry(
        id = "",
        type = type,
        title = title,
        body = if (type == EntryType.INTENT) "" else clean,
        risk = if (Regex("(?i)\\brm\\s+-rf?\\b|\\bmkfs\\b|dd\\s+if=").containsMatchIn(clean)) Risk.DANGER
        else Risk.SAFE,
        intent = intent
    )
}

private fun guessIntent(cmd: String): IntentSpec {
    fun arg(flag: String): String =
        Regex("\\s$flag\\s+(\\S+)").find(cmd)?.groupValues?.get(1).orEmpty()
    return IntentSpec(
        action = arg("-a"),
        component = arg("-n"),
        data = arg("-d"),
        mime = arg("-t")
    )
}

/**
 * 粘贴导入的结果对话框。所有写操作都在这里做完，只有「打开编辑器」需要外部导航。
 */
@Composable
fun PasteImportDialog(
    raw: String,
    folderId: String?,
    onDismiss: () -> Unit,
    onOpenEditor: (Entry) -> Unit,
    onOpenSources: () -> Unit
) {
    val ctx = LocalContext.current
    val kind = remember(raw) { detectPaste(raw) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴导入", fontSize = TypeScale.body) },
        text = {
            Column {
                when (kind) {
                    PasteKind.Empty -> {
                        Tip("剪贴板是空的。先复制一段命令、SMD 文本或备份 JSON。")
                    }

                    PasteKind.Unknown -> {
                        Tip("没认出这是什么格式。支持：备份 JSON、SMD 源文本、单条命令 / URL / am 命令。")
                    }

                    is PasteKind.Backup -> {
                        Sub("识别为：备份数据")
                        Tip("${kind.favs} 条收藏 · ${kind.folders} 个文件夹")
                        Spacer(Modifier.height(Space.xs))
                        MenuRow("合并导入（保留现有内容）") {
                            val err = Store.importBackup(kind.json, overwrite = false)
                            toast(err ?: "已合并导入")
                            onDismiss()
                        }
                        MenuRow("覆盖导入（先清空再恢复）", danger = true) {
                            val err = Store.importBackup(kind.json, overwrite = true)
                            toast(err ?: "已覆盖导入")
                            onDismiss()
                        }
                    }

                    is PasteKind.Smd -> {
                        Sub("识别为：SMD 源文本")
                        Tip("${kind.count} 条命令 · ${kind.name}")
                        Spacer(Modifier.height(Space.xs))
                        MenuRow("存为订阅源（可在分类里浏览）") {
                            val err = Store.importLocal(kind.name, kind.text, "粘贴导入.md")
                            toast(err ?: "已添加为订阅源")
                            onDismiss()
                        }
                        MenuRow("加入收藏") {
                            val parsed = SmdParser.parse(kind.text, "_paste", kind.name)
                            val (added, skipped) = Store.addFavs(parsed.source.entries, folderId)
                            toast(
                                when {
                                    added > 0 && skipped > 0 -> "加入 $added 条，$skipped 条已收藏"
                                    added > 0 -> "已加入 $added 条"
                                    else -> "都已收藏过了"
                                }
                            )
                            onDismiss()
                        }
                        MenuRow("看看有哪些订阅源") {
                            onDismiss()
                            onOpenSources()
                        }
                    }

                    is PasteKind.Single -> {
                        Sub("识别为：单条 ${typeLabel(kind.entry.type)}")
                        Tip(kind.entry.title.ifBlank { "（无标题）" })
                        Spacer(Modifier.height(Space.xs))
                        MenuRow("打开编辑器确认后保存") {
                            onDismiss()
                            onOpenEditor(kind.entry)
                        }
                        MenuRow("直接建一条命令") {
                            Store.addLocalEntry(kind.entry, folderId)
                            toast("已新建")
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun Sub(text: String) {
    Text(
        text,
        fontSize = TypeScale.small,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 3.dp)
    )
}

@Composable
private fun Tip(text: String) {
    Text(
        text,
        fontSize = TypeScale.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun MenuRow(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = TypeScale.small,
        color = if (danger) Tones.danger else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 4.dp)
    )
}
