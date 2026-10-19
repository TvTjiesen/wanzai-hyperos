package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.TronSchedule
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurface
import com.muyao.wanzaihyperos.ui.design.glass.LocalGlassBackdrop
import com.muyao.wanzaihyperos.ui.design.liquid.LargeTitle
import com.muyao.wanzaihyperos.ui.design.liquid.LocalLargeTitle
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageChrome
import com.muyao.wanzaihyperos.ui.design.liquid.iosHeaderInset
import top.yukonga.miuix.kmp.basic.Text
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
 *   **豎向合併**、顏色按 `courseId` 走 137.5° 黃金角色相哈希。
 *   版式照 WakeUp / [Sleepy](https://github.com/lingion/sleepy) 那一類**自己寫**
 *   —— Sleepy 是 GPL-3.0，**一行代碼都不搬**，只参照它的版面結構。
 * - **列表**：原本那種按天分段的卡片（資訊不丟，趕時間時看得更快）。
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
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val state by graph.schedule.state.collectAsState()
    val backdrop = LocalGlassBackdrop.current

    // 要顯示哪一週。
    //
    // `0` = 目前教學週（由 schedule 自己算，它會問 `teachingWeeks()`）；
    // 正負數 = 相對偏移。**不重新請求資料** —— 資料本來就含全學期。
    var weekOffset by remember { mutableIntStateOf(0) }
    // 三天窗口从哪一天開始：默認讓**今天落在中間**（左邊是昨天、右邊是明天）。
    var windowStart by remember {
        mutableIntStateOf((java.time.LocalDate.now().dayOfWeek.value - 1).coerceIn(1, 5))
    }
    var picked by remember { mutableStateOf<TronScheduleSlot?>(null) }

    LaunchedEffect(Unit) {
        // 应用级作用域（`AppGraph.fetch`）：切走页面不该把课表请求取消，
        // 更不该让取消被写成「取不到課表」。见 `AppGraph.ioScope` 注释。
        graph.fetch { graph.schedule.refresh() }
    }

    // ── 顶栏试点接线（T53）────────────────────────────────────────────
    //
    // 大标题不再由本页自己画，而是照参考那份 `ui/screen/Page.kt`：
    // **标题是列表的第 0 项**，跟着内容一起滚；滚到顶就由 `IosTopBar` 接手小标题。
    // 折叠度 = 标题被滚出去的比例，只用 `firstVisibleItem` 的信息，
    // 不接嵌套滚动 —— 参考原话，也是它不会和底栏抢手势的原因。
    val chrome = LocalPageChrome.current
    val active = LocalPageActive.current
    val largeTitle = LocalLargeTitle.current
    val barVisible = LocalNavBarVisible.current
    val listState = rememberLazyListState()
    var titleHeightPx by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(listState, largeTitle, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            if (largeTitle.isEmpty()) 1f
            else if (listState.firstVisibleItemIndex > 0) 1f
            else listState.firstVisibleItemScrollOffset / titleHeightPx.coerceAtLeast(1f)
        }.collect { chrome.progress = it.coerceIn(0f, 1f) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            // 标题自己带 inset，这里不再留 56dp 顶白
            bottom = if (barVisible) 132.dp else 44.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        if (largeTitle.isNotEmpty()) {
            item(key = "large_title") {
                Box(
                    modifier = Modifier.onSizeChanged {
                        if (it.height > 0) titleHeightPx = it.height.toFloat()
                    }
                ) {
                    LargeTitle(text = largeTitle, topInset = iosHeaderInset())
                }
            }
        }

        // ---- 週切換（玻璃）--------------------------------------------------
        item(key = "week-switch") {
            GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WeekNavButton("‹", desc = "上一週") { weekOffset -= 1 }
                    Text(
                        text = when (weekOffset) {
                            0 -> "本學期 · 本週"
                            -1 -> "本學期 · 上一週"
                            1 -> "本學期 · 下一週"
                            else -> if (weekOffset > 0) "本學期 · $weekOffset 週後" else "本學期 · ${-weekOffset} 週前"
                        },
                        fontSize = Type.body,
                        fontWeight = Type.weightBody,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    WeekNavButton("›", desc = "下一週") { weekOffset += 1 }
                }
            }
        }

        // ---- 排版切換 -------------------------------------------------------
        item(key = "view-switch") {
            ViewModeSwitch(grid = uiState.timetableGrid) { uiState.timetableGrid = it }
        }

        // ---- 課表內容 -------------------------------------------------------
        when (val s = state) {
            is ListState.FirstLoad -> item(key = "loading") {
                Text(
                    "正在取得課表…",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(Dims.cardPadding),
                )
            }

            is ListState.Failed -> item(key = "failed") {
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text(
                            "取不到課表",
                            fontSize = Type.body,
                            fontWeight = Type.weightBody,
                            color = colors.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.error.userText,
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            is ListState.Content -> {
                // ⚠️ `schedule.state` 是 `ListState<TronSchedule>`，而 `Content.items`
                // 是 `List<TronSchedule>` —— **單元素清單**（倉庫把單一 schedule 包起來）。
                // 所以要先 `firstOrNull()` 才是 TronSchedule 本身。
                val schedule = s.items.firstOrNull()
                // 目前教學週：schedule 自己算好的（CountValue.Known 才有值）。
                val baseWeek = (schedule?.currentWeek as? com.muyao.wanzaihyperos.data.CountValue.Known)?.value
                val targetWeek = baseWeek?.plus(weekOffset)

                if (targetWeek == null) {
                    item(key = "no-week") {
                        Text(
                            "還不知道目前是第幾教學週，所以無法對應課表。",
                            fontSize = Type.secondary,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier.padding(Dims.cardPadding),
                        )
                    }
                } else {
                    val slots = schedule?.slots.orEmpty()
                        .filter { it.activeIn(targetWeek) }
                        .sortedWith(compareBy({ it.dayOfWeek }, { it.periodStart }))

                    if (slots.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "第 $targetWeek 週沒有課程。",
                                fontSize = Type.secondary,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(Dims.cardPadding),
                            )
                        }
                    } else if (uiState.timetableGrid) {
                        // ---- 網格（三天窗口）------------------------------
                        item(key = "day-window") {
                            DayWindowChips(
                                start = windowStart,
                                today = LocalDate.now().dayOfWeek.value,
                                onPick = { windowStart = it },
                            )
                        }
                        item(key = "grid") {
                            AppCard {
                                WeekGrid(
                                    schedule = schedule,
                                    slots = slots,
                                    today = LocalDate.now().dayOfWeek.value,
                                    windowStart = windowStart,
                                    onPick = { picked = it },
                                )
                            }
                        }
                        if (picked != null) {
                            item(key = "picked") {
                                SlotDetailCard(slot = picked, onClose = { picked = null })
                            }
                        }
                    } else {
                        // ---- 列表（原有排版，資訊不丟）--------------------------
                        item(key = "week-label") {
                            Text(
                                text = "第 $targetWeek 週",
                                fontSize = Type.summary,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        slots.groupBy { it.dayOfWeek }.forEach { (day, daySlots) ->
                            item(key = "day-$day") {
                                Text(
                                    text = daySlots.first().dayLabel,
                                    fontSize = Type.summary,
                                    color = colors.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 2.dp),
                                )
                            }
                            daySlots.forEach { slot ->
                                item(key = "slot-${slot.courseId}-$day-${slot.periodStart}") {
                                    CourseCell(slot)
                                }
                            }
                        }
                    }

                    // ---- 認不出來的段落**原樣列出來**（兩種排版都要有）----------
                    //
                    // 寧可顯示一句看不懂的字串，也不要讓某門課的上課時間靜默消失
                    //（`TronScheduleSlot` 檔頭那條紀律）。
                    val unparsed = schedule?.unparsed.orEmpty()
                    if (unparsed.isNotEmpty()) {
                        item(key = "unparsed") {
                            AppCard {
                                Column(Modifier.padding(Dims.cardPadding)) {
                                    Text(
                                        "有 ${unparsed.size} 段上課時間沒認出格式（原樣列在下面）",
                                        fontSize = Type.summary,
                                        color = colors.error,
                                    )
                                    unparsed.forEach { (courseName, raw) ->
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
                    "還沒有課表資料。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(Dims.cardPadding),
                )
            }
        }
    }
}

/**
 * 週網格：左節次軸 + 按天的列，課程塊**跨節豎向合併**。
 *
 * ## 為什麼是「每列一個 Box + offset」而不是 LazyVerticalGrid
 *
 * 一塊課要佔 `periodStart..periodEnd` 好幾行，格狀容器做不到跨行合併；
 * 絕對定位（`offset(y)` + `height(節數 × 行高)`）才是 WakeUp / Sleepy 那一類的實際画法。
 *
 * ## 為什麼週六日「有課才出現」
 *
 * 這台機 1216px 寬 ≈ 442dp。七列 + 軸最多每列 ~50dp，課名只能截成兩個字；
 * 沒課的週末列純粹佔地方。所以固定顯示週一～五，**週末有課才加進來**。
 */
@Composable
private fun WeekGrid(
    schedule: TronSchedule?,
    slots: List<TronScheduleSlot>,
    today: Int,
    windowStart: Int,
    onPick: (TronScheduleSlot) -> Unit,
) {
    val colors = MiuixTheme.colorScheme

    // **三天窗口**，不是整週七列。
    //
    // 上一版把 5~7 天全塞進 442dp，同一時段又常撞 2~3 門課 ⇒ 每欄只剩 ~25dp，
    // 課名豎成一串認不出的波浪（業主原話：「我都看不見課了」）。
    // 三天 = 每天 ~130dp，撞兩門時每欄還有 ~65dp，課名能正常橫排。
    // 換窗口用頂部那排日子按鈕，**不做橫向滑動** —— 這頁的左右滑是「切板塊」的，
    // 在這裡加橫向滾動就會兩套水平手勢搶同一次拖動。
    val allDays = (1..7).toList()
    val start = windowStart.coerceIn(1, 7 - 2)
    val days = (start..start + 2).toList()

    val maxPeriod = (slots.maxOfOrNull { it.periodEnd } ?: 8).coerceAtLeast(1)
    // 「整學期」= 本週所有格子裡最寬的那個週次區間。只有**不是**整學期的課才在格子裡
    // 標週次（單雙週、第 5-17 週這種），否則每格都掛一行廢字。
    val termStart = slots.minOfOrNull { it.weekStart } ?: 1
    val termEnd = slots.maxOfOrNull { it.weekEnd } ?: termStart
    val rowH = 56.dp
    // 節次時間表（設定頁填的）。**沒填就只標節次號**，不編時間。
    val periodTimes = com.muyao.wanzaihyperos.data.UiPrefs.periodTimes
    val hasTimes = periodTimes.any { it.first.isNotBlank() }
    val axisW = if (hasTimes) 46.dp else 34.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }

    // 每天要几栏（同一时段撞几门课）。表頭與列共用這一份，列寬才不會和標題錯位。
    val lanesOfDay = days.associateWith { day ->
        val ends = mutableListOf<Int>()
        slots.filter { it.dayOfWeek == day }.sortedBy { it.periodStart }.forEach { s ->
            val free = ends.indexOfFirst { it < s.periodStart }
            if (free >= 0) ends[free] = s.periodEnd else ends.add(s.periodEnd)
        }
        ends.size.coerceAtLeast(1)
    }

    // 表頭：参照站只寫「一 二 三…」（不帶「周」字），今天那一列用主题色加粗。
    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)) {
        Spacer(Modifier.width(axisW))
        days.forEach { day ->
            val isToday = day == today
            Text(
                text = DAY_LABELS.getOrElse(day - 1) { day.toString() },
                fontSize = Type.summary,
                fontWeight = if (isToday) Type.weightTitle else Type.weightBody,
                color = if (isToday) colors.primary else colors.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                // 表頭和列用同一份欄數，否則列寬放大後標題會和列錯位。
                modifier = Modifier.weight((lanesOfDay[day] ?: 1).toFloat()),
            )
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // ---- 節次軸 --------------------------------------------------------
        //
        // ⚠️ 只標節次號，**不標幾點幾分**：學校接口沒有「節次→時間」表
        //   （`/api/org/{id}/teaching-weeks` 只有週次與日期），編一個時間就是造數據。
        //   参照站那個位置是有時分的（正方的節次表是公开的），等我們拿到可靠來源再補。
        Column(modifier = Modifier.width(axisW)) {
            (1..maxPeriod).forEach { period ->
                Box(modifier = Modifier.height(rowH)) {
                    Column(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = period.toString(),
                            fontSize = Type.body,
                            fontWeight = Type.weightBody,
                            color = colors.onSurfaceVariantSummary,
                        )
                        // 時間是**他自己在設定頁填的**，不是我們推的：填了才顯示
                        periodTimes.getOrNull(period - 1)?.takeIf { it.first.isNotBlank() }?.let { (s, e) ->
                            Text(
                                text = s,
                                fontSize = 9.sp,
                                color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
                            )
                            if (e.isNotBlank()) {
                                Text(
                                    text = e,
                                    fontSize = 9.sp,
                                    color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }
        }

        days.forEach { day ->
            val isToday = day == today
            val daySlots = slots.filter { it.dayOfWeek == day }
            // 同一格撞两门课（真机实测：周二第 1-2 節有兩門）必須**分欄並排**，
            // 不能疊在同一個位置上 —— 上一版就是疊著畫，文字互相壓成一片糊。
            // 贪心分欄：按開始節排序，能塞進已結束的那一欄就塞，否則開新的一欄。
            val laneEnds = mutableListOf<Int>()
            val laneOf = HashMap<Int, Int>()
            daySlots.sortedBy { it.periodStart }.forEach { s ->
                val key = s.courseId * 1000 + s.periodStart
                val free = laneEnds.indexOfFirst { it < s.periodStart }
                if (free >= 0) {
                    laneEnds[free] = s.periodEnd
                    laneOf[key] = free
                } else {
                    laneEnds.add(s.periodEnd)
                    laneOf[key] = laneEnds.lastIndex
                }
            }
            val lanes = laneEnds.size.coerceAtLeast(1)

            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    // ★ 撞课的天**按栏数加宽**（1 栏 = 1 份权重）。上一版每列固定 1 份，
                    //   撞两门课时那一栏被挤到 ~17dp，字竖成一串波浪（真机截图证实）。
                    .weight(lanes.toFloat())
                    .height(rowH * maxPeriod)
                    .then(
                        if (isToday) Modifier.background(colors.primary.copy(alpha = 0.06f)) else Modifier
                    )
                    .drawBehind {
                        val line = colors.onBackground.copy(alpha = 0.08f)
                        (1 until maxPeriod).forEach { i ->
                            val y = rowPx * i
                            drawLine(line, start = Offset(0f, y), end = Offset(size.width, y))
                        }
                    }
            ) {
                val colW = maxWidth / lanes
                // ★ 窄於 ~46dp 就**只畫色條、不塞字**：你的數據裡週三/週五同一時段常撞
                //   兩三門課，一欄只剩 ~25dp，硬排課名會豎成一串認不出的波浪（真機截圖證實）。
                //   色條 + 點一下看詳情，比「看起來壞了」誠實；要看全名用「列表」排版。
                val compact = colW < 46.dp
                daySlots.forEach { slot ->
                    val key = slot.courseId * 1000 + slot.periodStart
                    val lane = laneOf[key] ?: 0
                    CourseBlock(
                        slot = slot,
                        rowH = rowH,
                        showWeek = !(slot.weekStart == termStart && slot.weekEnd == termEnd) && !compact,
                        compact = compact,
                        modifier = Modifier
                            .offset(x = colW * lane, y = rowH * (slot.periodStart - 1))
                            .width(colW)
                            .height(rowH * (slot.periodEnd - slot.periodStart + 1)),
                        onPick = onPick,
                    )
                }
            }
        }
    }

    // 参照站那块面板下方是一颗淡彩玻璃胶囊「本周 13 门课程」——
    // 它同时是我们的**取数回执**：这一周真取到几门课，一眼能核对，不用去数格子。
    Box(
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(Dims.cardCorner))
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = "本週 ${slots.map { it.courseId }.distinct().size} 門課程 · 點格子看詳情",
            fontSize = Type.summary,
            fontWeight = Type.weightBody,
            color = colors.primary,
        )
    }
}

/**
 * 一格課。
 *
 * ## 配色照参照站（正方教务助手）那套，不是照它代码
 *
 * `courseId × 137.5°` 取色相（黄金角哈希，同一門課恆同色，且不依賴接口回傳顏色），
 * 然後：
 * - **填充** = 同色相、低飽和、高明度 + 55% alpha（淡彩，讓背後的網格線透出來）；
 * - **描边** = 同色相、飽和一點、明度低一档，1.5dp —— 参照站那种「彩色描边的果冻块」；
 * - **文字** = 主题的 `onBackground`（浅色下深字、深色下白字），**不写死白色**。
 *
 * 上一版是实心高饱和 + 白字，在浅色主题下像贴了一堆色卡，和参照站观感差一档。
 */
@Composable
private fun CourseBlock(
    slot: TronScheduleSlot,
    rowH: Dp,
    showWeek: Boolean,
    compact: Boolean = false,
    modifier: Modifier,
    onPick: (TronScheduleSlot) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val span = slot.periodEnd - slot.periodStart + 1
    val hue = ((slot.courseId * 137.5f) % 360f + 360f) % 360f
    val fill = Color.hsl(hue, 0.55f, if (colors.background.luminance() < 0.5f) 0.34f else 0.78f)
        .copy(alpha = 0.55f)
    val stroke = Color.hsl(hue, 0.62f, if (colors.background.luminance() < 0.5f) 0.62f else 0.52f)
    val shape = RoundedCornerShape(Dims.cardCorner)
    val partial = showWeek

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(shape)
            .background(fill)
            .drawBehind {
                // 描边自己画：Modifier.border 会算在内容尺寸里，1.5dp 的边会把两行的格子挤掉一行
                drawRect(
                    color = stroke,
                    style = Stroke(width = 1.5.dp.toPx()),
                    topLeft = Offset(0.75f.dp.toPx(), 0.75f.dp.toPx()),
                    size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
                )
            }
            .clickable { onPick(slot) }
    ) {
        Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp)) {
            // 窄欄只留前 4 個字（豎著兩行剛好）—— 純色條認不出是哪門課，
            // 全名又會豎成波浪；兩個字是這塊屏上「能認」的下限。
            Text(
                text = if (compact) slot.courseName.take(4).ifBlank { "課" }
                else slot.courseName.ifBlank { "（未命名）" },
                fontSize = if (compact) 10.sp else if (span >= 2) 11.sp else 10.sp,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                maxLines = if (compact) 2 else if (span >= 2) 3 else 2,
            )
            if (span >= 2 && !compact) {
                Text(
                    text = slot.periodLabel,
                    fontSize = 9.sp,
                    color = colors.onBackground.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
            if (partial) {
                Text(
                    text = slot.weekLabel,
                    fontSize = 9.sp,
                    color = colors.onBackground.copy(alpha = 0.62f),
                    maxLines = 1,
                )
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
                    text = slot.courseName.ifBlank { "（未命名課程）" },
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
            DetailRow("時間", "${slot.dayLabel} ${slot.periodLabel}")
            DetailRow("週次", slot.weekLabel)
            DetailRow("原文", slot.raw)
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
 * 排版切換：一顆膠囊轨道，兩個選項。
 *
 * 不用 Miuix `TabRow`：它自帶水平嵌套滚动/overscroll（`miuix-skill` 的
 * component-selection.md 第 116 行明写过），會和這頁剛加的水平滑動切板塊打架。
 */
@Composable
private fun ViewModeSwitch(grid: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.cardCorner))
            .background(colors.onBackground.copy(alpha = 0.06f))
            .padding(3.dp)
    ) {
        ViewModePill("網格", selected = grid, modifier = Modifier.weight(1f)) { onChange(true) }
        ViewModePill("列表", selected = !grid, modifier = Modifier.weight(1f)) { onChange(false) }
    }
}

@Composable
private fun ViewModePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dims.chipCorner))
            .background(if (selected) colors.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = Type.summary,
            fontWeight = if (selected) Type.weightTitle else Type.weightBody,
            color = if (selected) colors.primary else colors.onSurfaceVariantSummary,
        )
    }
}

