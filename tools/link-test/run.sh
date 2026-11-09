#!/bin/sh
# deep link 协议的桌面单测（不依赖 android）
# 用法：
#   KOTLINC="sh /sandbox/workspace/deps/kotlinc/kotlinc.sh" \
#   KOTLIN_STDLIB="/sandbox/workspace/deps/kotlinc/jars/kotlin-stdlib-2.4.21.jar" \
#   sh tools/link-test/run.sh
set -e
cd "$(dirname "$0")/../.."
KOTLINC=${KOTLINC:-kotlinc}
STDLIB=${KOTLIN_STDLIB:-}
CORE=app/src/main/java/com/xbta/shellstore/core
OUT=$(mktemp -d)
$KOTLINC $CORE/SchemeParse.kt $CORE/DeepLink.kt tools/link-test/DeepLinkTest.kt -d "$OUT"
java -cp "$OUT${STDLIB:+:$STDLIB}" DeepLinkTestKt
