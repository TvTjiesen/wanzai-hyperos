package com.muyao.wanzaihyperos.app

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BusyTrackerTest {
    @Test fun `one hundred concurrent starts and ends retain every id and finish at zero`() {
        val tracker = BusyTracker()
        val ids = ConcurrentHashMap.newKeySet<Long>()
        val started = CountDownLatch(100)
        val finish = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(100)
        try {
            val futures = (1..100).map {
                pool.submit {
                    val id = tracker.begin()
                    try {
                        assertTrue(ids.add(id))
                        assertTrue(tracker.state.value in 1..100)
                    } finally {
                        started.countDown()
                    }
                    try {
                        check(finish.await(10, TimeUnit.SECONDS))
                    } finally {
                        tracker.end(id)
                    }
                }
            }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            assertEquals(100, ids.size)
            assertEquals(100, tracker.state.value)
            finish.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(0, tracker.state.value)
        } finally {
            finish.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test fun `ending pre reset ids never decrements new work and duplicate ends are idempotent`() {
        val tracker = BusyTracker()
        val old = (1..100).map { tracker.begin() }
        assertEquals(100, tracker.state.value)
        tracker.reset()
        assertEquals(0, tracker.state.value)
        val current = (1..100).map { tracker.begin() }
        assertTrue(old.toSet().intersect(current.toSet()).isEmpty())
        old.forEach { tracker.end(it); assertEquals(100, tracker.state.value) }
        current.forEachIndexed { index, id ->
            tracker.end(id)
            assertEquals(99 - index, tracker.state.value)
            tracker.end(id)
            assertEquals(99 - index, tracker.state.value)
        }
        tracker.end(Long.MIN_VALUE)
        assertEquals(0, tracker.state.value)
    }

    @Test fun `repeated reset and out of order finalizers always return to zero`() {
        val tracker = BusyTracker()
        val stale = mutableListOf<Long>()
        repeat(100) {
            stale += tracker.begin()
            tracker.reset()
            val live = tracker.begin()
            stale.asReversed().forEach { tracker.end(it) }
            assertEquals(1, tracker.state.value)
            tracker.end(live)
            tracker.reset()
            assertEquals(0, tracker.state.value)
        }
    }
}
