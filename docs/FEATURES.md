# 功能与实现对照

按用户可见功能分组，每条标注实现位置与关键逻辑。行号会随迭代漂移，以方法名为准。

## 1. 内容浏览

| 功能 | 实现 | 关键逻辑 |
|---|---|---|
| 分类宫格 | `HomeScreen` 商店 Tab | 四个一行的大卡，显示分类名 + 条数；点击进 `CatList` |
| 全部命令 | `HomeScreen` | `Store.entries` 直接铺开，支持折叠预览 |
| 分类内列表 | `HomeScreen` 的 `CatListScreen` | 独立搜索框只在该分类内过滤 |
| 最近使用 | `RecentScreen` | `Store.recordRecent(id)` 在「复制/执行/打开」时记录，上限 60，按时间去重 |
| 空态 | `Widgets.EmptyState` | 没启用源时提示去启用 |

条目合并：多源并发解析后按 `id` 去重（同分类同标题视为同一条），
分类按 `Category.order` 排序后合并。

## 2. 搜索

| 点 | 实现 |
|---|---|
| 匹配字段 | 标题、命令体（body）、说明（desc）、标签、分类名，全部大小写不敏感 |
| 高亮 | `Widgets.highlightSpan()` 用 AnnotatedString 给命中片段加主色 + 淡底 |
| 类型/风险筛选 | 搜索态下叠加 `FilterChip`，`Store.search(query, types, risks)` |
| 搜索历史 | `Store.addSearchHistory()`，**只有点开某条结果才记**（避免逐字输入污染），上限 10 |
| 本地条目 | 自建条目也进搜索（`Store.search` 末尾并入 `local-` 前缀的收藏快照），但不进分类浏览 |

## 3. 详情与执行

`DetailScreen` 是信息密度最高的一页：

| 区块 | 说明 |
|---|---|
| 类型 / 风险 / 运行条件徽章 | `typeLabel` + `RiskBadge` + `Need.label` |
| 变体切换 | `entry.variants` 非空时出现 `FilterChip` 行，`-1` 表示用主体 |
| 命令内容块 | 等宽显示 `CommandRenderer.primaryText(entry, values, variantIndex)`；**点击进编辑** |
| 参数输入 | `entry.params` 逐个生成输入框，改动实时重算命令 |
| Intent 字段表 | `IntentFieldTable`，同时展示等价 `adb shell am ...` |
| 示例 / 说明 / 原始写法 | 有值才显示 |
| 来源 | 源名 + 条目 id 前 8 位 |
| 底部按钮 | `[执行\|打开\|编辑]` `[复制]` `[☆]`，前两个等宽 |

### 三个执行通道（`ShellExec`）

```
本机 sh  →  Root（检测到 su 时）  →  Termux（安装了才走）
```

- 本机通道能跑只读命令（`getprop` / `dumpsys` / `wm size` / `df` / `ps`）
- 需要系统权限的（`settings put` / `pm uninstall` / `svc`）才走 Root
- **stdout/stderr 必须分两个线程读**，`redirectErrorStream` 会让大输出卡死
- 超时、退出码、三个流的输出都回显在 `ExecResultDialog`

### Intent 触发（`IntentLauncher`）

```
activity / urlscheme / intent → startActivity
broadcast                     → sendBroadcast（manifest 接收者受 Android 8 隐式广播限制）
service                       → startService（App 在前台可用，后台会被拒）
```

失败会翻译成人话（`ActivityNotFoundException` → 「系统里没有能处理这个 Intent 的应用」等）。

### 复制前缀

`settings.copyPrefix`：无 / `adb shell` / `su -c`。复制时套上，Toast 提示「已复制（含 adb shell 前缀）」。

## 4. 收藏

`FavScreen`（1193 行）承载全部收藏逻辑。

