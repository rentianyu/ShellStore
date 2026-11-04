plugins {
    id("com.android.application")
}

// 导出 APK 用的模板工程：极简执行器，不含任何第三方依赖。
//
// 它本身不发布，只在 CI 里构建出一个「未签名」的 release APK，
// 拷进主 App 的 assets 作为模板；用户点「导出 APK」时，
// 主 App 会改这里的包名、替换命令文件、再用导出专用密钥重新签名。
//
// applicationId 必须与 namespace 分开：Java 包名的段不能以数字开头，
// 而 applicationId 的末段要留成「a + 8 位十六进制」以便导出时等长替换。
android {
    namespace = "com.xbta.cmd.runner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xbta.cmd.a0000000"
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
