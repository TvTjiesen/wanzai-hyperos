plugins {
    alias(libs.plugins.android.application)
    // 注意：AGP 9.0+ 内置 Kotlin 支持，**不能**再加 `org.jetbrains.kotlin.android`
    // （会报 "The 'org.jetbrains.kotlin.android' plugin is no longer required"）
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.muyao.wanzaihyperos"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.muyao.wanzaihyperos"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    // 机器上只有 JDK 21（无 JDK 17），用 21 编译、字节码目标仍为 17。
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Miuix（HyperOS 设计语言）—— 内部自带 JetBrains Compose Multiplatform 的 compose 运行时
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
