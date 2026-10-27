package com.muyao.wanzaihyperos.data.account

import com.muyao.wanzaihyperos.session.Cjh52Session
import com.muyao.wanzaihyperos.session.FailingSecureStore
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.JwSession
import com.muyao.wanzaihyperos.session.PortalSession
import com.muyao.wanzaihyperos.session.TronclassSession
import com.muyao.wanzaihyperos.session.ZhgySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * v3 #8：设置页「需要登录的账号」清单。
 *
 * 这一组断言守的是四条**说真话**的底线：
 * 1. 存储读失败 = `未读到`，**不许显示成「未登录」**（[com.muyao.wanzaihyperos.session.SessionState] 的教训）；
 * 2. 没有时刻就留空/破折号，**不许拿当前时间凑一个**；
 * 3. 快照里任何字段都不许出现凭据原文；
 * 4. 副标题的字数预算由测试守着 —— 02:44 那一版五行全被 `maxLines = 2` 无声裁掉后半句，
 *    复审计又量到已登录态会折成孤行，两次都是「字写多了」，所以这次把它变成断言。
 */
class AccountSourcesTest {

    /** 合成身份号。**别换成业主真实学号** —— 这条清单一旦推上 GitHub 就是 PII 来源（#14）。 */
    private val fakeUserCode = "SYNTHETIC-ID"

    /** 固定时间戳：1760000000000 = 2025-10-09T08:53:20Z。 */
    private val fixedMillis = 1760000000000L
    private val utc = ZoneOffset.UTC
    private val day = 86_400_000L

    private fun sourcesOf(
        portal: PortalSession = PortalSession(InMemorySecureStore()),
        tronclass: TronclassSession = TronclassSession(InMemorySecureStore()),
        jw: JwSession = JwSession(InMemorySecureStore()),
        cjh52: Cjh52Session = Cjh52Session(InMemorySecureStore()),
        zhgy: ZhgySession = ZhgySession(InMemorySecureStore()),
    ) = snapshotAccountSources(portal, tronclass, jw, cjh52, zhgy)

    private fun List<AccountSource>.byId(id: String): AccountSource = first { it.id == id }

    /** 五个真会话（智云课堂那行是常量，不参与存储）。 */
    private val trackedIds = listOf("portal", "tronclass", "jw", "cjh52", "zhgy")

    @Test
    fun `六源齐全且顺序稳定`() {
        assertEquals(
            listOf("portal", "tronclass", "jw", "cjh52", "zhgy", "zy"),
            sourcesOf().map { it.id },
        )
    }

