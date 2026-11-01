package com.xbta.shellstore.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计 token。所有尺寸只从这里取，UI 里不写字面量。
 * 规则：间距只用 4 的倍数；字号只用六档；圆角只用三档。
 */

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
}

/** 字号六档：统一用系统字体，字号偏大以保证可读性 */
object TypeScale {
    /** 徽章、脚注 */
    val caption = 11.5.sp

    /** 次要说明、条数、元信息 */
    val small = 13.sp

    /** 正文、卡片标题、列表项 */
    val body = 15.sp

    /** 区块标题 */
    val title = 16.sp

    /** 顶栏标题 */
    val headline = 19.sp

    /** 卡片内命令预览 */
    val code = 13.sp

    /** 详情页命令正文 */
    val codeLarge = 14.sp
}

object Dimen {
    val topBar = 54.dp
    val tabBar = 58.dp
    val button = 42.dp
    val buttonSmall = 36.dp
    val iconButton = 34.dp
    val badgeIcon = 20.dp
    val listIcon = 26.dp
}