/**
 * 三天窗口的起點按鈕（一…五；週六/週日只在真有課時才給）。
 *
 * 用按鈕而不是橫向滑動：這頁的左右滑已經歸「切板塊」，再疊一层橫向滾動就是兩套手勢
 * 搶同一次拖動（上一輪剛在閏值上栽過一次，不重犯）。
 */
@Composable
private fun DayWindowChips(start: Int, today: Int, onPick: (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val lastStart = if (today >= 6) 5 else 5
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dims.tight),
    ) {
        (1..lastStart).forEach { s ->
            val selected = s == start
            val coversToday = today in s..s + 2
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dims.chipCorner))
                    .background(
                        if (selected) colors.primary.copy(alpha = 0.16f)
                        else colors.onBackground.copy(alpha = 0.05f)
                    )
                    .clickable(onClickLabel = "看${DAY_LABELS[s - 1]}起三天") { onPick(s) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${DAY_LABELS.getOrElse(s - 1) { s.toString() }}${if (coversToday) "·今" else ""}",
                    fontSize = Type.caption,
                    fontWeight = if (selected) Type.weightTitle else Type.weightBody,
                    color = when {
                        selected -> colors.primary
                        coversToday -> colors.onBackground
                        else -> colors.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}

/**
 * 切週的圓形小鈕（參照站頂部那對 ‹ ›）。
 *
 * ⚠️ `label` 只是字形，`desc` 才給無障礙用 —— 把「‹」當 contentDescription
 *   的話 TalkBack 會念成「左尖括號」，等於沒有。
 */
@Composable
private fun WeekNavButton(label: String, desc: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.12f))
            .clickable(onClickLabel = desc) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = Type.title,
            fontWeight = Type.weightTitle,
            color = colors.primary,
            modifier = Modifier.semantics { contentDescription = desc },
        )
    }
}

/**
 * 一堂課的格子（列表排版用）。
 *
 * ⚠️ 顯示用的是 `TronScheduleSlot` **自帶的顯示名**（`dayLabel` / `periodLabel`），
 * 不是自己拼的 —— 那些屬性已經處理好「單節 vs 連堂」的差別
 * （`第3節` vs `第3-4節`）。自己拼容易在連堂時顯示成「第3節」而漏掉 4。
 */
@Composable
private fun CourseCell(slot: TronScheduleSlot) {
    val colors = MiuixTheme.colorScheme
    AppCard {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.courseName.ifBlank { "（未命名課程）" },
                    fontSize = Type.body,
                    fontWeight = Type.weightBody,
                    color = colors.onBackground,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = slot.periodLabel,
                    fontSize = Type.caption,
                    color = colors.primary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = slot.raw.ifBlank { slot.weekLabel },
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
            )
        }
    }
}

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
