package com.muyao.wanzaihyperos.ui.main

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Weeks

/**
 * **五個板塊**（業主指定的分法）。
 *
 * | 板塊 | 放什麼 |
 * |---|---|
 * | [Home] | 今日課程、簽到、待辦 |
 * | [Timetable] | 完整一週課表（可互動） |
 * | [Notify] | 訊息與新聞（**分兩段**） |
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
    val icon: ImageVector,
) {
    Home("首頁", MiuixIcons.Home),
    Timetable("課程表", MiuixIcons.Weeks),
    Notify("訊息", MiuixIcons.Messages),
    Apps("應用", MiuixIcons.GridView),
    Settings("設定", MiuixIcons.Settings),
}
