package com.muyao.wanzaihyperos.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SessionChangeObserverTest {
    @Test fun `concurrent notifications read latest revision only under owner monitor`() {
        val monitor = Any()
        var revision = "0"
        val changes = AtomicInteger()
        val observer = SessionChangeObserver(monitor, {
            assertTrue(Thread.holdsLock(monitor))
            revision
        }) { changes.incrementAndGet() }
        val entered = CountDownLatch(2)
        lateinit var first: Thread
        lateinit var second: Thread
        synchronized(monitor) {
            revision = "1"
            first = thread { entered.countDown(); observer.observe() }
            revision = "2"
            second = thread { entered.countDown(); observer.observe() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
        }
        first.join(5000); second.join(5000)
        assertFalse(first.isAlive); assertFalse(second.isAlive)
        assertEquals(1, changes.get())
        observer.observe()
        assertEquals(1, changes.get())
    }

    @Test fun `credential storage failure still runs reminders diagnostics and WebView cleanup`() = runBlocking {
        val storage = IllegalStateException("synthetic commit failure")
        val reminder = IllegalStateException("synthetic reminder failure")
        val steps = mutableListOf<String>()
        val result = runCatching {
            cleanupOnExit(
                action = { throw storage },
                { steps += "reminders"; throw reminder },
                { steps += "diagnostics" },
                { steps += "WebView" },
            )
        }.exceptionOrNull()
        assertSame(storage, result)
        assertEquals(listOf("reminders", "diagnostics", "WebView"), steps)
        assertSame(reminder, storage.suppressed.single())
    }

    @Test fun `cancellation preserves original error and all cleanup steps`() = runBlocking {
        val cancelled = CancellationException("synthetic cancellation")
        var steps = 0
        val error = runCatching { cleanupOnExit(action = { throw cancelled }, { steps++ }, { steps++ }) }.exceptionOrNull()
        assertSame(cancelled, error)
        assertEquals(2, steps)
    }

    @Test fun `successful action reports cleanup failure after remaining cleanups`() = runBlocking {
        val failure = IllegalStateException("synthetic cleanup")
        var lastRan = false
        val error = runCatching { cleanupOnExit(action = { 7 }, { throw failure }, { lastRan = true }) }.exceptionOrNull()
        assertSame(failure, error)
        assertTrue(lastRan)
    }
}
