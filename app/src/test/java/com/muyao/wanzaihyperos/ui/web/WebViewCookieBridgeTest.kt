package com.muyao.wanzaihyperos.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CAS cookie 注入的閘門判定（**純函式部分**）。
 *
 * ### 為什麼只測判定不測注入
 *
 * 真正的 `CookieManager.setCookie` 是 Android 系統 API，JVM 單測裡跑不了
 * （要真機）。但**注入的風險全在「允許注入給誰」這個判定上** ——
 * 一旦判錯（把學校會話交給外部域），洩漏就發生了。
 * 所以把判定抽成純函式（[WebViewCookieBridge.isSchoolHost]）並在這裡釘死。
 */
class WebViewCookieBridgeTest {

    @Test
    fun `学校域才允许注入`() {
        // 真機上用到的那些
        assertTrue(WebViewCookieBridge.isSchoolHost("ca.hzcu.edu.cn"))
        assertTrue(WebViewCookieBridge.isSchoolHost("etcard.hzcu.edu.cn"))
        assertTrue(WebViewCookieBridge.isSchoolHost("libres.hzcu.edu.cn"))
        assertTrue(WebViewCookieBridge.isSchoolHost("cjh52.hzcu.edu.cn"))
        assertTrue(WebViewCookieBridge.isSchoolHost("iappdd.hzcu.edu.cn"))
        // 裸域本身
        assertTrue(WebViewCookieBridge.isSchoolHost("hzcu.edu.cn"))
        // 大小写不该影响判定
        assertTrue(WebViewCookieBridge.isSchoolHost("CA.HZCU.EDU.CN"))
    }

    @Test
    fun `外部域一律拒绝`() {
        assertFalse(WebViewCookieBridge.isSchoolHost("evil.com"))
        assertFalse(WebViewCookieBridge.isSchoolHost("example.com"))
        assertFalse(WebViewCookieBridge.isSchoolHost(""))
        assertFalse(WebViewCookieBridge.isSchoolHost("   "))
    }

    @Test
    fun `后缀欺骗域名被拒绝`() {
        // ★ 這條是最重要的：用 `contains("hzcu.edu.cn")` 會被這些騙過，
        //   而後果是**把學校的 CAS 會話交給攻擊者**。
        //   所以判據必須是「等於裸域 或 以 .hzcu.edu.cn 結尾」。
        assertFalse(WebViewCookieBridge.isSchoolHost("hzcu.edu.cn.evil.com"))
        assertFalse(WebViewCookieBridge.isSchoolHost("evil-hzcu.edu.cn"))
        assertFalse(WebViewCookieBridge.isSchoolHost("hzcu.edu.cn.attacker.net"))
        assertFalse(WebViewCookieBridge.isSchoolHost("notreallyhzcu.edu.cn"))
    }

    @Test
    fun `只允许注入统一认证那两个域`() {
        // ★ 這是紅線：注入的**目標域**清單必須只有統一認證本身。
        //   一旦這裡多了門戶或某個業務系統，就等於把 CAS 會話擴散出去。
        assertEquals(
            listOf("ca.hzcu.edu.cn", "identity.hzcu.edu.cn"),
            WebViewCookieBridge.SSO_HOSTS,
        )
    }
}
