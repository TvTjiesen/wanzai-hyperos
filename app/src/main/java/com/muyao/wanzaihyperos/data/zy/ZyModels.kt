package com.muyao.wanzaihyperos.data.zy

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 智雲課堂（智慧雲課堂 CMC）的**資料模型**。
 *
 * ## 契約來源（真機探測 2026-10-14，`docs/B19-WALKTHROUGH.md`）
 *
 * 登入後首頁那張週課表：
 * ```
 * GET /courseapi/v2/schedule/get-week-schedules
 *     ?user_id=<id>&tenant_id=21&start_at=YYYY-MM-DD&end_at=YYYY-MM-DD
 * → {"success":true,"result":{"code":200,"msg":"","list":[
 *      {"day":"2026-10-12","course":[ {…} ]}, … 7 天 ]}}
 * ```
 *
 * ### ★ 認證：**只用 cookie 就夠**（已實測）
 *
 * 這個站的認證有兩條路：
 * - `_token` cookie（995 字元 JWT），以及一批 `.hzcu.edu.cn` 域 cookie；
 * - 有些介面也可以把 token 當 **URL 參數**（`…&token=<JWT>`）。
 *
 * 實測**不帶 `token` 參數、只帶 cookie** 就能拿到資料（`success:true`）。
 * 所以我們走 cookie 那條 —— 不必自己去算/保管 token。
 *
 * ⚠️ 但 cookie 是**域 cookie**（`.hzcu.edu.cn`），
 * 而 [com.muyao.wanzaihyperos.net.HzcuCookieJar] 是**按 host 分桶**的
 * （見它的類註釋：刻意如此，為了不讓憑據跨系統漂移）。
 * 所以「用 OkHttp 帶 cookie 打 API」在這條鏈路上**行不通** ——
 * 域 cookie 不會被分桶的罐子正確帶上。
 *
 * → 因此本功能照 B18 教務系統的模式：**讓 WebView 持有會話**，
 *   資料在同源環境下用 `fetch` 取。這樣域 cookie 由 WebView 自己管，完全正確。
 */
data class ZyScheduleItem(
    /** 節次 id（`id`）：**用它開播放頁**。 */
    val id: String,
    /** 課程 id（`course_id`）。 */
    val courseId: String,
    /** 課程標題（`course_title`）。 */
    val title: String,
    /** 開始時間（`start_at`，**Unix 秒的字串**）。 */
    val startAt: String,
    /** 結束時間（`end_at`）。 */
    val endAt: String,
    /** 教室（`room_name`）。 */
    val room: String,
    /** 教師（`teacher_name`）。 */
    val teacher: String,
    /** 狀態碼（`status`）。實測 `6` = 已結束（可看回放）。 */
    val status: String,
    /** 狀態文字（`status_label`）。實測「回放」「未开始」等 **由服務端給**。 */
    val statusLabel: String,
    /** 有沒有直播／回放流（`is_live`，`"1"`/`"0"`）。 */
    val isLive: String,
    /** 所屬日期（`day`，`YYYY-MM-DD`）—— 由外層帶下來。 */
    val day: String,
) {
    /**
     * 能不能看回放。
     *
     * 判據用 `is_live == "1"`（服務端說有流），**不是**靠猜狀態碼語義。
     * 實測有回放的那幾筆 `is_live` 都是 `"1"`。
     */
    val hasReplay: Boolean get() = isLive == "1"

    /** 界面顯示的狀態文字：**直接用服務端給的**。 */
    val displayStatus: String get() = statusLabel.ifBlank { if (hasReplay) "可回放" else "—" }
}

/** 一週課表（7 天，每天一組課）。 */
data class ZyWeek(
    val days: List<ZyDay>,
) {
    val items: List<ZyScheduleItem> get() = days.flatMap { it.items }
    val isEmpty: Boolean get() = items.isEmpty()
}

data class ZyDay(val day: String, val items: List<ZyScheduleItem>)

object ZyScheduleParser {
    /**
     * 解析週課表。
     *
     * ⚠️ 信封有**兩層**（實測）：外層 `{success, result}`，
     * 內層 `{code, msg, list}`。`list` 才是 7 天。
     * 只認外層會拿到空清單 —— 這類「雙層信封」很容易漏。
     */
    fun parse(root: JsonObject?): ZyWeek {
        root ?: return ZyWeek(emptyList())
        val result = root["result"].asObjectOrNull() ?: return ZyWeek(emptyList())
        val list = result["list"].asArrayOrNull() ?: return ZyWeek(emptyList())
        val days = list.mapNotNull { dayEl ->
            val dayObj = dayEl.asObjectOrNull() ?: return@mapNotNull null
            val day = dayObj["day"].asStringOrNull().orEmpty()
            val courses = dayObj["course"].asArrayOrNull().orEmpty()
            val items = courses.mapNotNull { c -> parseItem(c.asObjectOrNull(), day) }
            ZyDay(day, items)
        }
        return ZyWeek(days)
    }

