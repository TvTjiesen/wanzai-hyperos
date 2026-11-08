package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import com.muyao.wanzaihyperos.ui.design.liquid.rememberPageChromePilot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.muyao.wanzaihyperos.R
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.data.schedule.PeriodClock
import com.muyao.wanzaihyperos.data.tronclass.TronSchedule
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.data.schedule.jwVerdictOf
import com.muyao.wanzaihyperos.data.schedule.pickSlots
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Chip
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.PeriodTimeEditor
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.retrievalGlow
import com.muyao.wanzaihyperos.ui.design.liquid.LargeTitle
import com.muyao.wanzaihyperos.ui.design.liquid.LocalLargeTitle
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageChrome
import com.muyao.wanzaihyperos.ui.design.liquid.iosHeaderInset
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

/**
 * **課程表板塊**：完整一週 + 可切週 + **兩種排版可切換**。
 *
 * ## 資料來源（真實 API，不是猜的）
 *
 * 學在城院 `AppGraph.schedule`：
 * - `state: StateFlow<ListState<TronSchedule>>` —— 一次抓回**全部**課表格子
 *   （`TronScheduleSlot` 自帶 `weekStart..weekEnd` 的週次區間）；
 * - `slot.activeIn(week)` 判斷某週有沒有這堂課。
 *
 * 所以「切週」**不需要重新請求** —— 資料本來就含整學期。
 * 這一點很重要：切週若每次都打介面，滾一下就會撞限流。
 *
 * ## 為什麼用不透明卡而不是玻璃卡
 *
 * 規範 §14 的性能預算：「同屏實時折射的面積要預算；長列表裡的玻璃一律降級」。
 * 課表是密集格子，全開玻璃會很燙很卡。所以**只有頂部週切換條是玻璃**。
 *
 * ## 兩種排版（業主 2026-10-18 點名要 WakeUp 那一類）
 *
 * - **網格**：左一列節次 + 七（或五）列星期，課程塊按 `periodStart..periodEnd`
 *   **豎向合併**、左緣色脊按 `courseId` 從十枚手選色裡輪轉（見 [CourseChip] 的配色段）。
 *   版式照 WakeUp / [Sleepy](https://github.com/lingion/sleepy) 那一類**自己寫**
 *   —— Sleepy 是 GPL-3.0，**一行代碼都不搬**，只参照它的版面結構。
 * - **列表**：按天分段的**時間線**（左邊節次、右邊課名/老師/教室，趕時間時看得更快）。
 *
 * ⚠️ 節次**不顯示幾點幾分**：學校沒有「節次→時間」表（見 `TronScheduleSlot` 檔頭），
 * 猜一個時間就是編造。WakeUp 那個版式裡的時分列，我們只能放「第 N 節」。
 *
 * ⚠️ 選了哪種排畫存在 [AppUiState]（進程內），**沒有落盤** ——
 * 這個工程的界面偏好目前一律如此（`themeMode` 也没落盘），要落盘应该单开一张卡统一做，
 * 不要在这一页偷偷加第一个 SharedPreferences。
 */
