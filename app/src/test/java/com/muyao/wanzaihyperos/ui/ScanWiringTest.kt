package com.muyao.wanzaihyperos.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 全局扫一扫的**接线**守卫。
 *
 * 判据本身在 `data/tronclass/ScanRouter.kt`（那边有单测），这里钉的是「有没有真的接上」——
 * 一段纯函数写得再对，没人调用也是白写。这类缺陷在真机上表现为
 * 「点了扫一扫没反应」或「扫到签到码回到签到页却什么都没发生」，
 * 而单测全绿，光看测试结果发现不了。
 */
class ScanWiringTest {

    private fun src(path: String): String =
        File("src/main/java/com/muyao/wanzaihyperos/$path").readText(Charsets.UTF_8)

    @Test
    fun `应用页的扫一扫真的有入口`() {
        val apps = src("ui/main/pages/AppsSectionPage.kt")
        assertTrue("应用页没有 ScanEntry 这个入口", apps.contains("ScanEntry(onClick = onOpenScan)"))
        assertTrue(
            "扫一扫入口与搜索行必须并排（独立成第二条灰行会让人分不清哪条是搜）",
            apps.contains("SearchEntry(onClick = onOpenSearch, modifier = Modifier.weight(1f))"),
        )
        assertTrue("壳没把 onOpenScan 传进来", apps.contains("onOpenScan: () -> Unit = {}"))
    }

    @Test
    fun `扫到的签到码必须在跳转前放进中转槽`() {
        val root = src("WanzaiRoot.kt").replace("\r\n", "\n")
        assertTrue("没有注册 Route.Scanner（点了扫一扫会没反应）", root.contains("entry<Route.Scanner>"))
        val putAt = root.indexOf("ScanHandoff.put(raw)")
        val addAt = root.indexOf("backStack.add(Route.Rollcall())", putAt.coerceAtLeast(0))
        assertTrue("扫到签到码没有放进中转槽", putAt > 0)
        assertTrue("必须先 put 再跳转，否则签到页 take() 拿到的是空的", addAt > putAt)
    }

    @Test
    fun `签到页必须把中转槽取走并挂到选择卡上`() {
        val page = src("ui/tronclass/RollcallStationPage.kt")
        assertTrue("签到页没有取中转槽 ⇒ 从扫一扫带过来的码会丢", page.contains("ScanHandoff.take()"))
        // 2026-11-08 改判据：四种入口（相机 / 相册 / 粘贴 / 全局扫一扫）现在收成**一条管道**
        // `handleScanned(raw, target)`，中转槽也必须从这条管道走 —— 上一版这里自己解析一遍、
        // 只在 `pending == null` 时才挂，和相机那条路行为不一致。所以这里钉的不是
        // 「有没有 `ScanPending(...)` 这个字面量」，而是「有没有进同一条管道」。
        assertTrue(
            "中转槽取到的码没有进 handleScanned ⇒ 与相机/相册那几条路行为分叉",
            page.contains("handleScanned(handed, null)"),
        )
        assertTrue(
            "取到的码必须挂成 ScanPending（只挑不写），不能直接提交",
            page.contains("pending = ScanPending("),
        )
        assertFalse(
            "Handoff 不许绕过选择卡直接调用 answer —— 提交那一键必须由他按",
            Regex("""ScanHandoff[\s\S]{0,400}repository\.answer\(""").containsMatchIn(page),
        )
    }

    @Test
    fun `相机和确认卡先吃局部返回且保留来源页面`() {
        val page = src("ui/tronclass/RollcallStationPage.kt")
        assertTrue(page.contains("BackHandler(enabled = scanTarget != null || pending != null)"))
        assertTrue(page.contains("scanTarget != null -> { closeCamera(); true }"))
        assertTrue(page.contains("pending != null -> { closePending(); true }"))
        assertTrue(page.contains("onBack = { if (localBack?.invoke() != true) onExit() }"))
        assertTrue(page.indexOf("AppList(modifier") < page.indexOf("if (scanTarget != null)"))
        assertTrue(page.contains("confirmationFocus.requestFocus()"))
        assertTrue(page.contains("source.anchor.focusRequester.requestFocus()"))
        val scanner = src("ui/tronclass/QrScannerPage.kt")
        assertTrue(scanner.contains("BackHandler { latestExit() }"))
        assertTrue(scanner.contains("text != null && !reported"))
    }

    @Test
    fun `每个扫码入口捕获真实按钮且失效来源不能飞回`() {
        val page = src("ui/tronclass/RollcallStationPage.kt")
        listOf("quickAnchor", "rowAnchor", "albumAnchor", "pasteAnchor").forEach { anchor ->
            assertTrue(page.contains("$anchor.capture(windowStamp)"))
        }
        assertTrue(page.contains("it.boundsInWindow()"))
        assertTrue(page.contains("coordinates?.isAttached == true"))
        assertTrue(page.contains("onDispose { anchor.coordinates = null; anchor.placedBounds = null }"))
        assertTrue(page.contains("!sourceInvalid && scanSourceReusable("))
        assertFalse(page.contains("scanPanelBounds"))
        assertTrue(page.contains("handleScanned(handed, null)"))
    }

    @Test
    fun `确认卡仅退场结束移除且文字不缩放不自动提交`() {
        val page = src("ui/tronclass/RollcallStationPage.kt")
        val close = page.substringAfter("fun closePending()").substringBefore("fun closeCamera()")
        assertTrue(close.contains("pendingClosing = true"))
        assertFalse(close.contains("pending = null"))
        val onClosed = page.substringAfter("onClosed = {").substringBefore("fun closePending()")
        assertTrue(onClosed.contains("pending = null"))
        assertTrue(page.contains("if (pendingClosing || busyId != null) return@Button"))
        val reveal = src("ui/tronclass/TronScanReveal.kt")
        assertTrue(reveal.contains("if (closing) latestClosed()"))
        assertTrue(reveal.contains("sourceIsValid: () -> Boolean,"))
        assertFalse(reveal.contains("{ true }"))
        assertFalse(reveal.contains("scaleX"))
        assertFalse(reveal.contains("scaleY"))
        assertFalse(reveal.contains("submitTo("))
        assertFalse(reveal.contains("repository.answer("))
    }

    @Test
    fun `扫码页的标题不许写死成签到`() {
        val scanner = src("ui/tronclass/QrScannerPage.kt")
        assertTrue(
            "QrScannerPage 的标题没参数化 ⇒ 从扫一扫进来会读到「扫码签到」，骗人",
            scanner.contains("title: String = \"扫码签到\""),
        )
        assertTrue("副标题没参数化", scanner.contains("hint: String = "))
        assertTrue(
            // 稿 v2.1 `scanner.png` 顶栏就是「通用扫一扫」——原来这里钉的是「扫一扫」，
            // 那是照旧版板子写的。标题全称才说得清它跟签到站里那个扫码器不是一回事。
            "通用扫码页自己的标题必须是「通用扫一扫」",
            src("ui/tronclass/ScannerPage.kt").contains("title = \"通用扫一扫\""),
        )
    }
}
