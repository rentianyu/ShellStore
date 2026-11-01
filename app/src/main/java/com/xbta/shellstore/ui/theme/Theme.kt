package com.xbta.shellstore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * 全局主题入口。
 *
 * darkTheme 默认 false（白天模式），由上层状态显式切换。
 * fontFamily 为 null 时沿用系统默认字体（中文显示用厂商字体，效果通常最好）。
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    fontFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val base = LocalTextStyle.current
    val textStyle = if (fontFamily != null) base.copy(fontFamily = fontFamily) else base
    CompositionLocalProvider(LocalTextStyle provides textStyle) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