@Composable
fun TimetableSectionPage(
    graph: AppGraph,
    uiState: AppUiState,
    /** 「去教务系统」那一頁（要在那裡輸一次帳號密碼，課表才爬得動）。 */
    onOpenJw: () -> Unit = {},
    /** 頁尾「學在城院 · 本週 / 全部」：進原生 TronClass（我的課 + 待交作業）。 */
    onOpenTron: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val state by graph.schedule.state.collectAsState()
    val jwState by graph.jwTimetable.state.collectAsState()

    // 目前教學週（由 schedule 自己算，它會問 `teachingWeeks()`）。
    // 提到這裡是因為**週切換那一行**也要用它：那行現在要寫「第 6 周 · 2026-2027学年第1学期」，
    // 而它渲染在課表內容之前，拿不到內容分支裡的局部變數。
    // 頂部「第 N 周」「‹ ›」和下面的內容必須用**同一個**周次。
    // 上一版標題只讀 schedule.currentWeek，而內容分支（和點陣）早就會退回
    // `UiPrefs.weekFromAnchor` 推算 ⇒ 冷啟動時標題寫「還沒取到教學周」，
    // 底下卻老老實實列著「第 6 周」的課，翻周箭頭也像死的（审查 2026-10-21）。
    // 這裡只讀錨點、不寫盤：一條裝飾性的標題不該改持久化狀態。
    val scheduleWeek = (state as? ListState.Content)?.items?.firstOrNull()
        ?.currentWeek as? com.muyao.wanzaihyperos.data.CountValue.Known
    val baseWeek = scheduleWeek?.value?.takeIf { it > 0 }
        ?: com.muyao.wanzaihyperos.data.UiPrefs.weekFromAnchor(java.time.LocalDate.now().toEpochDay())
    /** 學期名：**學校自己寫的**那一段（教务课表页标题里的「2026-2027学年第1学期」）。空 = 还没取到。 */
    val termLabel = (jwState as? ListState.Content)?.items?.firstOrNull()?.termLabel.orEmpty()
    /**
     * 課表**只认教务那一份**（业主 2026-10-20：「数据来源那行去掉，统一用教务系统的」）。
     *
     * 以前这里读 `UiPrefs.timetableSource` 并给一屏一个切换器。去掉的理由不是"少一个按钮"：
     * 两份来源本来就该有一份是权威（排课在正方），让一个学生去判断"哪份对"是把我们的
     * 数据分歧转嫁给他 —— 他每次进来都要重新选一次，选错了还看不出区别。
     *
     * 学在城院那份**没有删**：教务取不到时仍然退回它（课表这一屏每天要用，
     * 不能因为教务登录态掉了就空白），但那是**兜底**，由顶部横幅说明，而不是让他选。
     */
    val useJiaowu = true

    // 要顯示哪一週。
    //
    // `0` = 目前教學週（由 schedule 自己算，它會問 `teachingWeeks()`）；
    // 正負數 = 相對偏移。**不重新請求資料** —— 資料本來就含全學期。
    var weekOffset by remember { mutableIntStateOf(0) }
    /** 日程模式下看的是哪一天（1=周一…7=周日）。默认今天。 */
    // ★ 2026-10-31：初值**不再夹进 1..5**。以前夹的理由是「周条只到周五，6/7 进来无高亮」，
    //   代价是周六周日打开课表看到的是**周五的课表**：左边写着「周五」，而右边那颗
    //   「今天」药丸按下去切的却是周六 —— 字和事对不上。
    //   空的一天也是一条信息（见 `WeekLayout.columns`：「週三沒課」不能因為沒課就消失），
    //   周六就该说「周六没有课。」。周条的高亮另治（见 `WeekDateStrip` 里 `isSelected`）。
    var selectedDay by remember {
        mutableIntStateOf(LocalDate.now().dayOfWeek.value)
    }
    var picked by remember { mutableStateOf<TronScheduleSlot?>(null) }
    // 2026-10-28 业主「我想要那种点击放大的动画」：详情不再插进列表，而是从**被点那一格**
    // 长到页面中央。所以点的时候要把那一格的位置记下来（root 坐标），并减掉页面自身原点。
    var pickedAnchor by remember { mutableStateOf<Rect?>(null) }
    /** 正在播退场动画。播完（onClosed）才真正把 picked 清掉，否则整层会一帧消失。 */
    var zoomClosing by remember { mutableStateOf(false) }
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }
    /** 整周板正文顶端在 root 里的 y（量出来的，用来把板子压进一屏）。 */
    var boardTopPx by remember { mutableStateOf(0f) }
    /** 这一页的真实高度（root 像素）；0 = 还没量到。 */
    var pageHeightPx by remember { mutableStateOf(0f) }
    /** 「節次時間」就地编辑（填完课表上每块课立刻显示几点到几点）。 */
    var showClockEditor by remember { mutableStateOf(false) }

    // ---- 頂部一週點陣的資料 ------------------------------------------------
    //
    // 點陣畫在週切換條旁邊，而「目標週 / 用哪一份資料」的推導在下面的內容分支裡。
    // 這裡用的是**同一份 state、同一個 WeekLayout.forWeek**，不重新請求、不改狀態機。
    // 刻意不呼叫 UiPrefs.noteWeek：它會把週錨點寫盤，而點陣在載入態也會畫，
    // 一條只讀的裝飾條不該去改持久化狀態。
    // ★ 独立审计 v3 #4 第 4 轮必修 4（N4）：这一页以前有**第二套 crawling 判法**
    //   （`jwState is ListState.FirstLoad`），而 `SlotSource.kt` 自己就禁了那种写法。
    //   只认 `isCrawling` 又会给本页留一帧空档（组合完 → LaunchedEffect 才发请求），
    //   那一帧会画另一份 ⇒ 又是那个闪。所以这里说的是**一个可验证的事实**：
    //   本页一进来必发这一发（:238 那个 `LaunchedEffect`），在它落回非 FirstLoad 之前，
    //   "在路上"为真。这不是拿状态猜，是拿本页自己的意图记。
    val jwWillFetch = remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(jwState) {
        if (jwState !is ListState.FirstLoad) jwWillFetch.value = false
    }
    val jwCrawlingNow = graph.jwTimetable.isCrawling.value || jwWillFetch.value

    val stripSchedule = (state as? ListState.Content)?.items?.firstOrNull()
    val stripJw = (jwState as? ListState.Content)?.items?.firstOrNull()
    // 選資料的規則全工程只有 `pickSlots` 一份（與正文、首页、外观预览同一条，见该档案注释）：
    // 教務還在爬時**不拿學在城院頂上去**，否則點陣會先亮起幾門他其實沒上的課。
    val stripAllSlots = pickSlots(
        jwSlots = if (useJiaowu) stripJw?.slots.orEmpty() else emptyList(),
        jwVerdict = jwVerdictOf(
            state = jwState,
            crawling = jwCrawlingNow,
            hasSlots = !stripJw?.slots.isNullOrEmpty(),
        ),
        tronSlots = stripSchedule?.slots.orEmpty(),
    ).slots
    // 外层 baseWeek 已含锚点兜底，这里不再重复算一次（两处算法迟早分叉）。
    val stripTargetWeek = baseWeek?.plus(weekOffset)
    // 週次不知道時 forWeek 會原樣回傳全學期 —— 那點會按「整學期」亮，不是「這週」。
    // 所以不知道週次就一個點都不畫（空清單），也不編一個週次出來。
    val stripSlots = if (stripTargetWeek == null) {
        emptyList()
    } else {
        com.muyao.wanzaihyperos.data.schedule.WeekLayout.forWeek(stripAllSlots, stripTargetWeek)
    }

    LaunchedEffect(Unit) {
        // 应用级作用域（`AppGraph.fetch`）：切走页面不该把课表请求取消，
        // 更不该让取消被写成「取不到課表」。见 `AppGraph.ioScope` 注释。
        graph.fetch { graph.schedule.refresh() }
        // 教務那份**只在選了它時才爬**：它要走 WebView 兩段登入，
        // 沒選就爬是白做一次登入（而且會把共用 WebView 導到課表頁）。
        if (useJiaowu) {
            graph.fetch { graph.jwTimetable.refresh() }
        }
    }

    // ── 顶栏试点接线（T53）────────────────────────────────────────────
    //
    // 大标题不再由本页自己画，而是照参考那份 `ui/screen/Page.kt`：
    // **标题是列表的第 0 项**，跟着内容一起滚；滚到顶就由 `IosTopBar` 接手小标题。
    // 折叠度 = 标题被滚出去的比例，只用 `firstVisibleItem` 的信息，
    // 不接嵌套滚动 —— 参考原话，也是它不会和底栏抢手势的原因。
    // 頂欄接線抽到 `rememberPageChromePilot()`（T53 的試點代碼）——
    // 現在有五個板塊要接，抄五遍遲早分叉。
    val pilot = rememberPageChromePilot()
    val largeTitle = LocalLargeTitle.current
    val barVisible = LocalNavBarVisible.current
    val listState = pilot.listState
    // ★ 第 9 轮审计必修 4：滚动状态报给玻璃层（滚动期降采样）。见 `PageChrome.scrolling`。
    androidx.compose.runtime.SideEffect { pilot.onScroll(listState.isScrollInProgress) }

    // ★ 2026-11-07：这一页**不再挂小球**。
    //
    // 业主 2026-10-25 说过「每个页面最好都设计一套」，于是五页标题左边各挂一只球。
    // 但 v2.1 稿的课表页第 1 行只有三件东西（`第 N 周` / `‹` / `回到今天` / `›`），
    // 「第」字起笔就在页边距上 —— 前面塞一只 36dp 的球会把整行右移 40dp，
    // 与 `screens/timetable.png` 对不上。用户 2026-11-07 的口径是「其余页面一比一复刻」，
    // 这一页的数据密度也最高，球在这里只是噪音。
    // `AiBallStates.ofTimetable` 本身没删（纯函数，`AiBallStatesTest` 仍在验它），
    // 哪天课表页要回一只球，接回去是一行的事。

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 放大的起點要用「格子在 root 裡的座標」減掉「頁面在 root 裡的原點」，
            // 這樣不管主殼把這一頁放在哪（狀態欄下方、有無側欄）算出來的落點都對。
            .onGloballyPositioned {
                pageOrigin = it.boundsInRoot().topLeft
                pageHeightPx = it.size.height.toFloat()
            },
    ) {
    // ── 教務課表要的「看不見的登入器」────────────────────────────────────
    //
    // ★ 為什麼必須放在這裡（而不是只在「教务系统」那一頁才有）：
    //   教務的登入**只能靠 WebView 走它自己的表單**（`docs/B18-WALKTHROUGH.md` §4），
    //   而爬課表的是**這一頁**。沒有這個 1dp 的 WebView，
    //   `JwEngine.awaitAttached()` 等 5 秒就會回「页面还没准备好」——
    //   那一句話我在真機上還沒跑就已經從程式碼裡看見了。
    //
    // 只在選了教務時才掛：掛著就多一個 WebView 的內存與進程成本，
    // 而他可能一學期都用不著那份來源。
    if (useJiaowu) {
        JwHiddenEngine(graph.jwEngine)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            // 标题自己带 inset，这里不再留 56dp 顶白
            // ★ #58 第 6 条：这里原来是写死的 132.dp —— 全工程第二个「底栏内距」真值。
            //   132 是「悬浮胶囊那会儿」拍的数：它等于共享公式 94dp（= 64 条高 + 24 inset + 2 + 4）
            //   再多加 38dp。多出来的那段是底栏覆盖不到的空白，但那是**已经上线的观感**，
            //   所以这里不悄悄减少它，只把「底栏实占」那部分换成同一份 pageBottomInsetDp()，
            //   于是底栏高度一改，这一页跟着动，不再各猜一个数。
            bottom = pageBottomInsetDp(barVisible) + 38.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        // ---- 周次 + 回到今天 + 翻周（设计稿 v2.1 课表页第 1 行）-------------------
        //
        // 2026-11-07 按 `screens/timetable.png` 复刻：这一行**只有三件东西** ——
        //   左「第 11 周」黑粗、右「‹ 回到今天 ›」。三个都是**裸元素**：
        //   `‹`/`›` 在稿上宽 7~8px、颜色 (81,86,98)，**没有描边圆**（旧版是 23dp 的圆环）；
        //   「回到今天」是主色文字按钮，没有底色也没有边框。
        // 原来这一行是「球 + 『课表』主色小胶囊 + ‹ › 两个圆环」——球挪走后整行才和稿对齐。
        item(key = "large_title") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { if (it.height > 0) pilot.titleHeightPx = it.height.toFloat() }
                    .padding(start = 0.dp, end = 0.dp, top = statusBarTopPadding() + 4.dp, bottom = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // 稿上写「第 11 周」。周次取不到时说「这一周」，**不编一个第 11 周**
                        //（`DEEPSEEK-IMPLEMENTATION.md §4.3`：跨周日期用真实学期锚点）。
                        text = stripTargetWeek?.let { "第 $it 周" } ?: "这一周",
                        style = TextStyle(fontFeatureSettings = Type.tabular),
                        fontSize = Type.body,
                        fontWeight = Type.weightTitle,
                        letterSpacing = (-0.02).em,
                        color = colors.onBackground,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    WeekNavButton("‹", desc = "上一周", backwards = true) { weekOffset -= 1 }
                    // 「回到今天」= 周次归零 + 选中日回到今天那一格，两件事一起做。
                    // 只归零周次会停在「本周的周三」上，而顶栏写着第 N 周 —— 与按钮字面不符。
                    Text(
                        text = "回到今天",
                        fontSize = Type.caption,
                        fontWeight = Type.weightBody,
                        color = colors.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClickLabel = "回到今天") {
                                weekOffset = 0
                                selectedDay = LocalDate.now().dayOfWeek.value
                            }
                            // 视觉上一颗文字按钮，但触区要够大：左右 10dp、上下 12dp
                            // ⇒ 落在 ≥44dp 的命中带里（§4.2「视觉与触区分开」）。
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                    WeekNavButton("›", desc = "下一周") { weekOffset += 1 }
                }
            }
        }
        // ---- 日期条（板子：7 个数字 + 有课小圆点；点一天就切到那天）----------
        //
        // ★ 2026-10-30 业主：「课表侧边的 ui 也有点丑，包括顶部的日期」。
        //   真机帧上这条周条和下面的整周板表头**是同一行字**（都是一26 二27 … 五30），
        //   一屏里把同一件事写两遍，还占掉 160px —— 而那 160px 正是板子要的行高。
        //   当时整周模式（板子在场）里就不画周条了。
        //
        // ★ 2026-11-07 改回来：v2.1 稿（`screens/timetable.png`）与实施手册 §4.1 的**固定顺序**
        //   是「52dp 顶栏 → 第N周 + 前/回到今天/后 → **七天条** → 左侧日期 + 右侧日/周胶囊 →
        //   议程或网格 → 来源」—— 七天条是这一屏的固定一层，不随模式隐藏。
        //   两处日期字确实会同时出现，但职责不同：周条是**可点的一天胶囊**（带选中态、有课点），
        //   板表头是**列标签**（点它切到日程）。业主当时嫌的是「同样一行字写两遍」，
        //   现在周条有主色竖胶囊、板表头是两行小字，视觉上不再是同一件东西。
        //   高度代价：周条 7 格 × padding 10dp + 三行字 ≈ 78dp，板表头 40dp，
        //   板体按 `boardHeightPx` 自己算行高，不会溢出（见 WeekBoard 的 overhead 计算）。
        item(key = "week-strip") {
            val today = LocalDate.now()
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                .plusWeeks(weekOffset.toLong())
            WeekDateStrip(
                monday = monday,
                selected = selectedDay,
                today = if (weekOffset == 0) today.dayOfWeek.value else 0,
                dotsFor = { day -> stripSlots.count { it.dayOfWeek == day }.coerceAtMost(3) },
                onSelect = { day ->
                    selectedDay = day
                    // 点某一天 = 回到日程；「整周」是另一颗按钮。
                    if (uiState.timetableGrid) {
                        uiState.timetableGrid = false
                        UiPrefs.saveTimetableGrid(false)
                    }
                },
            )
        }

        // ---- 今天 / 整周（板子：左边日期与门数，右边两颗小胶囊）----------------
        item(key = "view-switch") {
            val today = LocalDate.now()
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                .plusWeeks(weekOffset.toLong())
            val date = monday.plusDays((selectedDay - 1).toLong())
            val daySlots = stripSlots.filter { it.dayOfWeek == selectedDay }
            val dayCourses = daySlots
                .map { if (it.courseId != 0) "id:${it.courseId}" else "name:${it.courseName}" }
                .distinct().size
            DayModeSwitch(
                // 板 `.dayhead`：`h3{font-size:17px;font-weight:740}` + `.n{font-size:10.5px;color:var(--sub)}`
                // × 0.886 ⇒ **周几 15sp 粗 + 日期与节数 9.3sp 淡**，两档。
                // 原来三者串成同一句、同一档 10sp —— 周几是这一屏的主语，被降级成
                // 和元数据同级，列表首行就没有层级了（复评 2026-10-21）。
                // 整周模式下左边必须说「这一周」，不能说「周三」——
                // 2026-10-30 真机抓到的缺陷：点表头周三看日程、再点回「整周」，
                // 左边仍写着「周三 10 月 28 日 · 1 门课」，而板上画的是整周 8 门课。
                // 同一屏两种主语，读的人第一眼就分不清板上那五列是"这周"还是"周三"。
                label = if (uiState.timetableGrid) {
                    if (weekOffset == 0) "本周" else stripTargetWeek?.let { "第 $it 周" } ?: "这一周"
                } else {
                    DAY_WEEK[selectedDay - 1]
                },
                // 整周模式这行只写日期区间：门数板子底下已经有一句「本周 8 门课」，
                // 再说一遍既啰嗦又把这行挤到截断（2026-10-30 真机帧上正是「· 8 …」）。
                meta = if (uiState.timetableGrid) {
                    buildString {
                        append("${monday.monthValue} 月 ${monday.dayOfMonth} 日")
                        val sun = monday.plusDays(6)
                        append(" – ")
                        if (sun.monthValue != monday.monthValue) append("${sun.monthValue} 月 ")
                        append("${sun.dayOfMonth} 日")
                    }
                } else {
                    buildString {
                        append("${date.monthValue} 月 ${date.dayOfMonth} 日")
                        if (daySlots.isNotEmpty()) append(" · $dayCourses 门课")
                    }
                },
                // 药丸上的字**必须等于按下去会看到的那一天**：`onFirst` 把 `selectedDay`
                // 设成**今天**，不是设成当前选中的那天。周六进这一页时以前写「周五」、
                // 按下去却切到周六 —— 见 [dayPillLabel]。
                firstLabel = dayPillLabel(today.dayOfWeek.value, weekOffset),
                weekMode = uiState.timetableGrid,
                onFirst = {
                    selectedDay = today.dayOfWeek.value
                    if (uiState.timetableGrid) {
                        uiState.timetableGrid = false
                        UiPrefs.saveTimetableGrid(false)
                    }
                },
                onWeek = {
                    uiState.timetableGrid = true
                    UiPrefs.saveTimetableGrid(true)
                },
            )
        }
        // ---- 課表內容 -------------------------------------------------------
        //
        // ★ 選了教務時，**教務那一支的狀態才是主角**：
        //   它還在爬就說在爬，它失敗就把原因顯示出來（含「要先在設定裡存教務帳密」），
        //   絕不退回去畫學在城院那份卻標著「教務」—— 那是會讓人走錯教室的謊。
        // 教務那份**取不到時不空著**：先退回學在城院那一份，並在頂上說清楚
        // 「現在顯示的是另一份、為什麼」。
        //
        // ★ 為什麼要退而不是報錯完事（业主 2026-10-19 同時說過「好多功能都報錯」）：
        //   課表是每天要看的东西。教务那份爬不通（沒帳密／學校改版／介面形態不同）時，
        //   只顯示一張錯誤卡等於這一頁今天沒用 —— 而學在城院那份八成是對的。
        //   退回去 + 標明來源，比「正確地失敗」更有用，也沒有說謊。
        val jwResultNow = (jwState as? ListState.Content)?.items?.firstOrNull()
        val jwUsable = jwResultNow != null && jwResultNow.slots.isNotEmpty()
        // ★ 第 4 轮必修 2（N1）：横幅以前用 `jwResultNow != null` 分"没取到 / 这周没课"，
        //   而 Empty 修好之后 **Empty 没有 items** ⇒ 教务真给了空表时这里说"没取到"，
        //   同一屏下面那句又说"那份是空的" —— 两句话同时挂在一张屏上。
        //   现在两处都读同一个 `jwVerdictOf`，一个态只有一种说法。
        val bannerVerdict = jwVerdictOf(jwState, jwCrawlingNow, jwUsable)
        // 首屏（FirstLoad）由下面那张 `jw-pending` 单独说明；这张只在**已经有结论**时出现
        // （失败 / 登录失效 / 空的），否则两张卡会同时挂在屏上，各说各的。
        val jwPending = useJiaowu && jwState is ListState.FirstLoad
        // ★★ 独立审计 v3 #4 第 1 轮 A-5：这句话以前**只问教务、不问学在城院有没有货**。
        //   教务 Failed、学在城院也 Failed 的那一屏，上面写"下面显示的是学在城院那份"，
        //   下面同一屏就是"取不到课表"——界面自己跟自己 contradiction。
        //   现在必须**兜底那份真的有货**才挂这张横幅；没有货就不该承诺"下面有"。
        val tronHasSlotsNow = (state as? ListState.Content)
            ?.items?.firstOrNull()?.slots?.isNotEmpty() == true
        if (useJiaowu && !jwUsable && !jwPending && tronHasSlotsNow) {
            item(key = "jw-fallback") {
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text(
                            // 原来这里还写着 `if (jwState is ListState.FirstLoad) "正在从教务系统取课表…"`，
                            // 但外层 `!jwPending` 已经把 FirstLoad 排除掉了 —— 那是永远走不到的死分支，删。
                            // 剩下的两种要分开说：**教务给了结论、这周没课** 与 **这一次没取到**，
                            // 把后者说成"这周没课"是在替学校撒谎（红线：不编）。
                            text = com.muyao.wanzaihyperos.data.schedule
                                .jwVerdictSentence(bannerVerdict) + "，下面显示的是“学在城院”那份",
                            fontSize = Type.body,
                            fontWeight = Type.weightBody,
                            color = colors.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        val jwDetail: String = when (jwState) {
                            is ListState.Failed ->
                                (jwState as ListState.Failed).error.userText
                            // ★ 這一態要**單獨講**：它不是壞了，是還沒給他憑據。
                            //   把它混進「取数失败」他就會一直點重試而不知道差在哪。
                            ListState.SessionExpired ->
                                "教务系统还没登录 —— 那一页要输一次账号密码（存在手机加密存储）"
                            is ListState.Content ->
                                jwResultNow?.unparsed?.firstOrNull()
                                    ?: "教务那份是空的（可能学校还没排这学期的课）"
                            // 外层已经把 FirstLoad 排除掉了，这一支只剩 Empty（服务端说没有）。
                            // 以前它写"正在取…"，而这一刻并没有任何东西在跑。
                            else -> "教务那份没给出内容"
                        }
                        Text(
                            text = jwDetail,
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            if (jwState is ListState.SessionExpired) {
                                Text(
                                    text = "去教务系统登录 ›",
                                    fontSize = Type.caption,
                                    color = colors.primary,
                                    modifier = Modifier
                                        .clickable { onOpenJw() }
                                        .padding(end = 14.dp),
                                )
                            }
                            Text(
                                text = "重试教务那份",
                                fontSize = Type.caption,
                                color = colors.primary,
                                modifier = Modifier.clickable {
                                    graph.fetch { graph.jwTimetable.refresh() }
                                },
                            )
                        }
                    }
                }
            }
        }
        // ★ 教务還在爬的時候**不畫另一份**（业主 2026-10-20：「你课表里面还有垃圾没删完，
        //   会闪过一些错误的数据估计是学在城院里的数据」）。
        //   以前這一屏會先把學在城院那份畫出來、教务回来再整屏換掉 ——
        //   閃過的那幾門課是他**實際上沒有的**，比一片空白更糟（他會以為要去上）。
        //   所以加載中只給進度，不給另一份；只有教务**明確失敗**才退回並說明。
        //
        // ★★ 2026-10-22 v3 清单 #4：业主说这个闪**还在**。根因就是下面原来那个
        //   `&& !fallbackUsable` —— 学在城院那份一回来 `fallbackUsable` 就为真，
        //   于是这张"先不放另一份"的卡被自己关掉，直接落进 else 的 when，
        //   而那个 when 的 `state is ListState.Content` 分支画的就是学在城院那份。
        //   **闸门被它想保护的那份数据打开了**，等于这条规矩从来没生效过。
        //   现在只认 `jwPending`：教务在路上就只给进度，别的一律不画。
        if (jwPending) {
            item(key = "jw-pending") {
                // 这张卡本身就是「正在从教务系统取课表」那句话（只有 pending 时才挂），
                // 所以 active 恒为真：会走到这一支，就说明真有一趟爬取在路上。
                AppCard(
                    modifier = Modifier.retrievalGlow(
                        active = true,
                        cornerRadius = Dims.cardCorner,
                        color = colors.primary,
                    ),
                ) {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text(
                            text = "正在从教务系统取课表",
                            fontSize = Type.body,
                            fontWeight = Type.weightBody,
                            color = colors.onBackground,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            // 不藏「這一次會慢」：它要走一次網頁登入 + 等它自己把表格填完。
                            text = "教务那份要走一次网页登录、再等它自己把表格渲染完，" +
                                "第一次可能要十几秒。这一屏先不放另一份数据，" +
                                "免得闪出几门你其实没上的课。",
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        } else when {
            // ★ 判「这一屏有没有东西可画」用的是**内容**，不是「学在城院那支请求回没回」
            //   （业主 2026-10-20：「首先你就没解决刷新的问题，我不想每次进来都要看到他
            //    重新加载一遍」）。教务那份已经落盘、周次也能从锚点推出来时，
            //   第一眼就该是课表本身；只有**两份都还没有**才给一句「正在加载」。
            !jwUsable && state is ListState.FirstLoad -> item(key = "loading") {
                Text(
                    "正在取得课表…",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(Dims.cardPadding),
                )
            }

            !jwUsable && state is ListState.Failed -> item(key = "failed") {
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text(
                            "取不到课表",
                            fontSize = Type.body,
                            fontWeight = Type.weightBody,
                            color = colors.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (state as ListState.Failed).error.userText,
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            jwUsable || state is ListState.Content -> {
                // ⚠️ `schedule.state` 是 `ListState<TronSchedule>`，而 `Content.items`
                // 是 `List<TronSchedule>` —— **單元素清單**（倉庫把單一 schedule 包起來）。
                // 所以要先 `firstOrNull()` 才是 TronSchedule 本身。
                // 走到這裡時它**可能還在路上**（教务那份已經夠畫了）⇒ 全部按可空讀。
                val schedule = (state as? ListState.Content)?.items?.firstOrNull()
                // 目前教學週：學在城院算出來的那個是**權威值**（拿到就順手把錨點往前推，
                // 見 `UiPrefs.weekAnchor`）；它還沒回來時用錨點推算，於是課表能秒開。
                // 顯示值直接用外層 baseWeek（已經含同一條錨點兜底），避免標題/內容各算各的。
                com.muyao.wanzaihyperos.data.UiPrefs.noteWeek(
                    (schedule?.currentWeek as? com.muyao.wanzaihyperos.data.CountValue.Known)?.value,
                    java.time.LocalDate.now().toEpochDay(),
                )
                val targetWeek = baseWeek?.plus(weekOffset)

                // 全學期的槽位：優先教務那份（帶老師與教室），取不到就退回學在城院。
                // 上面那張「退回」橫幅已經把這件事講給他聽了，這裡只負責選對資料。
                val jwResult = (jwState as? ListState.Content)?.items?.firstOrNull()
                val bodyPick = pickSlots(
                    jwSlots = jwResult?.slots.orEmpty(),
                    jwVerdict = jwVerdictOf(
                        state = jwState,
                        crawling = jwCrawlingNow,
                        hasSlots = !jwResult?.slots.isNullOrEmpty(),
                    ),
                    tronSlots = schedule?.slots.orEmpty(),
                )
                val fromJiaowu = bodyPick.fromJiaowu
                val allSlots = bodyPick.slots

                if (targetWeek == null) {
                    item(key = "no-week") {
                        Text(
                            "还不知道目前是第几教学周，所以无法对应课表。",
                            fontSize = Type.secondary,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier.padding(Dims.cardPadding),
                        )
                    }
                } else {
                    val slots = com.muyao.wanzaihyperos.data.schedule.WeekLayout.forWeek(
                        allSlots, targetWeek,
                    )
                    val columns = com.muyao.wanzaihyperos.data.schedule.WeekLayout.columns(slots)
                    val courseCount =
                        com.muyao.wanzaihyperos.data.schedule.WeekLayout.courseCount(slots)
                    val periodCount = slots.sumOf {
                        (it.periodEnd - it.periodStart + 1).coerceAtLeast(1)
                    }
                    val source = if (fromJiaowu) {
                        com.muyao.wanzaihyperos.data.tronclass.ScheduleSource.JIAOWU
                    } else {
                        com.muyao.wanzaihyperos.data.tronclass.ScheduleSource.TRONCLASS
                    }

                    // 「有的课还不知道几点」这句话要在**课表上**能解决，
                    // 不是让他自己去設定里翻（业主：「你课表和时间对不上」）。
                    item(key = "clock-hint") {
                        ClockHint(slots) { showClockEditor = true }
                    }

                    if (slots.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                if (bodyPick.jwVerdict == com.muyao.wanzaihyperos.data.schedule.JwVerdict.SETTLED_EMPTY) {
                                    // 与上面那张横幅同一个 verdict：一个态只有一种说法（N1）。
                                    // 只有教务**真的给了结论且是空的**才可以说这句话（第 3 轮必修 6）：
                                    // 以前 `allSlots.isEmpty() && useJiaowu` 把"教务失败了 / 从没取过 /
                                    // 空的是学在城院那份"都说成"教务那份是空的"。
                                    "教务系统那份课表是空的（可能学校还没排这学期的课）。"
                                } else {
                                    "第 $targetWeek 周没有课程。"
                                },
                                style = TextStyle(fontFeatureSettings = Type.tabular),
                                fontSize = Type.secondary,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(Dims.cardPadding),
                            )
                        }
                    } else if (uiState.timetableGrid) {
                        // ---- 本週課表板（五天全顯示、單層）--------------------
                        val boardMonday = LocalDate.now()
                            .minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
                            .plusWeeks(weekOffset.toLong())
                        item(key = "board") {
                            // 业主第 ⑤ 条「课表的占地面积可以大一点」：这一页的 LazyColumn 左右各有
                            // screenH=16dp 边距，别的 item 需要它，板子不需要 —— 板子越宽，一行能放的
                            // 课名越全。fullBleedHorizontal 把子项按「两边各多 16dp」测量，再按原宽
                            // 上报、横向外扩半个出血位画出去：父级只认上报的宽度，多出来的部分
                            // 落在 LazyColumn 自己的边界内（它只裁自己的边，不裁 16dp 边距区）。
                            // 五栏各宽 220.8px，比加边距时每栏宽 12.8px（3.7dp）。
                            // 2026-10-28 业主：「把课表的那个背景去掉」。
                            // 原来这层 AppCard 给了板子一个实色卡底 + 1dp 描边 + 7dp 投影，
                            // 代价是横向吃掉 cardPadding×2 = 28dp、纵向再多一层卡边距 ——
                            // 列宽从 45.49dp 掉到 45.49dp 的同一行里再挤 28dp 分给五列。
                            // 去掉之后板子直接坐在纸面上，五列各宽 51.49dp（+13%）。
                            WeekBoard(
                                        // 2026-10-29 业主：「看完整个页面得上下滑动了…我要他刚好铺满
                                        // 不用翻页」。行高不能写死，得由「这块板子真正拿到多少高度」算：
                                        // 这里顺手量出正文顶端的 root y，页面根节点量出整页高度，
                                        // 两者相减再扣掉底栏预留，就是留给整周板的那一段。
                                        modifier = Modifier
                                            .fullBleedHorizontal(5.dp, Dims.screenH - 5.dp)
                                            .onGloballyPositioned { co ->
                                                val y = co.positionInRoot().y
                                                if (kotlin.math.abs(y - boardTopPx) > 0.5f) boardTopPx = y
                                            },
                                        boardHeightPx = if (pageHeightPx > 0f && boardTopPx > 0f) {
                                            val reserve = with(LocalDensity.current) { BoardBottomReserve.toPx() }
                                            (pageHeightPx - boardTopPx - reserve).coerceAtLeast(0f)
                                        } else {
                                            0f
                                        },
                                        columns = columns,
                                        // 只有看本週時才高亮今天；翻到別的週還標「今天」
                                        // 是把上週的課說成今天有課（列表視圖早就這麼判了）。
                                        today = if (weekOffset == 0) {
                                            LocalDate.now().dayOfWeek.value
                                        } else {
                                            0
                                        },
                                        week = targetWeek,
                                        courseCount = courseCount,
                                        source = source,
                                        // 表头要带日期数字（业主「包括顶部的日期」）：
                                        // monday 在这一分支里由 targetWeek 反推不出来，直接用本周一。
                                        dayDate = { dow -> boardMonday.plusDays((dow - 1).toLong()).dayOfMonth },
                        onPickDay = { dow ->
                            selectedDay = dow
                            uiState.timetableGrid = false
                            UiPrefs.saveTimetableGrid(false)
                        },
                                        onPick = { s, b ->
                                            picked = s
                                            pickedAnchor = b
                                        },
                            )
                        }
                        item(key = "jw-fresh") {
                            FreshnessLine(jwState, now = System.currentTimeMillis()) {
                                graph.fetch { graph.jwTimetable.refresh() }
                            }
                        }
                    } else {
                        // ---- 单日日程（板子：先把今天读清楚，再给整周）----------
                        val todayDow = LocalDate.now().dayOfWeek.value
                        val isToday = weekOffset == 0 && selectedDay == todayDow
                        val daySlots = slots
                            .filter { it.dayOfWeek == selectedDay }
                            .sortedBy { it.periodStart }
                        if (daySlots.isEmpty()) {
                            item(key = "day-empty") {
                                Text(
                                    "${DAY_WEEK[selectedDay - 1]}没有课。",
                                    fontSize = Type.secondary,
                                    color = colors.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(start = 0.dp, top = 4.dp),
                                )
                            }
                        } else {
                            // 一天的课装进**一张纸面卡**，行与行之间只留一条细线 ——
                            // 板子的日程是一行一节课，不是一节课一张卡（七张卡要滚三屏）。
                            item(key = "day-agenda") {
                                // 板子的日程直接落在纸面上，没有「一张大卡把一天装起来」。
                                // 这里只保留行与行之间的发丝线，边界交给页面的左右留白。
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                ) {
                                    daySlots.forEachIndexed { idx, slot ->
                                        if (idx > 0) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(colors.onBackground.copy(alpha = 0.07f)),
                                            )
                                        }
                                        CourseCell(
                                            slot = slot,
                                            isToday = isToday,
                                            onOpen = { s, r ->
                                                picked = s
                                                pickedAnchor = r
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "week-stats") {
                            Column(Modifier.fillMaxWidth().padding(start = 0.dp, top = 10.dp)) {
                                // 板 `.weekfoot{display:flex;justify-content:space-between;
                                // align-items:center;font-size:9.5px;color:var(--faint)}` + `b{--sub/650}`
                                // ⇒ **左右两栏**，不是把三段用「·」串成一行：
                                // 串起来那一行会自己折行，右边就空了（复评 2026-10-21）。
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "本周 $periodCount 节 · $courseCount 门课",
                                        style = TextStyle(fontFeatureSettings = Type.tabular),
                                        fontSize = Type.caption,
                                        fontWeight = Type.weightBody,
                                        color = colors.onSurfaceVariantSummary,
                                    )
                                    if (slots.none { it.dayOfWeek >= 6 }) {
                                        Text(
                                            text = "周六日无课",
                                            fontSize = Type.caption,
                                            color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = buildString {
                                        if (targetWeek != null) append("第 $targetWeek 周")
                                        if (termLabel.isNotBlank()) {
                                            if (isNotEmpty()) append(" · ")
                                            append(termLabel)
                                        }
                                        if (isNotEmpty()) append(" · ")
                                        append("来源 ${source.label}")
                                    },
                                    style = TextStyle(fontFeatureSettings = Type.tabular),
                                    fontSize = Type.micro,
                                    color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
                                )
                            }
                        }
                        // 秒开的代价必须写在屏幕上：这份是几点存的、要不要重取。
                        item(key = "jw-fresh-day") {
                            FreshnessLine(jwState, now = System.currentTimeMillis()) {
                                graph.fetch { graph.jwTimetable.refresh() }
                            }
                        }
                    }

                    // ---- 認不出來的段落**原樣列出來**（兩種排版都要有）----------
                    //
                    // 寧可顯示一句看不懂的字串，也不要讓某門課的上課時間靜默消失
                    //（`TronScheduleSlot` 檔頭那條紀律）。
                    // 認不出來的原文：學在城院那份是 (課名, 原文)，教務那份是一句話。
                    // 合成一種形態再顯示，兩邊都不會漏。
                    val unknown: List<Pair<String, String>> =
                        if (fromJiaowu) {
                            jwResult?.unparsed.orEmpty().map { "教务" to it }
                        } else {
                            schedule?.unparsed.orEmpty()
                        }
                    if (unknown.isNotEmpty()) {
                        item(key = "unparsed") {
                            AppCard {
                                Column(Modifier.padding(Dims.cardPadding)) {
                                    Text(
                                        "有 ${unknown.size} 段上课时间没认出格式（原样列在下面）",
                                        fontSize = Type.summary,
                                        color = colors.error,
                                    )
                                    Text(
                                        "没认出的那些**不会算进上面的门数** —— " +
                                            "所以数字比教务系统里少时，先来看这一段。",
                                        fontSize = Type.caption,
                                        color = colors.onSurfaceVariantSummary,
                                    )
                                    unknown.forEach { (courseName, raw) ->
                                        Text(
                                            "$courseName：$raw",
                                            fontSize = Type.caption,
                                            color = colors.onSurfaceVariantSummary,
                                            maxLines = 3,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> item(key = "none") {
                Text(
                    "还没有课表资料。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(Dims.cardPadding),
                )
            }
        }
            // ── 页尾（稿 v2.1 `timetable.png` 底部三件）────────────────────────
            // 稿上是折叠块「课表来源与缺失时间」+ 一排三个出口 + 一行来源小字。
            // 稿里那句「合成教务课表」是评审用的说明，产品里换成这一屏真实的两份来源。
            item(key = "source-details") {
                Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(
                        text = "课表来源与缺失时间",
                        fontSize = Type.caption,
                        fontWeight = Type.weightTitle,
                        color = colors.onSurface,
                    )
                    Text(
                        text = "课表只认教务那一份；教务还没取到时退回“学在城院”。" +
                            "两份都不知道第几节几点时，只写节次，不猜时间。",
                        fontSize = Type.micro,
                        // 行高不写死：Type.micro 配全局 1.55 倍小字行距（写死 15sp 会多一处改不动的字号）。
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item(key = "bottom-actions") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimetableFooterButton(text = "教务登录与成绩", onClick = onOpenJw)
                    TimetableFooterButton(text = "补充节次时间", onClick = { showClockEditor = true })
                    TimetableFooterButton(text = "学在城院 · 本周 / 全部", onClick = onOpenTron)
                }
            }
            item(key = "calendar-source") {
                val today = java.time.LocalDate.now()
                val custom = UiPrefs.periodTimes.count { it.first.isNotBlank() || it.second.isNotBlank() }
                Text(
                    text = "第 " + baseWeek + " 周 · " +
                        "%02d/%02d".format(today.monthValue, today.dayOfMonth) + " · " +
                        if (custom > 0) "节次设置已同步" else "节次用学校时间",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
                    style = TextStyle(fontFeatureSettings = Type.tabular),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
    }
    // 節次時間就地编辑：**存一次就落盘**（PeriodTimeEditor 每改一格就 save），
    // 所以这里不需要「保存」钮，也不会有「改了但忘了存」那种状态。
    // ── 点格子就地放大 ────────────────────────────────────────────────
    // 业主 2026-10-28：「我不喜欢这种生硬的在底下显示」「我想要那种点击放大的动画」。
    // 以前是往列表里插一张 SlotDetailCard —— 内容在下面凭空长出来，手指点的那一格
    // 和详情之间没有任何视觉联系。现在详情浮在同一页上，并从**那一格的实际矩形**
    // 长大到最终位置：起点来自 onGloballyPositioned 量出的真实坐标，不写死。
    androidx.activity.compose.BackHandler(enabled = picked != null) {
        zoomClosing = true
    }
    val zoomSlot = picked
    if (zoomSlot != null) {
        SlotZoomOverlay(
            slot = zoomSlot,
            anchor = pickedAnchor,
            pageOrigin = pageOrigin,
            closing = zoomClosing,
            onClose = { zoomClosing = true },
            onClosed = {
                picked = null
                pickedAnchor = null
                zoomClosing = false
            },
        )
    }
    OverlayDialog(
        show = showClockEditor,
        onDismissRequest = { showClockEditor = false },
        title = "节次时间",
        summary = "教务系统只标到“第几节”和上午/下午，不给几点。" +
            "第几行就是第几节，填完这一屏课立刻写明几点到几点；留空就只显示节次。",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PeriodTimeEditor(rows = UiPrefs.periodTimes) { rows ->
                UiPrefs.savePeriodTimes(rows)
            }
        }
    }
    }
}

/**
 * 教務系統那個 **1×1 透明 WebView**（登入器兼同源 fetch 的宿主）。
 *
 * 尺寸 1dp、alpha 0：**不能用 `GONE`** —— 那不執行 JS，登入會靜靜失敗
 *（`JwPage` 的檔頭記著這條實測）。
 *
 * `update` 裡再 attach 一次：這個引擎是**全域共用**的，
 * 他去「教务系统」那一頁再回來時，那邊 `onDispose` 會把引擎-detach 成 null，
 * 而這一頁的 AndroidView 不會重建 —— 不重新 attach 的話，
 * 之後每次刷課表都會報「页面还没准备好」。
 */
@Composable
private fun JwHiddenEngine(engine: com.muyao.wanzaihyperos.data.jw.JwEngine) {
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
                alpha = 0f
                engine.attach(this)
            }
        },
        update = { engine.attach(it) },
    )
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { engine.detach() }
    }
}

/**
 * 本週課表板：**左側一條時間軸 + 一天一欄、單層、五天全顯示、課塊按節次對齊行**。
 *
 * ## 為什麼長這樣（业主 2026-10-20：「你别标时间啊，跟其他课表软件一样
 * 侧边写个时间课程对齐时间不行吗」）
 *
 * 時間本來就該在**軸**上 —— 每塊課自己背一行「第3-4节 10:00-11:40」既佔地方又難讀，
 * 而且課名越長越擠。現在：行高 = 一節，軸上寫第幾節與幾點上課，課塊落在自己那幾行上。
 * 「單層」（一欄一道，不橫向切）照舊保住。
 *
 * 排布全部交給 [WeekLayout.lane]（純函式 + 單測）：它保證每一欄**恰好佔滿同樣的行數**，
 * 所以各欄在同一條水平線上。對齊要是交給界面自己湊，就會出現
 * 「週四的課比週二低半格」那種他一眼看得出、我在程式裡很難發現的錯。
 */
@Composable
internal fun WeekBoard(
    modifier: Modifier = Modifier,
    /** 正文可用高度（root 像素）。<=0 = 还没量到，退回写死的 48dp。 */
    boardHeightPx: Float = 0f,
    columns: List<com.muyao.wanzaihyperos.data.schedule.DayColumn>,
    today: Int,
    week: Int,
    courseCount: Int,
    source: com.muyao.wanzaihyperos.data.tronclass.ScheduleSource,
    /** 给表头的日期数字：dayOfWeek(1..7) -> dayOfMonth；返回 0 就不画（预览页没传日期）。 */
    dayDate: (Int) -> Int = { 0 },
    /**
     * 点表头某一天 = 切到那一天的日程。
     *
     * ★ 为什么要这个回调（2026-10-30）：整周模式下我撤掉了上面那条周条（它和表头是同一行字，
     *   还占掉 160px 行高）。撤掉之后「点一天看一天」这个动作就没有入口了 ——
     *   而表头本来就是那五颗日期，让它自己可点，比再多一行控件省一层。
     */
    onPickDay: (Int) -> Unit = {},
    onPick: (TronScheduleSlot, Rect) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // 暗档的轴字要换色：[Tone.quiet] 对纸面 4.77:1，但对暗卡片只有 3.05:1；[Tone.ink]
    // 在暗卡片上更是只有 1.14:1 —— 2026-10-27 模拟器深色档里节次号几乎看不见。
    val isDark = colors.background.luminance() < 0.5f
    val times = UiPrefs.periodTimes
    val maxPeriod = com.muyao.wanzaihyperos.data.schedule.WeekLayout.rowsNeeded(columns)
    if (maxPeriod <= 0) return
    // 2026-10-27 业主「课表的占地面积可以大一点」：行高 34 -> 48dp。
    // 9 行共 432dp + 表头 28dp = 460dp，仍落在 474.9dp 的可见视口内（余 14.9dp）。
    val density = LocalDensity.current
    val rowH = remember(boardHeightPx, maxPeriod, density) {
        if (boardHeightPx <= 0f) {
            // 没量到就只能用旧值：48dp 是 10-27「课表占地大一点」那版的高度。
            48.dp
        } else {
            // 表头也是一份固定开销：漏掉它，整块板就会比预算高出 28dp，
            // 页脚被底栏压住、页面还得再滚 70px —— 实测就是这么溢出的。
            val overhead = with(density) { HeaderHeight.toPx() + WeekBoardFooterHeight.toPx() }
            with(density) { ((boardHeightPx - overhead) / maxPeriod).toDp() }
                .coerceIn(WeekBoardMinRowHeight, 48.dp)
        }
    }

    // 轴上一行文字的**钉死行高**：两行各 12sp（≈13.8dp @font_scale 1.15），
    // 让「时刻在上、节次号在下」在任何行高下都排得下，空行也占同样的高。
    val axisLine = with(density) { Type.lineTight.toDp() }

    // ---- 表頭：軸位空一格，後面一天一格 --------------------------------
    // 两行：周几（micro）+ 日期数字（summary）。2026-10-30 加日期数字是因为整周模式下
    // 上面那条周条撤掉了，表头得自己说清「这几列是哪几天」——表头高度因此 28 → 40dp。
    // 今天那一格只做三件事 —— 换强调色、两行都加粗、底下一条 2dp 短线（线画在表头里面，
    // 不额外占高）。列底不铺 6% 淡底（对卡只有 1.09:1，等于没标），也不画竖线
    // （会和课块色脊并排成两条）。
    Row(modifier = Modifier.fillMaxWidth().height(HeaderHeight)) {
        Spacer(Modifier.width(AxisWidth))
        columns.forEach { col ->
            val isToday = col.dayOfWeek == today
            val dom = dayDate(col.dayOfWeek)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "看" + DAY_WEEK[col.dayOfWeek - 1] + "的日程") {
                        onPickDay(col.dayOfWeek)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DAY_LABELS[col.dayOfWeek - 1],
                        fontSize = Type.micro,
                        fontWeight = if (isToday) Type.weightTitle else Type.weightBody,
                        color = if (isToday) colors.primary else colors.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    if (dom > 0) {
                        Text(
                            text = "$dom",
                            fontSize = Type.summary,
                            fontWeight = if (isToday) Type.weightTitle else Type.weightBody,
                            color = if (isToday) colors.primary else colors.onBackground,
                            textAlign = TextAlign.Center,
                            style = TextStyle(fontFeatureSettings = Type.tabular),
                            maxLines = 1,
                        )
                    }
                }
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                            .width(20.dp)
                            .height(2.dp)
                            .background(colors.primary),
                    )
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // ---- 左側時間軸 ---------------------------------------------------
        // 一条等距的尺子：节次号 1..N 永远钉在同一相对位置，时刻只在真有开始时间的
        // 那几行出现（学校时间表里第 2/4/11 节只有结束时间，缺的就空着，不编造）。
        //
        // 2026-10-26 踩过：旧的「节次号居中 + 时刻绝对定位贴顶」两段各自定位，
        //   34dp 行里 10sp 行盒 + 11sp 行盒 = 29.4dp 只余 4.6dp，帧上已经交叠 2px。
        // 2026-10-29 踩过：改成「时刻槽 + 节次号」一列居中后，有时刻的行节次号被顶到
        //   下半、没时刻的行孤零零悬在中间 ⇒ 11 行数字看着不在一条线上（业主：「时间不均匀」）。
        // 2026-10-30 踩过：把时刻叠回节次号上面但**没钉行高**，font_scale 1.15 下两行
        //   行盒加起来超过行高，Compose 不裁只叠，「11:30」直接压住「5」。
        // 定案：两行竖排、两行都写 lineHeight = Type.lineTight（各 13.8dp，共 27.6dp），
        //   空行也占同样高的一格 ⇒ 任何行高下都不叠，且节次号始终等距。
        Column(modifier = Modifier.width(AxisWidth)) {
            (1..maxPeriod).forEach { n ->
                Column(
                    modifier = Modifier.height(rowH).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier.height(axisLine),
                        contentAlignment = Alignment.Center,
                    ) {
                        PeriodClock.startOf(times, n)?.let { t ->
                            Text(
                                text = t,
                                fontSize = Type.micro,
                                lineHeight = Type.lineTight,
                                color = if (isDark) Tone.quietDark else Tone.quiet,
                                style = TextStyle(fontFeatureSettings = Type.tabular),
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = "$n",
                        fontSize = Type.micro,
                        lineHeight = Type.lineTight,
                        fontWeight = Type.weightTitle,
                        color = if (isDark) Tone.inkDark else Tone.ink,
                        style = TextStyle(fontFeatureSettings = Type.tabular),
                        maxLines = 1,
                    )
                }
            }
        }

        // ---- 每一天一欄 ---------------------------------------------------
        columns.forEach { col ->
            // B4-04 v4 §5：今天那一栏**不铺底色、不画竖线**。原来那层 6% 淡底对卡只有 1.09:1，
            // 等于什么也没标；竖线又会和课块左缘那条色脊并排成两条。今天的位置由表头那 2dp 短线负责。
            // B4-04 v4 §1：这一栏**不挂横向 padding**。原来 2dp×2 会和课块自己的 1dp
            // 叠成 6dp 的缝，实测把块宽压到 42.49dp（稿是 47.49dp，少 5dp）。
            // 列与列之间的缝由课块那层 1dp 外边距负责，与稿的 margin:1px 一一对应。
            Column(modifier = Modifier.weight(1f)) {
                if (col.isEmpty) {
                    // 空格子也要佔滿这一栏的高度，否则下一栏的格子会跟它对不齐
                    Spacer(Modifier.height(rowH * maxPeriod))
                } else {
                    com.muyao.wanzaihyperos.data.schedule.WeekLayout.lane(col, maxPeriod)
                        .forEach { cell ->
                            when (cell) {
                                is com.muyao.wanzaihyperos.data.schedule.WeekLayout.LaneCell.Free ->
                                    Spacer(Modifier.height(rowH * cell.rows))

                                is com.muyao.wanzaihyperos.data.schedule.WeekLayout.LaneCell.Placed ->
                                    CourseChip(
                                        slot = cell.slot,
                                        rows = cell.rows,
                                        rowH = rowH,
                                        misaligned = cell.misaligned,
                                        conflicts = col.conflictsOf(cell.slot),
                                        onPick = onPick,
                                    )
                            }
                        }
                }
            }
        }
    }

    // 这一行是**取数回执**：这一周真取到几门课、来自哪一份，一眼能核对，不用去数格子。
    // 2026-10-28 业主：「底下的提示词也太明显了，淡化它的存在」。
    // 原来它是 primary 12% 淡底 + primary 11sp 字，读起来像一句操作提示、跟顶栏强调色抢。
    // 现在没有底、字降一档（micro）、颜色走次墨：留下「取到几门课、哪一份」，不再教人点格子
    //（点格子会就地放大，那句话已经过时了）。
    Box(modifier = Modifier.padding(top = 6.dp, start = 1.dp)) {
        Text(
            text = "第 $week 周 · 本周 $courseCount 门课 · " + source.label,
            style = TextStyle(fontFeatureSettings = Type.tabular),
            fontSize = Type.micro,
            color = if (isDark) Tone.quietDark else Tone.quiet,
        )
    }
    }
}

/**
 * 让一个子项比父级给的宽度更宽地画出来，但只上报原来的宽度。
 *
 * 用途只有一个：课表板。它所在的 LazyColumn 左右各有 Dims.screenH = 16dp 的
 * contentPadding（这一页别的 item 都要它），而板子**越宽一行能放的课名越全**。
 * 这层 modifier 把子项按「左右各多 bleed」测量、按原宽上报、再往外挪 bleed 画，
 * 于是父级布局不变，板子却一直吃到屏幕边缘。多出来的 16dp 落在 LazyColumn
 * 自己的边界以内（它只裁自己的视口），不会被切掉。
 */
internal fun Modifier.fullBleedHorizontal(startBleed: Dp, endBleed: Dp): Modifier =
    this.layout { measurable, constraints ->
        val a = startBleed.roundToPx()
        val b = endBleed.roundToPx()
        val widened = constraints.copy(
            minWidth = constraints.maxWidth + a + b,
            maxWidth = constraints.maxWidth + a + b,
        )
        val placeable = measurable.measure(widened)
        layout(placeable.width - a - b, placeable.height) {
            placeable.place(-a, 0)
        }
    }

/**
 * 「这份课表是几点存的」+ 一个重取的出口。
 *
 * 只在**教务那份是拿本地快照先画出来的**（stale）时才显眼；
 * 刚取过的也留一行，因为「为什么这次没转圈」他也需要一句解释。
 */
@Composable
private fun FreshnessLine(
    jwState: ListState<com.muyao.wanzaihyperos.data.jw.JwTimetableResult>,
    now: Long,
    onReload: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // ★ 第 4 轮必修 3（N5）：只 cast Content ⇒ 教务落进 Empty（真给了空表）时这一行退成
    //   「更新时间未知」，可 `ListState.Empty` 自己带着**真的** `updatedAtMillis`（ListState.kt:154-163）。
    val meta = (jwState as? ListState.Content)?.meta ?: (jwState as? ListState.Empty)?.meta
    val stale = meta?.stale == true
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // ★ 独立审计（v3 #4 第 2 轮第 5 条）：「正在重新取」以前挂在 `stale` 上。
            //   `stale` 是"这份是上次存下来的"的意思（播种出来的快照天生就是 stale），
            //   而真在跑的只有 `meta.refreshing` —— 于是这一行长期对他说"正在取"，
            //   而那一刻**没有任何请求在跑**。界面不许说数据证明不了的话。
            text = com.muyao.wanzaihyperos.data.agoLabel(meta?.updatedAtMillis, now) +
                when {
                    meta?.refreshing == true -> " · 正在重新取"
                    stale -> " · 这是上次存的"
                    else -> ""
                },
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "重取",
            fontSize = Type.caption,
            color = colors.primary,
            modifier = Modifier
                .clickable(onClickLabel = "重新从教务系统取课表") { onReload() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * 轴宽。
 *
 * 2026-10-30 定案 36dp：轴上是**两行竖排**（时刻在上、节次号在下）。
 * 实测 font_scale 1.15 下时刻「08:00」的墨迹宽 112px = 32.0dp —— 32dp 那一版
 * 最后一个字符被裁成「08:0」（帧上抓到的），所以两侧各留 2dp ⇒ 36dp。
 * 横排那版要 46dp（时刻与节次号并排），竖排只要 36dp。
 */
private val AxisWidth = 36.dp

/** 表頭高度（B4-04 v4 §2：34×9 + 28 = 334dp，這一格必須真的是 28dp 才算得对）。 */
private val HeaderHeight = 40.dp

/** 「今天 / 整周」那颗药丸的高度：两颗一样高，也不许被触摸目标最小尺寸撑大。 */
private val ModePillHeight = 26.dp

/**
 * 药丸的**命中带**高度（视觉只有 [ModePillHeight]）。
 *
 * `DEEPSEEK-IMPLEMENTATION.md §4.2`：可见胶囊 24–28dp，但**单项可点区域至少 44×44dp**，
 * 「视觉与触区分开」；并且明确禁止「在 24dp 按钮上只改 clickable 就宣称 44dp」——
 * 所以这里真的把外层盒子撑到 44dp，透明命中空间只在竖直方向，
 * 左右两颗各自独立节点、不覆盖左边的日期文字。
 */
private val ModePillTouch = 44.dp

/** 板子底下那行「第 N 周 · 本周 M 门课 · 来源」占的高度（含 6dp 上边距）。 */
private val WeekBoardFooterHeight = 22.dp

/** 一屏塞满时行高的下限：再矮连两行课名都放不下了。 */
private val WeekBoardMinRowHeight = 32.dp

/**
 * 板子底下要让出多高，才能让它"刚好铺满一屏"。
 *
 * 这个数不是拍出来的，是解出来的：滚动余量 = 板下内容 + 列表底 padding − 本预留。
 * 2026-10-30 真机实测（1216x2640 / density 560）：板下内容（"N 分钟前更新 / 重取"那行）
 * + 列表底 padding（pageBottomInsetDp + 38dp = 108dp）合计 406px，而当时预留 76dp(266px)
 * ⇒ 还多出正好 140px，也就是板子明明"铺满"了、手指一滑还能再滚一小段（业主口径里的
 * "刚好铺满不用翻页"没达成）。406px = 116dp，往下取整到 120dp 保证一定不剩。
 */
private val BoardBottomReserve = 120.dp

/**
 * 一格課（時間網格裡的一塊）。
 *
 * ## 為什麼塊上不再寫「第 N-M 節 / 幾點」
 *
 * 业主 2026-10-20：「**你别标时间啊，跟其他课表软件一样侧边写个时间课程对齐时间不行吗**」。
 * 節次和時間現在由**左側那條軸**負責（[WeekBoard]），塊的位置本身就是時間。
 * 塊上再寫一遍只是把課名該佔的地方吃掉。
 * （詳情卡沒有軸，所以那邊照舊寫 [clockLine]。）
 *
 * ## 內容随占幾行而變
 *
 * 一節課只有 34dp，硬塞「課名+老師+教室」會截成看不懂的半截字 ——
 * 所以單節的塊只給課名，兩節以上才加老師/教室。
 * **宁可少给一行字，也不给一行被裁掉的字**（那种"看着有其实没了"的坑今天已经踩过一次）。
 *
 * ## 配色（照参照站那套，不照它代碼）
 *
 * 底色 = 一塊實色 `Chip.face`；左緣一條 3dp 色脊，顏色由 `Chip.spineOf(courseId)` 從
 * **十枚手選色**裡按課程序號輪轉（同一門課恆同色，且不依賴接口回傳顏色）。
 *
 * 舊寫法是 `hsl(courseId × 137.5°)` 全色相輪轉 —— 同一排課的色脊明度差到 2.3 倍
 * （青綠 174° 對塊底 4.09，紫 264° 9.57），看着就是「有的重有的輕」。
 * 手選十枚把明度壓進同一段：淺檔最壞 5.13、深檔 5.62。
 * 文字 = 主題 `onBackground`（**不寫死白色**，否則淺色主題下看不清）。
 */
@Composable
private fun CourseChip(
    slot: TronScheduleSlot,
    /** 這一塊佔幾行（= 幾節）。 */
    rows: Int,
    rowH: Dp,
    /** true = 它被前面撞課的塊擠下了自己的行 ⇒ 必須標出來，否則他會以為這是連堂。 */
    misaligned: Boolean,
    conflicts: List<TronScheduleSlot>,
    onPick: (TronScheduleSlot, Rect) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    // B4-04 v4 §3：颜色只剩左缘那条 3dp 色脊，块本身是一块实色浅底。
    // 旧写法是「同色相低饱和高明度 + 55% alpha」，对纸面最坏 1.09:1 —— 块和底几乎同色。
    val spine = Chip.spineOf(slot.courseId, isDark)
    val face = if (isDark) Chip.faceDark else Chip.face
    val edge = if (isDark) Chip.edgeDark else Chip.edge
    val ink2 = if (isDark) Chip.ink2Dark else Chip.ink2
    val ink3 = if (isDark) Chip.ink3Dark else Chip.ink3
    val badgeFace = if (isDark) Chip.badgeFaceDark else Chip.badgeFace
    val badgeInk = if (isDark) Chip.badgeInkDark else Chip.badgeInk
    val shape = RoundedCornerShape(Chip.corner)
    val tight = rows <= 1
    // 行高 48dp：块内可用高度 = 48*rows - 2(外距) - 4(内距) ⇒ 单节 42dp、两节 90dp。
    // 一行 = Type.caption(11sp) + Type.lineTight(12sp) = 13.8dp（本机 font_scale 1.15）。
    //   单节 2 行课名 + 教室 = 41.4dp 塞满 42dp；两节 3 行课名 + 教室 = 55.2dp 仍宽松。
    // 业主 2026-10-28 拍板：教室每一块都给，老师那一行删掉（详情卡里仍有全称）。
    val showDetail = true
    // 徽标上的 N = 「同格还有几门」。misaligned 只会发生在被撞课挤下来时，那时 conflicts 必然非空；
    // 这里仍取一次 max，免得哪天 lane() 的语义变了徽标静悄悄变成 +0。
    val extra = maxOf(conflicts.size, if (misaligned) 1 else 0)
    // 这一格在屏幕上的真实矩形 —— 点开时放大的起点。随撞课/翻周/字号自己变，不写死。
    var cellRect by remember { mutableStateOf<Rect?>(null) }

    // 外層**恰好佔 rows 行**：塊與塊之間的縫走內層 padding，
    // 這樣每一欄加起來的高度一樣，格子才對得齊。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowH * rows)
            // 1dp，与稿的 .chip{margin:1px} 对齐：两块之间 2dp 缝，块宽 47.49dp。
            .padding(1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(face)
                .drawBehind {
                    // 描邊自己畫：`Modifier.border` 會算進內容尺寸，1dp 的邊會把三行的塊擠掉一行。
                    drawRect(
                        color = edge,
                        style = Stroke(width = 1.dp.toPx()),
                        topLeft = Offset(0.5f.dp.toPx(), 0.5f.dp.toPx()),
                        size = Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                    )
                    // 左緣色脊：絕對定位畫出來，**不佔排版寬度**（所以文字那 35.49dp 已經把它扣掉了）。
                    drawRect(
                        color = spine,
                        topLeft = Offset.Zero,
                        size = Size(Chip.SPINE_DP.dp.toPx(), size.height),
                    )
                }
                .onGloballyPositioned { c ->
                    cellRect = Rect(
                        offset = c.positionInRoot(),
                        size = Size(c.size.width.toFloat(), c.size.height.toFloat()),
                    )
                }
                .clickable { onPick(slot, cellRect ?: Rect.Zero) },
        ) {
            // 2026-10-26 真机三轮取证（这台设备的 font_scale = 1.15，每字比设计稿再宽 15%）：
            //   列宽 45.49dp，可用文字宽 = 45.49 − 左右内边距。
            //   第一轮 4+3dp 剩 38.49dp、13sp ⇒ 2.73 字/行，这门课显示成「B/S / 架…」。
            //   第二轮 3+2dp + 12sp 剩 40.49dp ⇒ 2.93 字/行，仍是「B/S架 / 构应用…」。
            //   第三轮 3+1dp + 11sp（Type.caption）剩 41.49dp ⇒ 3.41 字/行，能显示 7 个字。
            //   2026-10-30 真机量：色脊占 0..10px（3dp×3.5），文字起笔在 59px ⇒ 只隔 5px（1.4dp），
            //   看着像字贴着色条长出来。start 3dp → 5dp，缝变成 ~2.4dp；代价是每行少 0.2 字，换「不贴边」值得。
            Column(Modifier.padding(start = 5.dp, end = 0.dp, top = 2.dp, bottom = 2.dp)) {
                Text(
                    text = slot.courseName.ifBlank { "（未命名）" },
                    // ★ 清单 #11（业主「字太小」）：这一栏是他看课表格子必读的课名。以前 tight 档
                    //   压到 8.5sp，低于 Type.micro(10sp) 这条地板，也不读令牌。
                    //   2026-10-26 真机三轮之后两档统一走 Type.caption(11sp)：13sp / 12sp 在这台
                    //   font_scale 1.15 的设备上把课名截成「B/S架 / 构应用…」，认不出是哪门课；
                    //   11sp 一行 3.41 字，同一门课能显示到 7 个字。字更小但认得出 —— 这是
                    //   「看得清是哪门课」优先于「字号好看」的取舍，不是随手缩字。
                    fontSize = Type.caption,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                    // 行距收到 12sp（字号 11sp）：默认 1.448 倍行距一行实占 18.3dp，
                    //   两节的块只剩 62dp，3 行课名 + 1 行教室要 73dp ⇒ 只能给 2 行课名。
                    //   收到 12sp 后一行 13.8dp，3 行课名 + 教室 = 55.2dp，加内距 4dp 共 59.2dp ≤ 62dp ⇒ 两节的块也能给 3 行课名（实测 13sp 时 63.8dp 溢出 1.8dp，教室行被切掉下半）。
                    //   这不是「为了塞字把行距压到看不清」：11sp 字 + 13.8dp 行距仍是 1.25 倍，
                    //   CJK 宋黑系的字面高度约 0.88em，不会上下相碰。
                    lineHeight = Type.lineTight,
                    // 行数按块高给，不是越多越好：这台设备 font_scale = 1.15，CJK 字体一行实占
                    //   11sp×1.15×1.448 ≈ 18.3dp。两节的块 68dp 减去外距 1×2 与内距 2×2 只剩 62dp，
                    //   3 行课名 + 1 行教室 = 4×18.3 = 73dp ⇒ 教室那行会被切在底边上（2026-10-26
                    //   真机实测：两节的块里「教六…」正好被截在底边）。所以两节仍只给 2 行课名；
                    //   三节的块 102dp 剩 96dp，够 3 行课名 + 教室 + 老师，那一档才给 3 行。
                    // 截断有 TextOverflow.Ellipsis 兜着，点开还有详情卡的完整课名。
                    maxLines = if (rows >= 3) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    // ★ 有 +N 徽标时**课名一个字都不让**：实测把课名 end 内边距设成 14dp 后，
                    //   这一格的课名只剩「数 …」（35.49 − 14 = 21.49dp ÷ 13sp ≈ 1.6 字）。
                    //   而「课名是什么」永远比「同格还有几门」要紧 ⇒ 让位的是下面那两行细节。
                    modifier = Modifier,
                )
                // ★ 撞课那一格（有 +N 徽标）**不写教室/老师**：徽标就落在右下角那一行，
                //   两样东西抢同一块 78×34dp 的地方谁都看不清。地点/老师在点开的详情卡里有全称，
                //   一条都没丢（SlotDetailCard 有 时间/周次/状态/教师/地点/来源/原文）。
                if (showDetail && extra == 0) {
                    // ★ 真机截图证实：把「老师·教室」拼成一行会被裁成
                    //   「陈明·文」（文1-103 少了后半）、「周勇·」（教室整个没了）——
                    //   而这一栏最要紧的信息恰恰是**去哪间教室**。
                    //   所以拆成两行、各自一行一截，并**优先给教室**。
                    //   业主 2026-10-28：「要显示教室和课程，可以不显示老师」⇒ 教室每一块都给，老师整行删掉。
                    if (slot.room.isNotBlank()) {
                        Text(
                            text = slot.room,
                            fontSize = Type.caption,   // 清单 #11：原 8.5sp，低于地板，改走令牌
                            lineHeight = Type.lineTight,        // 与课名同一条紧行距，见上面的注释
                            fontWeight = Type.weightBody,
                            color = ink2,   // v4：实色 #5F5A50 对块底 5.76:1，不用 80% 透明造灰
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,   // 截了要看得出来，不能静悄悄少字
                        )
                    }
                }
                // 学校标的「待筛选」（红字斜体那种）。不标出来，他会把一门还没确认的课当成今天要去的课。
                if (slot.statusLabel.isNotBlank()) {
                    Text(
                        text = slot.statusLabel,
                        fontSize = Type.micro,   // 清单 #11：原 8sp，低于地板
                        fontWeight = Type.weightTitle,
                        color = colors.error,
                        maxLines = 1,
                    )
                }
                // 撞课不再在块里堆字（旧写法「同时 +N」在 ~78dp 的窄栏里被截成「同时 +」）：
                // v4 §6 改成右上角一枚 +N 徽标，画在下面 Column 之外，不占正文行高。
            }
            // 右上角「+N」：同格还有几门。底色是块底上再压一层极浅赭，字色 #A9522F 对它 4.53:1。
            // 绝对定位，不动正文布局；课名那一侧靠 end 内边距让出 13dp（见上一段的 maxLines 注释）。
            if (extra > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 2.dp, end = 2.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(badgeFace)
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "+$extra",
                        fontSize = Type.micro,   // 稿里的 8px 低于 10sp 地板，这里必须走令牌
                        color = badgeInk,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 點格子後的詳情。
 *
 * ⚠️ **刻意不做底部彈出卡**：Miuix 的 `OverlayBottomSheet` 要 `Scaffold` 祖先才画得出來
 *（`miuix-skill/references/SKILL.md`：overlay 渲染在 Scaffold 的弹层里），
 * 而這個工程的主壳是自定义 Box + AnimatedContent，沒有 Miuix Scaffold。
 * 要用真·底部卡得走 `WindowBottomSheet` 或补一个 Scaffold 层，那是单独一张卡。
 * 现在把信息放在格子上方，**一条都不少**（课名/星期/节次/周次/原文）。
 */
@Composable
private fun SlotDetailCard(slot: TronScheduleSlot?, onClose: () -> Unit) {
    if (slot == null) return
    val colors = MiuixTheme.colorScheme
    AppCard {
        Column(Modifier.padding(Dims.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.courseName.ifBlank { "（未命名课程）" },
                    fontSize = Type.body,
                    fontWeight = Type.weightBody,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "收起",
                    fontSize = Type.caption,
                    color = colors.primary,
                    modifier = Modifier.clickable { onClose() },
                )
            }
            Spacer(Modifier.height(6.dp))
            DetailRow(
                "时间",
                listOfNotNull(
                    "${slot.dayLabel} ${slot.periodLabel}",
                    clockLine(slot).takeIf { it.isNotBlank() },
                ).joinToString(" "),
            )
            DetailRow("周次", slot.weekLabel)
            // 學校自己标的选课状态（红字斜体=待筛选）。没标就不占一行。
            if (slot.statusLabel.isNotBlank()) DetailRow("状态", slot.statusLabel)
            // 老師／教室／來源：**來源給了才顯示**。
            // 空時整行不佔位 —— 顯示一排「—」會被讀成「學校說沒有老師」，
            // 而實際是這一份來源根本沒這個欄位（學在城院那份就沒有）。
            if (slot.teacher.isNotBlank()) DetailRow("教师", slot.teacher)
            if (slot.room.isNotBlank()) DetailRow("地点", slot.room)
            DetailRow("来源", slot.source.label)
            if (slot.raw.isNotBlank()) DetailRow("原文", slot.raw)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = value.ifBlank { "—" },
            fontSize = Type.summary,
            color = colors.onBackground,
            maxLines = 3,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 日程 / 整周：板子是「左边今天几号几门课，右边两颗小胶囊」。
 *
 * 不用 Miuix `TabRow`：它帶水平嵌套滚动，會和這頁左右滑切板塊打架。
 */
@Composable
private fun DayModeSwitch(
    label: String,
    /** 第二档：日期 + 节数，板上是 10.5px/--sub。 */
    meta: String,
    firstLabel: String,
    weekMode: Boolean,
    onFirst: () -> Unit,
    onWeek: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 板 `.dayhead .l{display:flex;align-items:baseline;gap:8px}`：
        // 周几与「日期 · N 门课」在**同一行**（基线对齐），不是上下两行 ——
        // 拆成两行会把标题下面撑出 37 CSS 的间隙（板 29），整屏第一块就往下坠。
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = label,
                // 板 `.dayhead h3{font-size:17px;font-weight:740;letter-spacing:-.02em}` ⇒ 15sp
                fontSize = Type.body,
                fontWeight = Type.weightTitle,
                letterSpacing = (-0.02).em,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    // 板 `.dayhead .n{font-size:10.5px;color:var(--sub)}` ⇒ 9.3sp
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // 板 `.seg{display:flex;padding:3px;border:1px solid var(--line);
        // border-radius:999px;gap:1px}` ⇒ 外面这一圈是**容器**，里面两颗才轮流填墨底。
        //
        // 2026-11-07：把「外圈描边」从布局里拿出来，改成 `drawBehind` 画一条 30dp 高的胶囊。
        // 因为 §4.2 要求每颗药丸的**可点区域 ≥44×44dp**，而可见胶囊只有 26dp ——
        // 若把 44dp 直接做成布局高度，这圈描边会被撑到 48dp，整行比稿里那枚小胶囊胖一倍。
        // 现在：布局高 44dp（触区），描边只在正中画 30dp（视觉），两者互不牵制。
        Row(
            modifier = Modifier
                .height(ModePillTouch)
                .drawBehind {
                    val h = (ModePillHeight + 4.dp).toPx()
                    val top = (size.height - h) / 2f
                    drawRoundRect(
                        color = colors.dividerLine,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, h),
                        cornerRadius = CornerRadius(h / 2f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.9.dp),
        ) {
            ModePill(firstLabel, selected = !weekMode, onClick = onFirst)
            ModePill("整周", selected = weekMode, onClick = onWeek)
        }
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    // 2026-11-07 按 `screens/timetable.png` 复刻，三处对齐稿子：
    //   ① 选中底从 `onBackground`（黑）改成 **`primary` 主色实底**、字用 `onPrimary` 白；
    //   ② 未选中底是**白纸色 `surface`**（稿上「整周」那半是 (255,253,248) 的白，不是透明）；
    //   ③ **两态同字重**（§4.2 明文）。旧版选中 weightTitle、未选 weightBody，
    //      切一下字宽就变，整排左右横抖 —— `Type` 的四条硬规则第 1 条也禁止这么写。
    // 视觉高 26dp（稿上 29px × 0.886 ≈ 26dp，落在 §4.2 的 24–28dp 区间），
    // 但**触区上下各撑到 44dp**（§4.2「视觉与触区分开」）：
    // 外层 Box 44dp 高、内层才 26dp 上色 —— 透明命中空间只在竖直方向，
    // 左右两颗各自独立节点、不与日期格重叠。
    Box(
        modifier = Modifier
            .height(ModePillTouch)
            .clickable(onClickLabel = "切换到$label") { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(ModePillHeight)
                .clip(RoundedCornerShape(50))
                .background(if (selected) colors.primary else colors.surface)
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = Type.caption,
                // §4.2：**两态相同字重**。切一下字宽就变，整排会左右横抖。
                fontWeight = Type.weightBody,
                color = if (selected) colors.onPrimary else colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WeekNavButton(
    label: String,
    desc: String,
    /** true = 这一枚是「上一周」，箭头要水平翻面。 */
    backwards: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // 2026-11-07 按 `screens/timetable.png` 复刻：稿上这两枚箭头**没有描边圆**，
    // 就是两颗宽 7~8px、颜色 (81,86,98) 的裸箭头，与「回到今天」同一条水平线居中。
    // 旧版是 23dp 圆环 + 11dp 图标（板 `.week-nav .chev span{26px;border:1px solid}`），
    // 三个元素一起看像一组按钮，比稿重一整档。
    // 视觉是裸箭头，**触区仍有 44dp**（§4.2「视觉与触区分开」）：外层 Box 撑 44dp 高、
    // 左右各 6dp，内层才画 12dp 的箭头。命中空间不与「回到今天」重叠 ——
    // 「回到今天」自己是独立的 clickable 节点、且左右各留 10dp 内衬。
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(28.dp)
            .clickable(onClickLabel = desc) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        // 板上只有一颗 `#i-chev`（朝右）。往左那枚**同一字形水平翻面**，
        // 不另画第二个图标 —— 左右长得不一样是常见的错。
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_board_chev),
            contentDescription = null,
            tint = colors.onSurfaceVariantSummary,
            modifier = Modifier
                .size(12.dp)
                .scale(scaleX = if (backwards) -1f else 1f, scaleY = 1f)
                .semantics { contentDescription = desc },
        )
    }
}

/** 投影时长（秒）：热调键 `wz_fling_ms` 优先，缺省回落到老常量（B4-15②）。 */
internal fun flingSeconds(): Float =
    (com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.flingProjectionMs / 1000f)
        .coerceIn(0.02f, 0.40f)

/**
 * 抬手速度的取窗（毫秒）：**最近这段窗内首尾差 ÷ dt**，样本不足 3 个 ⇒ 0。
 *
 * ## ★ 为什么不用 `VelocityTracker`（第 3 轮真机审计 Block 头条）
 *
 * Compose 的 Lsq2 估计器有效窗只有 **40ms / 最少 3 个样本**，而且它要求调用方**不要把 UP 喂进去**
 * （UP 的位置 = 最后一次 move 的位置，是个**零位移的重复点**；官方实现里 UP 走
 * `changedToUp` 分支、不 add 数据点，UP 距最后 move 超 40ms 还会整窗作废）。
 * 我上一轮为了"抢到之前的 move 也要进窗"改成自己 `addPosition` 每事件都喂 ⇒ 把这条保护拆了，
 * 真机上的后果是**符号反了**：手指抬手真速度 −1350 px/s（向左），估计器给出 +975 px/s（向右），
 * `projected = −149 + 975×0.11 = −41.8`（阈值 110.4 的 38%）⇒ **一次快甩不翻天**，
 * 而且条子抬手后"被吸回去"而不是"被甩出去"。
 *
 * 换成"最近 80ms 首尾差"之后：符号永远与手指一致（首尾两点都是手指自己的位置）、
 * 稀疏事件下**明确返回 0**（按纯位置判定），而不是被一个病态拟合硬凑出一个数。
 * 60~100ms 这个窗宽是第 3 轮审计给的口径（"放宽到 60–100ms"），取中值 80ms。
 */
internal fun releaseWindow(
    samples: List<Pair<Long, Float>>,
    nowMs: Long,
    windowMs: Long = com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.stripWindowMs.toLong().coerceIn(20L, 400L),
): List<Pair<Long, Float>> =
    samples.filter {
        // B4-15②：窗宽走热调键 `wz_strip_win_ms`（默认 80ms；测试可传显式值）
        nowMs - it.first <= windowMs
    }

internal fun releaseVelocityOf(
    samples: List<Pair<Long, Float>>,
    nowMs: Long,
    windowMs: Long = com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.stripWindowMs.toLong().coerceIn(20L, 400L),
): Float {
    val win = releaseWindow(samples, nowMs, windowMs)
    if (win.size < 3) return 0f
    val (t0, x0) = win.first()
    val (t1, x1) = win.last()
    val dtMs = t1 - t0
    if (dtMs <= 0L) return 0f
    return (x1 - x0) / (dtMs / 1000f)
}

/**
 * 落地格数：`projected` 是「位移 + 速度 × [flingSeconds]」，除以**整格宽**再四舍五入
 * ⇒ 门槛正好是半格（`cellPx / 2`）。
 *
 * ★ 第 5 轮真机审计要的：**判据必须抽成生产函数**。以前它是内联在组合里的，
 *   而 `ReleaseVelocityTest` 在测试里**自己又写了一遍**（本地 `step()` + 本地 `cellPx`）——
 *   于是"生产除数从 220.8 改成 110.4"这种回归测试照样绿（一行生产代码都没守到）。
 */
internal fun weekFlipStep(projected: Float, cellPx: Float): Int =
    if (cellPx <= 0f) 0 else (-projected / cellPx).roundToInt().coerceIn(-1, 1)

/**
 * 課表頂部的日期條（v2）：星期 + 月日数字 + 有课小圆点，**点一天就切到那天**。
 *
 * 圆点只按**本周 slots** 统计：有 ≥1 节才画，所以「没点」就是「这天本周没课」，
 * 不是「资料还没来」的猜测。
 */
@Composable
private fun WeekDateStrip(
    monday: LocalDate,
    selected: Int,
    today: Int,
    dotsFor: (Int) -> Int,
    onSelect: (Int) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // ── v3 清单 #5：这一排格子可以横扫切天 ────────────────────────────
    //
    // 判据全部以「一格」为单位，不写死 dp —— 字号缩放、换屏幕宽度都还成立。
    // 橡皮筋复用 `liquid/GlassControls.kt` 里那一份 `rubberBand`（动效标准第 5 步：
    // 二者择一，不许写第三种）。
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    // ★ 独立审计（v3 #5 第 1 轮）：以前用默认 `enabled = true`，而 `Motion.kt:207`
    //   那个 `rememberHapticsEnabled` **全工程 0 个调用点** ⇒ 触感开关在所有触发点等于没接。
    val haptics = com.muyao.wanzaihyperos.ui.design.rememberHaptics(
        enabled = com.muyao.wanzaihyperos.ui.design.rememberHapticsEnabled(
            androidx.compose.ui.platform.LocalContext.current,
        ),
    )
    val dragX = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var cellPx by remember { mutableFloatStateOf(0f) }
    // ★ 2026-11-07：周条改成**七天**（周一至周日），与设计稿 v2.1 的七天条一致
    //   （`DEEPSEEK-IMPLEMENTATION.md §4.1`：「七天条（周六周日保留）」）。
    //   于是「定义域洞」正式关闭：`selected` 本来就是 1..7，这里不再夹。
    val sel = selected.coerceIn(1, 7)

    // ★ 离开这一页必须把登记撤掉 —— 不然那块矩形一直挂在排除表里，
    //   其它页的"横滑切板块"就永久失灵（这种"修一处坏一处"本工程栽过）。
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.muyao.wanzaihyperos.ui.main.HorizontalDragZones.set("week-strip", null)
        }
    }

    Row(
        // 板 `.weekstrip{display:flex;gap:5px;padding:6px 14px 10px;align-items:stretch}` ⇒ gap 4.4dp
        // 板 `.weekstrip{padding:6px 14px}` ⇒ 在 16dp 列表内边距里**不再另缩 4dp**
        //（缩了以后周条比正文列还窄，选中绿块和下面行的左右边全不齐，复评 2026-10-21）。
        // ★ 不能用负 padding 去「撑回」板上那 4px：`Modifier.padding` 明确拒绝负值，
        // 真机上直接 `IllegalArgumentException: Padding must be non-negative` 崩在切课表那一下
        //（2026-10-21 23:00 logcat 抓到现场）。宁可留这 4px 的差，也不拿崩溃换像素。
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 2.dp)
            // 七格等分全宽：稿上七格中心 x = 43/94/145/195/246/297/347（392px 板）⇒ 间距 50.6px。
            .onSizeChanged { cellPx = it.width / 7f }
            // ★ v3 #5：把自己登记成"这块区域的横向手势归我"。
            //   切板块那条手势跑在 `PointerEventPass.Initial`（父层先看到），
            //   不登记的话真机上横扫周条会被它抢走 —— 实测三下横扫人就到了设置页。
            .onGloballyPositioned {
                com.muyao.wanzaihyperos.ui.main.HorizontalDragZones.set("week-strip", it.boundsInRoot())
            }
            // 只动合成层的 translationX，不动 padding/width —— 拖一下不许重测布局。
            .graphicsLayer { translationX = dragX.value }
            .pointerInput(sel, cellPx) {
                if (cellPx <= 0f) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 抬手速度的样本（毫秒时间戳 + **累计位移**）—— 见下面那段注释与 [releaseVelocityOf]。
                    //
                    // ★★ 第 5 轮真机审计的**真根因**（靠 `wz-motion` 那行日志定位，不是猜的）：
                    //   样本里原来记的是 `change.position.x` = 手指在**这一排的局部坐标系**里的 x，
                    //   而这一排自己挂着 `graphicsLayer { translationX = dragX.value }`
                    //   ⇒ **坐标系跟着条子一起动**：条子往左移 100px，手指在局部系里反而"往回走 +100"。
                    //   真机日志（几何：手指 −100px/56ms，单调向左）：
                    //     samples = 804 | 779 | 754 | 779 | 779 | 804   ← 出去又"回来"了
                    //     ⇒ v = **+641 px/s**（向左的甩，算出向右的速度）⇒ `projected = −29.5` ⇒ 不翻天
                    //   这也解释了第 3 轮那次"符号反转"：同一个机理。
                    //   `change.previousPosition` 与 `change.position` 在同一帧同一坐标系里，
                    //   所以它们的**差**是干净的手指增量（`drag` 一直是对的、−100 ✓）。
                    //   ⇒ 样本池记 `drag`（累计位移），坐标系怎么动都不影响它。
                    val samples = ArrayDeque<Pair<Long, Float>>()
                    samples.addLast(down.uptimeMillis to 0f)
                    // ★★ 第 2 轮真机审计（#5 Block）：接管**必须从表现值接着动**。
                    //   以前这里写 `var total = 0f` ⇒ 每个新手势都从 0 重开，
                    //   真机 A/B（同一套抓取手势，分别在上一段释放后 30ms / 300ms 抓下去，
                    //   抓取瞬间表现值是 −65 与 −1）**两次结果完全一样（都到 −85）** ⇒
                    //   抓取瞬间条子在哪儿完全不影响抓完去哪。那正是动效标准内核第 2 条
                    //   「被打断时从表现值接着动，不许从头再来」被直接踩掉。
                    //   `base` 取抓取那一刻的 `dragX`（= 屏幕上的值），`drag` 只记**本次手势**的位移。
                    val base = dragX.value
                    var drag = 0f
                    var dyTotal = 0f
                    var claimed = false
                    var releaseVelocity = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dxIn = change.position.x - change.previousPosition.x
                            // ★★ 第 3 轮真机审计 **Block 头条**：抬手速度的取法整个换掉。
                            //
                            // 事故形态（真机 `SYNTHETIC0000001`，第 3 轮）：一次**快甩**之后天不翻，
                            // 而且条子抬手后是"被吸回去"而不是"被甩出去"。逐帧拟合给出：
                            //   手指抬手真速度 **−1350 px/s（向左）**，弹簧初速反解 **+975 px/s（向右）**
                            //   ⇒ `projected = drag + v·0.11 = −149 + 107 = −41.8`（阈值 110.4 的 38%）⇒ 不翻天。
                            //
                            // 机理：Compose 的 Lsq2 估计器有效窗只有 **40ms / 最少 3 个样本**，
                            // 而 UP 的位置 = 最后一次 move 的位置（**零位移的重复点**）——
                            // 把 UP 也喂进去，最小二乘的斜率会被这个重复点拽偏甚至**翻号**。
                            // （官方实现里 UP 是不喂的，我上一轮为了"抢到之前的 move 也要进窗"
                            //   改成自己 `addPosition` 每事件都喂，把这条保护拆了。）
                            //
                            // 所以这里不再押那个奇异窗：**按"最近 80ms 首尾差 ÷ dt"自己算**，
                            // 样本不足 3 个或 dt ≤ 0 ⇒ **v = 0（按纯位置判定）**，不用病态拟合硬凑。
                            // 见 [releaseVelocityOf] 与它的单测（含"补一个重复点不许把符号翻过去"那条回归）。
                            // 记**累计位移**（干净的手指轨迹），不是局部坐标 —— 见上面那段
                            samples.addLast(change.uptimeMillis to drag)
                            while (samples.size > 16) samples.removeFirst()
                            // ★ 第 2 轮审计的缺陷 2：`dxIn` 只能记一次。
                            //   以前 `:1441`（未抢到那支）与 `:1493`（抢到之后）**各加一遍**
                            //   ⇒ 抢到的那一帧位移被记两次：慢拖 9 个 move 手指走 −83px、条子走 −93px；
                            //   抓取那一下手指 −43px、条子 −86px（整整 2 倍）⇒ 一帧内 42px 死跳，
                            //   而且"半格"实际是 99px（44.8%）而不是 110.4px（50%）。
                            drag += dxIn
                            dyTotal += change.position.y - change.previousPosition.y

                            if (!claimed) {
                                // ★★ 独立审计（v3 #5 第 1 轮）**Block 头条**：这一排以前**没有轴向锁**
                                //   （`isConsumed` / `position.y` 在全文件命中 0 次），
                                //   而周条是课表页 LazyColumn 的第 1 项 —— 手指压着它**竖着滚列表**时
                                //   x 增量照样在累，`abs(total) >= cellPx*0.5`（110.4px）或
                                //   `|v| >= 50dp/s`（175px/s）在斜向快滚里极易满足
                                //   ⇒ 他只是滚了一下列表，天就被切走、还震了一下。
                                //   这一改的是「他今天有哪几节课」，直接踩「别让人走错教室」那条红线。
                                //   判据抄同工程已经写对的那一份（`SwipeToSwitchSection.kt:87-93`）：
                                //   竖向先赢 ⇒ 交还给列表；横向先赢 ⇒ 这一排接管。
                                //   ⚠️ 抢到的判据一律用 **`drag`（本次手势的位移）**，不用 `base + drag`：
                                //     接手时条子可能正停在 −65，用总位移会让"手指才动 1px"就判成横向大幅拖拽。
                                if (abs(dyTotal) > slop * 1.5f && abs(dyTotal) > abs(drag) * 1.2f) {
                                    break
                                }
                                if (change.isConsumed) {
                                    // 已经被别人（列表惯性/父层）吃掉 ⇒ 不抢。
                                    break
                                }
                                if (abs(drag) > slop * 1.5f && abs(drag) > abs(dyTotal) * 1.2f) {
                                    claimed = true
                                } else if (!change.pressed) {
                                    // 轻点（没到横向阈值就抬起）⇒ 既不切天也不回弹动画，交给 clickable。
                                    break
                                } else {
                                    continue
                                }
                            }

                            if (!change.pressed) {
                                // ★ 抬手分**正常抬手 / 被抢走**两种（必修 2）：
                                //   以前 `if (!change.pressed)` 一律当同一件事，被系统取消的手势
                                //   照样提交切日 + 发触感。这里能走到这一步说明已经 claimed 且
                                //   这个 pointer 自己还活着 ⇒ 是用户真放的手。
                                val v = releaseVelocityOf(samples, change.uptimeMillis)
                                releaseVelocity = v
                                // ★★ 第 5 轮真机审计：注入用的 evdev 内核时间戳**本身不可靠**
                                //   （`v7sf60.bin` 里 DOWN 记在 +0.00ms、第一个 move 记在 +662983ms），
                                //   所以"App 内部那组 `uptimeMillis`"离线复算不出来 —— 根因只能在运行时看。
                                //   这一行把判据要用的量**一次全打出来**（tag `wz-motion`，logcat grep 它）：
                                //   靠它区分两个嫌疑：
                                //     甲 = 样本池里根本没有"释放瞬间那一段"（Compose 把中间 move 批处理掉了
                                //           ⇒ 要把 `change.historical` 里的历史点也喂进来）；
                                //     乙 = 池子里有 DOWN 这个**零速度样本**，落在窗口首元素 ⇒ 算成整段平均。
                                if (com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.debugLog) run {
                                    val win = releaseWindow(samples, change.uptimeMillis)
                                    android.util.Log.i(
                                        "wz-motion",
                                        "weekstrip release drag=$drag base=$base dragX=${dragX.value} " +
                                            "samples=${samples.joinToString("|") { "${it.first}:${it.second}" }} " +
                                            "nowMs=${change.uptimeMillis} winSize=${win.size} " +
                                            "t0=${win.firstOrNull()?.first} t1=${win.lastOrNull()?.first} " +
                                            "dtMs=${(win.lastOrNull()?.first ?: 0L) - (win.firstOrNull()?.first ?: 0L)} " +
                                            "v=$v projected=${drag + v * flingSeconds()} step=" +
                                            weekFlipStep(drag + v * flingSeconds(), cellPx),
                                    )
                                }
                                // ★ 必修 5：**速度真的交接给动画**（以前 `initialVelocity` 全文件 0 次，
                                //   `v` 只当布尔与符号用，落点是"半格 OR 速度符号"的二值判定）。
                                //   落点按**投影**算：`projected = 位移 + v · 0.11f`
                                //   —— 0.11f 是 Android 样条 fling 的等效投影时长（`PathInterpolator`
                                //   + 摩擦衰减积分出来的量纲），**真机 A/B 已证有效，别动**。
                                //   标准里那条 Web 的 `d/(1−d)`（d≈0.998）是 px/s + 毫秒量纲，不许照搬。
                                //
                                // ★ 第 2 轮审计要我拍板的一条：翻天的位移量用**手指自己的那段**（`drag`），
                                //   不用 `base + drag`。理由：翻不翻天是"他这一下想干什么"的问题，
                                //   语义一直是「手指走够半格」；而 `base + drag` 会让"条子正在大幅回弹时
                                //   手指只挪 1px 也翻天"。**视觉位置与判重量的关系写在这里**：
                                //   位置（`base + drag`）负责"条子跟着手"，判重用 `drag` 负责"这一下算不算数"，
                                //   两者各自单调、且不像以前那样共用一个被记两遍的 `total`。
                                val projected = drag + v * flingSeconds()
                                val step = weekFlipStep(projected, cellPx)
                                val next = (sel + step).coerceIn(1, 7)
                                if (next != sel) {
                                    haptics.commit()   // 因果明确的时刻：这一发真的把请求发出去了
                                    onSelect(next)
                                }
                                break
                            }

                            // 位置 = **抓取时的表现值 + 本次手势的位移**（第 2 轮审计必修 2）
                            val pos = base + drag
                            // 往哪边是"出界"：周一(1) 再往右、周日(7) 再往左都没有那一天。
                            val pastEnd = (pos < 0f && sel == 7) || (pos > 0f && sel == 1)
                            val want = if (pastEnd) {
                                com.muyao.wanzaihyperos.ui.design.liquid.rubberBand(pos, cellPx)
                            } else {
                                pos.coerceIn(-cellPx, cellPx)
                            }
                            // ★ 必修 6：**跟手是直接操作，不是动画** ⇒ 无条件走。
                            //   以前写的是 `dragX.snapTo(if (motion.canMove) want else 0f)`，
                            //   减弱动效下手指推着条子屏幕上什么都不动、只在抬手时天被切走
                            //   —— 那才是真的"零反馈"。
                            // ⚠️ 台账里"这里不用 scope.launch"那句**在语言层面不成立**，我试过：
                            //   `Animatable.snapTo/animateTo` 带 `@RestrictSuspension`，
                            //   在 `awaitEachGesture` 体里直调会被编译器拒绝
                            //   （实跑原文：Restricted suspending functions can invoke member or
                            //   extension suspending functions only on their restricted coroutine scope）。
                            //   所以派发还得走 scope；**要治的那一条（跟手不许被减弱动效关掉）已经治了**。
                            scope.launch { dragX.snapTo(want) }
                            change.consume()
                        }
                    } finally {
                        // ★ 必修 3：**每一条出口都要回位**（抬手、竖向让路、被 consume、
                        //   pointer 从 changes 消失、pointerInput 因 `cellPx`/定义域变化而重启）。
                        //   以前只有"正常抬手"那一条会回位，其余把条子停在偏移值上。
                        //   减弱动效 ⇒ 不做回弹动画，直接归零（位移停了，切天本身仍是反馈）。
                        // 第 5 轮审计要的第二行：动画**真正拿到**的初速（与上一行对照就知道有没有被吞）。
                        // B4-15①：只走 `hzr_debug` 开关，日常包里不刷屏。
                        if (com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.debugLog) {
                            android.util.Log.i(
                                "wz-motion",
                                "weekstrip spring initialVelocity=$releaseVelocity from=${dragX.value}",
                            )
                        }
                        if (motion.canMove) {
                            scope.launch { dragX.animateTo(
                                0f,
                                androidx.compose.animation.core.spring(
                                    // 常量写法，不推高 `MotionGuardTest` 的裸写弹簧基线。
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                ),
                                initialVelocity = releaseVelocity,
                            ) }
                        } else {
                            scope.launch { dragX.snapTo(0f) }
                        }
                    }
                }
            },
        // 七格之间不留缝：稿上七格中心等距 50.6px = 全宽 ÷ 7，加了 gap 每格就窄一圈、
        // 选中胶囊会与日期中心对不齐（稿上胶囊正压在「五」这一列的正中）。
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // 2026-11-07：七格全画（周六周日保留，§4.1）。有几节课就画几个点，最多 3 个。
        (1..7).forEach { day ->
            val date = monday.plusDays((day - 1).toLong())
            // ★ 高亮判**没有夹过的 `selected`**：这一排只到周五，周六周日不该有哪一格被点亮
            //   （以前 `sel` 夹成 5 ⇒ 周六进来周五那格亮着，看着像「今天是周五」）。
            //   手势那套判据仍然用夹过的 `sel`（定义域洞那条注释在上面），两者分工不同。
            val isSelected = day == selected
            val isToday = day == today
            // ★ 2026-11-07 按 `screens/timetable.png` 复刻：
            //   选中 = **主色实底竖胶囊**（稿上 49×78px ⇒ 43×69dp，圆角 = 半宽）、**没有阴影**；
            //   未选中 = **完全无底色无描边**，只有灰字。
            //   旧版是 13dp 圆角矩形 + 6dp 主色阴影，看着像一块贴上去的糖，稿上更轻。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected) {
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.primary)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(onClickLabel = "查看" + DAY_WEEK[day - 1]) { onSelect(day) }
                    // 稿上胶囊高 69dp、宽 43dp；这里用上下 10dp 内衬 + 三行字自然撑到 ~64dp，
                    // 七列各自 1/7 宽（本机 347dp ⇒ 每格 49.5dp），比稿略宽但不挤。
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = DAY_LABELS[day - 1],
                    fontSize = Type.micro,
                    color = if (isSelected) colors.onPrimary.copy(alpha = 0.85f)
                    else colors.onSurfaceVariantSummary,
                )
                // 板 `.day .n{margin-top:4px}` ⇒ 3.5dp
                Spacer(Modifier.height(3.5.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    fontSize = Type.body,
                    fontWeight = if (isSelected || isToday) Type.weightTitle else Type.weightBody,
                    color = when {
                        isSelected -> colors.onPrimary
                        isToday -> colors.primary
                        else -> colors.onBackground
                    },
                    style = TextStyle(fontFeatureSettings = Type.tabular),
                    maxLines = 1,
                )
                // 板 `.dots{height:4px;margin-top:5px}` ⇒ 3.5dp 高、4.4dp 上边距
                Spacer(Modifier.height(4.4.dp))
                // 有几节课就几个点（最多 3 个）；没课也占住同样高度，整条不会上下跳。
                Row(
                    modifier = Modifier.height(3.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.2.dp),
                ) {
                    repeat(dotsFor(day)) {
                        Box(
                            modifier = Modifier
                                // 板 `.dots i{width:3px;height:3px}` ⇒ 2.6dp
                                .size(2.6.dp)
                                .clip(CircleShape)
                                .background(
                                    // ★ 未选中那几格的点是 **--sub @ 55%**（板 `.day.has .dots i`），
                                    // 不是薄荷实心：整排五点全绿会把「选中的那天」这件事稀释掉。
                                    if (isSelected) colors.onPrimary.copy(alpha = 0.8f)
                                    else colors.onSurfaceVariantSummary.copy(alpha = 0.55f)
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** 日程裡的日標題：一條短豎線 + 星期，和下面的課卡屬於同一組。 */
@Composable
private fun AgendaDayHeader(label: String, isToday: Boolean) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 0.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isToday) colors.primary
                    else colors.onSurfaceVariantSummary.copy(alpha = 0.35f)
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = Type.summary,
            fontWeight = Type.weightTitle,
            color = if (isToday) colors.primary else colors.onBackground,
        )
    }
}

/**
 * 一堂課的一行（「按天日程」排版，v2.1 稿的 `.course-row`）。
 *
 * 版面照稿 `style.css:107-114`：左邊固定一列時刻（上行粗黑、下行淡灰），
 * 中間是課名 + 「教室 · 老師」+ 「01-02 節」三行，右邊一顆 `›`。
 * 課名左邊那條 2dp 的淡紫灰豎線 [AgendaSpine] 是這一行唯一的裝飾。
 *
 * ⚠️ 整行可點（2026-11-07 起）：稿上右側那顆 `›` 不是裝飾，它承諾了「點進去看詳情」。
 * 所以這一行的點擊走的是**和網格版點格子同一條路徑** —— `onOpen(slot, 那一行的真實矩形)`
 * 交給上層的 `picked` + `SlotZoomOverlay`，從手指點的位置放大出來。
 * 以前這裡刻意不給點擊，是因為那時沒有詳情入口；現在有了，`›` 就不是騙人的箭頭。
 *
 * 用不透明的紙面而不是玻璃：這一頁下面沒有可採樣的 backdrop，
 * 玻璃會退化成實底，等於白多一層合成。
 *
 * ⚠️ 時刻只顯示 `PeriodClock.startOf` 算得出來的那一頭，算不出就留白 ——
 * 「現在」也一樣，起止兩頭缺一頭就不標：編一個下課時間會讓他把已下課的課當成還在教室。
 */
@Composable
private fun CourseCell(
    slot: TronScheduleSlot,
    isToday: Boolean,
    onOpen: (TronScheduleSlot, Rect) -> Unit = { _, _ -> },
) {
    val colors = MiuixTheme.colorScheme
    val times = UiPrefs.periodTimes
    val start = PeriodClock.startOf(times, slot.periodStart)
    val end = PeriodClock.endOf(times, slot.periodEnd)
    val ongoing = isToday && isOngoing(start, end)
    // 稿上第三行是「01-02 节」：两位补零 + 短横线，不是「第3-4节」。
    val span = buildString {
        append("%02d-%02d 节".format(slot.periodStart, slot.periodEnd))
        // 上午/下午/晚上是来源给的时段，稿上没有它的位置；有就接在后面，没有就不占位
        //（空字段不写「—」冒充「学校说没有」）。
        if (slot.slotLabel.isNotBlank()) append(" · ").append(slot.slotLabel)
    }
    // 稿上第二行是「理二 302 · 陈老师」——**教室在前**。先看在哪间教室，再看谁上。
    val meta = listOf(slot.room, slot.teacher)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val tabular = MiuixTheme.textStyles.main.copy(fontFeatureSettings = Type.tabular)
    var cellRect by remember { mutableStateOf<Rect?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
            // 稿 `.course-info{border-left:2px solid #8894be;padding-left:12px}`：
            // 课名左边这条 **2dp 的淡紫灰竖线**是整行的结构线，不是分隔线 ——
            // 它把「几点」和「上什么」分开，并且跟着文字一起长高（画在 size.height 上）。
            .drawBehind {
                val x = (AgendaPeriodWidth + 7.dp).toPx()
                drawLine(
                    color = AgendaSpine,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            .onGloballyPositioned { c ->
                cellRect = Rect(
                    offset = c.positionInRoot(),
                    size = Size(c.size.width.toFloat(), c.size.height.toFloat()),
                )
            }
            .clickable(onClickLabel = "看${slot.courseName.ifBlank { "这门课" }}的详情") {
                onOpen(slot, cellRect ?: Rect.Zero)
            },
        verticalAlignment = Alignment.Top,
    ) {
        // 稿上左列是「08:00 / 09:35」两行：上课粗黑、下课淡灰。
        Column(modifier = Modifier.width(AgendaPeriodWidth).padding(end = 4.dp)) {
            Text(
                text = start ?: slot.periodLabel,
                fontSize = Type.caption,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                style = tabular,
                maxLines = 1,
            )
            if (end != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = end,
                    fontSize = Type.caption,
                    // v4：不用透明度造灰。原来 0.75 合成出 #949087，对卡 3.10:1，10sp 小字不合格。
                    color = if (colors.background.luminance() < 0.5f) Tone.quietDark else Tone.quiet,
                    style = tabular,
                    maxLines = 1,
                )
            }
        }
        // 稿 `.course-info{padding-left:12px}`：竖线到课名 12dp。
        Column(modifier = Modifier.weight(1f).padding(start = 19.dp)) {
            Text(
                text = slot.courseName.ifBlank { "（未命名课程）" },
                fontSize = Type.bodyEmphasis,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = span,
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
                style = tabular,
                maxLines = 1,
            )
        }
        // 稿上右侧是一颗 `›`。这不是装饰：整行真的可以点（点开就是那一格的详情放大层，
        // 与网格版点格子同一条路径），所以这里给箭头是有依据的。
        when {
            ongoing -> {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 2.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "现在",
                        fontSize = Type.caption,
                        fontWeight = Type.weightTitle,
                        color = colors.primary,
                        maxLines = 1,
                    )
                }
            }

            slot.statusLabel.isNotBlank() -> Text(
                text = slot.statusLabel,
                fontSize = Type.caption,
                fontWeight = Type.weightBody,
                color = colors.error,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
            )

            else -> Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_board_chev),
                contentDescription = null,
                tint = colors.onSurfaceVariantSummary.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp)
                    .size(10.dp),
            )
        }
    }
}

/**
 * 日程左側節次列的固定寬度。
 *
 * 「第10-11节」在 12sp + tnum 下真机量到 210px（560dpi ⇒ 60dp）；
 * 上一版 66dp − 6dp 留白恰好只剩 60dp，`maxLines=1` 没有省略号 ⇒
 * 「节」被从右边切掉（2026-10-21 真机截图）。
 * 现在 76dp − 4dp = 72dp，留 12dp 余量；课名那一列仍有两百多 dp，不会挤成竖条。
 */
private val AgendaPeriodWidth = 42.dp

/**
 * 日程每行課名左邊那條豎線的顏色（稿 `style.css:111 .course-info{border-left:2px solid #8894be}`）。
 *
 * ⚠️ 這是**結構線**，不是分隔線：它把「幾點」和「上什麼」分開，寬度 2dp，跟著文字長高。
 * 色值直接照稿抄 —— 它不是主題色（比 `primary` 淡得多），換主題時不該跟著變，
 * 所以不從 `colors.primary` 派生。深色模式下這枚偏亮的紫灰仍壓得住暗卡面（對 #222837 約 4:1），
 * 不需要另配一枚暗色值。
 */
private val AgendaSpine = Color(0xFF8894BE)

/**
 * 「現在正在上」= 起止時刻都算得出來，且本地時間落在 [start, end) 裡。
 * 任何一頭未知都回 false（**不知道就不標**，不猜）。
 */
private fun isOngoing(start: String?, end: String?): Boolean {
    val from = start?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: return false
    val to = end?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: return false
    val now = java.time.LocalTime.now()
    return !now.isBefore(from) && now.isBefore(to)
}

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

private val DAY_WEEK = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 「回到今天」那颗药丸上的字。
 *
 * 判据只有一条：**药丸上的字必须等于按下去会看到的那一天**。
 * 按下去的动是 `selectedDay = today.dayOfWeek.value`，所以结果永远是
 * 「今天」（还在本周）或「今天这个星期几」（翻到别的周时，看到的是那一周的同一个星期几）。
 *
 * 2026-10-31 真机：周六进课表，`selectedDay` 被夹成 5，药丸上写「周五」，
 * 按下去却切到周六（`today = 6`）—— 字和事对不上。标签跟着**动作**走，不跟着当前选中走。
 */
internal fun dayPillLabel(todayDow: Int, weekOffset: Int): String =
    if (weekOffset == 0) "今天" else DAY_WEEK[todayDow.coerceIn(1, 7) - 1]

/**
 * 一塊課上的「幾點」那行。
 *
 * ★ 三級優先，且**最後一級是空字符串**（业主 2026-10-19：「你课表和时间对不上」）：
 *
 * 1. 他在「設定 → 節次時間」填過的時間（`10:00-11:40`）—— 這是唯一能精確到點的來源；
 * 2. 沒填時退回**學校自己標的時段**（教务表左列的「上午/下午/晚上」，真機取證：
 *    正方那一欄只有時段分組，`docs/B22-JW-CELL-HTML.txt` 裡學校給的 11 個具名欄位
 *    就沒有一個是「幾點」）；
 * 3. 兩個都沒有 ⇒ 這一行不出現。
 *
 * 為什麼寧可不寫也不給個默認時間：**編一個時間就是造數據**，
 * 而學生按編出來的時間去上課是要遲到的。
 */
private fun clockLine(slot: TronScheduleSlot): String {
    val times = UiPrefs.periodTimes
    return PeriodClock.label(times, slot.periodStart, slot.periodEnd) ?: slot.slotLabel
}

/**
 * 「課表上沒有幾點」這件事要**在課表上能解決**，不是讓他自己去設定里找。
 *
 * 判據用「本週visible的課裡，有沒有任何一塊能算出時間」——
 * 不是「填沒填過」：填了 1-4 節但本週有第 8-9 節的課，那一塊仍然不知道幾點，
 * 而那句「已填 4 节」會讓他以為設定了卻還是對不上（這正是這次抱怨的形態）。
 */
@Composable
private fun ClockHint(slots: List<TronScheduleSlot>, onOpen: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val times = UiPrefs.periodTimes
    val noClock = slots.count { slot ->
        PeriodClock.label(times, slot.periodStart, slot.periodEnd) == null
    }
    if (noClock == 0) return
    // 板子上没有这张卡的位置：压成一行，只在真的有课缺时间时出现。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.containerCorner))
            .background(colors.onBackground.copy(alpha = 0.05f))
            .clickable(onClickLabel = "补节次时间") { onOpen() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "有 $noClock 节对不到几点",
            style = TextStyle(fontFeatureSettings = Type.tabular),
            fontSize = Type.caption,
            fontWeight = Type.weightBody,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            // 学校日历那份已经盖住了绝大多数课块（`SchoolPeriods`）；
            // 这里只针对**两头都查不到**的那种（例如第 12 节上的课）。
            text = "补时间 ›",
            fontSize = Type.caption,
            color = colors.primary,
        )
    }
}


internal object ZoomMotion {
    /** 进场首帧的不透明度。不是 0：从全透明弹出来会像闪一下。 */
    const val ENTER_ALPHA_FLOOR = 0.30f

    /** 退场最后这一段路程把不透明度收到 0（占整段轨迹的比例）。 */
    const val EXIT_FADE_SPAN = 0.25f

    fun scaleAt(s0: Float, t: Float): Float = s0 + (1f - s0) * t

    fun alphaAt(t: Float, closing: Boolean): Float {
        val base = ENTER_ALPHA_FLOOR + (1f - ENTER_ALPHA_FLOOR) * t
        if (!closing) return base
        return base * (t / EXIT_FADE_SPAN).coerceIn(0f, 1f)
    }

    /** 退场用的时间曲线：弹簧或者（关掉动效时）一段短淡出，不是零。 */
    fun exitSpec(canMove: Boolean): androidx.compose.animation.core.AnimationSpec<Float> =
        if (canMove) {
            androidx.compose.animation.core.spring(
                dampingRatio = com.muyao.wanzaihyperos.ui.design.Motion.OVERLAY_OUT_DAMPING,
                stiffness = com.muyao.wanzaihyperos.ui.design.Motion.OVERLAY_OUT_STIFFNESS,
            )
        } else {
            androidx.compose.animation.core.tween(com.muyao.wanzaihyperos.ui.design.Motion.FAST_MS)
        }
}

/**
 * 点格子后就地放大的详情层。
 *
 * 三条硬要求（都来自实测，不是审美偏好）：
 * 1. 起点必须是**那一格的真实矩形**。写死一个比例的话，撞课那一列、翻到别的周、
 *    真机 font_scale 1.15 之后都对不上，看起来像从屏幕中间弹出来（业主原话「生硬」）。
 * 2. 只能缩放 + 位移，不能改布局：把 content 换成另一套排版会在动画中途跳一下。
 * 3. 关掉动效时降级为一次短淡入，**不是零**（动效标准第 3 条：减少动效 ≠ 没有动效）。
 */
@Composable
internal fun SlotZoomOverlay(
    slot: TronScheduleSlot,
    anchor: Rect?,
    pageOrigin: Offset,
    closing: Boolean = false,
    onClose: () -> Unit,
    onClosed: () -> Unit = {},
) {
    val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
    val haptics = com.muyao.wanzaihyperos.ui.design.rememberHaptics(
        enabled = com.muyao.wanzaihyperos.ui.design.rememberHapticsEnabled(
            androidx.compose.ui.platform.LocalContext.current,
        ),
    )
    val progress = remember(slot) { androidx.compose.animation.core.Animatable(0f) }
    var panelRect by remember { mutableStateOf<Rect?>(null) }
    val motionSpec = com.muyao.wanzaihyperos.ui.design.Motion
    LaunchedEffect(slot) {
        haptics.tick()
        if (motion.canMove) {
            progress.animateTo(
                1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = motionSpec.OVERLAY_IN_DAMPING,
                    stiffness = motionSpec.OVERLAY_IN_STIFFNESS,
                ),
            )
        } else {
            progress.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(motionSpec.FAST_MS))
        }
    }
    // 退场：业主说进场「生硬」，退场原来更差 —— onClose 直接把 picked 置空，整层一帧消失，
    // 连它自己文档第 3 条（关掉动效时降级为一次短淡入，不是零）都不满足。现在反过来播一遍
    // 同一条轨迹：缩回那一格的真实矩形，最后 25% 路程把不透明度收到 0，免得停在残影上再消失。
    // 播完才通知上层把 picked 清掉。
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        progress.animateTo(0f, animationSpec = ZoomMotion.exitSpec(canMove = motion.canMove))
        onClosed()
    }
    Box(Modifier.fillMaxSize()) {
        // 遮罩：既有「这一层在最上面」的提示，也顺手吃掉点击（点空白处收起）。
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f * progress.value))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dims.screenH)
                .align(Alignment.Center)
                .onGloballyPositioned { c ->
                    panelRect = Rect(
                        offset = c.positionInRoot() - pageOrigin,
                        size = Size(c.size.width.toFloat(), c.size.height.toFloat()),
                    )
                }
                .graphicsLayer {
                    val target = panelRect
                    val from = anchor
                    if (target != null && from != null && from.width > 1f && target.width > 1f) {
                        val t = progress.value
                        // 等比缩放：取宽高比里更小的那个，免得先被撑变形再回正。
                        val s0 = minOf(from.width / target.width, from.height / target.height)
                            .coerceIn(0.06f, 1f)
                        val s = ZoomMotion.scaleAt(s0, t)
                        scaleX = s
                        scaleY = s
                        translationX = from.center.x + (target.center.x - from.center.x) * t - target.center.x
                        translationY = from.center.y + (target.center.y - from.center.y) * t - target.center.y
                        alpha = ZoomMotion.alphaAt(t, closing)
                    }
                },
        ) {
            SlotDetailCard(slot = slot, onClose = onClose)
        }
    }
}

/**
 * 課表頁底部那三顆出口。
 *
 * 為什麼不用 Miuix 的 `TextButton`：它是 36dp 高、沒有描邊，而稿上這三顆是
 * **同寬、44dp、1dp 主色半透明描邊**的整行按钮（触区也要够 44dp）。
 * 改全局 TextButton 令牌会牵动 `DesignTokensTest`，所以就地画一个。
 */
@Composable
private fun TimetableFooterButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClickLabel = text, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = Type.secondary, color = colors.primary)
    }
}
