package com.muyao.wanzaihyperos.data.portal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的事務」解析的離線驗證。
 *
 * ### 樣本從哪來
 *
 * 結構逐字取自真機取證（`app/build/b3-live-todo-probe.txt`），但**值全是編造的假資料**。
 * 真機探針只記了「欄位名 + 型別 + 長度」，這裡照那個形狀填進假值。
 *
 * ### 為什麼要專門盯「已辦」那條
 *
 * 三個接口的回應形狀**高度重疊但不完全相同**（已辦多一個 `handletime`、
 * 申請多 `billownername`/`waterstate`/`url`）。如果解析器只照「待辦」那條寫，
 * 申請與已辦的獨有欄位就會**靜默丟失** —— 不報錯，只是界面上少了一塊資訊。
 * 所以下面三條分別覆蓋。
 */
class PortalBillParserTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    /** 待辦（`listmybill`）—— 實測欄位最少的一條。 */
    private val todoJson = """
    {
      "billid": "1000001",
      "billname": "範例待辦事項",
      "billdes": "這是編造的描述文字，長度參照實測的 32 字元。",
      "billownerid": "30000001",
      "currusername": "張三",
      "currdeptname": "",
      "createtime": "2026-10-11 09:00:00",
      "curropertime": "2026-10-11 09:05:00",
      "state": 1
    }
    """.trimIndent()

    /** 我的申請（`listmyprebill`）—— 多發起人姓名、url。 */
    private val preBillJson = """
    {
      "billid": "1000002",
      "billname": "範例申請",
      "billdes": "編造的申請描述。",
      "billownerid": "30000001",
      "billownername": "李四",
      "createtime": "2026-10-10 08:00:00",
      "curruserid": "30000002",
      "curropertime": "2026-10-11 10:00:00",
      "watertid": "2000001",
      "url": "https://cjh52.hzcu.edu.cn/some/detail",
      "extfield1": null,
      "extfield2": null,
      "extfield3": null,
      "waterstate": 1,
      "state": 1,
      "wcreatedate": "2026-10-10 08:00:00"
    }
    """.trimIndent()

    /** 已辦（`listmyfinishbill`）—— 多處理時間。 */
    private val finishJson = """
    {
      "billid": "1000003",
      "billname": "範例已辦",
      "billdes": "編造的已辦描述。",
      "billownerid": "30000001",
      "billownername": "王五",
      "createtime": "2026-10-09 07:00:00",
      "curruserid": "30000002",
      "curropertime": "2026-10-11 11:00:00",
      "watertid": "2000002",
      "url": "https://cjh52.hzcu.edu.cn/some/detail2",
      "waterstate": 2,
      "state": 3,
      "handletime": "2026-10-11 12:00:00"
    }
    """.trimIndent()

    @Test
    fun `待办条目解析出核心字段`() {
        val bill = PortalBillParser.parse(listOf(obj(todoJson))).single()

        assertEquals("1000001", bill.id)
        assertEquals("範例待辦事項", bill.name)
        assertTrue(bill.description.startsWith("這是編造的"))
        assertEquals("2026-10-11 09:00:00", bill.createdAt)
        assertEquals(1, bill.state)
        // 待辦沒有這些欄位 → null / 空，**不是崩**
        assertNull(bill.handledAt)
        assertNull(bill.detailUrl)
        assertEquals("", bill.ownerName)
    }

    @Test
    fun `申请条目解析出 url 与发起人`() {
        val bill = PortalBillParser.parse(listOf(obj(preBillJson))).single()

        assertEquals("李四", bill.ownerName)
        assertEquals("https://cjh52.hzcu.edu.cn/some/detail", bill.detailUrl)
        assertNull("申請沒有處理時間", bill.handledAt)
    }

    @Test
    fun `已办条目解析出处理时间`() {
        val bill = PortalBillParser.parse(listOf(obj(finishJson))).single()

        assertEquals("2026-10-11 12:00:00", bill.handledAt)
        assertEquals("王五", bill.ownerName)
    }

    @Test
    fun `驳回复判据只看 state 等于 2`() {
        // 門戶 `getFinishBill()` 裡 2 = 駁回。認不出來的狀態一律當正常 —— **不猜**，
        // 猜錯會讓使用者虛驚（把「已處理」看成「被駁回」）。
        fun stateOf(s: Int) = PortalBillParser.parse(
            listOf(obj("""{"billid":"1","billname":"x","state":$s}""")),
        ).single().isRejected

        assertTrue(stateOf(2))
        assertEquals(false, stateOf(1))
        assertEquals(false, stateOf(3))
        // state 缺失 → 不是駁回
        val missing = PortalBillParser.parse(
            listOf(obj("""{"billid":"1","billname":"x"}""")),
        ).single()
        assertEquals(false, missing.isRejected)
        assertNull(missing.state)
    }

    @Test
    fun `缺 billid 的条目被丢掉`() {
        val json = """
        [
          {"billname":"没有 id"},
          {"billid":"ok1","billname":"正常"}
        ]
        """.trimIndent()
        val arr = Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonArray

        val bills = PortalBillParser.parse(arr.map { it as JsonObject })

        // 沒有 id 就無法去重、無法點擊，留著只會在列表裡多一條點不動的項。
        assertEquals(1, bills.size)
        assertEquals("ok1", bills.single().id)
    }

    @Test
    fun `缺其它字段不会让整条失败`() {
        val bill = PortalBillParser.parse(
            listOf(obj("""{"billid":"only-id"}""")),
        ).single()

        assertEquals("only-id", bill.id)
        assertEquals("", bill.name)
        assertEquals("", bill.description)
        assertEquals("", bill.createdAt)
    }

    @Test
    fun `三个清單的路徑与標籤都綁在枚举上`() {
        // 這條盯的是「路徑別寫錯」——門戶的路徑名不直觀
        // （申請是 `listmyprebill` 不是 `listmyapplybill`；
        //  已辦是 `listmyfinishbill` 不是 `listmydonebill`）。
        assertEquals("/api/app/billlist/listmybill", PortalBillBucket.TODO.path)
        assertEquals("/api/app/billlist/listmyprebill", PortalBillBucket.APPLIED.path)
        assertEquals("/api/app/billlist/listmyfinishbill", PortalBillBucket.FINISHED.path)
        // 標籤各不相同（否則 tab 上會出現兩個一樣的名字）
        assertEquals(3, PortalBillBucket.entries.map { it.label }.toSet().size)
    }
}
