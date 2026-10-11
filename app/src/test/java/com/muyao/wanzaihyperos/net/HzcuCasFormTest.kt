package com.muyao.wanzaihyperos.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CAS 表单解析。**不需要账号**就能验证，因为两套模板的字段集是实测记下来的。
 *
 * 这里最重要的一条是「移动模板没有 `validTime`」——
 * Flutter 蓝本因为要求字段一个不少，真机（移动 UA）**一次都没连上学校**。
 */
class HzcuCasFormTest {

    private val pageUri = "http://ca.hzcu.edu.cn/cas/login?service=https%3A%2F%2Fiappdd.hzcu.edu.cn%2F"

    /** 移动模板：真实的 App 走的就是这一套。**没有 validTime，多一个 randomStr**。 */
    private val mobileHtml = """
        <html><body>
        <form id="fm2" method="post" action="/cas/login;jsessionid=OTHER?service=x">
            <input name="phone" value="13800000000">
        </form>
        <form id="fm1" method="post"
              action="/cas/login;jsessionid=AAAA1111.1425?service=https%3A%2F%2Fiappdd.hzcu.edu.cn%2F">
            <input name="username" value="">
            <input name="password" value="">
            <input name="authType" value="0">
            <input name="lt" value="">
            <input name="execution" value="e1s1">
            <input name="_eventId" value="submit">
            <input name="randomStr" value="">
            <input type="submit" value="登录">
        </form>
        </body></html>
    """.trimIndent()

    /** 桌面模板：用桌面 UA 时才会拿到，多 `validTime`、少 `randomStr`。 */
    private val desktopHtml = """
        <html><body>
        <form id="fm1" method="post" action="/cas/login;jsessionid=BBBB2222.1425?service=x">
            <input name="username" value="">
            <input name="password" value="">
            <input name="authType" value="0">
            <input name="lt" value="">
            <input name="execution" value="e1s1">
            <input name="_eventId" value="submit">
            <input name="validTime" value="5">
        </form>
        </body></html>
    """.trimIndent()

    @Test
    fun `移动模板能解析且不要求 validTime`() {
        val form = HzcuCasForm.parse(mobileHtml)

        assertEquals("e1s1", form.execution.value)
        assertEquals("submit", form.eventId.value)
        assertEquals("0", form.authType.value)
        // ★ 移动模板没有 validTime：这里必须是 null，且**绝不能因此抛异常**。
        assertNull(form.validTime)
        assertEquals("", form.randomStr?.value)
    }

    @Test
    fun `桌面模板能解析且带 validTime`() {
        val form = HzcuCasForm.parse(desktopHtml)
        assertEquals("5", form.validTime?.value)
        assertNull("桌面模板不该有 randomStr", form.randomStr)
    }

    /**
     * ★ 只读 fm1：同页面里 fm2（手机验证码登录）的 `phone` 字段**不能**被收进来，
     * 否则提交体会多出一个浏览器不会发的字段。
     */
    @Test
    fun `只在 fm1 范围内收集字段`() {
        val form = HzcuCasForm.parse(mobileHtml)
        assertFalse("把 fm2 的字段也收进来了", form.has("phone"))
        assertTrue(form.has("username"))
    }

    @Test
    fun `action 原样保留 jsessionid`() {
        val form = HzcuCasForm.parse(mobileHtml)
        assertTrue(
            "丢了 ;jsessionid 就提交不回同一个会话",
            form.actionPath.contains(";jsessionid=AAAA1111.1425"),
        )
        assertTrue(form.actionPath.contains("service="))
    }

    /** 日志形态只能出现字段名，绝不能出现值。 */
    @Test
    fun `toString 只列字段名不列值`() {
        val form = HzcuCasForm.parse(mobileHtml)
        val text = form.toString()

        assertTrue(text.contains("username"))
        assertTrue(text.contains("randomStr"))
        // 真机上靠这一行区分「拿到的是桌面模板还是移动模板」。
        assertTrue(text.contains("fields=["))
        // action 里的会话 ID 不能出现。
        assertFalse("日志里出现了 jsessionid", text.contains("AAAA1111"))
    }

    @Test
    fun `提交地址会补齐 scheme 与 host 并丢掉取表单时的 query`() {
        val form = HzcuCasForm.parse(mobileHtml)
        val submit = form.submitUri(pageUri)

        assertTrue("实际是 $submit", submit.startsWith("http://ca.hzcu.edu.cn/cas/login"))
        assertTrue("jsessionid 必须保留", submit.contains(";jsessionid=AAAA1111.1425"))
        // pageUri 自带的 service 应被 action 自己的 query 取代，而不是拼接两份。
        assertEquals(1, Regex("service=").findAll(submit).count())
    }

