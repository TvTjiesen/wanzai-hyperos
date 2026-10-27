package com.muyao.wanzaihyperos.ui.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B4-05 的「一句实话」——它到底说不说、说的是哪一句。
 *
 * 真实事故（真机 2026-10-24 16:3x 长轮询 65 秒，24 次 uiautomator dump）：
 * 点「一卡通」后 WebView 真实落地地址是 `ca.hzcu.edu.cn`（学校 CAS 表单），
 * 而旧实现只把 `etcard.hzcu.edu.cn` / `libres.hzcu.edu.cn` 当学校域 ⇒
 * `onPageFinished` 走 else 分支 ⇒ 那句实话**一帧都没出现过**。
 * 图书馆是闪一下又消失：第一跳 libres 命中，随后跳到 CAS 又被关掉。
 *
 * 所以下面第一组用例就是那次事故的回放：CAS 落地页必须有话，
 * 而且「登过但过期」不能沿用「还没登录过」那句（那是假话）。
 */
class WebLoginHintTest {

    private val casLogin = "http://ca.hzcu.edu.cn/cas/login?service=http%3A%2F%2Fetcard.hzcu.edu.cn%3A8088%2F"
    private val etcardHome = "http://etcard.hzcu.edu.cn:8088/easytong_app/LoginH5DK?jumpMode=homeIndex"
    private val etcardInner = "http://etcard.hzcu.edu.cn:8088/easytong_app/card/home"
    private val libresHome = "https://libres.hzcu.edu.cn/"
    private val notice = "http://iappdd.hzcu.edu.cn/app/notice/detail?id=1"

    private val ticket = "route=1a2b3c; CASTGC=TGT-1234-abc; JSESSIONID=xyz"
    private val siteSession = "JSESSIONID=abc; etToken=deadbeef"

    // ---- 第一组：真机事故回放 ----------------------------------------------------

    @Test
    fun `一卡通落在 CAS 登录页时必须说实话（旧实现这里是静默的）`() {
        assertEquals(WebLoginHint.TEXT_NEVER, WebLoginHint.decide(casLogin, null, null))
    }

    @Test
    fun `图书馆从 libres 跳到 CAS 之后话不能消失`() {
        // 第一跳：图书馆自己的域，本站没有 cookie
        assertEquals(WebLoginHint.TEXT_NEVER, WebLoginHint.decide(libresHome, null, null))
        // 第二跳：被 CAS 接走 —— 旧实现在这一步把话收回去了
        assertEquals(WebLoginHint.TEXT_NEVER, WebLoginHint.decide(casLogin, null, null))
    }

    // ---- 第二组：什么时候不说 ----------------------------------------------------

    @Test
    fun `站点内部页只要本站会话还在就闭嘴`() {
        assertNull(WebLoginHint.decide(etcardInner, siteSession, ticket))
        assertNull(WebLoginHint.decide(etcardHome, siteSession, ticket))
    }

    @Test
    fun `跟登录无关的学校页面不提登录`() {
        assertNull(WebLoginHint.decide(notice, null, null))
        assertNull(WebLoginHint.decide(notice, siteSession, null))
        assertNull(WebLoginHint.decide("about:blank", null, null))
        assertNull(WebLoginHint.decide("", null, null))
    }

    // ---- 第三组：说的是哪一句 ----------------------------------------------------

    @Test
    fun `从没登过说"还没登录过" 登过但过期说"过期了"`() {
        assertEquals(WebLoginHint.TEXT_NEVER, WebLoginHint.decide(casLogin, null, null))
        assertEquals(WebLoginHint.TEXT_AGAIN, WebLoginHint.decide(casLogin, null, ticket))
        assertEquals(WebLoginHint.TEXT_AGAIN, WebLoginHint.decide(libresHome, null, ticket))
        // 有票据、本站没 cookie：仍然要说话，但说的是"过期"而不是"没登过"
        assertEquals(WebLoginHint.TEXT_AGAIN, WebLoginHint.decide(etcardHome, null, ticket))
    }

    @Test
    fun `票据判定认 cookie 名不认随便写的一段字`() {
        // 名字必须真的以 CASTGC= 出现；别家的 cookie 或只是提到这个名字都不算
        assertEquals(WebLoginHint.TEXT_NEVER, WebLoginHint.decide(casLogin, null, "CASTGCX=1; JSESSIONID=2"))
        assertEquals(WebLoginHint.TEXT_NEVER, WebLoginHint.decide(casLogin, null, "note=CASTGC"))
        assertEquals(WebLoginHint.TEXT_AGAIN, WebLoginHint.decide(casLogin, null, "CASTGC="))
    }

    // ---- 第四组：文案本身 --------------------------------------------------------

    @Test
    fun `两句都写清 App 不代输账密 且不出现"请重试"这种空话`() {
        for (t in listOf(WebLoginHint.TEXT_NEVER, WebLoginHint.TEXT_AGAIN)) {
            assertTrue("缺「不替你输入账号密码」：$t", t.contains("App 不替你输入账号密码"))
            assertTrue("不该出现空话「请重试」：$t", !t.contains("请重试"))
            assertTrue("不该出现空话「网络异常」：$t", !t.contains("网络异常"))
        }
        // 两句必须是两句，不能退化成同一句
        assertTrue(WebLoginHint.TEXT_NEVER != WebLoginHint.TEXT_AGAIN)
    }

    // ---- 第五组：域名判定 --------------------------------------------------------

    @Test
    fun `域名判定要区分站点域与 CAS 域 且不吃后缀伪装`() {
        assertTrue(WebLoginHint.isSchoolCasHost(etcardHome))
        assertTrue(WebLoginHint.isSchoolCasHost(libresHome))
        assertTrue(WebLoginHint.isCasLoginPage(casLogin))

        // 一卡通/图书馆的域不是 CAS 登录页，CAS 域也不是"站点域"——两套不能互相顶替
        assertTrue(!WebLoginHint.isCasLoginPage(etcardHome))
        assertTrue(!WebLoginHint.isSchoolCasHost(casLogin))

        // 后缀伪装与非 http 串不能混进来
        assertTrue(!WebLoginHint.isSchoolCasHost("http://etcard.hzcu.edu.cn.evil.com/"))
        assertTrue(!WebLoginHint.isCasLoginPage("http://ca.hzcu.edu.cn.evil.com/cas/login"))
        assertTrue(!WebLoginHint.isSchoolCasHost("not a url"))
        assertTrue(!WebLoginHint.isCasLoginPage(""))
    }
}
