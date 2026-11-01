package com.xbta.shellstore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.data.ThemeMode
import com.xbta.shellstore.ui.theme.AppTheme

sealed interface Page {
    data object AllCats : Page
    data class CatList(val cat: String) : Page
    data class Detail(val entryId: String) : Page
    data class FavFolder(val folderId: String?) : Page
    data object TagView : Page
    data object AllFav : Page
    data object Settings : Page
    data object Sources : Page
    data object Backup : Page
    data object About : Page
}

@Composable
fun App() {
    val settings by Store.settings.collectAsState()
    val dark = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    AppTheme(darkTheme = dark) {
        val d = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(d.density, d.fontScale * settings.fontScale)
        ) {
            Root()
        }
    }
}

@Composable
private fun Root() {
    var tab by remember { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf<List<Page>>(emptyList()) }
    val current = stack.lastOrNull()

    fun push(p: Page) { stack = stack + p }
    fun pop() { if (stack.isNotEmpty()) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (current == null) {
                when (tab) {
                    0 -> HomeScreen(onOpen = ::push)
                    1 -> FavScreen(onOpen = ::push)
                    else -> MeScreen(onOpen = ::push)
                }
            } else {
                when (val p = current) {
                    is Page.AllCats -> AllCatsScreen(onBack = ::pop, onOpenCat = { push(Page.CatList(it)) })
                    is Page.CatList -> CatListScreen(p.cat, onBack = ::pop, onOpen = ::push)
                    is Page.Detail -> DetailScreen(p.entryId, onBack = ::pop)
                    is Page.FavFolder -> FavFolderScreen(p.folderId, onBack = ::pop, onOpen = ::push)
                    is Page.TagView -> TagScreen(onBack = ::pop, onOpen = ::push)
                    is Page.AllFav -> AllFavScreen(onBack = ::pop, onOpen = ::push)
                    is Page.Settings -> SettingsScreen(onBack = ::pop)
                    is Page.Sources -> SourcesScreen(onBack = ::pop)
                    is Page.Backup -> BackupScreen(onBack = ::pop)
                    is Page.About -> AboutScreen(onBack = ::pop)
                }
            }
        }
        TabBar(current = tab, hasOverlay = stack.isNotEmpty()) {
            tab = it
            stack = emptyList()
        }
    }
}

@Composable
private fun TabBar(current: Int, hasOverlay: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        listOf("▦" to "商店", "☆" to "收藏", "◍" to "我的").forEachIndexed { i, pair ->
            val on = i == current && !hasOverlay
            val color = if (on) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(pair.first, fontSize = 17.sp, color = color)
                Spacer(Modifier.height(2.dp))
                Text(
                    pair.second,
                    fontSize = 10.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = color
                )
            }
        }
    }
}
