package com.muyao.wanzaihyperos.data.zy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B19：智雲課堂的解析測試。
 *
 * 輸入逐字抄自真機探測原文（`docs/B19-WALKTHROUGH.md`）。
 */
class ZyParserTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    // ── 週課表 ───────────────────────────────────────────────────────────
    @Test
    fun `解析雙層信封的週課表`() {
        // ⚠️ 這個站的信封有**兩層**：外層 {success, result}、內層 {code, msg, list}。
        //    只認外層會拿到空清單 —— 雙層信封很容易漏。
        val week = ZyScheduleParser.parse(
            obj(
                """{"success":true,"result":{"code":200,"msg":"","list":[
                     {"day":"2026-10-12","course":[
                       {"id":"2030670","course_id":"57246","course_title":"B/S架构应用开发",
                        "start_at":"1791782957","end_at":"1791795300","room_name":"城市学院理4-226",
                        "teacher_name":"赵磊","status":"6","status_label":"回放","is_live":"1"}
                     ]},
                     {"day":"2026-10-13","course":[]}
                   ]}}""",
            ),
        )
        assertEquals(2, week.days.size)
        assertEquals(1, week.items.size)
        assertEquals("B/S架构应用开发", week.items[0].title)
        assertEquals("2026-10-12", week.items[0].day)
    }

    @Test
    fun `只認外層信封時會拿到空清單（記錄這個坑）`() {
        val week = ZyScheduleParser.parse(obj("""{"success":true,"list":[]}"""))
        assertTrue("外層沒有 result 就沒有資料", week.isEmpty)
    }

    @Test
    fun `hasReplay 用 is_live 判断而不是猜狀態碼`() {
        val withLive = ZyScheduleParser.parse(
            obj("""{"result":{"list":[{"day":"d","course":[
                {"id":"1","course_title":"課","is_live":"1","status_label":"回放"}]}]}}"""),
        ).items[0]
        assertTrue(withLive.hasReplay)
        assertEquals("回放", withLive.displayStatus)

        val noLive = ZyScheduleParser.parse(
            obj("""{"result":{"list":[{"day":"d","course":[
                {"id":"2","course_title":"課","is_live":"0","status_label":"未开始"}]}]}}"""),
        ).items[0]
        assertFalse(noLive.hasReplay)
        // 狀態文字**用服務端給的**
        assertEquals("未开始", noLive.displayStatus)
    }

    @Test
    fun `缺 id 的課丟掉`() {
        val week = ZyScheduleParser.parse(
            obj("""{"result":{"list":[{"day":"d","course":[{"course_title":"無 id"}]}]}}"""),
        )
        assertTrue(week.isEmpty)
    }

    @Test
    fun `外層壞掉時回空週而不是崩掉`() {
        assertTrue(ZyScheduleParser.parse(null).isEmpty)
        assertTrue(ZyScheduleParser.parse(obj("""{"success":false}""")).isEmpty)
    }

    // ── 回放資訊 ─────────────────────────────────────────────────────────
    @Test
    fun `取回放資訊的第一筆`() {
        val info = ZyLiveParser.parseFirst(
            obj(
                """{"code":0,"msg":"获取直播课列表成功","total":1,"list":[
                     {"id":"57246","title":"B/S架构应用开发","thumb":"https://x/t.png",
                      "start_at":"1791782957","end_at":"1791795300","realname":"赵磊","status":"6"}
                   ]}""",
            ),
        )
        assertNotNull(info)
        assertEquals("57246", info!!.id)
        assertEquals("B/S架构应用开发", info.title)
        assertEquals("赵磊", info.teacher)
    }

    @Test
    fun `沒有回放時回 null`() {
        assertNull(ZyLiveParser.parseFirst(obj("""{"total":0,"list":[]}""")))
        assertNull(ZyLiveParser.parseFirst(null))
    }

    // ── 時間 ─────────────────────────────────────────────────────────────
    @Test
    fun `Unix 秒字串解析成時分`() {
        // 服務端給的是 Unix 秒的**字串**（不是 yyyy-MM-dd HH:mm:ss）——
        // 用錯格式會顯示成 1970 年。
        val range = timeRange("1791782957", "1791795300")
        assertNotNull(range)
        assertTrue("應為 HH:mm-HH:mm 形狀，實際 $range", Regex("""\d{2}:\d{2}-\d{2}:\d{2}""").matches(range!!))
    }

    @Test
    fun `時間解析不出來時回 null 而不是編一個`() {
        assertNull(timeRange("", ""))
        assertNull(timeRange("not-a-time", "also-bad"))
    }

    @Test
    fun `容忍毫秒時間戳`() {
        // 防禦性：> 10^11 視為毫秒
        assertNotNull(parseTime("1791782957000"))
    }

    // ── 播放網址 ─────────────────────────────────────────────────────────
    @Test
    fun `播放網址帶識別碼但不帶憑據`() {
        val url = ZyReplayUrls.detail(courseId = "57246", subId = "2030670")
        assertTrue(url.startsWith("https://livemobileapp.hzcu.edu.cn/#/pages/detail/detail2"))
        assertTrue(url.contains("course_id=57246"))
        assertTrue(url.contains("sub_id=2030670"))
        assertTrue(url.contains("tenant_code=21"))
        // ★ 這是可以進導航狀態的前提：網址上**沒有憑據**
        assertFalse("不該帶 token", url.contains("token="))
        assertFalse("不該帶 password", url.contains("password"))
    }

    // ── 失敗原因 ─────────────────────────────────────────────────────────
    @Test
    fun `需要登入的失敗才引導登入`() {
        assertTrue(ZyFailure.NotLoggedIn.needsLogin)
        assertFalse(ZyFailure.EngineNotReady.needsLogin)
        assertFalse(ZyFailure.NoResponse.needsLogin)
        assertFalse(ZyFailure.Other("x").needsLogin)
    }

    @Test
    fun `需要登入翻成 LoginExpired 其餘翻成 Parse`() {
        // ListState 的機制會把 LoginExpired 映射成 SessionExpired（引導登入），
        // 所以這個對應必須正確，否則使用者會被帶去一個沒用的地方。
        assertTrue(
            ZyFailure.NotLoggedIn.toHzcuException()
                is com.muyao.wanzaihyperos.net.HzcuException.LoginExpired,
        )
        assertTrue(
            ZyFailure.EngineNotReady.toHzcuException()
                is com.muyao.wanzaihyperos.net.HzcuException.Parse,
        )
    }
}
