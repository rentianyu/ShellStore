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
