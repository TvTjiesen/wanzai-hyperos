package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.ui.text.TextStyle
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.largeTitleItem
import com.muyao.wanzaihyperos.ui.design.liquid.rememberPageChromePilot
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.muyao.wanzaihyperos.ui.design.rememberRevealGate
import com.muyao.wanzaihyperos.ui.design.revealIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.R
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.AppOpenPlan
import com.muyao.wanzaihyperos.data.AppOpenStrategy
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.PortalSystem
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.data.apps.COMMON_LIMIT
import com.muyao.wanzaihyperos.data.apps.CommonReason
import com.muyao.wanzaihyperos.data.apps.CommonSignals
import com.muyao.wanzaihyperos.data.apps.commonCaption
import com.muyao.wanzaihyperos.data.apps.pickCommon
import com.muyao.wanzaihyperos.data.portal.PortalParsers
import com.muyao.wanzaihyperos.data.portal.toUiApp
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Mark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.markSemantics
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **应用板块**：把「原生应用」与「网页服务」分开放。
 *
 * ## 为什么分开
 *
 * 两类的体验差一档：原生页即时打开、跟随主题与深浅色；网页服务要等学校站点加载，
 * 网络差时可能只是一片白。混在一起的话，用户点下去才知道「这个又要等网页」。
 * 分开之后，期待是明确的。
 *
 * ## 怎么判定「原生」
 *
 * 只用 [AppOpenStrategy.decide] 的结果 —— 那是**唯一**的路由真相来源。
 * 不在这里另写一份名单：否则新增一个原生页却忘了更新名单，
 * 它会被错归到「网页服务」。
 */
