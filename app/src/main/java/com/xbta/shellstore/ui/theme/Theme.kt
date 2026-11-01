package com.xbta.shellstore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/**
 * 全局主题入口。
 *
 * darkTheme 默认 false（白天模式），由上层状态显式切换——不要默认跟随系统，
 * 否则同一份设计稿在不同手机上表现不一致，截图验收也无法复现。
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
