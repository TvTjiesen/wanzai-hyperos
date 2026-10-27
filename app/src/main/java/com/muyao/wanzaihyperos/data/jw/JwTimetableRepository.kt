package com.muyao.wanzaihyperos.data.jw

import android.content.Context
import android.content.pm.ApplicationInfo
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.session.JwSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * 只給**調試版**落一份原始回應用（見 [dumpDebug]）。
     *
     * ★ 為什麼要這個（业主 2026-10-19：「你课表和时间对不上」）：
     *   他学校那张表**到底给不给「第 N 节 = 几点」**，我没有真机证据 ——
     *   模拟器上没有他的教务登录态，而我不会代填凭据。
     *   没有原文就只能靠猜写解析器，而猜错的课表会让他**在错的时间去错的教室**。
     *   所以 debug 构建把两条路的原文写进 App 私有目录，我用 `run-as` 取回来
     *   照**真实字节**写解析器与单测；release 里这个开关根本不成立（见判据）。
     */
    private val appContext: Context? = null,
) : JwTimetableRepository {

    private val guard = StaleGuard()
    private val inFlight = Mutex()
    private val login = JwLoginCoordinator(engine, session, clock)

    private val _state = MutableStateFlow<ListState<JwTimetableResult>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<JwTimetableResult>> = _state.asStateFlow()

    // ★ 構造时就播快照，不等第一次 refresh（业主 2026-10-20：「课表里面还有垃圾没删完，
    //   会闪过一些错误的数据，估计是学在城院里的数据」）：
    //   教务这一份**只在課表頁才会去爬**（登录要走那一页藏着的 WebView），
    //   而首页也读它。以前首页冷启动时教务还是 FirstLoad ⇒ 只能拿学在城院那一份先画，
    //   等他进过一次課表、教务回来后又被整块换掉 —— 那一下就是他说的"闪过错误的数据"。
    //   把播种提前到构造，首页第一眼看到的就是教务（上次那份，标 stale）。
    init {
        seedFromSnapshot()
    }

    override val isCrawling = androidx.compose.runtime.mutableStateOf(false)

    override suspend fun refresh() {
        // ★ 第 4 轮独立审计必修 5：整段套一层 `finally`。
        //   以前 `isCrawling.value = false` 只写在 fold 之后那一行，而 fold 的 lambda 里
        //   还有会抛的代码（`login.clear()`、`toHzcuException()`），一抛就跳过那行 ——
        //   而 `AppGraph.fetch` 外层是 `runCatching {}` 无声吞掉 ⇒ `isCrawling` 永远 true
        //   ⇒ 首页与外观预览永远 WAIT、永远不画格子。正面证据必须和退出路径绑在一起。
        try {
            inFlight.withLock {
            val token = guard.begin()
            isCrawling.value = true
            // ── 首屏先播**上一次爬到的那份**（业主 2026-10-20：「我不想每次进来都要看到他重新加载一遍」）──
            //
            // 教务这一屏最慢：它要走一次网页登录、再把页面自己渲染完（`RENDER_SETTLE_MS`），
            // 十几秒是常态。以前這十幾秒屏幕上是一句「正在取」—— 可他上次的課表就在手機裡。
            //
            // 為什麼這樣是誠實的：快照標 `stale = true`，界面那行「X 分钟前更新 · 点这里重取」
            // 就是靠這個標出來的；而且網絡回來後會整屏換成新的。
            // 爬失敗時 [failWith] 也不會把這份掀掉（它保留 Content + stale + 錯誤原因），
            // 所以「舊的還在」與「新的沒到」兩件事同時說得清。
            if (_state.value is ListState.FirstLoad) seedFromSnapshot()
            _state.value = _state.value.startRefresh()

            val result = runCatching {
                login.ensureLoggedIn()

                // ── 路徑 ①：JSON 介面 ──────────────────────────────────
                val jsonText = engine.fetchText(jsonUrl(), formBody())
                dumpDebug("json", jsonText)
                val viaJson = jsonText?.takeIf { looksLikeJson(it) }?.let { text ->
                    runCatching { Json.parseToJsonElement(text) }
                        .getOrNull()
                        ?.let { JwTimetableParser.parseJson(it) }
                        ?.takeIf { it.slots.isNotEmpty() }
                }
                if (viaJson != null) {
                    saveSnapshot(jsonText)
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
                    dumpDebug("dom", dom)
                    if (dom.isNullOrBlank()) {
                        JwTimetableResult(
                            emptyList(),
                            listOf("教务课表页面没读到内容（可能还在加载，点右上角重试）"),
                            "教务网页", 0,
                        )
                    } else {
                        val parsed = runCatching { Json.parseToJsonElement(dom) }
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
                        //   `saveSnapshot(dom)`，于是**一次失败把上一次那份好课表盖掉了**，
                        //   首页随即退回学在城院那份（同一节次出现两门课）。
                        //   失败不该有写快照的权限。
                        if (parsed.slots.isNotEmpty()) saveSnapshot(dom)
                        parsed
                    }
                }
            }

            if (!guard.isCurrent(token)) {
                isCrawling.value = false
                return
            }
            // ★ 独立审计（v3 #4 第 2 轮）抓到的**致命一条**：这里以前写的是 `result.fold(…)`，
            //   一个把返回值丢在原地的空语句。`settleFirstPage` / `failWith` 都是
            //   `ListState<T>` 上的**纯函数**（ListState.kt:146 / :281，返回新状态、不改自己），
            //   所以那两个调用等于什么都没做 ⇒ 教务这份**在一次会话里永不发布**：
            //   没快照时永远 FirstLoad，有快照时永远停在 stale + refreshing，
            //   新爬到的课只能靠杀进程后 `init{ seedFromSnapshot() }` 从文件里读回来。
            //   工程里其它仓库都是 `_state.value = result.fold(`（PortalRepositories.kt:64）。
            //   这条由 `StatePublishingTest` 钉住，不许再退回去。
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
                    _state.value.failWith(failure.toHzcuException())
                },
            )
            // ★ 第 3 轮审计必修 5：`isCrawling` 必须**在这一行之后**才置假。
            //   原来它在 fold 的两个 lambda 第一句就写 false，而 `_state.value =` 要等 fold
            //   返回才生效 ⇒ 中间存在一帧「没在爬 + 仍是 FirstLoad」，
            //   爬取恰好在他看首页那一刻完成时，那一帧画的是学在城院那份、下一帧才回正
            //   —— 正是业主报的那个闪换了个出现方式。
            isCrawling.value = false
        }
        } finally {
            isCrawling.value = false
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

    /**
     * **只有可調試構建**會落盤：把某條路的原文寫進 App 私有目錄。
     *
     * 判據用 `FLAG_DEBUGGABLE`（安装包是不是 debuggable），**不是** `BuildConfig.DEBUG`
     * —— 這個工程沒開 buildConfig 生成，而且「裝在真機上的這份能不能被我讀」
     * 本來就該由安装包自己說。取回來的辦法（不需要 root）：
     * `adb -s <串號> shell run-as com.muyao.wanzaihyperos cat files/<名字>`。
     *
     * ⚠️ 里面是他**本人的課表原文**：我取回來看完就從設備上刪掉，
     *    不進日誌、不進倉庫、不在匯報里复述內容。
     */
    /** 快照寫在哪（App 私有目錄，release 也寫 —— 這是他自己要看的課表，不是調試殘留）。 */
    private fun snapshotFile(): java.io.File? =
        appContext?.let { java.io.File(it.filesDir, SNAPSHOT) }

    /** 把這條路拿到的原文存一份，下次進這一屏先播它。 */
    private fun saveSnapshot(content: String?) {
        val f = snapshotFile() ?: return
        if (content.isNullOrBlank()) return
        runCatching { f.writeText(content) }
    }

    /**
     * 用快照立刻把界面填上。
     *
     * 走的是**同一個解析器**（不另寫一套"快照格式"）—— 兩套解析遲早分叉，
     * 分叉的表現是「緩存裡的課表和線上看到的不一樣」。
     */
    private fun seedFromSnapshot() {
        val f = snapshotFile() ?: return
        if (!f.isFile) return
        val text = runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val parsed = runCatching {
            if (text.trimStart().startsWith("{")) {
                val root = kotlinx.serialization.json.Json.parseToJsonElement(text)
                // DOM 那一條的原文是一個物件（{rows:[…]}）；JSON 那一條是 kbList 外層。
                if (root.asObjectOrNull()?.containsKey("rows") == true) {
                    JwTimetableParser.parseDom(root).copy(strategy = "教务系统（上次取到的）")
                } else {
                    JwTimetableParser.parseJson(root).copy(strategy = "教务系统（上次取到的）")
                }
            } else null
        }.getOrNull()?.takeIf { it.slots.isNotEmpty() } ?: return
        _state.value = ListState.Content(
            listOf(parsed),
            ListMeta(updatedAtMillis = f.lastModified(), stale = true),
        )
    }

    private fun dumpDebug(which: String, content: String?) {
        val ctx = appContext ?: return
        if ((ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        runCatching {
            java.io.File(ctx.filesDir, "jw_raw_$which.txt").writeText(content ?: "（这条路没拿到内容）")
        }
    }

    private companion object {
        /** 等課表頁面把表格填完的時間（見上面註釋）。 */
        const val RENDER_SETTLE_MS = 2200L

        /** 上次爬到的課表原文（首屏拿它立刻畫出來）。 */
        const val SNAPSHOT = "jw_timetable_snapshot.json"
    }
}