| 功能 | 实现 |
|---|---|
| 多级文件夹 | `Folder(parentId)`，`FavData.pathOf/depthOf/subtreeIds` 做树操作 |
| 标签 | `Tag(id, name, colorIdx)`，条目存 `tags: List<String>`（存 id）；标签页按标签聚合 |
| 备注 | `FavItem.note`，卡片上单行显示 |
| 快照 | `FavItem.entry` 存完整 Entry —— 源被删也能看 |
| 拖拽排序 | `detectDragGesturesAfterLongPress` + `order` 位次，松手即 `Store.reorderFavs` |
| 多选批量 | 顶栏 `☑` 进入，批量移动 / 打标签 / 取消收藏；「全选」只作用于当前视图可见项 |
| 收藏失效标记 | `alive` 由 Store 回填，`!alive && !isAlive() && !isLocal()` 时标「源已失效」 |
| 悬浮新建 | 右下 52dp FAB，菜单四项（新建条目 / 新建文件夹 / 添加订阅源 / 粘贴导入） |

删除保护：本地条目（`local-` 前缀）删除前二次确认，「不属于任何订阅源，删掉就再也找不回来」；
批量删除时若含本地条目也先确认。

## 5. 条目编辑

`EntryEditor.kt`（475 行）是全字段表单。

| 分组 | 字段 |
|---|---|
| 类型 | 7 种 chip，切换时内容区跟着变（body 类 vs Intent 类） |
| 基本信息 | 标题（必填）、描述 |
| 内容 | body 类：多行等宽命令框；Intent 类：Action / Component / Package / Data / MIME / Category / Extras |
| 参数 | 变量名 / 显示名 / 默认值 / 说明，可增删 |
| 属性 | 风险 3 档、运行条件 4 档、标签 |
| 更多 | 置顶、保真命令、示例、文档、变体（名称 + 完整命令，可增删） |

三条保存规则：

1. 标题必填；body 类命令不能为空；Intent 类至少要 action 或 component
2. 未收藏的源条目编辑后**自动收藏**（页面顶部有提示）
3. 变体必须名称和命令都填才保存

「放弃改动，恢复源里的原始内容」：仅当条目可回溯到源且已被改过时出现，
调 `Store.revertFavEntry()` 用源里的条目覆盖快照并清 `edited`。

## 6. 订阅源

| 功能 | 实现 |
|---|---|
| 官方源 | `Store.enableOfficial()`，URL 常量 `OFFICIAL_URL`，拉 `f.xbta.cc/ai/ShellStore/sources/official.md` |
| 在线源 | `Store.addRemote(name, url)` |
| 本地源 | `Store.importLocal(name, text, fileName)`，正文复制进 `SourceCache` |
| 多源合并 | `reloadAll()` 逐个解析后按 id 去重合并 |
| 线路降级 | `Mirrors`：17 个节点，单节点 10s 超时，上次成功的提到最前（`settings.sourceMirror`） |
| 更新确认 | `checkSourceUpdates()` 只比对条数算差异，用户确认后才 `applySourceUpdates()` |
| 帮助页 | `SourceHelpScreen` + `SourceHelpText`（给人看的最小模板 + 给 AI 的完整规范，均可一键复制） |
| 小贝塔订阅源 | `SeriesSources`：拉 `f.xbta.cc/ai/ShellStore/sources/index.md`（本身是 SMD，代码块正文为 http 地址的条目即一个可选源），用 `SmdParser` 解析；`Store.setXbtaSeries()` 管总开关，`Store.toggleSeriesItem()` 管单源，`Store.refreshSeries()` 重拉清单并刷新该系列已启用的源 |
| 系列源门控 | `SourceConfig.series="xbta"` + `AppSettings.xbtaSeries`；总开关关闭时保留配置但不参与 `reloadAll` |
| 系列源认源 | `Store.seriesConfigOf()` **按 url 匹配**（不是按 series 标记），用户手动加过的同一个源会被就地归入系列，不会重复添加 |

