package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xbta.shellstore.core.Category
import com.xbta.shellstore.core.SmdPath
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 分类目录树的一行。缩进体现层级，父节点左侧有 ▾/▸ 可折叠。
 *
 * 缩进单位 14dp：三级以内不需要横向滚动，六层（MAX_DEPTH）时名称区仍有约 150dp。
 */
@Composable
fun CategoryRow(
    cat: Category,
    count: Int,
    index: Int,
    hasChild: Boolean,
    collapsed: Boolean,
    onToggle: (() -> Unit)?,
    onClick: () -> Unit,
    showChevron: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(start = Space.sm + (cat.depth * 14).dp, end = Space.md, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 折叠按钮：只有真有子目录时才可点，否则留出等宽占位让同级名称对齐
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .then(if (hasChild && onToggle != null) Modifier.clickable { onToggle() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (hasChild) {
                AppIcon(
                    if (collapsed) "›" else "▾",
                    size = 14.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(4.dp))

        Box(
            Modifier
                .size(if (cat.depth == 0) 26.dp else 22.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(
                    if (cat.depth == 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                cat.icon.ifBlank { catGlyph(index) },
                size = if (cat.depth == 0) IconSize.card else 13.dp,
                color = if (cat.depth == 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Space.sm))

        Column(Modifier.weight(1f)) {
            Text(
                cat.name,
                fontSize = if (cat.depth == 0) TypeScale.body else TypeScale.small,
                fontWeight = if (cat.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (cat.synthesized) {
                // 源里没写这一级，是子目录带出来的
                Text(
                    "目录",
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Text(
            "$count",
            fontSize = TypeScale.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showChevron) {
            Spacer(Modifier.width(Space.xs))
            AppIcon("›", size = IconSize.chevron, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * 首页的分类预览：按树序展示前 [limit] 个，不可折叠（避免首页状态太多）。
 * limit <= 0 表示全部。
 */
@Composable
fun CategoryTreePreview(
    cats: List<Category>,
    countOf: (Category) -> Int,
    onOpenCat: (String) -> Unit,
    limit: Int = 8
) {
    val shown = if (limit > 0) cats.take(limit) else cats
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        shown.forEachIndexed { i, c ->
            CategoryRow(
                cat = c,
                count = countOf(c),
                index = cats.indexOf(c),
                hasChild = hasChildOf(cats, c.id),
                collapsed = false,
                onToggle = null,
                onClick = { onOpenCat(c.id) }
            )
        }
        if (limit > 0 && cats.size > limit) {
            Text(
                "还有 ${cats.size - limit} 个分类，见「全部分类」",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = Space.sm, top = 2.dp)
            )
        }
    }
}

/** 判断有没有子目录（这里只用列表扫一遍，分类规模在几十到几百，足够快） */
private fun hasChildOf(cats: List<Category>, id: String): Boolean =
    cats.any { it.parentId == id }

/**
 * 面包屑：`系统 › 网络 › DNS`，除最后一级外都可点。
 * 用于分类页顶部，说明「你现在在哪一层」。
 */
@Composable
fun CategoryBreadcrumb(
    cat: Category,
    onOpenCat: (String) -> Unit,
    onOpenAll: () -> Unit
) {
    val segs = cat.segments
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "全部分类",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onOpenAll() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            segs.forEachIndexed { i, name ->
                Text(
                    "›",
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                val isLast = i == segs.lastIndex
                Text(
                    name,
                    fontSize = TypeScale.caption,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isLast) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = if (isLast) Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    else Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { onOpenCat(SmdPath.join(segs.take(i + 1))) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
