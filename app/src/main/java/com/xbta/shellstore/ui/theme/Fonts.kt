package com.xbta.shellstore.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.xbta.shellstore.R
import com.xbta.shellstore.data.FontChoice
import java.io.File

/** 内置 JetBrains Mono：代码块默认字体，比系统 monospace 明显更清晰 */
val JbMono = FontFamily(
    Font(R.font.jb_mono_regular, FontWeight.Normal),
    Font(R.font.jb_mono_bold, FontWeight.Bold)
)

/** 代码字体在当前组合树中的值 */
val LocalCodeFont = staticCompositionLocalOf<FontFamily> { JbMono }

/**
 * 解析界面字体。返回 null 表示不覆盖，沿用系统默认。
 */
fun resolveUiFont(choice: FontChoice, customPath: String): FontFamily? = when (choice) {
    FontChoice.SYSTEM -> null
    FontChoice.JETBRAINS -> JbMono
    FontChoice.CUSTOM -> loadCustomFont(customPath)
}

/**
 * 解析代码字体。永远返回可用值（失败回落到内置 JetBrains Mono）。
 */
fun resolveCodeFont(choice: FontChoice, customPath: String): FontFamily = when (choice) {
    FontChoice.SYSTEM -> FontFamily.Monospace
    FontChoice.JETBRAINS -> JbMono
    FontChoice.CUSTOM -> loadCustomFont(customPath) ?: JbMono
}

/** 从本地文件加载字体；文件缺失或损坏时返回 null */
fun loadCustomFont(path: String): FontFamily? {
    if (path.isBlank()) return null
    val f = File(path)
    if (!f.exists() || f.length() < 1024) return null
    return runCatching { FontFamily(Typeface.createFromFile(f)) }.getOrNull()
}
