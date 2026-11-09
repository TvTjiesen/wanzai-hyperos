import java.io.File
import java.util.Properties

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
        // ★ 抬到 33（Android 13）以使用 Miuix 的完整玻璃方案。
        //
        // 為什麼之前定 24 是錯的決策：
        //   `miuix-blur`（BloomStroke 雙光源高光 / ProgressiveBlur / textureBlur）
        //   的 minSdk 是 33。為了支援 24 而放棄它，結果是手搓了幾個小時
        //   的 shader 仍然不像 —— 而它自帶的就是成品。
        //
        // 代價：失去 Android 12 及以下機型。這個代價對「校園工具 App」可接受：
        //   - targetSdk 37（Android 16）的機型本來就覆蓋絕大多數在用設備
        //   - 沒有能力（時間/正確性）做出真的玻璃，才是更大的損失
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        // 加密存储（Android Keystore）无法在 JVM 单测里跑，只能在真机上验，
        // 所以有一条仪器化测试专门盯它。见 src/androidTest。
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 簽名配置（第四期準備）：key 與口令**都不在倉庫裡**
            //   - keystore：C:/Users/<user>/.wz-secret/wanzai-release.jks
            //   - 口令：keystore.properties（已被 .gitignore 擋住），或環境變數 WZ_KEYSTORE_PROPS
            //
            // 檔案不存在 ⇒ 這段跳過，release 產出**未簽名包**，assembleDebug 完全不受影響；
            // 不能因為缺簽名讓日常構建炸掉（這條是給下一個接手的人看的）。
            //
            // ⚠️ 這裡必須用文件頂部 import 進來的 Properties/File，**不能**寫 `java.util.Properties`：
            //    android{} 裡 `java` 指的是 JavaPluginExtension，會被解析過去而編譯失敗
            //    （2026-10-30 現場踩到：10 errors，全是 Cannot infer type / Unresolved reference 'io'）。
            val propsPath = System.getenv("WZ_KEYSTORE_PROPS")?.takeIf { it.isNotBlank() }
                ?: rootProject.file("keystore.properties").absolutePath
            val propsFile = File(propsPath)
            if (propsFile.isFile) {
                val props = Properties()
                // ⚠️ 必须用 Reader + UTF-8：Properties.load(InputStream) 按 ISO-8859-1 解，
                //    路径里的中文用户名（C:/Users/owner/…）会被解成乱码，报
                //    「Keystore file 'C:\Users\owner\.wz-secret\…' not found」（2026-10-30 现场踩到）。
                propsFile.reader(Charsets.UTF_8).use { props.load(it) }
                signingConfig = signingConfigs.create("release") {
                    storeFile = props.getProperty("storeFile")?.let { File(it) }
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }

        }
    }

    compileOptions {
        // 与上面的 jvmTarget 对齐（Miuix 0.9.4 是 JVM 21 字节码）。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        // 「关于」页要显示版本号（B4-10）：BuildConfig 默认不开，
        // 台账以前写的"渲染版本"是做不到的交付 —— 现在真开、真读。
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    // 机器上只有 JDK 21（无 JDK 17），用 21 编译。
    //
    // ⚠️ jvmTarget 必须也是 21，**不能**降成 17：Miuix 0.9.4 的产物（尤其
    // miuix-nav）是用 JVM target 21 编的（class 文件 major 65），而
    // `NavDisplay { entry<T> { ... } }` 是 inline 函数 ——
    // Kotlin 不允许把 JVM 21 的字节码内联进 JVM 17 的目标，会直接报
    // "Cannot inline bytecode built with JVM target 21 into bytecode that
    //  is being built with JVM target 17"。详见 docs/BUILD-KOTLIN.md §4.10。
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":glasmio-core"))
    implementation(project(":glasmio-glass"))
    implementation(project(":glasmio-ui"))
    implementation(project(":glasmio-icons"))
    implementation(project(":glasmio-preference"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Miuix（HyperOS 设计语言）—— 内部自带 JetBrains Compose Multiplatform 的 compose 运行时
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    // 保留 legacy 玻璃控件的 Kyant backdrop 依赖与原效果；主界面已接入上方 Glasmio 源码模块。
    implementation(libs.backdrop)
    // 保留 legacy BloomStroke 参考与 sensor.rememberDeviceTilt；不在本次迁移改动依赖或旧效果。
    implementation(libs.miuix.blur)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // 掃碼：CameraX（預覽 + 取幀）+ ZXing（解碼）。
    // 不用 Google 的 code-scanner（要 GMS，國產機普遍沒有）。
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // 真机验证：Android Keystore 的加解密在 JVM 上跑不了。
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
