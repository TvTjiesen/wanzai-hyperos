package com.muyao.wanzaihyperos.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 門戶消息的 `openurl` 要能直接交給 WebView。
 *
 * 為什麼需要這條判據：`docs/` 與抓包裡**一條真實 openurl 樣例都沒有**
 * （`grep -rho '"openurl":"[^"]*"' build/ docs/` = 0 命中），所以「學校給的是
 * 絕對地址還是站內相對路徑」這件事沒有證據。沒有證據就只能兩種都吃下 ——
 * 只認其中一種，另一種寫法的消息點下去是空白頁，而空白頁看起來像「App 壞了」。
 */
class PortalOpenUrlTest {

    @Test
    fun `绝对地址原样交出去`() {
        assertEquals(
            "https://x.hzcu.edu.cn/m/1",
            HzcuEndpoints.portalOpenUrl("https://x.hzcu.edu.cn/m/1"),
        )
        assertEquals(
            "http://iappdd.hzcu.edu.cn/m/2",
            HzcuEndpoints.portalOpenUrl("http://iappdd.hzcu.edu.cn/m/2"),
        )
    }

    @Test
    fun `站内相对路径拼到门户根上`() {
        assertEquals(
            HzcuEndpoints.PORTAL_BASE_URL + "caimsmvc/detail?id=7",
            HzcuEndpoints.portalOpenUrl("/caimsmvc/detail?id=7"),
        )
        // 不带前导斜线的写法同样成立，且不会拼出双斜线。
        assertEquals(
            HzcuEndpoints.PORTAL_BASE_URL + "caimsmvc/detail?id=7",
            HzcuEndpoints.portalOpenUrl("caimsmvc/detail?id=7"),
        )
        assertEquals(
            HzcuEndpoints.PORTAL_BASE_URL + "caimsmvc/detail?id=7",
            HzcuEndpoints.portalOpenUrl("///caimsmvc/detail?id=7"),
        )
    }

    @Test
    fun `没有地址时不给地址`() {
        // 門戶自己對這種行只 toast「未獲取到消息通知地址」，不給點。
        assertNull(HzcuEndpoints.portalOpenUrl(null))
        assertNull(HzcuEndpoints.portalOpenUrl(""))
        assertNull(HzcuEndpoints.portalOpenUrl("   "))
    }

    @Test
    fun `两端空白不算地址的一部分`() {
        assertEquals(
            "https://x.hzcu.edu.cn/m/1",
            HzcuEndpoints.portalOpenUrl("  https://x.hzcu.edu.cn/m/1  "),
        )
    }
}