SMD 格式本身见 [ShellStore/SKILL.md](https://github.com/rentianyu/ai/blob/main/ShellStore/SKILL.md)。

## 7. 导入导出

### 导出（`Export.kt`）

```
导出为订阅源 → 选文件夹（可多选，自动带子文件夹，可含「未分类」）
             → 系统文件选择器（选目录 + 文件名，默认名带范围与日期）
             → 写 SMD
```

- 单个文件夹：`shellstore-泰安项目-2026.11.03.md`
- 全部：`shellstore-全部收藏-2026.11.03.md`
- 多选：`shellstore-收藏3个文件夹-2026.11.03.md`
- 收藏夹 `⋯` 菜单里还有「导出此文件夹」，一步到位

导出的 SMD 里源名用文件夹名，换机器导入后分组对得上。

### JSON 备份

`Store.exportBackupJson()` / `importBackup(json, overwrite)`，合并或覆盖两种模式。
入口在「我的 › 备份与恢复」。

### 粘贴智能导入（`PasteImport.kt`）

读剪贴板，按以下顺序判断：

| 内容特征 | 识别为 | 可选动作 |
|---|---|---|
| `{` 开头且含 `items`+`folders` | 备份 JSON | 合并导入 / 覆盖导入 |
| 有 `### 标题` 且有 `## 分类`（或含 `@type`） | SMD 源 | 存为订阅源 / 加入收藏（去重）/ 看现有源 |
| 其他（≤ 20000 字符） | 单条命令 | 打开编辑器 / 直接建一条 |

单条识别：`am`/`cmd` 开头 → Intent（抽 `-a -n -d -t`）；含 `scheme://` → URL Scheme；其余 shell。
含 `rm -rf`、`mkfs`、`dd if=` 自动标「危险」。

> 注意：单条 SMD 片段（只有 `###` 没有 `##`）靠 `@type` 特征识别——不带 `@type` 的片段会被当成普通命令。

## 8. 设置（`MeScreen.SettingsScreen`）

| 项 | 说明 |
|---|---|
| 主题 | 浅色 / 深色 / 跟随系统，默认浅色 |
| 字号缩放 | 0.85× ~ 1.30×，通过 `LocalDensity.fontScale` 乘子实现 |
| 界面字体 / 代码字体 | 系统 / 内置 / 自定义 .ttf（文件选择器导入后复制到私有目录） |
| 复制前缀 | 无 / `adb shell` / `su -c` |
| 危险条目确认 | 执行 `danger` 条目时二次确认 |
| 折叠预览 | 列表卡片是否只显示标题行 |

## 9. 应用更新（`Updater` / `UpdateCenter`）

```
启动 2s 后静默检查 → releases/latest 拿 tag → 按数字段与 APP_VERSION 比较
→ 有新版本则下载 app-release.apk 到缓存 → FileProvider + ACTION_VIEW 交系统安装
→ 失败静默（不打扰），用户可手动「我的 › 检查更新」
```

`UpdateCenter` 是状态机：`Idle / Checking / Downloading(progress) / Ready(apk) / Error(msg)`，
UI 侧 `UpdateDialogs` 根据状态出弹窗。

## 10. 首次引导

`Welcome.kt`：未 onboarded 且没有任何源时全屏引导，
主按钮「启用官方源」（拉取 + 线路降级），也提供「先跳过，自己加源」。
老用户（已有源）自动跳过，不打扰。

## 版本演进（摘要）

| 版本 | 主题 |
|---|---|
| 0.1 ~ 0.9 | 基础：解析器、浏览、搜索、详情、执行、收藏、订阅源 |
| 0.10 ~ 0.13 | 收藏文件夹/标签/多选/拖拽、参数与变体、备份恢复、应用内更新 |
| 0.14.0 | 换 FlClash 风格配色与底栏 dock |
| 0.15.0 | 收藏里新建条目、全字段编辑、参数可视化编辑 |
| 0.16.x | 本地条目进搜索、导出可选文件夹、本地标记 |
| 0.17.x | 右下 FAB、粘贴智能导入（SMD/JSON/单条） |
| 0.18.0 | 所有列表加编辑入口 |
| 0.19.x | 详情页主按钮改编辑、编辑页补齐置顶/变体/示例/文档、按钮等宽 |

完整记录见 [../CHANGELOG.md](../CHANGELOG.md)。
