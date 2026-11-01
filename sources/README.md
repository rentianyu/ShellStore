# 命令源（SMD 格式）

Shell 商店的数据源就是这个目录里的 Markdown 文件。SMD 是 Markdown 的超集：
写起来跟普通 Markdown 一样，只是约定了几行 `@字段` 来做结构化描述。

## 文件结构

````md
# 源名称

@author 作者
@version 2026.11.1
@updated 2026-11-01

## 分类名

### 条目标题
@risk safe
@need shell
@tag 设备 型号

```sh
getprop ro.product.model
```
````

三层：`#` 文件标题 → `##` 分类 → `###` 条目。

`###` 也可以写成 `● 标题`、`• 标题` 或 `- 标题`（列表写法更好敲）。

## 条目字段

| 字段 | 说明 | 取值 |
|---|---|---|
| `@type` | 类型，不写会按内容推断 | `shell` `script` `urlscheme` `intent` `activity` `service` `broadcast` |
| `@risk` | 风险等级，决定卡片上的颜色点 | `safe`（只读）/ `caution`（会改东西）/ `danger`（不可逆） |
| `@need` | 需要的权限 | `none` / `shell`（adb 或 Shizuku）/ `root` / `termux` |
| `@tag` | 标签，空格或逗号分隔 | 任意 |
| `@desc` | 说明，写在一行 | 任意文本 |
| `@param` | 可填参数 | `键=标签\|默认值` |
| `@verified` | 最近验证日期 | `2026-11-01` |

参数在正文里用 `{键}` 引用：

````md
@param pkg=包名|com.tencent.mm

```sh
dumpsys package {pkg}
```
````

## Intent 类字段

`intent` / `activity` / `service` / `broadcast` 这四种用字段而不是代码块：

| 字段 | 说明 |
|---|---|
| `@action` | Intent action |
| `@component` | `包名/类名`，与 action 至少填一个 |
| `@data` | data URI |
| `@mime` | MIME 类型 |
| `@package` | 限定目标包名 |
| `@category` | 可重复，添加多个 category |
| `@flag` | `NEW_TASK` `CLEAR_TOP` 等，也可写十六进制 |
| `@extra` | `键:类型=值`，类型支持 string / bool / int / long / float / null / string-arr / int-arr |

```md
### 打开 WiFi 设置
@type activity
@risk safe
@need none

@action android.settings.WIFI_SETTINGS
```

`urlscheme` 类型可以直接把 URI 写在代码块里：

````md
### 拨号
@type urlscheme

```text
tel:10086
```
````

## 几条约定

- 代码块里的内容不做任何解析，`@`、`#`、反引号都原样保留，写复杂脚本不用转义。
- 命令里不要带 `adb shell` 前缀，解析器会自动剥掉；带 `su -c` 的会标成需要 root。
- 同一个文件里条目标题不要重复，重复会导致两屏内容互相覆盖。
- 风险等级宁可标高不标低：会改系统设置的一律 `caution`，删数据、卸载、重启的用 `danger`。

## 发布

改完直接推到仓库 `main` 分支即可，App 里的官方源指向：

```
https://raw.githubusercontent.com/rentianyu/ShellStore/main/sources/official.md
```

国内直连 GitHub raw 经常不通，App 拉取失败时会自动改走 `https://p.xbta.cc/` 反代，无需额外配置。
