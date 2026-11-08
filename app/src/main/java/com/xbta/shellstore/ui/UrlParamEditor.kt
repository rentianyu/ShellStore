package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.JsonLite
import com.xbta.shellstore.core.SchemeParse
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 编辑器里的 url 参数行编辑。
 *
 * 改的是 URL 文本本身（`onBodyChange` 收到重建后的完整串），所以不存在
 * 「面板状态」与「文本框」两份数据打架的问题：面板永远从 body 重新解析。
 *
 * 编码策略：用户没动过的参数原样保留（已是 `%E4%B8%AD` 的不会被二次编码），
 * 改过/新增的值才过 [SchemeParse.encodeIfNeeded]。
 */
@Composable
fun UrlParamEditor(body: String, onBodyChange: (String) -> Unit) {
    val parts = remember(body) { SchemeParse.parse(body) }
    // 正在编辑的下标：-1 表示新增
    var editing by remember { mutableStateOf<Int?>(null) }
    // 正在编辑展开后的某个子值
    var leafEdit by remember { mutableStateOf<LeafEdit?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "参数（${parts.params.size}）",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                "+ 添加",
                fontSize = TypeScale.caption,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { editing = -1 }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (parts.params.isEmpty()) {
            Text(
                "还没有参数。URL 里 `?a=1&b=2` 会自动拆成行，也可以点「+ 添加」。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        parts.params.forEachIndexed { i, p ->
            if (i > 0) Divider()
            // 与详情页共用同一套「解码 / 展开」渲染，编辑器里也能点开看 JSON，
            // 只是行尾多两个编辑按钮（改 / 删）
            key("ep$i") {
                ValueLine(
                    keyName = p.key,
                    raw = p.value,
                    depth = 0,
                    path = "ep$i",
                    onCopy = null,
                    nodePath = emptyList(),
                    onEdit = { subPath, text -> leafEdit = LeafEdit(i, subPath, text) },
                    trailing = {
                        MiniBtn("改") { editing = i }
                        MiniBtn("删") {
                            val list: MutableList<SchemeParse.Param> =
                                parts.params.toMutableList().also { it.removeAt(i) }
                            onBodyChange(SchemeParse.rebuild(body, list))
                        }
                    }
                )
            }
        }
    }

    /**
     * 改展开后的某个子值：沿路径把新文本写回 JSON，再按**原值的形态**回写参数——
     * 原来是 `%7B...` 编码的就整体编码回去，原来是明文 JSON 的就保持明文，
     * 不会因为改一个字段把整个参数从明文变成一长串 `%xx`。
     */
    fun editLeaf(paramIndex: Int, subPath: List<Int>, newText: String) {
        val cur = SchemeParse.parse(body).params.getOrNull(paramIndex) ?: return
        val root = JsonLite.parseOrNull(SchemeParse.readable(cur.value)) ?: return
        val newJson = JsonLite.compose(JsonLite.setText(root, subPath, newText))
        val newRaw = if (SchemeParse.hasPercent(cur.value)) SchemeParse.encode(newJson) else newJson
        val list: MutableList<SchemeParse.Param> = parts.params.toMutableList()
        if (paramIndex !in list.indices) return
        list[paramIndex] = SchemeParse.Param(cur.key, newRaw)
        onBodyChange(SchemeParse.rebuild(body, list))
    }

    leafEdit?.let { le ->
        ParamEditDialog(
            title = "修改值",
            initKey = "",
            initValue = le.text,
            keyEditable = false,
            onDismiss = { leafEdit = null },
            onSave = { _, v ->
                leafEdit = null
                editLeaf(le.index, le.path, v)
            }
        )
    }

    val idx: Int? = editing
    if (idx != null) {
        val origin = parts.params.getOrNull(idx)
        ParamEditDialog(
            title = if (idx < 0) "添加参数" else "修改参数",
            initKey = origin?.key.orEmpty(),
            // 编辑框里显示可读值（%E4%B8%AD → 中文），`+` 不转空格以免改坏 base64
            initValue = origin?.let { SchemeParse.readable(it.value) }.orEmpty(),
            onDismiss = { editing = null },
            onSave = { k, v ->
                val key = SchemeParse.encodeIfNeeded(k.trim())
                if (key.isNotEmpty()) {
                    val list: MutableList<SchemeParse.Param> = parts.params.toMutableList()
                    val item = SchemeParse.Param(key, SchemeParse.encodeIfNeeded(v))
                    if (idx < 0) list.add(item) else if (idx < list.size) list[idx] = item
                    onBodyChange(SchemeParse.rebuild(body, list))
                }
                editing = null
            }
        )
    }
}

/** 正在编辑的 JSON 叶子：属于哪个参数、路径、当前显示文本 */
private data class LeafEdit(val index: Int, val path: List<Int>, val text: String)

@Composable
private fun MiniBtn(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
private fun ParamEditDialog(
    title: String,
    initKey: String,
    initValue: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    keyEditable: Boolean = true
) {
    var k by remember { mutableStateOf(initKey) }
    var v by remember { mutableStateOf(initValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = TypeScale.body) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (keyEditable) {
                    OutlinedTextField(
                        value = k,
                        onValueChange = { k = it },
                        label = { Text("键", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )
                }
                OutlinedTextField(
                    value = v,
                    onValueChange = { v = it },
                    label = { Text("值（可以直接写中文，保存时自动编码）", fontSize = 11.sp) },
                    minLines = 2,
                    maxLines = 6,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                )
                Text(
                    if (keyEditable)
                        "只会转义必须转义的字符（空格、& # ? 与中文）；已经写好的 %xx 不会再编一次。"
                    else
                        "改完会按原参数的形态写回：原来是编码的就重新编码，原来是明文 JSON 的保持明文。",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(k, v) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
