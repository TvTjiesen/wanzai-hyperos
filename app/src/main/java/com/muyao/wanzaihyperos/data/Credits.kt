package com.muyao.wanzaihyperos.data

/**
 * 「感谢」那一页的数据（v3 清单 #9）。
 *
 * ## 这些字符串是从哪儿来的（业主判据：「依赖名与许可证从 build.gradle + Gradle 依赖报告取，不凭记忆写」）
 *
 * 1. 名单 = `./gradlew :app:dependencies --configuration debugRuntimeClasspath` 的**直接依赖**，
 *    版本号取**解析后**的那个（`lifecycle 2.9.4 -> 2.11.0` 这种要写 2.11.0，那才是真正打进包里的）。
 * 2. 许可证 = 逐个抓该坐标在 Maven Central / Google Maven 上的 **`.pom`**，
 *    读它的 `<licenses><name>`。POM 里没有 `<license>` 段的（zxing），照实写"未单列"，
 *    不拿记忆补一个名字上去。
 * 3. 改写的代码（`ui/design/liquid/` 里那些 "Adapted from Kyant0/AndroidLiquidGlass"）
 *    单独一栏 —— 那是**衍生作品**的署名义务，比依赖更要紧。
 *
 * ⚠️ 这一页**不许手填**：升级依赖后重新生成，并让 `CreditsTest` 与 Gradle 报告对齐。
 */
data class Credit(
    val name: String,
    /** Maven 坐标 `group:artifact`。 */
    val coordinate: String,
    val version: String,
    val license: String,
)

object Credits {

    /** Apache-2.0 的简写：POM 里两种写法都有，界面上统一成一句人话。 */
    private const val APACHE = "Apache License 2.0"

    val dependencies: List<Credit> = listOf(
        Credit("Kotlin Standard Library", "org.jetbrains.kotlin:kotlin-stdlib", "2.4.20", APACHE),
        Credit("AndroidX Core KTX", "androidx.core:core-ktx", "1.17.0", APACHE),
        Credit("AndroidX Lifecycle Runtime KTX", "androidx.lifecycle:lifecycle-runtime-ktx", "2.11.0", APACHE),
        Credit("AndroidX Lifecycle ViewModel Compose", "androidx.lifecycle:lifecycle-viewmodel-compose", "2.11.0", APACHE),
        Credit("AndroidX Activity Compose", "androidx.activity:activity-compose", "1.12.4", APACHE),
        Credit("Miuix UI", "top.yukonga.miuix.kmp:miuix-ui-android", "0.9.4", APACHE),
        Credit("Miuix Preference", "top.yukonga.miuix.kmp:miuix-preference-android", "0.9.4", APACHE),
        Credit("Miuix Icons", "top.yukonga.miuix.kmp:miuix-icons-android", "0.9.4", APACHE),
        Credit("Miuix Nav", "top.yukonga.miuix.kmp:miuix-nav-android", "0.9.4", APACHE),
        Credit("Miuix Blur", "top.yukonga.miuix.kmp:miuix-blur-android", "0.9.4", APACHE),
        Credit("Backdrop（液态玻璃采样层）", "io.github.kyant0:backdrop", "2.0.1", APACHE),
        Credit("kotlinx coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-android", "1.10.2", APACHE),
        Credit("kotlinx serialization JSON", "org.jetbrains.kotlinx:kotlinx-serialization-json", "1.11.0", APACHE),
        Credit("OkHttp", "com.squareup.okhttp3:okhttp", "5.1.0", APACHE),
        Credit("AndroidX Camera Core", "androidx.camera:camera-core", "1.4.2", "$APACHE；POM 另列 BSD License"),
        Credit("AndroidX Camera Camera2", "androidx.camera:camera-camera2", "1.4.2", APACHE),
        Credit("AndroidX Camera Lifecycle", "androidx.camera:camera-lifecycle", "1.4.2", APACHE),
        Credit("AndroidX Camera View", "androidx.camera:camera-view", "1.4.2", APACHE),
        Credit("ZXing Core", "com.google.zxing:core", "3.5.3", "Apache License 2.0（POM 内出现该链接，但未单列 license 段）"),
    )

    /**
     * **改写自**别人的源码 —— 不是依赖，但必须署名。
     * 依据是这些文件自己的档头注释，不是我印象里的出处。
     */
    val derived: List<Credit> = listOf(
        Credit(
            name = "AndroidLiquidGlass（折射、内阴影、阻尼拖拽、交互高光等）",
            coordinate = "github.com/Kyant0/AndroidLiquidGlass",
            version = "改写，未固定上游版本",
            license = APACHE,
        ),
    )

    /**
     * 随包字体。
     *
     * ⚠️ 这里**只写我们确实知道的事实**：仓库里放了 4 个字重的 ttf，
     * 而**授权文本没有随包存放** —— 所以这一行不声明任何许可证名字，
     * 等业主把官方授权页/许可原文放进 `app/src/main/res/raw/` 之后再补。
     */
    val fonts: List<Credit> = listOf(
        Credit(
            name = "MiSans（随包 4 个字重：regular / medium / semibold / bold）",
            coordinate = "app/src/main/res/font/misans_*.ttf",
            version = "未记录版本号",
            license = "授权文本未随包存放 —— 待补，不在此处代答",
        ),
    )
}

/**
 * 关于页要挂的两个外链（v3 #9：GitHub 项目地址 + 酷安主页）。
 *
 * ★ 两个都**留空**，因为业主还没定：仓库名与可见性由他决定（#14 明文），
 *   酷安主页他也还没给。这里写一个猜的域名，界面上就是一条打不开的死链，
 *   比"暂时没填"更糟。空着 ⇒ [com.muyao.wanzaihyperos.ui.about.AboutPage] 会把那一行
 *   显示成「尚未填写」并且不可点。
 */
object AboutLinks {
    /**
     * 他的 GitHub **主页**（业主 2026-10-23 给的身份：`TvTjiesen`，来源
     * `hzr/app/src/main/kotlin/xyz/nextalone/hyperzoomring/util/Links.kt:13-15`）。
     *
     * ⚠️ 那条来源给的是 **OmniRing（另一个 App）的仓库**；本 App 自己的库还没建（B4-09），
     * 这里指主页 ⇒ 打开是真页面，也不会把“这是本 App 的源码”说成假话。
     * B4-09 建完库把它换成仓库地址即可（`CreditsTest` 守着“非空且 https”）。
     */
    const val github = "https://github.com/TvTjiesen"

    /** 他的酷安主页（同一处来源）。 */
    const val coolapk = "https://www.coolapk.com/u/22174009"
}
