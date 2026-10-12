package com.muyao.wanzaihyperos.nav

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 全应用路由表。
 *
 * 只有「真正需要压在栈上的整屏页面」才进这里 —— 四个 tab **不是**路由，
 * 它们由 [com.muyao.wanzaihyperos.ui.MainShell] 内部的一个 selectedTab 状态驱动。
 * 理由见 miuix-skill 的 miuix-nav.md：「不要把 tab 选中态从 NavDisplay 条目推导」，
 * 否则会出现两套选中态（chrome 一套、路由一套）。
 *
 * 全部 route key 都必须 `@Serializable`：`rememberNavBackStack` 靠它做进程重建恢复。
 */
@Serializable
sealed interface Route : NavKey {

    /** 登录页。未登录时的栈底。 */
    @Serializable
    data object Login : Route

    /** 主壳（底部四 tab）。登录成功后就停在这里，再按返回键即退出 App。 */
    @Serializable
    data object Main : Route

    /** 应用搜索页（压在主壳之上）。 */
    @Serializable
    data object Search : Route

    /**
     * WebView 外壳（压在主壳之上）。
     *
     * 只带展示所需的最小信息：应用 id 用于日志与将来换 tempcode，
     * title 用于顶栏，url 用于加载。**不带凭据** —— A1 不接学校接口，
     * 这里的 url 全部来自展示用示例数据。
     */
    @Serializable
    data class Web(
        val appId: Int,
        val title: String,
        val url: String,
    ) : Route

    /**
     * 我的课程（B1，原生）。
     *
     * **刻意不带任何参数** —— 课表是按当前登录会话取的，
     * 参数化（比如按学生 id）会诱导把识别码写进导航状态，
     * 而导航状态会被系统保存/恢复（`rememberNavBackStack`）。
     * 拿不到会话时页面自己显示「需要先登录」。
     */
    @Serializable
    data object Courses : Route

    /**
     * 我的事務（B3，原生）—— 待辦 / 我的申請 / 我的已辦。
     *
     * 帶一個 `bucket` 參數：首頁那三個計數卡各自對應一個清單，
     * 點哪張就該落到哪個 tab（否則使用者點「已辦」卻看到「待辦」，會以為壞了）。
     *
     * 存的是**枚舉名**（`String`）而不是枚舉本身：
     * 導航狀態會被序列化保存，用名字更穩 —— 枚舉改名時只是回退到默認 tab，
     * 而不是反序列化失敗讓整個返回棧崩掉。
     */
    @Serializable
    data class Busyness(val bucket: String = "TODO") : Route

    /**
     * B3 剩餘三項的 cjh52 原生頁：日常通知 / 我的考試 / 我的成長。
     *
     * 用**一個路由 + 一個枚舉名**而不是三個 data object：
     * 它們的形狀完全一樣（都是「一個 cjh52 原生頁 + 預熱閘門 + 網頁版出口」），
     * 拆三個會讓導航表、閘門接線、返回處理各寫三遍。
     *
     * 存的是**枚舉名**（`String`）而不是枚舉本身：導航狀態會被序列化保存，
     * 用名字更穩 —— 枚舉改名時只是回退到默認頁，而不是反序列化失敗讓整個返回棧崩掉。
     */
    @Serializable
    data class Cjh52Feature(val feature: String = "NOTICE") : Route


    /** B6：多來源待辦（按來源分組）。 */
    @Serializable
    data object TodoBoard : Route

    /**
     * 學在城院原生首頁（我的課 + 待交作業）。
     *
     * 帶 `tileUrl` 是為了「改用網頁版」能回退到**同一個**學在城院地址。
     */
    @Serializable
    data class TronHome(val tileUrl: String = "") : Route

    /**
     * 學在城院 → 簽到（看 + 簽）。
     *
     * 帶 `tileUrl` 同樣為了回退網頁版。
     */
    @Serializable
    data class Rollcall(val tileUrl: String = "") : Route

    /**
     * 學在城院 → 某門課的作業（**只讀**）。
     *
     * 帶課程 id 與課名：作業接口是課程內的，必須知道看哪門課。
     * `courseName` 只為了標題顯示（免得使用者不知道自己在哪門課裡）。
     */
    @Serializable
    data class Homework(val courseId: Int = 0, val courseName: String = "", val tileUrl: String = "") : Route
}