    @Test
    fun `action 是绝对地址时直接使用`() {
        val html = mobileHtml.replace(
            "action=\"/cas/login;jsessionid=AAAA1111.1425?service=https%3A%2F%2Fiappdd.hzcu.edu.cn%2F\"",
            "action=\"https://ca.hzcu.edu.cn/cas/login?service=x\"",
        )
        val submit = HzcuCasForm.parse(html).submitUri(pageUri)
        assertEquals("https://ca.hzcu.edu.cn/cas/login?service=x", submit)
    }

    @Test
    fun `HTML 实体被还原`() {
        val html = mobileHtml.replace(
            "action=\"/cas/login;jsessionid=AAAA1111.1425?service=https%3A%2F%2Fiappdd.hzcu.edu.cn%2F\"",
            "action=\"/cas/login?service=a&amp;b=1\"",
        )
        val submit = HzcuCasForm.parse(html).submitUri(pageUri)
        assertTrue("实际是 $submit", submit.contains("service=a&b=1"))
        assertFalse("实体没有被还原", submit.contains("&amp;"))
    }

    @Test
    fun `缺必需字段时明确报出缺的是哪个`() {
        val html = mobileHtml.replace("<input name=\"execution\" value=\"e1s1\">", "")
        val error = runCatching { HzcuCasForm.parse(html) }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.Parse)
        // 给用户看的那句话要点名缺哪个字段（真机上抓瞎就是因为只报「结构不一致」）。
        assertTrue("错误里要说清缺哪个字段", error?.message!!.contains("execution"))
        // 已读到的字段列表放在 details 里（属于诊断信息，不该塞进给用户看的短文案）。
        assertTrue(
            "details 要列出已读到的字段以便排查",
            (error as HzcuException).details!!.contains("username"),
        )
    }

    /** 页面改版（例如被短信/验证码流程接管）时要给一句能看懂的话，而不是空指针。 */
    @Test
    fun `找不到 fm1 时给出明确原因`() {
        val error = runCatching { HzcuCasForm.parse("<html><body>没有表单</body></html>") }
            .exceptionOrNull()
        assertTrue("实际是 $error", error is HzcuException.Parse)
        assertTrue(error?.message!!.contains("fm1"))
    }

    @Test
    fun `表单没闭合时明确报错`() {
        val error = runCatching {
            HzcuCasForm.parse("<form id=\"fm1\" action=\"/x\"><input name=\"username\" value=\"\">")
        }.exceptionOrNull()
        assertTrue("实际是 $error", error is HzcuException.Parse)
        assertTrue(error?.message!!.contains("闭合"))
    }

    // ---------------------------------------------------------------------------
    // 提交内容
    // ---------------------------------------------------------------------------

    @Test
    fun `提交字段 = 页面全部字段 + 账号 + 密文密码`() {
        val form = HzcuCasForm.parse(mobileHtml)
        val cipher = HzcuCasCrypto.encryptPassword("Passw0rd!")

        val fields = form.fields(account = "2021001", encryptedPassword = cipher)

        assertEquals("2021001", fields["username"])
        assertEquals(cipher, fields["password"])
        // 页面给的其他字段原样带上（学校以后加隐藏域也不用改代码）。
        assertEquals("e1s1", fields["execution"])
        assertEquals("submit", fields["_eventId"])
        assertEquals("0", fields["authType"])
        assertTrue(fields.containsKey("lt"))
        assertTrue(fields.containsKey("randomStr"))
    }

    /**
     * ★ 红线守卫：有人改 UI 时忘了调加密，明文密码会走上 **HTTP 明文链路**。
     * 这里必须当场炸，而不是「尽力发送」。
     */
    @Test
    fun `拒绝把明文密码当密文提交`() {
        val form = HzcuCasForm.parse(mobileHtml)
        val error = runCatching {
            form.fields(account = "2021001", encryptedPassword = "Passw0rd!")
        }.exceptionOrNull()

        assertTrue("实际是 $error", error is HzcuException.Parse)
        assertTrue(error?.message!!.contains("Base64"))
    }

    @Test
    fun `带等号填充的合法密文不会被误判成明文`() {
        // 蓝本第一版的 bug：正则漏掉 `=`，把合法密文判成明文。
        val form = HzcuCasForm.parse(mobileHtml)
        val cipher = HzcuCasCrypto.encryptPassword("abc") // 以 == 结尾
        assertTrue(cipher.endsWith("=="))

        val fields = form.fields(account = "2021001", encryptedPassword = cipher)
        assertEquals(cipher, fields["password"])
    }
}
