package com.muyao.wanzaihyperos.data.jw

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 教務系統（正方 ZFSoft V-9）的**通用分頁回應**。
 *
 * ## 契約（真機探測 2026-10-13，`docs/B18-JW-PROBE.txt`）
 *
 * ```
 * POST /jwglxt/<功能路徑>?doType=query&gnmkdm=<功能碼>
 *      Content-Type: application/x-www-form-urlencoded
 *      X-Requested-With: XMLHttpRequest
 *      body: xnm=&xqm=&_search=false&nd=<毫秒>&queryModel.showCount=50
 *            &queryModel.currentPage=1&queryModel.sortName=&queryModel.sortOrder=asc
 * → { currentPage, currentResult, entityOrField, items:[…], limit, offset,
 *     pageNo, pageSize, showCount, sortName, sortOrder, sorts,
 *     totalCount, totalPage, totalResult }
 * ```
 *
 * ### ★ 為什麼要做成通用的
 *
 * 實測「考試信息」與「學生成績」兩支的回應**信封完全相同**
 *（`items` + `totalCount` + 那一堆分頁欄位），差別只在 `items[]` 裡的欄位。
 * 所以分頁/信封的解析**寫一次**，各功能只寫自己的欄位映射。
 *
 * ### ⚠️ `totalCount` 與 `totalResult` 可能不一致
 *
 * 實測考試信息 `totalCount: 22`、`totalResult: 22` 相符，
 * 但這個站的同類系統出現過兩者不一致的情形。
 * 所以**取較大值**當「總筆數」的參考，而翻頁仍以「本次回傳數 < 頁大小」為準
 *（與全專案一致，見 `ListMeta.hasMore` 的註釋）。
 */
data class JwPage(
    val items: List<JsonObject>,
    /** 全部筆數（取值較大者）。取不到時為 null —— **未知不當成 0**。 */
    val total: Int?,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

object JwPageParser {
    fun parse(root: JsonObject?): JwPage {
        root ?: return JwPage(emptyList(), null)
        val items = root["items"].asArrayOrNull()
            ?.mapNotNull { it.asObjectOrNull() }
            .orEmpty()
        val a = root["totalCount"].asIntOrNull()
        val b = root["totalResult"].asIntOrNull()
        val total = listOfNotNull(a, b).maxOrNull()
        return JwPage(items, total)
    }
}

/**
 * 教務系統的**功能定義**。
 *
 * ## ★ 這些值全部逐字抄自它自己的選單 onclick
 *
 * 真機實測（不是猜的）：
 * ```
 * clickMenu('N358105','/kwgl/kscx_cxXsksxxIndex.html','考试信息查询',…)
 * clickMenu('N305005','/cjcx/cjcx_cxDgXscj.html','学生成绩查询',…)
 * clickMenu('N253508','/kbcx/xskbcx_cxXskbcxIndex.html','个人课表查询',…)
 * clickMenu('N105515','/xsxy/xsxyqk_cxXsxyqkIndex.html?echarts=1','学生学业情况查询',…)
 * ```
 *
 * ⚠️ 前綴 `/jwglxt` 是選單 JS 拼上去的，所以路徑要自己接。
 *
 * ## 為什麼用 enum 而不是散落的常數
 *
 * 「功能碼（gnmkdm）／路徑／標題」三者必須**一起**正確才打得通 ——
 * 用 enum 讓它們綁在一起，不可能出現「改了路徑忘了改碼」這種錯。
 */
enum class JwFeature(
    /** 功能碼，對應 `gnmkdm`。 */
    val code: String,
    /** 功能頁路徑（不含 `/jwglxt` 前綴）。 */
    val path: String,
    /** 界面標題。 */
    val title: String,
    /** 這支有沒有 `doType=query` 的資料介面（沒有就只能靠頁面 HTML）。 */
    val hasDataApi: Boolean = true,
) {
    /** 考試信息查詢。實測 22 筆。 */
    EXAM("N358105", "/kwgl/kscx_cxXsksxxIndex.html", "考试信息"),

    /** 學生成績查詢。實測 46 KB。 */
    SCORE("N305005", "/cjcx/cjcx_cxDgXscj.html", "学生成绩"),

    /**
     * 個人課表查詢。實測 27 KB。
     *
     * ⚠️ 這支**沒有** `doType=query` 的資料介面（實測只回 4 B `null`）——
     * 它的內容直接畫在頁面 HTML 裡，所以 [dataUrl] 對它不適用。
     *
     * 但頁面自己的 JS（`xskbcx.js` 的 `cxKbContent`）另有一支具名介面：
     * `xskbcx_cxXsgrkb.html?gnmkdm=N253508`，POST `xnm`/`xqm`
     *（見 `JwTimetableRepository.jsonUrl` / `formBody`）。2026-11-23 起課表走那支，
     * 解析見 `JwTimetableParser.parseGrkb`；讀不到才退回解析 DOM。
     * 所以 `hasDataApi` 仍是 `false` —— 它指的是 `doType=query` 那支，不是全部介面。
     */
    TIMETABLE("N253508", "/kbcx/xskbcx_cxXskbcxIndex.html", "个人课表", hasDataApi = false),
    ;

    /** 帶 `/jwglxt` 前綴的完整路徑。 */
    val fullPath: String get() = "/jwglxt$path"

    /** 帶 `gnmkdm` 與 `layout=default` 的頁面網址（照它自己的 `openWin` 規則）。 */
    fun pageUrl(base: String): String = "$base$fullPath?gnmkdm=$code&layout=default"

    /** 資料介面網址（`doType=query`）。 */
    fun dataUrl(base: String): String = "$base$fullPath?doType=query&gnmkdm=$code"
}
