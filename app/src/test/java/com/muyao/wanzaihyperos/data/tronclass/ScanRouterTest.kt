package com.muyao.wanzaihyperos.data.tronclass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全局扫一扫的路由判据。
 *
 * 这几条钉住的是**顺序**：先判网址、再判签到码。
 * 反过来写的话，一个带 `~` 的普通链接会被 `RollcallQrCode.parse` 吃成签到码，
 * 用户点开链接却掉进签到页。
 */
class ScanRouterTest {

    @Test
    fun `网址原样开网页`() {
        assertEquals(
            ScanRouter.Target.Web("https://cjh52.hzcu.edu.cn/course/student"),
            ScanRouter.classify("https://cjh52.hzcu.edu.cn/course/student"),
        )
        assertEquals(
            ScanRouter.Target.Web("http://x.hzcu.edu.cn/a"),
            ScanRouter.classify("  http://x.hzcu.edu.cn/a  "),
        )
    }

    @Test
    fun `带波浪号的网址仍然是网址而不是签到码`() {
        val url = "https://example.com/~chen/share?a=1~2"
        assertEquals(ScanRouter.Target.Web(url), ScanRouter.classify(url))
    }

    @Test
    fun `真签到码带去签到页`() {
        // 真机扫到的形态（2026-10-29）：/j?p=0~<0x10>1g6b!3~<0x10>...
        val code = "/j?p=0~\u00105cmw!3~0123456789abcdef0123456789abcdef12345678!4~\u00101g6b"
        val target = ScanRouter.classify(code)
        assertTrue("签到码应判成 Rollcall，实际 $target", target is ScanRouter.Target.Rollcall)
        assertEquals(code, (target as ScanRouter.Target.Rollcall).raw)
    }

    @Test
    fun `其它二维码不算签到码`() {
        assertEquals(ScanRouter.Target.Unknown, ScanRouter.classify("WIFI:S:eduroam;T:WPA;P:x;;"))
        assertEquals(ScanRouter.Target.Unknown, ScanRouter.classify("{\"scan_code\":{\"scan_result\":\"\"}}"))
        assertEquals(ScanRouter.Target.Unknown, ScanRouter.classify(""))
        assertEquals(ScanRouter.Target.Unknown, ScanRouter.classify(null))
    }

    @Test
    fun `扫到的签到码只中转一次`() {
        ScanHandoff.clear()
        ScanHandoff.put("  /j?p=0~abc  ")
        assertEquals("/j?p=0~abc", ScanHandoff.take())
        assertNull("取走之后必须清空，否则下次进签到页会再挂一次", ScanHandoff.take())

        ScanHandoff.put("")
        assertNull("空串不当中转内容", ScanHandoff.take())
    }
}
