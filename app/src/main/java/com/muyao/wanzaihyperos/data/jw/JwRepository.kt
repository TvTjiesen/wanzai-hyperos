package com.muyao.wanzaihyperos.data.jw

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.session.JwSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 教務系統的倉庫：**負責登入（兩段）與取資料**。
 *
 * ## 職責邊界
 *
 * - [JwEngine] 只管「在 WebView 裡做什麼」（登入、fetch）；
 * - 這個倉庫管「什麼時候要登入、要登入幾次、取了之後怎麼解析」。
 *
 * ## ★ 兩段登入
 *
 * 實測（`docs/B18-JW-PROBE.txt`）：功能頁在 `/jwglxt/` 上下文，
 * **只登入 `/` 會導致所有功能頁 302 回登入頁**。
 * 所以 [ensureLoggedIn] 會**兩個上下文都登入**，並把結果記在記憶體，
 * 同一次啟動內不重複登入（避免無謂的密碼傳輸）。
 *
 * ## 憑據怎麼用
 *
 * 帳密來自 [JwSession]（加密存儲）。這裡**只讀來登入，不寫任何日誌**。
 */
interface JwRepository {
    /** 目前這個功能的資料狀態。 */
    fun state(feature: JwFeature): StateFlow<ListState<JwRawPage>>
    suspend fun load(feature: JwFeature, force: Boolean = false)
}

/**
 * 一支資料介面的**原始回應**（先不解析成模型）。
 *
 * 為什麼分兩層：解析（`JwExamParser` 等）是**純函式、可單測**；
 * 抓取要 WebView（不可單測）。分開之後解析那半有測試盯著。
 */
data class JwRawPage(
    val feature: JwFeature,
    val root: JsonObject?,
    /** 原始文字長度（排查用；**不含內容**，避免個資進日誌）。 */
    val rawLength: Int,
    /** 服務端回的 HTML 而非 JSON（通常代表未登入被導去登入頁）。 */
    val looksLikeHtml: Boolean,
    /**
     * 這次失敗的**具體原因**（成功時為 null）。
     *
     * ★ 真機踩到：回應「不是 JSON 也不是預期的信封」時，第一版只把它當成
     * 「解析出 0 筆」，界面就顯示「沒有查到考試安排」——
     * **那是在替服務端撒謊**（學校明明有 22 筆）。
     * 所以把原因帶出來，界面才有辦法說真話。
     */
    val failure: JwFailure? = null,
)

/**
 * 把 [JwFailure] 當例外往上拋的載體。
 *
 * ⚠️ 它**只在倉庫內部**流動（`ensureLoggedIn` / `fetch` 拋出，`load` 立刻翻譯成
 * 全專案的 [HzcuException]）。界面永遠看不到這個型別 —— 界面讀的是
 * `ListState.Failed.error`，而那裡已經是 [HzcuException]。
 */
class JwDomainFailure(val failure: JwFailure) : Exception(failure.message)

/**
 * 教務系統的**取數失敗原因**。
 *
 * ### ★ 為什麼要有這個（真機踩到的）
 *
 * 第一版只有成功／失敗兩種結果，於是「WebView 還沒交進來」、
 * 「帳密錯」、「session 過期」在界面上**長得一模一樣**
 *（都是「登入已失效」）。
 *
 * 而這台機器上 **App 的 `Log` 輸出拿不到**（HyperOS 過濾掉了），
 * 所以「把原因講清楚」不只是體驗問題，也是**唯一的排查手段**。
 *
 * ### 與全專案 [HzcuException] 的分工
 *
 * 這個型別是**教務系統自己的領域語言**（引擎回它）。
 * 到了倉庫邊界會**翻譯**成全專案的 [HzcuException]，
 * 因為 `ListState` 的失敗機制是綁在那個 sealed class 上的 ——
 * 見 [JwFailure.toHzcuException]。
 */
sealed interface JwFailure {
    /** 隱藏 WebView 還沒就緒（組合與 layout 的競態）。 */
    data object EngineNotReady : JwFailure

    /** 帳密不對（服務端拒絕登入）。 */
    data object BadCredential : JwFailure

    /** 登入頁都載不出來（網路或站點問題）。 */
    data class PageNotLoaded(val path: String) : JwFailure

    /** 找不到登入表單的欄位（站點改版）。 */
    data class FormNotFound(val detail: String) : JwFailure

