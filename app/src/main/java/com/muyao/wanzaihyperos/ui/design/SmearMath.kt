package com.muyao.wanzaihyperos.ui.design

import kotlin.math.abs

/**
 * 底欄滑塊**形變量**的一維純函數。
 *
 * ## 為什麼做成純函數（而不是寫在 Composable 裡）
 *
 * 這塊**看截圖判斷不了**：「有沒有回到 0」「方向對不對」在靜態圖上都看不出來，
 * 而它一旦不回到 0，底欄就會**永久歪著**——而且是「用著用著才發現」的那種。
 *
 * 抽成純函數後，這些都能單測（見 `SmearMathTest`）。
 *
 * ## 兩個來源相加（這是「液體感」的核心）
 *
 * ```
 * smear = 手指累計位移 / (單格寬 × 2.6)  +  滑塊領先量 / (格數-1) × 0.6
 * ```
 *
 * - **第一項**給「正在被拽」的手感；
 * - **第二項**是「滑塊已經跑到容器前面」的領先量 ——
 *   切頁時它也非零，於是**整條欄會朝新 tab 傾一下再落回**。
 *   只有第一項的話，純點擊切換完全沒有形變，看起來是「死」的。
 *
 * ## 3% / 1.5% 是被砍下來的
 *
 * 原本是 7.5% / 3.5%，業主反饋「晃太多」後砍到現在的值。
 * 規範 §6.3：「寧可小」。
 *
 * ## 為什麼支點在「受力那一側的對面」
 *
 * 拉長的是**朝向手指去的那條邊**。支點若放錯邊，整條欄會朝反方向甩，
 * 看起來像「被推開」而不是「被拉過去」。
 */
object SmearMath {

    /**
     * 拖多少像素算「滿形變」。
     *
     * 值越大 = 同樣的拖拽量形變越小。2.6 是實測舒服區間
     *（1.0 太敏感、4.0 幾乎看不出來）。
     */
    const val DRAG_FULL_PX_FACTOR = 2.6f

    /** 領先量在形變裡的權重。 */
    const val LEAD_WEIGHT = 0.6f

    /** 合成後的上下限。 */
    const val LIMIT = 1.0f

    /** 橫向拉長上限（**3%**，從 7.5% 砍下來的）。 */
    const val MAX_SCALE_X = 0.03f

    /** 縱向壓扁上限（**1.5%**，從 3.5% 砍下來的）。 */
    const val MAX_SCALE_Y = 0.015f

    /**
     * 合成形變量。
     *
     * @param rawDragPx 手指累計的橫向位移（帶符號，鬆手後會被動畫拉回 0）
     * @param tabWidthPx 一個 tab 的寬度
     * @param leadUnits 滑塊目標位置減當前位置（單位數，帶符號）
     * @param tabsCount tab 數量，≤1 時沒有「領先」可言
     */
    fun smear(rawDragPx: Float, tabWidthPx: Float, leadUnits: Float, tabsCount: Int): Float {
        // 還沒量出寬度 = 這一幀根本沒畫，直接給 0：
        // 否則領先項會先動一下，等布局完成那一幀看起來會「跳形」。
        if (tabWidthPx <= 0f) return 0f
        val drag = (rawDragPx / (tabWidthPx * DRAG_FULL_PX_FACTOR)).coerceIn(-1f, 1f)
        val lead = if (tabsCount > 1) {
            (leadUnits / (tabsCount - 1)).coerceIn(-1f, 1f)
        } else {
            0f
        }
        return (drag + lead * LEAD_WEIGHT).coerceIn(-LIMIT, LIMIT)
    }

    /** 橫向拉伸係數（最多胖 3%）。 */
    fun scaleX(s: Float): Float = 1f + MAX_SCALE_X * abs(s)

    /** 縱向壓扁係數（最多瘦 1.5%）。 */
    fun scaleY(s: Float): Float = 1f - MAX_SCALE_Y * abs(s)

    /**
     * 支點 x（0 = 左邊緣，1 = 右邊緣）。
     *
     * 放在**背對手**指的那一側，於是「被拉長的是朝手指去的那條邊」。
     * s = 0 時兩邊等價，取 0 不影響畫面。
     */
    fun pivotX(s: Float): Float = if (s >= 0f) 0f else 1f

    /**
     * 兩端越界的橡皮筋位移（dp）。
     *
     * 規範 §6.3：「兩端越界的橡皮筋只給 **2dp**
     *（原來 4dp 被判『整條欄晃太多』）」。
     *
     * @param overscrollPx 累計越界量（正 = 往右越界）
     * @param density 螢幕密度（px/dp）
     */
    fun edgeRubberBandDp(overscrollPx: Float, density: Float): Float {
        if (density <= 0f) return 0f
        val px = overscrollPx * EDGE_RUBBER_RATIO
        return (px / density).coerceIn(-MAX_EDGE_DP, MAX_EDGE_DP)
    }

    /** 越界位移的上限（dp）—— 2dp，不是 4dp。 */
    const val MAX_EDGE_DP = 2f

    /** 越界量到實際位移的縮減比（越界 10px 只走 3px，手感才「有阻力」）。 */
    const val EDGE_RUBBER_RATIO = 0.3f
}
