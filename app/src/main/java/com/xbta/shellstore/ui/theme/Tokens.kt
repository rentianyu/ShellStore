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

    /**
     * 页面左右留白。14dp 是 4 栅格的例外：正文 15sp + 14dp 两侧留白才有合适的行长，
     * 改成 16 会让内容区明显变窄。单独定义成一档，避免 14.dp 散落在几十个地方。
     */
    val page = 24.dp
}

/**
 * 圆角档位对齐 FlClash（AppCorner: 4/8/16/20/24/28）。
 * 这里保留三档：小控件 8、卡片 16、容器与弹层 24。
 */
object Radius {
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
}

/** 字号六档：统一用系统字体，字号偏大以保证可读性 */
object TypeScale {
    /** 徽章、脚注 */
    val caption = 11.5.sp

    /** 次要说明、条数、元信息 */
    val small = 13.sp

    /** 正文、卡片标题、列表项 */
    val body = 15.sp

    /** 区块标题（对齐 FlClash 的 headline 17） */
    val title = 17.sp

    /** 顶栏标题（对齐 FlClash 的 title3 20） */
    val headline = 20.sp

    /** 卡片内命令预览 */
    val code = 13.sp

    /** 详情页命令正文 */
    val codeLarge = 14.sp
}

/**
 * 图标字号档位。UI 上的字形图标（非图片）统一从这里取字号，
 * 调整图标大小只改这一处，避免各处散落字面量。
 */
object IconSize {
    /** 底部 tab 图标 */
    val tab = 30.sp

    /** 顶栏操作按钮（搜索、视图、加号） */
    val top = 24.sp

    /** 顶栏返回箭头 */
    val nav = 32.sp

    /** 列表卡片右侧操作（⋯） */
    val action = 19.sp

    /** 分类卡片 / 卡片内装饰图标 */
    val card = 18.sp

    /** 搜索框内图标 */
    val inline = 17.sp

    /** 行尾「›」箭头 */
    val chevron = 18.sp

    /** 勾选框内的对勾 */
    val check = 13.sp
}

object Dimen {
    val topBar = 56.dp
    val tabBar = 64.dp
    val button = 42.dp
    val buttonSmall = 36.dp

    /** 图标按钮的点击热区。按无障碍建议取 48dp（顶栏 54dp 高，上下各余 3dp） */
    val iconButton = 48.dp

    /** 类型徽章方块边长 */
    val badgeIcon = 24.dp
    val listIcon = 30.dp

    /** 多选勾选框边长 */
    val checkbox = 22.dp
}
