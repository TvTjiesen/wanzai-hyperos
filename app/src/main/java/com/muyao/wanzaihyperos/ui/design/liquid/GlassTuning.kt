package com.muyao.wanzaihyperos.ui.design.liquid

import android.content.Context
import android.provider.Settings

/**
 * 玻璃参数的热调入口（真机排障用，不用重编）。
 *
 * 为什么要它：判断"实时模糊到底有没有生效"，唯一的办法是同一个位置改一个量再截图对比。
 * 每改一次就重装一次太慢，而且装机这条路本身会引入别的变量（LSPosed 缓存 dex 等）。
 * 应用侧读 Settings.Global 不需要任何权限，写由 adb 完成。
 *
 * adb shell settings put global hzr_glass_bar_blur 60     # 底栏/滑块的模糊 dp
 * adb shell settings put global hzr_glass_veil_blur 60    # 顶栏纱的模糊 dp
 * adb shell settings put global hzr_glass_lens 40         # 底栏透镜折射 dp
 * adb shell settings put global hzr_glass_bar_blur 4      # 回默认
 */
object GlassTuning {
    /**
     * 底栏模糊 dp。**2026-10-21 由 4f 改为 20f** ——
     * 板上 `.tabbar{backdrop-filter:blur(26px) saturate(1.55)}`，
     * 26 CSS px × 0.886 ≈ 23dp，取 20（8ms 帧预算下留一档余量）。
     *
     * 4f 是那版**悬浮胶囊**（64dp 高、只盖一格 tab）时候的值：面积小、又叠了
     * lens(24dp) 折射， blur 只需要「有一点雾」。现在整条满宽贴底、
     * lens 按板删掉（CSS 没有折射），只剩 blur 撑着「这是一层玻璃」，
     * 4dp 在 68% 的 --glass 垫色下等于没糊。
     *
     * A/B（改完 2 秒生效，不用重装）：
     *   adb shell settings put global hzr_glass_bar_blur 4    # 回到胶囊那版的糊法
     *   adb shell settings put global hzr_glass_bar_blur 20   # 板上这版（默认）
     */
    const val BAR_DEFAULT_DP = 20f
    const val VEIL_DEFAULT_DP = 30f
    const val LENS_DEFAULT_DP = 24f

    @Volatile
    var barBlurDp = BAR_DEFAULT_DP

    @Volatile
    var veilBlurDp = VEIL_DEFAULT_DP

    @Volatile
    var lensDp = LENS_DEFAULT_DP

    /** 1 = 把采样层也挂到根容器上（让顶栏/底栏成为它的子孙，能采到页面内容） */
    @Volatile
    var rootLayer = 0f

    /**
     * 1 = 干脆不挂采样层（底栏退化成纯色）。
     *
     * 这是测量用的开关，不是给用户看的设置：cfg18 把"每页一份 backdrop"上机之后，
     * 一次受控上滑的内容位移从 255px 掉到 50px，同时底栏带随内容变化的比例从 30.5%
     * 掉到 0%。两个数一起变说明问题不在"参数"而在"挂了这个节点本身"。
     * 关掉它再量一次，才能把"recorder 影响滚动"和"别的东西影响滚动"分开。
     */
    @Volatile
    var recorderOff = 0f

    /**
     * 1 = 允许 lens 用 depthEffect + chromaticAberration。
     *
     * 默认 0（关）。理由是真机连拍：快速点底栏时"跳出来的那个黑东西"就是选中胶囊那层
     * `lens(depthEffect = true, chromaticAberration = 0.5f)` —— 它没有 blur 垫着，
     * 采样层在被重录的那一帧是空的，AGSL 直接给出实心黑；而带 blur 的那几处只是变淡，
     * 不出黑。留这个键是为了能在不重编的前提下 A/B 对比，别当设置项给用户看。
     */
    @Volatile
    var depthLens = 0f

