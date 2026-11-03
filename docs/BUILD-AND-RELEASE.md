# 构建、CI 与发版

## 环境要求

| 项 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | AGP 8.13 要求 |
| Gradle | 8.14.6 | CI 里由 `gradle/actions/setup-gradle` 指定 |
| AGP | 8.13.2 | Android Gradle Plugin |
| Kotlin | 2.4.21 | + `org.jetbrains.kotlin.plugin.compose` |
| Compose BOM | 2026.06.01 | compose 1.9 系列 |
| compileSdk / targetSdk | 36 | |
| minSdk | 26 | Android 8.0 |
| Android SDK | platform 36 + build-tools 36.0.0 | |

## 依赖（刻意保持极简）

```kotlin
implementation(platform("androidx.compose:compose-bom:2026.06.01"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.foundation:foundation")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:1.12.0")
implementation("androidx.core:core-ktx:1.18.0")          // ← 必须锁 1.18
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
```

**没有**：Room、Retrofit/OkHttp、kotlinx-serialization、Gson、Coil/Glide、Hilt/Koin、Navigation-Compose、ViewModel。
JSON 用系统自带 `org.json`，网络用 `java.net.HttpURLConnection`，图片没有（图标全是字形字符）。

### 版本兼容硬约束

| 约束 | 原因 |
|---|---|
| `core-ktx` 锁 **1.18.0** | 1.19.x 起要求 compileSdk 37，升了直接编译失败 |
| Compose BOM 不要升到 2026.08+ | 那批（compose 1.12）要求 compileSdk 37 + AGP 9.1 |
| AGP 9 会内置 Kotlin | 届时必须移除 `org.jetbrains.kotlin.android` 插件，否则构建失败 |

判定某个 AndroidX 库最低要求的方法：

```bash
unzip -p <lib>.aar META-INF/com/android/build/gradle/aar-metadata.properties
# 看 minCompileSdk 与 minAndroidGradlePluginVersion
```

## 版本号

**唯一来源是 `gradle.properties`**，不要在别处改：

```properties
VERSION_CODE=29          # 整数，每次出包必须递增（Android 只认它判断升级）
VERSION_NAME=0.19.1      # 语义化：新功能进次版本，修 bug 进修订号
```

`app/build.gradle.kts` 读这两个值注入 `versionCode/versionName` 和
`BuildConfig.APP_VERSION / APP_VERSION_CODE`（关于页与更新检查用）。

> ⚠️ **只改 versionName 不递增 versionCode，用户装不上新版**（覆盖安装会被系统拒绝）。
> 这是本项目踩过的坑，每次发版都要确认 code 加了 1。

## 本地构建

```bash
./gradlew assembleDebug      # 不需要签名，直接出 debug 包
./gradlew assembleRelease    # 需要 keystore.properties
```

签名配置放在项目根目录 `keystore.properties`（`.gitignore` 已排除，**不要提交**）：

```properties
storeFile=/绝对路径/release.keystore
storePassword=xxx
keyAlias=shellstore
keyPassword=xxx        # PKCS12 下必须与 storePassword 相同
```

debug 与 release **用同一把钥匙签名**（见 `build.gradle.kts` 的 `debug { signingConfig = release }`），
这样迭代时 debug 包可以覆盖安装到正式版上，不用卸载。

## CI（`.github/workflows/build-apk.yml`）

### 触发与产物

