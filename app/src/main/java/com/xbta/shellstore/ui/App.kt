package com.xbta.shellstore.ui

import android.widget.Toast
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
import com.xbta.shellstore.core.Entry
import com.xbta.shellstore.core.LegalText
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.data.UpdateCenter
import com.xbta.shellstore.data.ThemeMode
import com.xbta.shellstore.ui.theme.AppTheme
import com.xbta.shellstore.ui.theme.Dimen
import com.xbta.shellstore.ui.theme.IconSize
import com.xbta.shellstore.ui.theme.Radius
import com.xbta.shellstore.ui.theme.Space
import com.xbta.shellstore.ui.theme.TypeScale
import com.xbta.shellstore.ui.theme.LocalCodeFont
import com.xbta.shellstore.ui.theme.resolveCodeFont
import com.xbta.shellstore.ui.theme.resolveUiFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** LocalContext 可能被包在 ContextWrapper 里，直接 as? Activity 会拿到 null */
private fun findActivity(c: android.content.Context?): android.app.Activity? {
    var cur = c
    while (cur is android.content.ContextWrapper) {
        if (cur is android.app.Activity) return cur
        cur = cur.baseContext
    }
    return null
}

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
    data object SourceHelp : Page
    data class Legal(val kind: String) : Page
    /** entryId 为空 = 新建；folderId 决定新条目落在哪个收藏夹；draft 用于粘贴导入时预填 */
    data class Editor(
        val entryId: String? = null,
        val folderId: String? = null,
        val draft: Entry? = null
    ) : Page
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
    val appSettings by Store.settings.collectAsState()
    val sourceList by Store.sources.collectAsState()
    val basicMode by Store.basicMode.collectAsState()

    // 协议门控：未同意（或条款升级）先进同意页，同意前不发生任何联网
    if (appSettings.agreedVersion < LegalText.LEGAL_VERSION && !basicMode) {
        AgreementScreen(
            onAgree = { Store.agreeLegal() },
            onDecline = { Store.declineLegal() }
        )
        return
    }
    // 老用户已有源，直接算作已初始化，不打扰
    LaunchedEffect(appSettings.onboarded, sourceList.size) {
        if (!appSettings.onboarded && sourceList.isNotEmpty()) {
            Store.updateSettings(Store.settings.value.copy(onboarded = true))
        }
    }
    // 首次启动不再拦路要用户点「启用官方源」：后台自动打开「小贝塔订阅源」并加默认项，
    // 失败（比如没网）就不置 onboarded，下次启动再试
    LaunchedEffect(Unit) {
        if (appSettings.onboarded || !Store.netAllowed) return@LaunchedEffect
        val err = withContext(Dispatchers.IO) { Store.bootstrapOnboarding() }
        if (err == null) {
            Store.updateSettings(Store.settings.value.copy(onboarded = true))
        }
    }

    var tab by remember { mutableIntStateOf(0) }
    var stack by remember { mutableStateOf<List<Page>>(emptyList()) }
    val current = stack.lastOrNull()

    val ctx = LocalContext.current
    val upState by UpdateCenter.state.collectAsState()
    val srcDiffs by Store.pendingUpdates.collectAsState()
    val holder = rememberSaveableStateHolder()

    // 启动后静默检查：先看自身有没有新版本（下完再弹窗），再看订阅源有没有变化
    // 只有同意协议后才联网：检查更新 + 检查源变化
    LaunchedEffect(Unit) {
        if (!Store.netAllowed) return@LaunchedEffect
        delay(2000)
        withContext(Dispatchers.IO) {
            UpdateCenter.checkAndDownload(ctx, BuildConfig.APP_VERSION)
        }
    }
    LaunchedEffect(Unit) {
        if (!Store.netAllowed) return@LaunchedEffect
        delay(3500)
        withContext(Dispatchers.IO) { Store.checkSourceUpdates() }
    }

    fun push(p: Page) { stack = stack + p }
    fun pop() { if (stack.isNotEmpty()) stack = stack.dropLast(1) }

    // 返回：有子页面就退一层；已经在 Tab 根页时才走「再按一次退出」，
    // 避免误触直接关掉 App。内层页面自己的 BackHandler（搜索、多选、弹窗）
    // 在组合树里更靠内，会先于这里拿到事件。
    var backArmed by remember { mutableStateOf(false) }
    val backScope = rememberCoroutineScope()
    BackHandler {
        if (stack.isNotEmpty()) {
            pop()
            backArmed = false
        } else if (backArmed) {
            findActivity(ctx)?.finish()
        } else {
            backArmed = true
            Toast.makeText(ctx, "再按一次退出", Toast.LENGTH_SHORT).show()
            backScope.launch {
                delay(2000)
                backArmed = false
            }
        }
    }

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

        // 基础模式：不同意隐私政策时进入，联网全部关闭，这里给一条恢复入口
        if (basicMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { Store.exitBasicMode() }
                    .padding(horizontal = Space.page, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "未同意隐私政策，联网功能已关闭",
                    fontSize = TypeScale.caption,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "去同意 ›",
                    fontSize = TypeScale.caption,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
                    is Page.Detail -> DetailScreen(p.entryId, onBack = ::pop, onOpen = ::push)
                    is Page.FavFolder -> FavFolderScreen(p.folderId, onBack = ::pop, onOpen = ::push)
                    is Page.TagView -> TagScreen(onBack = ::pop, onOpen = ::push)
                    is Page.AllFav -> AllFavScreen(onBack = ::pop, onOpen = ::push)
                    is Page.Recent -> RecentScreen(onBack = ::pop, onOpen = ::push)
                    is Page.Settings -> SettingsScreen(onBack = ::pop, onOpen = ::push)
                    is Page.Sources -> SourcesScreen(onBack = ::pop, onOpen = ::push)
                    is Page.Backup -> BackupScreen(onBack = ::pop)
                    is Page.About -> AboutScreen(onBack = ::pop, onOpen = ::push)
                    is Page.Help -> HelpScreen(onBack = ::pop)
                    is Page.SourceHelp -> SourceHelpScreen(onBack = ::pop)
                    is Page.Legal -> LegalScreen(p.kind, onBack = ::pop)
                    is Page.Editor -> EntryEditorScreen(p.entryId, p.folderId, p.draft, onBack = ::pop)
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
                    // 选中态只用颜色区分，不加托盘/胶囊底（避免看起来像一块椭圆阴影）
                    Box(
                        Modifier.width(58.dp).height(32.dp),
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
