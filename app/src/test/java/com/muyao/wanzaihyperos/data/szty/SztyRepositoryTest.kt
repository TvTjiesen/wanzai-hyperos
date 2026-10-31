package com.muyao.wanzaihyperos.data.szty

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.SztyClient
import com.muyao.wanzaihyperos.net.TestHttpServer
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.SztySession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 数智体育**取数链路**的测试：仓库 → 客户端 → 服务端（本地假服务端）。
 *
 * 这里只验三件在真机上无法反复复现、但一错就会骗人的事：
 * ①「哪一块没取到」必须写进 notes，不许静默少一块；
 * ② token 失效（-3 / -4）必须整体判失效，不许做成「网络不通」；
 * ③ 六个请求之间真的隔开了（服务端要求 1.6 秒，我们设 1.8 秒）。
 */
class SztyRepositoryTest {

    private lateinit var server: TestHttpServer

    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        server = TestHttpServer()
        now = 1_700_000_000_000L
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun session(token: String? = "SPORT_API_TEST", userId: String? = "80000"): SztySession {
        val store = InMemorySecureStore()
        if (token != null && userId != null) {
            store.write(SztySession.KEY_TOKEN, token)
            store.write(SztySession.KEY_USER_ID, userId)
        }
        return SztySession(store)
    }

    private fun repository(
        session: SztySession = session(),
        slept: MutableList<Long> = mutableListOf(),
    ): Pair<LiveSztyRepository, MutableList<Long>> {
        val client = SztyClient(
            baseUrl = server.baseUrl,
            // 单测不真等 1.8 秒；间隔逻辑本身用下面那条测 slept 来验。
            sleeper = { slept += it },
            clock = { now },
        )
        return LiveSztyRepository(client, session, clock = { now }) to slept
    }

    private val envelope = """{"statusCode":1,"message":null,"obj":"""

    private fun ok(obj: String): String = envelope + obj + "}"

    // ------------------------------------------------------------------
    // 正常一路
    // ------------------------------------------------------------------

    @Test
    fun `六条都成功时三块内容齐全且没有备注`() = runTest {
        val (repo, _) = repository()
        server.enqueue(200, ok("""[{"year":2026,"seasonName":"2026","grade":"缺项","allScore":"缺项","level":"","scoreData":[{"name":"50米","data":"","score":0}]}]"""))
        server.enqueue(200, ok("""[{"info":{"eventName":"总目标"},"effectiveDistance":168.28,"number":54},{"info":{"eventName":"跑步"},"effectiveDistance":16.78,"targetDistance":120,"number":4}]"""))
        server.enqueue(200, ok("""[{"termId":14},{"termId":15}]"""))
        server.enqueue(200, ok("""[{"classId":3062,"name":"匹克球","week":"星期五","jc":"3,4","address":"西网球场"}]"""))
        server.enqueue(200, ok("""[{"name":"2026-09-18 第1次课 考勤","signInType":"已签到"}]"""))
        server.enqueue(200, ok("""{"normal":4,"leave":2,"headStatistics":"已签到4次,请假2次"}"""))

        repo.refresh()
        val state = repo.state.value
        assertTrue("六条都成功时应当是 Content，实际 $state", state is ListState.Content)
        val snapshot = (state as ListState.Content).items.single()
        assertEquals(emptyList<String>(), snapshot.notes)
        assertEquals(1, snapshot.fitness.size)
        assertEquals("2026", snapshot.fitness[0].year)
        assertEquals(16.78, snapshot.run!!.runKm!!, 1e-9)
        assertEquals(168.28, snapshot.run!!.totalKm!!, 1e-9)
        assertEquals(3062, snapshot.peClass!!.classId)
        assertEquals(1, snapshot.attendance.size)
        assertEquals(4, snapshot.attendanceSummary!!.signed)
    }

    @Test
    fun `请求路径与 body 都是只读查询该有的样子`() = runTest {
        val (repo, _) = repository()
        repeat(6) { server.enqueue(200, ok("null")) }
        repo.refresh()
        val paths = server.requests.map { it.path }
        assertEquals(
            listOf(
                "/tyapi/mobile/health/getMyGrade",
                "/tyapi/mobile/exerciseRecord/getSemesterExerciseList",
                "/tyapi/mobile/exerciseRecord/getAssessmentYears",
            ),
            paths.take(3),
        )
        // ★ 只读红线：不许出现任何写类端点。
        listOf("endRun", "updateRecord", "startExercise", "endExercise", "getCheckPoint", "simulator")
            .forEach { banned ->
                assertTrue("取数链路里出现了写接口 $banned", paths.none { it.contains(banned) })
            }
        // 每条都带 token 头与浏览器 UA —— 少了 UA 服务端 WAF 直接 403。
        assertTrue(server.requests.all { it.tokenHeader == "SPORT_API_TEST" })
    }

    @Test
    fun `六条之间真的隔开了`() = runTest {
        val (repo, slept) = repository()
        // 必须让六条都真发出去：学期那条回空的话，后面三条根本不会请求。
        server.enqueue(200, ok("""[{"year":2026,"scoreData":[]}]"""))
        server.enqueue(200, ok("""[{"info":{"eventName":"跑步"},"effectiveDistance":3.5}]"""))
        server.enqueue(200, ok("""[{"termId":15}]"""))
        server.enqueue(200, ok("""[{"classId":7,"name":"篮球"}]"""))
        server.enqueue(200, ok("""[]"""))
        server.enqueue(200, ok("""null"""))
        repo.refresh()
        assertEquals("六条都得真发出去", 6, server.requestCount)
        // clock 固定在原地 ⇒ 每条之后的 wait 都等于 MIN_INTERVAL_MS。
        assertEquals(5, slept.size)
        assertTrue(
            "两次请求之间的间隔必须 >= 1800ms（服务端红线 1.6s），实际 $slept",
            slept.all { it >= SztyClient.MIN_INTERVAL_MS },
        )
    }

