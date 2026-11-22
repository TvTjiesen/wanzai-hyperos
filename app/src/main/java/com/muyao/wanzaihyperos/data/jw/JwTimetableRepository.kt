package com.muyao.wanzaihyperos.data.jw

import android.content.Context
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.SecondaryGuard
import com.muyao.wanzaihyperos.data.repo.finishRefresh
import com.muyao.wanzaihyperos.data.repo.retainCachedOnFailure
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.DiagnosticPolicy
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.session.JwSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 教務系統的**課表**倉庫：兩段登入 → 先試 JSON → 再讀渲染好的表格。
 *
 * ## 為什麼要兩條路
 *
 * 實測這個部署的課表頁**沒有** `doType=query` 的 JSON 介面
 *（`docs/B18-WALKTHROUGH.md` §11 記的），但正方的同名介面在别的版本裡是有的，
 * 而且它比 DOM 穩定得多（有 `kcmc`/`jsxm`/`cdmc`/`zcd` 這種具名欄位）。
 * 所以：**能拿到 JSON 就用 JSON，拿不到就讀表格**。
 * 兩條路的結果都進同一個 [JwTimetableResult]，界面上標明用的是哪一條 ——
 * 出問題時一眼知道該查哪條，而不是「課表是空的」三個字。
 *
 * ## ★ 與「考試/成績」共用同一個 WebView 與同一份登入
 *
 * 見 [JwLoginCoordinator]。兩段登入只要有一處忘了第二段，
 * 表現就是所有功能頁 302 回登入頁 —— 那種坑不該在兩個倉庫裡各踩一次。
 *
 * ⚠️ 隱性前提：[JwEngine] 是**單例共用**的，所以「刷課表」與「刷考試」
 * 同時進行時會互相把頁面導走。實作上用 [inFlight] 串行化本倉庫自己的刷新，
 * 跨倉庫的並發由界面保證（兩個功能不在同一屏）。
 */
interface JwTimetableRepository {
    val state: StateFlow<ListState<JwTimetableResult>>
    suspend fun refresh()

    /**
     * 「这一趟教务爬取**真的在跑**」—— 界面只有读到 true 才可以写「正在取教务课表」。
     *
     * ★ 为什么不能只看 `ListState`（独立审计 v3 #4 第 2 轮必修第 4 条）：
     *   `ListState.startRefresh()` 在还没有内容时**原样返回 FirstLoad**（ListState.kt:130），
     *   所以 `FirstLoad` 既可能是"正在爬"，也可能是"这台机子上根本还没人发起过"
     *   （教务那份要走课表页那只隐藏的 WebView，首页没有它）。
     *   把后一种也说成"正在取"，就是界面在说数据证明不了的话。
     */
    val isCrawling: androidx.compose.runtime.State<Boolean>
}