    /**
     * 副标题的**逐句**字数预算（判据在 v3 #8/#11 第 7 轮改写）。
     *
     * ⚠️ 旧判据「`provides` + ` · ` + `whereToLogin` 合计 ≤ 20 字」是**按 1.0 字号量的**。
     *   业主把系统字号调到 1.15，那一行实测只剩约 19 字 ⇒ 预算本身就会折，
     *   而折点是句中任意位置，末尾吊着一两个字的孤行（他这次报的就是这个）。
     *   二选一里"整串砍到 16 字"要砍掉的恰好是他要知道的那半句（账单、"只能自己粘链接"），
     *   所以选了另一条：**两句各占一行**（`SettingsSectionPage` 里用换行分），
     *   预算改为逐句 ≤ 12 —— 12 字在 1.3 档（一行约 17 字）下也不会自己再折。
     */
    @Test
    fun `副标题逐句预算守住`() {
        sourcesOf().forEach { s ->
            assertTrue(
                "${s.id} 的 provides ${s.provides.length} 字（${s.provides}）超预算：1.3 字号下会自己折出孤行",
                s.provides.length <= 12,
            )
            assertTrue(
                "${s.id} 的 whereToLogin ${s.whereToLogin.length} 字（${s.whereToLogin}）超预算",
                s.whereToLogin.length <= 12,
            )
        }
        // 结构也得钉住：拼回" · "一句，1.15 字号立刻又是孤行（这条是判据的另一半）。
        // B4-06 之后这六行住在「账号」二级页（`AccountsPage.kt`），判据跟着搬。
        val page = java.io.File(
            "src/main/java/com/muyao/wanzaihyperos/ui/main/pages/AccountsPage.kt",
        ).readText(Charsets.UTF_8)
        assertTrue(
            "账号那六行的副标题又不是各占一行了（换行点回到句中任意位置 = 孤行复活）",
            page.contains("""subtitle = "${'$'}{source.provides}\n${'$'}{source.whereToLogin}""""),
        )
    }

    /**
     * 这一组守的是 v3 #8 第 1 轮踩过的那条红线：脚注当时写「App 不代填凭据、不代登录」，
     * 而 `data/jw/JwEngine.kt` 的 `login()` 确实会拿存下的账密填表并按学校的提交钮。
     */
    @Test
    fun `脚注不许出现兑现不了的承诺`() {
        val note = accountFootnoteText(sourcesOf())
        assertFalse("教务那一源 App 确实会替他登录，这句是假话", note.contains("不代登录"))
        assertFalse(note.contains("不代填"))
        assertFalse("退出登录口目前界面上按不到，不许承诺清除", note.contains("退出登录"))
        assertTrue("必须写明教务会自动续登这件事", note.contains("教务例外"))
        assertTrue("智云课堂的会话读不到，得说清", note.contains("智云课堂的会话在网页里"))
    }

    @Test
    fun `智云课堂那一行不承诺能登录`() {
        val zy = sourcesOf().byId("zy")
        assertEquals(AccountStatus.InWebOnly, zy.status)
        assertFalse(
            "ZyPage 那颗「去智云课堂登录」的 onClick 只是返回，App 内没有通向它网页登录口的路",
            zy.whereToLogin.contains("登录"),
        )
    }

    @Test
    fun `空存储判未登录而不是未读到`() {
        val tracked = sourcesOf().filter { it.id in trackedIds }
        assertTrue("存储读成功且没有凭据 = 确实未登录", tracked.all { it.status == AccountStatus.LoggedOut })
        assertTrue("没有时刻就整行留空，不拿当前时间凑", tracked.all { it.credentialAtMillis == null })
        assertEquals("未登录", tracked.byId("portal").statusText)
        // 上一行已经写着「未登录」，第二行再写「尚无记录」就是同一件事说两遍。
        assertEquals("", tracked.byId("jw").timeText(nowMillis = fixedMillis, nowZone = utc))
    }

    @Test
    fun `存储读失败判未读到_绝不显示未登录`() {
        val sources = sourcesOf(
            portal = PortalSession(FailingSecureStore()),
            tronclass = TronclassSession(FailingSecureStore()),
            jw = JwSession(FailingSecureStore()),
            cjh52 = Cjh52Session(FailingSecureStore()),
            zhgy = ZhgySession(FailingSecureStore()),
        )
        val tracked = sources.filter { it.id in trackedIds }
        assertEquals(5, tracked.size)
        assertTrue(tracked.all { it.status == AccountStatus.Unknown })
        assertTrue(
            "「未知」被渲染成「未登录」就是替服务器撒谎（他明明登录着）",
            tracked.all { it.statusText == "未读到" },
        )
        // 智云课堂的会话压根不在加密存储里，读不读得动都还是「网页内」。
        assertEquals(AccountStatus.InWebOnly, sources.byId("zy").status)
    }

    @Test
    fun `有凭据时状态与时间都来自存储真实值`() {
        val portal = PortalSession(
            InMemorySecureStore(
                mapOf(
                    PortalSession.KEY_ACCESS_TOKEN to "a".repeat(PortalSession.ACCESS_TOKEN_LENGTH),
                    PortalSession.KEY_USER_CODE to "SYNTHETIC-ID",
                    PortalSession.KEY_SAVED_AT to fixedMillis.toString(),
                ),
            ),
        )
        val jw = JwSession(
            InMemorySecureStore(
                mapOf(
                    JwSession.KEY_ACCOUNT to "SYNTHETIC-ID",
                    JwSession.KEY_PASSWORD to "not-a-real-password",
                    JwSession.KEY_LAST_LOGIN to (fixedMillis + 60_000).toString(),
                ),
            ),
        )
        val zhgy = ZhgySession(InMemorySecureStore(mapOf(ZhgySession.KEY_CODE to "jwt-like-code")))
        val sources = sourcesOf(portal = portal, jw = jw, zhgy = zhgy)
        assertEquals(AccountStatus.LoggedIn, sources.byId("portal").status)
        // 行内只给时刻，语义标签（认证/登录）由脚注统一解释 —— 标签写进行内就把文字列挤到折行。
        assertEquals("08:53", sources.byId("portal").timeText(nowMillis = fixedMillis, nowZone = utc))
        assertEquals("08:54", sources.byId("jw").timeText(nowMillis = fixedMillis, nowZone = utc))
        // 智慧公寓存了码但没写时间键：有凭据 ≠ 有时间，这里必须给破折号而不是凑一个。
        assertEquals(AccountStatus.LoggedIn, sources.byId("zhgy").status)
        assertEquals("—", sources.byId("zhgy").timeText(nowMillis = fixedMillis, nowZone = utc))
    }

    @Test
    fun `CJH52 有码但未激活判成未激活而不是已登录`() {
        val cjh52 = Cjh52Session(
            InMemorySecureStore(
                mapOf(
                    Cjh52Session.KEY_TOKEN to "0f0f0f0f-1111-2222-3333-444444444444",
                    Cjh52Session.KEY_OBTAINED_AT to fixedMillis.toString(),
                ),
            ),
        )
        val before = sourcesOf(cjh52 = cjh52).byId("cjh52")
        assertEquals(AccountStatus.ObtainedNotActivated, before.status)
        assertEquals("未激活", before.statusText)
        assertEquals("取码", before.timeLabel)
        assertEquals("08:53", before.timeText(nowMillis = fixedMillis, nowZone = utc))

        cjh52.markActivated()
        assertEquals(
            "真实网页载过一次之后才算能用",
            AccountStatus.LoggedIn,
            sourcesOf(cjh52 = cjh52).byId("cjh52").status,
        )
    }

    @Test
    fun `相对时间分档`() {
        assertEquals("08:53", relativeMoment(fixedMillis, fixedMillis, utc))
        assertEquals("昨天", relativeMoment(fixedMillis, fixedMillis + day, utc))
        assertEquals("前天", relativeMoment(fixedMillis, fixedMillis + 2 * day, utc))
        assertEquals("4天前", relativeMoment(fixedMillis, fixedMillis + 4 * day, utc))
        // 超过一周回到日期，不写「很久以前」这种含糊话。
        assertEquals("10-09", relativeMoment(fixedMillis, fixedMillis + 30 * day, utc))
        // 时钟倒挂（存储里的时刻比现在晚）不许算成「-3天前」，退回到点。
        assertEquals("08:53", relativeMoment(fixedMillis, fixedMillis - 3 * day, utc))
    }

    @Test
    fun `没有独立入口的那一行不可点`() {
        assertEquals(AccountTarget.None, sourcesOf().byId("tronclass").target)
        // 其余五源都必须能点：跳过去只是到那一屏，账密还是他自己按。
        assertTrue(
            sourcesOf().filter { it.id != "tronclass" }.all { it.target != AccountTarget.None },
        )
    }

    @Test
    fun `每源的时刻语义不同不许统一写成登录`() {
        assertEquals(
            listOf("认证", "换票", "登录", "取码", "存码"),
            sourcesOf().mapNotNull { it.timeLabel },
        )
    }

    @Test
    fun `快照任何字段都不含凭据原文`() {
        val secret = "SECRETTOKEN-32chars-XXXXXXXXXXXXX"
        val sources = sourcesOf(
            portal = PortalSession(
                InMemorySecureStore(
                    mapOf(
                        PortalSession.KEY_ACCESS_TOKEN to secret,
                        PortalSession.KEY_SAVED_AT to fixedMillis.toString(),
                    ),
                ),
            ),
            jw = JwSession(
                InMemorySecureStore(
                    mapOf(
                        JwSession.KEY_ACCOUNT to "SYNTHETIC-ID",
                        JwSession.KEY_PASSWORD to secret,
                        JwSession.KEY_LAST_LOGIN to fixedMillis.toString(),
                    ),
                ),
            ),
        )
        sources.forEach { source ->
            val rendered = source.toString()
            assertFalse("快照里出现了凭据：${source.id}", rendered.contains(secret))
            assertFalse("学号也不该进快照（身份那行由设置页标题给）：${source.id}", rendered.contains(fakeUserCode))
        }
    }

    @Test
    fun `界面文案是简体中文`() {
        val all = sourcesOf().flatMap { listOf(it.name, it.provides, it.whereToLogin, it.statusText) }
        assertTrue("每条文案都得有中文", all.all { s -> s.any { c -> c.code in 0x4E00..0x9FFF } })
    }
}
