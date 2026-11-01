pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 老库不在 Maven Central 时加聚合镜像（jcenter 已死）：
        // maven("https://maven.aliyun.com/repository/public")
        // maven("https://jitpack.io")
    }
}

rootProject.name = "ShellStore"
include(":app")
