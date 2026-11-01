import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名配置：CI 里由 workflow 从 secrets 生成 keystore.properties；本地没有就只出 debug 包。
// 不要把 keystore 与密码提交进仓库（.gitignore 已排除）。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

val appVersionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("VERSION_NAME") as String?) ?: "0.1.0"

android {
    namespace = "com.xbta.shellstore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xbta.shellstore"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "APP_VERSION", "\"$appVersionName\"")
        buildConfigField("int", "APP_VERSION_CODE", "$appVersionCode")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                // PKCS12 里 keyPassword 必须等于 storePassword，否则 apksigner 报 keystore 密码错误
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 迭代期也出签名包：与 release 同签名才能互相覆盖安装，
            // 否则每换一次构建类型都要先卸载（用户侧表现为「应用未安装」）。
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // APK 体积优化：目标设备全是 64 位时打开，体积约砍 60%（装得快、下载快）。
    // 打开后产物名变成 app-arm64-v8a-debug.apk，CI 用 find 收集不受影响。
    // 需要兼容 32 位老旧设备时保持关闭。
    // splits {
    //     abi {
    //         isEnable = true
    //         reset()
    //         include("arm64-v8a")
    //         isUniversalApk = false
    //     }
    // }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    // 与 compileOptions 同一级别，避免 "Inconsistent JVM-target compatibility"
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.0")
    // core 1.19.x 起要求 compileSdk 37，锁 1.18.0
    implementation("androidx.core:core-ktx:1.18.0")

    // StateFlow 全局状态
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 需要协程 / 网络时再加（不要为了「以后可能用」提前引依赖）：
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
