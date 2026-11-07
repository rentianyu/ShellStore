#!/bin/sh
# url scheme 解析单测：拆参数 / 容错 / 编解码 / 多层解码。
# 纯逻辑不依赖 android，桌面直接跑。
#
# 用法：
#   KOTLINC="sh /sandbox/workspace/deps/kotlinc/kotlinc.sh" \
#   KOTLIN_STDLIB="/sandbox/workspace/deps/kotlinc/jars/kotlin-stdlib-2.4.21.jar" \
#   sh tools/scheme-test/run.sh
set -e
cd "$(dirname "$0")/../.."
KOTLINC=${KOTLINC:-kotlinc}
STDLIB=${KOTLIN_STDLIB:-}
OUT=$(mktemp -d)
$KOTLINC \
  app/src/main/java/com/xbta/shellstore/core/SchemeParse.kt \
  tools/scheme-test/SchemeParseTest.kt -d "$OUT"
if [ -n "$STDLIB" ]; then
  java -cp "$OUT:$STDLIB" SchemeParseTestKt
else
  java -cp "$OUT" SchemeParseTestKt
fi
