package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xbta.shellstore.core.Changelog
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 更新后首次打开的「本次更新内容」弹窗。
 *
 * 内容取自 assets/changelog.md 里与当前版本对应的那一段——和「我的 › 关于 › 更新日志」
 * 是同一份数据，不另写一份，避免两边说法不一致。
 *
 * @param version 当前版本那一段；为空说明日志没解析出来，此时直接调用方不弹
 * @param onShowAll 去看全部更新日志
 * @param onShowFeatures 去看功能总览（和「全部更新日志」并排，位置一样显眼）
 */
@Composable
fun WhatsNewDialog(
    version: Changelog.Version,
    onShowAll: () -> Unit,
    onShowFeatures: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 标题：版本号 + 日期
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, end = Space.sm, top = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "v${version.version} 更新内容",
                        fontSize = TypeScale.body,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        listOf(version.date, version.tag).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg)
                    .padding(top = Space.sm)
            ) {
                if (version.lead.isNotBlank()) {
                    Text(
                        version.lead,
                        fontSize = TypeScale.small,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                version.sections.forEach { sec ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        sec.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    sec.entries.forEach { e ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                if (e.sub) "·" else "•",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(
                                    start = if (e.sub) 14.dp else 2.dp,
                                    end = 6.dp,
                                    top = 1.dp
                                )
                            )
                            Text(
                                inlineText(e.text),
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // 底部：全部更新日志 + 知道了
            Row(
                Modifier.fillMaxWidth().padding(Space.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onShowAll) { Text("更新日志", fontSize = 13.sp) }
                TextButton(onClick = onShowFeatures) { Text("全部功能", fontSize = 13.sp) }
                TextButton(onClick = onDismiss) {
                    Text("知道了", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 行内 `**粗体**` 与 `` `代码` `` 着色，与关于页的渲染保持一致 */
@Composable
private fun inlineText(text: String) = buildAnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    Changelog.inline(text).forEach { span ->
        withStyle(
            SpanStyle(
                fontWeight = if (span.bold) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (span.code) FontFamily.Monospace else FontFamily.Default,
                background = if (span.code) codeBg else androidx.compose.ui.graphics.Color.Unspecified
            )
        ) { append(span.text) }
    }
}
