package com.xbta.shellstore.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.data.CloudBackend
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtTime(ts: Long): String =
    if (ts <= 0L) "从未"
    else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts))

/**
 * 云备份（WebDAV / S3 兼容对象存储）。
 *
 * 设计取舍：
 * - 两种后端共用一页，用分段切换；「自动备份」单选当前后端，避免一次改动上传两份、流量翻倍
 * - 配置项点「保存」才写盘，避免边输边改导致校验逻辑乱跳
 * - 密码 / SecretKey 各走 KeyStore 加密文件，导出备份与云端快照都不含它们
 * - 只同步一份覆盖式快照，不做版本历史（两种后端都不需要额外清理逻辑）
 *
 * 文件名沿用 WebDavScreen.kt 是历史原因，实际只有一个入口 CloudBackupScreen。
 */
@Composable
fun CloudBackupScreen(onBack: () -> Unit) {
    val settings by Store.settings.collectAsState()
    val davState by Store.webdav.collectAsState()
    val s3State by Store.s3.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val backend = settings.cloudBackend
    val state = if (backend == CloudBackend.WEBDAV) davState else s3State

    // WebDAV
    var url by remember { mutableStateOf(settings.webdavUrl) }
    var user by remember { mutableStateOf(settings.webdavUser) }
    var pass by remember { mutableStateOf(Store.webdavPassOf()) }
    var path by remember { mutableStateOf(settings.webdavPath) }
    var showPass by remember { mutableStateOf(false) }

    // S3
    var ep by remember { mutableStateOf(settings.s3Endpoint) }
    var region by remember { mutableStateOf(settings.s3Region) }
    var bucket by remember { mutableStateOf(settings.s3Bucket) }
    var ak by remember { mutableStateOf(settings.s3AccessKey) }
    var sk by remember { mutableStateOf(Store.s3SecretOf()) }
    var skey by remember { mutableStateOf(settings.s3Key) }
    var pathStyle by remember { mutableStateOf(settings.s3PathStyle) }
    var showSk by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var askRestore by remember { mutableStateOf(false) }

    fun save() {
        Store.updateSettings(
            Store.settings.value.copy(
                webdavUrl = url.trim(),
                webdavUser = user.trim(),
                webdavPath = path.trim().ifBlank { "shellstore/shellstore-backup.json" },
                s3Endpoint = ep.trim(),
                s3Region = region.trim().ifBlank { "auto" },
                s3Bucket = bucket.trim(),
                s3AccessKey = ak.trim(),
                s3Key = skey.trim().ifBlank { "shellstore/shellstore-backup.json" },
                s3PathStyle = pathStyle
            )
        )
        Store.setWebdavPass(pass)
        Store.setS3Secret(sk)
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    fun run(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val msg = runCatching { block() }.getOrElse { it.message ?: it.javaClass.simpleName }
            busy = false
            toast(msg)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("云备份", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = 20.dp)
        ) {
            GroupTitle("备份通道")
            GroupCard {
                Row(
                    Modifier.padding(Space.md),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    CloudBackend.entries.forEach { b ->
                        FilterChip(b.label, backend == b, Modifier.weight(1f)) {
                            Store.updateSettings(Store.settings.value.copy(cloudBackend = b))
                        }
                    }
                }
                Divider()
                Text(
                    if (backend == CloudBackend.WEBDAV)
                        "任意支持 WebDAV 的服务：群晖、坚果云、Alist、自建 R2-WebDAV 等。目录不存在会自动创建。"
                    else
                        "S3 兼容对象存储：Cloudflare R2、AWS S3、MinIO、七牛 Kodo、阿里 OSS/腾讯 COS 的 S3 端点等。",
                    fontSize = TypeScale.caption,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)
                )
            }

            if (backend == CloudBackend.WEBDAV) {
                GroupTitle("WebDAV 服务器")
                GroupCard {
                    DavField(
                        "服务器地址", url, { url = it },
                        hint = "https://dav.example.com",
                        keyboard = KeyboardType.Uri
                    )
                    Divider()
                    DavField("用户名", user, { user = it }, hint = "留空表示匿名")
                    Divider()
                    DavField(
                        "密码", pass, { pass = it },
                        password = !showPass,
                        trailing = { RevealToggle(showPass) { showPass = !showPass } }
                    )
                    Divider()
                    DavField(
                        "远端路径", path, { path = it },
                        hint = "shellstore/shellstore-backup.json"
                    )
                }
                Hint("密码用系统密钥库加密后只存在本机；备份文件、云端快照、导出的 JSON 都不含密码。")
            } else {
                GroupTitle("S3 连接")
                GroupCard {
                    DavField(
                        "Endpoint", ep, { ep = it },
                        hint = "https://<账号ID>.r2.cloudflarestorage.com",
                        keyboard = KeyboardType.Uri
                    )
                    Divider()
                    DavField(
                        "Region", region, { region = it },
                        hint = "R2 填 auto；AWS 填桶所在区域，如 ap-northeast-1"
                    )
                    Divider()
                    DavField("Bucket", bucket, { bucket = it }, hint = "桶名")
                    Divider()
                    DavField(
                        "Access Key ID", ak, { ak = it },
                        hint = "只读+写对象权限即可"
                    )
                    Divider()
                    DavField(
                        "Secret Access Key", sk, { sk = it },
                        password = !showSk,
                        trailing = { RevealToggle(showSk) { showSk = !showSk } }
                    )
                    Divider()
                    DavField(
                        "对象键", skey, { skey = it },
                        hint = "shellstore/shellstore-backup.json"
                    )
                    Divider()
                    SettingRow(
                        "▦",
                        "路径风格",
                        if (pathStyle) "endpoint/bucket/key（MinIO、自建常用）" else "bucket.endpoint/key（R2、AWS 常用）",
                        trailing = { Toggle(pathStyle) { pathStyle = it } }
                    )
                }
                Hint("Secret Key 同样用系统密钥库加密只存本机。建议单独建一个只对该桶有读写权限的密钥。")
            }

            Spacer(Modifier.height(Space.sm))
            GroupCard {
                SettingRow("✓", "保存配置", "改完参数先保存", onClick = {
                    save(); toast("已保存")
                })
                Divider()
                SettingRow(
                    "▶", "测试连接",
                    if (busy) "正在连接…" else "只做一次无副作用的探测",
                    enabled = !busy,
                    onClick = {
                        save()
                        run {
                            val r = Store.pingCloud(backend)
                            if (r.ok) "连接正常：${r.msg}" else "失败：${r.msg}"
                        }
                    }
                )
            }

            GroupTitle("自动备份")
            GroupCard {
                SettingRow(
                    "⟳",
                    "变更后自动备份",
                    "数据改动约 30 秒后上传到「${backend.label}」，云端只保留最新一份",
                    trailing = {
                        Toggle(settings.webdavAuto) { on ->
                            Store.updateSettings(Store.settings.value.copy(webdavAuto = on))
                            if (on) {
                                save()
                                toast("已开启，改完数据会自动上传到「${backend.label}」")
                            }
                        }
                    }
                )
            }

            GroupTitle("手动")
            GroupCard {
                SettingRow(
                    "⇧", "立即备份",
                    if (busy) "正在上传…" else "订阅源 · 收藏夹 · 设置 · 最近使用",
                    enabled = !busy,
                    onClick = {
                        save()
                        run {
                            val r = Store.backupNow(backend)
                            if (r.ok) "备份完成，${r.msg}" else "备份失败：${r.msg}"
                        }
                    }
                )
                Divider()
                SettingRow(
                    "⇩", "从云端恢复",
                    if (busy) "正在下载…" else "覆盖本地或与本地合并",
                    enabled = !busy,
                    onClick = {
                        save()
                        askRestore = true
                    }
                )
            }

            GroupTitle("状态")
            GroupCard {
                SettingRow(
                    if (state.lastOk) "✓" else "⚠",
                    "上次备份（${backend.label}）",
                    when {
                        state.running -> "进行中…"
                        state.lastAt <= 0L -> "还没有备份过"
                        state.lastOk -> "${fmtTime(state.lastAt)} · ${state.lastMsg}"
                        else -> "${fmtTime(state.lastAt)} · 失败：${state.lastMsg}"
                    },
                    danger = state.lastAt > 0L && !state.lastOk
                )
            }
            Hint("订阅源内容本身不随备份走（只备份源的地址与开关），恢复后在「订阅源」里刷新一次即可重新下载。")
        }
    }

    if (askRestore) {
        AlertDialog(
            onDismissRequest = { askRestore = false },
            title = { Text("从「${backend.label}」恢复", fontSize = TypeScale.title) },
            text = {
                Column {
                    Text(
                        "云端快照会与你本机的数据合并或覆盖。覆盖会清掉本机现有的收藏夹、订阅源与设置。",
                        fontSize = TypeScale.small,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "「合并」按 id 去重，保留两边各自独有的条目与文件夹；「覆盖」以云端为准。",
                        fontSize = TypeScale.caption,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(Space.sm))
                    TextButton(onClick = {
                        askRestore = false
                        run {
                            val err = Store.restoreFromCloud(true, backend)
                            if (err == null) "已按云端覆盖，订阅源需刷新一次" else "恢复失败：$err"
                        }
                    }) { Text("覆盖本地") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askRestore = false
                    run {
                        val err = Store.restoreFromCloud(false, backend)
                        if (err == null) "已合并，订阅源需刷新一次" else "恢复失败：$err"
                    }
                }) { Text("合并") }
            },
            dismissButton = {
                TextButton(onClick = { askRestore = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RevealToggle(shown: Boolean, onClick: () -> Unit) {
    Text(
        if (shown) "隐藏" else "显示",
        fontSize = TypeScale.caption,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { onClick() }
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = TypeScale.caption,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 7.dp, start = 2.dp)
    )
}

@Composable
private fun DavField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String = "",
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(Modifier.padding(horizontal = Space.md, vertical = 10.dp)) {
        Text(label, fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    Text(hint, fontSize = TypeScale.body, color = MaterialTheme.colorScheme.outline)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = TypeScale.body,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    visualTransformation =
                        if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailing != null) trailing()
        }
    }
}
