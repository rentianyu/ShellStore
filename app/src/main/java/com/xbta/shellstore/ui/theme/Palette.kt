package com.xbta.shellstore.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 配色集中在这里，UI 里只引用 MaterialTheme.colorScheme.*，不要写死十六进制
val LightColors = lightColorScheme(
    primary = Color(0xFF1A6DFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3ECFF),
    onPrimaryContainer = Color(0xFF00174A),
    secondary = Color(0xFF4F5B75),
    background = Color(0xFFFAFBFC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF5A6270),
    outline = Color(0xFFD5DAE1),
    error = Color(0xFFB3261E),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = Color(0xFF00296B),
    primaryContainer = Color(0xFF1F3C77),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFFB9C3D9),
    background = Color(0xFF111317),
    onBackground = Color(0xFFE3E5E8),
    surface = Color(0xFF181B20),
    onSurface = Color(0xFFE3E5E8),
    surfaceVariant = Color(0xFF242830),
    onSurfaceVariant = Color(0xFFA8B0BC),
    outline = Color(0xFF3A4048),
    error = Color(0xFFF2B8B5),
)