    /** 有登入但取數時被導回登入頁。 */
    data object SessionExpired : JwFailure

    /** 其它。 */
    data class Other(val detail: String) : JwFailure

    /** 給使用者看的一句話。 */
    val message: String
        get() = when (this) {
            EngineNotReady -> "页面还没准备好，请点右上角重新加载"
            BadCredential -> "账号或密码不对"
            is PageNotLoaded -> "打不开教務系統登录页（可能是网络问题）"
            is FormNotFound -> "教務系統登录页的结构变了（$detail），这个功能需要更新"
            SessionExpired -> "登录已过期"
            is Other -> detail
        }

    /**
     * 是不是「憑據本身的問題」。
     *
     * 只有這一類才該引導使用者**重新輸入帳密**；
     * 網路問題或站點改版要他去重輸密碼是沒用的，只會讓他白忙。
     */
    val isCredentialProblem: Boolean
        get() = this is BadCredential || this is SessionExpired

    /**
     * 翻成全專案的錯誤模型。
     *
     * ### 為什麼要翻譯而不是直接用
     *
     * `ListState.failWith` 綁在 [HzcuException] 上（那個 sealed class 只能在
     * `net` 包內繼承），而它有一個**很重要的既有行為**：
     * 「已經顯示的內容不會被清掉」—— 有資料時降級成「舊資料 + 標註刷新失敗」。
     * 自己另寫一套會漏掉這個行為。
     *
     * 而 `LoginExpired` 在那套機制裡會被映射成 `ListState.SessionExpired`，
     * 正好對應「憑據有問題」—— 所以**憑據類走 LoginExpired、其餘走 Parse**，
     * 語義與界面分支都對得上。
     */
    fun toHzcuException(): HzcuException =
        if (isCredentialProblem) {
            HzcuException.LoginExpired(message)
        } else {
            HzcuException.Parse(message)
        }
}

