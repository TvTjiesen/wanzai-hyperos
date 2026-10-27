package com.muyao.wanzaihyperos.ui.design.glass

/**
 * **玻璃浮动面的唯一名单**（业主 2026-10-22 13:2x 签字的边界：三档只管浮动面）。
 *
 * ## 为什么要有这一份名单
 *
 * #12 这条要求说了三遍「三档要覆盖全应用且实际有效」，而每一轮的修法都是"找到一处玻璃、
 * 把材质接上去"——**没有人有一份"到底哪几面是玻璃"的清单**，于是：
 *  · 判据只能写成"逐件截图对比"（= 只能靠眼睛，永远闭不掉）；
 *  · 同一类缺陷会在没被点到的那一面上复活（第 6 轮就是：底栏接了、卡没接）；
 *  · "覆盖全应用"这句话永远无法被证伪，也无法被证明。
 *
 * 现在把它变成一条**可静态判的枚举**：一面玻璃 = 一个条目 + 它所在的文件。
 * `GlassModeWiringTest` 的 G-6/G-7 钉两件事：
 *  ① 名单里每一面都必须真的读玻璃材质（读了不接 = 假开关，第 7 轮那条判据的同族）；
 *  ② 任何**没登记**的文件里出现玻璃绘制点（`GlassSurface(` / `drawBackdrop(`）⇒ 测试红，
 *     先登记再加面。机制本体（[GlassSurface] 自己、[CombinedBackdrop]）在白名单里。
 *
 * ## 边界（业主签字，审计不许再以此扣分）
 *
 * **不做玻璃的对象**：`ui/design/SettingsChrome.kt` 的 `PillSwitch`（开关）与
 * 15 个文件的返回键。理由当场给过业主：动效标准 §4「现有玻璃还没一致之前不加新的玻璃面」，
 * 每多一面 = 每帧多一次全屏采样；而开关属"一天几十次"那一档，它要的是**一眼看出开/关**，
 * 玻璃会糊掉状态本身。所以外观页那行文案必须写「开关和返回按钮始终是实心」，
 * 不许再写"覆盖全应用"这种兑现不了的口径。
 */
enum class GlassSurfaceId(
    /** 界面上给业主看的那个名字（外观页那行说明用它拼句子）。 */
    val label: String,
    /** 这一面画在哪个文件里（相对 `app/src/main/java/com/muyao/wanzaihyperos/`）。 */
    val siteFile: String,
    /** 它读材质走的是哪条路：`glass` 栈读 `LocalBarMaterial`，`liquid` 栈读 `MotionPrefs.refractsGlass`。 */
    val stack: GlassStack,
    /**
     * 这一面**现在**是不是玻璃面。
     *
     * 为什么名单里要有非玻璃的一格：业主 13:2x 签的那份清单里写了「外观预览卡」，
     * 但 `ui/home/HomeSky.kt` 的 `SkyPreview` 是**一支 Canvas 直绘**（跟首页共用 `drawSkyScene`，
     * 为的是"预览与实机同一套画法"），它根本不采样、也就没有材质可切。
     * 把"不是"写在名单里，比把它删掉诚实：下一轮审计能一眼看到"这一面为什么不生效"，
     * 而不是在五个名字里少一个、没人知道是漏了还是故意。
     */
    val kind: GlassSurfaceKind = GlassSurfaceKind.GLASS,
) {
    NavBarContainer("底部导航条", "ui/design/liquid/LiquidGlassNavigationBar.kt", GlassStack.LIQUID),
    NavBarSlider("导航条滑块", "ui/design/liquid/LiquidGlassNavigationBar.kt", GlassStack.LIQUID),
    /**
     * 底栏第三面：拖动/按压时跟着手指的那颗高光泡（`LiquidGlassNavigationBar.kt:576` 那个
     * `drawBackdrop(combinedBackdrop …)`）。第 8 轮审计点名"这个文件有三处 drawBackdrop、
     * 名单只登记两面 ⇒ 第三面不需要 id"，就是它 —— 现在它有自己的 id，G-7 的**配额**会盯住。
     */
    NavBarHighlight("导航条按压高光", "ui/design/liquid/LiquidGlassNavigationBar.kt", GlassStack.LIQUID),
    TopBarVeil("顶栏纱", "ui/design/liquid/IosChrome.kt", GlassStack.LIQUID),
    SearchButton("搜索按钮", "ui/design/liquid/GlassControls.kt", GlassStack.LIQUID),
    // ⚠️ 这里曾经有一格 `RestartScopeButton`。第 9 轮审计按业主 13:2x 的签字把它移出去了：
    //   **「划出范围就收」——玻璃只给浮动面，开关与返回按钮保持实底 + 描边**，
    //   而"重启范围"是个**按钮**（点一下就执行动作），不是浮在内容上的面。
    //   它的实底化改动在同文件的 `RestartScopeButton`（采样整条关掉、tint 提到近实底 + 描边），
    //   名额记到 `unglassedSites` 里，见那张表。
    HomeFocusCard("首页此刻卡", "ui/main/pages/HomeSectionPage.kt", GlassStack.GLASS),

    /**
     * 外观页那张预览卡：**现在是 Canvas 直绘，不是玻璃面**（见 [kind] 上面的说明）。
     * 要把它接成玻璃，等于在"现有玻璃还没一致之前新加一面"，而且它要采的是自己身后那张天空
     * （禁令 1 里那个"玻璃采到自己"的死循环形状）⇒ 需要业主单独点头，不许顺手做。
     */
    AppearancePreview(
        "外观预览卡",
        "ui/home/HomeSky.kt",
        GlassStack.LIQUID,
        GlassSurfaceKind.NOT_GLASS,
    ),

    // ⚠️ `GlassControls.kt:235` 那个 `drawBackdrop{ blur(30,30) }` **不算一面玻璃**：它是重启范围
    //   弹层的**压暗遮罩**（技能禁令 1 里唯一那份"空采样层闸门"白名单就是它 —— 空层最坏是
    //   "暗得实一点"，不会画黑带）。它本来也不该随档位变材质：它表达的是"背景不可点"，
    //   不是"这是一块玻璃"。登记进来会逼着给它接材质，那是为了凑名单而加效果。
    ;

    companion object {
        /** 名单里所有面落在哪些文件（G-7 用它判"有没有偷偷加新玻璃面"）。 */
        val registeredFiles: Set<String> get() = entries.map { it.siteFile }.toSet()
    }
}

