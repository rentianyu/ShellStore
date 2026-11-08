package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.xbta.shellstore.ui.theme.Tones

/** 递归展开的最大层数，防止恶意/异常数据把页面炸掉 */
private const val MAX_DEPTH = 5

/**
 * url scheme 的参数逐行展示，支持**递归**：
 *
 *   解码 → 发现是 JSON → 展开 → 子键值各自还能再解码/展开 → …
 *
 * 每一行都独立维护「是否解码」「是否展开」两个状态，且都以该行的原文为 key，
 * 所以外层切换编解码时，内层的展开状态会跟着重置，不会出现「展开的是旧内容」。
 */
@Composable
fun SchemeParamList(
    params: List<SchemeParse.Param>,
    onCopy: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        params.forEachIndexed { i, p ->
            if (i > 0) Divider()
            key("p$i") {
                ValueLine(keyName = p.key, raw = p.value, depth = 0, path = "p$i", onCopy = onCopy)
            }
        }
    }
}

/**
 * 一行值。值本身可能层层套娃：URL 编码 → JSON → 又一个编码的 JSON……
 * 所以这里既渲染「当前行的键值」，也在展开时递归渲染子节点。
 */
@Composable
private fun ValueLine(
    keyName: String,
    raw: String,
    depth: Int,
    path: String,
    onCopy: (String) -> Unit
) {
    var decoded by remember(raw) { mutableStateOf(false) }
    var expanded by remember(raw) { mutableStateOf(false) }

    val text = if (decoded) SchemeParse.prettyDecode(raw) else raw
    val node = remember(text) { JsonLite.parseOrNull(text) }
    val canDecode = SchemeParse.hasPercent(raw) || (decoded && SchemeParse.hasPercent(text))
    val canExpand = node != null && JsonLite.expandable(node) && depth < MAX_DEPTH
    val isOpen = expanded && canExpand

    val preview = when {
        isOpen && node is JsonLite.Obj -> "{ ${node.fields.size} 个键 }"
        isOpen && node is JsonLite.Arr -> "[ ${node.items.size} 项 ]"
        else -> text
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCopy(text) }
            .padding(start = 11.dp + (depth * 12).dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            keyName,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 88.dp).width(88.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (preview.isEmpty()) "（空）" else preview,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = if (preview.isEmpty()) MaterialTheme.colorScheme.outline else Tones.codeFg,
            maxLines = if (isOpen) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (canDecode) {
            Spacer(Modifier.width(4.dp))
            MiniAction(if (decoded) "编码" else "解码") {
                decoded = !decoded
                expanded = false      // 内容换了，旧的展开结构不再对应
            }
        }
        if (canExpand) {
            Spacer(Modifier.width(2.dp))
            MiniAction(if (isOpen) "收起" else "展开") { expanded = !expanded }
        }
    }

    if (isOpen && node != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, bottom = 4.dp)
        ) {
            when (node) {
                is JsonLite.Obj -> node.fields.forEachIndexed { j, (k, v) ->
                    key("$path.$j") {
                        ValueLine(
                            keyName = k,
                            raw = JsonLite.plain(v),
                            depth = depth + 1,
                            path = "$path.$j",
                            onCopy = onCopy
                        )
                    }
                }
                is JsonLite.Arr -> node.items.forEachIndexed { j, v ->
                    key("$path[$j]") {
                        ValueLine(
                            keyName = "[$j]",
                            raw = JsonLite.plain(v),
                            depth = depth + 1,
                            path = "$path[$j]",
                            onCopy = onCopy
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun MiniAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

/** 参数区上方的概要行：协议 + 参数的编码情况，信息量小但省得用户自己找 */
@Composable
fun SchemeSummary(parts: SchemeParse.Parts) {
    val bits = buildList {
        if (parts.scheme.isNotBlank()) add(parts.scheme)
        add("${parts.params.size} 个参数")
        if (parts.hash.isNotBlank()) add("含锚点")
        if (parts.params.any { SchemeParse.looksEncoded(it.value) }) add("含编码")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bits.forEach { b ->
            Text(
                b,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}
