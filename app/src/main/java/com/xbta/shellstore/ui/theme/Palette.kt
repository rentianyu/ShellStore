package com.xbta.shellstore.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 配色集中在这里，UI 里只引用 MaterialTheme.colorScheme.*，不要写死十六进制
//
// 分层靠「底色 vs 卡片白」的对比，不靠深色边框：
//   background #F3F5F8  /  surface #FFFFFF  /  outline 很淡，只做轻微收边
val LightColors = lightColorScheme(
    primary = Color(0xFF2F6BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9F0FF),
    onPrimaryContainer = Color(0xFF0B2E7A),
    secondary = Color(0xFF4F5B75),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF17191C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17191C),
    surfaceVariant = Color(0xFFEFF2F6),
    onSurfaceVariant = Color(0xFF5E6673),
    outline = Color(0xFFE1E5EB),
    outlineVariant = Color(0xFFEDF0F4),
    error = Color(0xFFC3271F),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = Color(0xFF00296B),
    primaryContainer = Color(0xFF1B2C4E),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFFB8C0D0),
    onSecondary = Color(0xFF20242B),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF23272D),
    onSurfaceVariant = Color(0xFFA4ADBA),
    outline = Color(0xFF2E333A),
    outlineVariant = Color(0xFF262A30),
    error = Color(0xFFFF8A80),
)
