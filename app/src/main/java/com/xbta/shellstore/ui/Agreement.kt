package com.xbta.shellstore.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.LegalText
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 首次启动的协议同意页。
 *
 * 合规要点（《个人信息保护法》与 App 违规收集使用个人信息认定方法）：
 *  - 首次运行以显著方式提示，**不默认勾选**
 *  - 同时提供「同意」与「不同意」两个按钮
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
    if (openKind != null) {
        LegalScreen(openKind) { legal = null }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(46.dp))

        Text(
            "欢迎使用 ${LegalText.APP_NAME}",
            fontSize = TypeScale.title,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "使用前请阅读并同意以下条款",
            fontSize = TypeScale.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Space.lg))

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                LegalText.CONSENT_SUMMARY,
                fontSize = TypeScale.small,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(Space.lg))

            LegalLink("《用户协议》") { legal = "user" }
            Spacer(Modifier.height(Space.sm))
            LegalLink("《隐私政策》") { legal = "privacy" }

            Spacer(Modifier.height(Space.md))
            Text(
                "点击「同意并继续」即表示你已阅读并同意上述两份文件。" +
                    "本软件不提供、不存储任何订阅源内容，订阅源由你自行添加并负责；" +
                    "命令由你自行决定是否执行，后果自负。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(Space.lg))
        }

        // 主按钮：同意
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

        Spacer(Modifier.height(Space.md))
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
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = TypeScale.body,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        Text("›", fontSize = TypeScale.body, color = MaterialTheme.colorScheme.outline)
    }
}

/** 协议全文阅读页。kind: "user" | "privacy" */
@Composable
fun LegalScreen(kind: String, onBack: () -> Unit) {
    val isUser = kind == "user"
    Column(Modifier.fillMaxSize()) {
        TopBar(if (isUser) "用户协议" else "隐私政策", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(top = Space.sm, bottom = Space.xl)
        ) {
            Text(
                if (isUser) LegalText.USER_AGREEMENT else LegalText.PRIVACY_POLICY,
                fontSize = TypeScale.small,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
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
