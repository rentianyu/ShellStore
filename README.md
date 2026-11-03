# Shell 商店

Android 上的**命令与组件速查台**。把 shell 命令、脚本、URL Scheme、Intent、Activity、Service、广播放在
同一个货架上，按分类浏览、搜索、一键复制，需要时直接执行或发送。

内容不是写死在 App 里的，而是来自可插拔的 **SMD 数据源**：可以自己写、可以分享、可以在线更新。
App 本身不含命令，首次启动会引导你启用官方源（129 条 / 10 个分类）。

---

## 安装

| 下载 | 说明 |
|---|---|
| [app-release.apk](https://p.xbta.cc/https://github.com/rentianyu/ShellStore/releases/latest/download/app-release.apk) | 永远指向最新正式版，App 内也会自动检查更新 |

要求 Android 8.0（API 26）以上。首次安装需允许「安装未知应用」。

国内直连 GitHub 慢时，上面的 `p.xbta.cc` 是自建反代，直接点就行。

## 首次使用

1. 打开 App，欢迎页点「启用官方源」——会拉取 129 条命令，拉不动会自动换线路（共 17 条备用）
2. 底部三个 Tab：
   - **商店**：分类宫格 + 全部命令 + 搜索
   - **收藏**：多级文件夹、标签、批量操作
   - **我的**：订阅源、最近使用、备份恢复、检查更新

## 日常用法

### 找命令 → 填参数 → 复制

点分类进列表，或点右上角 `⌕` 搜索（标题、命令体、说明、标签、分类名都会匹配，命中词高亮）。

详情页的命令里如果有 `{占位符}`，上方会出现输入框，填完命令实时重算。多个写法（变体）可以用
左右箭头切换。底部「复制」按你设置的前缀处理（无 / `adb shell` / `su -c`）。

搜索框留空时会显示**最近搜索**（点开过结果的词才会记进去），「我的 › 最近使用」里还有一份
按使用时间排的操作记录。

### 执行与发送

| 条目类型 | 底部按钮 | 说明 |
|---|---|---|
| `shell` / `script` | 执行 | 用 App 自身权限跑 `sh`，**不是 root**。要 root 的命令请自行通过 adb / Shizuku 执行 |
| `intent` / `activity` / `service` / `broadcast` | 打开 / 发送 / 启动 | 按条目字段构造 Intent 直接唤起，失败会提示原因 |
| `urlscheme` | 打开 | 交给系统处理对应 URI |
| 其余 | 复制 | — |

Intent 类条目在详情页会显示字段表，以及等价的 `adb shell am ...` 命令，方便你在电脑上复现。

### 收藏整理

- 详情页点 `☆` 收藏；收藏页可建多级文件夹、给条目加标签和备注
- 收藏存的是**快照**：源被删了，收藏里的内容仍然看得到
- 长按条目可拖动排序（松手即保存，新收藏默认排最前）
- 长按或点「全选」进多选模式，可批量移动、批量打标签、批量删除
- 「标签」页按标签聚合，点进去看该标签下的所有条目

### 搜索与筛选

搜索态下可叠加类型筛选（全部 / shell / intent …）。分类页内也有独立搜索框，只在该分类内过滤。

## 订阅源

### 官方源

```
https://raw.githubusercontent.com/rentianyu/ShellStore/main/sources/official.md
```

文件在仓库的 [sources/](sources/) 目录，129 条命令 / 10 个分类。App 启动时会检查它有没有更新
（比对条目数量），发现变化会先问你「新增几条、删除几条」，确认后才替换。

### 加自己的源

「我的 › 订阅源 › 添加」支持两种：

1. **在线源**：填任意可访问的 `.md` 地址（GitHub raw、Gist、自建静态站都行）
2. **本地文件**：从文件管理器选一个 `.md` 导入，内容会复制进 App

多个源可以同时启用，条目会合并显示。源拉不动时自动走反代，成功后会记住这条线路。

### 写自己的源

SMD 是 Markdown 的超集，只多约定了几行 `@字段`。完整语法见 **[sources/README.md](sources/README.md)**，
一分钟能上手：

````markdown
## 应用管理

### 列出第三方应用
@risk safe
@need shell
@tag 包名
@param filter=范围|-3

```sh
pm list packages {filter}
```
````

导出反向也支持：「我的 › 备份与恢复 › 导出为订阅源」会把你的收藏按文件夹导成一份 SMD 文件，
别人导入就能用；详情页右上角菜单还能把单条复制成 SMD 片段。

## 常见问题

**Q：官方源一直拉不下来？**
A：App 内置 17 条线路自动降级，每次 10 秒。全部失败通常是网络整体出不去（公司网络 / 代理拦截
GitHub 系域名），可以换成「本地文件」方式：电脑上下载 `official.md` 再传到手机导入。

**Q：点「执行」没反应 / 提示权限不足？**
A：执行用的是 App 自己的权限，普通应用能做的事（读 `getprop`、查 `dumpsys` 的一部分）可以做，
改系统设置、访问 `/data` 这类需要 root 的做不了。这不是 bug。

**Q：命令里带了 `adb shell` 前缀怎么办？**
A：解析时会自动剥掉，你原样贴进来也不会出错。

**Q：收藏会不会丢？**
A：收藏存在 App 私有目录，随 App 卸载一起删除。建议「我的 › 备份与恢复」定期导出一份 JSON
（含收藏、标签、设置），换机时导入即可，支持合并或覆盖。

**Q：为什么应用内更新下载完要我手动点安装？**
A：Android 的安全限制，App 不能静默安装，只能把安装包交给系统安装器。

**Q：订阅源更新后我的收藏会变吗？**
A：不会。收藏是入藏时的快照，源更新不影响已收藏的内容（但条目本身在列表里会跟着源变）。

**Q：需要哪些权限？**
A：只有三个：联网、读取网络状态（判断在线源能否更新）、安装未知应用（应用内更新）。
不申请存储、定位、悬浮窗等任何权限。

## 开发

架构、数据模型、界面规范、功能实现位置、构建发版流程都在 **[docs/](docs/)**：

| 文档 | 内容 |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 分层、状态管理、导航、数据流 |
| [docs/DATA-MODEL.md](docs/DATA-MODEL.md) | 模型字段、SMD 映射、本地存储 |
| [docs/UI-DESIGN.md](docs/UI-DESIGN.md) | 配色、尺寸 token、组件、交互约定 |
| [docs/FEATURES.md](docs/FEATURES.md) | 每个功能的实现位置 |
| [docs/BUILD-AND-RELEASE.md](docs/BUILD-AND-RELEASE.md) | 依赖约束、CI、签名、发版 SOP |

## 构建

```bash
./gradlew assembleRelease     # 需要 keystore.properties（签名配置）
./gradlew assembleDebug       # 不需要签名，直接出 debug 包
```

CI 由 GitHub Actions 负责：

- 推 `main` → 只做编译验证（aapt2 体检 + 30 天 artifact），不出 Release
- 推 `v*` tag → 构建 release → 生成正式 Release，这是唯一的分发出口

技术栈：Kotlin 2.4 · Jetpack Compose · AGP 8.13 / Gradle 8.14 · JDK 17 · minSdk 26 / targetSdk 36。
无数据库、无序列化框架、无图片库——SMD 解析器和 UI 都是手写的。

版本变更见 [CHANGELOG.md](CHANGELOG.md)。

## 许可

MIT
