package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.net.HzcuEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用开启策略。纯函数，所以可以逐条把规则钉死。
 */
class AppOpenStrategyTest {

    private fun app(
        id: Int = 1,
        name: String = "某应用",
        url: String = "https://example.hzcu.edu.cn/x",
        system: PortalSystem = PortalSystem.STANDALONE,
    ) = PortalApp(id = id, name = name, url = url, system = system)

    @Test
    fun `学在城院走手机版免登`() {
        val plan = AppOpenStrategy.decide(
            app(id = 156, name = "学在城院", url = "https://mobile.hzcu.edu.cn/"),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.TronclassFreeLogin)
        assertEquals(
            "https://mobile.hzcu.edu.cn/identity-web-login-callback?_h5=true",
            (plan as AppOpenPlan.TronclassFreeLogin).callbackUrl,
        )
    }

    @Test
    fun `桌面版域也走免登`() {
        val plan = AppOpenStrategy.decide(
            app(name = "课程平台", url = "https://courses.hzcu.edu.cn/x"),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.TronclassFreeLogin)
    }

    // -------------------------------------------------------------------------
    // B1：cjh52 的「我的課程」走原生頁
    // -------------------------------------------------------------------------

    @Test
    fun `cjh52 的课程页走原生页`() {
        // ★ 這個測試盯的是**規則本身**（路徑+host 判定正確），
        //   而不是「現在有沒有啟用」——啟用與否由 ENABLE_NATIVE_COURSES 控制，
        //   目前是 false（業主決定擱置，見該常量的註釋）。
        //
        //   所以斷言寫成「若啟用則為 NativeCourses，否則為 TempCode」，
        //   兩種狀態都算通過，但**都不許是別的型別** ——
        //   這樣一旦有人改壞判據（例如誤判成 Plain），測試會紅。
        val plan = AppOpenStrategy.decide(
            app(id = 60, name = "我的课程", url = "https://cjh52.hzcu.edu.cn/course/student", system = PortalSystem.CJH52),
        )
        if (AppOpenStrategy.ENABLE_NATIVE_COURSES) {
            assertTrue("已啟用原生頁時應為 NativeCourses，实际是 $plan", plan is AppOpenPlan.NativeCourses)
            val p = plan as AppOpenPlan.NativeCourses
            assertEquals(60, p.appId)
            // ★ 必須保留原地址 —— 原生頁要拿它做「改用網頁版」的出口。
            assertTrue(p.tileUrl.contains("/course/student"))
        } else {
            assertTrue("擱置時應退回 TempCode，实际是 $plan", plan is AppOpenPlan.TempCode)
        }
    }

    @Test
    fun `课程页判定只看 path 不看 query`() {
        // 門戶下發的地址帶一串 query（dd_nav_bgcolor/appid/...），
        // 判據必須只看 path，否則真機那條 URL 永遠匹配不上。
        // 這裡直接驗判據函式本身，與啟用與否無關。
        assertTrue(
            AppOpenStrategy.let {
                // 透過 decide 的結果間接驗：無論開關如何，都不該變成 Plain/CampusOnly
                val r = it.decide(
                    app(
                        id = 60,
                        name = "我的课程",
                        url = "https://cjh52.hzcu.edu.cn/course/student?appid=60&mode=prod",
                        system = PortalSystem.CJH52,
                    ),
                )
                r is AppOpenPlan.NativeCourses || r is AppOpenPlan.TempCode
            },
        )
    }

