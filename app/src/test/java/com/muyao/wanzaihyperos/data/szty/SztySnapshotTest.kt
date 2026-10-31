package com.muyao.wanzaihyperos.data.szty

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.net.SztyClient
import com.muyao.wanzaihyperos.net.TestHttpServer
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.SztySession
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 数智体育**磁盘快照**的测试：上次取到的原文要能救活下一次冷启动。
 *
 * 这三条守的是同一件事的三个面：
 * ① 拿到的数据要真的落盘（否则冷启动还是空等十几秒）；
 * ② 读回来要与线上**走同一套解析**（两套解析迟早分叉，分叉的表现是
 *    「快照里和线上看到的不是一回事」）；
 * ③ 一次失败不许把上一份好数据冲掉（2026-10-21 真机事故：失败那次把快照写成了失败页，
 *    重启后还是失败页）。
 */
class SztySnapshotTest {

    private lateinit var server: TestHttpServer
    private lateinit var dir: java.io.File

    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        server = TestHttpServer()
        now = 1_700_000_000_000L
        dir = java.nio.file.Files.createTempDirectory("szty-snapshot").toFile()
    }

    @After
    fun tearDown() {
        server.stop()
        dir.deleteRecursively()
    }

    private fun session(): SztySession {
        val store = InMemorySecureStore()
        store.write(SztySession.KEY_TOKEN, "SPORT_API_TEST")
        store.write(SztySession.KEY_USER_ID, "80000")
        return SztySession(store)
    }

    private fun repository(snapshotDir: java.io.File? = dir): LiveSztyRepository =
        LiveSztyRepository(
            SztyClient(
                baseUrl = server.baseUrl,
                sleeper = { },
                clock = { now },
            ),
            session(),
            clock = { now },
            snapshotDir = snapshotDir,
        )

    private val envelope = """{"statusCode":1,"message":null,"obj":"""

    private fun ok(obj: String): String = envelope + obj + "}"

    /** 六条都成功的成套应答（与 SztyRepositoryTest 里那套同源）。 */
    private fun enqueueAllSix() {
        server.enqueue(200, ok("""[{"year":2026,"scoreData":[{"name":"体重指数","data":"21.5"}]}]"""))
        server.enqueue(200, ok("""[{"info":{"eventName":"跑步"},"effectiveDistance":16.78,"targetDistance":120}]"""))
        server.enqueue(200, ok("""[{"termId":15}]"""))
        server.enqueue(200, ok("""[{"classId":3062,"name":"匹克球Ⅰ","address":"西网球场"}]"""))
        server.enqueue(200, ok("""[{"title":"第 3 周","status":"已签到"}]"""))
        // 字段名照 SztyParsers.attendanceSummary 认的那几个：normal / late / leave / absenteeism。
        server.enqueue(200, ok("""{"normal":4,"late":0,"leave":2,"absenteeism":0}"""))
    }

    private fun snapshotFile(): java.io.File = java.io.File(dir, "szty_snapshot.json")

    // ------------------------------------------------------------------

    @Test
    fun `取到的原文会真的落盘`() = runTest {
        val repo = repository()
        enqueueAllSix()
        repo.refresh()
        val f = snapshotFile()
        assertTrue("刷新成功后快照文件必须存在", f.isFile)
        val text = f.readText()
        // 存的是**服务端 obj 的原文**：连字段名都得还在，而不是一堆 toString 出来的模型。
        assertTrue("快照里得有体测那条原文，实际 $text", text.contains("scoreData"))
        assertTrue("快照里得有校园跑那段原文，实际 $text", text.contains("effectiveDistance"))
    }

    @Test
    fun `冷启动立刻拿快照填上，并标成上次存的`() = runTest {
        // 第一台设备/第一次启动：真取一遍，留下快照。
        enqueueAllSix()
        repository().also { it.refresh() }

        // 第二次冷启动：构造出来就该有内容，不必先刷新（这正是省掉那十几秒的地方）。
        val next = repository()
        val seeded = next.state.value
        assertTrue("构造完就得是 Content，实际 $seeded", seeded is ListState.Content)
        val content = seeded as ListState.Content
        val snapshot = content.items.single()

        // ★ 与线上同一个解析器 ⇒ 数字必须一模一样。
        assertEquals(16.78, snapshot.run!!.runKm!!, 1e-9)
        assertEquals(120.0, snapshot.run!!.runTargetKm!!, 1e-9)
        assertEquals(3062, snapshot.peClass!!.classId)
        assertEquals("匹克球Ⅰ", snapshot.peClass!!.name)
        assertEquals(4, snapshot.attendanceSummary!!.signed)
        assertEquals("2026", snapshot.fitness.single().year)

        // ★ 必须标 stale：界面靠它显示「这是上次存的」，不能假装是刚取的。
        assertEquals(true, content.meta?.stale)
        assertEquals(java.io.File(dir, "szty_snapshot.json").lastModified(), content.meta?.updatedAtMillis)
    }

    @Test
    fun `一次全都失败不会把上一份好快照冲掉`() = runTest {
        // 先有一份好的。
        enqueueAllSix()
        repository().also { it.refresh() }
        val before = snapshotFile().readText()

        // 再来一台「全都失败」的：服务端一条都不给（fallbackStatus=500）。
        val failing = repository()
        failing.refresh()
        // 已经有快照时，失败**不该**把内容撤掉 —— 那等于把用户上次看到的数据当场弄没。
        // 正确的表现是：内容还在、标成 stale、把这次失败原因挂上去（界面上说「这是上次存的」）。
        val afterFailure = failing.state.value
        assertTrue("有快照时全失败应保留内容，实际 $afterFailure", afterFailure is ListState.Content)
        assertEquals(true, (afterFailure as ListState.Content).meta?.stale)
        assertNotNull("失败原因必须留下，不能静默", afterFailure.meta?.lastRefreshError)

        assertEquals("失败那次不许改写快照", before, snapshotFile().readText())
        // 而且下一次冷启动还能靠它起来。
        val after = repository()
        assertTrue("旧快照必须还能用", after.state.value is ListState.Content)
    }

    @Test
    fun `坏掉的快照文件当作没有缓存，不许把启动搞崩`() = runTest {
        snapshotFile().writeText("{这不是 JSON")
        val repo = repository()
        // 读不懂 ⇒ 回到 FirstLoad（而不是 Empty：我们并不知道服务端有没有内容）。
        assertTrue("坏文件不该让构造崩，实际 ${repo.state.value}", repo.state.value is ListState.FirstLoad)
        assertNotNull(repo.state.value)
    }

    @Test
    fun `没有给目录就完全不落盘`() = runTest {
        val repo = repository(snapshotDir = null)
        enqueueAllSix()
        repo.refresh()
        assertTrue("没给目录时不该凭空写文件", dir.listFiles().orEmpty().isEmpty())
    }
}