    private fun parseItem(obj: JsonObject?, day: String): ZyScheduleItem? {
        obj ?: return null
        val id = obj["id"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return ZyScheduleItem(
            id = id,
            courseId = obj["course_id"].asStringOrNull().orEmpty(),
            title = obj["course_title"].asStringOrNull().orEmpty(),
            startAt = obj["start_at"].asStringOrNull().orEmpty(),
            endAt = obj["end_at"].asStringOrNull().orEmpty(),
            room = obj["room_name"].asStringOrNull().orEmpty(),
            teacher = obj["teacher_name"].asStringOrNull().orEmpty(),
            status = obj["status"].asStringOrNull().orEmpty(),
            statusLabel = obj["status_label"].asStringOrNull().orEmpty(),
            isLive = obj["is_live"].asStringOrNull().orEmpty(),
            day = day,
        )
    }
}

/**
 * 一節課的**回放/直播**信息（點「查看回放」後才拿到）。
 *
 * ## 契約（真機探測）
 *
 * ```
 * GET /courseapi/v2/schedule/search-live-course-list
 *     ?all=1&show_all=1&show_delete=2&with_sub_data=1
 *     &course_id=<課>&sub_id=<節次>&tenant=21
 * → {"code":0,"msg":"获取直播课列表成功","total":1,"list":[ {…} ]}
 * ```
 *
 * 而**實際的影片檔**是播放器再自己去載的：
 * ```
 * https://resourcese.hzcu.edu.cn/play/default/2026/10/12/<hash>_1920_1080.mp4
 * ```
 *
 * ### ★★ 影片網址**不需要登入**（已實測）
 *
 * 我用 `curl` **不帶任何 cookie** 就能下載（`HTTP 206`、`content-type: video/mp4`、
 * 支援 `accept-ranges` 即拖動進度）。所以：
 * - 「跳到第三方瀏覽器播放」**技術上可行**（不會被要求重登）；
 * - 但**不是最優** —— 見 `docs/B19-WALKTHROUGH.md` 的方案比較。
 *
 * ### 為什麼不做成原生播放器
 *
 * 影片高達 **4.5 GB / 12436 秒（3.5 小時）**，且路徑**沒有簽名也沒有防盜鏈**。
 * 看起來可直連，但：
 * 1. 那麼大的檔要**邊下邊播**，自己寫播放器要處理緩衝、斷線重連、進度上報；
 * 2. 播放時它還會呼叫 `courseapi/v2/course/record-create`（記錄觀看）與
 *    `behavior/suspend`（暫停上報）——**那是學校用來認定學習進度的**；
 *    自己播而不上報，等於「看了等於沒看」。
 * 3. 而 `detail2` 頁本身還有 PPT 同步、AI 筆記、討論等一整套。
 *
 * → 所以把**播放頁**放在 WebView 裡（它自己會處理這一切），
 *   而**清單與信息原生**。這是收益/風險最平衡的切法。
 */
data class ZyLiveInfo(
    /** 節次 id（`id`）。 */
    val id: String,
    /** 標題（`title`）。 */
    val title: String,
    /** 封面（`thumb`）。 */
    val thumb: String,
    /** 開始時間（`start_at`，Unix 秒）。 */
    val startAt: String,
    /** 結束時間（`end_at`）。 */
    val endAt: String,
    /** 教師（`realname`）。 */
    val teacher: String,
    /** 狀態（`status`）。 */
    val status: String,
)

object ZyLiveParser {
    /** 取第一筆（這一節課的直播／回放）。 */
    fun parseFirst(root: JsonObject?): ZyLiveInfo? {
        root ?: return null
        val first = root["list"].asArrayOrNull()?.firstOrNull()?.asObjectOrNull() ?: return null
        val id = first["id"].asStringOrNull()
            ?: first["id"].asIntOrNull()?.toString()
            ?: return null
        return ZyLiveInfo(
            id = id,
            title = first["title"].asStringOrNull().orEmpty(),
            thumb = first["thumb"].asStringOrNull().orEmpty(),
            startAt = first["start_at"].asStringOrNull().orEmpty(),
            endAt = first["end_at"].asStringOrNull().orEmpty(),
            teacher = first["realname"].asStringOrNull().orEmpty(),
            status = first["status"].asStringOrNull().orEmpty(),
        )
    }
}
