package com.xbta.shellstore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.LegalText
import com.xbta.shellstore.core.LegalText.Kind
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 首次启动的协议同意页。
 *
 * 合规要点：
 *  - 首次运行以显著方式提示，**不默认勾选**
 *  - 同时提供「同意」「不同意」
 *  - 不同意不强制退出，可继续使用不联网的基础功能
 *  - 同意前不发生任何联网与信息收集
 */
@Composable
fun AgreementScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit
) {
    var legal by remember { mutableStateOf<String?>(null) }
    val openKind = legal

    // 协议页在导航栈之外，必须自己接住返回键，否则一按就退出 App
    if (openKind != null) {
        LegalScreen(openKind) { legal = null }
        return
    }
    BackHandler { onDecline() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(44.dp))

        Text(
            "欢迎使用 ${LegalText.APP_NAME}",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "使用前请阅读并同意以下条款",
            fontSize = TypeScale.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            LegalText.summaryBlocks().forEach { b -> LegalBlockView(b, compact = true) }

            Spacer(Modifier.height(6.dp))
            LegalLink("《用户协议》") { legal = "user" }
            LegalLink("《隐私政策》") { legal = "privacy" }

            Spacer(Modifier.height(Space.md))
            Text(
                "点击「同意并继续」即表示你已阅读并同意上述两份文件。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(Space.lg))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onAgree() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "同意并继续",
                fontSize = TypeScale.title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(Modifier.height(Space.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable { onDecline() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "不同意，仅使用离线功能",
                fontSize = TypeScale.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "版本 v${LegalText.LEGAL_VERSION} · 生效 ${LegalText.EFFECTIVE_DATE}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = Space.lg)
        )
    }
}

@Composable
private fun LegalLink(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = TypeScale.body,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        AppIcon("›", size = 18.dp, color = MaterialTheme.colorScheme.outline)
    }
}

/** 协议全文页。kind: "user" | "privacy" */
@Composable
fun LegalScreen(kind: String, onBack: () -> Unit) {
    // 这个页面在导航栈之外（首次同意阶段），返回键必须自己处理
    BackHandler { onBack() }

    val blocks = remember(kind) { LegalText.blocks(kind) }
    val title = if (kind == "user") "用户协议" else "隐私政策"

    Column(Modifier.fillMaxSize()) {
        TopBar(title, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(top = Space.md, bottom = Space.xl)
        ) {
            blocks.forEach { b -> LegalBlockView(b, compact = false) }
            Spacer(Modifier.height(Space.lg))
            Text(
                "反馈与侵权通知：${LegalText.CONTACT}",
                fontSize = TypeScale.caption,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** 每种块的排版。compact=true 用于首次启动页的摘要（更紧凑） */
@Composable
private fun LegalBlockView(b: LegalText.Block, compact: Boolean) {
    when (b.kind) {
        Kind.TITLE -> {
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                Text(
                    b.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
            } else {
                Text(
                    inline(b.text),
                    fontSize = TypeScale.small,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Kind.META -> {
            if (!compact) {
                Text(
                    b.text,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(2.dp))
            }
        }

        Kind.SECTION -> {
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
            Text(
                b.text,
                fontSize = if (compact) TypeScale.small else 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (compact) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
        }

        Kind.NOTICE -> {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    inline(b.text),
                    fontSize = if (compact) 11.sp else 12.sp,
                    lineHeight = if (compact) 17.sp else 19.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Kind.BULLET -> {
            Row(Modifier.padding(bottom = 5.dp)) {
                Text(
                    "·",
                    fontSize = if (compact) 11.sp else 12.sp,
                    lineHeight = if (compact) 18.sp else 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 7.dp)
                )
                Text(
                    inline(b.text),
                    fontSize = if (compact) 11.sp else 13.sp,
                    lineHeight = if (compact) 18.sp else 21.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Kind.PARA -> {
            Text(
                inline(b.text),
                fontSize = if (compact) 11.5.sp else 13.sp,
                lineHeight = if (compact) 18.sp else 21.sp,
                color = if (compact) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
            )
            Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
        }
    }
}

/** 行内 **加粗** 解析，不做完整 Markdown */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) {
            append(text.substring(i)); break
        }
        append(text.substring(i, start))
        val end = text.indexOf("**", start + 2)
        if (end < 0) {
            append(text.substring(start)); break
        }
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(text.substring(start + 2, end))
        }
        i = end + 2
    }
}
