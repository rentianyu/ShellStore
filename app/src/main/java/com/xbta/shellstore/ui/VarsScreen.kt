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
import com.xbta.shellstore.core.GlobalVars
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.Tones
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 全局变量：`{{name}}` 在任何命令里都能引用，执行前替换成实际值。
 * 适合放设备序列号、常用目录、固定 token 这类跨条目重复的东西。
 */
@Composable
fun VarsScreen(onBack: () -> Unit) {
    val settings by Store.settings.collectAsState()
    val entries by Store.entries.collectAsState()
    val ctx = LocalContext.current

    var editing by remember { mutableStateOf<String?>(null) }  // null=关闭；""=新增；其余=编辑该名称
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val vars = settings.globalVars

    // 哪些条目引用了未定义的变量（只提示，不拦截执行）
    val missingReport = remember(vars, entries) {
        entries.mapNotNull { e ->
            val cmd = e.body.ifBlank { e.adb }
            val miss = GlobalVars.missing(cmd, vars)
            if (miss.isEmpty()) null else e.title to miss
        }
    }

    fun save(name: String, value: String, renameFrom: String?) {
        val next = vars.toMutableMap()
        if (renameFrom != null && renameFrom != name) next.remove(renameFrom)
        next[name] = value
        Store.setGlobalVars(next)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("全局变量", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "命令里写 {{名字}}，执行前会替换成对应的值。条目本身不改，换设备只改这里。",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.sm)
            )

            GroupCard {
                if (vars.isEmpty()) {
                    Column(Modifier.padding(Space.md)) {
                        Text("还没有变量", fontSize = TypeScale.small)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "例如加一个 serial = 你的设备序列号，命令里写 adb -s {{serial}} shell",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                vars.keys.sorted().forEachIndexed { i, name ->
                    if (i > 0) Divider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = name }
                            .padding(horizontal = Space.md, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "{{$name}}",
                                fontSize = TypeScale.small,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                vars[name].orEmpty().ifBlank { "（空值）" },
                                fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "改",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Divider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = "" }
                        .padding(horizontal = Space.md, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+", fontSize = TypeScale.body, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Space.sm))
                    Text("新增变量", fontSize = TypeScale.body)
                }
            }

            if (missingReport.isNotEmpty()) {
                GroupTitle("引用了未定义的变量")
                GroupCard {
                    Column(Modifier.padding(Space.md)) {
                        Text(
                            "这些命令里写了没定义的变量，执行时会原样保留（不会报错，但也不会替换）。",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(6.dp))
                        missingReport.take(12).forEach { (title, miss) ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    title,
                                    fontSize = TypeScale.caption,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    miss.joinToString(" ") { "{{$it}}" },
                                    fontSize = TypeScale.caption,
                                    color = Tones.danger
                                )
                            }
                        }
                        if (missingReport.size > 12) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "还有 ${missingReport.size - 12} 条未列出",
                                fontSize = TypeScale.caption,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.md))
            Text(
                "变量名只能用字母、数字、下划线、点和连字符；不要和条目自己的参数同名（{参数} 是另一套，会被先替换）。",
                fontSize = TypeScale.caption,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (editing != null) {
        val isNew = editing == ""
        val oldName = if (isNew) null else editing
        var name by remember(editing) { mutableStateOf(oldName.orEmpty()) }
        var value by remember(editing) { mutableStateOf(oldName?.let { vars[it] }.orEmpty()) }
        val nameOk = GlobalVars.isValidName(name)
        val dup = isNew && vars.containsKey(name.trim())

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isNew) "新增变量" else "编辑变量", fontSize = TypeScale.body) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名字（命令里写 {{名字}}）") },
                        singleLine = true,
                        isError = name.isNotBlank() && (!nameOk || dup),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (name.isNotBlank() && !nameOk) {
                        Text(
                            "只能用字母、数字、下划线、点、连字符",
                            fontSize = TypeScale.caption,
                            color = Tones.danger
                        )
                    } else if (dup) {
                        Text("已有同名变量", fontSize = TypeScale.caption, color = Tones.danger)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("值") },
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && nameOk && !dup,
                    onClick = {
                        save(name.trim(), value, oldName)
                        editing = null
                        Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                Row {
                    if (!isNew) {
                        TextButton(onClick = { confirmDelete = oldName; editing = null }) {
                            Text("删除", color = Tones.danger)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text("取消") }
                }
            }
        )
    }

    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除变量 {{$name}}", fontSize = TypeScale.body) },
            text = {
                Text(
                    "引用它的命令会保留 {{$name}} 原文，执行时不再替换。",
                    fontSize = 12.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.setGlobalVars(vars - name)
                    confirmDelete = null
                }) { Text("删除", color = Tones.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}
