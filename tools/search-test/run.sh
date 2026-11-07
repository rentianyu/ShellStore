#!/bin/sh
# 搜索核心单测：分词 / 拼音首字母 / 打分 / 高亮区间。
# 纯逻辑不依赖 android，可以在桌面直接跑，不必等 CI。
#
# 用法：
#   KOTLINC="sh /sandbox/workspace/deps/kotlinc/kotlinc.sh" \
#   KOTLIN_STDLIB="/sandbox/workspace/deps/kotlinc/jars/kotlin-stdlib-2.4.21.jar" \
#   sh tools/search-test/run.sh
set -e
cd "$(dirname "$0")/../.."
KOTLINC=${KOTLINC:-kotlinc}
STDLIB=${KOTLIN_STDLIB:-}
OUT=$(mktemp -d)
$KOTLINC \
  app/src/main/java/com/xbta/shellstore/core/Model.kt \
  app/src/main/java/com/xbta/shellstore/core/SearchText.kt \
  tools/search-test/SearchTextTest.kt -d "$OUT"
if [ -n "$STDLIB" ]; then
  java -cp "$OUT:$STDLIB" SearchTextTestKt
else
  java -cp "$OUT" SearchTextTestKt
fi
