package com.xbta.shellstore.core

/**
 * 订阅源的帮助文案。放在代码里而不是 assets：两段文本都要能一键复制，
 * 写在常量里最省事，也保证和解析器同步演进（改字段时这里必须一起改）。
 */
object SourceHelpText {

    /** 最小可用模板：给人抄的，短。 */
    val MINIMAL = """
# 我的源

@author 你的名字
@updated 2026-11-02

## 应用管理

### 列出第三方应用
@risk safe
@need shell
@tag 包名
@param filter=范围|-3
  -3 只列第三方，-s 只列系统

```sh
pm list packages {filter}
```
""".trimIndent()

    /**
     * 交给 AI 的完整规范。要求：
     *  - 自包含，粘给任何模型就能产出可解析的 SMD，不依赖外部链接
     *  - 所有约束都与 SmdParser 的实际行为一致（字段名、风险枚举、代码块规则）
     *  - 结尾固定输出要求，避免模型加解释、加代码块外壳
     */
    val AI_PROMPT = """
你是一个订阅源生成器。请严格按下面的 SMD 格式规范，把用户给你的内容整理成一个订阅源文件。

# SMD 格式规范

SMD 是 Markdown 的超集，只多约定了几行 @字段。解析器按固定规则读取，多余的花样一律不认。

## 一、文件结构（三层）

# 源名称                      ← 一级标题，整个文件只有一个
@author 作者
@version 1.0
@updated 2026-11-02

## 分类名                      ← 二级标题，每一类一个
### 条目标题                  ← 三级标题，每条一个
@risk safe
@need shell

```sh
命令正文写这里
```

条目标题也可以写成 “● 标题” 或 “- 标题”，三种等价。

## 二、条目字段

| 字段 | 说明 | 取值 |
|---|---|---|
| @type | 类型，不写按内容推断 | shell / script / urlscheme / intent / activity / service / broadcast |
| @risk | 风险等级 | safe（只读）/ caution（会改设置）/ danger（删数据、卸载、重启） |
| @need | 需要的权限 | none / shell / root / termux |
| @tag | 标签，空格或逗号分隔 | 任意 |
| @desc | 一句话说明 | 任意 |
| @param | 可填参数 | 键=标签|默认值（下一行可缩进写说明） |
| @verified | 最近验证日期 | 2026-11-02 |
| @link | 参考链接 | URL |

参数在命令里用花括号引用：{键}。

## 三、Intent 类专用字段（type 为 intent/activity/service/broadcast 时）

@action       Intent action，如 android.settings.WIFI_SETTINGS
@component    包名/类名，与 action 至少写一个
@data         data URI
@mime         MIME 类型
@package      限定目标包名
@category     可重复多行
@flag         NEW_TASK / CLEAR_TOP 等，也可写十六进制
@extra        键:类型=值，类型支持 string / bool / int / long / float / null / string-arr / int-arr

urlscheme 类型直接把 URI 写进代码块，例如 tel:10086。

## 四、多条写法（同一命令的变体）

一个条目里写多个代码块，会在详情页变成可切换的“写法”。
第一个代码块是主命令，后面每个代码块前用 @v 名字 标注变体名。

## 五、硬约束（违反会解析失败或显示错乱）

1. 命令正文必须放进围栏代码块（三个反引号），代码块内不做任何解析，@、#、反引号原样保留，不需要转义。
2. 命令开头不要写 “adb shell”（解析器会自动剥掉，写了也不报错）；带 “su -c” 的会被自动标成需要 root。
3. 同一个文件里条目标题不能重复，重复会互相覆盖。
4. 字段必须写成 “@键 值” 一行一个，值不换行（说明文字写在 @param 的下一行并缩进）。
5. 每个条目最多一个主代码块；多出来的代码块会被当成写法变体。
6. 风险等级宁可标高不标低：会改系统设置的一律 caution，删数据、卸载、重启用 danger。
7. 分类不要超过 12 个，每类 3-15 条；少于 3 条的类合并掉。

## 六、完整示例

# 示例源
@author 小贝塔
@version 1.0
@updated 2026-11-02

## 设备信息

### 查看设备型号
@risk safe
@need shell
@tag 硬件

```sh
getprop ro.product.model
```

### 打开 WiFi 设置
@type activity
@risk safe
@need none

@action android.settings.WIFI_SETTINGS

## 应用管理

### 强制停止应用
@risk caution
@need shell
@tag 包名
@param pkg=包名|com.tencent.mm

```sh
am force-stop {pkg}
```

## 七、输出要求

- 只输出 SMD 正文，从 “# 源名称” 开始，不要任何解释、前言、结语
- 不要用 markdown 代码块把整个结果包起来
- 不要输出 “以下是…” 这类话
- 分类顺序按你认为的使用频率排
""".trimIndent()
}
