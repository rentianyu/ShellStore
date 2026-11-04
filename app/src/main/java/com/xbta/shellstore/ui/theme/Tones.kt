package com.xbta.shellstore.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 语义色（风险等级、代码块、徽章）。
 *
 * 这里原来是一套写死的浅色值，注释还写着「与主题无关，深浅色通用」——实际深色模式下
 * 浅底深字全部沿用，标签发白、代码块刺眼、文字看不清。现在拆成浅/深两套，
 * 由 AppTheme 通过 [LocalSemanticColors] 注入，Tones 只做取值入口，调用点不用改。
 */
data class SemanticColors(
    val safe: Color,
    val safeBg: Color,
    val caution: Color,
    val cautionBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val neutralFg: Color,
    val neutralBg: Color,
    val codeFg: Color,
    val codeBg: Color,
)

/** 浅色：沿用原值，白天观感不变 */
val LightSemantic = SemanticColors(
    safe = Color(0xFF12854A),
    safeBg = Color(0xFFE8F7EE),
    caution = Color(0xFFA9660A),
    cautionBg = Color(0xFFFDF3E3),
    danger = Color(0xFFC3271F),
    dangerBg = Color(0xFFFDECEC),
    neutralFg = Color(0xFF5E5C60),
    neutralBg = Color(0xFFE9E6EA),
    codeFg = Color(0xFF1B1B1E),
    codeBg = Color(0xFFE9E6EA),
)

/**
 * 深色：底色是 #131315 / #252628，前景色按 WCAG AA 重新挑过
 * （浅底深字 → 深底浅字，底色改成低亮度同色相，避免整片发白）。
 */
val DarkSemantic = SemanticColors(
    safe = Color(0xFF7FDCA4),
    safeBg = Color(0xFF1D3327),
    caution = Color(0xFFF2BE6B),
    cautionBg = Color(0xFF35290F),
    danger = Color(0xFFFF9A93),
    dangerBg = Color(0xFF3A2020),
    neutralFg = Color(0xFFC7C6CA),
    neutralBg = Color(0xFF35353A),
    codeFg = Color(0xFFE4E2E4),
    codeBg = Color(0xFF1B1B1E),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

/**
 * 语义色入口。用 @Composable getter 是为了跟随主题重组——
 * 调用点保持 `Tones.safe` 的写法不变，但必须在 @Composable 上下文里读。
 */
object Tones {
    val safe: Color @Composable get() = LocalSemanticColors.current.safe
    val safeBg: Color @Composable get() = LocalSemanticColors.current.safeBg
    val caution: Color @Composable get() = LocalSemanticColors.current.caution
    val cautionBg: Color @Composable get() = LocalSemanticColors.current.cautionBg
    val danger: Color @Composable get() = LocalSemanticColors.current.danger
    val dangerBg: Color @Composable get() = LocalSemanticColors.current.dangerBg
    val neutralFg: Color @Composable get() = LocalSemanticColors.current.neutralFg
    val neutralBg: Color @Composable get() = LocalSemanticColors.current.neutralBg
    val codeFg: Color @Composable get() = LocalSemanticColors.current.codeFg
    val codeBg: Color @Composable get() = LocalSemanticColors.current.codeBg
}
