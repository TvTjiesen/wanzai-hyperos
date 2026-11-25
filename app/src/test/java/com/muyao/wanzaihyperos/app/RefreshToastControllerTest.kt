package com.muyao.wanzaihyperos.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshToastControllerTest {
    @Test fun `brief requests never announce and ending is not reported as success`() = runTest {
        var now = 0L
        val busy = BusyTracker()
        val controller = RefreshToastController(showDelayMs = 650, hideDelayMs = 400, cooldownMs = 8_000) { now }
        val observer = launch { controller.observe(busy.state) }
        runCurrent()

        val brief = busy.begin()
        runCurrent()
        advanceTimeBy(300)
        busy.end(brief)
        runCurrent()
        advanceUntilIdle()
        assertNull(controller.message.value)

        val long = busy.begin()
        runCurrent()
        advanceTimeBy(650)
        runCurrent()
        assertEquals("正在更新 · 保留已有内容", controller.message.value)
        busy.end(long)
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        assertNull("request completion must not be described as success", controller.message.value)
        observer.cancel()
    }

    @Test fun `rapid overlapping requests share one in flight interval`() = runTest {
        val busy = BusyTracker()
        val controller = RefreshToastController(showDelayMs = 100, hideDelayMs = 400, cooldownMs = 8_000) { testScheduler.currentTime }
        val observer = launch { controller.observe(busy.state) }
        runCurrent()

        val first = busy.begin()
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals("正在更新 · 保留已有内容", controller.message.value)
        busy.end(first)
        runCurrent()
        advanceTimeBy(250)
        runCurrent()
        val second = busy.begin()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals("toast should remain continuous across a short gap", "正在更新 · 保留已有内容", controller.message.value)
        busy.end(second)
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        assertNull(controller.message.value)
        observer.cancel()
    }

    @Test fun `cooldown suppresses repeated feedback`() = runTest {
        var now = 0L
        val busy = BusyTracker()
        val controller = RefreshToastController(showDelayMs = 100, hideDelayMs = 0, cooldownMs = 8_000) { now }
        val observer = launch { controller.observe(busy.state) }
        runCurrent()

        val first = busy.begin()
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals("正在更新 · 保留已有内容", controller.message.value)
        busy.end(first)
        runCurrent()
        advanceUntilIdle()
        assertNull(controller.message.value)

        now += 1_000
        val repeated = busy.begin()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertNull("same burst inside cooldown remains quiet", controller.message.value)
        busy.end(repeated)
        runCurrent()

        now += 8_000
        val later = busy.begin()
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals("later burst is visible", "正在更新 · 保留已有内容", controller.message.value)
        busy.end(later)
        observer.cancel()
    }
}