    /**
     * 滑块（选中胶囊）自己那层模糊，单位 dp。**默认 0 = 不糊**。
     *
     * 他要的是"玻璃滑块留着，但选中那个别糊"（2026-10-16 深夜）。cfg27 那版胶囊固定垫了
     * 6dp blur，静止时 `pressProgress = 0` → 折射量也是 0，于是胶囊里那格 tab 内容**只剩糊**，
     * 这就是他看到的"选中的那个直接模糊"。把 blur 归零后只剩按下去才起的折射，字就是清晰的。
     *
     * A/B（不用重装，2 秒生效）：
     *   adb shell settings put global hzr_glass_pill_blur 6   # 回到 cfg27 的糊法
     *   adb shell settings put global hzr_glass_pill_blur 0   # 去掉模糊（默认）
     * 黑块风险由 `backdropIsLive(tabsBackdrop)` 闸门兜住：镜像采样层空掉的那一帧不画玻璃。
     */
    @Volatile
    var pillBlurDp = 0f

    /**
     * 1 = 右上角那颗钮真的去采页面内容（liveBlur）。**默认关**（2026-10-19 真机 A/B 定的）。
     *
     * ## 为什么以前关着
     *
     * 这颗钮只有 44dp，cfg26 那版一开就变**实心黑圆盘**。当时还没修
     * 「共享采样层被离场页清空」那件事 —— 黑不是"糊不出来"，是空样本被画成不透明黑。
     *
     * ## 这次重做 A/B 的结论：**四页能糊、首页会黑，所以还不能开**
     *
     * 用**换包**做的（不是热调：这台机上 `settings put global` 那条通道
     *  实测**改变不了画面** —— 拿控制组 `hzr_glass_off 1` 量过，全图零变化，
     *  所以以前那些"热调 A/B 结论"都不能信，必须换包）。
     *
     * 量的是钮那一块 78×78 的**总亮度**（当年那块黑的判据就是这个数从 ~735 掉到 ~445）：
     *
     * | 页面 | liveBlur 关 | liveBlur 开 |
     * |---|---|---|
     * | 課程表 / 消息 / 應用 / 設定 | ~720 | ~691–720（跟着滚动变，说明真采到内容） |
     * | **首頁** | ~720 浅色 | **168.4**（mean 42/54/73 = 深蓝黑） |
     *
     * 而且首页那一颗**不会自愈**：停留 4 秒、切去課表再切回来、再等 5 秒，
     * 四次采样全是同一个 168.4 —— 不是过渡帧，是稳定状态。
     * 那个颜色正是首页「视窗」那幅画的色调，所以症状指向
     * **采样层在这颗钮上的取样的位置跑到了视窗那块画上**（坐标空间不一致），
     * 不是"没采到"。这是一个要单独查的东西，不是一颗能凑合的按钮。
     *
     * 所以：**保持 0**，等采样偏移查清楚再默认打开。
     * 想自己看：`adb shell settings put global hzr_glass_btn_live 1`
     * （⚠️ 这台机上这条通道目前**不生效**，要看效果得改这里的默认值重新装）。
     */
    /**
     * 右上角搜索钮要不要真的实时折射（liveBlur）。
     *
     * ## 2026-10-20 重新量过：**默认打开**
     *
     * T60 那次关掉它，是因为一开就把首页那颗钮画成**黑饼**（亮度 168.4，四次采样不自愈）。
     * 今晚在同一台机（PLZ110）、同一颗钮上重量，**黑饼不复现了**：
     *
     * | 位置 | btnLive=1 实测（RGB / 亮度） |
     * |---|---|
     * | 按钮盘内（未滚动） | (226,235,251) / **234.1** |
     * | 同一行按钮左侧页面 | (151,158,168) / 157.0（那是大字「20」的笔画） |
     * | 按钮右边贴边处 | (221,230,250) / 230.2 |
     * | 滚到画上再量：盘内 | (232,240,254) / **239.2** |
     * | 滚到画上再量：盘正下方（画里） | (233,236,245) / 236.1 |
     *
     * 盘内亮度跟着**它自己那块背景**走（234→239），不再是钉死的 168 ——
     * 也就是说采样拿到的就是按钮底下的内容。T60 那个黑块是**当时那一版**的症状
     * （那之后顶栏接线整块重做过），不是一个还在等修的 bug。
     *
     * ★ 留这条注释的原因：如果哪天它又变回黑饼，判据现成 ——
     *   同一坐标量「盘内」与「盘正下方」，两者一致=采样对、只是背景本来暗（改配色），
     *   差很多=采样错位（改坐标空间）。别再靠猜。
     */
    @Volatile
    var btnLive = 1f

