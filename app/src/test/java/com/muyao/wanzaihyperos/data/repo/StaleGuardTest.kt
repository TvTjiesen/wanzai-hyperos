package com.muyao.wanzaihyperos.data.repo

import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.DemoMessage
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.PortalSystem
import com.muyao.wanzaihyperos.data.items
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import com.muyao.wanzaihyperos.session.SessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ★ 这一整个文件盯的是计划 A2 里的一句明文要求：
 *
 * > 「切分組、搜索及登出後，過時響應不得覆蓋新狀態。」
 *
 * 这句话光写注释是证明不了的，必须能**制造乱序返回**并断言旧响应被丢掉。
 * 下面用「让慢请求挂在 [CompletableDeferred] 上、先让快请求返回、再放慢请求」来复现。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleGuardTest {

    @Test
    fun `begin 之后旧的代次号立即失效`() {
        val guard = StaleGuard()
        val a = guard.begin()
        assertTrue(guard.isCurrent(a))

        val b = guard.begin()

        assertFalse("新请求开始后旧请求就该作废", guard.isCurrent(a))
        assertTrue(guard.isCurrent(b))
    }

    @Test
    fun `invalidate 让在飞请求失效但不产生新代次`() {
        val guard = StaleGuard()
        val token = guard.begin()
        val before = guard.current

        guard.invalidate()

        assertFalse(guard.isCurrent(token))
        assertEquals(before + 1, guard.current)
    }

    private fun msg(id: String, group: String) = DemoMessage(
        id = id,
        title = "标题 $id",
        summary = "摘要",
        group = group,
        updatedAt = "2026-10-10",
        unread = false,
    )

    private fun app(id: Int, name: String) =
        PortalApp(id = id, name = name, url = "https://x.hzcu.edu.cn/", system = PortalSystem.STANDALONE)

    /**
     * 真实场景：用户点了「教学通知」，请求还在飞，他又点了「校务通知」。
     * 后者的响应先到并渲染，前者后到 —— 如果不过滤，列表内容会和分类对不上。
     */
    @Test
    fun `切分组后先发的旧响应不得覆盖新状态`() = runTest {
        val releaseSlowGroup = CompletableDeferred<Unit>()
        val repository = DemoMessageRepository(
            pageSize = 100,
            loadGroups = { emptyList() },
            loadMessages = { group ->
                if (group == "教学通知") {
                    releaseSlowGroup.await()
                    listOf(msg("a1", "教学通知"))
                } else {
                    listOf(msg("b1", "校务通知"))
                }
            },
        )

        val slowFirstClick = launch { repository.selectGroup("教学通知") }
        runCurrent() // 让第一次点击走到挂起点
        repository.selectGroup("校务通知") // 用户改主意
        releaseSlowGroup.complete(Unit) // 旧请求现在才回来
        slowFirstClick.join()

        assertEquals("校务通知", repository.selectedGroup.value)
        assertEquals(
            "旧响应把列表覆盖成了上一个分组的内容",
            listOf("b1"),
            repository.messages.value.items.map { it.id },
        )
    }

    @Test
    fun `快速改搜索词时旧结果不会覆盖新结果`() = runTest {
        val releaseOldKeyword = CompletableDeferred<Unit>()
        val repository = DemoAppSearchRepository(
            load = { keyword ->
                if (keyword == "课") {
                    releaseOldKeyword.await()
                    listOf(app(1, "课程表"))
                } else {
                    listOf(app(2, "课程中心"))
                }
            },
        )

        val slowFirstSearch = launch { repository.search("课") }
        runCurrent()
        repository.search("课程")
        releaseOldKeyword.complete(Unit)
        slowFirstSearch.join()

        assertEquals(listOf(2), repository.state.value.items.map { it.id })
    }

    /**
     * 真实场景：用户点了登出，登出前发出的请求后到并带着数据。
     * 不过滤的话界面会被重新填满，用户以为「登出没生效」。
     */
    @Test
    fun `登出会作废共享闸门`() = runTest {
        val session = PortalSession(InMemorySecureStore())
        session.save("0123456789abcdef0123456789abcdef")
        val guard = StaleGuard()
        val repository = PortalSessionRepository(session, guard)

        val inFlight = guard.begin()
        repository.logout()

        assertFalse("登出后旧请求必须作废", guard.isCurrent(inFlight))
        assertEquals(SessionState.LoggedOut, repository.portalState.value)
        assertNull(session.accessToken())
    }

    // ---------------------------------------------------------------------------
    // 15 分钟下限与「未知不是 0」
    // ---------------------------------------------------------------------------

    @Test
    fun `未读自动刷新在 15 分钟内会被挡下`() = runTest {
        var loadCalls = 0
        var now = 1_000L
        val repository = DemoUnreadRepository(
            load = { loadCalls++; 7 },
            clock = { now },
        )

        assertTrue("第一次必须真的去取", repository.refresh(force = false))
        assertEquals(CountValue.Known(7), repository.state.value.value)

        now += 60_000 // 过 1 分钟
        assertFalse("15 分钟以内的自动刷新必须被挡下", repository.refresh(force = false))
        assertEquals(1, loadCalls)

        assertTrue("用户明确要求时不受下限限制", repository.refresh(force = true))
        assertEquals(2, loadCalls)
    }

    @Test
    fun `未读取数失败不会把计数写成 0`() = runTest {
        val repository = DemoUnreadRepository(load = { throw HzcuException.Transport() })

        repository.refresh(force = true)

        assertEquals(CountValue.Unknown, repository.state.value.value)
        assertNull("失败时绝不能显示 0", repository.state.value.value.orNull)
        // 从未成功过，所以没有「旧值」可标 stale；界面显示「—」即可。
        assertFalse(repository.state.value.stale)
    }

    @Test
    fun `取过之后再失败会保留旧值并标记不新鲜`() = runTest {
        var shouldFail = false
        var now = 1_000L
        val repository = DemoUnreadRepository(
            load = { if (shouldFail) throw HzcuException.Transport() else 12 },
            clock = { now },
        )

        repository.refresh(force = true)
        assertEquals(CountValue.Known(12), repository.state.value.value)

        shouldFail = true
        now += 20 * 60 * 1000
        repository.refresh(force = true)

        assertEquals("旧值应当保留，而不是掉成 0", CountValue.Known(12), repository.state.value.value)
        assertTrue("但必须标成不新鲜", repository.state.value.stale)
    }

    @Test
    fun `登出后未读回到未知而不是零`() = runTest {
        val repository = DemoUnreadRepository(load = { 12 })
        repository.refresh(force = true)
        repository.reset()

        assertEquals(CountValue.Unknown, repository.state.value.value)
        assertNull(repository.state.value.value.orNull)
    }

    @Test
    fun `待办计数在从未成功过时是 null 而不是 0`() = runTest {
        val repository = DemoTodoRepository(load = { throw HzcuException.Transport() })
        repository.refresh(force = true)

        assertNull("界面必须显示「—」而不是「0 个待办」", repository.counts.value)
    }

    @Test
    fun `待办计数登出后清空`() = runTest {
        val repository = DemoTodoRepository()
        repository.refresh(force = true)
        assertTrue(repository.counts.value != null)

        repository.reset()
        assertNull(repository.counts.value)
    }
}
