package com.muyao.wanzaihyperos.ui.main.pages

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B4-06（设置页账号收进二级页）。判据是三条**可扫**的，不是"我看着像"：
 *
 *  ① 设置首页只剩**一行聚合入口**（六源的 `forEach` 不在那一页了）；
 *  ② 六源在二级页**逐条**渲染（行渲染函数与脚注都还在，且文案来自数据层）；
 *  ③ 跳转（登录/教务/智慧公寓/应用）一个都不少 —— 收到二级页不能让任何一项到不了。
 */
class AccountsPageTest {

    private val pages = "src/main/java/com/muyao/wanzaihyperos/ui/main/pages"
    private val settings = File("$pages/SettingsSectionPage.kt")
    private val accounts = File("$pages/AccountsPage.kt")
    private val accountsSources =
        File("src/main/java/com/muyao/wanzaihyperos/data/account/AccountSources.kt")
    private val root = File("src/main/java/com/muyao/wanzaihyperos/WanzaiRoot.kt")

    /** 去掉注释：说明里天生会写繁体术语，判据不该因此变红。 */
    private fun code(text: String): String =
        Regex("""/\*[\s\S]*?\*/""").replace(text, "").lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun read(f: File): String {
        assertTrue("找不到 ${f.absolutePath}", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    @Test
    fun `设置首页只剩一行聚合入口`() {
        val text = read(settings)
        assertFalse(
            "设置首页又在逐条渲染账号源了（B4-06 要求收进二级页）：`accountSources.forEach` 应只出现在 AccountsPage",
            "accountSources.forEach" in text,
        )
        assertTrue("设置首页少了聚合入口的回调 onOpenAccounts", "onOpenAccounts" in text)
        assertTrue("入口必须使用共享本机配置事实", "accountConfigurationSummary(accountSources)" in code(text))
        val overview = code(read(File("src/main/java/com/muyao/wanzaihyperos/ui/main/pages/redesign/SettingsOverview.kt")))
        assertTrue("卡片须显示配置数量和不可验证在线的边界", "configuration.countText()" in overview && "configuration.boundaryText()" in overview)
        val sources = code(read(File("src/main/java/com/muyao/wanzaihyperos/data/account/AccountSources.kt")))
        assertTrue("已保存凭据和已取得待激活code都属于配置", "AccountStatus.LoggedIn || it.status == AccountStatus.ObtainedNotActivated" in sources)
        assertTrue("未知与网页内会话须独立计数", "unknownCount = sources.count { it.status == AccountStatus.Unknown }" in sources && "webOnlyCount = sources.count { it.status == AccountStatus.InWebOnly }" in sources)
        assertTrue("文案不能将配置声称在线", "配置不等于在线" in sources && "不计入已配置" in sources)
        assertFalse(
            "副标题又拖回「逐项查看与重新登录」那一截了 —— 真机上它会折出孤零零两个字",
            // 用 code() 而不是原文：上面那条注释自己就写着这段旧文案。
            "逐项查看与重新登录" in code(text),
        )
    }

    @Test
    fun `六源在二级页仍然逐条可达`() {
        val text = read(accounts)
        assertTrue("二级页没在逐条渲染账号源", "sources.forEach" in text)
        assertTrue("二级页必须复用同一行渲染（禁抄一份）", "AccountSourceRow(" in text)
        assertTrue("脚注（哪一项要自己粘贴链接这类真话）丢了", "accountFootnoteText" in text)
        // 文案来自数据层：账号名不许在这一页写死
        listOf("一卡通", "学在城院", "教务系统", "智慧公寓", "钉钉").forEach { name ->
            assertFalse("二级页把账号名 `$name` 写死了 —— 名称必须来自 AccountSources", name in text)
        }
    }

    @Test
    fun `四条跳转一条都不能少`() {
        val text = read(accounts)
        listOf(
            "AccountTarget.PortalLogin -> onOpenLogin()",
            "AccountTarget.JwPage -> onOpenJw()",
            "AccountTarget.ZhgyPage -> onOpenZhgy()",
            "AccountTarget.AppsTab -> onOpenApps()",
            "AccountTarget.None -> Unit",
        ).forEach { branch ->
            assertTrue("二级页少了这条跳转：$branch", branch in text)
        }
    }

    /**
     * 文案简体（`docs/COPY-TERMS.md`）。
     *
     * ⚠️ 上一版只扫 `AccountsPage.kt`，且表里混着「密/定/示」这种**简繁同形**的字 ——
     * 既漏文件又误报。现在：只列繁体**独有**字、剥掉注释、扫三个真文案源。
     */
    @Test
    fun `三处文案源都是简体`() {
        val traditional =
            "帳號設觀連頁務學園點選這裡錄顯開關閉與為階級碼證書車馬長門問聞間題東時電腦網絡軟體檔轉換語圖標顏濾鏡動畫靜態螢觸聲機藍線記憶儲載刪編輯確認讀寫錯誤幫項單內啟鎖訊"
        listOf(accounts, settings, accountsSources).forEach { f ->
            val text = code(read(f))
            val hit = traditional.filter { it in text }
            assertTrue("${f.name} 里出现了繁体字：$hit", hit.isEmpty())
        }
    }

    /**
     * 快照刷新键：`LaunchedEffect(Unit)` 在这一页是**错的** ——
     * Miuix 导航不会把被盖住的那一屏移出组合，从登录页返回时它不会重新取快照。
     * 设置首页的聚合计数更隐蔽：**只 key `uiState.loggedIn` 不够**（它只会 false→true），
     * 已登录门户之后再登教务 / CJH52 / 智慧公寓都不重算 ⇒ 必须把返回栈深度也传进去。
     */
    @Test
    fun `快照必须按回到这一页的键重取`() {
        val text = read(accounts)
        assertFalse("AccountsPage 又用 LaunchedEffect(Unit) 取快照了（返回时不会重取）", "LaunchedEffect(Unit) {" in text)
        assertTrue(
            "AccountsPage 缺 refreshKey（返回栈深度）这个刷新键",
            "refreshKey" in text && "LaunchedEffect(refreshKey" in text,
        )
        val settingsText = read(settings)
        assertTrue(
            "设置首页的聚合计数必须**同时** key 登录态与返回栈深度",
            "LaunchedEffect(graph, uiState.loggedIn, refreshKey, pageActive, entryOwner)" in settingsText,
        )
        assertTrue("设置首页没有 refreshKey 参数", "refreshKey: Int = 0" in settingsText)
        assertTrue(
            "WanzaiRoot 没把栈深度传进去（缺省 0 等于没有这个键）",
            "navDepth = backStack.size" in read(root),
        )
    }

    /**
     * 退出登录：`AppGraph.logout()` 在 2026-10-23 之前**全工程 0 调用点**
     * （设置页注释里自己写着这件事）。现在它必须挂在一颗按钮的 onClick 上。
     */
    @Test
    fun `退出登录有入口且挂在按钮上`() {
        val text = read(accounts)
        assertTrue("二级页没有调用 graph.logout()", "graph.logout()" in text)
        val lines = text.lines()
        val i = lines.indexOfFirst { "graph.logout()" in it }
        assertTrue("找不到 graph.logout() 那一行", i >= 0)
        val near = lines.subList(maxOf(0, i - 12), i + 1).joinToString("\n")
        assertTrue(
            "graph.logout() 不在按钮的 onClick 链上（难道是自动退出登录？）",
            "onClick = if (loggingOut) null else" in near,
        )
    }

    /**
     * 页面注册守卫：`entry<Route.AccountSources>` 必须真的在 `WanzaiRoot` 里注册。
     *
     * 这条是补来的 —— 上一版没有任何测试守它：把那 12 行删掉，**六条测试全绿**，
     * 而用户点「登录与账号」根本没有反应（页面进不去，谁也测不出来）。
     */
    @Test
    fun `账号二级页必须在导航里注册`() {
        val rootText = read(root)
        assertTrue("WanzaiRoot 里没有 entry<Route.AccountSources>（页面进不去）",
            "entry<Route.AccountSources>" in rootText)
        assertTrue("设置页的聚合入口没有接到 Route.AccountSources",
            "onOpenAccounts = { backStack.add(Route.AccountSources) }" in rootText)
        val routes = read(File("src/main/java/com/muyao/wanzaihyperos/nav/Routes.kt"))
        assertTrue(
            "AccountSources 漏了 @Serializable（rememberNavBackStack 持久化栈要它）",
            Regex("""@Serializable\s+data object AccountSources : Route""").containsMatchIn(routes),
        )
    }
}
