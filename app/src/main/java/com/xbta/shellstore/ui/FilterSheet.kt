package com.xbta.shellstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xbta.shellstore.core.Category
import com.xbta.shellstore.core.EntryFilter
import com.xbta.shellstore.core.EntryType
import com.xbta.shellstore.core.FilterFlag
import com.xbta.shellstore.core.FilterSort
import com.xbta.shellstore.core.FilterState
import com.xbta.shellstore.core.Need
import com.xbta.shellstore.core.Risk
import com.xbta.shellstore.core.TagMode
import com.xbta.shellstore.data.SourceConfig
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale

/**
 * 筛选面板（商店页与收藏页共用）。
 *
 * 每改一项立刻回调（实时生效），没有「应用」按钮，关掉就是完成。
 * 分区：排序 / 类型 / 风险 / 权限 / 分类 / 来源 / 标签 / 属性。
 */
@Composable
fun FilterSheet(
    state: FilterState,
    onChange: (FilterState) -> Unit,
    onDismiss: () -> Unit,
    categories: List<Category>,
    sources: List<SourceConfig>,
    tags: List<String>,
    /** 收藏页才有的属性（改过内容、源里还在） */
    showFavFlags: Boolean = false,
    /** 商店页才有「已收藏」 */
    showFavFlag: Boolean = true
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("筛选", fontSize = TypeScale.title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(Space.sm))
                if (state.activeCount > 0) {
                    Text(
                        "${state.activeCount} 项生效",
                        fontSize = TypeScale.caption,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "重置",
                    fontSize = TypeScale.small,
                    color = if (state.isDefault) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable(enabled = !state.isDefault) { onChange(FilterState()) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    "完成",
                    fontSize = TypeScale.small,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Divider()

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg)
                    .padding(bottom = Space.lg)
            ) {
                FSecTitle("排序")
                ChipFlow {
                    FilterSort.entries.forEach { s ->
                        PickChip(s.label, state.sort == s) {
                            onChange(state.copy(sort = if (state.sort == s) FilterSort.DEFAULT else s))
                        }
                    }
                }

                FSecTitle("类型")
                ChipFlow {
                    EntryType.entries.forEach { t ->
                        PickChip(t.id, t in state.types) {
                            onChange(state.copy(types = state.types.toggle(t)))
                        }
                    }
                }

                FSecTitle("风险")
                ChipFlow {
                    Risk.entries.forEach { r ->
                        PickChip(r.label, r in state.risks) {
                            onChange(state.copy(risks = state.risks.toggle(r)))
                        }
                    }
                }

                FSecTitle("权限需求")
                ChipFlow {
                    Need.entries.forEach { n ->
                        PickChip(n.label, n in state.needs) {
                            onChange(state.copy(needs = state.needs.toggle(n)))
                        }
                    }
                }

                if (categories.isNotEmpty()) {
                    FSecTitle("分类")
                    ChipFlow {
                        categories.forEach { c ->
                            PickChip(
                                if (c.depth > 0) "· ".repeat(c.depth) + c.name else c.name,
                                c.id in state.cats
                            ) {
                                onChange(state.copy(cats = state.cats.toggle(c.id)))
                            }
                        }
                    }
                }

                if (sources.size > 1) {
                    FSecTitle("来源")
                    ChipFlow {
                        sources.forEach { s ->
                            PickChip(s.name, s.id in state.sources) {
                                onChange(state.copy(sources = state.sources.toggle(s.id)))
                            }
                        }
                        PickChip("本地自建", EntryFilter.LOCAL_SOURCE in state.sources) {
                            onChange(state.copy(sources = state.sources.toggle(EntryFilter.LOCAL_SOURCE)))
                        }
                    }
                }

                if (tags.isNotEmpty()) {
                    FSecTitle("标签")
                    ChipFlow {
                        tags.take(40).forEach { t ->
                            PickChip(t, t in state.tags) {
                                onChange(state.copy(tags = state.tags.toggle(t)))
                            }
                        }
                    }
                    if (tags.size > 40) {
                        Text(
                            "只列前 40 个（共 ${tags.size} 个）",
                            fontSize = TypeScale.caption,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (state.tags.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        ChipFlow {
                            TagMode.entries.forEach { m ->
                                PickChip("${m.label}命中", state.tagMode == m) {
                                    onChange(state.copy(tagMode = m))
                                }
                            }
                        }
                    }
                }

                FSecTitle("属性")
                ChipFlow {
                    FilterFlag.entries.forEach { fl ->
                        val show = when (fl) {
                            FilterFlag.EDITED, FilterFlag.ALIVE -> showFavFlags
                            FilterFlag.FAVORITED -> showFavFlag
                            else -> true
                        }
                        if (show) {
                            PickChip(fl.label, fl in state.flags) {
                                onChange(state.copy(flags = state.flags.toggle(fl)))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Space.md))
                Text(
                    "同一组内多选是「任一命中」，不同组之间同时满足。分类按目录匹配：选「系统」也会带出「系统/网络」里的条目。",
                    fontSize = TypeScale.caption,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * 列表顶部的筛选状态条：没筛选时只是一个「筛选」入口，筛选后显示生效项与命中数。
 */
@Composable
fun FilterBar(
    state: FilterState,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    total: Int,
    shown: Int
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(
                if (state.activeCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onOpen() }
            .padding(horizontal = Space.md, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon("▽", size = 15.dp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(
            if (state.activeCount > 0) "筛选 ${state.activeCount} 项" else "筛选",
            fontSize = TypeScale.small,
            fontWeight = if (state.activeCount > 0) FontWeight.SemiBold else FontWeight.Normal,
            color = if (state.activeCount > 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.activeCount > 0) {
            Spacer(Modifier.width(Space.sm))
            Text(
                "$shown / $total 条",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.activeCount > 0) {
            Text(
                "清空",
                fontSize = TypeScale.caption,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onClear() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        } else {
            AppIcon("›", size = 14.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

@Composable
private fun FSecTitle(text: String) {
    Text(
        text,
        fontSize = TypeScale.caption,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.md, bottom = 6.dp)
    )
}

/** 自动换行的 chip 容器，省去手写 Row 分行 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        fontSize = 11.5.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}
