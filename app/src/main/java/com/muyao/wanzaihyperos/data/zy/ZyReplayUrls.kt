package com.muyao.wanzaihyperos.data.zy

/**
 * 智雲課堂的**播放頁網址**。
 *
 * ## 契約（真機探測 2026-10-14）
 *
 * 點課程列表裡的「查看回放」後，SPA 導到：
 * ```
 * https://livemobileapp.hzcu.edu.cn/#/pages/detail/detail2
 *     ?course_id=<課>&sub_id=<節次>&tenant_code=21
 * ```
 *
 * ## ⚠️ 為什麼可以把它放進導航狀態（本專案通則禁止憑據進導航）
 *
 * 本專案的通則是「**憑據永不進導航狀態**」（見 `Route.Zhgy` 的註釋：
 * 導航狀態會被系統序列化保存/恢復，憑據放進去等於讓它出現在可能被 dump 的地方）。
 *
 * 所以這裡特地確認過這個 URL 帶什麼：只有 `course_id` / `sub_id` / `tenant_code`
 * 三個**識別碼** —— 認證在 cookie 裡，**不在 URL 上**。
 * 識別碼不是憑據（它們只是「哪一門課的哪一節」），因此可以進導航狀態。
 *
 * 對照組：CJH52 的 billlist/todos 那種 URL **會一次性帶 SSO 憑據**，
 * 那種本專案一律不顯示、不點擊（`docs/` 有記錄）。
 *
 * ## 為什麼要「拼」而不是「照抄」
 *
 * 這個網址在 SPA 裡是前端路由自己組的，服務端介面沒有直接回它
 *（回的是 `course_id` 與 `sub_id` 兩個欄位）。
 * 所以照它的規則拼 —— 規則本身是穩定的（`detail2` + 三個參數）。
 */
object ZyReplayUrls {

    /**
     * 某一節課的播放頁。
     *
     * @param courseId 課程 id（`course_id`）
     * @param subId 節次 id（`id`／`sub_id`）
     */
    fun detail(courseId: String, subId: String): String = buildString {
        append(ZyEngine.BASE)
        append("/#/pages/detail/detail2")
        append("?course_id=").append(courseId)
        append("&sub_id=").append(subId)
        append("&tenant_code=").append(ZyEngine.TENANT)
    }
}
