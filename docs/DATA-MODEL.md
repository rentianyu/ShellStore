# 数据模型与存储

## 一、核心模型（`core/Model.kt`）

### Entry —— 一条命令/组件

所有源（在线 / 本地 / 内置）解析后都归一到这个结构。

```kotlin
data class Entry(
    val id: String,                 // 由「分类名|标题」哈希生成，见下文
    val type: EntryType,            // shell / script / urlscheme / intent / activity / service / broadcast
    val title: String,
    val desc: String = "",
    val categoryId: String = "",    // 分类名本身就是 id（不做额外映射）
    val tags: List<String> = emptyList(),
    val risk: Risk = SAFE,          // safe / caution / danger
    val need: Need = NONE,          // none / shell / root / termux
    val body: String = "",          // shell / script / urlscheme 的命令原文（已剥 adb shell 前缀）
    val variants: List<Variant> = emptyList(),   // 同一命令的多种写法
    val params: List<Param> = emptyList(),       // 可填参数，body 里用 {key} 引用
    val example: String = "",
    val doc: String = "",
    val adb: String = "",           // 保真 adb 命令，非空时优先于 body
    val intent: IntentSpec? = null, // intent 族专用
    val extra: Map<String, String> = emptyMap(), // @x.* 与未知字段，原样保留
    val verifiedAt: String = "",
    val pinned: Boolean = false,
    val origin: String = "",        // 导入时的原始写法（含 adb shell 前缀），仅详情页折叠展示
    val sourceId: String = ""       // 来自哪个源，多源聚合时回溯
)
```

`EntryType.hasBody` 是唯一的行为分叉点：`shell / script / urlscheme` 走代码块 + 复制，
其余四种走字段表 + Intent 触发。

### 枚举

| 枚举 | 取值 | 说明 |
|---|---|---|
| `EntryType` | `shell` `script` `urlscheme` `intent` `activity` `service` `broadcast` | `of()` 大小写不敏感，未知返回 null |
| `Risk` | `safe`（只读）/ `caution`（会改东西）/ `danger`（不可逆） | 决定卡片色点与执行前确认 |
| `Need` | `none` / `shell`（adb 或 Shizuku）/ `root` / `termux` | 提示运行条件，不参与执行判断 |
| `ExtraType` | `STRING` `BOOL` `INT` `LONG` `FLOAT` `NULL` `STRING_ARRAY` `INT_ARRAY` | `of()` 接受简写（`z`→BOOL、`i`→INT、`arr`→STRING_ARRAY 等） |

### IntentSpec

```kotlin
data class IntentSpec(
    val action: String = "", val data: String = "", val mime: String = "",
    val component: String = "", val packageName: String = "",
    val categories: List<String> = emptyList(),
    val extras: List<Extra> = emptyList(),   // Extra(key, type: ExtraType, value: String)
    val flags: List<String> = emptyList()
) {
    val isActionable get() = component.isNotBlank() || action.isNotBlank()
}
```

`isActionable` 决定详情页是否出现「打开/发送/启动」按钮——action 与 component 至少有一个。

### 其他

| 类型 | 字段 |
|---|---|
| `Param(key, label, default, note)` | 详情页按 key 弹输入框，默认值预填 |
| `Variant(name, body)` | 变体切换，`variantIndex = -1` 表示用主体 |
| `Category(id, name, icon, order)` | id 与 name 相同 |
| `Source(meta, entries, categories)` | 一个源解析后的完整结果 |
| `SourceMeta(name, version, author, updated, schema)` | 文件头的 `#` 与 `@version/@author/@updated` |
| `ParseWarning(lineNo, message)` | 解析告警，非致命 |

## 二、收藏模型（`data/Repo.kt`）

```kotlin
data class FavItem(
    val entryId: String,
    val folderId: String? = null,        // null = 根目录
    val tags: List<String> = emptyList(),// 引用 FavData.tags 里的 id
    val note: String = "",
    val savedAt: Long = 0L,
    val entry: Entry = Entry(...),       // ★ 完整快照，源消失也能看
    val sourceId: String = "",
    val sourceName: String = "",
    val order: Int = 0,                  // 手动排序位次，越大越靠前；0 = 未排序过
    val edited: Boolean = false          // ★ 用户改过 → 详情页用快照而非源
) {
    var alive: Boolean = true            // Store 加载后回填：源里还能不能找到（运行时字段，不落盘）
}
```

`FavData` 提供文件夹树操作：`childrenOf / depthOf / pathOf / subtreeIds / countOf / itemsIn`。

排序规则统一为 `order 降序 → savedAt 降序`，新收藏取同级 `max(order)+1`（即排最前）。

### 两个 id 前缀

| 前缀 | 含义 |
|---|---|
| `local-XXXXXXXX` | 本地新建条目，只存在于 `favs`，不参与 `entries` 聚合 |
| 其他（8 位 hex） | 源条目，id = `hash("分类名|标题")` 的低 32 位十六进制 |

因为 id 不含 sourceId，**不同源里的同分类同标题条目会合并成一条**——这是多源聚合去重的设计。
代价：粘贴导入时若与已有条目 id 相同，`addFavs` 会跳过（计入 skipped）。

