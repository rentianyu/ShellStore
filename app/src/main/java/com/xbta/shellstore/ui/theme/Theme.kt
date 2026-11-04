package com.xbta.shellstore.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 全局主题入口。
 *
 * darkTheme 默认 false（白天模式），由上层状态显式切换。
 * fontFamily 为 null 时沿用系统默认字体（中文显示用厂商字体，效果通常最好）。
 */
/** 形状档位跟着 FlClash 走：8 / 12 / 16 / 24 / 28，比 Material 默认更圆 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    fontFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val base = LocalTextStyle.current
    val textStyle = if (fontFamily != null) base.copy(fontFamily = fontFamily) else base
    CompositionLocalProvider(
        LocalTextStyle provides textStyle,
        LocalSemanticColors provides if (darkTheme) DarkSemantic else LightSemantic,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
        ) {
            // 关键：没有这一层 Surface，LocalContentColor 会回退成纯黑，
            // 所有「没显式写 color」的 Text（分类名、卡片标题、分组标题）在深色模式下就是黑字配深底。
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
