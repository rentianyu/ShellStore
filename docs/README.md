# Shell 商店 · 开发文档

面向要改代码、改数据、改界面或发版的人。用户向说明在 [../README.md](../README.md)，
版本记录在 [../CHANGELOG.md](../CHANGELOG.md)。

## 文档地图

| 文档 | 内容 | 什么时候看 |
|---|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 分层、状态管理、导航、数据流、关键设计决策 | 第一次读代码 / 想加新页面 |
| [DATA-MODEL.md](DATA-MODEL.md) | `Entry` 等模型、SMD 字段映射、本地存储文件、兼容性 | 改数据结构 / 改解析器 / 排查数据丢失 |
| [UI-DESIGN.md](UI-DESIGN.md) | 配色、尺寸 token、布局规则、组件清单、交互约定 | 改界面 / 加组件 / 调样式 |
| [FEATURES.md](FEATURES.md) | 每个功能的实现位置与关键逻辑 | 想改某个功能但不知道在哪 |
| [BUILD-AND-RELEASE.md](BUILD-AND-RELEASE.md) | 依赖约束、构建、CI、签名、发版 SOP、验收 | 出包 / 发版 / CI 报错 |
| [../sources/README.md](../sources/README.md) | SMD 数据格式（面向写源的人） | 写订阅源 |

## 项目一句话

Android 上的命令速查台：shell / 脚本 / URL Scheme / Intent 四类内容统一成 `Entry`，
从可插拔的 SMD 订阅源读入，支持浏览、搜索、复制、执行、收藏、编辑、导入导出。

- Kotlin + Jetpack Compose，单 Activity，无数据库、无网络框架、无序列化框架
- 约 9300 行 Kotlin，三个 Tab（商店 / 收藏 / 我的）
- 权限只有三个：INTERNET、ACCESS_NETWORK_STATE、REQUEST_INSTALL_PACKAGES

## 五分钟上手代码

```
1. 先看 ui/App.kt            —— Root() 里的 Page 栈 + TabBar，全 App 的骨架
2. 再看 data/Store.kt         —— 所有 StateFlow 与业务动作都在这，12 个 Flow
3. 然后 core/Model.kt         —— Entry 是核心模型，读懂字段就懂了整个数据形状
4. 想改界面 → ui/Widgets.kt   —— 公共组件都在这，改一处全 App 生效
```

## 常见改动怎么下手

| 要做的事 | 改哪里 |
|---|---|
| 加一个新页面 | `App.kt` 的 `Page` 加分支 → `when` 里挂 Composable → 调用处 `onOpen(Page.Xxx)` |
| 加一种条目类型 | `EntryType` 枚举 → `hasBody` → `IntentLauncher` 的可触发集合 → 详情页分叉 |
| 加一个 SMD 字段 | `SmdParser` 的 `applyMeta` when 分支 → `Model.kt` 加字段 → `entryToJson/entryFromJson` → `SmdWriter` |
| 改配色 | `theme/Palette.kt`（或改 seed 后重跑 `.ci/gen_flclash_theme.py`） |
| 改尺寸/字号 | `theme/Tokens.kt`，**不要**在界面里写字面量 dp/sp |
| 改底栏样式 | `App.kt` 的 `TabBar()` |
| 加设置项 | `AppSettings`（Repo.kt）→ `settings.json` 编解码 → `SettingsScreen` 加行 |
| 改收藏逻辑 | `Store.kt` 的收藏段 + `FavScreen.kt` |
| 改解析规则 | `core/SmdParser.kt`，改完跑 `tests/SmdTest.kt` |
| 出正式版 | 按 [BUILD-AND-RELEASE.md](BUILD-AND-RELEASE.md) 的 SOP |

## 两条容易踩的线

1. **`core/` 不许 import `android.*`**
   它是唯一能在无 SDK 环境用 kotlinc 单独编译 + 单测的部分。一旦引入 Android 依赖，
   解析器就没法脱离设备验证了。

2. **尺寸/字号只从 `theme/Tokens.kt` 取**
   界面里散落的 `16.dp`、`13.sp` 会让整体调整变成体力活。要新档位就加 token，别就地写字面量。

## 已知技术债

| 项 | 说明 |
|---|---|
| `Tones.kt` 是固定浅色 | 深色模式下风险徽章偏亮，要改成按主题取色需动所有调用点 |
| `MeScreen.kt` 962 行 | 设置 / 订阅源 / 备份 / 关于四块挤在一个文件，该拆 |
| `FavScreen.kt` 1193 行 | 15 个弹窗分支，可抽成独立文件 |
| UI 无自动化测试 | 只有 CI 编译验证 + 真机手测；`core` 层有单测 |
| 条目 id 不含 sourceId | 不同源里同分类同标题的条目会合并，改标题会导致收藏「源已失效」 |
