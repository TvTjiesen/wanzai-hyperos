package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 「我的事務」的一條記錄（待辦 / 申請 / 已辦）。
 *
 * ### 為什麼三種狀態共用一個模型
 *
 * 真機取證（`app/build/b3-live-todo-probe.txt`）顯示三個接口的**回應形狀高度重疊**：
 *
 * | 接口 | 獨有欄位 |
 * |------|---------|
 * | `listmybill`（待辦） | — |
 * | `listmyprebill`（申請） | `billownername`、`waterstate`、`url`、`wcreatedate` |
 * | `listmyfinishbill`（已辦） | `handletime` |
 *
 * 共同的骨幹是 `billid / billname / billdes / createtime / state`。
 * **刻意不拆成三個 data class**：它們描述的是同一件事（一條事務記錄），
 * 差別只是「現在處於哪個階段」。拆三個會讓列表頁、點擊、排序各寫三遍，
 * 而三份幾乎一樣的代碼裡，只要有一份漏掉後來新增的欄位就會**靜默顯示不全**。
 *
 * 獨有欄位用可空值收進來，需要時再用。
 */
data class PortalBill(
    /** 事務 id。 */
    val id: String,
    /** 標題（`billname`）。 */
    val name: String,
    /** 描述（`billdes`）。可能很長，UI 自己截斷。 */
    val description: String,
    /** 發起人 user id（`billownerid`）。**是識別碼，不顯示**。 */
    val ownerId: String,
    /** 發起人姓名（`billownername`，僅申請/已辦有）。 */
    val ownerName: String,
    /** 建立時間（`createtime`，`yyyy-MM-dd HH:mm:ss` 形態）。 */
    val createdAt: String,
    /**
     * 狀態碼（`state`）。
     *
     * ⚠️ **不同接口的取值含義不同**，且門戶自己是用 `type` 分派的
     * （`getFinishBill()` 裡：`1→申請中 / 2→駁回 / 3→已處理`）。
     * 所以**不要把 state 直接當文案**，必須先知道自己在哪個清單。
     */
    val state: Int?,
    /** 處理時間（`handletime`，僅已辦有）。 */
    val handledAt: String?,
    /** 詳情頁地址（`url`，僅申請/已辦有）。**可能帶一次性憑據，禁止記日誌。** */
    val detailUrl: String?,
) {
    /**
     * 這條事務是不是「有問題」的狀態（駁回）。
     *
     * 只按門戶 `getFinishBill()` 裡那一套判（2 = 駁回）。
     * 認不出來的 state 一律當「正常」—— **不猜**，猜錯會讓使用者虛驚。
     */
    val isRejected: Boolean get() = state == STATE_REJECTED

    companion object {
        /** 門戶 `MyBusynessPage` 裡 `getFinishBill()` 的判據之一。 */
        const val STATE_REJECTED = 2
    }
}

/**
 * 三個清單。**UI 的 tab 就是它。**
 *
 * 抽成列舉是為了讓「接口路徑」「標籤」「state 含義」三件事**綁在一起**：
 * 分散寫的話，新增一個清單要改三個地方，漏一處就會出現
 * 「標籤寫待辦、其實打的是已辦接口」這種靜默錯誤。
 */
enum class PortalBillBucket(
    /** 門戶接口路徑。 */
    val path: String,
    /** 界面標籤。 */
    val label: String,
) {
    /** 我的待辦事項（要我處理的）。 */
    TODO("/api/app/billlist/listmybill", "我的待办"),

    /** 我的申請（我提交的，還在流轉）。 */
    /** ⚠️ 路徑是 `listmyprebill`（pre = 待結），不是 `listmyapplybill`。 */
    APPLIED("/api/app/billlist/listmyprebill", "我的申请"),

    /** 我的已辦（我處理過的）。 */
    /** ⚠️ 路徑是 `listmyfinishbill`。 */
    FINISHED("/api/app/billlist/listmyfinishbill", "我的已办"),
}

/**
 * 把 `rows[]` 映射成 [PortalBill]。
 *
 * **宽进严出**（与 A2 的其它解析器同一策略）：
 * - 缺 `billid` 的條目**丟掉**（沒有 id 就無法去重、無法點擊）；
 * - 其它欄位缺就給空字串 / null，**不拋異常** ——
 *   學校的欄位會變，缺一個欄位不該讓整個列表空白。
 */
object PortalBillParser {

    fun parse(rows: List<JsonObject>): List<PortalBill> = rows.mapNotNull { parseOne(it) }

    private fun parseOne(obj: JsonObject): PortalBill? {
        val id = obj["billid"].asStringOrNull() ?: return null
        return PortalBill(
            id = id,
            name = obj["billname"].asStringOrNull().orEmpty(),
            description = obj["billdes"].asStringOrNull().orEmpty(),
            ownerId = obj["billownerid"].asStringOrNull().orEmpty(),
            ownerName = obj["billownername"].asStringOrNull().orEmpty(),
            createdAt = obj["createtime"].asStringOrNull().orEmpty(),
            state = obj["state"].asIntOrNull(),
            handledAt = obj["handletime"].asStringOrNull(),
            detailUrl = obj["url"].asStringOrNull(),
        )
    }
}
