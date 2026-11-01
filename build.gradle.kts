// 根构建脚本：只声明插件版本，不在这里配置 android 块。
// 版本组合是「一起锁定」的，单独升某一个必炸，参见 references/stack-versions.md
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    // Kotlin 2.0+ 起 Compose 编译器是独立插件，版本必须与 Kotlin 完全一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
