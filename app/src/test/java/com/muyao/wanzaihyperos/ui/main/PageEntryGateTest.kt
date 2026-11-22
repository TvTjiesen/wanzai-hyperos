package com.muyao.wanzaihyperos.ui.main

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEntryGateTest {
    private fun page(name: String): String = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/$name.kt").readText()

    @Test fun inactiveNeighboursDoNotClaimAndFirstVisibleEntryReadsOncePerPageAndOwner() {
        val gate = PageEntryGate()
        val counts = mutableMapOf<String, Int>()
        fun enter(page: String, owner: Long, active: Boolean) {
            if (active && gate.claim(page, owner)) counts[page] = counts.getOrDefault(page, 0) + 1
        }
        for (page in listOf("home", "apps", "notify", "timetable")) {
            repeat(3) { enter(page, 7, active = false) }
            assertFalse(gate.hasEntered(page, 7))
            enter(page, 7, active = true)
            repeat(3) { enter(page, 7, active = false); enter(page, 7, active = true) }
            assertEquals(1, counts[page])
            enter(page, 8, active = false)
            assertFalse(gate.hasEntered(page, 8))
            enter(page, 8, active = true)
            assertEquals(2, counts[page])
        }
    }

    @Test fun manualRefreshIsNotClaimedAndNewShellCanReadAgain() {
        val gate = PageEntryGate()
        var calls = 0
        if (gate.claim("home", 3)) calls++
        repeat(2) { calls++ }
        assertFalse(gate.claim("home", 3))
        assertEquals(3, calls)
        assertTrue(PageEntryGate().claim("home", 3))
    }

    @Test fun pageExitDoesNotCancelAlreadyLaunchedApplicationRead() = runBlocking {
        val gate = PageEntryGate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val reply = CompletableDeferred<Unit>()
        var calls = 0
        var completed = false
        try {
            assertTrue(gate.claim("notify", 4))
            val read = scope.async { calls++; reply.await(); completed = true }
            assertFalse(gate.claim("notify", 4))
            assertFalse(read.isCancelled)
            reply.complete(Unit)
            read.await()
            assertEquals(1, calls)
            assertTrue(completed)
        } finally { scope.cancel() }
    }

    @Test fun automaticPageReadsAreActiveOwnerGatedAndStillUseApplicationScope() {
        for ((name, id) in mapOf("HomeSectionPage" to "home", "AppsSectionPage" to "apps", "NotifySectionPage" to "notify")) {
            val text = page(name)
            assertTrue(name, text.contains("LocalPageEntryGate.current"))
            assertTrue(name, text.contains("LocalPageEntryOwner.current"))
            val at = text.indexOf("LaunchedEffect(pageActive, entryOwner, entryGate")
            assertTrue("$name 找不到页面入口闸门 effect", at >= 0)
            val effect = text.substring(at).substringBefore("\n    }")
            assertTrue(name, effect.contains("pageActive"))
            assertTrue(name, effect.contains("entryGate.claim(\"$id\", entryOwner)"))
            assertFalse(name, text.contains("LaunchedEffect(Unit)"))
            assertTrue(name, text.contains("graph.fetch {"))
        }
        // 课表页刻意不挂 PageEntryGate：它的爬取器由 LocalPageVisible 管（见该页 JwHiddenEngine 注释）。
        val timetable = page("TimetableSectionPage")
        assertTrue(timetable.contains("visible = com.muyao.wanzaihyperos.ui.main.LocalPageVisible.current"))
        assertFalse(timetable.contains("LocalPageEntryGate.current"))
        val notify = page("NotifySectionPage")
        // 消息组的分组/选组刷新走同一个 refreshGate 循环，不再另开一条 effect；
        // 新闻组仍保留自己的可见性入口。
        val newsEffect = notify.substringAfter("LaunchedEffect(newsGroupsState, newsSelected, pageActive, entryOwner)").substringBefore("\n    }")
        assertTrue(newsEffect.indexOf("if (!pageActive)") < newsEffect.indexOf("graph.fetch"))
        assertTrue(notify.contains("refreshGate.claim(\"message-groups\""))
        assertTrue(notify.contains("refreshGate.claim(\"messages:"))
    }

    @Test fun inactiveTimetableDoesNotAttachOrStopEngineAndFirstLoadIntentAlwaysFinishes() {
        val text = page("TimetableSectionPage")
        assertTrue(text.contains("val jwWillFetch = remember(entryOwner) { androidx.compose.runtime.mutableStateOf(false) }"))
        assertTrue(text.contains("if (!pageActive) return@LaunchedEffect"))
        assertTrue(text.contains("val jwPending = useJiaowu && jwCrawlingNow && jwState is ListState.FirstLoad"))
        assertTrue(text.contains("invokeOnCompletion { jwWillFetch.value = false }"))
        assertTrue(text.contains("LocalPageVisible.current"))
        assertTrue(text.contains("shouldRefreshTimetable(graph.jwTimetable.state.value, System.currentTimeMillis())"))
        assertTrue(text.contains("try { graph.jwTimetable.refresh() }") && text.contains("finally { jwWillFetch.value = false }"))
        val host = text.substringAfter("private fun JwHiddenEngine(").substringBefore("\n}\n")
        assertTrue(host.contains("if (visible) engine.attach(this)"))
        assertTrue(host.contains("update = { if (visible) engine.attach(it) }"))
        assertTrue(host.contains("onRelease = { engine.release(it) }"))
        assertFalse(host.contains("else engine.release"))
        assertFalse(host.contains("engine.detach"))
    }

    @Test fun weekStripClaimsOnlyHorizontalAxisInInitialAtStandardSlop() {
        val text = page("TimetableSectionPage")
        val gesture = text.substringAfter(".pointerInput(sel, cellPx)").substringBefore("horizontalArrangement =")
        assertTrue(gesture.contains("awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)"))
        assertTrue(gesture.contains("awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)"))
        assertTrue(gesture.contains("abs(drag) > slop && abs(drag) > abs(dyTotal) * 1.2f"))
        assertTrue(gesture.contains("abs(dyTotal) > slop && abs(dyTotal) > abs(drag) * 1.2f"))
        assertFalse(gesture.contains("slop * 1.5f"))
        assertTrue(gesture.indexOf("claimed = true") < gesture.indexOf("change.consume()"))
        assertTrue(gesture.contains("val projected = drag + v * flingSeconds()"))
        assertTrue(gesture.contains("val next = (sel + step).coerceIn(1, 7)"))
        assertTrue(gesture.contains("finally {"))
        assertFalse(text.contains("HorizontalDragZones"))
    }

    @Test fun settingsLocalReadsWaitUntilActiveAndOnlySafeUiFieldsAreSaveable() {
        val settings = page("SettingsSectionPage")
        val read = settings.substringAfter("LaunchedEffect(graph, uiState.loggedIn, refreshKey, pageActive, entryOwner)").substringBefore("val pilot =")
        assertTrue(read.indexOf("if (!pageActive)") < read.indexOf("ballFacts.beginRead()"))
        assertTrue(read.indexOf("if (!pageActive)") < read.indexOf("snapshotAccountSources("))
        val notify = page("NotifySectionPage")
        assertTrue(notify.contains("var localFilter by androidx.compose.runtime.saveable.rememberSaveable"))
        assertTrue(notify.contains("var confirmReadAll by remember"))
        assertTrue(notify.contains("var localFullText by remember"))
        val timetable = page("TimetableSectionPage")
        assertTrue(timetable.contains("var weekOffset by androidx.compose.runtime.saveable.rememberSaveable"))
        assertTrue(timetable.contains("var selectedDay by androidx.compose.runtime.saveable.rememberSaveable"))
        assertTrue(timetable.contains("var picked by remember"))
    }
}
