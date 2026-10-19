package com.muyao.wanzaihyperos.ui.design

/**
 * **玻璃的失效防線**（規範 §6.4）。
 *
 * ## 這一節現在**是真的在用了**
 *
 * 歷史上它寫於「不裝 `miuix-blur`（要 minSdk 33，我們是 24）」那段時間，
 * 當時是純 Compose 繪製，沒有實時採樣，所以這套防線只是**預備**。
 *
 * 2026-10-18 底欄重做時把它**接進了渲染路徑**：
 * [verdictOf] 由 `GlassBackdropHost` 每幀得出結論，經 `LocalGlassLiveness` 傳給
 * `GlassSurface`，`rememberGlassRenderMode` 據此決定開不开真折射。
 *
 * ## ★ 為什麼要有它（不是裝飾）
 *
 * 三條失效全部來自「實時採樣 + 模糊」：
 *
 * | 失效 | 表現 |
 * |---|---|
 * | 切頁/快速點擊時採到空層 | 玻璃變成實心黑或實心白，**而且不報錯** |
 * | 共享採樣層被離場頁清了座標 | 用著用著實時模糊永久消失 |
 * | 這台機的 GPU/ROM 不给 shader | 整塊不画或沒有高光 |
 *
 * 「沒報錯就算好」正是本專案反覆栽跟頭的地方
 *（B18 那次「沒有查到考試安排」也是同一類：解析成功、內容為空、界面撒謊）。
 */
object GlassLiveness {

    /**
     * 存活判據的**結果**。
     *
     * 分成三態而不是布林：`Unknown`（還沒驗）與 `Dead`（驗了、死了）
     * 在界面上該顯示完全不同的東西 ——
     * 混成一態就會出現「明明是沒驗過，卻顯示成壞了」。
     */
    sealed interface Verdict {
        /** 還沒驗證過（例如剛創建）。 */
        data object Unknown : Verdict

        /** 哨兵讀回來了，且值沒被別人動過 → 這一層確實活著。 */
        data object Alive : Verdict

        /**
         * 死了。
         *
         * @param reason 具體原因（給開發看；界面上只顯示「降級」）
         */
        data class Dead(val reason: String) : Verdict

        /** 實用判斷：只有 [Alive] 才允許開真玻璃。 */
        val canUseGlass: Boolean get() = this is Alive
    }

    /**
     * 哨兵值：寫進採樣層的那個「獨特值」。
     *
     * ## 為什麼要有它
     *
     * 「座標非空」只能證明**有人登記過**，不能證明**裡面真的有內容**。
     * 哨兵的做法是：寫一個已知值進去，過一會兒**讀回來比對**。
     *
     * - 讀得回、且等於寫入值 → 這一層是真的在流通；
     * - 讀不回、或值被改掉 → 這一層是死的（或有人在亂清）。
     *
     * 用 `Long` 而不是固定常量：固定值會被「其它地方也寫了同一個值」騙過。
     * 每次用**不同**的值，才能確認「讀到的是我剛寫的那個」。
     */
    fun newSentinel(seed: Long): Long = seed * 31L + 0x5DEECE66DL

    /**
     * 由「寫入值」與「讀回值」判定存活。
     *
     * @param written 剛寫進去的哨兵值
     * @param readBack 之後讀回來的值（null = 完全讀不到）
     */
    fun judge(written: Long, readBack: Long?): Verdict = when {
        readBack == null -> Verdict.Dead("哨兵讀不回來（這一層沒被繪製）")
        readBack != written -> Verdict.Dead("哨兵被別人改掉（written=$written, read=$readBack）")
        else -> Verdict.Alive
    }

    /**
     * **生產路徑用的判據**：這一層這一幀到底有没有真的被畫出來。
     *
     * ## 為什麼用尺寸而不是哨兵值
     *
     * [judge] 那種哨兵要能「寫進去再讀出來」才成立，而 Compose 的採樣層
     * 沒有給我們讀像素的通道（讀一次就是一次同步渲染阻塞，得不償失）。
     * 所以退一步取**仍然算正面證據**的量：這個 layer 的 `drawWithContent` 有没有被走過、
     * 走過時尺寸是不是非零。
     *
     * ⚠️ 這**不等於**「層裡有可看的內容」，只等於「這一幀确实画了东西」。
     * 首幀 0×0 必須判死 —— 否則領先項/形變會在布局完成前那一幀「跳形」。
     *
     * @param contentWidthPx 採樣層這一幀的寬（px）
     * @param contentHeightPx 採樣層這一幀的高（px）
     */
    fun verdictOf(contentWidthPx: Float, contentHeightPx: Float): Verdict =
        if (contentWidthPx > 0f && contentHeightPx > 0f) Verdict.Alive
        else Verdict.Dead("採樣層這一幀尺寸為 ${contentWidthPx}×${contentHeightPx}（根本沒畫）")

    /**
     * **降級描述**：玻璃不可用時，界面該用什麼。
     *
     * ## 為什麼要「降級」而不是「硬畫」
     *
     * 規範：「採樣為空時組件**不報錯**，直接鋪不透明底色」——
     * 也就是說，玻璃壞掉時界面**不會告訴你**，只是變成一大塊實心色。
     *
     * 所以寧可**主動**退成「實心淺色 + 描邊」：那至少是一個看得過去的外觀，
     * 而不是一塊突兀的黑/白。
     *
     * 這個函式回傳的參數由調用方套到容器上；它不自己畫，
     * 因為「怎麼畫」是每個頁面的佈局問題。
     */
    data class Fallback(
        /** 容器底色（不透明，避免透出後面的東西）。 */
        val containerColor: androidx.compose.ui.graphics.Color,
        /** 描邊色（區分同色相鄰兩層）。 */
        val strokeColor: androidx.compose.ui.graphics.Color,
        /** 描邊寬度（dp）。 */
        val strokeWidthDp: Float = 1f,
    )

    /**
     * 依深淺給出降級外觀。
     *
     * 用**主題色**而不是硬編碼黑白：這樣在淺色/深色/未來的自訂色板下
     * 都不會出現「一塊和環境不搭的實心色」。
     */
    fun fallbackFor(
        isDark: Boolean,
        surface: androidx.compose.ui.graphics.Color,
    ): Fallback = Fallback(
        containerColor = surface,
        strokeColor = AppColors.cardStroke(isDark),
    )
}