    // ------------------------------------------------------------------
    // 一块失败不拖垮另外两块
    // ------------------------------------------------------------------

    @Test
    fun `体测那条挂了只记备注，不整页失败`() = runTest {
        val (repo, _) = repository()
        server.enqueue(200, """{"statusCode":-1,"message":"业务失败","obj":null}""")
        server.enqueue(200, ok("""[{"info":{"eventName":"跑步"},"effectiveDistance":3.5}]"""))
        server.enqueue(200, ok("""[{"termId":15}]"""))
        server.enqueue(200, ok("""[{"classId":7,"name":"篮球"}]"""))
        server.enqueue(200, ok("""[]"""))
        server.enqueue(200, ok("""null"""))

        repo.refresh()
        val state = repo.state.value
        assertTrue("另一块有数据时不该整页失败，实际 $state", state is ListState.Content)
        val snapshot = (state as ListState.Content).items.single()
        assertEquals(listOf("体测成绩没取到：业务失败"), snapshot.notes)
        assertEquals(3.5, snapshot.run!!.runKm!!, 1e-9)
    }

    @Test
    fun `学期没回时明说体育课签到这次没查`() = runTest {
        val (repo, _) = repository()
        server.enqueue(200, ok("""[]"""))
        server.enqueue(200, ok("""[{"info":{"eventName":"跑步"},"effectiveDistance":3.5}]"""))
        server.enqueue(200, ok("""[]"""))

        repo.refresh()
        val snapshot = (repo.state.value as ListState.Content).items.single()
        assertEquals(listOf("学期的接口没回当前学期，体育课签到这次没查"), snapshot.notes)
        // 学期没有 ⇒ 后面三条一个请求都不该发出去。
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `没有体育课时说清楚是服务端没给班级`() = runTest {
        val (repo, _) = repository()
        server.enqueue(200, ok("""[]"""))
        server.enqueue(200, ok("""[{"info":{"eventName":"跑步"},"effectiveDistance":3.5}]"""))
        server.enqueue(200, ok("""[{"termId":15}]"""))
        server.enqueue(200, ok("""[]"""))

        repo.refresh()
        val snapshot = (repo.state.value as ListState.Content).items.single()
        assertEquals(listOf("这学期没有体育课（服务端没给班级），所以没有签到要看"), snapshot.notes)
        assertEquals(4, server.requestCount)
    }

    // ------------------------------------------------------------------
    // token 失效与「没网」必须分开
    // ------------------------------------------------------------------

    @Test
    fun `token 失效时整页判 SessionExpired 而不是网络不通`() = runTest {
        val (repo, _) = repository()
        server.enqueue(200, """{"statusCode":-3,"message":"token已过期","obj":null}""")
        repeat(5) { server.enqueue(200, ok("null")) }

        repo.refresh()
        val state = repo.state.value
        assertTrue("-3 必须整体判失效，实际 $state", state is ListState.SessionExpired)
    }

    @Test
    fun `被别的设备顶下线也算失效`() = runTest {
        val (repo, _) = repository()
        server.enqueue(200, """{"statusCode":-4,"message":"账号在其他设备登录","obj":null}""")
        repeat(5) { server.enqueue(200, ok("null")) }

        repo.refresh()
        assertTrue(repo.state.value is ListState.SessionExpired)
    }

    @Test
    fun `一条都没有时是 Failed 而不是假装空`() = runTest {
        val (repo, _) = repository()
        repeat(6) { server.enqueue(500, "boom", "text/plain") }

        repo.refresh()
        val state = repo.state.value
        assertTrue("每条都 500 时必须 Failed，实际 $state", state is ListState.Failed)
        val error = (state as ListState.Failed).error
        assertFalse("500 不许被说成 token 失效", error is HzcuException.LoginExpired)
    }

    @Test
    fun `都应了但都没内容时是 Empty 而不是失败`() = runTest {
        val (repo, _) = repository()
        // 体测没录、没有校园跑记录、这学期也没有体育课 —— 三句都是「没有」，不是「取失败」。
        server.enqueue(200, ok("null"))
        server.enqueue(200, ok("null"))
        server.enqueue(200, ok("""[{"termId":15}]"""))
        server.enqueue(200, ok("""[]"""))
        repo.refresh()
        val state = repo.state.value
        assertTrue("一条内容都没有、也没有报错 ⇒ Empty，实际 $state", state is ListState.Empty)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `没填凭据时是 Empty 而不是失败`() = runTest {
        val (repo, _) = repository(session = session(token = null, userId = null))
        repo.refresh()
        assertTrue("没凭据不是错误，实际 ${repo.state.value}", repo.state.value is ListState.Empty)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `拉取成功会记下这次同步时间`() = runTest {
        val store = InMemorySecureStore()
        store.write(SztySession.KEY_TOKEN, "SPORT_API_TEST")
        store.write(SztySession.KEY_USER_ID, "80000")
        val session = SztySession(store)
        val (repo, _) = repository(session = session)
        server.enqueue(200, ok("""[{"year":2026,"scoreData":[]}]"""))
        repeat(5) { server.enqueue(200, ok("null")) }

        repo.refresh()
        assertEquals(now, session.lastSyncMillis())
    }
}