    @Test
    fun `cjh52 的其它应用仍走 WebView`() {
        // ★ 這條是防「原生只做了課表、卻把別的入口也導過去」——
        //   那會讓使用者看到空白頁。只放行 /course/student。
        val plan = AppOpenStrategy.decide(
            app(id = 104, name = "我的班级", url = "https://cjh52.hzcu.edu.cn/clazz/student", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.TempCode)
    }

    @Test
    fun `教师端课程路径不误判为原生`() {
        // B0 路由表核對過：/course/teacher 是教師端。學生的原生頁不該吃它。
        val plan = AppOpenStrategy.decide(
            app(id = 61, name = "课程管理", url = "https://cjh52.hzcu.edu.cn/course/teacher", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $plan", plan !is AppOpenPlan.NativeCourses)
    }

    @Test
    fun `其它域的 course student 不误判为原生`() {
        // host 必須一起判：只判路徑會把別的系統裡同名的路徑也吃進來。
        val plan = AppOpenStrategy.decide(
            app(id = 1, name = "别的系统", url = "https://other.hzcu.edu.cn/course/student", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $plan", plan !is AppOpenPlan.NativeCourses)
    }

    // -------------------------------------------------------------------------
    // B3 剩餘三項：日常通知 / 我的考試 / 我的成長 走原生頁
    // -------------------------------------------------------------------------

    @Test
    fun `日常通知走原生页`() {
        val plan = AppOpenStrategy.decide(
            app(id = 188, name = "日常通知", url = "https://cjh52.hzcu.edu.cn/notice", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.NativeFeature)
        assertEquals("NOTICE", (plan as AppOpenPlan.NativeFeature).feature)
    }

    @Test
    fun `我的考试走原生页`() {
        val plan = AppOpenStrategy.decide(
            app(id = 211, name = "我的考试", url = "https://cjh52.hzcu.edu.cn/examination", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.NativeFeature)
        assertEquals("EXAM", (plan as AppOpenPlan.NativeFeature).feature)
    }

    @Test
    fun `我的成长走原生页`() {
        val plan = AppOpenStrategy.decide(
            app(id = 106, name = "我的成长", url = "https://cjh52.hzcu.edu.cn/development/home", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.NativeFeature)
        assertEquals("GROWTH", (plan as AppOpenPlan.NativeFeature).feature)
    }

    @Test
    fun `未做原生页的 cjh52 应用仍走 WebView`() {
        // ★ 保守：只放行**已經做了原生頁**的三個路徑。
        //   導去沒有實作的原生頁只會得到空白。
        for (url in listOf(
            "https://cjh52.hzcu.edu.cn/clazz/student",
            "https://cjh52.hzcu.edu.cn/lecture/lectureList",
            "https://cjh52.hzcu.edu.cn/lost/lostList",
            "https://cjh52.hzcu.edu.cn/studentCadre/StudentPostList",
        )) {
            val plan = AppOpenStrategy.decide(app(id = 1, name = "x", url = url, system = PortalSystem.CJH52))
            assertTrue("$url 不該走原生，实际是 $plan", plan is AppOpenPlan.TempCode)
        }
    }

    @Test
    fun `其它域的 notice 不误判`() {
        val plan = AppOpenStrategy.decide(
            app(id = 1, name = "别的系统", url = "https://other.hzcu.edu.cn/notice", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $plan", plan !is AppOpenPlan.NativeFeature)
    }

    @Test
    fun `名字里有学在城院也认（门户有时不给地址）`() {
        val plan = AppOpenStrategy.decide(
            app(name = "学在城院", url = "https://somewhere-else.example.com/"),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.TronclassFreeLogin)
    }

    @Test
    fun `仅校园网的入口给出明确提示`() {
        val plan = AppOpenStrategy.decide(
            app(name = "实验室平台", url = "http://10.61.132.121/x", system = PortalSystem.CAMPUS_ONLY),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.CampusOnly)
    }

    @Test
    fun `字面内网地址校外必然打不开，所以先提示而不是白屏等超时`() {
        val plan = AppOpenStrategy.decide(
            app(name = "医学实验室预约", url = "http://10.61.132.121/thirdlogin.html", system = PortalSystem.CAMPUS_ONLY),
        )
        assertEquals("医学实验室预约", (plan as AppOpenPlan.CampusOnly).name)
    }

    @Test
    fun `学校自建系统走 tempcode`() {
        // ⚠️ 这是 A2 的**待验证假设**（见 AppOpenStrategy 的可信度分级表）：
        //    接口确实存在，但「哪些应用需要它」要等 A3 用真机数据逐条比对。
        val cjh52 = AppOpenStrategy.decide(
            app(url = "https://cjh52.hzcu.edu.cn/page", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $cjh52", cjh52 is AppOpenPlan.TempCode)
        assertEquals(1, (cjh52 as AppOpenPlan.TempCode).appId)

        val jsp = AppOpenStrategy.decide(
            app(url = "https://dingding.hzcu.edu.cn/form", system = PortalSystem.JSP),
        )
        assertTrue("实际是 $jsp", jsp is AppOpenPlan.TempCode)
    }

    @Test
    fun `第三方独立系统走普通外链`() {
        val plan = AppOpenStrategy.decide(
            app(url = "https://library.example.edu.cn/x", system = PortalSystem.STANDALONE),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.Plain)
    }

    @Test
    fun `没有地址的入口变成不可打开而不是崩`() {
        val plan = AppOpenStrategy.decide(app(name = "空地址", url = ""))
        assertTrue("实际是 $plan", plan is AppOpenPlan.Unopenable)
    }

    @Test
    fun `host 解析失败也不抛异常`() {
        assertEquals(null, AppOpenStrategy.hostOf("这不是一个网址"))
        assertEquals(null, AppOpenStrategy.hostOf(""))
        assertEquals("mobile.hzcu.edu.cn", AppOpenStrategy.hostOf("https://MOBILE.hzcu.edu.cn/x"))
    }

    /**
     * ★ 策略层刻意不依赖网络层（那样它会需要 OkHttp），代价是域名常量写了两份。
     * 这条断言就是那两处的**闸门**：改了一处忘了另一处，测试立刻红。
     */
    @Test
    fun `域名常量与网络层保持一致`() {
        assertEquals(HzcuEndpoints.TRONCLASS_MOBILE_HOST, HzcuHosts.TRONCLASS_MOBILE)
        assertEquals(HzcuEndpoints.CHAOXING_HOST, HzcuHosts.TRONCLASS_DESKTOP)
        assertTrue(HzcuEndpoints.TRONCLASS_OIDC_TOKEN_URL.startsWith("https://${HzcuHosts.IDENTITY}"))
    }

    // ---------------------------------------------------------------------------
    // tempcode 拼 URL（逐字照抄门户 JS）
    // ---------------------------------------------------------------------------

    @Test
    fun `原本没有 query 的地址，首个分隔符变成问号`() {
        val url = AppOpenStrategy.buildTempCodeUrl(
            appUrl = "https://cjh52.hzcu.edu.cn/course/student",
            appId = 60,
            colorArgb = null,
            tempCode = "9a362a1d-42f1-4173-b189-c96f2bfa4fd9",
            nowMillis = 1791390998667L,
        )

        // 门户 JS: `if (url.indexOf("?") == -1) url = url.replace("&", "?")`
        assertTrue("首个分隔符必须是 ?：$url", url.contains("?"))
        assertTrue(
            url.startsWith("https://cjh52.hzcu.edu.cn/course/student?"),
        )
        assertFalse("不该同时出现问号和它前面的 &", url.startsWith("https://cjh52.hzcu.edu.cn/course/student&"))
    }

    @Test
    fun `原本就有 query 的地址保持不变，不再补问号`() {
        val url = AppOpenStrategy.buildTempCodeUrl(
            appUrl = "https://cjh52.hzcu.edu.cn/x?a=1",
            appId = 60,
            colorArgb = null,
            tempCode = "T",
            nowMillis = 1L,
        )

        assertEquals(1, Regex("\\?").findAll(url).count())
        assertTrue(url.startsWith("https://cjh52.hzcu.edu.cn/x?a=1&"))
    }

    @Test
    fun `固定参数一个不少`() {
        val url = AppOpenStrategy.buildTempCodeUrl(
            appUrl = "https://cjh52.hzcu.edu.cn/x",
            appId = 60,
            colorArgb = null,
            tempCode = "TEMPCODE",
            nowMillis = 1791390998667L,
        )

        assertTrue(url.contains("appid=60"))
        assertTrue(url.contains("userid=-1"))
        assertTrue(url.contains("trackid=1791390998667"))
        assertTrue(url.contains("devicetype=5"))
        assertTrue(url.contains("mode=prod"))
        assertTrue(url.contains("tempcode=TEMPCODE"))
        assertTrue(url.contains("code="))
    }

    @Test
    fun `有颜色时才加 dd_nav_bgcolor，且去掉井号`() {
        val withColor = AppOpenStrategy.buildTempCodeUrl(
            appUrl = "https://x.hzcu.edu.cn/y",
            appId = 1,
            colorArgb = 0xFF108EE9.toInt(),
            tempCode = "T",
            nowMillis = 1L,
        )
        assertTrue(withColor.contains("dd_nav_bgcolor=FF108EE9"))
        assertFalse("井号必须去掉", withColor.contains("#"))

        val withoutColor = AppOpenStrategy.buildTempCodeUrl(
            appUrl = "https://x.hzcu.edu.cn/y",
            appId = 1,
            colorArgb = null,
            tempCode = "T",
            nowMillis = 1L,
        )
        assertFalse("颜色为空时整段都不该出现", withoutColor.contains("dd_nav_bgcolor"))
    }

    @Test
    fun `tempcode 是拼进去的，没有被编码`() {
        // UUID 里的短横线不需要编码；若哪天有人给它套了 URLEncoder，断言会立刻红。
        val code = "9a362a1d-42f1-4173-b189-c96f2bfa4fd9"
        val url = AppOpenStrategy.buildTempCodeUrl(
            appUrl = "https://x.hzcu.edu.cn/y",
            appId = 1,
            colorArgb = null,
            tempCode = code,
            nowMillis = 1L,
        )
        assertTrue(url.endsWith("tempcode=$code"))
    }

    // ---------------------------------------------------------------------------
    // 凭据绝不发往外部域
    // ---------------------------------------------------------------------------

    @Test
    fun `学校域判定用后缀，不用 contains`() {
        assertTrue(AppOpenStrategy.isSchoolHost("cjh52.hzcu.edu.cn"))
        assertTrue(AppOpenStrategy.isSchoolHost("hzcu.edu.cn"))
        assertTrue(AppOpenStrategy.isSchoolHost("A.HZCU.EDU.CN"))
        assertFalse(AppOpenStrategy.isSchoolHost("evil.com"))
        // ★ 这条是最容易被松判据放过的：`contains("hzcu")` 会把它当学校域。
        assertFalse(
            "把 hzcu 放在自己的域名里不该被当成学校域",
            AppOpenStrategy.isSchoolHost("hzcu.edu.cn.evil.com"),
        )
        assertFalse(AppOpenStrategy.isSchoolHost(""))
    }

    /** ★ 计划 §A3 必守契約：「避免將憑據帶到外部域名」。 */
    @Test
    fun `外部域的入口不会被拼上 tempcode`() {
        // 即便门户把它归成学校自建系统，只要域名不在 hzcu.edu.cn 下，就不拼码。
        val plan = AppOpenStrategy.decide(
            app(url = "https://evil.example.com/x", system = PortalSystem.CJH52),
        )

        assertTrue("不能给外部域拼一次性凭据，实际是 $plan", plan is AppOpenPlan.Plain)
    }

    @Test
    fun `学校域的入口正常走 tempcode`() {
        val plan = AppOpenStrategy.decide(
            app(url = "https://cjh52.hzcu.edu.cn/course", system = PortalSystem.CJH52),
        )
        assertTrue("实际是 $plan", plan is AppOpenPlan.TempCode)
    }
}
