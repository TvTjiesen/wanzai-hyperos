package com.muyao.wanzaihyperos.ui.zy

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.zy.ZyEngine
import com.muyao.wanzaihyperos.data.zy.ZyRepository
import com.muyao.wanzaihyperos.data.zy.ZyScheduleItem
import com.muyao.wanzaihyperos.data.zy.timeRange
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.MarkedHeading
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.infoItemsIndexed
import com.muyao.wanzaihyperos.ui.web.WebShellPage
import java.time.LocalDate
import kotlinx.coroutines.launch
import com.muyao.wanzaihyperos.ui.components.WanzaiButton as Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **智雲課堂**原生頁：本週課表 + 每堂課的回放入口。
 *
 * ## ★ 這個頁面為什麼也要藏一個 WebView
 *
 * 不是偷懶。它的認證靠一批**域 cookie**（`.hzcu.edu.cn` 下），
 * 而本專案的 cookie 罐是**按 host 精確分桶**的（那個設計是刻意的，
 * 為了不讓 CAS 憑據跨系統漂移）——所以 OkHttp 帶不上那些域 cookie，
 * 打出去的請求會是「未登入」。
 *
 * 讓 WebView 自己發請求，域 cookie 由它自己管，天然正確。
 * 而且**播放頁迟早要用 WebView**（觀看進度上報是學校認定學習進度的依據），
 * 既然那個會話已經在 WebView 裡，取數也用它最一致。
 * 詳見 `data/zy/ZyEngine.kt` 的類註釋。
 *
 * WebView 給 **1 像素**且透明：**不能用 `GONE`**（那不執行 JS，取數會靜靜失敗）。
 *
 * ## ★★ 2026-11-07：假出口 G03 修掉了
 *
 * 舊版「去智云课堂登录」的 `onClick` 是 `onExit` —— 那只是 `backStack.removeLastOrNull()`，
 * 等於**按了返回鍵**：使用者既沒登入，頁面還退掉了，回來仍是「未登入」。
 * 那是一個**假的出口**（評審 G03 記的就是這件事）。
 *
 * 現在它真的打開智雲自己的網頁（同一個進程預設 `CookieManager`，
 * 與隱藏引擎共用域 cookie），登入完點返回 → 這裡 `force` 重取一次。
 * 不新增路由、不改 `WanzaiRoot`：出口就是本頁自帶的一個可見 `WebShellPage`。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ZyPage(
    repository: ZyRepository,
    engine: ZyEngine,
    onOpenReplay: (ZyScheduleItem) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.week.collectAsState()
    val scope = rememberCoroutineScope()

    // 真正的登入出口：打開智雲自己的網頁（可見的 WebView 殼）。
    var webLogin by remember { mutableStateOf(false) }
    // 從網頁回來之後要重取一次 —— 登入剛完成，手上的肯定是舊結果。
    var reloadAfterLogin by remember { mutableStateOf(false) }

    // 首次進入就載入（引擎內部會先建／沿用會話）。
    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.load() }
    }
    LaunchedEffect(reloadAfterLogin) {
        if (reloadAfterLogin) {
            reloadAfterLogin = false
            runCatching { repository.load(force = true) }
        }
    }

    if (webLogin) {
        // 登入頁是原站自己的頁面，登完由使用者按返回 —— 所以 onExit 只是關掉這一層，
        // **不是**離開智雲頁（這正是舊版 onclick=onExit 的病根）。
        WebShellPage(
            title = "智云课堂登录",
            url = ZyEngine.BASE + "/#/pages/liveCourse/index?tenant_code=" + ZyEngine.TENANT,
            onExit = {
                webLogin = false
                reloadAfterLogin = true
            },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val failed = state as? ListState.Failed
    val needsLogin = failed?.error is HzcuException.LoginExpired
    val items: List<ZyScheduleItem> = (state as? ListState.Content)?.items.orEmpty()

    // 頂欄漸變紗要靠它算進度（見 SecondaryScaffold 的 scrollProgress 說明）。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 按日芯片选中了哪一天：-1 = 还没动过（自动落在今天）。
    // 之所以提到页面这一层：zyWeek 是 LazyListScope 扩展函数，里面不能 remember。
    var zyPicked by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableIntStateOf(-1)
    }
    val veilProgress by com.muyao.wanzaihyperos.ui.design.rememberScrollProgress(listState)

    SecondaryScaffold(
        scrollProgress = veilProgress,
        title = "智云课堂",
        subtitle = "本周课表与回放",
        onBack = onExit,
        onRefresh = { scope.launch { runCatching { repository.load(force = true) } } },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "zy-hero") {
                    SecHero(
                        eyebrow = "ZHIYUN / 智云课堂",
                        title = "把课堂留在手边",
                        sub = "本周按日浏览 · 播放与进度由官方播放器负责。",
                    )
                }

                when {
                    // 沒登入 → 給他一個**真的**出口：打開智雲自己的網頁登入一次。
                    // 不做原生登入表單：那是學校統一身份認證的頁面，
                    // 抄一份等於多一處要跟著學校改的地方。
                    needsLogin -> {
                        item(key = "zy-login-banner") {
                            ResultBanner(
                                message = "还没有登录智云课堂。打开智云自己的网页登录一次，" +
                                    "回来刷新就能看到你的课表。",
                                ok = false,
                            )
                        }
                        item(key = "zy-login-exit") {
                            Button(
                                onClick = { webLogin = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("去智云网页登录") }
                        }
                    }

                    failed != null -> item {
                        ResultBanner(message = failed.error.userText, ok = false)
                    }

                    state is ListState.FirstLoad -> item {
                        HintText("正在登录智云课堂并取课表…")
                    }

                    items.isEmpty() -> item {
                        HintText("本周没有课程安排。")
                    }

                    else -> zyWeek(items, zyPicked, { zyPicked = it }, onOpenReplay)
                }

                item {
                    HintText(
                        "说明：只有学校给了回放的课才能点开播放。" +
                            "播放用的是智云课堂自己的页面（观看进度由学校统计）。",
                    )
                }
            }

            // ---- 看不見的取數器 ------------------------------------------------
            AndroidView(
                modifier = Modifier.size(1.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        layoutParams = ViewGroup.LayoutParams(1, 1)
                        alpha = 0f
                        engine.attach(this)
                    }
                },
            )
            DisposableEffect(Unit) {
                onDispose { engine.detach() }
            }
        }
    }
}

