# 架构

## 一句话

单 Activity + Jetpack Compose，全部状态集中在一个 `object Store`（StateFlow），
数据靠手写 JSON 落盘，内容靠手写 SMD 解析器从订阅源读入。没有数据库、没有序列化框架、
没有图片库、没有 DI 框架、没有 ViewModel。

## 分层

```
core/                    纯 Kotlin，不依赖 android.*（可用 kotlinc 单独编译验证）
  Model.kt               Entry / IntentSpec / Param / Variant / Category / Source / ParseResult
  SmdParser.kt           SMD 文本 → Source（含告警收集）
  SmdWriter.kt           Entry → SMD 片段 / 收藏 → 完整源（导出反向）
  CommandRenderer.kt     条目 + 参数值 + 变体索引 → 最终要复制/执行的命令文本
  SourceHelpText.kt      「怎么写源」的两份文案（给人看的最小模板 / 给 AI 的完整规范）

data/                    数据与 IO，依赖 android.content.Context
  Store.kt               全局单例：所有 StateFlow + 所有业务动作（829 行）
  Repo.kt                本地存储：settings/sources/favorites/recent/search 的读写与 JSON 编解码
  Sources.kt             SourceConfig（源配置）与 SourceCache（正文缓存）
  Mirrors.kt             拉取线路表 + 线路择优
  ShellExec.kt           命令执行：本机 / Root / Termux 三通道降级
  Updater.kt             自身更新：查版本、下载 APK、交系统安装
  UpdateCenter.kt        更新的状态机 + 启动时静默检查

ui/                      Compose 界面
  App.kt                 根：主题、字体缩放、Tab + 页面栈、启动检查
  HomeScreen.kt          商店（分类宫格 / 全部 / 搜索结果 / 分类内列表）
  FavScreen.kt           收藏（文件夹 / 标签 / 全部 / 多选 / 拖拽排序 / 各种弹窗）
  MeScreen.kt            我的（设置 / 订阅源 / 备份恢复 / 关于）
  DetailScreen.kt        详情（内容块、参数输入、变体切换、执行、Intent 字段表）
  EntryEditor.kt         新建/编辑条目（全字段表单）
  PasteImport.kt         粘贴识别与导入（JSON 备份 / SMD / 单条命令）
  Export.kt              导出范围选择 + 系统文件选择器 + SMD 生成
  SourceHelpScreen.kt    订阅源帮助页
  RecentScreen.kt        最近使用
  HelpScreen.kt          使用帮助
  Welcome.kt             首次启动引导
  UpdateDialogs.kt       更新相关弹窗
  Widgets.kt             公共组件：TopBar/IconBtn/CodeBlock/EntryRow/Badge/FilterChip/GroupCard…
  IntentLauncher.kt      条目 → 真实 Intent 并触发
  theme/                 Palette（配色）、Theme（MaterialTheme 装配）、Tokens（尺寸/字号）、
                         Tones（语义色）、Fonts（字体解析）
```

依赖方向严格单向：`ui → data → core`，`ui → core`。`core` 不许 import `data` 或 `ui`。

## 状态管理

`Store` 是唯一的数据源（single source of truth），暴露 12 个 StateFlow：

| Flow | 内容 |
|---|---|
| `entries` | 所有已启用源的条目合并结果（去重后） |
| `categories` | 分类合并结果 |
| `sources` | 源配置列表（含启用状态、条数、错误信息） |
| `favs` | `FavData`：文件夹 + 标签 + 收藏项 |
| `settings` | `AppSettings`：主题、字体、字号缩放、复制前缀、确认开关等 |
| `recent` | 最近使用（上限 60） |
| `searchHistory` | 最近搜索（上限 10） |
| `pendingUpdates` | 源更新差异，等用户确认 |
| `warnings` | 解析告警 |
| `sourceName` / `sourceVersion` / `loaded` | 单个源场景的元信息 |

界面一律 `Store.xxx.collectAsState()`，不做本地副本；写操作直接调 `Store.funXxx()`，
方法内部改 Flow + 落盘。

**没有 ViewModel**：单 Activity + 单例 Store，页面切换不会重新加载数据。
页面级临时状态（搜索词、当前文件夹、弹窗）用 `remember`，由
`rememberSaveableStateHolder()` 按页面 key 保存与恢复（见 `App.kt` 的 `Root()`）。

## 导航

不用 Navigation-Compose，用手写的栈：

```kotlin
sealed interface Page {
    data object AllCats; data class CatList(cat); data class Detail(entryId)
    data class FavFolder(folderId); TagView; AllFav; Recent
    Settings; Sources; Backup; About; Help; SourceHelp
    data class Editor(entryId = null, folderId = null, draft = null)
}
```

