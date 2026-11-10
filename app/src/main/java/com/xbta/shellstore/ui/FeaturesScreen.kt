package com.xbta.shellstore.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 功能说明文档。**纯文字**：只有标题、段落和条目，整页没有一个可点的地方。
 *
 * 这一页回答「这个 App 能做什么」，不承担导航职责 —— 想用某个功能请到对应页面，
 * 所以这里刻意不写「点这里去 XX」的入口。
 */
@Composable
fun FeaturesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("功能说明", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = Space.xl)
        ) {
            Spacer(Modifier.height(Space.sm))

            Text(
                "ShellStore 是一个离线的 shell 命令与 App 快捷方式管理器：把常用命令、URL scheme、" +
                    "Intent 整理成卡片，需要时一键执行、复制或做成桌面图标。下面按用途说明各项能力。",
                fontSize = TypeScale.small,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            for (sec in SECTIONS) {
                DocH(sec.title)
                for (p in sec.paras) DocP(p)
                for (it in sec.items) DocLine(it.name, it.text)
            }

            DocH("联网说明")
            DocP("上述功能全部离线可用。会联网的只有四处：拉取订阅源内容、检查 App 自身更新、云备份的上传与恢复、以及 scheme 中明确涉及网络的动作（加源、刷新、检查更新、备份）。除此之外不发出任何请求。")

            Spacer(Modifier.height(Space.lg))
        }
    }
}

private data class DocItem(val name: String, val text: String)
private data class DocSection(val title: String, val paras: List<String> = emptyList(), val items: List<DocItem>)

@Composable
private fun DocH(text: String) {
    Text(
        text,
        fontSize = TypeScale.body,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.xs)
    )
}

@Composable
private fun DocP(text: String) {
    Text(
        text,
        fontSize = TypeScale.small,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Space.sm)
    )
}

/** 一条功能：名称加粗 + 说明，同一段落内换行，不带任何交互 */
@Composable
private fun DocLine(name: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)) {
                append("· ")
                append(name)
                append("　")
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(text) }
        },
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(bottom = Space.sm)
    )
}

