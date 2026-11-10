package com.xbta.shellstore.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.BuildConfig
import com.xbta.shellstore.core.DeepLink
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 功能总览：一页把 App 能做的事全列出来，按「你要干什么」分组，不是按代码模块。
 *
 * 入口两个：我的 › 关于 › 全部功能，以及更新完的「本次更新内容」弹窗底部按钮。
 * 带 › 的行可以直接点进对应页面。
 */
@Composable
fun FeaturesScreen(onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val entries by Store.entries.collectAsState()
    val cats by Store.categories.collectAsState()
    val sources by Store.sources.collectAsState()
    val favs by Store.favs.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopBar("全部功能", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = Space.xl)
        ) {
            Spacer(Modifier.height(Space.sm))

            // 现状概览：直接给数字，比空话有用
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.sm)
            ) {
                Text(
                    "v${BuildConfig.APP_VERSION} · ${DeepLink.ALL.size} 个 scheme 动作",
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${sources.count { it.enabled }} 个源 / ${cats.size} 个分类 / ${entries.size} 条命令" +
                        " / 收藏 ${favs.items.size} 条",
                    fontSize = TypeScale.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "本页只是导览，带 › 的行可以直接点进去。",
                    fontSize = TypeScale.small,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            for (g in GROUPS) {
                GroupTitle(g.name)
                GroupCard {
                    g.items.forEachIndexed { i, f ->
                        if (i > 0) Divider()
                        SettingRow(
                            glyph = f.glyph,
                            title = f.title,
                            subtitle = f.desc,
                            onClick = f.page?.let { p -> { onOpen(p()) } }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))
            Text(
                "这些功能全部离线可用，只有订阅源更新、检查更新、云备份、scheme 里联网类动作需要网络。",
                fontSize = TypeScale.small,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = Space.xs)
            )
            Spacer(Modifier.height(Space.md))
        }
    }
}

private data class Feat(
    val glyph: String,
    val title: String,
    val desc: String,
    /** 有值表示这一行能点进去 */
    val page: (() -> Page)? = null
)

private data class FeatGroup(val name: String, val items: List<Feat>)

