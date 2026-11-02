package com.xbta.shellstore.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 配色规则参照 FlClash（chen08209/FlClash，GPL-3.0）的**设计思路**，代码为独立实现，
 * 只借鉴"用色原则"不复制其源码：
 *  - primary 直接取 seed 色（#007AFF），不用 Material 默认的 tone 40，所以主色更"正"、不发灰
 *  - surface 系列保留 Material 的 tone，但把 chroma 截到 3（浅色的 surface/surfaceBright 截到 1.5），
 *    页面是有极轻色调的灰，不是死灰也不是纯白
 *  - 深色 surface 自定 tone：6 / 15 / 21 / 27，步进比 Material 更大，层级才看得出来
 *
 * 色值由 `.ci/gen_flclash_theme.py` 生成，换 seed 就重跑那个脚本。
 * 分层靠填充差异，不靠深色边框：page = surfaceContainer，card = surfaceBright。
 */
val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF0E448E),
    secondary = Color(0xFF405E96),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFA1BEFD),
    onSecondaryContainer = Color(0xFF2D4C83),
    tertiary = Color(0xFF8E2CB1),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA949CD),
    onTertiaryContainer = Color(0xFFFFFBFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFEFEDF0),
    onBackground = Color(0xFF1B1B1E),
    surface = Color(0xFFFCF8F9),
    onSurface = Color(0xFF1B1B1E),
    surfaceVariant = Color(0xFFE4E2E4),
    onSurfaceVariant = Color(0xFF474649),
    surfaceTint = Color(0xFF007AFF),
    inverseSurface = Color(0xFF2D3039),
    inverseOnSurface = Color(0xFFEEF0FC),
    inversePrimary = Color(0xFFADC6FF),
    surfaceDim = Color(0xFFDBD9DC),
    surfaceBright = Color(0xFFFCF8F9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F6),
    surfaceContainer = Color(0xFFEFEDF0),
    surfaceContainerHigh = Color(0xFFEAE7EA),
    surfaceContainerHighest = Color(0xFFE4E2E4),
    outline = Color(0xFF777779),
    outlineVariant = Color(0xFFC7C6CA),
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF2F83FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0E448E),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFADC6FF),
    onSecondary = Color(0xFF082F65),
    secondaryContainer = Color(0xFF26467D),
    onSecondaryContainer = Color(0xFF98B5F3),
    tertiary = Color(0xFFEEB1FF),
    onTertiary = Color(0xFF53006F),
    tertiaryContainer = Color(0xFFC967EC),
    onTertiaryContainer = Color(0xFF290039),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    // 注意：深色下 background 用 tone 6、surface 用 tone 15。
    // FlClash 的深色页面与卡片就是 6/15 两个 tone，同色会让卡片完全看不出边界。
    background = Color(0xFF131315),
    onBackground = Color(0xFFE4E2E4),
    surface = Color(0xFF252628),
    onSurface = Color(0xFFE4E2E4),
    surfaceVariant = Color(0xFF46464A),
    onSurfaceVariant = Color(0xFFC7C6CA),
    surfaceTint = Color(0xFF2F83FF),
    inverseSurface = Color(0xFFE0E2ED),
    inverseOnSurface = Color(0xFF2D3039),
    inversePrimary = Color(0xFF005BC1),
    surfaceDim = Color(0xFF131315),
    surfaceBright = Color(0xFF39393B),
    surfaceContainerLowest = Color(0xFF0D0E10),
    surfaceContainerLow = Color(0xFF1B1B1E),
    surfaceContainer = Color(0xFF252628),
    surfaceContainerHigh = Color(0xFF323235),
    surfaceContainerHighest = Color(0xFF404042),
    outline = Color(0xFF919093),
    outlineVariant = Color(0xFF46464A),
    scrim = Color(0xFF000000),
)
