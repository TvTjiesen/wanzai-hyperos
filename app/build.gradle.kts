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
        versionCode = 1
        versionName = "0.1.0"
        // 加密存储（Android Keystore）无法在 JVM 单测里跑，只能在真机上验，
        // 所以有一条仪器化测试专门盯它。见 src/androidTest。
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        // 与上面的 jvmTarget 对齐（Miuix 0.9.4 是 JVM 21 字节码）。
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Miuix（HyperOS 设计语言）—— 内部自带 JetBrains Compose Multiplatform 的 compose 运行时
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    // 液態玻璃（iOS 26/27 style）：真實折射 + 模糊 + 高光。Apache-2.0，minSdk 21。
    // ★ 這條是**唯一**的玻璃引擎：折射 = `com.kyant.backdrop.effects.lens(...)`，
    //   高光 = `BloomStrokeShader`（Miuix BloomStroke 的移植）。
    implementation(libs.backdrop)
    // Miuix 的玻璃模組：這裡**只**用它的 BloomStroke 高光移植参考與
    // `sensor.rememberDeviceTilt`（重力方向）。它本身不提供折射 effect
    //（0.9.4 的 BackdropEffectsKt 只有 blur/progressiveBlur/blendColors/noiseDither/textureBlur），
    // 所以折射一律走上面那條 backdrop 路徑 —— 不許出現第二條渲染路徑。
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