@Composable
fun AppsSectionPage(
    graph: AppGraph,
    onOpenApp: (PortalApp) -> Unit,
    onOpenSearch: () -> Unit,
    /** 全局扫一扫：搜索行右边那颗方形钮。扫到网址开网页，扫到签到码带去签到页。 */
    onOpenScan: () -> Unit = {},
    // 「整理」全页只有一颗，在「常用」区块行的行尾（见 [AppsTidyButton]）。
    //
    // 2026-11-08 改判：原来这里写着「板上它是顶栏那颗（`.topbar .meta .tidybtn`），由壳画」
    // —— 那是把板面读错了。v2.1 的 `screens/apps.png` 里顶栏只有「应用 + 搜索钮」，
    // 「整理」在「常用」标题行的右侧。照旧注释做，真机上就出现了两颗「整理」。
    // 现在壳里那颗已经删掉，改由本页的区块行动作承担。
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val catalogState by graph.catalog.state.collectAsState()

    // 「收起网页服务」是落盘的偏好。必须在这里（组合作用域）读：
    // `LazyColumn` 的构建 lambda 不是 @Composable，在那里读快照状态不会触发重组，
    // 点了「收起」界面不会动。
    val webCollapsed = UiPrefs.appsWebCollapsed

    // ── v3 #7：「常用」那一排的输入 ──────────────────────────────────
    // ★ 这句以前写的是「不为了算常用而多发一次请求」—— **与下面的代码相反**：
    //   这一页正是为了第 3 档，自己发了 `graph.frequent.refresh()`（第 1 轮 B-3 就要求改准）。
    //   订阅的是已经落到内存的那两份状态 + 第 3 档那份学校名单：
    // 首页本来就会拉签到与待办，切到这一页时它们要么有货、要么确实没有。
    val rollcallState by graph.rollcall.state.collectAsState()
    val todoState by graph.todoBoard.state.collectAsState()
    // 学校门户自己那份「常用应用」（实测 14 条，走 `listappinfoex`）。
    // ★ 独立审计（v3 #7 第 1 轮）抓到：以前这一档是从**目录分组**里找名叫「常用应用」的那组，
    //   而目录的组名是「热门推荐」「校园生活」—— 那个名字只是 `listappinfoex` 的来源标签
    //   （`PortalParsers.FREQUENT_GROUP_NAME`），所以 `schoolFrequent` **恒为空**，
    //   注释里"学校统计出来的"成了假宣传。真数据源是 `graph.frequent`，现在接的是它。
    val frequentState by graph.frequent.state.collectAsState()
    val pinnedIds = UiPrefs.commonPinned
    val openCounts = UiPrefs.appOpenCounts
    // 整理模式：点格子 = 钉/撤，不是打开。
    val tidy = UiPrefs.appsTidy.value

    LaunchedEffect(Unit) {
        // 应用级作用域，见 `AppGraph.fetch` / `ioScope` 的根因说明。
        graph.fetch { graph.catalog.refresh() }
        // v3 #7：默认算法第 3 档的数据源。以前只有已删的旧壳首页（`ui/home/HomePage.kt`，第 9 轮审计删除）会拉它，
        // 所以这一页永远读不到那份「学校给的常用」—— 现在这一页自己拉。
        graph.fetch { graph.frequent.refresh() }
    }

    // 大标题交给顶栏（T53 的试点抽成共用实现）：标题是列表第 0 项，
    // 跟着内容一起滚，滚出去之后由顶栏接手小标题。
    // 原本这一页自己画了一个「消息/应用/设置」的大字 —— 那就是双标题的来源：
    // 顶栏已经会显示板块名，页内再画一个，两处的字体/位置迟早不一致。
    val pilot = rememberPageChromePilot()
    // 交错入场只在页面打开后头 700ms 内生效（见 revealIn 的 KDoc）。
    val revealGate = rememberRevealGate()

    // 小球跟着内容走（业主 2026-10-25「不要单独一个图层」）：挂在大标题左边，随列表滚。
    // 这一页唯一说得出口的状态就是应用清单本身：首次加载 / 失败 / 到手，
    // 三种都不许互相冒充（data/ListState.kt 的红线）。
    val inFlight by graph.inFlight.collectAsState()
    val ballState = AiBallStates.ofList(catalogState, fetching = inFlight > 0)

    // ── 英雄区副行（设计稿 v2.1：「79 个来源入口 · 原生与网页分开」）──────────
    // 数字全部来自真实清单，一条都不编。清单还没到就是空串，
    // [LargeTitle] 会走 `subtitle.isEmpty()` 分支把副行整行省掉。
    val heroApps = (catalogState as? ListState.Content)
        ?.items.orEmpty().flatMap { it.apps }.distinctBy { it.id }.map { it.toUiApp() }
    val heroNative = heroApps.count { isNativeApp(it) }
    val heroSubtitle = if (heroApps.isEmpty()) {
        ""
    } else {
        "${heroApps.size} 个来源入口 · 原生 $heroNative / 网页 ${heroApps.size - heroNative}"
    }

    LazyColumn(
        state = pilot.listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            // 大标题自己带状态栏 inset，这里 top 必须归零 ——
            // 留着原本那 56dp 会出现「标题下面凭空一大块空白」。
            top = 0.dp,
            bottom = pageBottomInsetDp(LocalNavBarVisible.current),   // 留出玻璃底栏的高度（让内容能滚到它下面，玻璃才折射得到东西）
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 2026-11-07 按设计稿 v2.1 复刻首屏英雄区：「校园服务 / 校园集合 / N 个来源入口 · …」。
        // 顶栏小标题仍是「应用」，首屏大字换成「校园集合」—— 设计稿里这两句本来就不是同一句话。
        largeTitleItem(
            pilot = pilot,
            leading = { AiBall(state = ballState, diameter = AiBall.DIAMETER_PAGE, hitSlop = AiBall.INLINE_SLOP) },
            kicker = "校园服务",
            heroTitle = "校园集合",
            subtitle = heroSubtitle,
            divider = true,
        )

        // 搜索只做**入口**：真正的输入框与查询状态都在搜索页。
        // 这里再放一个输入框会出现两份查询状态，点进来还要同步一次。
        // 扫一扫与搜索并排：两者是同一件事的两面 ——
        // 搜索是「我知道我要什么」，扫一扫是「我不知道，让 App 认」。
        item(key = "search") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchEntry(onClick = onOpenSearch, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(8.dp))
                ScanEntry(onClick = onOpenScan)
            }
        }

        when (val s = catalogState) {
            is ListState.FirstLoad -> item(key = "loading") {
                Text(
                    "正在加载应用清单…",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(Dims.cardPadding),
                )
            }

            is ListState.Failed -> item(key = "failed") {
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text("应用清单加载失败", fontSize = Type.body, color = colors.error)
                        Spacer(Modifier.height(Dims.tight))
                        Text(
                            s.error.userText,
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            is ListState.Content -> {
                // 门户给的是 `PortalAppEntry`（数据层模型），而界面与路由用的是
                // `PortalApp`（展示模型）。用既有的 `toUiApp()` 转，**不自己造一份** ——
                // 两份映射迟早会漂移（一边加了 subtitle、另一边没加）。
                //
                // ★ 必须跨分组按 id 去重：门户全量目录是「7 个分组 87 条记录、
                //   独立应用只有约 60 个」（docs/FEATURE_MAP.md），同一条应用被放进
                //   多个分组。解析层给 `allApps` 去过重，但这一页读的是 groups；
                //   不去重就会把「我的成长」「我的课程」各画两遍（真机 2026-10-21 见到）。
                //   保留首次出现的顺序 —— 上层分组顺序仍由门户决定。
                val apps = s.items
                    .flatMap { g -> g.apps }
                    .distinctBy { it.id }
                    .map { it.toUiApp() }

                // ★ 用 AppOpenStrategy 的判定结果分类，不另写名单。
                val (native, web) = apps.partition(::isNativeApp)

                if (native.isEmpty() && web.isEmpty()) {
                    // 空分组不画空标题：两个分组都没内容时只留一句提示。
                    item(key = "empty-content") { SmallHint("还没有应用清单。") }
                } else {
                    // v3 #7：常用**恒 ≤4 格**，默认算法自动匹配 + 可手动钉/撤。
                    // 每一档用的是哪份真数据，写在 `data/apps/CommonApps.kt` 的档头注释里。
                    // 第 3 档 = 学校门户那份「常用应用」，走 `graph.frequent`（`listappinfoex`，
                    // 实测 14 条），**按学校给的顺序**。
                    // ★ 以前这里是从目录分组里找名叫「常用应用」的那组 —— 组名其实是
                    //   「热门推荐」「校园生活」，那个名字只是来源标签 ⇒ 这一档一直空转。
                    val schoolFrequent = (frequentState as? ListState.Content)
                        ?.items?.map { it.id }
                        .orEmpty()
                    // 「现在有事」= 真有一条没截止的签到，或真有待办（此刻屏上的量，不是说本周）。
                    // 它落在哪一格仍然问 `AppOpenStrategy`（原生学在城院那一格装着签到与作业），
                    // 不在这里另写一份名单 —— 那是本工程的硬规矩（见档头「怎么判定原生」）。
                    val liveIds = buildSet {
                        val hasRollcall = (rollcallState as? ListState.Content)
                            ?.items?.any { !it.expired } == true
                        // ★ `items` 是**单元素看板包装**（`TodoRepository` 把整个 `TodoBoard`
                        //   塞成 listOf(board)），所以 `items.isNotEmpty()` 只要看板落过一次库就**恒真**
                        //   —— 待办 0 条时那一格也挂着「现在有事」。必须下到看板里看**分组真有没有条**。
                        //   （独立审计 v3 #4/#7 第 2 轮：B-2 同一处病根，注释与代码第二次对不上。）
                        val hasTodo = (todoState as? ListState.Content)
                            ?.items?.firstOrNull()
                            ?.groups?.any { !it.failed && it.items.isNotEmpty() } == true
                        if (hasRollcall || hasTodo) {
                            apps.forEach {
                                if (AppOpenStrategy.decide(it) is AppOpenPlan.NativeTronclass) {
                                    add(it.id)
                                }
                            }
                        }
                    }
                    val common = pickCommon(
                        native + web,
                        CommonSignals(pinnedIds, liveIds, schoolFrequent, openCounts),
                    )
                    val commonIds = common.mapTo(HashSet()) { it.app.id }
                    // 屏上真的画出来的钉住格数（不是 prefs 里存了几条）。
                    val pinnedOnScreen = common.count { it.reason == CommonReason.PINNED }
                    if (common.isNotEmpty()) {
                        item(key = "common-header") {
                            SectionLabel(
                                title = "常用",
                                subtitle = "",
                                icon = MarkIcon.Pin,
                                // ★ 原来这里写死「最近使用」，可这一页从来没记过一次打开 ——
                                //   那句是在替业主撒谎（红线：不编）。现在这句由
                                //   **实际命中的档位**拼出来，用了哪几档就写哪几档。
                                // 板面「常用」标题行右侧只有一颗「整理」，没有计数也没有档位说明。
                                // 那些说明只在「整理中」以一行小字出现（见下面的 common-tidy）。
                                count = 0,
                                action = { AppsTidyButton(tidy = tidy, onClick = { UiPrefs.appsTidy.value = !tidy }) },
                            )
                        }
                        // 整理模式才出现的两行：一句说明 + 一颗「恢复默认」。
                        // 板上没有这两样 —— 但钉住状态需要一个撤销口，撤掉它就是留一颗假按钮的反面，
                        // 所以只在「整理中」露出，平时不占版面（对齐板面）。
                        if (tidy) {
                            item(key = "common-tidy") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (pinnedOnScreen >= COMMON_LIMIT)
                                            "已钉满 ${COMMON_LIMIT} 个，再点别的会先撤掉最早钉的那个"
                                        else "点格子 = 钉住 / 撤下",
                                        fontSize = Type.caption,
                                        color = colors.onSurfaceVariantSummary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (pinnedIds.isNotEmpty() || openCounts.isNotEmpty()) {
                                        Text(
                                            text = "恢复默认",
                                            fontSize = Type.caption,
                                            color = colors.error,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(onClickLabel = "恢复默认的常用") {
                                                    UiPrefs.clearCommonOverrides()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                        common.chunked(COMMON_LIMIT).forEachIndexed { rowIndex, rowApps ->
                            item(
                                key = "c-$rowIndex-" +
                                    rowApps.joinToString("-") { it.app.id.toString() },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    rowApps.forEachIndexed { col, pick ->
                                        val app = pick.app
                                        val pinned = app.id in pinnedIds
                                        AppGridCell(
                                            app = app,
                                            modifier = Modifier
                                                .weight(1f)
                                                .revealIn(revealGate, rowIndex),
                                            web = !isNativeApp(app),
                                            featured = true,
                                            // 整理模式下把"已钉住"画成板上那种薄荷主格；
                                            // 平时仍是板上定的**只有第一格**是薄荷底（`.app .box.hi`）。
                                            highlight = if (tidy) pinned else rowIndex == 0 && col == 0,
                                            onClick = {
                                                if (tidy) {
                                                    UiPrefs.toggleCommonPinned(app.id)
                                                } else {
                                                    UiPrefs.recordAppOpen(app.id)
                                                    onOpenApp(app)
                                                }
                                            },
                                        )
                                    }
                                    repeat(COMMON_LIMIT - rowApps.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    if (tidy) {
                        item(key = "tidy-hint") {
                            Text(
                                text = "整理中：下面这些点一下也是钉住 / 撤下，不会打开应用。",
                                fontSize = Type.caption,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                            )
                        }
                    }

                    // 下面两个分组要**排掉已经进了常用的那些**：原来是 `native.drop(4)`，
                    // 可现在常用是按算法挑的（可能挑到网页条目），按位置切会把同一格画两遍。
                    val restNative = native.filter { it.id !in commonIds }
                    if (restNative.isNotEmpty()) {
                        item(key = "native-header") {
                            // 板 `.sec-head .count{12 · 打开即用}` —— 数字直接打头，没有量词「个」。
                            SectionLabel(title = "原生应用", subtitle = "", count = restNative.size,
                                icon = MarkIcon.App,
                                note = native.size.toString() + " 项")
                        }
                        restNative.chunked(4).forEachIndexed { rowIndex, rowApps ->
                            item(
                                key = "n-$rowIndex-" +
                                    rowApps.joinToString("-") { it.id.toString() },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    rowApps.forEach { app ->
                                        AppGridCell(
                                            app = app,
                                            modifier = Modifier
                                                .weight(1f)
                                                .revealIn(revealGate, rowIndex),
                                            onClick = {
                                                // ★ 独立审计（v3 #7 第 2 轮）抓到：整理模式下
                                                //   这些非常用格子**照旧 recordAppOpen + 打开**，
                                                //   于是"点空白也能把某个应用调起来"，还把它记进了
                                                //   默认算法第 4 档。整理模式里一律只钉/撤，绝不开应用。
                                                if (tidy) {
                                                    UiPrefs.toggleCommonPinned(app.id)
                                                } else {
                                                    UiPrefs.recordAppOpen(app.id)
                                                    onOpenApp(app)
                                                }
                                            },
                                        )
                                    }
                                    repeat(4 - rowApps.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    // 网页服务同样排掉已经进了常用的那些（与上面 restNative 一条规矩）。
                    val restWeb = web.filter { it.id !in commonIds }
                    if (restWeb.isNotEmpty()) {
                        item(key = "web-header") {
                            // 板上 `.sec-head` 只有一行标题 + 右侧一个 count，**没有第二行说明**。
                            // 原来多写的一句「在学校网页里打开 · 加载受网络影响」是解释性文字，
                            // 板面没有它，而且它把右侧的 count 挤到了第二行高度上。
                            SectionLabel(
                                title = "网页服务",
                                subtitle = "",
                                count = restWeb.size,
                                icon = MarkIcon.Web,
                                note = "",
                                action = {
                                    CollapseToggle(collapsed = webCollapsed) {
                                        // 落盘：下次进页面还是用户上次的选择。
                                        UiPrefs.saveAppsWebCollapsed(!webCollapsed)
                                    }
                                },
                            )
                        }
                        // 整张网页卡是一个 item：分隔线、圆角、描边只画一次；
                        // 卡内是连续的单行列表，跨行 id 重复也不会撞 key。
                        item(key = "web-card") {
                            WebCard(
                                apps = restWeb,
                                collapsed = webCollapsed,
                                onOpenApp = {
                                    // 同上：整理模式里这一行也只是钉/撤，不打开、不计数。
                                    if (tidy) {
                                        UiPrefs.toggleCommonPinned(it.id)
                                    } else {
                                        UiPrefs.recordAppOpen(it.id)
                                        onOpenApp(it)
                                    }
                                },
                                onExpand = { UiPrefs.saveAppsWebCollapsed(false) },
                                onCollapse = { UiPrefs.saveAppsWebCollapsed(true) },
                            )
                        }
                    }
                }
            }

            is ListState.Empty -> item(key = "empty") {
                SmallHint("学校门户暂时没有返回应用。")
            }

            is ListState.SessionExpired -> item(key = "expired") {
                SmallHint("登录已失效，请重新登录后再试。")
            }
        }

        // A-5：板 `.bottom-action` 的「校园地图与更多入口」。
        // 原型的校园地图页自己写着「地图底图与定位接入待确认」，我们没有底图来源，
        // 也不画一张假地图当导航；这颗按钮打开的是**门户首页**（学校自己的入口目录）。
        item(key = "portal-more") {
            val colors = MiuixTheme.colorScheme
            val portalHome = remember {
                PortalApp(
                    id = 0,
                    name = "校园门户",
                    url = HzcuEndpoints.PORTAL_BASE_URL,
                    system = PortalSystem.STANDALONE,
                )
            }
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(1.dp, colors.onSurfaceVariantSummary.copy(alpha = 0.22f), shape)
                    .clickable(onClickLabel = "打开校园门户入口目录") { onOpenApp(portalHome) }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "校园地图与更多入口",
                    fontSize = Type.bodyEmphasis,
                    fontWeight = Type.weightBody,
                    color = colors.primary,
                )
            }
        }

        // A-6：板上 `p.caption.muted` 的后半句。前半句「这里是依据文档构建的静态评审目录」
        // 只在评审期成立（产品里目录来自门户动态返回），按清单去掉。原文 79 两侧无空格。
        item(key = "catalog-footnote") {
            Text(
                text = "实际产品继续显示门户动态返回的全部项目，不以79为硬上限。",
                fontSize = Type.caption,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

/** 「原生」的判据只有一条：策略说要走原生页。 */
private fun isNativeApp(app: PortalApp): Boolean = when (AppOpenStrategy.decide(app)) {
    is AppOpenPlan.NativeTronclass,
    is AppOpenPlan.NativeCourses,
    is AppOpenPlan.NativeFeature,
    // 智慧公寓走的是我们自己的闸门页（Route.Zhgy），不是 WebView ——
    // 路由把它当原生，这里少一类就会被排进「网页服务」，点开却跳原生页（审查 2026-10-21）。
    is AppOpenPlan.ZhgyGate,
    is AppOpenPlan.JwNative,
    is AppOpenPlan.ZyNative -> true

    else -> false
}

/**
 * 顶栏右上角那颗「整理」。
 *
 * 板 `.topbar .meta .tidybtn{font-size:10.5px;font-weight:650;color:var(--accent);
 *      background:var(--accent-soft);border-radius:999px;padding:7px 11px}`
 * × 0.886 ⇒ **9.3sp（取 summary=10）、6.2×9.7dp、accent 字 + accent-soft 底**。
 *
 * 它是**顶栏的控件**，所以由壳（`WanzaiMainShell`）画，不塞在「常用」那一行里 ——
 * 上一版放在行尾，比板低了两层，而且和右侧的 count 挤在一起。
 * 高频入口 ⇒ **不加动画**（`wanzai-motion` 第 1 步频率闸门）。
 */
@Composable
fun AppsTidyButton(tidy: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
val shape = RoundedCornerShape(50)
    // 它现在是全页唯一的整理开关（页内那颗撤了），所以字必须跟着状态走 ——
    // 否则整理中它仍写「整理」，按下去却退出整理，标签在替自己撒谎。
    Text(
        text = if (tidy) "完成" else "整理",
        fontSize = Type.summary,
        fontWeight = Type.weightBody,
        color = colors.primary,
        modifier = Modifier
            .clip(shape)
            .clickable(onClickLabel = if (tidy) "完成整理" else "整理应用") { onClick() }
            .padding(horizontal = 9.7.dp, vertical = 6.2.dp),
    )
}

/** 顶部搜索入口：一条可点的近实底行，不是输入框。 */
@Composable
private fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    // 板 `.searchline{margin:2px 18px 5px;height:34px;border-radius:12px;
    //      background:rgba(26,25,23,.045);gap:8px;padding:0 11px;color:var(--faint);font-size:10.5px}`
    // × 0.886 ⇒ **30dp 高、10.6dp 圆角、9.3sp 淡字、图标 12.4dp**。
    // 它是一条「按上去搜索的灰行」，不是输入框，也不是一张有投影的白卡 ——
    // 原来 44dp 全圆 + surfaceContainerHigh 把它抬成了主角，内容就被它压低了。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(10.6.dp))
            .background(colors.onBackground.copy(alpha = 0.045f))
            .clickable(onClickLabel = "搜索应用与网页服务") { onClick() }
            .padding(horizontal = 9.7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_board_search),
                contentDescription = null,
                tint = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
                modifier = Modifier.size(12.4.dp),
            )
            Spacer(Modifier.size(7.dp))
            Text(
                text = "搜索应用与网页服务",
                fontSize = Type.summary,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
    }
}

/**
 * 扫一扫入口：与搜索行同高的一颗方钮（30dp）。
 *
 * 为什么不做成第二条灰行：两条一模一样的灰行长在一起，
 * 用户分不出哪条是按、哪条是搜；方钮在右端，形状本身就说明「按一下」。
 */
@Composable
private fun ScanEntry(onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.6.dp))
            .background(colors.onBackground.copy(alpha = 0.045f))
            .clickable(onClickLabel = "扫一扫") { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_board_scan),
            // 必须给名字：这颗钮里只有一张图标，没有文字也没有子节点，
            // contentDescription 留空的话 uiautomator 里它是 NAF="true"（无名称），
            // 读屏用户听到的只是一声「按钮」（真机实测过）。
            contentDescription = "扫一扫",
            tint = colors.onSurfaceVariantSummary.copy(alpha = 0.85f),
            modifier = Modifier.size(14.4.dp),
        )
    }
}

/** 分组标题：主标题 + 一行说明，数量在右；可选的文字动作也放右侧。 */
@Composable
private fun SectionLabel(
    title: String,
    subtitle: String,
    count: Int,
    modifier: Modifier = Modifier,
    /**
     * 标题前的正文图标（2026-11-07 起）。跟 [BodyMark] 是同一套 25 枚，
     * 不是另造一批：应用页这三堆（常用 / 原生 / 网页）用 [MarkIcon.Pin]、
     * [MarkIcon.App]、[MarkIcon.Web] 分开，一眼能看出「这是钉住的」还是「这要联网」。
     */
    icon: MarkIcon? = null,
    /** 右侧描述文案（板子不是裸数字）：例如「6 个 · 常用入口」。 */
    note: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, start = 0.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    BodyMark(
                        icon = icon,
                        tint = colors.onBackground,
                        modifier = Modifier
                            .markSemantics(icon)
                            .padding(end = 6.dp),
                        size = Mark.rowSize,
                    )
                }
                Text(
                    text = title,
                    // 板 `.sec-head h3{font-size:15px;font-weight:700;letter-spacing:-.01em}` × 0.886 ⇒ 13sp
                    fontSize = Type.body,
                    fontWeight = Type.weightTitle,
                    letterSpacing = (-0.01).em,
                    color = colors.onBackground,
                )
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(Dims.tight))
                Text(
                    text = subtitle,
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(Dims.tight))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note ?: if (count > 0) "$count" else "",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
            if (action != null) {
                Spacer(Modifier.size(Dims.loose))
                action()
            }
        }
    }
}

/** 「收起 / 展开」文字按钮。高频操作，不加动画。 */
@Composable
private fun CollapseToggle(collapsed: Boolean, onToggle: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Text(
        // A-2：板 `.text-button` 的原文是「展开全部 ›」/「收起 ›」。
        text = if (collapsed) "展开全部 ›" else "收起 ›",
        fontSize = Type.caption,
        color = colors.primary,
        modifier = Modifier
            .clickable(
                onClickLabel = if (collapsed) "展开网页服务" else "收起网页服务",
            ) { onToggle() }
            // padding 在 clickable 之内：把小字按钮的命中区撑到接近 44dp。
            .padding(horizontal = Dims.normal, vertical = Dims.cardPadding),
    )
}

/**
 * 名称 → 正文前图标（[MarkIcon]）。
 *
 * ## 为什么换掉原来那套 `ic_board_*`
 *
 * 门户下发的 `app.icon` 是同一颗通用图标（每个入口长一个样），复评的判据是
 * 「逐格都是同一个四方块 + 一个实心形」—— 那不是每格不一样，是每格一样。
 * 老做法是按关键词挑 24 枚 `ic_board_*` 矢量，**但那是 24 枚各自的粗细/圆角/画布
 * 都不完全一致的手绘素材**，混在一屏里看得出参差；而且它与新做的正文前图标
 * （[BodyMark]/[MarkIcon]）是两套笔性，同一页上并存就显得没做完。
 *
 * 现在统一到 [MarkIcon] 这一套：同一条 0.095 中线宽、同一 0.08 内缩、同一圆角比例，
 * 所以「常用」四格、「原生应用」网格、「网页服务」列表用的是**同一支笔**。
 *
 * ## 挑选规则（先匹配先赢，顺序有讲究）
 *
 * 挑不到就用 [MarkIcon.App]（「这是个入口」）——**不猜分类、不编一个像样的图标**。
 * 顺序陷阱：「一卡通」必须先于「卡」类被收走；「我的课程」要先于「我的」被收走，
 * 否则整列全是同一个人形（17:04 截图里「我的成长 / 我的班级」撞成同一颗就是这么来的）。
 */
private fun markIconFor(name: String): MarkIcon = when {
    // ★ 2026-11-07 真机截图暴露的问题：`学在城院` / `智云课堂` 两条**最常用**的入口
    //   名字里一个关键词都不沾，双双落到兜底的 `App`（同一枚 88 方格），
    //   排在一起像复制粘贴。它们各自有明确的语义，前置点名。
    name.contains("学在城院") -> MarkIcon.Schedule
    name.contains("智云课堂") -> MarkIcon.Course
    name.contains("一码通") -> MarkIcon.Scan
    // 先处理带「我的」这类高歧义前缀的复合词，否则会被末尾的兜底规则抢走
    name.contains("成长") || name.contains("画像") || name.contains("绩点") -> MarkIcon.Growth
    name.contains("班级") || name.contains("信息卡") || name.contains("学籍") -> MarkIcon.Account
    name.contains("课程") || name.contains("课表") || name.contains("查课") -> MarkIcon.Schedule
    name.contains("讲座") || name.contains("活动") || name.contains("劳动") -> MarkIcon.Ticket
    name.contains("考试") || name.contains("问卷") || name.contains("申报") ||
        name.contains("测评") || name.contains("申报") -> MarkIcon.File
    name.contains("签到") || name.contains("打卡") || name.contains("待办") -> MarkIcon.Todo
    name.contains("成绩") || name.contains("排名") || name.contains("统计") ||
        name.contains("教务") -> MarkIcon.Data
    name.contains("一卡通") || name.contains("消费") || name.contains("充值") ||
        name.contains("卡") -> MarkIcon.Card
    name.contains("图书") || name.contains("馆藏") || name.contains("借阅") ||
        name.contains("资料") -> MarkIcon.Library
    name.contains("消息") || name.contains("通知") || name.contains("收件") ||
        name.contains("邮件") || name.contains("问卷") -> MarkIcon.Message
    name.contains("座位") || name.contains("自习") || name.contains("空间") ||
        name.contains("公寓") || name.contains("宿舍") || name.contains("场馆") -> MarkIcon.Pin
    name.contains("预约") || name.contains("票") -> MarkIcon.Ticket
    name.contains("网络") || name.contains("wifi") || name.contains("WiFi") ||
        name.contains("校园网") -> MarkIcon.Wifi
    name.contains("车") || name.contains("交通") || name.contains("班车") -> MarkIcon.Bus
    name.contains("体育") || name.contains("跑") || name.contains("运动") -> MarkIcon.Run
    name.contains("报修") || name.contains("维修") || name.contains("工具") -> MarkIcon.Wrench
    name.contains("电话") || name.contains("联系") -> MarkIcon.Phone
    name.contains("证明") || name.contains("文件") || name.contains("回执") ||
        name.contains("申请") -> MarkIcon.File
    name.contains("扫码") || name.contains("扫描") || name.contains("一码") -> MarkIcon.Scan
    name.contains("时间") || name.contains("日程") || name.contains("校历") -> MarkIcon.Clock
    name.contains("就业") || name.contains("规划") || name.contains("发展") -> MarkIcon.Growth
    name.contains("心理") || name.contains("安全") || name.contains("预警") -> MarkIcon.Warn
    // 网页来源天然带「网页」语义；原生入口一律落到 App
    else -> MarkIcon.App
}

private fun markIconForWeb(name: String): MarkIcon {
    val own = markIconFor(name)
    return if (own == MarkIcon.App) MarkIcon.Web else own
}

/**
 * 原生应用的一格：板子是 4 列「圆底图标 + 一行名字」，不是 2 列大卡。
 *
 * 两种尺寸，板上各一套：
 *  · 常用 `.freq .box{width:52px;height:52px;border-radius:17px}` + `.icon{23px}` ⇒ **46dp/15dp + 20dp**
 *  · 原生 `.app  .box{width:46px;height:46px;border-radius:15px}` + `.icon{21px}` ⇒ **41dp/13dp + 19dp**
 * 底色 `rgba(26,25,23,.045)` + 一道 `rgba(255,255,255,.28)` 的**内高光边**（深色版 .055/.07），
 * 名字 9px⇒8sp `--sub`。原来这些数全偏大一档，而且没有那道边，所以看着像「灰块贴着字」。
 */
@Composable
private fun AppGridCell(
    app: PortalApp,
    modifier: Modifier = Modifier,
    web: Boolean = false,
    featured: Boolean = false,
    /** 板 `.app .box.hi`：主应用那一格换成**薄荷底 + accent 字形**，给这一屏一个重点。 */
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val boxSize = if (featured) 46.dp else 41.dp
    val boxRadius = if (featured) 15.dp else 13.dp
    val glyphSize = if (featured) 20.dp else 19.dp
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "打开${app.name}") { onClick() }
            // 板 `.appgrid{gap:12px 8px}` ⇒ 行距 10.6dp。原来上下各 5dp 再加图标座外面
            // 那圈余量，两行之间空出近 30dp，整页被拉得很散。
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(boxSize + 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 板 `.app .box{border:1px solid rgba(255,255,255,.28)}`：那道亮边是「凸起」的来源，
            // 少了它，米色方块就像一块脏印子。
            Box(
                modifier = Modifier
                    .size(boxSize)
                    .clip(RoundedCornerShape(boxRadius))
                    .background(
                        if (highlight) colors.primary.copy(alpha = 0.11f)
                        else colors.onBackground.copy(alpha = 0.045f),
                    )
                    .border(
                        1.dp,
                        if (highlight) Color.Transparent
                        else if (isDark) Color(0xFFFFFFFF).copy(alpha = 0.07f)
                        else Color(0xFFFFFFFF).copy(alpha = 0.28f),
                        RoundedCornerShape(boxRadius),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 板子的原生区是**单色线图标 + 米色方块**，不是彩色圆片；
                // 只有 `.hi` 那一格换成 accent 字形（和它的薄荷底成一对）。
                // 2026-11-07：换到统一的正文前图标笔性（MarkIcon），详见 markIconFor 的 KDoc。
                BodyMark(
                    icon = if (web) markIconForWeb(app.name) else markIconFor(app.name),
                    tint = if (highlight) colors.primary else colors.onBackground,
                    modifier = Modifier.markSemantics(
                        if (web) markIconForWeb(app.name) else markIconFor(app.name),
                    ),
                    // 网格里那枚比正文前略大：它要撑住 41~46dp 的托盘。
                    size = glyphSize,
                )
            }
            if (web) {
                // 板 `.app .net{position:absolute;top:-4px;right:-7px;font-size:6px;font-weight:700;
                //      color:var(--faint);background:var(--phone);border:1px solid var(--line-strong);
                //      border-radius:999px;padding:1.5px 4px}`
                // —— 它是**浮在格子右上角的一枚小牌**（有纸色底、有描边），
                // 不是名字下面多出来的一行字。原来写在下面，等于把一格挤成两行。
                Text(
                    text = "网页",
                    fontSize = Type.micro,
                    fontWeight = Type.weightTitle,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    lineHeight = 12.sp,   // 第 9 轮审计：#11 的地板是 10sp，9sp 的行高连字都放不下
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 1.dp, y = (-3).dp)
                        .clip(RoundedCornerShape(50))
                        // ★ 板 CSS 里 `.app .net`（暖色那支）**定义了但 HTML 没调用**，
                        //   屏上真正画出来的是 `.webmark{color:var(--faint);background:var(--phone);
                        //   border:1px solid var(--line-strong);padding:1.5px 4px}`
                        //   ⇒ 灰字 + 纸底 + 一道描边。上一轮我照了那条死规则改色，方向反了。
                        .background(colors.background)
                        .border(
                            1.dp,
                            if (isDark) Color(0xFFFFFFFF).copy(alpha = 0.18f)
                            else Color(0xFF1A1917).copy(alpha = 0.16f),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 3.5.dp, vertical = 0.6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.name,
            // 板 `.app span{font-size:9px;color:var(--sub)}` × 0.886 ⇒ 8sp
            fontSize = Type.micro,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

/** 网页服务卡：默认逐行列出；收起时只留第一行与「另有 N 个」整行按钮。 */
@Composable
private fun WebCard(
    apps: List<PortalApp>,
    collapsed: Boolean,
    onOpenApp: (PortalApp) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        if (collapsed && apps.size > 1) {
            val first = apps.first()
            WebRow(app = first, onClick = { onOpenApp(first) })
            WebDivider()
            ExpandRow(onExpand = onExpand)
        } else {
            apps.forEachIndexed { index, app ->
                if (index > 0) WebDivider()
                WebRow(app = app, onClick = { onOpenApp(app) })
            }
            // 板子底部那一行「全部 N 个」在这里是真的：展开态给一条收起，不骗人。
            if (apps.size > 1) {
                WebDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "收起网页服务") { onCollapse() }
                        .padding(horizontal = Dims.cardPadding, vertical = Dims.screenV),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "共 ${apps.size} 个网页服务 · 收起",
                        style = TextStyle(fontFeatureSettings = Type.tabular),
                        fontSize = Type.summary,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

/** 网页服务的一行：紧凑单行，点整行打开。 */
@Composable
private fun WebRow(app: PortalApp, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    // 板 `.webrow{padding:8px 12px;gap:10px}` + `.box{29px;radius 9px}` + `.icon{15px}`
    //   + `.wname{11.5px/590}` ⇒ × 0.886 = **7×10.6dp / 26dp / 13dp / 10.2sp**。
    // 板上这行右侧还有一枚 `.wtag`（「需登录」「仅校园网」）—— 那种限制**门户不告诉我们**，
    // 拿不到就不画（工程规矩：不编造学校系统给不出的数据）。这里只留一个真箭头。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开${app.name}") { onClick() }
            .padding(horizontal = 10.6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.onBackground.copy(alpha = 0.045f)),
            contentAlignment = Alignment.Center,
        ) {
            // 板 `.webrow .box{color:var(--sub)}` —— 网页那批的字形比原生淡一档。
            BodyMark(
                icon = markIconForWeb(app.name),
                tint = colors.onSurfaceVariantSummary,
                modifier = Modifier.markSemantics(markIconForWeb(app.name)),
                size = 13.dp,
            )
        }
        Spacer(Modifier.size(9.dp))
        // A-4：板 `.web-item` 的 `span.task-main` 是**两行** —— 名称 + 来源徽标
        // （`S1 · 原站服务 · ID 106`）。徽标由 PortalSystem 推出，不编造。
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                fontSize = Type.summary,
                fontWeight = Type.weightBody,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = portalSourceBadge(app),
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(6.dp))
        // 板 `.webrow .wtag{font-size:7.5px;font-weight:650;color:var(--faint);
        // border:1px solid var(--line-strong);border-radius:999px;padding:2px 5px}` ⇒ 6.6sp。
        // ★ 板上那两枚字是「需登录 / 仅校园网」—— 那是**门户没告诉我们的限制**，
        //   我们不知道就不能编（工程红线）。这里只写一个**事实**：它是网页服务。
        Text(
            text = "网页",
            fontSize = Type.micro,
            fontWeight = Type.weightBody,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(
                    1.dp,
                    if (colors.background.luminance() < 0.5f)
                        Color(0xFFFFFFFF).copy(alpha = 0.18f)
                    else Color(0xFF1A1917).copy(alpha = 0.16f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 4.4.dp, vertical = 1.8.dp),
        )
        Spacer(Modifier.size(6.dp))
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_board_chev),
            contentDescription = null,
            tint = colors.onSurfaceVariantSummary.copy(alpha = 0.7f),
            modifier = Modifier.size(9.dp),
        )
    }
}

/** 0.5dp 发丝分隔线。Miuix 0.9.4 没有 HorizontalDivider，按本工程既有写法用 Box 画。 */
@Composable
private fun WebDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dims.cardPadding)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)),
    )
}

/** 收起状态下的整行按钮：「查看其余网页服务（完整目录）」。 */
@Composable
private fun ExpandRow(onExpand: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "查看其余网页服务（完整目录）") { onExpand() }
            .padding(horizontal = Dims.cardPadding, vertical = Dims.screenV),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "查看其余网页服务（完整目录）",
            style = TextStyle(fontFeatureSettings = Type.tabular),
            fontSize = Type.summary,
            color = colors.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SmallHint(text: String) {
    Text(
        text = text,
        fontSize = Type.secondary,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(Dims.cardPadding),
    )
}

/**
 * 一行的来源徽标：`S1 · 原站服务 · ID 106`（板 `.web-item small` 与搜索页同一句）。
 *
 * S1..S4 就是 [PortalSystem] 的四个取值，与门户文档的 79 个 ID 分组一一对应
 * （`audit/FUNCTION-INVENTORY.md:12`："79 个不同 ID：S1 37 + S2 18 + S3 20 + S4 4"）：
 * CJH52 → S1、JSP → S2、STANDALONE → S3、CAMPUS_ONLY → S4。
 *
 * 门户下发的 `PortalAppEntry` 只有 url / introduction / color，**没有分组字段**，
 * 所以这里不编一个「文档里写着但运行时拿不到」的分组名；能确定的只有两件：
 * 属于哪个子系统（[PortalSystem]），以及它是不是字面内网地址（[PortalApp.needsCampusNetwork]）。
 * 徽标只报这两件事实，再带上门户自己的 ID —— 它是用户能拿去对账的唯一编号。
 */
internal fun portalSourceBadge(app: PortalApp): String {
    val group = when (app.system) {
        PortalSystem.CJH52 -> "S1"
        PortalSystem.JSP -> "S2"
        PortalSystem.STANDALONE -> "S3"
        PortalSystem.CAMPUS_ONLY -> "S4"
    }
    val network = if (app.needsCampusNetwork) "需校园网 / WebVPN" else "原站服务"
    return "$group · $network · ID ${app.id}"
}