private val SECTIONS: List<DocSection> = listOf(

    DocSection(
        "一、浏览与查找",
        listOf(
            "命令来自订阅源，按分类组织。首页纵向列出全部分类与各自的条数，进入分类后是该类的条目列表。"
        ),
        listOf(
            DocItem("搜索", "对标题、命令正文、描述、标签、参数名做全文匹配。多个关键词用空格分隔，需同时命中（AND）；标题命中的排在前面。支持拼音首字母，输入 dc 可命中「多点」这类开头为 dc 的标题。结果中命中的词会高亮。搜索框留空时显示最近搜索过的词。"),
            DocItem("筛选", "可按条目类型（shell、script、url scheme、intent、activity、service、broadcast 七种）和风险等级（安全、谨慎、危险）组合过滤。"),
            DocItem("最近使用", "打开过的条目按时间倒序保留，条数有上限，可在设置里清空。"),
            DocItem("刷新订阅源", "重新拉取所有已启用的源并解析，本地收藏与对收藏条目的编辑不受影响。")
        )
    ),

    DocSection(
        "二、查看与使用一条命令",
        listOf(
            "条目详情页是主要操作界面。上半部分是命令内容与参数，下半部分是来源、风险、运行条件等属性。"
        ),
        listOf(
            DocItem("执行", "shell 与 script 类型的条目用 App 自身权限调用 sh 执行，不具备 root 权限；需要更高权限时可选 Shizuku（免 root 的授权通道）或 Root。执行输出会按设置以 Toast、通知或弹窗展示。"),
            DocItem("发送 Intent", "url scheme、activity、intent、broadcast、service 五类条目不经过 shell，直接构造 Intent 交给系统。这类条目通常是「打开某个 App 的某个页面」，用 shell 执行会失败。"),
            DocItem("复制", "可复制原始内容，也可复制成等价的 adb 命令（am start / am broadcast 形式），便于在电脑上复现。复制时可自动加 adb shell 或 su -c 前缀。"),
            DocItem("参数占位符", "命令里写 {键} 的地方会在详情页生成输入框，填入后命令实时重算。参数值支持一键解码、展开 JSON 子字段、URL 编码与解码；修改子字段会按原形态写回，原来是编码的仍编码，原来是明文的仍明文。"),
            DocItem("多命令变体", "同一个条目可以写多个代码块，详情页用左右箭头切换，每个变体有各自的参数。"),
            DocItem("风险与运行条件", "每条有风险标记（安全 / 谨慎 / 危险）与运行条件（是否需要 root、是否依赖某个 App）。执行危险条目时默认二次确认，可在设置中关闭。"),
            DocItem("桌面快捷方式", "把一条命令固定到桌面，点击即执行，参数使用填好的值。"),
            DocItem("控制中心磁贴", "把命令放进下拉快捷开关，从通知栏直接触发。"),
            DocItem("导出为独立 APK", "把一条命令打包成一个独立的小 App，可自定义名称、包名、图标文字或图片、执行身份（shell / root / shizuku）、结果提示方式与超时时间。导出后可直接安装或分享。")
        )
    ),

    DocSection(
        "三、收藏与整理",
        listOf(
            "收藏是把源里的条目复制一份到本地，源更新或换源都不会影响已收藏的内容；本地条目也可以直接新建，与其他条目一样使用。"
        ),
        listOf(
            DocItem("收藏文件夹", "收藏时可选择文件夹。若关掉「收藏时询问文件夹」，则一律进入默认位置。"),
            DocItem("排序", "收藏列表支持长按拖动调整顺序，松手即保存。"),
            DocItem("多选批量操作", "进入多选后可批量移动到其他文件夹、批量删除、批量打标签。"),
            DocItem("置顶", "单条收藏可置顶，列表中最前显示。"),
            DocItem("标签", "一条命令可挂多个标签，标签视图中按标签交叉查看。"),
            DocItem("导出收藏", "收藏可导出为标准 SMD 文本，直接作为订阅源分享给他人。")
        )
    ),

    DocSection(
        "四、订阅源",
        listOf(
            "订阅源是命令的来源，格式为 SMD（Markdown 的一个子集，代码块正文即命令）。一个源可以有多个分类，每个分类下若干条目。"
        ),
        listOf(
            DocItem("添加方式", "填写远程 SMD 地址、粘贴一段 SMD 文本，或从本地文件导入。远程地址支持 GitHub 链接并自动走镜像加速。"),
            DocItem("内置系列", "「小贝塔订阅源」是一份可逐项开关的系列清单。此前手动添加过的同一个源会自动归入系列，不会重复出现两条。"),
            DocItem("更新检查", "启动时会拉取各源的新内容并计算差异（分类与条数的增减），列出来供确认；同意后才写入缓存并重新解析，取消则丢弃候选内容，不会悄悄改动本地库。"),
            DocItem("多线路加速", "GitHub 地址会在多个公共镜像与自建反代之间选择较快的线路，失败自动换下一条，并记住上次成功的线路供下次优先使用。"),
            DocItem("格式说明", "App 内另有「订阅源格式说明」一节，讲字段含义、分类写法、参数占位符与常见错误。")
        )
    ),

    DocSection(
        "五、备份与恢复",
        listOf(
            "备份内容为收藏（含本地条目、文件夹、标签）与设置项，不含 WebDAV 密码与 S3 SecretKey —— 这两项单独加密保存在系统密钥库中，不进入备份文件。"
        ),
        listOf(
            DocItem("WebDAV", "填写服务地址、用户名、密码与远端路径即可。密码使用 Android Keystore 加密保存。"),
            DocItem("S3 兼容存储", "填写 Endpoint、Region、Bucket、AccessKey、SecretKey 与对象键。请求签名由 App 自行实现，不依赖第三方 SDK；支持虚拟主机与路径风格两种寻址方式。"),
            DocItem("自动备份", "数据变更后重新计时 30 秒，安静下来才上传一次，避免连续修改触发多次上传。只走当前选中的那一个后端。"),
            DocItem("导出与导入", "整份数据可导出为 JSON 文本（可复制到剪贴板或存为文件）；导入时可选覆盖或合并。"),
            DocItem("云端恢复", "拉取最近一次备份并覆盖本地数据，此操作不可撤销，执行前会二次确认。")
        )
    ),

    DocSection(
        "六、外观与阅读设置",
        listOf(),
        listOf(
            DocItem("字号", "10 档，只影响文字大小（sp），控件尺寸不变，最大约 140%。"),
            DocItem("界面缩放", "5 档，同时放大控件与文字（dp 与 sp），适合大屏或点击不便的场景，可与字号叠加。"),
            DocItem("主题", "浅色、深色、跟随系统三选，默认浅色。"),
            DocItem("字体", "界面字体与代码字体可分别设置，支持导入自定义字体文件；代码区始终优先使用等宽字体。"),
            DocItem("列表样式", "卡片样式信息更全；紧凑列表一行一条、仅细分隔线，一屏可见条目更多。"),
            DocItem("折叠预览", "开启后列表只显示标题，收起命令正文预览。")
        )
    ),

    DocSection(
        "七、自动化",
        listOf(
            "App 的每一项功能都可以从外部用 scheme 调起，形式为 shellstore://<动作>?<参数>，覆盖导航、执行、收藏、订阅源管理、备份与设置修改。",
            "调用方式包括 adb（am start -a android.intent.action.VIEW -d \"shellstore://...\"）、Tasker 等自动化工具的 VIEW Intent，以及在备忘录或浏览器中直接点击。完整动作表见「我的 › 关于 › scheme 用法」，那里每一行都可复制。",
            "其中会改动数据或覆盖内容的动作（取消收藏、删除订阅源、清空数据、导入备份、云端恢复）必须显式附带 confirm=1 参数才执行，防止误触；执行命令（run）不带该限制，因为它本身就是为自动化准备的。密码类字段不接受通过链接传入。"
        ),
        listOf()
    ),

    DocSection(
        "八、隐私与安全",
        listOf(),
        listOf(
            DocItem("首次启动协议", "《用户协议》与《隐私政策》为两份独立文本，启动时不默认勾选，须主动同意。不同意则进入离线基础模式：核心的浏览、执行、复制、收藏全部可用，只有联网功能关闭。"),
            DocItem("数据留存", "App 没有自建服务端，也不含任何第三方统计或广告 SDK。订阅源、收藏、设置、崩溃日志都只存在本机；联网仅发生在你自己配置的源地址与备份目标上。"),
            DocItem("崩溃日志", "崩溃现场在本地保留最近 5 份，可查看与复制，用于反馈问题。"),
            DocItem("危险操作保护", "标记为危险的命令执行前二次确认；批量删除、清空收藏、云端恢复等不可逆操作均有确认步骤。")
        )
    ),

    DocSection(
        "九、更新与其他",
        listOf(),
        listOf(
            DocItem("检查更新", "会读取发布页的最新版本号，若高于当前版本则提示；下载时依次尝试多条线路，下载完成后校验 zip 结构、包内版本号与签名，不合格的包会被丢弃重下，不会安装损坏或被替换的安装包。"),
            DocItem("更新日志", "按版本记录改动内容，更新后首次打开会弹一次当前版本的说明。"),
            DocItem("使用帮助", "离线可查的操作指引，只讲 App 本身怎么用，不含订阅源格式细节。")
        )
    )
)
