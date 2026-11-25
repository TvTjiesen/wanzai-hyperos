package com.muyao.wanzaihyperos.ui.main

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SectionPagerWiringTest {
    private fun source(name: String) = File("src/main/java/com/muyao/wanzaihyperos/ui/main/$name.kt").readText()

    @Test fun standardPagerIsTheOnlyPageMovementAndKeepsStableKeys() {
        val shell = source("WanzaiMainShell")
        assertEquals(1, Regex("\\bHorizontalPager\\(").findAll(shell).count())
        assertTrue(shell.contains("key = { MainSection.entries[it] }"))
        assertTrue(shell.contains("beyondViewportPageCount = 0"))
        assertTrue(shell.contains("val swipeEnabled = !overlaysVisible && customization.swipePages"))
        assertTrue(shell.contains("userScrollEnabled = swipeEnabled && !crossAxis"))
        for (old in listOf("AnimatedContent(", "swipePx", "swipeGen", "navDir", ".swipeToSwitchSection(", "delay(280)", "translationX =")) {
            assertFalse("Old movement still present: $old", shell.contains(old))
        }
    }

    @Test fun selectionCancelsEarlierJobAndPublishesOnlySettledCurrentIntent() {
        val shell = source("WanzaiMainShell")
        val select = shell.substringAfter("fun selectPage(target: Int)").substringBefore("DisposableEffect(Unit)")
        assertTrue(select.indexOf("selectionJob?.cancel()") < select.indexOf("selectionScope.launch"))
        assertTrue(select.contains("pagerState.animateScrollToPage(page)"))
        assertTrue(select.contains("else pagerState.scrollToPage(page)"))
        assertTrue(select.contains("if (generation == selectionGeneration) requestedPage = null"))
        val tap = shell.substringAfter("onSelect = { idx ->").substringBefore("backdrop =")
        assertTrue(tap.indexOf("uiState.selectedTab = idx") < tap.indexOf("selectPage(idx)"))
        assertTrue(shell.contains("!scrolling && pending == null && generation == selectionGeneration"))
        assertTrue(shell.contains("requestedPage == null && !pagerState.isScrollInProgress && pagerState.settledPage == settled"))
        assertTrue(shell.contains("uiState.selectedTab.coerceIn(0, maxIdx) == committedPage"))
        assertTrue(shell.contains("uiState.publishSettledTab(settled, revision)"))
        assertTrue(shell.contains("revision == uiState.tabRequestRevision && revision == consumedRequestRevision"))
        assertTrue(shell.contains("revision != consumedRequestRevision"))
        assertTrue(tap.indexOf("consumedRequestRevision = uiState.tabRequestRevision") < tap.indexOf("selectPage(idx)"))
        assertFalse(shell.contains("target != committedPage"))
        assertTrue(shell.contains("if (notifiedPage != settled)"))
        assertTrue(shell.contains("onDispose { selectionJob?.cancel() }"))
    }

    @Test fun everyCompletedTabGestureSubmitsIntentEvenOnCurrentTransitPage() {
        val navigation = File("../third_party/glasmio/glasmio-ui/src/main/java/io/github/neoruaa/glasmio/ui/GlassBottomBar.kt").readText()
        val release = navigation.substringAfter("onDragStopped = {").substringBefore("onDrag = {")
        assertTrue(release.contains("stableOnSelected(tabItemsState.value[target].key)"))
        assertFalse(release.contains("if (target != selectedIndexState.value)"))
        val cancel = navigation.substringAfter("onDragCancelled = {").substringBefore("onDragStopped = {")
        assertFalse(cancel.contains("stableOnSelected("))
        assertTrue(release.contains("val releasedTarget = gestureTarget.value"))
        assertTrue(release.contains("releasedTarget.fastRoundToInt()"))
        assertFalse(release.contains("val target = targetValue"))
        assertTrue(cancel.contains("gestureTarget.value = null"))
        val programmatic = navigation.substringAfter("LaunchedEffect(selectedIndex, dragAnimation, gestureActive)").substringBefore("val selectionFeedbackEnabled")
        assertTrue(programmatic.contains("if (!gestureActive &&"))
        assertFalse(programmatic.contains("stableOnSelected("))
        val drag = navigation.substringAfter("onDrag = { _, dragAmount ->").substringBefore("lensStyle = NativeControlLensStyle.TabBarIndicator")
        assertTrue(drag.contains("val currentTarget = gestureTarget.value ?: return@DampedDragAnimation"))
        assertTrue(drag.contains("gestureTarget.value = nextTarget"))
    }

    @Test fun pageChromeBackdropAndVisibilityRemainPerPageNotShared() {
        val shell = source("WanzaiMainShell")
        assertTrue(shell.contains("MainSection.entries.map { PageChrome() }"))
        assertTrue(shell.contains("LocalPageChrome provides pageChromes[s]"))
        assertTrue(shell.contains("LocalLargeTitle provides MainSection.entries[s].label"))
        assertTrue(shell.contains("LocalPageActive provides (s == pagerState.settledPage && !pagerState.isScrollInProgress && !overlaysVisible)"))
        assertTrue(shell.contains("LocalPageVisible provides (s == pagerState.settledPage && !overlaysVisible)"))
        assertTrue(shell.contains("val pageBackdrop = rememberLayerBackdrop()"))
        assertTrue(shell.contains("val recordEnabled = !recorderOff && isCurrent"))
        assertTrue(shell.contains("remember(pageBackdrop, recordEnabled) { mutableStateOf(false) }"))
        assertTrue(shell.contains("LaunchedEffect(pageBackdrop, recordEnabled)"))
        assertEquals(2, Regex("withFrameNanos \\{ \\}").findAll(shell).count())
        assertTrue(shell.contains("LaunchedEffect(s, pageBackdrop, isCurrent, recordEnabled, sampleReady.value)"))
        assertTrue(shell.contains("if (isCurrent && recordEnabled && sampleReady.value) activeBackdrop.value = pageBackdrop"))
        assertTrue(shell.contains(".then(if (recordEnabled)"))
        assertTrue(shell.contains("if (activeBackdrop.value === pageBackdrop) activeBackdrop.value = null"))
        assertEquals(1, Regex("\\bGlassNavBar\\(").findAll(shell).count())
        val home = File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/HomeSectionPage.kt").readText()
        assertTrue(home.contains("homeUsesLightStatusIcons("))
        assertTrue(home.contains("chrome.sampledTopLuminance, statusCollapse"))
        val statusIcons = home.substringAfter("LightStatusBarIcons(").substringBefore("val pageActive =")
        assertTrue("只有当前正常首页可写状态栏，布局 preview 不得抢占", statusIcons.contains("active = activeHome && !previewOnly,"))
        assertTrue(home.contains("DisposableEffect(light, window, active)"))
        assertTrue(home.contains("if (!active) return@DisposableEffect onDispose { }"))
        assertTrue("离场时应恢复先前状态栏外观", home.contains("onDispose { controller.isAppearanceLightStatusBars = previous }"))
    }

    @Test fun onlyTopmostOverlayStaysActiveWhileMainPagerIsBlocked() {
        val shell = source("WanzaiMainShell")
        assertTrue(shell.contains("LocalPageActive provides (showAppearance && !showPeriods && !showReminders)"))
        assertFalse(shell.contains("showAccessibility"))
        assertTrue(shell.contains("LocalPageActive provides (showPeriods && !showReminders)"))
        assertTrue(shell.contains("LocalPageActive provides showReminders"))
        assertTrue(shell.contains("val swipeEnabled = !overlaysVisible && customization.swipePages"))
        assertTrue(shell.contains("userScrollEnabled = swipeEnabled && !crossAxis"))
    }

    @Test fun heldBallConsumesBeforePagerMainWithoutPermanentRegion() {
        val ball = File("src/main/java/com/muyao/wanzaihyperos/ui/design/ai/AiBall.kt").readText()
        val gesture = ball.substringAfter("awaitEachGesture {").substringBefore("} finally")
        assertTrue(gesture.contains("awaitPointerEvent(PointerEventPass.Initial)"))
        // 动效 2 新策略：HOLD 判定先于认领/消费（滚动在此期间可正常接管）；
        // 确认长按后才 claim + consume，且消费先于 Main 传递（拖动小球不触发翻页）。
        assertTrue(gesture.indexOf("withTimeoutOrNull(AiBallDrag.HOLD_MS)") < gesture.indexOf("HorizontalDragZones.claimPointer"))
        assertTrue(gesture.indexOf("HorizontalDragZones.claimPointer") < gesture.indexOf("change.consume()"))
        assertTrue(gesture.indexOf("change.consume()") < gesture.indexOf("awaitPointerEvent(PointerEventPass.Main)"))
        assertTrue(gesture.contains("aiBallPressDecision("))
        assertTrue(gesture.contains("viewConfiguration.touchSlop"))
        assertTrue(ball.contains("HorizontalDragZones.releasePointers(captureOwner)"))
        assertFalse(ball.contains("HorizontalDragZones.set("))
        assertFalse(ball.contains("detectDragGestures"))
    }
}
