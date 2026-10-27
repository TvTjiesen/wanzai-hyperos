package com.muyao.wanzaihyperos.ui.main

import androidx.annotation.DrawableRes
import com.muyao.wanzaihyperos.R

/**
 * **五個板塊**（業主指定的分法）。
 *
 * | 板塊 | 放什麼 |
 * |---|---|
 * | [Home] | 今日課程、簽到、待辦 |
 * | [Timetable] | 完整一週課表（可互動） |
 * | [Notify] | 消息與新聞（**分兩段**） |
 * | [Apps] | 已重構（原生）／未重構（網頁）**分開** |
 * | [Settings] | 帳號、外觀、關於 |
 *
 * ## 為什麼用 enum 而不是散落的常數
 *
 * 索引要與底欄的 `selectedIndex` 一一對應，而且**順序就是使用者看到的順序**。
 * 用 enum 讓「順序」與「名字」綁在一起，改順序時不會漏掉某處的硬編碼下標。
 */
enum class MainSection(
    val label: String,
    /**
     * v2 定稿板上是**線性**圖標（`fill:none;stroke:currentColor;stroke-width:1.65`），
     * Miuix 自帶那套是實心面性 —— 一比一复刻就只能自己落 drawable。
     *
     * 這五個 `ic_board_*` **不是手抄的**，是 `tools/board_icons_to_xml.py` 直接從
     * `wz-v2.html` 的 `<symbol id="i-home|i-cal|i-msg|i-grid|i-gear">` 生成出來的
     * （rect/circle 展開成 path，`filter="url(#soft)"` 那種投影在可 tint 圖標上沒有對應物，丟掉）。
     * 要改形狀就改板、重跑腳本，不要在 xml 里手描 —— 手描一次就再也對不上板了。
     * 上色交給底欄的 tint（選中 accent / 未選 faint），不另存一份顏色的圖。
     */
    @DrawableRes val iconRes: Int,
) {
    Home("首页", R.drawable.ic_board_home),
    Timetable("课表", R.drawable.ic_board_cal),
    Notify("消息", R.drawable.ic_board_msg),
    Apps("应用", R.drawable.ic_board_grid),
    Settings("设置", R.drawable.ic_board_gear),
}
