# Shell 商店

Android 上的**命令与组件速查台**：把 shell 命令、脚本、URL Scheme、Intent、Activity、Service、广播放在同一个货架上，按分类浏览、搜索、一键复制。

不是「命令列表 App」，而是**可插拔数据源**的命令台——内容来自 SMD 格式的数据源文件，可以自己写、可以分享、可以在线更新。

## 功能

- **七种条目类型**：`shell` / `script` / `urlscheme` / `intent` / `activity` / `service` / `broadcast`
- **分类货架**：按主题分类浏览，一屏放满，搜索全字段匹配
- **参数模板**：`{占位符}` + 默认值，填完实时重算命令
- **收藏**：多级文件夹 + 标签 + 个人备注，源失效后仍保留快照
- **两种详情形态**：命令类显示代码块，Intent 类显示字段表 + 自动生成的等价 adb 命令
- **零权限**：首版只做复制，不申请任何执行权限

## 数据源格式 SMD

SMD 是 Markdown 的超集，四条语法：

```markdown
## 应用管理

### 列出第三方应用
@risk safe
@need shell
@param filter = 范围 | -3
  -3 只列第三方，-s 只列系统
@example pm list packages -3

```sh
pm list packages {filter}
```
```

| 写法 | 含义 |
|---|---|
| `## 分类名` | 分类 |
| `### 标题` 或 `● 标题` | 条目（`●` / `•` 用于直接粘贴外部清单） |
| `@键 值` | 元信息：`@risk` `@need` `@tag` `@param` `@example` `@action` `@data` `@extra` `@flag` `@adb` … |
| 围栏代码块 | 命令体，**内容原样，不做任何解析** |

用 Markdown 而不是 JSON / YAML 的核心原因：命令里充满 `#` `:` `"` `\` `{}` `|`，

- JSON 里必须转义（一条含三层引号的 curl 会多出 20 多个 `\"`），
- YAML 里 `#` 会被当注释静默截断、`key: value` 直接报错，

而代码块内零解析，原样收。这条命令可以直接贴进去：

```sh
curl 'https://example.com/hook?key={key}' -H 'Content-Type: application/json' \
-d '{"text": "'"$msg"'"}'
```

## 构建

```bash
./gradlew assembleRelease
```

或直接看 [Releases](../../releases) 里的构建产物。

CI 由 GitHub Actions 负责：推送 `main` 更新 `dev-latest` 预发布，推 `v*` tag 出正式版。

## 技术栈

Kotlin 2.4.21 · Compose BOM 2026.06.01 · AGP 8.13.2 · Gradle 8.14.6 · JDK 17
compileSdk/targetSdk 36 · minSdk 26

无数据库（文件 + 内存索引）、无序列化框架（`org.json` 手解析）、无图片库（自绘矢量图标）。

## 许可

MIT
