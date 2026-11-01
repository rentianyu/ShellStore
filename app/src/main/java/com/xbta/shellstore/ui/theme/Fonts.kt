package com.xbta.shellstore.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.xbta.shellstore.data.FontChoice
import java.io.File

/**
 * 字体策略：默认全部使用系统字体（各厂商字体对中文的适配最好），
 * 不内置任何字体文件；用户若导入自己的 ttf 则全局替换。
 */

/** 代码字体在当前组合树中的值；默认跟随系统等宽 */
val LocalCodeFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

/** 解析界面字体。返回 null 表示不覆盖，沿用系统默认 */
fun resolveUiFont(choice: FontChoice, customPath: String): FontFamily? = when (choice) {
    FontChoice.SYSTEM -> null
    FontChoice.CUSTOM -> loadCustomFont(customPath)
}

/** 解析代码字体。失败回落到系统等宽 */
fun resolveCodeFont(choice: FontChoice, customPath: String): FontFamily = when (choice) {
    FontChoice.SYSTEM -> FontFamily.Monospace
    FontChoice.CUSTOM -> loadCustomFont(customPath) ?: FontFamily.Monospace
}

/** 从本地文件加载字体；文件缺失或损坏时返回 null */
fun loadCustomFont(path: String): FontFamily? {
    if (path.isBlank()) return null
    val f = File(path)
    if (!f.exists() || f.length() < 1024) return null
    return runCatching { FontFamily(Typeface.createFromFile(f)) }.getOrNull()
}
