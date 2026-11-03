# 界面与设计系统

## 设计语言来源

配色与圆角规则借鉴 [FlClash](https://github.com/chen08209/FlClash)（GPL-3.0）的**设计思路**，
代码全部独立实现，不复制其源码（尤其其 GLSL 着色器）。

具体借鉴了三条原则：

1. **primary 直接取 seed 色**（`#007AFF`），不用 Material 默认的 tone 40 —— 主色更"正"、不发灰
2. **surface 系列保留 Material 的 tone，但把 chroma 截到 3**（浅色下 surface/surfaceBright 截到 1.5）——
   页面是有极轻色调的灰，不是死灰也不是纯白
3. **分层靠填充差异，不靠边框** —— 所以全 App 的卡片都没有 1dp 描边

色值由 `.ci/gen_flclash_theme.py` 生成，换 seed 重跑那个脚本即可。

## 配色

### 浅色

| 角色 | 色值 | 用途 |
|---|---|---|
| primary | `#007AFF` | 主按钮、选中态、可点文字 |
| background / surfaceContainer | `#EFEDF0` | 页面底色 |
| surface / surfaceBright | `#FCF8F9` | 卡片、底栏、弹层 |
| surfaceContainerHighest | `#E4E2E4` | 底栏选中托盘、输入框底 |
| onBackground / onSurface | `#1B1B1E` | 正文 |
| onSurfaceVariant | `#474649` | 次要文字 |
| outline | `#777779` | 三级文字、分隔线 |
| outlineVariant | `#C7C6CA` | 更弱的分隔线 |

### 深色

| 角色 | 色值 | 备注 |
|---|---|---|
| primary | `#2F83FF` | 深色下提亮一档保证对比度 |
| background | `#131315` | tone 6 |
| surface | `#252628` | tone 15 ← **必须与 background 分开**，同色会让卡片完全看不出边界 |
| surfaceBright | `#39393B` | tone 21 |
| surfaceContainerHighest | `#404042` | tone 27 |
| onSurface | `#E4E2E4` | 正文 |

深色 tone 步进（6 / 15 / 21 / 27）比 Material 默认更大，层级才看得出来。

### 语义色（`Tones.kt`）

与主题无关的固定色，用于风险等级与代码块：

```
safe       #12854A    safeBg      #E8F7EE
caution    #A9660A    cautionBg   #FDF3E3
danger     #C3271F    dangerBg    #FDECEC
neutralFg  #5E5C60    neutralBg   #E9E6EA
codeFg     #1B1B1E    codeBg      #E9E6EA
```

> ⚠️ 已知问题：这组色是固定浅色值，深色模式下风险徽章偏亮。改进方式是改成按主题取色，
> 但会牵动所有调用点，一直没做。

## 尺寸 token（`Tokens.kt`）

**规则：UI 里不写字面量，所有尺寸从 token 取。**

### 间距（4 的倍数）

| token | 值 | 用途 |
|---|---|---|
| `Space.xs` | 4dp | 图标与文字之间 |
| `Space.sm` | 8dp | 卡片内元素间距 |
| `Space.md` | 12dp | 卡片内边距 |
| `Space.lg` | 16dp | 区块之间 |
| `Space.xl` | 20dp | 大区块 |
| `Space.page` | **24dp** | 页面左右留白（从 14 → 24，内容区明显变窄） |

### 圆角（三档）

| token | 值 | 用途 |
|---|---|---|
| `Radius.sm` | 8dp | 小控件、输入框、徽章底 |
| `Radius.md` | 16dp | 卡片 |
| `Radius.lg` | 24dp | 弹层、底栏 dock |

### 字号（六档 + 两档代码）

| token | 值 | 用途 |
|---|---|---|
| `caption` | 11.5sp | 徽章、脚注 |
| `small` | 13sp | 次要说明、条数 |
| `body` | 15sp | 正文、卡片标题 |
| `title` | 17sp | 区块标题 |
| `headline` | 20sp | 顶栏标题 |
| `code` | 13sp | 卡片内命令预览 |
| `codeLarge` | 14sp | 详情页命令正文 |

### 图标字号

| token | 值 | 用途 |
|---|---|---|
| `tab` | 30sp | 底部 tab |
| `top` | 24sp | 顶栏操作按钮 |
| `nav` | 32sp | 顶栏返回箭头 |
| `action` | 19sp | 列表右侧 `⋯` |
| `card` | 18sp | 分类卡片图标 |
| `inline` | 17sp | 搜索框内 |
| `chevron` | 18sp | 行尾 `›` |
| `check` | 13sp | 勾选框对勾 |

图标全部是**字形字符**（`▦ ☆ ◍ ⌕ ☰ ☑ + ⋯ ›`），不是图片资源 —— 所以只有字号没有尺寸。

### 固定尺寸

```
topBar 56dp   tabBar 64dp   button 42dp   buttonSmall 36dp
iconButton 48dp（无障碍热区）  badgeIcon 24dp  listIcon 30dp  checkbox 22dp
```

## 布局规则

### 页面骨架

```
Column(fillMaxSize) {
    TopBar(56dp)      标题 20sp，返回 32sp，右侧操作 24sp
    [筛选行 / 搜索框]
    LazyColumn(weight 1f, contentPadding = 左右 24dp)
}
```

页面内容区左右留白统一 24dp，靠 `Space.page`。**底部有 FAB 的页面**要把列表 `bottom` padding
加到 88dp，否则最后一条会被盖住。

### 底栏：浮动胶囊 dock

不是贴边整条，而是浮起来的胶囊：

```
左右外边距 18dp，上下 8dp，高 64dp，圆角 24dp，阴影 10dp，背景 surface
选中指示器：58×32dp 中性色托盘（surfaceContainerHighest），不是主色药丸
未选中图标：onSurfaceVariant 75% 透明度；选中：primary
```

选中的颜色只给图标和文字，**托盘保持中性色**——这是 FlClash 的做法，避免整条底栏都在发蓝光。

### 列表卡片

- 背景 `surface`，圆角 16dp，内边距 12dp
- **无边框**，与页面底色（`surfaceContainer`）靠填充差异区分
- 结构：`[类型徽章 24dp] [标题 15sp 加粗] [本地徽章] [风险点] [☆ 48dp] [⋯ 48dp]`
- 折叠预览（`settings.foldPreview`）时只显示一行，展开时多一行等宽命令预览
- 多选态：背景主色 7% 透明 + 1.5dp 主色描边 + 左侧勾选圈

### 悬浮新建按钮（收藏页）

52dp 圆形，primary 底，10dp 阴影，距右边 18dp、距底 18dp（与 dock 的外边距对齐）。
进入搜索或多选时自动隐藏。

## 组件清单（`Widgets.kt`）

| 组件 | 说明 |
|---|---|
| `TopBar(title, onBack, actions)` | 56dp 顶栏，标题单行省略 |
| `IconBtn(glyph, onClick)` | 48dp 热区的字形按钮 |
| `SearchField(...)` | 圆角搜索框，自带聚焦与取消 |
| `FilterChip(label, selected, onClick)` | 筛选胶囊，选中填主色 |
| `CodeBlock(text, maxLines, fontSize, background)` | 等宽代码块，默认 `Tones.codeBg` |
| `EntryRow(...)` | 列表卡片（标题 / 徽章 / 预览 / ☆ / ⋯） |
| `TypeBadge(t)` | 类型方块徽章（带字形图标） |
| `RiskBadge(risk)` / `Badge(text, fg, bg)` / `MutedBadge(text)` | 三类徽章 |
| `GroupCard { }` / `GroupTitle(text)` / `Divider()` | 设置类页面的分组容器 |
| `SettingRow(icon, title, subtitle, onClick)` | 设置项：左图标 + 标题 + 副标题 + `›` |
| `Toggle(on, onChange)` / `CheckCircle(checked)` | 开关与勾选圈 |
| `EmptyState(title, hint)` | 空态 |
| `ViewOptionsDialog(...)` | 列表显示设置弹层 |
| `highlightSpan(text, keyword)` | 搜索命中高亮（AnnotatedString） |

## 交互约定

| 动作 | 行为 |
|---|---|
| 点列表卡片 | 进详情页 |
| 点卡片 `⋯` | **直接进编辑页**（首页/分类/搜索/最近/收藏文件夹页都一致） |
| 长按收藏卡片 | 进入拖拽排序（松手即保存） |
| 点 `☆` | 收藏 / 取消收藏 |
| 详情页命令块 | 点击进编辑（块内提示「点击编辑」） |
| 详情页底部 | `[执行\|打开\|编辑]` `[复制]` `[☆]`，前两个等宽各占一半 |
| 返回键 | 关弹窗 → 退搜索/多选 → pop 页面 → 根页「再按一次退出」 |
| 收藏页 `+` | 右下 FAB：新建条目 / 新建文件夹 / 添加订阅源 / 粘贴导入 |

## 字体与字号缩放

```
settings.uiFont   → 界面字体（系统 / 内置 / 自定义 .ttf）
settings.codeFont → 代码字体（LocalCodeFont 注入，CodeBlock 与命令正文用）
settings.fontScale → 通过 CompositionLocalProvider(LocalDensity provides Density(density, fontScale * f))
```

关键：**只改 fontScale、不改 density**，所以字号放大时 dp 尺寸（间距、按钮高度）不变，
不会把布局撑爆。全局一处生效，不需要逐处改 fontSize。

## 深浅色

- `ThemeMode.LIGHT / DARK / SYSTEM`，默认 **LIGHT**（凯哥偏好白天模式）
- 深浅色只切 `AppTheme(darkTheme = ...)`，业务代码不判断深浅
- 需要主题色的地方一律取 `MaterialTheme.colorScheme.xxx`，只有语义色（风险、代码块）用 `Tones`
