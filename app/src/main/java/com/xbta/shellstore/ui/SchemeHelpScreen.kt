package com.xbta.shellstore.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.DeepLink
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * shellstore:// 协议说明。内容直接从 [DeepLink.ALL] 渲染，
 * 与代码里真正支持的动作是同一份数据 —— 不会出现「文档写了、代码没实现」。
 */
@Composable
fun SchemeHelpScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar("scheme 用法", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = Space.xl)
        ) {
            Spacer(Modifier.height(Space.sm))

            SecH("怎么用")
            SecP("所有功能都能从外部用 scheme 调起，形式：")
            LinkRow("shellstore://<动作>?<参数>", "点一下复制")
            LinkRow("shellstore://source/add?url=https%3A%2F%2F…", "两段式：source/add = source-add")
            SecP(
                "参数值里的 & = 用 %26 转义；中文、空格建议先编码，" +
                    "否则不同调用方可能截断。动作名不区分大小写，下划线和横线等价。"
            )

            SecH("怎么触发")
            SecP("· adb shell am start -a android.intent.action.VIEW -d \"shellstore://run?id=xxx\"")
            SecP("· Tasker / 自动化：发送 VIEW Intent，data 填上面的地址")
            SecP("· 浏览器地址栏、备忘录里直接点（部分浏览器会拦自定义 scheme，用 adb 最稳）")

            SecH("⚠ 标记与确认")
            SecP("⚠ 表示这条会执行命令或改动数据。其中这 5 个必须显式加 &confirm=1 才执行，避免误触：")
            SecP("unfav（取消收藏）、source-del（删源）、clear（清空）、backup-import（导入）、restore-cloud（云端恢复）")
            SecP("run 带 ⚠ 是提醒它会真的执行命令，但属于自动化用途，不加 confirm 也直接执行。")

            for (group in listOf("导航", "动作", "数据", "设置")) {
                val list = DeepLink.ALL.filter { it.group == group }
                if (list.isEmpty()) continue
                SecH("$group（${list.size}）")
                for (spec in list) {
                    LinkRow(
                        spec.usage,
                        (if (spec.danger) "⚠ " else "") + spec.desc
                    )
                }
            }

            SecH("set 的字段（${DeepLink.SETTING_KEYS.size} 项）")
            for ((k, v) in DeepLink.SETTING_KEYS) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        k,
                        modifier = Modifier.widthIn(min = 108.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(v, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SecP("密码类字段（WebDAV 密码、S3 SecretKey）只能在本页 App 内输入，不走 scheme。")

            SecH("例子")
            LinkRow("shellstore://run?id=<条目id>&pkg=com.jd", "执行条目并覆盖参数 pkg")
            LinkRow("shellstore://entry?id=<条目id>", "打开详情")
            LinkRow("shellstore://set?key=theme&value=dark", "切深色主题")
            LinkRow("shellstore://set?key=fontLevel&value=6", "字号放大到第 6 档")
            LinkRow("shellstore://source-add?url=https%3A%2F%2Ff.xbta.cc%2Fai%2FShellStore%2Fsources%2Fgaode.md&name=高德", "加一个订阅源")
            LinkRow("shellstore://backup-now", "立即云备份")

            Spacer(Modifier.height(Space.md))
        }
    }
}

/** 小节标题（本文件私有，避免和 HelpScreen / SourceHelpScreen 里的同名函数冲突） */
@Composable
private fun SecH(text: String) {
    Text(
        text,
        fontSize = TypeScale.body,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = Space.md, bottom = 4.dp)
    )
}

/** 正文段落 */
@Composable
private fun SecP(text: String) {
    Text(
        text,
        fontSize = TypeScale.small,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/** 一行等宽协议（点击整行复制），下面一行灰字说明 */
@Composable
private fun LinkRow(cmd: String, desc: String) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            cmd,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = com.xbta.shellstore.ui.theme.LocalCodeFont.current,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .07f))
                .clickable {
                    runCatching {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("scheme", cmd))
                        Toast.makeText(ctx, "已复制　$cmd", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
        Text(
            desc,
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
        )
    }
}
