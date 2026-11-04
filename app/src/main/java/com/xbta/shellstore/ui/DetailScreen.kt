package com.xbta.shellstore.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xbta.shellstore.core.*
import com.xbta.shellstore.data.ApkExporter
import com.xbta.shellstore.data.EntryShortcuts
import com.xbta.shellstore.data.ShellExec
import com.xbta.shellstore.data.ShizukuRunner
import com.xbta.shellstore.data.CopyPrefix
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import rikka.shizuku.Shizuku
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(entryId: String, onBack: () -> Unit, onOpen: (Page) -> Unit = {}) {
    val entry = Store.entryById(entryId)
    val favs by Store.favs.collectAsState()
    val settings by Store.settings.collectAsState()
    val ctx = LocalContext.current

    if (entry == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("未找到", onBack = onBack)
            EmptyState("条目不存在", "可能已被源移除")
        }
        return
    }

    var values by remember(entry.id) {
        mutableStateOf(entry.params.associate { it.key to it.default })
    }
    var variantIndex by remember(entry.id) { mutableIntStateOf(-1) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var execResult by remember { mutableStateOf<ShellExec.Result?>(null) }
    // 导出 APK 的状态机：false + null = 未开始；exporting = 进行中；result/error = 结束
    var exporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<ApkExporter.Result?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    // 放在函数顶层而不是底部操作栏的 lambda 里：导出对话框在顶层，作用域要够得着
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    // 结果对话框要显示「真正执行的命令」（非 shell 条目执行的是生成的 am 命令）
    var executedCmd by remember { mutableStateOf("") }

    val text = remember(entry, values, variantIndex) {
        CommandRenderer.primaryText(entry, values, variantIndex)
    }
    val fav = favs.isFav(entry.id)

    fun copy(silent: Boolean = false) {
        val out = settings.copyPrefix.fn(text)
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("命令", out))
        Toast.makeText(ctx, if (settings.copyPrefix == CopyPrefix.NONE) "已复制" else "已复制（含 ${settings.copyPrefix.label} 前缀）", Toast.LENGTH_SHORT).show()
        Store.recordRecent(entry.id)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = entry.title,
            onBack = onBack,
            actions = {
                Box {
                    IconBtn("⋯") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("复制为 SMD 片段", fontSize = TypeScale.body) },
                            onClick = {
                                menuOpen = false
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("SMD", SmdWriter.entry(entry)))
                                Toast.makeText(ctx, "已复制 SMD 片段，可直接贴进订阅源", Toast.LENGTH_SHORT).show()
                                Store.recordRecent(entry.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("复制标题", fontSize = TypeScale.body) },
                            onClick = {
                                menuOpen = false
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("标题", entry.title))
                                Toast.makeText(ctx, "已复制标题", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("编辑内容", fontSize = TypeScale.body) },
                            onClick = {
                                menuOpen = false
                                onOpen(Page.Editor(entry.id, null))
                            }
                        )
                    }
                }
                IconBtn(if (fav) "★" else "☆") { Store.toggleFav(entry) }
            }
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Space.page, end = Space.page, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MutedBadge(typeLabel(entry.type))
                    Spacer(Modifier.width(5.dp))
                    if (Store.isLocal(entry.id)) {
                        MutedBadge("本地")
                        Spacer(Modifier.width(5.dp))
                    }
                    RiskBadge(entry.risk)
                    if (entry.need != Need.NONE) {
                        Spacer(Modifier.width(5.dp))
                        MutedBadge(entry.need.label)
                    }
                    if (entry.categoryId.isNotBlank()) {
                        Spacer(Modifier.width(5.dp))
                        MutedBadge(entry.categoryId)
                    }
                }
            }

            if (entry.risk == Risk.DANGER) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(Tones.dangerBg)
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    ) {
                        Text(
                            "⚠ 高风险操作。执行前请确认命令内容确实符合预期，误操作可能造成数据丢失或系统异常。",
                            fontSize = TypeScale.small,
                            color = Tones.danger,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            if (entry.variants.isNotEmpty()) {
                item { SectionLabel("变体") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip("默认", variantIndex == -1) { variantIndex = -1 }
                        entry.variants.forEachIndexed { i, v ->
                            FilterChip(v.name, variantIndex == i) { variantIndex = i }
                        }
                    }
                }
            }

            item {
                if (entry.type.hasBody || entry.type == EntryType.URLSCHEME) {
                    SectionLabel(if (entry.type == EntryType.SCRIPT) "命令（脚本）" else "命令")
                } else {
                    SectionLabel("等价 adb 命令")
                }
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpen(Page.Editor(entry.id)) }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text.ifBlank { "（无内容）" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = TypeScale.codeLarge,
                            lineHeight = 19.sp,
                            color = Tones.codeFg
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "点击编辑",
                                fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text("${text.lines().size} 行 · ${text.length} 字符", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            if (!entry.type.hasBody && entry.intent != null) {
                item { SectionLabel("Intent 字段") }
                item { IntentFieldTable(entry.intent) }
            }

            if (entry.params.isNotEmpty()) {
                item { SectionLabel("参数") }
                entry.params.forEach { p ->
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.label, fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(9.dp))
                                BasicTextField(
                                    value = values[p.key] ?: "",
                                    onValueChange = { values = values + (p.key to it) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                if ((values[p.key] ?: "").isNotEmpty()) {
                                    AppIcon(
                                        "✕",
                                        size = IconSize.inline,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.clickable { values = values + (p.key to "") }.padding(4.dp)
                                    )
                                }
                            }
                            if (p.note.isNotBlank()) {
                                Text(
                                    p.note,
                                    fontSize = TypeScale.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (entry.example.isNotBlank()) {
                item { SectionLabel("示例") }
                item { CodeBlock(entry.example, fontSize = 11) }
            }

            if (entry.desc.isNotBlank()) {
                item { SectionLabel("说明") }
                item {
                    Text(
                        entry.desc,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.origin.isNotBlank()) {
                item { SectionLabel("原始写法") }
                item { CodeBlock(entry.origin, fontSize = 11) }
            }

            item { SectionLabel("来源") }
            item {
                val src = Store.sourceOf(entry.sourceId)
                val meta = src?.name ?: "内置命令集"
                val ver = Store.sourceVersion.collectAsState().value
                Text(
                    "来源　$meta" + (if (ver.isNotBlank() && src?.type == com.xbta.shellstore.core.SourceType.BUILTIN) " · v$ver" else "") + "\n" +
                        "条目　${entry.id}" +
                        (if (entry.verifiedAt.isNotBlank()) "\n验证　${entry.verifiedAt}" else ""),
                    fontSize = TypeScale.caption,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // 底部操作栏：能执行的给「执行」，能触发的给「打开 / 发送 / 启动」，其余只有复制
        val runnable = entry.type == EntryType.SHELL || entry.type == EntryType.SCRIPT
        val triggerable = IntentLauncher.canTrigger(entry)
        val primary = MaterialTheme.colorScheme.primary
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        val variant = MaterialTheme.colorScheme.surfaceVariant
        val onVariant = MaterialTheme.colorScheme.onSurfaceVariant

        // Shizuku 的授权结果从 binder 线程回来，切回主线程再提示
        // 授权状态缓存一份：组合期每次重组都调 pingBinder 是 IPC，没必要
        var shizukuReady by remember { mutableStateOf(ShizukuRunner.hasPermission()) }

        val permListener = remember {
            Shizuku.OnRequestPermissionResultListener { code, grant ->
                if (code == ShizukuRunner.REQ_CODE) {
                    scope.launch {
                        toast(
                            if (grant == PackageManager.PERMISSION_GRANTED) {
                                shizukuReady = true
                                "已授权给 Shizuku，再点一次「Shizuku」就能执行"
                            } else "Shizuku 授权被拒绝"
                        )
                    }
                }
            }
        }
        DisposableEffect(Unit) {
            runCatching { Shizuku.addRequestPermissionResultListener(permListener) }
            onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(permListener) } }
        }

        /**
         * 提权执行时用的命令：shell/script 直接跑正文；
         * url scheme / activity / intent / broadcast / service 必须先转成等价的 am 命令，
         * 否则把 `openapp.jdmobile://...` 当命令丢给 sh 必然失败。
         */
        fun privilegedCommand(): String =
            if (entry.type == EntryType.SHELL || entry.type == EntryType.SCRIPT) text
            else CommandRenderer.toAdb(entry, values)

        fun runVia(kind: String) {
            val cmd = privilegedCommand()
            if (cmd.isBlank()) {
                toast("这个条目没有等价的 am 命令，无法用提权通道执行")
                return
            }
            executedCmd = cmd
            scope.launch {
                running = true
                val r = withContext(Dispatchers.IO) {
                    if (kind == "shizuku") ShellExec.runViaShizuku(cmd) else ShellExec.runViaRoot(cmd)
                }
                running = false
                execResult = r
                if (r.ok) Store.recordRecent(entry.id)
            }
        }

        fun shizukuRun() {
            if (!ShizukuRunner.isInstalled(ctx)) { toast("没装 Shizuku：装好并启动后再回来"); return }
            if (!ShizukuRunner.isRunning()) { toast("Shizuku 服务没在运行，打开 Shizuku 点「启动」"); return }
            if (!ShizukuRunner.hasPermission()) {
                ShizukuRunner.requestPermission()
                toast("已发起授权请求，允许之后再点一次")
                return
            }
            shizukuReady = true
            runVia("shizuku")
        }

        fun rootRun() = runVia("root")

        fun exportApk() {
            if (exporting) return
            exporting = true
            exportResult = null
            exportError = null
            scope.launch {
                val r = runCatching {
                    withContext(Dispatchers.IO) {
                        ApkExporter.export(ctx, entry.title, text) { }
                    }
                }
                exporting = false
                r.onSuccess { exportResult = it }
                    .onFailure { exportError = it.message ?: it.javaClass.simpleName }
            }
        }

        fun pinHome() {
            val err = EntryShortcuts.pinToHome(ctx, entry.id, entry.title, text, values)
            toast(err ?: "已请求添加到桌面，确认即可")
        }

        fun addTile() {
            val err = EntryShortcuts.addTile(ctx, entry.id, entry.title, text)
            toast(err ?: "已在控制中心添加磁贴")
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = Space.page, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
        // 第一行：主操作 + 复制 + 收藏（大小与位置保持原样）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (runnable) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(if (running) variant else primary)
                        .clickable(enabled = !running) {
                            executedCmd = text
                            scope.launch {
                                running = true
                                val r = withContext(Dispatchers.IO) { ShellExec.run(ctx, text) }
                                running = false
                                execResult = r
                                Store.recordRecent(entry.id)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (running) "执行中…" else "执行",
                        fontSize = TypeScale.body,
                        fontWeight = FontWeight.Bold,
                        color = if (running) onVariant else onPrimary
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(variant)
                        .clickable { copy() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("复制", fontSize = TypeScale.body, fontWeight = FontWeight.Bold, color = onVariant)
                }
            } else if (triggerable) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(primary)
                        .clickable {
                            val err = IntentLauncher.trigger(ctx, entry, values)
                            if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                            else {
                                Toast.makeText(ctx, "${IntentLauncher.actionLabel(entry)}成功", Toast.LENGTH_SHORT).show()
                                Store.recordRecent(entry.id)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        IntentLauncher.actionLabel(entry),
                        fontSize = TypeScale.body,
                        fontWeight = FontWeight.Bold,
                        color = onPrimary
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(variant)
                        .clickable { copy() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("复制", fontSize = TypeScale.body, fontWeight = FontWeight.Bold, color = onVariant)
                }
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(primary)
                        .clickable { onOpen(Page.Editor(entry.id)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "编辑",
                        fontSize = TypeScale.body,
                        fontWeight = FontWeight.Bold,
                        color = onPrimary
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(variant)
                        .clickable { copy() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("复制", fontSize = TypeScale.body, fontWeight = FontWeight.Bold, color = onVariant)
                }
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(variant)
                    .clickable { Store.toggleFav(entry) },
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    if (fav) "★" else "☆",
                    size = IconSize.action,
                    color = if (fav) androidx.compose.ui.graphics.Color(0xFFF2A93B) else onVariant
                )
            }
        }

            // 第二行：5 个扩展能力，全部摆在明面（不进菜单）
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionChip(
                    glyph = "⚡",
                    label = "Shizuku",
                    onClick = { shizukuRun() },
                    tint = if (shizukuReady) primary else null
                )
                ActionChip(glyph = "⚿", label = "Root", onClick = { rootRun() })
                ActionChip(glyph = "⇩", label = "导出APK", onClick = { exportApk() }, enabled = !exporting)
                ActionChip(glyph = "⌂", label = "桌面", onClick = { pinHome() })
                ActionChip(glyph = "▣", label = "磁贴", onClick = { addTile() })
            }
        }
    }

    if (exporting || exportResult != null || exportError != null) {
        AlertDialog(
            onDismissRequest = {
                if (!exporting) { exportResult = null; exportError = null }
            },
            title = {
                Text(
                    when {
                        exportError != null -> "导出失败"
                        exporting -> "正在生成 APK"
                        else -> "导出完成"
                    },
                    fontSize = TypeScale.body
                )
            },
            text = {
                when {
                    exporting -> Text(
                        "正在改写包名、写入命令并重新签名，几秒钟。",
                        fontSize = TypeScale.small
                    )
                    exportError != null -> Text(
                        "导出失败：$exportError",
                        fontSize = TypeScale.small
                    )
                    else -> {
                        val res = exportResult!!
                        Text(
                            "已生成 ${res.file.name}\n包名 ${res.pkg}\n\n" +
                                "装上就能用：点开图标直接执行这条命令，不需要 Shell 商店在后台。\n\n" +
                                "应用名固定显示为「Shell 命令」——模板里的名称资源没法等长替换，" +
                                "所以多条命令请靠包名区分；签名用的是导出专用密钥，和商店本体无关。",
                            fontSize = TypeScale.small
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    exporting -> TextButton(onClick = { }) { Text("请稍候") }
                    exportError != null -> TextButton(onClick = { exportError = null }) { Text("知道了") }
                    else -> TextButton(onClick = {
                        exportResult?.let { res ->
                            runCatching { ctx.startActivity(ApkExporter.installIntent(ctx, res.file)) }
                                .onFailure { toast("没有找到可用的安装器") }
                        }
                    }) { Text("安装") }
                }
            },
            dismissButton = if (!exporting && exportResult != null) {
                {
                    TextButton(onClick = {
                        exportResult?.let { res ->
                            runCatching { ctx.startActivity(ApkExporter.shareIntent(ctx, res.file, entry.title)) }
                                .onFailure { toast("分享失败") }
                        }
                    }) { Text("分享") }
                }
            } else null
        )
    }

    val r = execResult
    if (r != null) {
        ExecResultDialog(
            result = r,
            command = executedCmd.ifBlank { text },
            onCopy = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("输出", listOf(r.out, r.err).filter { it.isNotBlank() }.joinToString("\n")))
                Toast.makeText(ctx, "输出已复制", Toast.LENGTH_SHORT).show()
            },
            onOpenTermux = { ShellExec.openTermux(ctx) },
            onDismiss = { execResult = null }
        )
    }
}

/** 执行结果。输出可能很长，内部滚动，不撑破屏幕。 */
@Composable
internal fun ExecResultDialog(
    result: ShellExec.Result,
    command: String,
    onCopy: () -> Unit,
    onOpenTermux: () -> Unit,
    onDismiss: () -> Unit
) {
    val ok = result.ok
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
                .padding(Space.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ok) "执行完成" else "执行失败",
                    fontSize = TypeScale.title,
                    fontWeight = FontWeight.Bold,
                    color = if (ok) Tones.safe else Tones.danger
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "通道 ${result.channel.label}" + if (!ok) " · 返回码 ${result.code}" else "",
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.note.isNotBlank()) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    result.note,
                    fontSize = TypeScale.small,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.out.isNotBlank()) {
                Spacer(Modifier.height(Space.md))
                SectionLabel("标准输出")
                Spacer(Modifier.height(Space.xs))
                Box(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    CodeBlock(result.out, fontSize = 12)
                }
            }

            if (result.err.isNotBlank()) {
                Spacer(Modifier.height(Space.md))
                SectionLabel(if (result.out.isBlank()) "输出" else "错误输出")
                Spacer(Modifier.height(Space.xs))
                Box(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                    CodeBlock(result.err, fontSize = 12, background = Tones.dangerBg)
                }
            }

            if (result.out.isBlank() && result.err.isBlank() && result.note.isBlank()) {
                Spacer(Modifier.height(Space.md))
                Text("命令没有任何输出。", fontSize = TypeScale.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (!ok) {
                    Text(
                        "打开 Termux",
                        fontSize = TypeScale.body,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable { onOpenTermux() }
                            .padding(horizontal = Space.sm, vertical = Space.xs)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "复制输出",
                    fontSize = TypeScale.body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { onCopy() }
                        .padding(horizontal = Space.sm, vertical = Space.xs)
                )
                Text(
                    "关闭",
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { onDismiss() }
                        .padding(horizontal = Space.sm, vertical = Space.xs)
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = TypeScale.caption,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.md, start = Space.xs)
    )
}

@Composable
private fun IntentFieldTable(spec: IntentSpec) {
    val rows = buildList {
        add("action" to spec.action)
        add("component" to spec.component)
        add("data" to spec.data)
        add("mime" to spec.mime)
        add("package" to spec.packageName)
        add("category" to spec.categories.joinToString(", "))
        add("extras" to spec.extras.joinToString("\n") {
            "${it.key} (${it.type.name.lowercase()}) = ${it.value}"
        })
        add("flags" to spec.flags.joinToString(", "))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        rows.forEachIndexed { i, pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp)) {
                Text(
                    pair.first,
                    fontSize = TypeScale.small,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp)
                )
                Text(
                    pair.second.ifBlank { "—" },
                    fontSize = TypeScale.small,
                    fontFamily = FontFamily.Monospace,
                    color = if (pair.second.isBlank()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            if (i != rows.lastIndex) Divider()
        }
    }
}