/** 这一面现在是不是玻璃面。`NOT_GLASS` 不是"待办"，是**当前事实**，G-6 会去证它。 */
enum class GlassSurfaceKind { GLASS, NOT_GLASS }

/** 两条栈读材质的入口不同 —— 这是 #12 反复"接了却无效"的结构性原因，必须显式区分。 */
enum class GlassStack {
    /** 目录 `ui/design/glass` 那一栈：读 `LocalBarMaterial`（档位）+ `LocalGlassLiveness`（采样层活不活）。 */
    GLASS,

    /** 目录 `ui/design/liquid` 那一栈：读 `MotionPrefs.refractsGlass`（液态/磨砂）与 `GlassTuning` 的旋钮。 */
    LIQUID,
}

/**
 * 机制本体白名单（**只许减不许加**）：这两处是"玻璃怎么画"的实现，不是"某一面玻璃"，
 * 它们里面出现 `GlassSurface(` / `drawBackdrop(` 不算新增玻璃面。
 *
 * ⚠️ 与技能禁令 1 里那份"空采样层闸门白名单"不是一份东西，别合并：那份讲的是**要不要挡头两帧**，
 * 这份讲的是**要不要登记成一面玻璃**。
 */
val glassMechanismFiles = setOf(
    "ui/design/glass/GlassSurface.kt",
    "ui/design/glass/BarOptics.kt",
    "ui/design/liquid/CombinedBackdrop.kt",
    "ui/design/liquid/PageBackdrop.kt",
    // `IosChrome.kt` 里那个 `GlassSurface(` 是 **liquid 栈的机制本体**（这个文件定义了
    // `fun GlassSurface(...)`），它内部那处 `drawBackdrop` 也是实现。顶栏纱这一**面**登记在该文件里、
    // 由 G-6 判它有没有读材质 ⇒ 这里按机制文件处理，不重复计配额。
    "ui/design/liquid/IosChrome.kt",
)

/**
 * 「这里有一处绘制点，但它**不是一面玻璃**」的例外表 —— 键是文件、值是豁免几处。
 *
 * **每一条都要写清理由**：豁免是给"这个绘制点不承载材质语义"用的，不是给"懒得登记"用的。
 * 这张表只许因为"那个绘制点改了性质"而增删，不许为了"让测试变绿"而加行。
 */
val unglassedSites = mapOf(
    // 一处是 `GlassControls.kt` 里**重启范围弹层的整屏压暗遮罩**（`drawBackdrop{ blur(30,30) }`）：
    // 它表达的是"背景不可点"，不是"这是一块玻璃"，本来也不该随档位变材质
    //（技能禁令 1 里唯一那份闸门白名单就是它）。
    // 另一处是**重启范围那颗按钮**：业主 13:2x 签字把按钮划出玻璃范围（见上面那行注释），
    // 所以它现在**不登记成一面玻璃**，但文件里那个 `GlassSurface(` 调用点还在（实底化用的同一条 API）。
    "ui/design/liquid/GlassControls.kt" to 2,
)
