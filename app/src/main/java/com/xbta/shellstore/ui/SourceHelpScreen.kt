package com.xbta.shellstore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.SourceHelpText
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 订阅源帮助。两套内容分开：
 *  - 人看的：结构化说明 + 最小模板 + 字段表，直接在页面上读
 *  - AI 用的：一整段自包含规范，一键复制粘给任意模型
 * 两份文案都在 core/SourceHelpText.kt 里，改解析器时要同步改。
 */
@Composable
fun SourceHelpScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var showFull by remember { mutableStateOf(false) }

    fun copy(label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, "已复制$label", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("订阅源帮助", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = Space.xl)
        ) {
            Spacer(Modifier.height(Space.sm))

            Text(
                "订阅源就是一份 Markdown 文件",
                fontSize = TypeScale.title,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "SMD 是 Markdown 的超集，只多约定了几行 @字段。写好后传到任意能访问的地方（GitHub、Gist、自己的静态站），" +
                    "把地址填进「添加在线源」就能用；也可以直接选本地 .md 文件导入。",
                fontSize = TypeScale.small,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Steps(
                listOf(
                    "写" to "按下面的格式写好一个 .md 文件",
                    "传" to "传到 GitHub 或任意静态地址，拿到原始文件 URL",
                    "加" to "回到订阅源页 →「添加在线源」，粘贴地址并启用"
                )
            )

            HelpH("最小可用模板")
            CodeBlock(SourceHelpText.MINIMAL, fontSize = 12)
            Spacer(Modifier.height(Space.sm))
            ActionButton("复制这个模板", primary = true) { copy("模板", SourceHelpText.MINIMAL) }

            HelpH("常用字段")
            FieldRow("## 分类名", "二级标题就是分类，条目归到它下面")
            FieldRow("### 条目标题", "三级标题就是条目；也可写成 ● 标题 或 - 标题")
            FieldRow("@risk", "safe 只读 / caution 会改设置 / danger 删数据卸载重启")
            FieldRow("@need", "none / shell / root / termux，决定详情页的提示")
            FieldRow("@tag", "标签，空格或逗号分隔")
            FieldRow("@param", "参数：键=标签|默认值，正文用花括号引用")
            FieldRow("@type", "shell / script / urlscheme / intent / activity / service / broadcast，不写会推断")
            FieldRow("@action …", "Intent 类用：action / component / data / mime / package / category / flag / extra")

            HelpH("让 AI 帮你写")
            Text(
                "下面这段是一份自包含的规范，复制后粘给任意 AI，再补一句你要什么命令，它就能直接产出可解析的 SMD。" +
                    "规范里已包含字段表、硬约束和输出要求，不需要再额外解释。",
                fontSize = TypeScale.small,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.md))
            ActionButton("复制给 AI 的完整规范", primary = true) {
                copy("AI 规范", SourceHelpText.AI_PROMPT)
            }
            Spacer(Modifier.height(Space.sm))
            ActionButton(
                if (showFull) "收起预览" else "预览完整规范",
                primary = false
            ) { showFull = !showFull }

            if (showFull) {
                Spacer(Modifier.height(Space.sm))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    CodeBlock(SourceHelpText.AI_PROMPT, fontSize = 11)
                }
            }

            HelpH("三条容易踩的坑")
            Text(
                "1. 命令一定放进围栏代码块里，块内不做任何解析，@ # 反引号都原样保留，不用转义。\n" +
                    "2. 命令开头的 adb shell 会被自动剥掉，带 su -c 的会自动标成需要 root。\n" +
                    "3. 同一个文件里条目标题不要重复，重复会互相覆盖。",
                fontSize = TypeScale.small,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Space.lg))
            Text(
                "完整语法（含 Intent 全部字段）见仓库 sources/README.md",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpH(text: String) {
    Spacer(Modifier.height(Space.lg))
    Text(text, fontSize = TypeScale.title, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(Space.sm))
}

@Composable
private fun Steps(items: List<Pair<String, String>>) {
    Spacer(Modifier.height(Space.lg))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        items.forEachIndexed { i, (tag, desc) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${i + 1}",
                        fontSize = TypeScale.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(Space.sm))
                Text(tag, fontSize = TypeScale.small, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(Space.xs))
                Text(
                    desc,
                    fontSize = TypeScale.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FieldRow(key: String, desc: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            key,
            fontSize = TypeScale.small,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(112.dp)
        )
        Text(
            desc,
            fontSize = TypeScale.small,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold, color = fg)
    }
}