    /** adb `settings put global hzr_debug 1`：界面侧自检要不要往 logcat 写 */
    @Volatile
    var debugLog = false

    /**
     * 二级页（路由）切换的时长，单位 ms。**默认 180**（第 9 轮审计：主导航应削到 170–200ms）。
     *
     * 为什么不是参考（变焦环）的 280：页面切换属「几十次/天」那一档 ——
     * 标准 §1 对它的要求是"删掉，或只留几乎察觉不到的"。280ms 在这一档上偏长，
     * 而且参考那边是"整页推进 + 缩放"的复合动作，我们只有一条深度曲线，同一条时长体感更重。
     *
     * 留这个键是因为**快慢是观感，由他判**，不该由我猜完就烘死。
     *
     * A/B（不用重装，改完 2 秒生效）：
     *   adb shell settings put global wz_nav_ms 140   # 与"减弱动效"那一档同速（纯 alpha 档的时长）
     *   adb shell settings put global wz_nav_ms 180   # 默认
     *   adb shell settings put global wz_nav_ms 280   # 旧默认，用来对比
     *   ⚠️ `0`（和负数）**不再是"用 Miuix 默认曲线"**：那条语义会让"关掉动效"变成"位移照旧、
     *      时长还不由我们定"。现在 `<= 0` 只表示"不要位移"，走同一个 140ms 纯 alpha 档
     *      （见 `nav/RouteTransitions.kt` 的 `routeMotionMs` 与它的单测）。
     */
    @Volatile
    var navMs = 180f

    /**
     * 周条横扫的**落点投影时长**（毫秒）—— B4-15② 业主点名要的 A/B 键之一。
     *
     * 语义：`projected = 位移 + 速度 × 本值`，再按格宽取整。物理上它就是"抬手后还会滑多久"。
     * 热调（不用重装，改完下一次抬手就生效 —— 它每帧读，不走观察器）：
     *   adb shell settings put global wz_fling_ms 110   # 默认（Android 样条 fling 的等效量级）
     *   adb shell settings put global wz_fling_ms 60    # 更"干脆"
     *   adb shell settings put global wz_fling_ms 200   # 更"飘"
     */
    @Volatile
    var flingProjectionMs = 110f

    /**
     * 抬手速度的**取窗宽度**（毫秒）—— B4-15② 第二个 A/B 键。
     *
     * 太小 ⇒ 样本不够（`<3` 直接判 0，按纯位置走）；太大 ⇒ 把"慢下来的那一段"也算进去、速度被平均掉。
     *   adb shell settings put global wz_strip_win_ms 80   # 默认
     *   adb shell settings put global wz_strip_win_ms 40   # 更"看最后一瞬"
     *   adb shell settings put global wz_strip_win_ms 160  # 更"看整段"
     */
    @Volatile
    var stripWindowMs = 80f

    /** 这两条计数是用来**证明观察器这条路在这台 ROM 上真的会响**的，不是装饰 */
    @Volatile
    var observerHits = 0L; private set

    @Volatile
    var pollHits = 0L; private set

    /** 有没有被热调过（用来决定要不要在界面上提示"参数已改"） */
    val isTuned: Boolean
        get() = barBlurDp != BAR_DEFAULT_DP || veilBlurDp != VEIL_DEFAULT_DP || lensDp != LENS_DEFAULT_DP

