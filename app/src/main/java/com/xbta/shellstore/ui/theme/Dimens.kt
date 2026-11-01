package com.xbta.shellstore.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距/圆角 token。UI 里不写字面量 dp，改版式只改这里。
 * 尺度取 4dp 栅格，避免出现 13dp/17dp 这类随手值导致对不齐。
 */
object Dimens {
    val gapXs = 4.dp
    val gapS = 8.dp
    val gapM = 12.dp
    val gapL = 16.dp
    val gapXl = 24.dp

    val pageH = 16.dp      // 页面左右留白
    val cornerS = 6.dp
    val cornerM = 10.dp
    val cornerL = 16.dp

    val rowMinHeight = 48.dp  // 可点击行的最小高度（触控目标）
    val hairline = 1.dp
}
