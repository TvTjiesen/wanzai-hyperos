package com.muyao.wanzaihyperos.ui.main.pages

import com.muyao.wanzaihyperos.net.CacheDomain
import com.muyao.wanzaihyperos.ui.SourceScan
import com.muyao.wanzaihyperos.ui.main.pages.redesign.SettingsCacheClearPlan
import com.muyao.wanzaihyperos.ui.main.pages.redesign.settingsCacheSizeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPlanTest {
    private val sources by lazy { SourceScan.sources().toMap() }
    private fun source(name: String): String = sources.getValue(name)
    private fun page() = source("main/ui/main/pages/SettingsSectionPage.kt")
    private fun overview() = source("main/ui/main/pages/redesign/SettingsOverview.kt")
    private fun accounts() = source("main/ui/main/pages/AccountsPage.kt")
    private fun preferences() = source("main/ui/main/pages/PreferencesPage.kt")
    private fun appearance() = source("main/ui/main/pages/AppearancePage.kt")
    private fun compact(text: String) = text.replace(Regex("\\s+"), "")

    private fun block(text: String, marker: String): String {
        val markerStart = text.indexOf(marker)
        assertTrue("Missing source block: $marker", markerStart >= 0)
        val start = text.indexOf('{', markerStart)
        assertTrue("Missing opening brace: $marker", start >= 0)
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
            } else when (char) {
                '"', '\'' -> quote = char
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start + 1, index)
                }
            }
        }
        error("Unclosed source block: $marker")
    }

    @Test fun selectedAHasOne64DpBallOnCardShoulderWithIndependentHitBoxAndScrolling() {
        val page = page()
        assertEquals(1, Regex("AiBall\\(").findAll(page).count())
        assertTrue(page.contains("AiBallStates.ofAccounts(accountSources"))
        assertTrue(page.contains("diameter = 64.dp, hitSlop = 4.dp"))
        assertTrue(page.contains("settingsTitleItem(pilot = pilot)"))
        val entry = page.substringAfter("item(key = \"account-entry\")").substringBefore("item(key = \"personal-preferences\")")
        assertTrue(entry.contains("SettingsConnectionCard("))
        assertTrue(entry.contains("leading = { AiBall("))
        val overview = overview()
        val title = overview.substringBefore("fun SettingsConnectionCard")
        assertFalse(title.contains("leading()"))
        val card = overview.substringAfter("fun SettingsConnectionCard").substringBefore("internal data class")
        assertTrue(card.contains("val ballBox = 72.dp"))
        assertTrue(card.contains("padding(top = ballBox / 2)"))
        assertTrue(card.contains("padding(start = 12.dp).size(ballBox)"))
        assertTrue(card.indexOf("{ leading() }") > card.indexOf("configuration.boundaryText()"))
        assertEquals(1, Regex("\\.clickable\\(").findAll(card).count())
        assertFalse(card.contains("offset("))
        assertFalse(card.contains("clip("))
        assertFalse(card.contains("LiquidGlass"))
        assertTrue(card.contains("padding(start = 76.dp)"))
    }

    @Test fun connectionPagesShareConfigurationSemanticsAndReadSnapshotsOnIo() {
        for (text in listOf(page(), accounts())) {
            assertTrue(text.contains("accountConfigurationSummary("))
            assertTrue(text.contains("withContext(Dispatchers.IO)"))
            assertTrue(text.contains("snapshotAccountSources("))
        }
        assertTrue(overview().contains("configuration.countText()"))
        assertTrue(overview().contains("configuration.boundaryText()"))
        assertTrue(accounts().contains("configuration.countText() + \"\\n\" + configuration.boundaryText()"))
        assertTrue(accounts().contains("trailingChevron = onClick != null"))
        assertTrue(accounts().contains("source.timeText()"))
    }

    @Test fun everyOriginalEntryAndBackupPreviewCancellationRemainReachable() {
        val text = compact(page())
        for (token in listOf(
            "onClick=onOpenAccounts", "onClick=onOpenAppearance", "onClick=onOpenPeriods",
            "onClick=onOpenReminders", "onClick=onOpenAbout", "onClick=onOpenPreferences",
            "saveTimetableGrid(uiState.timetableGrid)", "pageBottomInsetDp(LocalNavBarVisible.current)",
        )) assertTrue("Missing settings action: $token", text.contains(token))
        val shell = compact(source("main/ui/main/WanzaiMainShell.kt"))
        for (token in listOf(
            "onOpenAppearance={showAppearance=true}", "onOpenPeriods={showPeriods=true}",
            "onOpenReminders={showReminders=true}", "onOpenAbout=onOpenAbout",
            "onOpenPreferences=onOpenPreferences", "onOpenAccounts=onOpenAccounts",
        )) assertTrue("Missing shell wiring: $token", shell.contains(token))
        val root = compact(source("main/WanzaiRoot.kt"))
        for ((callback, route) in listOf("Preferences" to "Preferences", "About" to "About", "Accounts" to "AccountSources")) {
            assertTrue("Missing $route navigation", root.contains("onOpen$callback={backStack.add(Route.$route)}"))
            assertTrue("Missing $route destination", root.contains("entry<Route.$route>"))
        }
        val destination = block(source("main/WanzaiRoot.kt"), "entry<Route.Preferences>")
        assertTrue(destination.contains("PreferencesPage("))
        assertTrue(compact(destination).contains("onBack={backStack.removeLastOrNull()}"))
        val display = block(appearance(), "item(key = \"section-interaction\")")
        for (token in listOf("ThemeExpandableSection(\"动画与显示\"", "AppearanceToggle(\"减少动态\"",
            "AppearanceToggle(\"高对比显示\"", "AppearanceToggle(\"减弱透明度\"", "ACTION_ACCESSIBILITY_SETTINGS")) {
            assertTrue("Missing integrated accessibility action: $token", display.contains(token))
        }
        assertTrue(source("main/ui/about/AboutPage.kt").contains("Text(\"资料来源\""))
        val prefs = compact(preferences())
        for (token in listOf("FLAG_DEBUGGABLE", "AiBallLabActivity", "CreateDocument", "OpenDocument",
            "PrefsBackup.parse(text)", "syncUiStateFromPrefs(uiState)", "setRefractsGlass",
            "setReduceTransparency", "pending.lines[index]")) {
            assertTrue("Missing preference action: $token", prefs.contains(token))
        }
        val preview = block(preferences(), "preview?.let { pending ->")
        assertTrue(compact(preview).contains("onDismissRequest={preview=null}"))
        val cancel = compact(block(preview, "AppearanceChoice(\"取消恢复\""))
        assertEquals("preview=null", cancel)
        assertFalse(compact(preview).substringBefore("AppearanceChoice(\"确认恢复这份偏好备份\"").contains("PrefsBackup.apply("))
        val restore = block(preview, "AppearanceChoice(\"确认恢复这份偏好备份\"")
        val restoreCode = compact(restore)
        assertTrue(restoreCode.startsWith("if(busy||preview!=pending)return@AppearanceChoice"))
        assertTrue(restoreCode.contains("withContext(Dispatchers.IO){PrefsBackup.apply(pending)}"))
        assertTrue(restoreCode.contains("if(count>=0){com.muyao.wanzaihyperos.ui.syncUiStateFromPrefs(uiState)"))
        assertTrue(restoreCode.contains("throwcancel"))
        assertEquals(1, Regex("PrefsBackup\\.apply\\(").findAll(preferences()).count())
        for (token in listOf("onOpenLogin()", "onOpenJw()", "onOpenZhgy()", "onOpenSzty()", "onOpenApps()", "graph.logout()")) {
            assertTrue("Missing account entry: $token", accounts().contains(token))
        }
        assertFalse(page().contains("LayoutPreset"))
    }

    @Test fun categoryAndAllClearOnlyStagePlanUntilExplicitConfirmationThenUseIo() {
        val prefs = preferences()
        assertTrue(compact(prefs).contains("mutableStateOf<List<CacheDomain>?>(null)"))
        val initialRead = block(prefs, "LaunchedEffect(graph.httpCache)")
        assertTrue(compact(initialRead).contains("domains=withContext(Dispatchers.IO){graph.httpCache.domains()}"))
        val entries = block(prefs, "item(key = \"local-data\")")
        val category = block(entries, "domains.orEmpty().forEach { domain ->")
        assertTrue(compact(category).contains("pendingClear=SettingsCacheClearPlan(domain.name,domain.entries,domain.bytes)"))
        val all = block(entries, "AppearanceChoice(\"清理全部读取缓存\"")
        assertTrue(compact(all).contains("pendingClear=SettingsCacheClearPlan(null,"))
        assertFalse(entries.contains("graph.httpCache.clear"))
        val dialog = block(prefs, "pendingClear?.let { plan ->")
        assertTrue(compact(dialog).contains("onDismissRequest={pendingClear=null}"))
        assertTrue(compact(dialog).contains("title=plan.title,summary=plan.impactText()"))
        val cancel = compact(block(dialog, "AppearanceChoice(\"取消，不删除缓存\""))
        assertEquals("pendingClear=null", cancel)
        val confirmed = block(dialog, "AppearanceChoice(\"确认清理本地读取缓存\"")
        val confirmedCode = compact(confirmed)
        assertTrue(confirmedCode.startsWith("if(busy||pendingClear!=plan)return@AppearanceChoice"))
        assertTrue(confirmedCode.contains("pendingClear=null;busy=true"))
        val io = block(confirmed, "withContext(Dispatchers.IO)")
        val ioCode = compact(io)
        assertTrue(ioCode.contains("if(plan.domain==null)graph.httpCache.clearAll()elsegraph.httpCache.clearDomain(plan.domain)"))
        assertTrue(ioCode.contains("graph.httpCache.domains()"))
        val observed = confirmedCode.indexOf("domains=next;result=plan.observedResult(next)")
        assertTrue("Observe the rescan only after the IO block", observed > confirmedCode.indexOf("graph.httpCache.domains()"))
        assertTrue(confirmedCode.contains("catch(cancel:CancellationException){throwcancel}"))
        assertTrue(confirmedCode.contains("finally{busy=false}"))
        for (method in listOf("clearAll", "clearDomain")) {
            val call = Regex("graph\\.httpCache\\.$method\\(")
            assertEquals("Only confirmed IO may call $method", 1, call.findAll(prefs).count())
            assertEquals("$method must stay inside IO", 1, call.findAll(io).count())
        }
        assertFalse(prefs.substringBefore("pendingClear?.let { plan ->").contains("graph.httpCache.clear"))
        assertFalse(dialog.contains("PrefsBackup.apply"))
        assertFalse(dialog.contains("graph.logout"))
    }

    @Test fun clearImpactAndObservedResultNeverPromiseAttemptedBytesWereFreed() {
        val category = SettingsCacheClearPlan("成绩", 4, 4096)
        val all = SettingsCacheClearPlan(null, 8, 8192)
        assertTrue(category.title.contains("成绩"))
        assertTrue(all.title.contains("全部"))
        assertTrue(category.impactText().contains("不删除凭据、偏好、草稿或学校数据"))
        assertTrue(category.impactText().contains("取消不会删除"))
        assertTrue(category.impactText().contains("离线不可用"))
        val otherDomain = listOf(CacheDomain("课表", 3, 2048))
        assertTrue(category.observedResult(otherDomain).contains("未发现目标缓存"))
        assertTrue(all.observedResult(otherDomain).contains("仍有 3 条"))
        assertTrue(category.observedResult(listOf(CacheDomain("成绩", 2, 2048))).contains("仍有 2 条"))
        assertFalse(category.observedResult(emptyList()).contains("已释放"))
        assertEquals("0 KB", settingsCacheSizeLabel(0))
        assertEquals("1 KB", settingsCacheSizeLabel(1))
        assertEquals("1.0 MB", settingsCacheSizeLabel(1024L * 1024))
    }

    @Test fun importantActionsHave48DpGrowingTargetsAndLargeTextAccessoriesMoveBelow() {
        val prefs = preferences()
        for (label in listOf("导出偏好", "从备份恢复", "取消，不删除缓存", "确认清理本地读取缓存", "确认恢复这份偏好备份", "取消恢复")) {
            val action = block(prefs, "AppearanceChoice(\"$label\"")
            assertTrue("$label must have a real callback", action.isNotBlank())
        }
        val choice = compact(block(appearance(), "internal fun AppearanceChoice("))
        assertTrue("Shared actions must grow from 48dp", choice.contains("Row(modifier.fillMaxWidth().heightIn(min=48.dp)"))
        assertTrue(choice.contains(".clickable(enabled=enabled,role=Role.Button,onClick=onClick)"))
        assertFalse(choice.contains(".height("))
        assertFalse(choice.contains("maxLines="))
        assertFalse(choice.contains("TextOverflow.Ellipsis"))
        assertTrue(compact(page()).contains("onClick=onOpenAbout"))
        assertTrue(source("main/ui/about/AboutPage.kt").contains("Text(\"资料来源\""))
        val rows = overview().substringAfter("fun SettingsOverviewRow")
        assertTrue(rows.contains("heightIn(min = 72.dp)"))
        assertTrue(rows.contains("fontScale >= 1.2f"))
        assertTrue(rows.contains("if (largeText"))
        assertFalse(rows.contains("maxLines"))
        assertFalse(rows.contains("TextOverflow.Ellipsis"))
        assertFalse(accounts().contains("SettingRow("))
    }
}