private val GROUPS: List<FeatGroup> = listOf(
    FeatGroup(
        "找命令", listOf(
            Feat("⌕", "全字段搜索", "空格分开多个词是 AND；支持拼音首字母（dc 能搜到「多点」类标题）；标题命中的排在前面，结果里高亮关键词。搜索框留空会显示最近搜过的词。"),
            Feat("▦", "按分类浏览", "首页直接列出每个分类与条数，点进去看该分类的全部命令。"),
            Feat("☰", "筛选", "按 7 种类型（shell / script / url scheme / intent / activity / service / broadcast）与 3 档风险过滤。"),
            Feat("◷", "最近使用", "打开过的条目按时间倒序留在这里，方便回头找。"),
            Feat("↻", "刷新订阅源", "重拉所有启用的源并重新解析，保留本地收藏与编辑。")
        )
    ),
    FeatGroup(
        "看与用一条命令", listOf(
            Feat(">_", "执行", "shell / script 用 App 自身权限跑 sh（不是 root）；也可以指定走 Shizuku 或 Root 执行。"),
            Feat("↗", "发 Intent", "url scheme、activity、intent、broadcast、service 五类条目直接发给系统打开，不经过 shell。"),
            Feat("⧉", "复制", "复制原始内容，或复制成 am 命令（按设置自动加 adb shell / su -c 前缀）。"),
            Feat("⌘", "参数占位符", "命令里写 {键} 就会出输入框，填完命令实时重算。参数值可以一键解码、展开 JSON 子字段、URL 编码/解码，改子字段会自动写回。"),
            Feat("≡", "多命令变体", "同一个条目写多个代码块，详情页左右箭头切换，参数各自独立。"),
            Feat("⚠", "风险与运行条件", "标注安全 / 谨慎 / 危险，以及是否需要 root、是否要装某个 App，避免误执行。"),
            Feat("⌂", "桌面快捷方式", "把一条命令固定到桌面，点一下直接执行；参数值会用你填过的那份。"),
            Feat("▣", "控制中心磁贴", "把命令放到下拉快捷开关里，通知栏一划就能点。"),
            Feat("⇩", "导出成 APK", "把一条命令打包成独立小 App（可改名称、包名、图标、执行身份、结果提示方式），分享给别人也能用。")
        )
    ),
    FeatGroup(
        "收藏与整理", listOf(
            Feat("★", "收藏到文件夹", "收藏时可以选文件夹，也可以关掉询问、一律进默认位置。"),
            Feat("☰", "拖动排序", "长按卡片拖动调整顺序，松手即保存。"),
            Feat("☑", "多选批量", "批量移动到文件夹、批量删除、批量打标签。"),
            Feat("▶", "置顶", "常用条目固定在列表最前。"),
            Feat("▤", "标签", "一条命令可有多个标签，标签视图里交叉检索。"),
            Feat("⇧", "导出收藏为 SMD", "收藏整理成标准 SMD 文本，可以直接当订阅源发给别人。", { Page.AllFav })
        )
    ),
    FeatGroup(
        "订阅源", listOf(
            Feat("+", "三种添加方式", "填远程 SMD 地址、粘贴一段文本、从本地文件导入。", { Page.Sources }),
            Feat("✦", "小贝塔订阅源", "内置系列清单，一项一项开关；之前手动加过的同一个源会自动归入系列，不会重复。", { Page.Sources }),
            Feat("↻", "更新检查", "拉下新内容后先算差异（条数增减）给你确认，同意了才写入，不会悄悄改掉你的库。", { Page.Sources }),
            Feat("⇅", "多线路加速", "GitHub 地址自动在多个镜像间选快的，失败自动换下一条；记住上次成功的线路下次优先。", { Page.Sources }),
            Feat("？", "SMD 格式说明", "怎么写自己的源：字段、分类、参数占位符、常见坑。", { Page.SourceHelp })
        )
    ),
    FeatGroup(
        "备份与恢复", listOf(
            Feat("⇅", "云备份双通道", "WebDAV 或任意 S3 兼容对象存储（自写签名，不引第三方 SDK），自动按后端单选。", { Page.Cloud }),
            Feat("↻", "变更后自动备份", "数据一变就重新计时 30 秒，安静下来才上传，避免连续改十几次传十几次。", { Page.Cloud }),
            Feat("⇧", "导出 / 导入 JSON", "整份收藏与设置导成 JSON；导入可选覆盖或合并。", { Page.Backup }),
            Feat("⇩", "从云端恢复", "拉回最近一次备份并覆盖本地，恢复前会提示不可撤销。", { Page.Backup })
        )
    ),
    FeatGroup(
        "外观与手感", listOf(
            Feat("字", "字号 10 档", "只放大文字，控件尺寸不变；和界面缩放可以叠加。", { Page.Settings }),
            Feat("▣", "界面缩放 5 档", "整体放大（dp 与 sp 一起），适合大屏或手抖点不准。", { Page.Settings }),
            Feat("◍", "深浅色", "浅色 / 深色 / 跟随系统，默认浅色。", { Page.Settings }),
            Feat("⌨", "字体", "界面与代码可以分别设字体，支持导入自定义字体文件。", { Page.Settings }),
            Feat("▤", "列表样式", "卡片（默认，信息多）或紧凑列表（一行一条，一屏看得更多）。", { Page.Settings }),
            Feat("≡", "折叠预览", "列表只显示标题，收起命令预览，扫起来更快。", { Page.Settings })
        )
    ),
    FeatGroup(
        "自动化", listOf(
            Feat("⇢", "${DeepLink.ALL.size} 个 scheme 动作", "所有功能都能用 shellstore:// 从外部调起：跳页面、执行命令、改设置、加源、备份。", { Page.SchemeHelp }),
            Feat("⌘", "外部调用方式", "adb shell am start -d \"shellstore://run?id=xxx\"；Tasker 发 VIEW Intent 同理。"),
            Feat("⚡", "配合 Shizuku", "有 Shizuku 就能免 root 执行需要系统权限的命令，不用每次弹授权。")
        )
    ),
    FeatGroup(
        "隐私与安全", listOf(
            Feat("§", "首次启动要同意", "用户协议与隐私政策分开两份，不默认勾选；不同意进离线基础模式，核心功能照常能用。", { Page.Legal("user") }),
            Feat("⚠", "危险动作确认", "执行标为危险的命令前二次确认，可以按需关掉。"),
            Feat("⊘", "不收集任何数据", "没有自建服务端、没有第三方统计 SDK，数据全在本机；联网只发生在你自己配置的源与备份上。", { Page.Legal("privacy") }),
            Feat("?", "崩溃日志", "崩溃现场留在本地（最多 5 份），可查看、复制，方便反馈问题。", { Page.Help })
        )
    ),
    FeatGroup(
        "其他", listOf(
            Feat("↻", "检查更新", "多线路并发尝试，下载后做 zip 结构 + 签名 + 版本三重校验，坏包不会装。", { Page.About }),
            Feat("≡", "更新日志", "每个版本改了什么，按版本分组。", { Page.Changelog }),
            Feat("？", "使用帮助", "离线可查，只讲 App 怎么用。", { Page.Help }),
            Feat("ⓘ", "关于", "版本、协议入口、scheme 用法、当前内容统计。", { Page.About })
        )
    )
)
