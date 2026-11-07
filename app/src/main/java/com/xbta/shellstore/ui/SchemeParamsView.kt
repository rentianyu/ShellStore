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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.SchemeParse
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Tones

/**
 * url scheme 的参数逐行展示（对齐 tool.xbta.cc/url-editor 的思路）：
 * 一行一个键值，长值可展开看全文，含 `%xx` 的可一键解码、再一键编回。
 *
 * 只影响详情页的**显示**，不改变条目里存的原始 scheme —— 执行、复制走的仍是原文。
 */
@Composable
fun SchemeParamList(
    params: List<SchemeParse.Param>,
    onCopy: (String) -> Unit
) {
    val expanded = remember(params) { mutableStateMapOf<Int, Boolean>() }
    val decoded = remember(params) { mutableStateMapOf<Int, Boolean>() }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        params.forEachIndexed { i, p ->
            if (i > 0) Divider()
            val isDecoded = decoded[i] == true
            val isOpen = expanded[i] == true
            val raw = p.value
            val shown = if (isDecoded) SchemeParse.prettyDecode(raw) else raw
            val canDecode = SchemeParse.looksEncoded(raw)
            val canDecodeMore = isDecoded && SchemeParse.hasPercent(shown)
            val long = raw.length > 24 || raw.contains('\n')

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onCopy(shown) }
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    p.key,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp).width(96.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (shown.isEmpty()) "（空）" else shown,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = if (shown.isEmpty()) MaterialTheme.colorScheme.outline else Tones.codeFg,
                    maxLines = if (isOpen) 40 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (canDecode || canDecodeMore) {
                    Spacer(Modifier.width(6.dp))
                    MiniAction(if (isDecoded) "编码" else "解码") { decoded[i] = !isDecoded }
                }
                if (long) {
                    Spacer(Modifier.width(4.dp))
                    MiniAction(if (isOpen) "收起" else "展开") { expanded[i] = !isOpen }
                }
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
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 参数区上方的概要行：协议 + 是否有锚点，信息量小但省得用户自己找 */
@Composable
fun SchemeSummary(parts: SchemeParse.Parts) {
    val bits = buildList {
        if (parts.scheme.isNotBlank()) add(parts.scheme)
        add("${parts.params.size} 个参数")
        if (parts.hash.isNotBlank()) add("含锚点")
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
        Spacer(Modifier.height(0.dp))
    }
}