/**
 * 本週課表：頂上一排「按日」芯片，下面只畫那一天。
 *
 * ## 為什麼要按天切
 *
 * 板（`zy.png`）就是「一 02 … 日 08」七枚芯片 + 當天課程。七天全鋪在一頁時，
 * 「今天有什麼課」得自己往下找，而這頁最常被問的恰恰就是那句。
 *
 * ## ★ 兩個不能編的地方
 *
 * 1. **日期用本機時鐘算**（`LocalDate.now()` 往前推到本週一），
 *    不從服務端字串反推星期 —— 反推錯一個時區就會把「今天」標到昨天。
 * 2. **服務端給的日期與本週七天對不上時，退回「不分天全列」**。
 *    寧可少一層篩選，也不能因為時區／跨週邊界把整週課表藏起來說「沒有課」。
 */
private fun androidx.compose.foundation.lazy.LazyListScope.zyWeek(
    items: List<ZyScheduleItem>,
    picked: Int,
    onPick: (Int) -> Unit,
    onOpenReplay: (ZyScheduleItem) -> Unit,
) {
    val week = weekDates(LocalDate.now())
    val known = items.map { it.day }.toSet()
    val matchesWeek = week.any { it.second in known }

    if (!matchesWeek) {
        // 對不上就老實全列（分組標題仍用服務端原字串）。
        val byDay = items.groupBy { it.day }
        byDay.forEach { (day, dayItems) ->
            item(key = "day-$day") { MarkedHeading(text = dayText(day), icon = MarkIcon.Course) }
            infoItemsIndexed(dayItems) { c -> zyRow(c, onOpenReplay) }
        }
        return
    }

    // -1 = 還沒動過，自動落在「今天」那一枚；这个 state 由 ZyPage 持有并传进来
    // （LazyListScope 扩展函数里不能 remember，那属于 composable 作用域）。
    val todayIndex = (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6)
    val index = if (picked in 0..6) picked else todayIndex
    val dayItems = items.filter { it.day == week[index].second }

    item(key = "zy-days") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dims.tight),
            modifier = Modifier.padding(vertical = Dims.tight),
        ) {
            items(7) { i ->
                val on = i == index
                AppCard(
                    modifier = Modifier.width(86.dp),
                    onClick = { onPick(i) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dims.minTouch),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = week[i].first,
                            fontSize = Type.bodyEmphasis,
                            fontWeight = if (on) Type.weightTitle else Type.weightBody,
                            color = if (on) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            style = TextStyle(fontFeatureSettings = Type.tabular),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    if (dayItems.isEmpty()) {
        // 「這一天沒課」與「這一週沒課」是兩件事，不能混著說。
        item(key = "zy-day-empty") {
            HintText(week[index].first + " 这一天没有课程。别的日期仍以服务端课表为准。")
        }
    } else {
        item(key = "zy-day-title") {
            MarkedHeading(text = week[index].third, icon = MarkIcon.Schedule)
        }
        infoItemsIndexed(dayItems) { c -> zyRow(c, onOpenReplay) }
    }
}

@Composable
private fun zyRow(c: ZyScheduleItem, onOpenReplay: (ZyScheduleItem) -> Unit) {
    InfoItem(
        title = c.title,
        subtitle = listOfNotNull(
            timeRange(c.startAt, c.endAt),
            c.room.takeIf { it.isNotBlank() },
            c.teacher.takeIf { it.isNotBlank() },
        ).joinToString("　"),
        // 狀態文字**用服務端給的**（實測「回放」「未开始」…）
        trailing = c.displayStatus.takeIf { it != "—" },
        trailingColor = if (c.hasReplay) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onBackgroundVariant
        },
        onClick = if (c.hasReplay) {
            { onOpenReplay(c) }
        } else {
            null
        },
    )
}

/**
 * 本週七天：(芯片字、`YYYY-MM-DD`、分區標題)。
 *
 * 芯片字照板是「一 02」這種「星期 + 兩位日」；分區標題是「11月06日」。
 * 三者都由同一個 [LocalDate] 產生，不會互相對不上。
 */
private fun weekDates(today: LocalDate): List<Triple<String, String, String>> {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    return (0 until 7).map { i ->
        val d = monday.plusDays(i.toLong())
        Triple(
            names[i] + " " + String.format("%02d", d.dayOfMonth),
            d.toString(),
            d.monthValue.toString() + "月" + String.format("%02d", d.dayOfMonth) + "日",
        )
    }
}

/**
 * 這頁的標題區：eyebrow + 大標題 + 一句說明。
 *
 * 板 `.eyebrow{11.29px}`×0.886 ⇒ `Type.caption`；`.hero-title{29.35px}`×0.886
 * ⇒ `Type.displayTitle`（26sp，**不新造字號檔**）；`.muted` ⇒ `Type.caption` 灰。
 *
 * 與其他二次頁各自私有這一份：它只有二十來行，而共用位置在 `ui/design` / `ui/common`，
 * 那兩處不歸本輪可寫範圍 —— 與其伸手過去，不如先重複，等 Lead 決定要不要上提。
 */
@Composable
private fun SecHero(eyebrow: String, title: String, sub: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(bottom = Dims.tight)) {
        Text(
            text = eyebrow,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            letterSpacing = 0.10.em,
            color = colors.onSurfaceVariantSummary,
            style = TextStyle(fontFeatureSettings = Type.tabular),
        )
        Text(
            text = title,
            fontSize = Type.displayTitle,
            fontWeight = Type.weightTitle,
            letterSpacing = (-0.02).em,
            lineHeight = Type.lineDisplayTitle,
            color = colors.onBackground,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = sub,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * `2026-10-12` → 顯示用日期。
 *
 * 服務端給的是 `YYYY-MM-DD`，本身就清楚，**原樣顯示**（對不上本週時走的那一支）。
 */
private fun dayText(day: String): String = day