class LiveJwRepository(
    private val engine: JwEngine,
    private val session: JwSession,
    private val clock: () -> Long = System::currentTimeMillis,
) : JwRepository {

    private val guard = StaleGuard()
    private val inFlight = Mutex()

    /** 每個功能一份狀態。 */
    private val states = mutableMapOf<JwFeature, MutableStateFlow<ListState<JwRawPage>>>()

    /** 這次啟動已經登入成功的上下文。 */
    private val loggedIn = mutableSetOf<String>()

    override fun state(feature: JwFeature): StateFlow<ListState<JwRawPage>> =
        states.getOrPut(feature) { MutableStateFlow(ListState.FirstLoad) }.asStateFlow()

    private fun stateOf(feature: JwFeature): MutableStateFlow<ListState<JwRawPage>> =
        states.getOrPut(feature) { MutableStateFlow(ListState.FirstLoad) }

    override suspend fun load(feature: JwFeature, force: Boolean) {
        inFlight.withLock {
            val flow = stateOf(feature)
            val token = guard.begin()
            flow.value = flow.value.startRefresh()

            val result = runCatching {
                ensureLoggedIn()
                fetch(feature)
            }

            if (!guard.isCurrent(token)) return
            flow.value = result.fold(
                onSuccess = { page ->
                    // ★ 三種「不是正常資料」的情況要**分開且說實話**：
                    //   1) 服務端回 HTML（通常是被導去登入頁）→ 登入失效
                    //   2) 明確的失敗原因（回應不是預期信封）→ 照實顯示
                    //   3) 正常 JSON 但 items 真的空 → 才是「沒有資料」
                    // 第一版把 2) 混進 3)，界面就顯示「沒有查到考試安排」——
                    // **那是替服務端撒謊**（學校明明有 22 筆）。真機踩過。
                    when {
                        page.looksLikeHtml -> {
                            loggedIn.clear()
                            flow.value.failWith(JwFailure.SessionExpired.toHzcuException())
                        }

                        page.failure != null -> {
                            flow.value.failWith(page.failure.toHzcuException())
                        }

                        else -> flow.value.settleFirstPage(listOf(page), clock()) { it.feature }
                    }
                },
                onFailure = { e ->
                    // 帶原因的用原因；其餘當 Other。
                    // **訊息會顯示在界面上** —— 這台機器上 App 的 Log 拿不到，
                    // 界面就是唯一的排查窗口。
                    val failure: JwFailure = (e as? JwDomainFailure)?.failure
                        ?: JwFailure.Other(e.message ?: "取數失敗")
                    flow.value.failWith(failure.toHzcuException())
                },
            )
        }
    }

    /**
     * 確保兩個上下文都登入。
     *
     * ⚠️ 帳密缺一不可；缺了就拋 [JwDomainFailure]（引導去登入），
     * **不要**默默回空資料 —— 那會讓界面顯示「沒有考試」而其實是沒登入。
     */
    private suspend fun ensureLoggedIn() {
        val account = session.account()
        val password = session.password()
        if (account.isNullOrBlank() || password.isNullOrBlank()) {
            throw JwDomainFailure(JwFailure.SessionExpired)
        }

        // ★ 先等 WebView 交進來（見 [JwEngine.awaitAttached] 的競態說明）。
        //   少了這步，第一幀進來時 webView 還是 null，login() 會失敗。
        if (!engine.awaitAttached()) {
            throw JwDomainFailure(JwFailure.EngineNotReady)
        }

        for (path in listOf("/xtgl/login_slogin.html", JwEngine.JWGLXT_LOGIN_PATH)) {
            if (loggedIn.contains(path)) continue
            val failure = engine.login(account, password, path)
            if (failure != null) {
                // ★ 把**具體原因**帶到界面 —— 少了它，「WebView 沒就緒」和
                //   「密碼錯」在使用者眼裡一模一樣（真機踩過）。
                throw JwDomainFailure(failure)
            }
            loggedIn.add(path)
        }
        session.markLogin(clock())
    }

    /** 抓一支功能的資料介面。 */
    private suspend fun fetch(feature: JwFeature): JwRawPage {
        if (!feature.hasDataApi) {
            // 沒有 doType=query 的功能（如課表）：先回頁面 HTML，
            // 由呼叫方決定怎麼處理。**不假裝有 JSON**。
            val html = engine.fetchText(feature.pageUrl(JwEngine.BASE)).orEmpty()
            return JwRawPage(feature, null, html.length, looksLikeHtml = true)
        }
        val body = buildFormBody()
        val text = engine.fetchText(feature.dataUrl(JwEngine.BASE), body)
        // evalJsDecoded 在 WebView 未就緒／求值失敗時回 null。
        // ★ 這時**不能**當成「沒有資料」—— 那會顯示「沒有查到考試安排」，
        //   是對使用者的誤導（真機第一版就是這樣）。
        if (text == null) {
            return JwRawPage(
                feature, null, 0, looksLikeHtml = false,
                failure = JwFailure.Other("取数没有返回内容（页面可能还在加载），请点右上角重试"),
            )
        }
        if (text.startsWith("__ERR__")) {
            return JwRawPage(
                feature, null, 0, looksLikeHtml = false,
                failure = JwFailure.Other("請求失敗：${text.removePrefix("__ERR__").take(60)}"),
            )
        }
        val trimmed = text.trimStart()
        val isHtml = trimmed.startsWith("<")
        if (isHtml) {
            // 被導去登入頁（或別的頁面）→ 由呼叫方當成登入失效處理。
            return JwRawPage(feature, null, text.length, looksLikeHtml = true)
        }
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        // ★ 解析不出 JSON 物件 → 這是**解析失敗**，不是「沒有資料」。
        if (root == null) {
            return JwRawPage(
                feature, null, text.length, looksLikeHtml = false,
                failure = JwFailure.Other("教務系統回的格式看不懂（${text.length} 字元），可能需要更新"),
            )
        }
        return JwRawPage(feature, root, text.length, looksLikeHtml = false)
    }

    /**
     * 資料介面的表單包體。
     *
     * 逐字照抄真機實測成功的組合（`docs/B18-JW-PROBE.txt`）：
     * `xnm`/`xqm` 留空＝**全部學年學期**（不篩選），這正是「看全部成績」要的。
     */
    private fun buildFormBody(): String = listOf(
        "xnm=",
        "xqm=",
        "_search=false",
        "nd=${clock()}",
        "queryModel.showCount=$PAGE_SIZE",
        "queryModel.currentPage=1",
        "queryModel.sortName=",
        "queryModel.sortOrder=asc",
    ).joinToString("&")

    private companion object {
        /** 一次要多少筆。實測 50 筆的回應約 46 KB，可接受。 */
        const val PAGE_SIZE = 50
    }
}
