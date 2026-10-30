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
     * 全局扫一扫（应用页搜索行右边那颗方形钮进来）。
     *
     * 它自己不处理任何内容：判完就转交（见 ui/tronclass/ScannerPage.kt）——
     * 网址开 WebView、签到码带去 [Rollcall]、其它老实说不是。
     *
     * 为什么单开一条路由而不是塞进 [Rollcall]：签到页的相机只认签到码，
     * 而用户手里那张码不一定是签到码（可能是活动报名页、通知详情）。
     */
    @Serializable
    data object Scanner : Route

    /** 关于 + 开源许可（B4-10）：从设置页「关于」那一行走进来。 */
    @Serializable
    data object About : Route

    /**
     * B4-06：设置页「账号」二级页（六源逐条）。
     * ⚠️ 必须 `@Serializable`：`rememberNavBackStack` 要靠它持久化栈，
     * 漏了这条 ⇒ 该页在栈上时进程重建恢复会失败（同文件其余 20 个 Route 都有）。
     */
    @Serializable
    data object AccountSources : Route

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
     *
     * ★ 第二條入口（2026-10-19）：首頁那顆「簽到」磁貼直接進這裡
     *（业主：「做成一個簽到的磁貼點進去可以掃碼或者其他簽到方式」）。
     * 學在城院外殼裡那個二級頁用的是**同一份** [com.muyao.wanzaihyperos.ui.tronclass.RollcallBody]。
     */
    @Serializable
    data class Rollcall(val tileUrl: String = "") : Route

    /**
     * 學在城院 → 某門課的作業清單。
     */
    @Serializable
    data class Homework(val courseId: Int = 0, val courseName: String = "", val tileUrl: String = "") : Route

    /**
     * 學在城院 → **一項作業的詳情 + 提交**（2026-10-19）。
     *
     * 四個參數都是**識別碼 + 顯示文字**，不含憑據（通則：憑據永不進導航狀態，
     * 見 [Zhgy] 的註釋 —— 導航狀態會被系統序列化保存/恢復）。
     *
     * 為什麼要帶 `homeworkId` 又還要在倉庫裡核對一次：
     * 從待辦進來的 id 是 `/api/todos` 的 `id`，**它是否等於作業 id 沒有證據**，
     * 而這個 id 會被拿去當提交目標。核對規則見
     * [com.muyao.wanzaihyperos.data.tronclass.LiveHomeworkDetailRepository.resolveIn]。
     */
    @Serializable
    data class HomeworkDetail(
        val courseId: Int = 0,
        val homeworkId: Int = 0,
        val courseName: String = "",
        val title: String = "",
        val tileUrl: String = "",
    ) : Route

    /**
     * B16：**愛講座**（CJH52）原生清單。**只讀**。
     *
     * 帶 `tileUrl` 是為了「改用網頁版」能回退到**同一個**愛講座地址
     *（它是門戶應用 id 54，`/lecture/lectureList`）。
     *
     * 為什麼不併進 [Cjh52Feature]：那個路由的頁面形狀是
     *「標題 + 一包 cjh52 資料」，而且**不分頁**；
     * 講座要分頁（實測 2541 場）且要顯示名額，形狀不同。
     * 硬併進去會讓那個 when 分支塞進兩套邏輯。
     */
    @Serializable
    data class Lectures(val tileUrl: String = "") : Route

    /**
     * B17：**智慧公寓**（`zhgy.hzcu.edu.cn`）。
     *
     * **不帶任何參數** —— 憑據（那段 `code`）存在加密會話裡，
     * 不該進導航狀態：導航狀態會被系統序列化保存/恢復，
     * 把憑據放進去等於讓它出現在可能被 dump 的地方。
     *
     * 沒設過憑據時，這一頁顯示輸入提示；設過就直接載入頁面。
     */
    @Serializable
    data object Zhgy : Route

    /**
     * B18：**教務系統**（`ijw.hzcu.edu.cn`，正方 ZFSoft）。
     *
     * **不帶參數** —— 帳密存在加密會話裡，不該進導航狀態
     *（導航狀態會被系統序列化保存/恢復，憑據放進去等於讓它出現在可能被 dump 的地方）。
     */
    @Serializable
    data object Jw : Route

    /**
     * B19：**智雲課堂**（`livemobileapp.hzcu.edu.cn`）。
     *
     * **不帶參數** —— 認證沿用既有的統一身份認證會話（cookie），
     * 不該進導航狀態。
     */
    @Serializable
    data object Zy : Route

    /**
     * B19：智雲課堂的**回放播放頁**。
     *
     * 這裡帶 `url` 是**刻意的、也是不得已的**：播放頁的網址由服務端在
     * 「點某一節課」時才給（不是固定模板）。
     *
     * ⚠️ 本專案的通則是「**憑據永不進導航狀態**」（見 [Zhgy] 的註釋：
     * 導航狀態會被系統序列化保存/恢復）。所以這裡先確認過：
     * 這個 URL **不含憑據** —— 實測它只帶 `course_id` / `sub_id` / `tenant_code`
     * 這些**識別碼**，認證在 cookie 裡。識別碼不是憑據，可以進導航狀態。
     *
     *（`Route.Cjh52Feature` 那條路曾被提醒同一件事：billlist/todos 的 URL
     *  可能**一次性**帶 SSO 憑據，那種一律不能顯示或點擊。）
     */
    @Serializable
    data class ZyReplay(val url: String, val title: String) : Route
}