- `stack: List<Page>`，`push` 加尾、`pop` 去尾
- 底部三个 Tab（商店 / 收藏 / 我的）是平级，切 Tab 会清空栈
- `BackHandler` 逐层拦截：先关弹窗 → 退搜索/多选 → 再 pop 页面
- 每页滚动位置由 `SaveableStateProvider(current?.toString() ?: "tab$tab")` 保值

新增页面 = 加 Page 分支 + 在 `when (val p = current)` 里挂一个 Composable，
不需要改任何依赖注入或注册表。

## 数据流

### 启动

```
MainActivity → Store.init(context)
  ├─ loadSettings / loadSourceConfigs / loadFavs / loadRecent / loadSearchHistory
  └─ reloadAll()
       ├─ 对每个启用的源：读 SourceCache 正文 → SmdParser.parse
       └─ 合并（条目按 id 去重）→ 写 entries / categories
App() → Root()
  ├─ 2s 后静默检查自身更新（UpdateCenter.checkAndDownload）
  └─ 3.5s 后静默检查源更新（Store.checkSourceUpdates，只比对条数）
```

### 拉取与降级

```
Sources.kt     源配置：id / name / url / type(REMOTE|LOCAL) / enabled / lastFetch / count / error
SourceCache    正文缓存（按源 id 存文件），离线可用
Mirrors.kt     17 个线路节点，Form 分 FULL（要 https:// 前缀）和 BARE（不能带）
               单个节点 10s 超时，失败换下一个；上次成功的线路提到最前（存 settings.sourceMirror）
```

### 更新确认

源更新**不自动应用**：`checkSourceUpdates()` 只比对条数、算差异放进 `pendingUpdates`，
`App.kt` 弹窗问「新增 N 条、删除 M 条」，确认后才 `applySourceUpdates()` 覆写缓存并 reload。

### 收藏与编辑

收藏项 `FavItem` 带完整 `Entry` 快照。两条取值路径：

```kotlin
// Store.entryById
fun entryById(id: String): Entry? {
    val fav = favs.items.firstOrNull { it.entryId == id }
    if (fav != null && fav.edited) return fav.entry   // 改过 → 用本地快照
    return entries.firstOrNull { it.id == id } ?: fav?.entry  // 否则用源，回退快照
}
```

- `edited = true` 表示用户手动改过（或本地新建），详情页与列表显示改后版本，源更新不覆盖
- 本地新建条目 id 前缀 `local-`，只存在于收藏里，不进 `entries`
- `revertFavEntry()` 用源里的原始条目覆盖快照并清标记

### 执行通道

```
ShellExec：本机 sh（App 权限）→ Root（检测到 su 时）→ 丢给 Termux（装了才走）
           stdout/stderr 分别读（不能 redirectErrorStream，否则大输出会卡死）
IntentLauncher：activity/urlscheme/intent → startActivity
                broadcast → sendBroadcast（manifest 接收者受 Android 8 隐式广播限制）
                service → startService（App 在前台可用）
```

## 关键设计决策

| 决策 | 原因 |
|---|---|
| 无数据库 | 数据量小（几百条），JSON 文件够用；避免 Room 的编译期成本与迁移负担 |
| 无 ViewModel | 单例 Store 已经保证跨页面共享，页面销毁不丢数据 |
| core 层不依赖 android.* | 解析器可以用 kotlinc 在无 SDK 环境编译 + 单测（`tests/SmdTest.kt`） |
| 手写 JSON 编解码 | 无反射、无插件，包体小；字段增减靠 `optString/optInt` 的默认值容错 |
| 收藏存快照 | 源被删/改不影响已收藏内容；代价是编辑要区分 `edited` |
| 内容全外置 | App 不含命令，源可换；官方源托管在 `rentianyu/ai` 的 `ShellStore/sources/official.md` |
| 只有 3 个权限 | INTERNET / ACCESS_NETWORK_STATE / REQUEST_INSTALL_PACKAGES，无存储、无悬浮窗 |

## 代码规模（v0.19.1）

| 区域 | 行数 |
|---|---|
| core | ~1050 |
| data | ~1900 |
| ui | ~6300 |
| 合计 | ~9300（Kotlin，不含资源） |

最大的三个文件：`FavScreen.kt`（1193）、`MeScreen.kt`（962）、`Store.kt`（829）。
再拆的理由：`MeScreen` 里塞了设置/订阅源/备份/关于四块，接近该拆了；
`FavScreen` 的弹窗分支已经 15 个，可考虑抽成独立文件。
