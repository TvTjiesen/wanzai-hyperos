package com.muyao.wanzaihyperos.data.tronclass

/**
 * 全局「扫一扫」扫到东西之后**该去哪儿**（纯函数，可单测）。
 *
 * ## 为什么需要它
 *
 * 以前全工程只有一条进扫码的路：签到页顶部那颗按钮（`RollcallStationPage`）。
 * 要扫码得先「去签到」——可用户手里的码不一定是签到码：
 * 学校的通知、活动的报名页、教务的通知，也常常是一张二维码。
 * 所以扫一扫要能自己判断扫到的是什么，而不是让用户先去猜。
 *
 * ## 三种去处，多一种都不给
 *
 * | 扫到的内容 | 去处 |
 * |---|---|
 * | `http(s)://…` | 直接开 WebView |
 * | 学在城院的签到码（能被 [RollcallQrCode.parse] 解出编号或凭据） | 带去签到页，**提交仍由他按** |
 * | 其它 | 老实说「不是签到码也不是链接」 |
 *
 * 判据顺序不能反：签到码是 `/j?p=…` 这种**路径**，不会被误判成网址；
 * 但反过来先判签到码的话，一个恰好含 `~` 的网址会被误认成签到码
 *（`RollcallQrCode.parse` 的第三个入口就是「含 ~」），
 * 那样用户点开一条普通链接却进了签到页。
 */
object ScanRouter {

    /** 扫到之后要做的事。 */
    sealed interface Target {
        /** 签到码：原文交给签到页（它自己再解一次，取码里的 data）。 */
        data class Rollcall(val raw: String) : Target

        /** 网页地址：直接开。 */
        data class Web(val url: String) : Target

        /** 既不是签到码也不是链接。 */
        data object Unknown : Target
    }

    fun classify(raw: String?): Target {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Target.Unknown
        // 先判网址：RollcallQrCode.parse 会把「含 ~」的串当签到码，
        // 而网址里带 ~ 并不罕见（用户主页路径）。
        if (text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
        ) {
            return Target.Web(text)
        }
        return if (RollcallQrCode.parse(text) != null) Target.Rollcall(text) else Target.Unknown
    }
}

/**
 * 扫到的签到码原文从「扫一扫」交到「签到页」的**一次性中转**。
 *
 * ## 为什么不放进路由参数
 *
 * 本專案的通则：**凭据永不进导航状态**（见 `nav/Routes.kt` 里 [Route.Zhgy] 的注释）——
 * 导航状态会被系统序列化保存/恢复，凭据放进去等于让它出现在可能被 dump 的地方。
 * 签到码里那段 `data` 就是提交用的凭据，所以只能这样递：**只在本进程内存里放一次**。
 *
 * ## 一次性
 *
 * [take] 取走即清空。留着的话，用户下次从首页磁贴自己进签到页，
 * 会被上一次扫到的码再挂一次选择卡 —— 他早就交过了，再看到会以为没交上。
 *
 * ⚠️ 只在主线程用（两条调用都在 UI 事件里），不加锁。
 */
object ScanHandoff {

    private var slot: String? = null

    fun put(raw: String) {
        slot = raw.trim().takeIf { it.isNotEmpty() }
    }

    /** 取走并清空；没有就 null。 */
    fun take(): String? {
        val value = slot
        slot = null
        return value
    }

    /** 放弃这次中转（例如用户中途退出扫一扫）。 */
    fun clear() {
        slot = null
    }
}
