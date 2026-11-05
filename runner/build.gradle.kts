plugins {
    id("com.android.application")
}

// 导出 APK 用的模板工程：极简执行器 + 可替换图标，不含业务逻辑。
//
// 它本身不发布，只在 CI 里构建出一个「未签名」的 release APK，
// 拷进主 App 的 assets 作为模板；用户点「导出 APK」时，主 App 会：
//   1. 重建 AndroidManifest 的字符串池，替换包名 / 应用名 / Shizuku provider 的 authorities
//   2. 改写 resources.arsc 里 package chunk 的包名（256 字节定长区，支持任意长度）
//   3. 替换各密度 mipmap 的图标 PNG
//   4. 写入 assets/config.json（动作类型 / 内容 / 执行身份）
//   5. 用导出专用密钥重新签名
//
// 因此这里的约束是：
//   - android:label 必须是**占位符字面量**（导出时按字符串池替换，改资源表太复杂）
//   - 必须自带 mipmap 图标（否则没有可替换的 PNG 条目、arsc 里也没有 package chunk）
//   - authorities 里必须写全包名，导出时要跟着一起换
android {
    namespace = "com.xbta.cmd.tpl"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xbta.cmd.tpl"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 模板要尽量小：导出时整个 APK 会被复制一份给用户
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    // Shizuku：让导出的 App 也能借 ADB / root 身份执行
    // 锁 12.1.0：13.x 起 Shizuku.newProcess 改成 private，拿不到进程的 stdout/stderr 流
    implementation("dev.rikka.shizuku:api:12.1.0")
    implementation("dev.rikka.shizuku:provider:12.1.0")
}
