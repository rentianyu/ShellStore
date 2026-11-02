package com.xbta.shellstore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xbta.shellstore.BuildConfig
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.data.UpdateCenter
import com.xbta.shellstore.data.ThemeMode
import com.xbta.shellstore.ui.theme.AppTheme
import com.xbta.shellstore.ui.theme.Dimen
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.TypeScale
import com.xbta.shellstore.ui.theme.LocalCodeFont
import com.xbta.shellstore.ui.theme.resolveCodeFont
import com.xbta.shellstore.ui.theme.resolveUiFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Page {
    data object AllCats : Page
    data class CatList(val cat: String) : Page
    data class Detail(val entryId: String) : Page
    data class FavFolder(val folderId: String?) : Page
    data object TagView : Page
    data object AllFav : Page
    data object Recent : Page
    data object Settings : Page
    data object Sources : Page
    data object Backup : Page
    data object About : Page
    data object Help : Page
}

@Composable
fun App() {
    val settings by Store.settings.collectAsState()
    val dark = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val uiFont = remember(settings.uiFont, settings.customFontPath) {
        resolveUiFont(settings.uiFont, settings.customFontPath)
    }
    val codeFont = remember(settings.codeFont, settings.customFontPath) {
        resolveCodeFont(settings.codeFont, settings.customFontPath)
    }
    CompositionLocalProvider(LocalCodeFont provides codeFont) {
        AppTheme(darkTheme = dark, fontFamily = uiFont) {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, d.fontScale * settings.fontScale)
            ) {
                Root()
            }
        }
    }
}

@Composable
private fun Root() {
    // 首次安装（或还没走过欢迎页）先让用户明确启用官方源，不再是内置命令集
    val appSettings by Store.settings.collectAsState()
    val sourceList by Store.sources.collectAsState()
    LaunchedEffect(appSettings.onboarded, sourceList.size) {
        // 老用户已有自己的源，直接放行，不打扰
        if (!appSettings.onboarded && sourceList.isNotEmpty()) {
            Store.updateSettings(Store.settings.value.copy(onboarded = true))
        }
    }
    if (!appSettings.onboarded && sourceList.isEmpty()) {
        WelcomeScreen(onDone = { Store.updateSettings(Store.settings.value.copy(onboarded = true)) })
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf<List<Page>>(emptyList()) }
    val current = stack.lastOrNull()

    val ctx = LocalContext.current
    val upState by UpdateCenter.state.collectAsState()
    val srcDiffs by Store.pendingUpdates.collectAsState()
    val holder = rememberSaveableStateHolder()

    // 启动后静默检查：先看自身有没有新版本（下完再弹窗），再看订阅源有没有变化
    LaunchedEffect(Unit) {
        delay(2000)
        withContext(Dispatchers.IO) {
            UpdateCenter.checkAndDownload(ctx, BuildConfig.APP_VERSION)
        }
    }
    LaunchedEffect(Unit) {
        delay(3500)
        withContext(Dispatchers.IO) { Store.checkSourceUpdates() }
    }

    fun push(p: Page) { stack = stack + p }
    fun pop() { if (stack.isNotEmpty()) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val downloading = upState as? UpdateCenter.State.Downloading
        if (downloading != null) {
            DownloadBar(downloading.pct)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // 记住每页的滚动位置与临时状态：从详情返回列表不会跳回顶部
            holder.SaveableStateProvider(current?.toString() ?: "tab$tab") {
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
                    is Page.Recent -> RecentScreen(onBack = ::pop, onOpen = ::push)
                    is Page.Settings -> SettingsScreen(onBack = ::pop)
                    is Page.Sources -> SourcesScreen(onBack = ::pop)
                    is Page.Backup -> BackupScreen(onBack = ::pop)
                    is Page.About -> AboutScreen(onBack = ::pop, onOpen = ::push)
                    is Page.Help -> HelpScreen(onBack = ::pop)
                }
            }
            }
        }
        TabBar(current = tab, hasOverlay = stack.isNotEmpty()) {
            tab = it
            stack = emptyList()
        }
    }

    // 启动检查的两个提示：先自身更新，再订阅源差异
    val st = upState
    if (st is UpdateCenter.State.Ready) {
        UpdateReadyDialog(
            tag = st.tag,
            currentVersion = BuildConfig.APP_VERSION,
            onInstall = {
                val err = UpdateCenter.install(ctx)
                if (err != null) android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
                UpdateCenter.dismiss()
            },
            onLater = { UpdateCenter.dismiss() }
        )
    } else if (srcDiffs.isNotEmpty()) {
        SourceUpdateDialog(
            diffs = srcDiffs,
            onConfirm = { Store.applySourceUpdates() },
            onCancel = { Store.dismissSourceUpdates() }
        )
    }
}

@Composable
private fun TabBar(current: Int, hasOverlay: Boolean, onSelect: (Int) -> Unit) {
    // 浮动胶囊 dock：不贴边、带阴影，落到页面底色上，才有「浮起来」的层次。
    // 选中指示器用中性色托盘（比 dock 亮一档）而不是主色药丸，色只留给图标和文字。
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimen.tabBar)
                .shadow(10.dp, RoundedCornerShape(Radius.lg), clip = false)
                .clip(RoundedCornerShape(Radius.lg))
                .background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("▦" to "商店", "☆" to "收藏", "◍" to "我的").forEachIndexed { i, pair ->
                val on = i == current && !hasOverlay
                val fg = if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .width(58.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (on) MaterialTheme.colorScheme.surfaceContainerHighest
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(pair.first, fontSize = IconSize.tab, color = fg)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        pair.second,
                        fontSize = TypeScale.small,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = fg
                    )
                }
            }
        }
    }
}
