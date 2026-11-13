package com.xbta.shellstore.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 共用 24 × 24 矢量画布；尺寸使用 dp，不继承正文的字体、行高或字号缩放。
 * 保留现有符号作为索引，页面不再通过 Text 渲染图标。
 */
@Composable
fun AppIcon(
    glyph: String,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Icon(
        imageVector = AppVectors.forGlyph(glyph),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = color
    )
}

fun iconActionLabel(glyph: String): String = when (glyph) {
    "☰" -> "列表显示"
    "⌕" -> "搜索"
    "▽" -> "筛选"
    "☑" -> "多选"
    "★" -> "取消收藏"
    "☆" -> "收藏"
    "⋯" -> "更多操作"
    "✎" -> "编辑"
    "‹" -> "返回"
    "✕" -> "关闭"
    "+" -> "添加"
    "↻", "⟳" -> "刷新"
    else -> "操作"
}

private object AppVectors {
    private fun vector(name: String, path: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ).build()

    private const val STAR = "M12,3 L14.8,8.7 L21,9.6 L16.5,14 L17.6,20.2 L12,17.3 L6.4,20.2 L7.5,14 L3,9.6 L9.2,8.7 Z"
    private const val CIRCLE = "M21,12 A9,9 0,1 1,3,12 A9,9 0,1 1,21,12 Z"
    private val grid by lazy { vector("Grid", "M3,3 H9 V9 H3 Z M15,3 H21 V9 H15 Z M3,15 H9 V21 H3 Z M15,15 H21 V21 H15 Z") }
    private val home by lazy { vector("Home", "M4,10 L12,3 L20,10 L20,20 C20,20.5 19.5,21 19,21 L15,21 L15,15 L9,15 L9,21 L5,21 C4.5,21 4,20.5 4,20 Z") }
    private val star by lazy { vector("Star", STAR) }
    private val starFilled by lazy { vector("StarFilled", STAR, filled = true) }
    private val person by lazy { vector("Person", "M16,7 A4,4 0,1 1,8,7 A4,4 0,1 1,16,7 Z M4,21 V19 C4,15.5 7,14 12,14 C17,14 20,15.5 20,19 V21") }
    private val menu by lazy { vector("Menu", "M4,6 H20 M4,12 H20 M4,18 H20") }
    private val search by lazy { vector("Search", "M17,10 A7,7 0,1 1,3,10 A7,7 0,1 1,17,10 Z M15,15 L21,21") }
    private val edit by lazy { vector("Edit", "M4,15 L15,4 C16,3 17,3 18,4 L20,6 C21,7 21,8 20,9 L9,20 L3,21 Z M13,6 L18,11 M4,15 L9,20") }
    private val back by lazy { vector("Back", "M15,5 L8,12 L15,19") }
    private val next by lazy { vector("Next", "M9,5 L16,12 L9,19") }
    private val close by lazy { vector("Close", "M6,6 L18,18 M18,6 L6,18") }
    private val check by lazy { vector("Check", "M4,12 L9,17 L20,6") }
    private val select by lazy { vector("Select", "M20,13 V20 H4 V4 H14 M9,10 L12,13 L21,4") }
    private val plus by lazy { vector("Plus", "M12,4 V20 M4,12 H20") }
    private val more by lazy { vector("More", "M5,11 A1,1 0,1 1,5,13 A1,1 0,1 1,5,11 Z M12,11 A1,1 0,1 1,12,13 A1,1 0,1 1,12,11 Z M19,11 A1,1 0,1 1,19,13 A1,1 0,1 1,19,11 Z", filled = true) }
    private val terminal by lazy { vector("Terminal", "M4,5 L11,12 L4,19 M13,19 H21") }
    /** 筛选漏斗 */
    private val funnel by lazy { vector("Funnel", "M3,5 H21 L14,13.5 V20 L10,17.5 V13.5 Z") }
    private val document by lazy { vector("Document", "M5,3 H15 L20,8 V21 H5 Z M15,3 V8 H20 M8,12 H17 M8,16 H17") }
    private val lines by lazy { vector("Lines", "M4,5 H20 M4,10 H20 M4,15 H16 M4,20 H12") }
    private val external by lazy { vector("External", "M14,3 H21 V10 M21,3 L10,14 M10,4 H4 V20 H20 V14") }
    private val target by lazy { vector("Target", "$CIRCLE M16,12 A4,4 0,1 1,8,12 A4,4 0,1 1,16,12 Z") }
    private val window by lazy { vector("Window", "M3,4 H21 V20 H3 Z M3,9 H21 M7,6.5 H7.1 M10,6.5 H10.1") }
    private val settings by lazy { vector("Settings", "M9,3 H15 L16,6 L19,7 L21,10 L19,12 L21,14 L19,17 L16,18 L15,21 H9 L8,18 L5,17 L3,14 L5,12 L3,10 L5,7 L8,6 Z M15.5,12 A3.5,3.5 0,1 1,8.5,12 A3.5,3.5 0,1 1,15.5,12 Z") }
    private val broadcast by lazy { vector("Broadcast", "M8,8 A5.7,5.7 0,0 0,8,16 M16,8 A5.7,5.7 0,0 1,16,16 M5,5 A10,10 0,0 0,5,19 M19,5 A10,10 0,0 1,19,19 M13,12 A1,1 0,1 1,11,12 A1,1 0,1 1,13,12 Z") }
    private val clock by lazy { vector("Clock", "$CIRCLE M12,6 V12 L16,14") }
    private val transfer by lazy { vector("Transfer", "M7,3 V21 M3,7 L7,3 L11,7 M17,3 V21 M13,17 L17,21 L21,17") }
    private val refresh by lazy { vector("Refresh", "M20,8 A8.5,8.5 0,1 0,20,16 M20,3 V8 H15") }
    private val help by lazy { vector("Help", "$CIRCLE M9,9 A3,3 0,1 1,13,11.8 L12,12.5 V14 M12,17 H12.01") }
    private val info by lazy { vector("Info", "$CIRCLE M12,11 V17 M12,7 H12.01") }
    private val play by lazy { vector("Play", "M7,3 L21,12 L7,21 Z") }
    private val upload by lazy { vector("Upload", "M12,16 V3 M7,8 L12,3 L17,8 M4,16 V21 H20 V16") }
    private val download by lazy { vector("Download", "M12,3 V16 M7,11 L12,16 L17,11 M4,17 V21 H20 V17") }
    private val copy by lazy { vector("Copy", "M8,8 H21 V21 H8 Z M16,8 V3 H3 V16 H8") }
    private val warning by lazy { vector("Warning", "M12,3 L22,21 H2 Z M12,9 V14 M12,17 H12.01") }
    private val forbidden by lazy { vector("Forbidden", "$CIRCLE M5.6,5.6 L18.4,18.4") }
    private val type by lazy { vector("Type", "M3,20 L10,4 L17,20 M6,14 H14 M17,4 H23 M20,4 V12") }
    private val code by lazy { vector("Code", "M8,5 L2,12 L8,19 M16,5 L22,12 L16,19 M14,3 L10,21") }
    private val balance by lazy { vector("Balance", "M12,3 V21 M7,21 H17 M4,6 H20 M5,6 L1.5,14 H8.5 Z M19,6 L15.5,14 H22.5 Z") }
    private val bolt by lazy { vector("Bolt", "M13,2 L4,14 H11 L10,22 L20,9 H13 Z") }
    private val sparkle by lazy { vector("Sparkle", "M12,2 L15,9 L22,12 L15,15 L12,22 L9,15 L2,12 L9,9 Z") }
    private val keyboard by lazy { vector("Keyboard", "M2,5 H22 V19 H2 Z M6,9 H6.1 M10,9 H10.1 M14,9 H14.1 M18,9 H18.1 M6,12 H6.1 M10,12 H10.1 M14,12 H14.1 M18,12 H18.1 M7,16 H17") }
    private val key by lazy { vector("Key", "M11,8 A5,5 0,1 1,1,8 A5,5 0,1 1,11,8 Z M10,12 L20,22 M16,18 L19,15 M19,21 L22,18") }
    private val square by lazy { vector("Square", "M5,5 H19 V19 H5 Z") }

    fun forGlyph(glyph: String): ImageVector = when (glyph) {
        "▦" -> grid
        "☆" -> star
        "★" -> starFilled
        "◍" -> person
        "☰" -> menu
        "⌕" -> search
        "✎" -> edit
        "‹" -> back
        "›" -> next
        "✕" -> close
        "✓" -> check
        "☑" -> select
        "+" -> plus
        "⋯" -> more
        ">_" -> terminal
        "▤", "§" -> document
        "≡" -> lines
        "↗" -> external
        "◎" -> target
        "▣" -> window
        "⌂" -> home
        "⚙" -> settings
        "◉" -> broadcast
        "◷", "◔" -> clock
        "⇅" -> transfer
        "↻", "⟳" -> refresh
        "？", "?" -> help
        "ⓘ" -> info
        "▶" -> play
        "⇧" -> upload
        "⇩" -> download
        "⧉" -> copy
        "⚠" -> warning
        "⊘" -> forbidden
        "字" -> type
        "⌘" -> code
        "⚖" -> balance
        "⚡" -> bolt
        "✦" -> sparkle
        "⌨" -> keyboard
        "▽", "⏷" -> funnel
        "⚿" -> key
        else -> square
    }
}
