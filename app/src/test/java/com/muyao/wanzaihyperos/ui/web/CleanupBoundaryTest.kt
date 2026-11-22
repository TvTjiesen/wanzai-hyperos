package com.muyao.wanzaihyperos.ui.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Registry and engine-work boundaries are pure JVM code; no WebView or CookieManager is created. */
class CleanupBoundaryTest {
    @Test fun `replacement has identity semantics and stale close never unregisters replacement`() = runBlocking {
        val registry = CleanupRegistry()
        val owner = Any()
        val stopped = mutableListOf<String>()
        val old = registry.register(owner) { stopped += "old" }
        val current = registry.register(owner) { stopped += "current" }
        old.close()
        old.close()
        registry.clear { stopped += "cookies" }
        assertEquals(listOf("current", "cookies"), stopped)
        current.close()
        registry.clear { stopped += "again" }
        assertEquals(listOf("current", "cookies", "again"), stopped)
    }

    @Test fun `closed registration is absent and all actual stops precede global cleanup`() = runBlocking {
        val registry = CleanupRegistry()
        val stopped = mutableSetOf<String>()
        registry.register(Any()) { stopped += "closed" }.close()
        registry.register(Any()) { stopped += "shell" }
        registry.register(Any()) { stopped += "jw" }
        registry.register(Any()) { stopped += "zy" }
        registry.clear { assertEquals(setOf("shell", "jw", "zy"), stopped) }
        assertEquals(setOf("shell", "jw", "zy"), stopped)
    }

    @Test fun `stopper exceptions including cancellation never skip remaining views or cookie cleanup`() = runBlocking {
        val registry = CleanupRegistry()
        val failure = IllegalStateException("synthetic stopper")
        val cancellation = kotlinx.coroutines.CancellationException("synthetic cancellation")
        val stopped = mutableSetOf<String>()
        registry.register(Any()) { stopped += "bad"; throw failure }
        registry.register(Any()) { stopped += "cancel"; throw cancellation }
        registry.register(Any()) { stopped += "good" }
        var cookies = false
        val result = try {
            registry.clear { cookies = true }
            null
        } catch (error: Throwable) { error }
        assertEquals(setOf("bad", "cancel", "good"), stopped)
        assertTrue(cookies)
        // withContext stacktrace recovery may wrap the propagated exception.
        val original = generateSequence(result) { it.cause }.take(16)
            .firstOrNull { it === failure || it === cancellation }
        assertNotNull(original)
        val other = if (original === failure) cancellation else failure
        assertTrue(original!!.suppressed.any { it === other })
        // Failed clear has drained the old entries and lifted the registration barrier.
        registry.register(Any()) { stopped += "new" }
        registry.clear { }
        assertTrue("new" in stopped)
    }

    @Test fun `newly attached views are stopped immediately while cookies are still clearing`() = runBlocking {
        val registry = CleanupRegistry()
        val events = mutableListOf<String>()
        registry.register(Any()) { events += "old-stop" }
        registry.clear {
            events += "cookies-start"
            registry.register(Any()) { events += "new-stop" }
            events += "cookies-end"
        }
        assertEquals(listOf("old-stop", "cookies-start", "new-stop", "cookies-end"), events)
        registry.clear { events += "done" }
        assertEquals("done", events.last())
        assertEquals(1, events.count { it == "new-stop" })
    }

    @Test fun `caller cancellation cannot interrupt bounded security cleanup`() = runBlocking {
        val registry = CleanupRegistry()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stopped = false
        var finished = false
        registry.register(Any()) { stopped = true }
        val clear = launch(start = CoroutineStart.UNDISPATCHED) {
            registry.clear {
                entered.complete(Unit)
                release.await()
                finished = true
            }
        }
        entered.await()
        clear.cancel()
        assertTrue(stopped)
        assertFalse(finished)
        release.complete(Unit)
        clear.join()
        assertTrue(finished)
        registry.register(Any()) { }
        registry.clear { }
    }

    @Test fun `engine detach cancels complete old chain and nested work while new attachment can run`() = runBlocking {
        val work = CleanupWorkSet()
        val entered = CompletableDeferred<Unit>()
        var lateEval = false
        val old = async(start = CoroutineStart.UNDISPATCHED) {
            work.run {
                work.run {
                    entered.complete(Unit)
                    awaitCancellation()
                }
                lateEval = true
            }
        }
        entered.await()
        work.cancelAll()
        val failure = try { old.await(); null } catch (error: Throwable) { error }
        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertFalse(lateEval)
        assertEquals("new-owner", work.run { "new-owner" })
        work.cancelAll()
        work.cancelAll()
        assertEquals("after-repeat", work.run { "after-repeat" })
    }
}
