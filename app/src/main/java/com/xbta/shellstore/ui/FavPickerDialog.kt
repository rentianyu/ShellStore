package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.data.FavData
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 点星标收藏时选文件夹。
 *
 * 点某一行就直接完成收藏（不再要一次「确认」），因为这一步的意图很明确：
 * 用户点星标是想收起来，弹框只是问「放哪儿」。
 */
@Composable
fun FavFolderPickerDialog(
    entry: Entry,
    favs: FavData,
    onPick: (folderId: String?, dontAskAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var dontAsk by remember { mutableStateOf(false) }
    val folders = remember(favs) { favs.orderedFolders() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("收藏到", fontSize = TypeScale.body, fontWeight = FontWeight.SemiBold)
                Text(
                    entry.title,
                    fontSize = TypeScale.small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column {
                Column(
                    Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    PickerRow(
                        label = "不放入文件夹",
                        count = favs.rootCount,
                        depth = 0,
                        onClick = { onPick(null, dontAsk) }
                    )
                    if (folders.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                    }
                    folders.forEach { (f, depth) ->
                        PickerRow(
                            label = f.name,
                            count = favs.countOf(f.id),
                            depth = depth,
                            onClick = { onPick(f.id, dontAsk) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { dontAsk = !dontAsk }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Text(
                        "以后直接收藏，不再询问",
                        fontSize = TypeScale.small,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PickerRow(label: String, count: Int, depth: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(start = (Space.md + (depth * 14).dp), end = Space.sm, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (depth > 0) {
            Text(
                "└",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Text(
            label,
            fontSize = TypeScale.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count > 0) {
            Text(
                "$count",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}
