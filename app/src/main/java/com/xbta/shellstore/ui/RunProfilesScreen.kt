package com.xbta.shellstore.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.RunHost
import com.xbta.shellstore.core.RunProfile
import com.xbta.shellstore.core.RunTemplate
import com.xbta.shellstore.data.ShellExec
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 运行方式：一条命令「怎么跑」的可配置清单。
 *
 * 每一项 = 宿主（本机 / Shizuku / 交给 Termux）+ 命令行模板（`$command` 是占位符）。
 * 执行时按列表从上到下依次尝试，第一个返回 0 就停。
 * 内置四项（系统 / Shizuku / Root / Termux）名称与模板都能改，但不能删。
 */
@Composable
fun RunProfilesScreen(onBack: () -> Unit) {
    val settings by Store.settings.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<RunProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ShellExec.Result?>(null) }

    val list = settings.runProfiles

    fun save(newList: List<RunProfile>) = Store.setRunProfiles(newList)

    fun move(i: Int, delta: Int) {
        val to = i + delta
        if (to < 0 || to >= list.size) return
        val m = list.toMutableList()
        val tmp = m[i]
        m[i] = m[to]
        m[to] = tmp
        save(m)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("运行方式", onBack = onBack, actions = {
            Text(
                "恢复默认",
                fontSize = TypeScale.small,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { confirmReset = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "执行命令时按下面的顺序依次尝试，第一个成功即停。关掉的项不参与。",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.sm)
            )

            GroupCard {
                list.forEachIndexed { i, p ->
                    if (i > 0) Divider()
                    ProfileRow(
                        profile = p,
                        index = i,
                        last = list.lastIndex,
                        onToggle = { on ->
                            save(list.map { if (it.id == p.id) it.copy(enabled = on) else it })
                        },
                        onUp = { move(i, -1) },
                        onDown = { move(i, 1) },
                        onClick = { editing = p }
                    )
                }
                Divider()
                SettingRow(
                    "+", "新增执行方式",
                    "自己写模板，例如 /sdcard/bin/python -c \$command",
                    onClick = { adding = true }
                )
            }

            GroupTitle("测试")
            GroupCard {
                Column(Modifier.padding(Space.md)) {
                    Text(
                        "用现在这套配置跑一条 echo，看哪个方式能通、输出是什么。",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip("跑一次测试", testing) {
                            if (testing) return@FilterChip
                            testing = true
                            testResult = null
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    ShellExec.runProfiles(
                                        ctx,
                                        "echo ShellStore-ok && id -u",
                                        Store.activeRunProfiles()
                                    )
                                }
                                testResult = r
                                testing = false
                            }
                        }
                        FilterChip("清空", false) { testResult = null }
                    }
                    testResult?.let { r ->
                        Spacer(Modifier.height(9.dp))
                        Text(
                            if (r.ok) "成功 · ${r.profileName.ifBlank { r.channel.label }}"
                            else "失败 · ${r.profileName.ifBlank { r.channel.label }}",
                            fontSize = TypeScale.small,
                            fontWeight = FontWeight.Medium,
                            color = if (r.ok) MaterialTheme.colorScheme.primary else Tones.danger
                        )
                        val body = listOf(r.out, r.err, r.note)
                            .filter { it.isNotBlank() }.joinToString("\n")
                        if (body.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(body, fontSize = TypeScale.caption, lineHeight = 17.sp)
                        }
                    }
                    if (Store.activeRunProfiles().isEmpty()) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "现在一项都没启用，命令会执行不了。",
                            fontSize = TypeScale.caption,
                            color = Tones.danger
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.md))
            Text(
                "模板里的 \$command 会被替换成命令本身，替换时自动做 shell 引用，" +
                    "命令里带引号或 \$ 也不会被吃掉。占位符写成 \"\$command\" 也可以，会按双引号内规则转义。",
                fontSize = TypeScale.caption,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (adding || editing != null) {
        val editingNow = editing
        ProfileDialog(
            initial = editingNow,
            onDismiss = { adding = false; editing = null },
            onSave = { p ->
                if (editingNow == null) save(list + p)
                else save(list.map { if (it.id == p.id) p else it })
                adding = false
                editing = null
            },
            onDelete = editingNow?.takeIf { !it.builtin }?.let { p ->
                {
                    save(list.filterNot { it.id == p.id })
                    editing = null
                    Toast.makeText(ctx, "已删除「${p.name}」", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("恢复默认运行方式", fontSize = TypeScale.body) },
            text = {
                Text(
                    "内置四项回到出厂设置（系统 / Shizuku / Root / 交给 Termux），" +
                        "自己加的会保留在末尾但关闭。",
                    fontSize = 12.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val customs = settings.runProfiles.filterNot { it.builtin }
                        .map { it.copy(enabled = false) }
                    save(RunProfile.defaults() + customs)
                    confirmReset = false
                }) { Text("恢复", color = Tones.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ProfileRow(
    profile: RunProfile,
    index: Int,
    last: Int,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = Space.md, end = Space.xs, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.name,
                    fontSize = TypeScale.small,
                    fontWeight = FontWeight.SemiBold,
                    color = if (profile.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    profile.host.label,
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                profile.templateOrPlain,
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 顺序就是尝试顺序，上下箭头比拖动稳（不用处理手势与滚动冲突）
        OrderArrow("↑", index == 0) { onUp() }
        OrderArrow("↓", index == last) { onDown() }
        Spacer(Modifier.width(2.dp))
        Toggle(profile.enabled, onToggle)
    }
}

@Composable
private fun OrderArrow(glyph: String, disabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(enabled = !disabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            fontSize = TypeScale.small,
            color = if (disabled) MaterialTheme.colorScheme.outline.copy(alpha = .4f)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileDialog(
    initial: RunProfile?,
    onDismiss: () -> Unit,
    onSave: (RunProfile) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var template by remember { mutableStateOf(initial?.templateOrPlain ?: RunProfile.PH) }
    var host by remember { mutableStateOf(initial?.host ?: RunHost.DIRECT) }

    val builtin = initial?.builtin == true
    val valid = name.isNotBlank() && template.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增执行方式" else "编辑执行方式", fontSize = TypeScale.body) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("由谁执行", fontSize = TypeScale.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RunHost.entries.forEach { h ->
                        FilterChip(h.label, host == h, Modifier.weight(1f)) { host = h }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("命令模板") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "预览：" + RunTemplate.render(template, "ls -l"),
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                // 常用写法一键填，省得手打占位符
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "\$command",
                        "su -c \$command",
                        "/sdcard/bin/python -c \$command"
                    ).forEach { t ->
                        FilterChip(if (t == "\$command") "直接跑" else t, template == t) { template = t }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val id = initial?.id ?: ("c" + System.currentTimeMillis().toString(36))
                    onSave(
                        RunProfile(
                            id = id,
                            name = name.trim(),
                            host = host,
                            template = template.trim(),
                            enabled = initial?.enabled ?: true,
                            builtin = builtin
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = Tones.danger) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
