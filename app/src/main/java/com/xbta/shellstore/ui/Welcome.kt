package com.xbta.shellstore.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.R
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首次启动的欢迎页。内置命令集已移除，命令全部来自在线源，
 * 所以第一步必须让用户明确启用官方源（或者自己去添加）。
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun enable() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            val err = withContext(Dispatchers.IO) { Store.enableOfficial() }
            busy = false
            if (err == null) onDone() else error = err
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.weight(1f))

        // 图标
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().scale(1.55f)
            )
        }

        Spacer(Modifier.height(Space.xl))
        Text("Shell 商店", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Space.sm))
        Text(
            "命令、脚本、URL Scheme、Intent 的查询与执行入口",
            fontSize = TypeScale.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(Space.xl))

        // 说明卡
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Space.lg)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text("1", fontSize = TypeScale.small, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "内置命令已移除，内容全部来自在线源，随时可在源头更新。",
                    fontSize = TypeScale.small,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.Top) {
                Text("2", fontSize = TypeScale.small, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "官方命令库托管在 GitHub，按分类整理，支持增量更新。",
                    fontSize = TypeScale.small,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.Top) {
                Text("3", fontSize = TypeScale.small, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "也可以稍后自己添加源：链接、本地 .md 文件都行。",
                    fontSize = TypeScale.small,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val err = error
        if (err != null) {
            Spacer(Modifier.height(Space.md))
            Text(
                err,
                fontSize = TypeScale.small,
                lineHeight = 19.sp,
                color = Tones.danger
            )
        }

        Spacer(Modifier.weight(1f))

        // 主按钮
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (busy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                .clickable(enabled = !busy) { enable() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (busy) "正在拉取官方源…" else if (err != null) "重试" else "启用官方命令库",
                fontSize = TypeScale.title,
                fontWeight = FontWeight.Bold,
                color = if (busy) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(Modifier.height(Space.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(enabled = !busy) { onDone() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "先跳过，我自己添加源",
                fontSize = TypeScale.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Space.md))
        Text(
            Store.OFFICIAL_URL,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = Space.xl)
        )
    }
}