## 三、设置（`AppSettings`）

```kotlin
theme          ThemeMode(LIGHT/DARK/SYSTEM)
fontScale      Float，通过 LocalDensity 的 fontScale 乘子实现「只放大字号不动布局」
uiFont/codeFont FontChoice(系统 / 内置 / 自定义路径) + customFontPath
copyPrefix     CopyPrefix(NONE / ADB_SHELL / SU_C)
confirmDanger  Boolean，危险条目执行前二次确认
foldPreview    Boolean，列表卡片是否折叠命令预览
sourceMirror   String，上次拉取成功的线路名（空 = 直连）
onboarded      Boolean，是否走过欢迎页
```

## 四、SMD 格式

完整语法见 **[ShellStore/SKILL.md](https://github.com/rentianyu/ai/blob/main/ShellStore/SKILL.md)**（面向写源的人）。这里只记解析器行为。

### 解析输出

`SmdParser.parse(text, sourceId, sourceName)` → `ParseResult(source, warnings)`。
**不抛异常**：遇到不认识的 `@` 行、未闭合代码块、非法 `@param`，记一条 `ParseWarning` 后跳过。

### 结构识别

| 写法 | 含义 |
|---|---|
| `# 名称` | 文件标题（只取第一个） |
| `## 分类` | 分类；也接受 `●`/`•` 前缀的列表写法 |
| `### 标题` | 条目；同样接受 `●`/`•`/`-` |
| ` ```` ```sh ```` 代码块 | 内容全部进 `body`，**不做任何解析**（`@`、`#`、反引号原样保留） |
| 行首缩进的非空行 | 续行：接到上一条 `@param` 的说明或 `@example` 的多行值 |
| 其他正文文本 | 归入当前条目的 `desc` |

### 字段

| 字段 | 落到 |
|---|---|
| `@type` | `EntryType`，缺失时按内容推断（有代码块→shell，有 `@action`→intent，`scheme://`→urlscheme） |
| `@risk` `@need` | 枚举 |
| `@tag` | 空格或逗号分隔 |
| `@desc` | `desc` |
| `@param key=标签\|默认值` | `Param` |
| `@verified` | `verifiedAt` |
| `@adb` | 保真 adb 命令 |
| `@example` `@doc` | 多行（配合续行） |
| `@action` `@component` `@data` `@mime` `@package` `@category` `@flag` `@extra` | `IntentSpec` 各字段 |
| `@x.*` 及其他未知 `@` | 原样进 `Entry.extra`，不丢字段 |

### 命令体清洗

- 自动剥掉开头的 `adb shell ` 前缀，原始写法存进 `origin`
- 含 `su -c` 的自动把 `need` 标成 `root`
- 代码块未闭合 → 丢弃该段 + 告警

## 五、本地存储

全部在 App 私有目录（`context.filesDir`），**卸载即删**。五个 JSON + 一份源正文缓存。

| 文件 | 内容 | 写入方式 |
|---|---|---|
| `settings.json` | `AppSettings` 全量 | 直接覆写 |
| `sources.json` | `SourceConfig` 列表 | 直接覆写 |
| `favorites.json` | `FavData`（folders / tags / items） | 写 `.tmp` 后改名（原子替换） |
| `recent.json` | `RecentItem(entryId, at)` 列表，上限 60 | 直接覆写 |
| `search.json` | 搜索历史字符串数组，上限 10 | 直接覆写 |
| `sources/<源 id>.md` | 源正文缓存（`SourceCache`） | 拉取成功时写 |

### 序列化约定

- 手写 `JSONObject`，无注解、无反射
- 读取一律 `optString/optInt/optBoolean/optLong` 带默认值 → **旧文件缺字段不会崩**
- 未知字段丢弃（不需要保留，因为源正文才是真相，收藏里存的是完整快照）
- `Entry` 的 `entryToJson` / `entryFromJson` 覆盖全部字段（含 variants / params / intent / extras / extra），
  保证「编辑 → 落盘 → 重读」不丢内容

### 备份 JSON（导出/导入）

`Store.exportBackupJson()` 产出的结构与 `favorites.json` 同构，外加 `app` 与 `exportedAt`。
导入时 `importBackup(json, overwrite)`：

- `overwrite = false` → 合并（收藏按 entryId 去重，文件夹按 id 追加）
- `overwrite = true` → 先清空收藏再写入
- 返回 `null` 表示成功，否则返回错误文案

## 六、兼容性策略

| 场景 | 处理 |
|---|---|
| 源新增字段 | 解析器忽略未知 `@`（但会存进 `extra`），不报错 |
| 收藏文件缺新字段 | `optXxx` 默认值兜底（如 `edited` 缺失 = false） |
| 条目 id 变化（改标题） | 收藏项仍能靠快照显示，但 `alive = false`，卡片标「源已失效」 |
| 源被删除 | 收藏内容照常可看可改，只是不再跟随源 |
| 应用降级安装 | 不做 versionCode 校验，JSON 字段只增不减，可降级 |