class LiveJwTimetableRepository(
    private val engine: JwEngine,
    private val session: JwSession,
    private val clock: () -> Long = System::currentTimeMillis,
    private val appContext: Context? = null,
    private val epoch: AccountEpoch = AccountEpoch(),
    private val diagnostics: DiagnosticPolicy? = null,
) : JwTimetableRepository {

    private val inFlight = Mutex()
    private val login = JwLoginCoordinator(engine, session, clock, epoch)

    private val _state = MutableStateFlow<ListState<JwTimetableResult>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<JwTimetableResult>> = _state.asStateFlow()

    override val isCrawling = androidx.compose.runtime.mutableStateOf(false)
    private var crawlingToken: Long? = null
    private val guard = SecondaryGuard(epoch, session) {
        _state.value = ListState.FirstLoad
        isCrawling.value = false
        crawlingToken = null
    }
    private val snapshots = OwnerSnapshotStore(snapshotFile(), "jw", epoch, session::account)

    init {
        epoch.registerReset {
            guard.invalidate()
            _state.value = ListState.FirstLoad
            isCrawling.value = false
            crawlingToken = null
        }
        seedFromSnapshot(epoch.token())
    }

    override suspend fun refresh() {
        val token = guard.begin()
        val ownerToken = epoch.token()
        var credentialRevision = -1L
        if (!guard.publish(token) { credentialRevision = session.credentialRevision() }) return
        try {
            inFlight.withLock {
                if (!guard.publish(token) { isCrawling.value = true; crawlingToken = token }) return
                // ── 首屏先播**上一次爬到的那份**（业主 2026-10-20：「我不想每次进来都要看到他重新加载一遍」）──
                //
                // 教务这一屏最慢：它要走一次网页登录、再把页面自己渲染完（`RENDER_SETTLE_MS`），
                // 十几秒是常态。以前這十幾秒屏幕上是一句「正在取」—— 可他上次的課表就在手機裡。
                //
                // 為什麼這樣是誠實的：快照標 `stale = true`，界面那行「X 分钟前更新 · 点这里重取」
                // 就是靠這個標出來的；而且網絡回來後會整屏換成新的。
                // 爬失敗時 [failWith] 也不會把這份掀掉（它保留 Content + stale + 錯誤原因），
                // 所以「舊的還在」與「新的沒到」兩件事同時說得清。
                if (_state.value is ListState.FirstLoad) seedFromSnapshot(ownerToken, token)
                if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return

                val result = engine.run { catchingActive {
                    login.ensureLoggedIn(ownerToken)

                    // ── 路徑 ①：JSON 介面 ──────────────────────────────────
                    val jsonText = engine.fetchText(jsonUrl(), formBody())
                    dumpDebug(token, ownerToken, "json", jsonText)
                    val viaJson = jsonText?.takeIf { looksLikeJson(it) }?.let { text ->
                        catchingActive { Json.parseToJsonElement(text) }
                            .getOrNull()
                            ?.let { JwTimetableParser.parseJson(it) }
                            ?.takeIf { it.slots.isNotEmpty() }
                    }
                    if (viaJson != null) {
                        saveSnapshot(token, ownerToken, Json.encodeToString(viaJson))
                        viaJson.copy(rawLength = jsonText?.length ?: 0)
                    } else {
                        // ── 路徑 ②：讀渲染好的表格 ────────────────────────────
                        // 先把它自己的課表頁載進來（表格是頁面自己渲染的）。
                        engine.load(JwFeature.TIMETABLE.pageUrl(JwEngine.BASE))
                        // 這個站的課表是頁面載完之後再用 XHR 填進表格的
                        //（JwEngine.load 只等 onPageFinished，不等 XHR），
                        // 所以這裡必須留時間 —— 少了這段會讀到**空表格**，
                        // 而空表格在界面上和「本週沒課」長得一模一樣。
                        kotlinx.coroutines.delay(RENDER_SETTLE_MS)
                        val dom = engine.eval(JwTimetableDom.SCRIPT)
                        dumpDebug(token, ownerToken, "dom", dom)
                        if (dom.isNullOrBlank()) {
                            JwTimetableResult(
                                emptyList(),
                                listOf("教务课表页面没读到内容（可能还在加载，点右上角重试）"),
                                "教务网页", 0,
                            )
                        } else {
                            val parsed = catchingActive { Json.parseToJsonElement(dom) }
                                .map { JwTimetableParser.parseDom(it) }
                                .getOrElse {
                                    JwTimetableResult(
                                        emptyList(),
                                        listOf("教务课表页面读回来的格式看不懂"),
                                        "教务网页", dom.length,
                                    )
                                }
                            // ★ 只有真的解析出课程才落盘（2026-10-21 真机）：
                            //   教务页面自己抛错时，DOM 脚本返回的是
                            //   `{"error":"这个页面里没有任何表格"}`；旧代码在读之前就
                            //   `saveSnapshot(token, ownerToken, dom)`，于是**一次失败把上一次那份好课表盖掉了**，
                            //   首页随即退回学在城院那份（同一节次出现两门课）。
                            //   失败不该有写快照的权限。
                            if (parsed.slots.isNotEmpty() || parsed.unparsed.isEmpty()) {
                                saveSnapshot(token, ownerToken, Json.encodeToString(parsed))
                            }
                            parsed
                        }
                    }
                } }
                guard.publish(token) {
                    _state.value = result.fold(
                        onSuccess = { r ->
                            // ★ 独立审计（v3 #4 第 1 轮）抓到：**读到空表格被当成"有结论"**。
                            //   `dom.isNullOrBlank()` 与"格式看不懂"这两条路都返回
                            //   `slots = emptyList()` + 一句 unparsed（那是它自己记的"没认出来的原文"），然后照样 `settleFirstPage`
                            //   ⇒ 状态永远是 Content、永远不可能是 Empty/Failed，
                            //   于是 `pickSlotSource` 判成"教务有结论且没货"，**常驻**画学在城院那份，
                            //   而那条横幅还不问兜底那份有没有货。
                            //   现在：**有槽位**才算有结论；**没槽位且自己报了警告**=这一次没读到，
                            //   按失败结算（`failWith` 会保留已有快照那份 Content + stale，
                            //   所以"旧的还在、新的没到"两件事仍然说得清，不会闪回另一份）。
                            //   真的本周没课（解析成功、0 槽、无警告）仍然算有结论 → 走 Empty 那条。
                            if (r.slots.isEmpty() && r.unparsed.isNotEmpty()) {
                                _state.value.failWith(
                                    JwFailure.Other(r.unparsed.first()).toHzcuException(),
                                )
                            } else {
                                // ★ 真没课（解析成功、0 槽、无警告）现在**真的会落进 Empty**：
                                //   `settleFirstPage(listOf(r))` 那个单元素包装以前让 `ListState.Empty`
                                //   在教务这条链上永远不可达，于是界面上一堆"Empty 那一支"的措辞与注释
                                //   都是没发生过的话（第 3 轮必修 5 的后半）。
                                _state.value.settleFirstPage(
                                    if (r.slots.isEmpty()) emptyList() else listOf(r),
                                    clock(),
                                ) { r.strategy }
                            }
                        },
                        onFailure = { e ->
                            val failure: JwFailure = (e as? JwDomainFailure)?.failure
                                ?: JwFailure.Other(e.message ?: "取数失败")
                            if (failure.isCredentialProblem) login.clear()
                            retainCachedOnFailure(_state.value, failure.toHzcuException())
                        },
                    )
                }
            }
        } finally {
            epoch.publishData(ownerToken) {
                session.publishCredential(credentialRevision) {
                    if (crawlingToken == token) {
                        _state.value = _state.value.finishRefresh()
                        isCrawling.value = false
                        crawlingToken = null
                    }
                }
            }
        }
    }

    private fun jsonUrl(): String =
        JwEngine.BASE + "/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=" + JwFeature.TIMETABLE.code + "&doType=query"

    /**
     * 課表介面的表單包體。
     *
     * ⚠️ `xh` 用**登錄帳號本身**（正方的學號就是帳號）——
     * 不另找一個「學生 id」欄位：那個要再打一支介面才有，而帳號錯的話
     * 這支介面只會回空課表，界面上就成了「本週沒課」的謊。
     * `xn`/`xqm` 留空＝不篩學年學期（與考試/成績那兩支同一個做法）。
     */
    private fun formBody(): String {
        val xh = session.account().orEmpty()
        return listOf(
            "xh=${urlEncode(xh)}",
            "xn=",
            "xqm=",
            "cdlxid=",
            "jsid=",
            "did=",
            "kckxld=",
            "_search=false",
            "nd=${clock()}",
            "queryModel.showCount=200",
            "queryModel.currentPage=1",
            "queryModel.sortName=",
            "queryModel.sortOrder=asc",
            "time=0",
        ).joinToString("&")
    }

    private fun urlEncode(v: String): String =
        java.net.URLEncoder.encode(v, "UTF-8")

    /** JSON 判據：去空白後以 `{` 或 `[` 開頭。回 HTML（被導去登入頁）時走 ②。 */
    private fun looksLikeJson(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("{") || t.startsWith("[")
    }

    private fun snapshotFile(): java.io.File? =
        appContext?.let { java.io.File(it.filesDir, SNAPSHOT) }

    private fun saveSnapshot(requestToken: Long, ownerToken: Long, content: String?) {
        if (content.isNullOrBlank()) return
        guard.publish(requestToken) { snapshots.write(ownerToken, content) }
    }

    private fun seedFromSnapshot(ownerToken: Long, ticket: Long = guard.begin()) {
        guard.publish(ticket) {
            val text = snapshots.read(ownerToken)?.takeIf { it.isNotBlank() } ?: return@publish
            val parsed = catchingActive {
                val root = Json.parseToJsonElement(text)
                when {
                    root.asObjectOrNull()?.containsKey("slots") == true -> Json.decodeFromString<JwTimetableResult>(text)
                    root.asObjectOrNull()?.containsKey("rows") == true -> JwTimetableParser.parseDom(root)
                    else -> JwTimetableParser.parseJson(root)
                }
            }.getOrNull()?.takeIf { it.slots.isNotEmpty() || it.unparsed.isEmpty() } ?: return@publish
            val meta = ListMeta(updatedAtMillis = snapshotFile()?.lastModified(), stale = true)
            _state.value = if (parsed.slots.isEmpty()) ListState.Empty(meta) else
                ListState.Content(listOf(parsed), meta)
        }
    }

    private fun dumpDebug(requestToken: Long, ownerToken: Long, which: String, content: String?) {
        guard.publish(requestToken) { epoch.publishData(ownerToken) { diagnostics?.record("jw", which, content.orEmpty()) } }
    }

    private companion object {
        /** 等課表頁面把表格填完的時間（見上面註釋）。 */
        const val RENDER_SETTLE_MS = 2200L

        /** 上次爬到的課表原文（首屏拿它立刻畫出來）。 */
        const val SNAPSHOT = "jw_timetable_snapshot.json"
    }
}
