package com.xbta.shellstore.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 内置使用帮助。全部离线，内容随版本打包，不依赖网络。
 * 只放「不查也能看完」的要点，完整 SMD 语法指向 GitHub 上的 sources/README.md。
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("使用帮助", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = Space.xl)
        ) {
            Spacer(Modifier.height(Space.sm))

            HelpH("快速上手")
            HelpP("1. 首次打开点「启用官方源」，会拉取 129 条命令（拉不动会自动换线路，共 17 条备用）。")
            HelpP("2. 商店页按分类浏览，或点右上角 ⌕ 全字段搜索；搜索框留空时会显示最近搜索过的词。")
            HelpP("3. 点进条目：有 {占位符} 的会出输入框，填完命令实时重算，底部「复制」按设置的前缀处理。")

            HelpH("七种条目类型")
            HelpRow("shell", "一条 shell 命令，可本地执行或复制")
            HelpRow("script", "多行脚本，同上")
            HelpRow("urlscheme", "URI，如 tel:10086，交给系统处理")
            HelpRow("intent", "带 action / data / extra 的 Intent，可一键发送")
            HelpRow("activity", "直接启动某个 Activity")
            HelpRow("service", "启动或停止服务")
            HelpRow("broadcast", "发送广播")

            HelpH("参数与变体")
            HelpP("命令里的 {键} 会被替换成输入框的值。同一个条目可以写多个命令块，详情页用左右箭头切换。")
            HelpP("命令开头的 adb shell 会被自动剥掉；带 su -c 的会被标成需要 root。")

            HelpH("执行与发送")
            HelpP("shell / script 用 App 自身权限执行 sh，不是 root。改系统设置、访问 /data 这类需要 root 的做不了。")
            HelpP("Intent 类条目在底部有「打开 / 发送 / 启动」按钮，详情页同时显示等价的 adb 命令，方便在电脑上复现。")

            HelpH("收藏与备份")
            HelpP("收藏存的是快照：源被删了，收藏里原来的内容还在。")
            HelpP("收藏夹内长按条目可拖动排序；多选模式支持批量移动、批量打标签、批量删除。")
            HelpP("备份与恢复支持导出 JSON（收藏 + 标签 + 设置），导入时可选合并或覆盖。")
            HelpP("「导出为订阅源」会把收藏按文件夹导出成 SMD 文件，别人导入即可用；详情页右上角菜单还能复制单条 SMD 片段。")

            HelpH("写自己的订阅源")
            HelpP("SMD 是 Markdown 的超集，只多约定几行 @字段。最小可用模板：")
            CodeBlock(
                """
## 分类名

### 条目标题
@risk safe
@need shell
@tag 标签

```sh
你的命令
```
                """.trimIndent()
            )
            HelpP("支持的字段：@type @risk @need @tag @desc @param @verified，Intent 类另有 @action @component @data @mime @package @category @flag @extra。")
            HelpP("完整语法见仓库 sources/README.md；也可以「我的 › 订阅源 › 添加」填在线地址，或直接选本地 .md 导入。")

            HelpH("常见问题")
            HelpQ("官方源一直拉不下来？", "内置 17 条线路自动降级，每次 10 秒。全失败通常是网络整体出不去，可以在电脑上下载 official.md 再传到手机用「本地文件」导入。")
            HelpQ("点执行提示权限不足？", "执行用的是 App 自身权限，需要 root 的命令做不了，这不是 bug。")
            HelpQ("收藏会丢吗？", "存在 App 私有目录，卸载即删除。建议定期在「备份与恢复」里导出一份 JSON。")
            HelpQ("怎么更新到新版本？", "启动时会自动检查，发现新版本后台下载，下载完弹窗点安装即可。也可以「我的 › 检查更新」手动查。")
            HelpQ("更新下载完为什么要手动点安装？", "Android 的安全限制，App 不能静默安装，只能把安装包交给系统安装器。")
            HelpQ("需要哪些权限？", "只有联网、读取网络状态、安装未知应用三项。不申请存储、定位、悬浮窗。")

            Spacer(Modifier.height(Space.lg))
            Text(
                "完整文档：github.com/rentianyu/ShellStore",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpH(text: String) {
    Spacer(Modifier.height(Space.lg))
    Text(
        text,
        fontSize = TypeScale.title,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(Space.sm))
}

@Composable
private fun HelpP(text: String) {
    Text(
        text,
        fontSize = TypeScale.small,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Space.sm)
    )
}

@Composable
private fun HelpRow(name: String, desc: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.xs)
    ) {
        Text(
            name,
            fontSize = TypeScale.small,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(84.dp)
        )
        Text(
            desc,
            fontSize = TypeScale.small,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HelpQ(q: String, a: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.md)
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Space.md)
    ) {
        Text(q, fontSize = TypeScale.small, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Space.xs))
        Text(
            a,
            fontSize = TypeScale.small,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
