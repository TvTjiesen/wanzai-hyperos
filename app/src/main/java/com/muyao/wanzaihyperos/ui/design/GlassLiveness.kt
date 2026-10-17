package com.muyao.wanzaihyperos.ui.design

/**
 * **玻璃的失效防線**（規範 §6.4）。
 *
 * ## 這一節在本專案目前是「預備」而非「正在用」
 *
 * 我們走的是規範 §0.2 的**路線 B**：不裝 `miuix-blur`（它要 minSdk 33，我們是 24），
 * 所以全部用純 Compose 繪製（半透明容器 + 漸層 + 描邊）。
 *
 * 路線 B **沒有**規範 §6.4 列的那些失效模式 ——
 * 那些失效全部來自「實時採樣 + 模糊」：
 *
 * | 規範裡的失效 | 為什麼路線 B 不會有 |
 * |---|---|
 * | 切頁時玻璃變實心黑/白 | 沒有采樣，就沒有「採到空層退回鋪底色」 |
 * | 用著用著實時模糊沒了 | 沒有共享採樣層，也沒有 onDetach 清座標 |
 * | 低版本機器崩/不畫 | 沒有 shader 路徑 |
 *
 * 所以這一節現在的價值是：**萬一哪天抬到 minSdk 33 要上真玻璃，防線已經在了。**
 * 那是一個平台決策（規範 §0.2 的 A 路），要業主/Lead 拍板。
 *
 * ## ★ 為什麼先寫「哨兵驗證」而不是先寫防線
 *
 * 規範裡有一句很重的話：
 *
 * > ⚠️「坐標非空」不等於「裡面有內容」—— 我們拿它當存活判據被騙過一次。
 * > 要做**哨兵驗證**：往那層寫一個獨特值，看有沒有別人真的讀到/改掉它，
 * > 而不是「沒報錯就算好」。
 *
 * 「沒報錯就算好」正是本專案反覆栽跟頭的地方
 *（B18 那次「沒有查到考試安排」也是同一類：解析成功、內容為空、界面撒謊）。
 *
 * 所以這裡**先**提供一個可測的哨兵機制，讓「這層到底活著沒」有正面證據。
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
