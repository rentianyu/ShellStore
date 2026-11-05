package com.xbta.shellstore.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xbta.shellstore.core.AdbIntent
import com.xbta.shellstore.core.ExportSpec
import com.xbta.shellstore.core.PackageRules
import com.xbta.shellstore.data.ApkExporter
import com.xbta.shellstore.data.IconMaker
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale
import java.io.ByteArrayOutputStream

/**
 * 「导出 APK」表单：名称 / 包名 / 图标 / 动作类型 / 详细内容 / 执行身份。
 *
 * 六项都可改，默认值从条目本身推导，用户什么都不动也能直接生成。
 */
@Composable
fun ExportApkDialog(
    defaultTitle: String,
    defaultType: String,
    defaultValue: String,
    defaultAction: String,
    defaultComponent: String,
    defaultData: String = "",
    defaultExtras: String = "",
    defaultExec: String,
    onDismiss: () -> Unit,
    onConfirm: (ExportSpec) -> Unit
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(defaultTitle) }
    var pkg by remember { mutableStateOf("") }
    var autoPkg by remember { mutableStateOf(ApkExporter.autoPackage()) }
    var iconText by remember { mutableStateOf(defaultTitle.take(1)) }
    var type by remember { mutableStateOf(defaultType) }
    var value by remember { mutableStateOf(defaultValue) }
    var action by remember { mutableStateOf(defaultAction) }
    var component by remember { mutableStateOf(defaultComponent) }
    var data by remember { mutableStateOf(defaultData) }
    var extras by remember { mutableStateOf(defaultExtras) }
    var exec by remember { mutableStateOf(defaultExec) }
    var showOutput by remember { mutableStateOf(true) }
    var timeout by remember { mutableStateOf("30") }
    var advanced by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        photo = runCatching {
            // 先量尺寸再按需采样，避免大图直接 OOM
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / sample > 1024 || opts.outHeight / sample > 1024) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }

    // 导出后自动保存的目录（SAF），设过就记住，下次默认用
    val settings by Store.settings.collectAsState()
    val dirUri = settings.exportDirUri
    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 不申请持久化权限的话，重启 App 后这个目录就用不了了
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        Store.updateSettings(Store.settings.value.copy(exportDirUri = uri.toString()))
    }

    /**
     * 切动作类型时先规范化当前值：如果「详细内容」其实是一整条 am 命令，
     * 就把它拆成 value / action / component / data，保证导出的 App 直接走 Intent。
     */
    fun switchType(t: String) {
        val n = AdbIntent.normalize(t, value, action, component, extras, data)
        value = n.value
        action = n.action
        component = n.component
        data = n.data
        extras = n.extras
        // 执行身份只对 shell 类型必需；切到其它类型时清空 = 默认不勾选（直接调 Intent）
        exec = if (t == ExportSpec.TYPE_SHELL) exec.ifBlank { ExportSpec.EXEC_SHELL } else ""
        type = t
    }

    val pkgError = PackageRules.validate(pkg)
    val needIntentFields = type == ExportSpec.TYPE_ACTIVITY ||
        type == ExportSpec.TYPE_BROADCAST || type == ExportSpec.TYPE_SERVICE

    // 预览：名称首字 / 类型配色 / 用户选的图
    val previewBytes by remember(iconText, type, photo) {
        mutableStateOf(
            runCatching { IconMaker.preview(iconText, type, photo, 192) }.getOrNull()
        )
    }
    val previewBmp = remember(previewBytes) {
        previewBytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // 标题栏
            Row(
                Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.sm, top = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "导出为独立 App",
                    fontSize = TypeScale.body,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("取消") }
            }

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg)
            ) {
                // 图标 + 名称一行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { picker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBmp != null) {
                            Image(
                                previewBmp.asImageBitmap(),
                                contentDescription = "图标预览",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("选图", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it.take(24)
                                if (it.isNotBlank()) iconText = it.take(1)
                            },
                            label = { Text("名称", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (photo != null) "点图标可换图" else "点图标可选图片，或改下面的字",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.weight(1f)
                            )
                            if (photo != null) {
                                Text(
                                    "恢复默认",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { photo = null }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = iconText,
                    onValueChange = { iconText = it.take(2) },
                    label = { Text("图标文字（1~2 个字，选了图片则忽略）", fontSize = 11.sp) },
                    singleLine = true,
                    enabled = photo == null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it.trim().take(80) },
                    label = { Text("包名（留空自动生成）", fontSize = 11.sp) },
                    placeholder = { Text(autoPkg, fontSize = 12.sp) },
                    singleLine = true,
                    isError = pkgError != null,
                    supportingText = {
                        Text(
                            pkgError ?: "留空则用 $autoPkg，多个导出互不覆盖",
                            fontSize = 10.sp,
                            color = if (pkgError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        TextButton(onClick = {
                            autoPkg = ApkExporter.autoPackage()
                            pkg = ""
                        }) { Text("随机", fontSize = 11.sp) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 动作类型
                Text("动作类型", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportSpec.TYPES.take(3).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { switchType(t) },
                            label = { Text(ExportSpec.typeLabel(t), fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportSpec.TYPES.drop(3).forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { switchType(t) },
                            label = { Text(ExportSpec.typeLabel(t), fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(
                            when (type) {
                                ExportSpec.TYPE_SHELL -> "命令内容"
                                ExportSpec.TYPE_URL -> "要打开的链接 / scheme"
                                ExportSpec.TYPE_ACTIVITY -> "URI（可留空）"
                                ExportSpec.TYPE_BROADCAST -> "广播 action"
                                else -> "服务组件（package/.Class）"
                            },
                            fontSize = 11.sp
                        )
                    },
                    minLines = if (type == ExportSpec.TYPE_SHELL) 3 else 1,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                if (needIntentFields) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = component,
                        onValueChange = { component = it.trim() },
                        label = { Text("组件（package/.Class）", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (type == ExportSpec.TYPE_ACTIVITY) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = action,
                            onValueChange = { action = it.trim() },
                            label = { Text("Intent action（可留空）", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // 广播/服务：value 装的是 action 或组件名，data 单独填
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = data,
                            onValueChange = { data = it.trim() },
                            label = { Text("data URI（可留空）", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 执行身份：shell 类型必须选；其余类型是「可选开关」，不选就直接调 Intent
                val isShellType = type == ExportSpec.TYPE_SHELL
                Spacer(Modifier.height(10.dp))
                Text(
                    if (isShellType) "执行身份" else "执行身份（可选）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExportSpec.EXECS.forEach { e ->
                        FilterChip(
                            selected = exec == e,
                            onClick = {
                                // 非 shell 类型再点一次就是取消
                                exec = if (!isShellType && exec == e) "" else e
                            },
                            label = { Text(ExportSpec.execLabel(e), fontSize = 11.sp) }
                        )
                    }
                }
                Text(
                    when {
                        isShellType -> when (exec) {
                            ExportSpec.EXEC_ROOT -> "直接 su 执行，需要设备已 root 且授权"
                            ExportSpec.EXEC_SHIZUKU -> "借 Shizuku 的 ADB 权限执行，第一次打开会请求授权"
                            else -> "以本应用身份执行，不需要额外授权，权限和普通 App 一样"
                        }
                        exec.isEmpty() -> "不勾选：点开直接调用系统接口，最稳、最快，没有任何中间层" +
                            "（需要系统权限的操作可能被系统拒绝时，再回来勾一个身份）"
                        else -> "勾选后改用 ${ExportSpec.execLabel(exec)} 执行等价的 am 命令" +
                            "（有些系统限制直接调用，走 shell 反而能成）"
                    },
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                // 保存位置
                Spacer(Modifier.height(10.dp))
                Text("保存位置", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (dirUri.isBlank()) "仅应用缓存（安装 / 分享时取出）"
                            else ApkExporter.folderLabel(dirUri),
                            fontSize = 12.sp
                        )
                        Text(
                            if (dirUri.isBlank()) "选一个目录，导出后自动留一份到那里"
                            else "导出后自动存一份到这里，文件名带时间",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    TextButton(onClick = { dirPicker.launch(null) }) {
                        Text(if (dirUri.isBlank()) "选择" else "更改", fontSize = 11.sp)
                    }
                    if (dirUri.isNotBlank()) {
                        TextButton(onClick = {
                            Store.updateSettings(Store.settings.value.copy(exportDirUri = ""))
                        }) { Text("清除", fontSize = 11.sp) }
                    }
                }

                // 高级
                Spacer(Modifier.height(8.dp))
                Text(
                    if (advanced) "收起高级选项" else "高级选项（超时 / 附加参数）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { advanced = !advanced }.padding(vertical = 4.dp)
                )
                if (advanced) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("执行超时", fontSize = 11.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = timeout,
                            onValueChange = { timeout = it.filter { c -> c.isDigit() }.take(3) },
                            singleLine = true,
                            modifier = Modifier.width(90.dp)
                        )
                        Text(" 秒", fontSize = 11.sp)
                    }
                    OutlinedTextField(
                        value = extras,
                        onValueChange = { extras = it },
                        label = { Text("附加参数（k=v 每行一个）", fontSize = 11.sp) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("显示执行输出", fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Switch(checked = showOutput, onCheckedChange = { showOutput = it })
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            // 底部按钮
            Row(
                Modifier.fillMaxWidth().padding(Space.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (pkgError != null) "包名不合法" else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = pkgError == null && title.isNotBlank() && value.isNotBlank(),
                    onClick = {
                        onConfirm(
                            ExportSpec(
                                title = title.trim(),
                                packageName = pkg,
                                type = type,
                                value = value,
                                action = action,
                                component = component,
                                data = data,
                                extras = extras,
                                exec = exec,
                                showOutput = showOutput,
                                timeoutSec = timeout.toIntOrNull() ?: 30,
                                iconText = iconText,
                                iconPng = photo?.let { bmp ->
                                    val out = ByteArrayOutputStream()
                                    Bitmap.createScaledBitmap(bmp, 512, 512, true)
                                        .compress(Bitmap.CompressFormat.PNG, 100, out)
                                    out.toByteArray()
                                }
                            )
                        )
                    }
                ) {
                    Text("生成 APK", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
