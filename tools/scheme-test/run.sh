#!/bin/sh
# url scheme 相关纯逻辑的桌面单测：
#   SchemeParseTest —— 拆参数 / 容错 / 编解码 / 多层解码 / 回写
#   JsonLiteTest    —— 极简 JSON 解析（参数值展开用）
#
# 用法：
#   KOTLINC="sh /sandbox/workspace/deps/kotlinc/kotlinc.sh" \
#   KOTLIN_STDLIB="/sandbox/workspace/deps/kotlinc/jars/kotlin-stdlib-2.4.21.jar" \
#   sh tools/scheme-test/run.sh
set -e
cd "$(dirname "$0")/../.."
KOTLINC=${KOTLINC:-kotlinc}
STDLIB=${KOTLIN_STDLIB:-}
CORE=app/src/main/java/com/xbta/shellstore/core

OUT1=$(mktemp -d)
$KOTLINC $CORE/SchemeParse.kt tools/scheme-test/SchemeParseTest.kt -d "$OUT1"
java -cp "$OUT1${STDLIB:+:$STDLIB}" SchemeParseTestKt

OUT2=$(mktemp -d)
$KOTLINC $CORE/JsonLite.kt tools/scheme-test/JsonLiteTest.kt -d "$OUT2"
java -cp "$OUT2${STDLIB:+:$STDLIB}" JsonLiteTestKt

OUT3=$(mktemp -d)
$KOTLINC $CORE/JsonLite.kt tools/scheme-test/JsonSetTest.kt -d "$OUT3"
java -cp "$OUT3${STDLIB:+:$STDLIB}" JsonSetTestKt

# 目录（多级分类）规则：拆分、补父节点、折叠、SMD 解析往返
OUT4=$(mktemp -d)
$KOTLINC $CORE/Model.kt $CORE/SmdPath.kt $CORE/CategoryTree.kt $CORE/SmdParser.kt \
    $CORE/SmdWriter.kt tools/scheme-test/SmdPathTest.kt -d "$OUT4"
java -cp "$OUT4${STDLIB:+:$STDLIB}" SmdPathTestKt

# 筛选/排序逻辑：类型、风险、权限、分类目录、来源、标签、属性、排序
OUT5=$(mktemp -d)
$KOTLINC $CORE/Model.kt $CORE/SmdPath.kt $CORE/SearchText.kt $CORE/EntryFilter.kt tools/scheme-test/EntryFilterTest.kt -d "$OUT5"
java -cp "$OUT5${STDLIB:+:$STDLIB}" EntryFilterTestKt

# 运行模板：$command 替换与 shell 安全引用
OUT6=$(mktemp -d)
$KOTLINC $CORE/RunTemplate.kt tools/scheme-test/RunTemplateTest.kt -d "$OUT6"
java -cp "$OUT6${STDLIB:+:$STDLIB}" RunTemplateTestKt

# 分享导入：前缀清理、类型猜测、草稿生成
OUT7=$(mktemp -d)
$KOTLINC $CORE/Model.kt $CORE/ShareDraft.kt tools/scheme-test/ShareDraftTest.kt -d "$OUT7"
java -cp "$OUT7${STDLIB:+:$STDLIB}" ShareDraftTestKt

# 全局变量：{{name}} 替换、引用检查、名称校验
OUT8=$(mktemp -d)
$KOTLINC $CORE/GlobalVars.kt tools/scheme-test/GlobalVarsTest.kt -d "$OUT8"
java -cp "$OUT8${STDLIB:+:$STDLIB}" GlobalVarsTestKt