| 触发 | 动作 | 产物 |
|---|---|---|
| push `main`/`master`（app/** 等路径变更） | `assembleDebug` + aapt2/apksigner 体检 | 一次性 artifact（30 天），**不出 Release** |
| push tag `v*` | `assembleRelease` | **正式 Release**（唯一分发出口） |
| workflow_dispatch | 手动跑一次编译验证 | 同上（分支构建） |

### 必需 secrets

| 名字 | 内容 |
|---|---|
| `KEYSTORE_B64` | release.keystore 的 base64（**单行，无换行**） |
| `KEYSTORE_PASSWORD` | storePassword |
| `KEY_ALIAS` | keyAlias |
| `KEY_PASSWORD` | keyPassword（= storePassword） |

还原方式（workflow 里已实现，注意细节）：

```bash
printf '%s' "$KEYSTORE_B64" | base64 -d > "$RUNNER_TEMP/release.keystore"
```

必须是 `printf` 不能是 `echo` —— `echo` 会补换行，破坏 base64。

没有 secrets 时只出 unsigned debug 包；tag 构建会直接失败（`tag 构建必须配置签名 secrets`）。

### CI 里的 SDK

用 runner 自带的 SDK + `sdkmanager "platforms;android-36" "build-tools;36.0.0"` 手装。
**不要用** `android-actions/setup-android@v3`——新版 runner 上会报
`Wrong version in preinstalled sdkmanager` 直接失败。

## 发版 SOP

```bash
# 1. 确认功能已合并到 main 并通过一次分支构建
# 2. 改 gradle.properties（code +1，name 按语义化）
# 3. 更新 CHANGELOG.md（新增章节放最上面，标注【正式版】）
# 4. 推送
# 5. 等 branch build 绿
# 6. 打 tag（指向 main 当前 HEAD）
# 7. 等 tag build 出 Release
# 8. 验收
```

打 tag 用 API（本地 git push tag 容易碰 SSH 环境问题）：

```bash
SHA=$(curl -s -H "Authorization: Bearer $GH_TOKEN" \
  https://api.github.com/repos/rentianyu/ShellStore/git/ref/heads/main \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['object']['sha'])")

curl -s -X POST -H "Authorization: Bearer $GH_TOKEN" \
  -d "{\"ref\":\"refs/tags/v0.19.1\",\"sha\":\"$SHA\"}" \
  https://api.github.com/repos/rentianyu/ShellStore/git/refs
```

## 验收清单

发版后逐项核对，**不要只看"构建绿了"**：

1. **Release 存在且不是 draft / prerelease**
   `GET /repos/rentianyu/ShellStore/releases/tags/vX.Y.Z`
2. **asset 是 `app-release.apk`**，大小合理（当前约 8.7 MB）
3. **`releases/latest` 指向新 tag**
4. **APK 元数据**（用 `apk_peek.py` 或 aapt2）：

```bash
python3 apk_peek.py <apk-url> --expect-package com.xbta.shellstore --min-code <期望值>
# 要确认：package / versionCode / versionName / debuggable=null
```

5. **签名**：`apksigner verify --print-certs` 的 V2/V3 Signer SHA-256 与 keystore 一致

> APK 大小偶尔会与上一版**完全相同**（deflate 压缩 + 对齐后恰好等长），这不是没构建新包。
> 用 GitHub release asset 的 `digest`（sha256）或 APK 内的 versionName 判断。

## 分发

```
https://p.xbta.cc/https://github.com/rentianyu/ShellStore/releases/latest/download/app-release.apk
```

- `p.xbta.cc` 是自建反代（Cloudflare Worker），直连可用
- 必须用 `releases/latest/download/` 这条固定路径，不要写死 tag（否则永远拿旧版）
- 仓库必须 **public**：私有库的 release asset 匿名下载会返回 9 字节错误体，代理也救不了

App 内自动更新走 `Updater`：

1. 请求 `releases/latest` 拿 tag → 与 `BuildConfig.APP_VERSION` 比较（按数字段比，不是字符串）
2. 拼 `releases/latest/download/app-release.apk` 下载到缓存
3. 走 `FileProvider` + `ACTION_VIEW` 交系统安装器（需要 `REQUEST_INSTALL_PACKAGES` 权限）

## 常见坑

| 现象 | 原因 / 处理 |
|---|---|
| CI 报 `Wrong version in preinstalled sdkmanager` | 删掉 setup-android action，改用 runner 自带 SDK |
| tag 构建报「必须配置签名 secrets」 | secrets 没配或名字不符 |
| base64 解码出的 keystore 打不开 | 用了 `echo` 而不是 `printf`（多了换行） |
| `apksigner` 报 keystore 密码错误 | PKCS12 下 keyPassword ≠ storePassword |
| 用户装不上新版 | versionCode 没递增 |
| 构建 OOM | `org.gradle.jvmargs=-Xmx2048m` 可调大 |
| 拿不到 Release asset | 仓库私有 / 用了写死的 tag / 没走 proxy |

## 测试

`tests/` 下是**纯 Kotlin 测试**（不依赖 Android SDK，可用 kotlinc 直接编译跑）：

| 文件 | 内容 |
|---|---|
| `SmdTest.kt` | 解析器测试主程序：读 `tests/samples/*.md`，逐条 check 通过/失败 |
| `QuickCheck.kt` | 快速验证几个典型片段 |
| `samples/01-basic.md` … `05-intent.md` | 五份样例源（基础 / 列表写法 / adb / 引号转义 / Intent） |

运行方式（需本地有 kotlinc，沙箱里可从阿里云 Maven 拉一套）：

```bash
kotlinc app/src/main/java/com/xbta/shellstore/core/*.kt tests/SmdTest.kt -include-runtime -d /tmp/smdtest.jar
java -jar /tmp/smdtest.jar tests/samples
```

`core` 层刻意不 import `android.*`，就是为了让这部分能在无 SDK 环境验证。
UI 层没有自动化测试，靠 CI 编译 + 真机手测。