    /**
     * @return 这一次有没有真的改变。
     *
     * 必须返回它：调用方只在"变了"的时候才推一次重组。早先无条件每 400ms 自增一个
     * 状态位，等于整棵页面树每秒重画 2.5 次 —— 手指按下去正好赶上输入链被重建就会
     * 丢触控（他 2026-10-14 报的"app 内频繁丢触控，要点底栏才好"），而且玻璃的采样层
     * 会被反复重挂，实时折射也跟着失效。
     */
    fun refresh(context: Context): Boolean {
        var changed = false
        try {
            val r = context.contentResolver
            val bar = Settings.Global.getFloat(r, "hzr_glass_bar_blur", barBlurDp)
            val veil = Settings.Global.getFloat(r, "hzr_glass_veil_blur", veilBlurDp)
            val lens = Settings.Global.getFloat(r, "hzr_glass_lens", lensDp)
            val root = Settings.Global.getFloat(r, "hzr_glass_root", rootLayer)
            val off = Settings.Global.getFloat(r, "hzr_glass_off", recorderOff)
            val depth = Settings.Global.getFloat(r, "hzr_glass_depth", depthLens)
            val pill = Settings.Global.getFloat(r, "hzr_glass_pill_blur", pillBlurDp)
            val btn = Settings.Global.getFloat(r, "hzr_glass_btn_live", btnLive)
            val dbg = Settings.Global.getInt(r, "hzr_debug", 0) == 1
            val nav = Settings.Global.getFloat(r, "wz_nav_ms", navMs)
            val fling = Settings.Global.getFloat(r, "wz_fling_ms", flingProjectionMs)
            val stripWin = Settings.Global.getFloat(r, "wz_strip_win_ms", stripWindowMs)
            changed = bar != barBlurDp || veil != veilBlurDp || lens != lensDp ||
                root != rootLayer || off != recorderOff || depth != depthLens ||
                pill != pillBlurDp ||
                btn != btnLive || dbg != debugLog || nav != navMs ||
                fling != flingProjectionMs || stripWin != stripWindowMs
            barBlurDp = bar
            veilBlurDp = veil
            lensDp = lens
            rootLayer = root
            recorderOff = off
            depthLens = depth
            pillBlurDp = pill
            btnLive = btn
            debugLog = dbg
            navMs = nav
            flingProjectionMs = fling
            stripWindowMs = stripWin
        } catch (e: Throwable) {
            // 读不到就用当前值，热调只是排障工具，不该影响界面
        }
        return changed
    }

    /**
     * 用 ContentObserver 取代"每 800ms 去 provider 里捞 7 个键"。
     *
     * 为什么要换（2026-10-16 收尾轮量出来的）：轮询版是 7 次 binder 往返 / 0.8 秒 ≈
     * **每分钟 525 次**，而我们只是"他偶尔用 adb 改一个调试参数"。事件驱动之后
     * 静置时一条 binder 都不发，改参数的响应还更快（provider 直接通知）。
     *
     * onChange 是在 binder 线程上回调的：读 provider 放这儿没问题（本来就不是主线程），
     * 但"推重组"这个动作必须回主线程，所以回调里显式 post 到 main looper。
     *
     * @return 用完必须 close()（调用方放 DisposableEffect 的 onDispose）
     */
    fun startObserving(context: Context, onChanged: () -> Unit): AutoCloseable {
        val resolver = context.contentResolver
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                if (refresh(context)) {
                    observerHits++
                    main.post(onChanged)
                }
            }
        }
        runCatching {
            resolver.registerContentObserver(Settings.Global.CONTENT_URI, true, observer)
        }.onFailure {
            android.util.Log.w("GlassTuning", "注册 Settings 观察器失败，只能退回轮询", it)
        }
        return AutoCloseable { runCatching { resolver.unregisterContentObserver(observer) } }
    }

    /** 兜底轮询用：调用方每跳一次计数，方便在 logcat 里看清到底是谁在驱动 */
    fun notePoll() {
        pollHits++
    }
}
