pluginManagement {
    repositories {
        // 国内镜像优先（本机直连 repo.maven.apache.org / plugins.gradle.org 会 TLS 中断）
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
    }
}

rootProject.name = "WanzaiHyperOS"
include(":app")

listOf("core", "glass", "ui", "icons", "preference").forEach { module ->
    include(":glasmio-$module")
    project(":glasmio-$module").projectDir = file("third_party/glasmio/glasmio-$module")
}
