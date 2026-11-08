package com.muyao.wanzaihyperos.ui.main.pages

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.muyao.wanzaihyperos.ui.design.pressSpring
import com.muyao.wanzaihyperos.ui.design.OdometerText
import com.muyao.wanzaihyperos.ui.design.retrievalGlow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.data.schedule.PeriodClock
import com.muyao.wanzaihyperos.data.schedule.TodayPlan
import com.muyao.wanzaihyperos.data.schedule.JwVerdict
import com.muyao.wanzaihyperos.data.schedule.jwVerdictOf
import com.muyao.wanzaihyperos.data.schedule.pickSlots
import com.muyao.wanzaihyperos.data.szty.SztyRun
import com.muyao.wanzaihyperos.data.szty.SztySnapshot
import com.muyao.wanzaihyperos.data.todo.TodoBoard
import com.muyao.wanzaihyperos.data.todo.TodoNext
import com.muyao.wanzaihyperos.data.tronclass.TronRollcall
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.ui.common.kmText
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.ContainerStyle
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.LocalContainerStyle
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.glass.BarMaterial
import com.muyao.wanzaihyperos.ui.design.glass.GlassBackdropHost
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurface
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurfaceColor
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurfaceStyle
import com.muyao.wanzaihyperos.ui.design.glass.cardFaceAlpha
import com.muyao.wanzaihyperos.ui.design.glass.rememberEffectiveMaterial
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive
import com.muyao.wanzaihyperos.ui.design.liquid.LocalPageChrome
import com.muyao.wanzaihyperos.ui.design.liquid.iosHeaderInset
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import com.muyao.wanzaihyperos.ui.home.HomeSky
import com.muyao.wanzaihyperos.ui.home.SkyLook
import com.muyao.wanzaihyperos.ui.home.SkyPreset
import com.muyao.wanzaihyperos.ui.home.TileGlyph
import com.muyao.wanzaihyperos.ui.home.TileIcon
import com.muyao.wanzaihyperos.ui.home.cardBackdrops
import com.muyao.wanzaihyperos.ui.home.cardInkWorstCase
import com.muyao.wanzaihyperos.ui.home.decodeWallpaper
import com.muyao.wanzaihyperos.ui.home.WallpaperStats
import com.muyao.wanzaihyperos.ui.home.faceOverSky
import com.muyao.wanzaihyperos.ui.home.TEXT_BACKING_ALPHA
import com.muyao.wanzaihyperos.ui.home.textBackingOver
import com.muyao.wanzaihyperos.ui.home.photoBackdrops
import com.muyao.wanzaihyperos.ui.home.readableAccent
import com.muyao.wanzaihyperos.ui.home.skyLookOf
import com.muyao.wanzaihyperos.ui.home.wallpaperFile
import com.muyao.wanzaihyperos.ui.home.wallpaperStats
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 首頁（v2）：一片**真的天空** + 壓在上面的玻璃「此刻」卡 + 今天的時間線 + 快捷區。
 *
 * ## 這一版跟上一版的差別（也是為什麼整頁重寫）
 *
 * 上一版是「大日期 + 視窗 + 一疊卡片 + 磁貼網格」：視窗只是一張會動的圖，
 * 玻璃卡片採的是 `LocalGlassBackdrop`（恆 null）⇒ 一直是實心。
 * 業主說「保留液態玻璃，但要有優雅的壁紙」，所以壁紙不能只當一張圖：
 * 它必須**是被採樣的那一層**。這裡用 `GlassBackdropHost` 把 [HomeSky] 錄進
 * 一份 `LayerBackdrop`，玻璃卡是它的兄弟節點 —— 採到的是天空，不是自己。
 *
 * ## 今日課程為什麼還是同一份資料
 *
 * 教务那份一旦有货就只用它，学在城院只在教务**已经有结论且没货**时顶上 ——
 * 这一条判断全工程只有 `data/schedule/SlotSource.kt` 一份（首页、课表页两处、外观预览都调它）。
 * 教务还在爬时首页两份都不画，副行写「正在取得课表…」。
 * 周次仍走 `UiPrefs.noteWeek`，和课表页同一个入口 —— 两处不能各算各的。
 * 没有资料时**不写「今天没有课」**：那是在替学校服务器撒谎。
 */
@Composable
fun HomeSectionPage(
    graph: AppGraph,
    onOpenJw: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenMessages: () -> Unit,
    /** 首頁「簽到」磁貼：**點一下直接進**簽到頁（掃碼/數字/自助）。 */
    onOpenRollcall: () -> Unit = {},
    /** 「外觀」：壁紙、柔化、暗度、玻璃、首頁模組。 */
    onOpenAppearance: () -> Unit = {},
    /** 「校園跑」卡：點一下進數智體育整頁（那也是接 token 的閘門頁）。 */
    onOpenSzty: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current

    val rollcallState by graph.rollcall.state.collectAsState()
    val todoState by graph.todoBoard.state.collectAsState()
    val scheduleState by graph.schedule.state.collectAsState()
    val jwTimetableState by graph.jwTimetable.state.collectAsState()
    val sztyState by graph.szty.state.collectAsState()
    // 在途取数计数（第 0 期「球场」要它说「正在处理」）。壳里每开一笔 fetch 就 +1。
    val inFlight by graph.inFlight.collectAsState()

    // 全部待办拍平成一串，交给 AiBallStates.hasUrgentTodo 判「24 小时内有没有到期」。
    // 取不到（首屏 / 失败）时是空串 —— 那本来就**不知道有没有**，不该被说成「没有急事」；
    // 但这条只影响 Focus 这张脸，而 loading/failure 的优先级都在它前面，所以不会撒谎。
    val allTodos = (todoState as? ListState.Content<TodoBoard>)?.items.orEmpty().flatMap { it.groups }.flatMap { it.items }

    // 每分鐘對一次時鐘：FocusCard 的「此刻」與時間線的「現在」都要跟著走。
    // 只是一個整數狀態，成本遠低於讓整頁每秒重組。
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }

    // ── 壁紙 ──────────────────────────────────────────────────────
    // 版本號一變就重讀檔案：外觀頁換圖/刪圖之後，首頁不能還畫著舊位圖。
    val wallpaper = remember(UiPrefs.wallpaperRev, UiPrefs.wallpaperPreset) {
        if (UiPrefs.wallpaperPreset == SkyPreset.ALBUM.id) {
            decodeWallpaper(wallpaperFile(context))
        } else {
            null
        }
    }
    // 相册档：**量一遍照片有多亮**（卡所在那一段 + 整张）。以前这里用 {黑, 白} 两端兜"未知"，
    // 字色判据扩到三档之后会把垫色抬到 ≈0.9 ⇒ 液态与磨砂在卡上逐像素相同（实测 MAD 0.000）。
    val photoStats = remember(wallpaper) { wallpaper?.let { wallpaperStats(it) } }
    val requestedPreset = SkyPreset.of(UiPrefs.wallpaperPreset)
    // 相冊圖不在了（被清理/損壞）就退回自動天空，不顯示一塊破圖。
    val preset = if (requestedPreset == SkyPreset.ALBUM && wallpaper == null) {
        SkyPreset.AUTO
    } else {
        requestedPreset
    }
    // ★ 2026-11-07：容器背景 = 「只留最外层」时，这一屏身后**没有天光**。
    //
    // 真机踩到（`[local]\y_home_bare_s.png`）：切到 bare 之后整片纸底透上来，
    // 而上面那行「鲸集」、大日期「今天」、「周六 11 月 7 日」、状态行、焦点卡字色
    // 全都还在用 `look.ink` —— 相册档与夜航档那一支是**纯白**，
    // 于是白字压白纸：整屏上半段几乎读不出来。
    //
    // 判据：bare 时身后真正会出现的底色就是根上那层整页渐变（`AppColors.pageBrush`），
    // 也就是 `colors.background`。所以造一版「纸面天空」交给下游：
    // 三段色全取纸色（`cardBackdrops` 会据此喂出一整排纸色，焦点卡按它算字色），
    // 墨色取主题的 `onBackground`，状态栏图标跟随主题明暗。
    val bare = LocalContainerStyle.current == ContainerStyle.Bare
    val look = if (bare) {
        SkyLook(
            top = colors.background,
            bottom = colors.background,
            mid = colors.background,
            ridgeFar = Color.Transparent,
            ridgeNear = Color.Transparent,
            ink = colors.onBackground,
            lightIcons = colors.background.luminance() < 0.5f,
        )
    } else {
        skyLookOf(preset, now.hour)
    }
    LightStatusBarIcons(light = look.lightIcons)

    // ── 顶栏接线 ──────────────────────────────────────────────────
    //
    // 首页不画列表大标题（大日期就是标题），所以不走 `rememberPageChromePilot`：
    // 那个实现要量标题高度，这里没有标题可量。折叠度改按**滚动距离**算，
    // 120dp 走完：大日期沉到栏下，顶栏接手「首页」小标题与纱。
    // 只让在场那一页写 progress —— 切换过渡期间离场页还在组合里。
    val listState = rememberLazyListState()
    // 下拉刷新：首页四份数据（签到 / 待办 / 课表 / 未读）一次重取。
    // 走仓库自己的 refresh —— 它们都是「有内容就原地保留」，不会把卡片闪成骨架。
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 下拉量（px）。业主 2026-11-01「壁纸下拉过后就是一坨白色」—— 天空层要跟着这个数长高。
    // 用 miuix 自己的 state，不自己截 nestedScroll：`dragOffset` 与内容真实位移同源。
    val pullState = rememberPullToRefreshState()
    val chrome = LocalPageChrome.current
    val pageActive = LocalPageActive.current
    // ★ 第 9 轮审计必修 4：把"列表正在滚"报给玻璃层（滚动期降采样；见 `PageChrome.scrolling`）。
    //   只报滚动状态、不动进度 —— 进度那支仍然只由在场页写。
    val scrolling = listState.isScrollInProgress
    androidx.compose.runtime.SideEffect { chrome.onScroll(scrolling) }
    val collapsePx = with(LocalDensity.current) { 120.dp.toPx() }
    LaunchedEffect(listState, pageActive, collapsePx) {
        if (!pageActive) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val travelled = if (index > 0) collapsePx else offset.toFloat()
            chrome.progress = (travelled / collapsePx).coerceIn(0f, 1f)
        }
    }

    // 「重新读取全部来源」：与首屏那一发同一批来源。抽成 lambda 是为了两处永远同步 ——
    // 以后加了新来源，只改这里一处，首屏与按钮都不会漏。只重取，不动偏好与登录态。
    val refreshAll: () -> Unit = {
        graph.fetch { graph.rollcall.refresh() }
        graph.fetch { graph.todoBoard.refresh() }
        graph.fetch { graph.schedule.refresh() }
        graph.fetch { graph.szty.refresh() }
        if (graph.jwEngine.isAttached) {
            graph.fetch { graph.jwTimetable.refresh() }
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    // ── 今天要用的那份課表 ────────────────────────────────────────
    //
    // ★ 走 `pickSlots`（v3 #4）。这里以前手抄了一遍"教务优先、没货退学在城院"，
    //   而**没有 WAIT 这一支** ⇒ 教务还在爬的那几秒，首页直接把学在城院那份
    //   当成今天的课画出来 —— 业主报了三次的「闪出没的课」就是这个闪。
    //   `SlotSourceTest` 里那条源码扫描钉住这件事不许再退回去。
    val tron = (scheduleState as? ListState.Content)?.items?.firstOrNull()
    val jwHas = (jwTimetableState as? ListState.Content)?.items?.firstOrNull()?.slots?.isNotEmpty() == true
    val slotPick = pickSlots(
        jwSlots = (jwTimetableState as? ListState.Content)?.items?.firstOrNull()?.slots.orEmpty(),
        // ★ 首页不是爬取的发起方：只有真在爬才叫 IN_FLIGHT，"从没取过"要另说一句实话（第 3 轮必修 1/2）。
        jwVerdict = jwVerdictOf(
            state = jwTimetableState,
            crawling = graph.jwTimetable.isCrawling.value,
            hasSlots = jwHas,
        ),
        tronSlots = tron?.slots.orEmpty(),
    )
    val daySlots = slotPick.slots
    val week = UiPrefs.noteWeek(
        (tron?.currentWeek as? com.muyao.wanzaihyperos.data.CountValue.Known)?.value,
        now.toLocalDate().toEpochDay(),
    )
    val times = UiPrefs.periodTimes
    val plan = if (daySlots.isNotEmpty()) {
        TodayPlan.of(slots = daySlots, week = week, now = now, times = times)
    } else {
        null
    }
    val todaySlots = remember(daySlots, week, now.toLocalDate()) {
        if (week == null) {
            emptyList()
        } else {
            daySlots
                .filter { it.dayOfWeek == now.dayOfWeek.value && it.activeIn(week) }
                .sortedBy { it.periodStart }
        }
    }
    val openRollcall = (rollcallState as? ListState.Content)
        ?.items?.firstOrNull { !it.expired }

    // 「此刻」卡和時間線共用同一組状态 —— 两处各判一次，迟早一处说「正在取」、
    // 另一处说「今天没有课」，而后者是在替学校服务器撒谎。
    val loading = daySlots.isEmpty() &&
        (slotPick.isWaiting || scheduleState is ListState.FirstLoad)
    // ★ 卡沿那圈流光只在「真有一笔请求在路上」时亮，而这件事必须是正面证据：
    //   教务那趟由 jwVerdictOf 认（它已经把 isCrawling 与 meta.refreshing 合起来了），
    //   学在城院那一发看自己的 meta.refreshing。
    //   不拿上面那个 loading 当判据 —— 它是「这一屏画不出课」的集合，里面还含「取不到」
    //   与「压根没人发起过」；把后两者画成「正在取」，就是界面在说数据证明不了的话
    //   （见 data/schedule/SlotSource.kt 开头那条纪律）。
    val fetchingSchedule = slotPick.jwVerdict == JwVerdict.IN_FLIGHT ||
        scheduleState.metaOrNull?.refreshing == true
    // ★ 第 3 轮审计必修 3：这一句以前只看"哪一份失败了"，不看**这一屏到底有没有画出课**。
    //   教务 Failed、学在城院有货时，大标题下写「课表没取到，去课表页看看」，
    //   而同一屏下面的「此刻」卡照样画出他下一节课 —— 自己跟自己矛盾。
    //   措辞里也点名是哪一份没取到（另一份就在下面画着，不点名就是含糊其辞）。
    val failure = when (val s = scheduleState) {
        is ListState.Failed -> "学在城院那份没取到：" + s.error.userText
        ListState.SessionExpired -> "学在城院的登录已失效，去设置里重新登录一次。"
        else -> null
    } ?: when (val s = jwTimetableState) {
        is ListState.Failed -> "教务那份没取到：" + s.error.userText
        // 首页不挂教务的隐藏 WebView（见课表页），所以教务登录失效只能提示去那边。
        ListState.SessionExpired -> "教务登录已失效，去课表页登录一次就会同步。"
        else -> null
    }
    // 画得出课就不说"没取到"：同一屏不能一边说没取到、一边画出他下一节课。
    val shownFailure = if (daySlots.isEmpty()) failure else null
    // 今天真没课时，时间线卡能说的只有「今天没有课」和「下一节：周一…」——
    // 这两句「此刻」卡已经说过了（2026-10-31 真机 dump 里两处逐字相同）。
    // 同一屏讲两遍同一件事，读起来像两个不同的结论；整周入口还在「看课表」和底栏课表页上。
    val timelineHasNothingToSay = !loading && shownFailure == null &&
        week != null && todaySlots.isEmpty()

    GlassBackdropHost(
        modifier = modifier.fillMaxSize(),
        content = {
            // 板 `.atmos{height:220px}` × 0.886 ⇒ **195dp**。
            // ★ 这句以前写的是「浅色列**不做渐变收口**…硬切」—— **与实物相反**（第 6 轮必修 5）：
            //   浅色档现在既有 `fadeStart = skyBottom − 0.10h` 那段 fade，也有 24dp+4px 的底边收口
            //   （`HomeSky.kt:299` / `:441-450`）。板上确实只有 dark 那一支铺 ::after，
            //   但我们这一版是照第 2/3 轮审计的接缝判据（切线 |ΔL| ≤0.12）加的，不是照板加的。
            //   板上 `body.dark .atmos::after` 才铺一层
            // 到 --phone 的渐变，浅色那版是**硬切**，切线正好藏在「此刻」卡的
            // 上半身里（`.focus-wrap{margin-top:-40px}` ⇒ 卡往上压 35dp）。
            // 山棱带（99dp）铺在切线上方，所以那条边读起来是「地平线」而不是一刀。
            // 256dp 而不是板换算的 195dp。两个理由叠在一起：
            //  ①（板 `.focus-wrap{margin-top:-40px}`）`HomeSky` 内部只画到 `skyBottom = 0.80h`，
            //    要让天空真正的底边落在「此刻」卡（顶边 ≈167dp）之上，卡的玻璃底下才采得到天空
            //    —— 没有这段重叠，垫的是纸面，`surfaceAlpha` 再对也没用
            //    （复评 2026-10-21：「卡的玻璃底下必须是可采样的天空」）。
            //  ②（业主 2026-10-25「首页壁纸的范围可以稍微靠下一点点，大概是卡片的上三分之一吧」）
            //    真机 560dpi 实测：卡顶 y=505、卡高 586 ⇒ 上三分之一 = y≈700。
            //    天空 alpha 收 0 的行 = `skyFadeStart + 0.9167 × band` = `0.78h`
            //    （`0.9167` 来自 featherStops 的透明支在 t=0.88 处收 0，即第 11/12 站）。
            //    250dp 时它 = 0.78×875 = **682px**；真机逐列剖面 x=20 到 y≈680 就完全淡成纸色，
            //    对得上 —— 只压到卡高的 29%。
            //    解 0.78h = 700 ⇒ h ≈ 897px ⇒ 当时取 256dp（896px），实际收 0 落 698px。
            // ★ 业主 2026-11-01「壁纸的显示范围也可以大一点」⇒ 基线抬到 **288dp**（1008px）：
            //   alpha 收 0 的行 = `0.78h` ≈ **785px**，「此刻」卡顶边 ≈737px ⇒ 卡的上半身仍压在
            //   天空里（卡的玻璃采得到照片），照片可见面积比 256dp 多 112px。
            //   三个比例 `0.80 / 0.24 / 0.9167` 一个没动 —— 改的只是画布高度。
            // 「今日课程」开着时用 288dp；关着时这一屏没有那张卡，切线会从「要处理」那行中间            // 穿过去（复评 2026-10-21 量的就是这个），所以天空收到 hero 自己的底缘。
            // 业主 2026-11-01 第 2 条：「壁纸做成实时模糊」+「壁纸的显示范围也可以大一点」+
            // 「下拉过后就是一坨白色」。三件事在这里一起落：
            //  ① 基线 256dp → **288dp**（1008px）：照片多露 112px，淡出带整体下移。
            //  ② 下拉时盒高跟着 `dragOffset` 一起长，照片底边永远压在「此刻」卡的上面 ——
            //     纸面不再从照片底下翻出来。盒变高不会拉伸照片：`drawCoverBitmap` 的 scale
            //     由**宽度**定（1216/3024 = 0.402 远大于 1008/4032 = 0.25），加高只是多露下半部。
            //  ③ 同一份下拉量驱动一层实时模糊：静止时半径 0，`Modifier.blur` 在 0dp 上不建 RenderEffect
            //     ⇒ 不占 GPU；拉起来才模糊，松手自己回 0。
            val skyBaseHeight = if ("courses" in UiPrefs.homeModules) 288.dp else 150.dp
            // `0.80` 是 `skyBottomY` 的比例：画布长高 1px，照片底边只下移 0.8px，
            // 所以补偿内容那一段位移要除以它（下拉 1px ⇒ 盒高 1/0.8 = 1.25px）。
            val pullExtra = with(LocalDensity.current) {
                (pullState.dragOffset * PULL_SKY_GAIN).toDp()
            }.coerceIn(0.dp, PULL_SKY_MAX_EXTRA)
            val skyBlur = pullExtra * PULL_BLUR_PER_DP
            // 天空画一层还是两层：静止时只画一层（与改前逐像素相同）；
            // 拉起来时**底下先铺一层不模糊的**，再把模糊那层压在上面。
            // 理由（2026-11-01 真机实测）：模糊会把层的边缘抹成半透明，透出身后那层纸色
            // （峰值帧左边 0..44px 亮度 150→81 的那圈白边、顶部整条 250 白都是它）。
            // 铺一层不模糊的底，抹开的边露出来就是天空自己，边缘就没了；界面静止时这层不画。
            val drawSky: @Composable (Modifier) -> Unit = { m ->
                HomeSky(
                    preset = preset,
                    wallpaper = wallpaper,
                    dim = UiPrefs.wallpaperDim,
                    soft = UiPrefs.wallpaperSoft,
                    // v3 #10：相册那张的取景（内置天空用不上，函数里只在 ALBUM 分支生效）
                    panX = UiPrefs.wallpaperPanX,
                    panY = UiPrefs.wallpaperPanY,
                    zoom = UiPrefs.wallpaperZoom,
                    // 定稿板的天光只铺首屏上半段（到「此刻」卡底边），
                    // 下面「今天 / 要处理」落在暖米纸面上，不是整页都压着蓝天。
                    modifier = m,
                )
            }
            // 容器背景两版：`Designed` 画页内天光；`Bare` 不画，把根上那层整页渐变直接透上来。
            // 注意只撤「天光这一层」，球、大日期、焦点卡一个都不动 —— 那是他要的上半屏。
            val showSky = LocalContainerStyle.current == ContainerStyle.Designed
            if (showSky) {
                Box(Modifier.fillMaxWidth().height(skyBaseHeight + pullExtra)) {
                    drawSky(Modifier.fillMaxSize())
                    if (skyBlur > 0.dp) {
                        drawSky(Modifier.fillMaxSize().blur(skyBlur, BlurredEdgeTreatment.Unbounded))
                    }
                    if (colors.background.luminance() < 0.5f) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(67.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        // 板 ::after：rgba(16,18,19,0) 0% → .55 46% → --phone 100%
                                        0f to Color.Transparent,
                                        0.46f to colors.background.copy(alpha = 0.55f),
                                        1f to colors.background,
                                    ),
                                ),
                        )
                    }
                }
            }
        },
    ) { skyBackdrop ->
        val compact = UiPrefs.homeDensity == "compact"
        // 「此刻」卡和副标题共用同一次推导：正在上的课 / 下一节 / 还要上几节。
        val focus = remember(todaySlots, now, times) {
            focusNowOf(todaySlots, now.toLocalTime(), times)
        }

        // ── 「球场」那颗球（第 0 期：从卡里 36dp 挪到标题区，放大到 96dp）──────────
        // 四个新事实各自都有出处，**没有一个来自猜测**：
        //  · waitingRollcall：学在城院真有一张还没答的二维码签到（TronRollcall:117/:120）。
        //    它有时限，所以优先级排在「正在读」前面 —— 读完还能补，签到过期就没了。
        //  · urgentTodo：24 小时内到期、且**真的解析出截止时刻**的待办
        //    （TodoBoard.groups[].items[].sortTimeMillis，null 的不算）。
        //  · hour：now 已经是每 60s 自刷的那个时钟，不另取一次。
        //  · fetching：graph.inFlight 的真实在途计数，不是「定时器在跑」。
        val ballState = AiBallStates.ofHome(
            plan = plan,
            loading = loading,
            // 用 shownFailure 而不是 failure：与卡内那句大标题、下面时间线的口径同源。
            failure = shownFailure,
            ongoing = focus.ongoing,
            hour = now.hour,
            waitingRollcall = openRollcall?.let { it.unanswered && it.needsCamera } == true,
            urgentTodo = AiBallStates.hasUrgentTodo(
                items = allTodos,
                nowMillis = System.currentTimeMillis(),
            ),
            fetching = inFlight > 0,
            // 在调用点就地算：这段统计在上面已经算过一遍，但 ballState 在这之前就要用。
            minutesToNextClass = todaySlots
                .mapNotNull { parseClock(PeriodClock.startOf(times, it.periodStart)) }
                .firstOrNull { it.isAfter(now.toLocalTime()) }
                ?.let { java.time.Duration.between(now.toLocalTime(), it).toMinutes().toInt() }
                ?.takeIf { it >= 0 },
        )

        val nowTime = now.toLocalTime()
        val remainingCount = todaySlots.count { slot ->
            val end = parseClock(PeriodClock.endOf(times, slot.periodEnd))
            end == null || nowTime.isBefore(end)
        }
        val nextStart = todaySlots
            .mapNotNull { parseClock(PeriodClock.startOf(times, it.periodStart)) }
            .firstOrNull { it.isAfter(nowTime) }
        // 课间那张脸的判据（B9）在 ofHome 调用点就地算，这里量的是同一份 nextStart。

        Box(Modifier.fillMaxSize()) {
        // 下拉刷新：四份数据各发一发，谁失败都不影响其他（runCatching 各自兜住）。
        // contentPadding 只挪下拉指示器，不给内容加 padding —— 内容 inset 仍由 LazyColumn 自己消费。
        PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    try {
                        // 教务那份要那只隐藏 WebView 在才发（同首刷的判据）；没挂载就不发，别白失败。
                        if (graph.jwEngine.isAttached) { runCatching { graph.jwTimetable.refresh() } }
                        runCatching { graph.schedule.refresh() }
                        runCatching { graph.todoBoard.refresh() }
                        runCatching { graph.rollcall.refresh() }
                        runCatching { graph.szty.refresh() }
                        runCatching { graph.unread.refresh(force = true) }
                    } finally {
                        refreshing = false
                    }
                }
            },
            pullToRefreshState = pullState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBarTopPadding()),
            refreshTexts = listOf("下拉刷新", "松开手就刷", "正在刷新…", "刷完了"),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dims.screenH,
                    end = Dims.screenH,
                    // 顶栏是悬浮的：大日期从它下面开始，不能压在搜索钮上。
                    top = iosHeaderInset() + 2.dp,
                    bottom = pageBottomInsetDp(LocalNavBarVisible.current) + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    if (compact) Dims.cardGap - 4.dp else Dims.cardGap,
                ),
            ) {
                item(key = "hero") {
                    HomeSkyHeader(
                        now = now,
                        look = look,
                        remainingCount = remainingCount,
                        todayCount = todaySlots.size,
                        nextStart = nextStart,
                        loading = loading,
                        compact = compact,
                        failure = shownFailure,
                        slotSource = slotPick.source,
                        jwVerdict = slotPick.jwVerdict,
                        jwIsStale = (jwTimetableState as? ListState.Content)?.meta?.stale == true,
                        ballState = ballState,
                    )
                }

                if ("courses" in UiPrefs.homeModules) {
                    item(key = "focus") {
                        FocusGlassCard(
                            backdrop = skyBackdrop,
                            plan = plan,
                            focus = focus,
                            loading = loading,
                            fetching = fetchingSchedule,
                            // 同一条规矩：这一屏画得出课就不说「没取到」（三张卡共用 shownFailure）。
                            shownFailure = shownFailure,
                            week = week,
                            now = now,
                            // 字色按**卡面实际铺出来的亮度**算（天空 + 这一档的垫色），
                            // 不再是"主题墨色 or 天空墨色"二选一 —— 见 `FocusGlassCard` 上面那段。
                            look = look,
                            skyIsPhoto = preset == SkyPreset.ALBUM,
                            photoStats = if (preset == SkyPreset.ALBUM) photoStats else null,
                            openRollcall = openRollcall,
                            onClick = onOpenTimetable,
                            onOpenRollcall = onOpenRollcall,
                        )
                    }

                    if (!timelineHasNothingToSay) item(key = "timeline") {
                        TodayTimelineCard(
                            slots = todaySlots,
                            week = week,
                            now = now,
                            times = times,
                            loading = loading,
                            // 同一条规矩：这一屏画得出课就不说「没取到」（三张卡共用 shownFailure）。
                            failure = shownFailure,
                            onOpenTimetable = onOpenTimetable,
                        )
                    }
                }

                // 有课板块时「去签到」已经在「此刻」卡里，不再重复一张横幅；
                // 只有他把「今日课程」模块关掉时，这里才单独兜住签到入口。
                // 稿 `home` 在焦点卡下面单独有一格「课堂签到」+「进入」+ 一句说明。
                // 原来这里只在「今日课程」模块关掉时才出现（当时怕和「此刻」卡里的签到重复）。
                // 但两件事不是一件事：卡里那是「下一节课」，这里是「现在有没有要签的到」。
                // 所以改成只要开着签到模块就有这一格；没有进行中的签到时如实说没有。
                if ("rollcall" in UiPrefs.homeModules) {
                    item(key = "rollcall-open") {
                        RollcallSection(
                            open = openRollcall,
                            onOpen = onOpenRollcall,
                        )
                    }
                }

                if ("todos" in UiPrefs.homeModules) {
                    item(key = "todos") {
                        TodoSummaryCard(
                            state = todoState,
                            // 本页一进来就发了这一发（见上面那个 LaunchedEffect），所以：
                            // 还没有任何结论 + 壳里真有一笔在途 ⇒ 这一栏确实在等。
                            fetching = todoState is ListState.FirstLoad && inFlight > 0,
                            onClick = onOpenTodos,
                        )
                    }
                }

                if ("szty" in UiPrefs.homeModules) {
                    item(key = "szty") {
                        RunSummaryCard(
                            state = sztyState,
                            fetching = sztyState is ListState.FirstLoad && inFlight > 0,
                            onClick = onOpenSzty,
                        )
                    }
                }

                // 板子：课表 / 消息 / 应用交给底栏，首页不再重复一排快捷入口。
                // 签到入口在「此刻」卡里（有签到才出现），待办在「要处理」里。

                // 稿 `home` 页尾两个按钮：先给「去看消息」，再给「重取一遍」。
                // 第二个不是下拉刷新的替代品 —— 下拉刷的是这一页，这个是四个来源都重取，
                // 因为首页最常出的问题是「某一份没取到」，而那一份未必归这张卡管。
                item(key = "bottom-actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dims.cardGap),
                    ) {
                        BottomActionButton(
                            text = "查看消息与通知",
                            onClick = onOpenMessages,
                            modifier = Modifier.weight(1f),
                        )
                        BottomActionButton(
                            text = "重新读取全部来源",
                            onClick = refreshAll,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item(key = "tail") { Spacer(Modifier.height(8.dp)) }
            }
        }

        // 顶栏那一行：左「鲸集」，右「第 N 周」（搜索钮由顶栏自己画在最右）。
        // 叠在内容之上、顶栏之下；滚动时和顶栏纱一起退场，不会和折叠后的小标题打架。
        val headerAlpha = (1f - chrome.progress * 2.2f).coerceIn(0f, 1f)
        if (headerAlpha > 0.01f) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = Dims.screenH, end = 66.dp, top = statusBarTopPadding())
                    .height(52.dp)
                    .alpha(headerAlpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "鲸集",
                    fontSize = Type.caption,
                    color = look.ink.copy(alpha = 0.82f),
                )
                Spacer(Modifier.weight(1f))
                if (week != null) {
                    Text(
                        text = "第 $week 周",
                        style = TextStyle(fontFeatureSettings = Type.tabular),
                        fontSize = Type.micro,
                        color = look.ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dims.chipCorner))
                            .background(look.ink.copy(alpha = 0.10f))
                            .clickable(onClickLabel = "打开课表") { onOpenTimetable() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        }
    }
}

/**
 * 深色天空上**狀態欄圖標必須變白**。
 *
 * 這不是配色偏好：`enableEdgeToEdge()` 依主題決定圖標明暗，而夜航/暮色/相冊
 * 的天空比深色主題還深 —— 淺色主題下就是「黑圖標壓在黑天空上」。
 * 只在本頁活著時改，離場還原成它進來前的值（通常是主題給的那一份）。
 */
@Composable
private fun LightStatusBarIcons(light: Boolean) {
    val context = LocalContext.current
    val window = (context as? Activity)?.window ?: return
    DisposableEffect(light, window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = !light
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/** 「今天」大标题 + 星期/日期 + 一句真实的课程状态（不写问候语、不写每日一句）。 */
@Composable
private fun HomeSkyHeader(
    now: LocalDateTime,
    look: SkyLook,
    remainingCount: Int,
    /** 今天一共有几节课（不是「还剩几节」）。0 与「都上完了」是两件事，副行必须分开说。 */
    todayCount: Int,
    nextStart: LocalTime?,
    loading: Boolean,
    failure: String?,
    compact: Boolean,
    /** 这一屏画的究竟是哪一份 —— 副行那句「来自学在城院」只能由它决定（审计 v3 #4 第 2 轮第 7 条）。 */
    slotSource: com.muyao.wanzaihyperos.data.schedule.SlotSource,
    /** 教务走到哪一步（`JwVerdict.name`）—— 退到另一份时那句实话要用它。 */
    jwVerdict: com.muyao.wanzaihyperos.data.schedule.JwVerdict =
        com.muyao.wanzaihyperos.data.schedule.JwVerdict.NOT_STARTED,
    /** 画的是教务那份、但它是**上次存的**（首页自己不重取，N3）。 */
    jwIsStale: Boolean = false,
    /** 标题区左侧那颗 96dp 的球要画哪张脸（见 `AiBallStates.ofHome`）。 */
    ballState: com.muyao.wanzaihyperos.ui.design.ai.AiBallState,
) {
    val weekLabel = WEEKDAYS.getOrElse(now.dayOfWeek.value - 1) { "" }
    val ink = look.ink
    val subline = when {
        loading -> "正在取得课表…"
        // ★ 第 6 轮 §4②：以前这里固定说「课表没取到，去课表页看看」，
        //   而下面算出来的 `failure` 已经带了名分（"学在城院那份没取到：…" / "教务那份…"），
        //   却一个字都没上屏 ⇒ 那句注释是假的。现在真把带名分的那句画出来。
        failure != null -> failure
        // ★ 2026-10-31 真机：周六（今天一节没有）副行写「今天的课上完了」——
        //   remainingCount 为 0 既可能是「全上完了」也可能是「今天压根没课」，
        //   这两件事对读的人完全不同：前者值得恭喜，后者是一句空话。分开说。
        // ★ 返工总单 B1（语言层）：第三行是**球的话**，不是又一句课表统计。
        //   稿 `docs/B4-12-BALL-AS-CORE.md` §4：「脸说情绪，字说事实，字不许重复脸说过的话」。
        //   所以这里写的是把那张脸反译成人话（`AiBall.lineOf`），与读屏念的同一句。
        //   课表统计并没有丢：下一节几点、还剩几节都在下面那张「此刻」卡里。
        else -> com.muyao.wanzaihyperos.ui.design.ai.AiBall.lineOf(ballState)
    } + if (slotSource == com.muyao.wanzaihyperos.data.schedule.SlotSource.TRONCLASS) {
        // 来源标注仍然留着：它不是重复表情，而是在说「这份数据来自哪」，属事实补充。
        " · 来自学在城院（" +
            com.muyao.wanzaihyperos.data.schedule.jwVerdictSentence(jwVerdict)
                .removeSuffix("…") + "）"
    } else if (slotSource == com.muyao.wanzaihyperos.data.schedule.SlotSource.JIAOWU && jwIsStale) {
        " · 这是上次存的"
    } else {
        ""
    }
    // ★ 尾部那句来源标注不是装饰：教务那份还没爬到（首页**不会**自己去爬，
    //   登录要走课表页藏着的 WebView）时，这里画的是学在城院那一份。
    //   独立审计 v3 #4 抓到的正是"首页一直画另一份却不说话"。

    // 第 0 期「球场」：球在左、三行字在右（docs/B4-12-BALL-AS-CORE.md §2）。
    // 为什么放这里而不是卡里：这颗球现在**先说话**——它是这一屏的情绪，标题与副行才是补充；
    // 埋在「此刻」卡右下角时它只是装饰，而且被卡面压住了对比度（球体 vs 卡面只有 1.05:1，
    // 全靠 1dp 环才看得见）。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 96dp 的球自己带 6dp 透明命中区（外侧），所以左边再补 6dp 让**看得见的**
        // 球面左沿与下面各卡的内框线对齐，而不是被命中区顶出去 6dp。
        AiBall(
            state = ballState,
            diameter = AiBall.DIAMETER_FIELD,
            modifier = Modifier.padding(start = AiBall.HIT_SLOP),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
        // 板 `.date-line{align-items:baseline}`：今天 35px/780 · 周X 15px/650 · 日期 12px/550 @.68
        // —— 三段各有各的字号。原来把「周三 10 月 21 日」并成一个 17sp Bold，
        // 比板上重了一档半，也少了板上那种「大字带两个小字」的层次。
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "今天",
                fontSize = if (compact) 28.sp else 31.sp,
                fontWeight = Type.weightTitle,
                color = ink,
                letterSpacing = (-0.04).em,
                lineHeight = if (compact) 30.sp else 33.sp,
            )
            Text(
                text = weekLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink.copy(alpha = 0.94f),
                modifier = Modifier.padding(start = 11.dp, bottom = 3.dp),
            )
            Text(
                text = "${now.monthValue} 月 ${now.dayOfMonth} 日",
                // 清单 #11：改走令牌（`Type.caption` = 11sp，与 :535 那条注释现在说的是同一个数）。
                fontSize = Type.caption,
                fontWeight = Type.weightBody,
                // 板 .68。深色档实测：这段日期正好压在日盘软光上时 CR 只有 3.48
                // （同一段落在没有盘的地上是 5.04）⇒ 深色档提到 .80 补回可读性。
                color = ink.copy(alpha = if (look.lightIcons) 0.80f else 0.68f),
                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = subline,
            // 板 `.subline{margin-top:7px;font-size:12.5px;font-weight:550;color:rgba(27,43,40,.72)}`
            // × 0.886 ⇒ 板上 11sp；**实物走 `Type.secondary` = 13.sp**（第 6 轮必修 5：
            //   旧注释报 11sp 与代码差 2sp/17.8%，判据②当年正是靠这个偏大才过 4.5）/ .72 墨
            //
            // ★ .72 是板子压在**中调天空**上的值（实测板上副行 CR=4.83）。
            //   暮色/夜航那两档这一行落在被 fade 收淡的带上，白字 .72 实测只剩 **3.04**
            //   （独立审计 v3 #2 第 7 条：判据②「默认档文字仍可读」就是被这一条卡住的）。
            //   深色天空抬到 .88；浅色档保持板上的 .72 不动。
            fontSize = Type.secondary,
            fontWeight = Type.weightBody,
            color = ink.copy(alpha = if (look.lightIcons) 0.88f else 0.72f),
        )
    }
    }
}

/**
 * 「此刻」玻璃卡。
 *
 * 它是整個 P0.5 的驗收物：背後必須是**真的天空採樣層**。
 * `GlassBackdropHost` 給的 liveness 只有在天空真的畫過之後才變 Alive，
 * 所以第一幀是實心、之後才成玻璃 —— 這正是我們要的「最壞只是不糊，不會出黑」。
 */
@Composable
private fun FocusGlassCard(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    plan: TodayPlan?,
    focus: FocusNow,
    loading: Boolean,
    /** 有一笔请求真的在路上（判据见调用点的 fetchingSchedule）。为真时卡沿绕一圈流光。 */
    fetching: Boolean,
    /** 已经过「画得出课就不说没取到」这道闸门的失败话术（调用点传 shownFailure）。 */
    shownFailure: String?,
    week: Int?,
    now: LocalDateTime,
    /** 这一屏的天空。卡面字色要从它**算**，不是从主题猜。 */
    look: SkyLook,
    /** 相册档：身后是他自己的照片 ⇒ 亮度**未知**，不许拿来推算字色。 */
    skyIsPhoto: Boolean,
    /** 相册档那张照片量出来的亮度（`wallpaperStats`）；真解不出像素时为 null ⇒ 退回两端兜。 */
    photoStats: WallpaperStats?,
    openRollcall: TronRollcall?,
    onClick: () -> Unit,
    onOpenRollcall: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    // ★ 不是 `LocalBarMaterial.current`：那是"他选了哪一档"，这是"这一面此刻**画得出**哪一档"。
    //   采样层没活 / 减弱透明度 / 刚换上来的头两帧 ⇒ 画面是实心纸，字色就得按纸判。
    //   两个数分开写就是"代码说液态、画面是纸"那类只能靠肉眼发现的错。
    val material = rememberEffectiveMaterial(backdrop)
    val isDark = colors.background.luminance() < 0.5f

    // ★ v3 #12 的真根因（业主真机逐像素量到的那一轴）：
    //   这张卡的垫色以前写死「浅色 0.94 / 深色 0.30」两个常数 —— **与档位无关**。
    //   链路其实一通百通（SOLID ⇒ 不采样、FROSTED ⇒ 折射归零、LIQUID ⇒ 全开），
    //   可 0.94 的白漆已经把身后的天空糊死了：模糊与折射只剩 6% 的像素可动，
    //   于是真机上「磨砂 vs 液态 = 卡带 0.000 / 0px」—— "接线正确、画面不动"就是这么来的。
    //   ⇒ 档位必须改**画面上那个数**（四个值逐条取自定稿板，见 `cardFaceAlpha`）。
    val wantAlpha = cardFaceAlpha(material, isDark)
    // ★ 卡身后**真的**会出现的那几层底（第 7 轮审计判死了"取带内最亮档"那条：
    //   卡顶 ≈167dp 而天空只到 200dp ⇒ 八成高度压在已经淡成纸色的那一段，我采的那一段不是它）。
    //   相册档 = 他自己的照片，亮度**未知** ⇒ 两端都算（黑与白），逼着垫色厚到两端都读得清；
    //   这不是保守，是"不许拿猜出来的亮度决定字色"（工程红线：没有来源就照实说不知道）。
    val backdrops = when {
        // 量到了照片亮度 ⇒ 跟内置天空一样按**真值**判（这样液态/磨砂的厚度差能保住）
        skyIsPhoto && photoStats != null -> photoBackdrops(photoStats, colors.background)
        // 真拿不到（位图读不出像素）才退回两端兜 —— 代价是三档厚度会被抬到一起，写在注释里
        skyIsPhoto -> listOf(Color.Black, Color.White)
        else -> look.cardBackdrops(colors.background)
    }
    // 卡面落进「两套板色都不合格」的明度带时**加厚垫色**，而不是硬挑一个读不清的字色。
    // ★★ B4 / #12（业主 2026-10-23 拍板）：**卡面厚度 = 档位自己的，不再为字色抬厚度**。
    //   以前这里 `readableFaceAlpha(...)` 会把液态/磨砂一起抬到同一个 alpha（真机实测：夜档与照片上
    //   两档在卡内**逐像素相同**、关闭档差 48 MAD ⇒ 三档退化成两态）。
    //   现在可读性交给**文字块下面那层局部衬底**（`textBackingOver`）：卡面照档位透，
    //   字只压在"卡面 + 衬底"那一小块上 ⇒ 两档厚度差回来了，字也照样读得清。
    val faceAlpha = wantAlpha
    val faces = backdrops.map { textBackingOver(it, colors.surfaceContainer, faceAlpha) }
    val inks = cardInkWorstCase(faces)
    val titleInk = inks.main
    val quietInk = inks.quiet
    // 那颗胶囊与倒计时环吃的是品牌绿：卡面抬到中间明度时它只有 1.46:1 ⇒ 读不清就让位给主墨
    //（审计必修 2；"品牌色优先"在这里就是那条不该有的优先级）。
    val accentOnFace = readableAccent(faces, colors.primary, titleInk)

    val slot = focus.slot
    val headline: String
    val subline: String?
    if (slot != null) {
        headline = slot.courseName.ifBlank { "（未命名课程）" }
        subline = buildList {
            if (slot.room.isNotBlank()) add(slot.room)
            if (focus.ongoing) {
                focus.end?.let { add("${it.format(TIME_HM)} 下课") }
            } else {
                focus.start?.let { add("${it.format(TIME_HM)} 上课") }
            }
        }.joinToString(" · ").ifBlank { null }
    } else {
        headline = plan?.headline ?: when {
            loading -> "正在取得课表…"
            shownFailure != null -> "取不到课表"
            else -> "还没有课表数据。"
        }
        subline = plan?.detail
    }
    // 板 `.focus-card .live` 是**状态胶囊**（上课中 / 下一节）。
    // 没有状态可说的时候不要拿「第 N 周」去填那个位置：那一行下面 meta 已经写了周次，
    // 顶栏胶囊也写着周次 —— 同一屏出现三次同一个数（复评 2026-10-21）。
    val chip = when {
        focus.ongoing -> "上课中"
        slot != null -> "下一节"
        else -> ""
    }
    val signIn = openRollcall?.let {
        if (it.endTime.isBlank()) "签到进行中" else "签到进行中 · 截止 ${it.endTime}"
    }

    GlassSurface(
        backdrop = backdrop,
        // 板 `.focus-card{box-shadow:0 18px 34px rgba(55,48,36,.14), inset 0 1px 0 rgba(255,255,255,.62)}`
        // × 0.886 ⇒ **向下 16dp、模糊 30dp、14% 暖黑**。没有这团影，卡不「浮」在天空上，
        // 读起来就是贴上去的一块浅米板（复评 2026-10-21）。
        modifier = Modifier
            .fillMaxWidth()
            .dropShadow(
                shape = RoundedCornerShape(Dims.cardCorner * 1.3f),
                shadow = androidx.compose.ui.graphics.shadow.Shadow(
                    radius = 30.dp,
                    // DpOffset，不是 IntOffset：这层 API 要的是 dp
                    offset = androidx.compose.ui.unit.DpOffset(0.dp, 16.dp),
                    color = Color(0xFF373024),
                    alpha = 0.14f,
                ),
            )
            .retrievalGlow(
                active = fetching,
                cornerRadius = Dims.cardCorner * 1.3f,
                color = colors.primary,
            ),
        cornerRadius = Dims.cardCorner * 1.3f,
        // ★ `material` 在这里**显式传**（不靠 `GlassSurface` 那个默认参数）：
        //   默认参数读的是同一个 `LocalBarMaterial`，但它让"这张卡吃哪一档"在源码里
        //   看不见 —— v3 #12 就是因为只能靠读函数签名才发现"接了但没用上"。
        //   现在 `GlassModeWiringTest` 直接扫这一处调用点。
        material = material,
        // 定稿板的此刻卡有**两个变体**：`.focus-card`（板的 `--card`，配 blur(22px)）
        // 和 `.focus-card.glass`（三段白 .42/.19/.28，配 blur(26px) saturate(1.55)）。
        // 这两档本来就画的不是一种东西 ⇒ 垫色按档位给，见上面的 `faceAlpha`。
        style = GlassSurfaceStyle.CONTAINER.copy(
            surfaceColor = GlassSurfaceColor.SURFACE_CONTAINER,
            // 与上面算 `face` / 字色用的是**同一个数**（不要再写第二份常数，
            // 那正是"字色判断和实际垫色对不上"的来源）。
            surfaceAlpha = faceAlpha,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "打开课表") { onClick() }
                .padding(Dims.cardPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "此刻 · " + now.format(TIME_HM),
                    // 板 `.eyebrow{font-size:10px;letter-spacing:.10em;font-weight:700;color:var(--sub)}`
                    fontSize = Type.caption,
                    fontWeight = Type.weightTitle,
                    letterSpacing = 0.10.em,
                    color = quietInk,
                    style = TextStyle(fontFeatureSettings = Type.tabular),
                    modifier = Modifier.weight(1f),
                )
                if (chip.isNotEmpty()) {
                    Text(
                        text = chip,
                        fontSize = Type.micro,
                        fontWeight = Type.weightTitle,
                        color = accentOnFace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dims.chipCorner))
                            .background(accentOnFace.copy(alpha = 0.11f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        // ★ B4 / #12：**字底衬底**（只在字块这一小块，外扩 8×6dp、圆角 10dp）。
                        //   卡面厚度按档位给（液态/磨砂各自），可读性靠这一层 —— 业主 2026-10-23 定的口径。
                        //   用 `drawBehind` 而不是 `background`：不改布局（不产生内边距/重测）。
                        .drawBehind {
                            val padX = 8.dp.toPx()
                            val padY = 6.dp.toPx()
                            drawRoundRect(
                                color = colors.surfaceContainer.copy(alpha = TEXT_BACKING_ALPHA),
                                topLeft = androidx.compose.ui.geometry.Offset(-padX, -padY),
                                size = androidx.compose.ui.geometry.Size(
                                    size.width + padX * 2,
                                    size.height + padY * 2,
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                            )
                        },
                ) {
                    Text(
                        text = headline,
                        // 板 `.focus-title{font-size:23px;font-weight:750;letter-spacing:-.025em;line-height:1.1}`
                        // × 0.886 ⇒ **20sp**。这是这一屏最大的一块字，原来吃 Type.title(16) 小了一档。
                        fontSize = 20.sp,
                        fontWeight = Type.weightTitle,
                        letterSpacing = (-0.025).em,
                        lineHeight = 22.sp,
                        color = titleInk,
                        maxLines = 2,
                    )
                    subline?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = it,
                            fontSize = Type.summary,
                            color = quietInk,
                            maxLines = 2,
                        )
                    }
                    signIn?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            fontSize = Type.caption,
                            fontWeight = Type.weightBody,
                            color = inks.warm,
                        )
                    }
                }
                val minutes = focus.minutes
                if (slot != null && minutes != null) {
                    Spacer(Modifier.width(12.dp))
                    val fraction = if (focus.ongoing && focus.totalMinutes != null &&
                        focus.totalMinutes > 0
                    ) {
                        minutes.toFloat() / focus.totalMinutes
                    } else {
                        1f
                    }
                    CountdownRing(
                        minutes = minutes,
                        // ★ 环只画**真的在走的那一段**：`fraction` 在「距下一节」那支是硬编码 1f，
                        //   满圈 = 板上 conic 306°+54° 余量的反面，读起来是「已经上完」。
                        //   没有分母就不画环，只留数字（复评 2026-10-22）。
                        fraction = if (focus.ongoing) fraction else -1f,
                        // 板 `.ring` 的空档是 --accent-soft（rgba(46,107,100,.11)）——
                        // 它是**几乎看不见的一圈**，所以整个环读起来是「一段带缺口的弧」。
                        // 原来给 0.18 的轨道，环就变成了一根完整的表盘，缺口没了。
                        track = accentOnFace.copy(alpha = 0.11f),
                        accent = accentOnFace,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 板子上这两颗按钮是常驻的：去签到本身就是一个真页面（扫码/数字/自助），
                // 没有「正在进行的签到」也应该能进去看。不要因为当前没签到就把它藏掉。
                // 谁是主按钮看**有没有真签到可去**：有开着的签到 ⇒「去签到」填实底；
                // 没有（比如今天的课都上完了）还让它当主按钮，就是推一个此刻无意义的动作。
                FocusButton(
                    label = "去签到",
                    filled = openRollcall != null,
                    lineInk = titleInk,
                    primary = colors.primary,
                    onFilled = colors.onPrimary,
                    onClick = onOpenRollcall,
                )
                FocusButton(
                    label = "看课表",
                    filled = openRollcall == null,
                    lineInk = titleInk,
                    primary = colors.primary,
                    onFilled = colors.onPrimary,
                    onClick = onClick,
                )
            }
        }

        // 第 0 期把球从这张卡的右下角**搬走了**（业主 2026-10-25「以表情为核心」）：
        // 它现在在标题区左侧当 96dp 的主视觉（见 HomeSkyHeader 里的球场）。
        // 不在这张卡里再画一颗 —— 一处状态只在屏幕上一个地方说，两处迟早各说各的。
    }
}

/**
 * 焦点卡右侧的倒计时环：正在上 = 剩余时间；下一节 = 距上课还有多久。
 *
 * 尺寸按板 `.ring{width:55px;height:55px}` + `::before{inset:5px}` × 0.886
 * ⇒ **49dp 外圈、4.4dp 厚**。旧的 62dp / 2.5dp 是「细线大环」，
 * 板上是「短粗的一弧」，观感差一整档。弧线端点用 Butt ——
 * conic-gradient 的扫掠端本来就是平的，Round 会把缺口填掉。
 */
@Composable
private fun CountdownRing(
    minutes: Long,
    fraction: Float,
    track: Color,
    accent: Color,
) {
    Box(Modifier.size(49.dp), contentAlignment = Alignment.Center) {
        // fraction < 0 = 没有进度可表达：退化成纯数字，不给一圈假的满环。
        if (fraction < 0f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = minutes.coerceAtMost(999).toString(),
                    fontSize = 12.sp,
                    fontWeight = Type.weightTitle,
                    letterSpacing = (-0.02).em,
                    color = accent,
                    style = TextStyle(fontFeatureSettings = Type.tabular),
                    maxLines = 1,
                    lineHeight = 13.sp,
                )
                Text(
                    text = "分钟",
                    fontSize = Type.micro,
                    color = accent.copy(alpha = 0.72f),
                    maxLines = 1,
                    lineHeight = 12.sp,   // 第 9 轮审计：#11 的地板是 10sp，9sp 的行高连字都放不下
                )
            }
            return@Box
        }
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.4.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke),
                topLeft = Offset(inset, inset),
                size = arcSize,
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke),
                topLeft = Offset(inset, inset),
                size = arcSize,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = minutes.coerceAtMost(999).toString(),
                // 板 `.ring b{font-size:14px;font-weight:750;letter-spacing:-.02em}` × 0.886 ⇒ 12sp
                fontSize = 12.sp,
                fontWeight = Type.weightTitle,
                letterSpacing = (-0.02).em,
                color = accent,
                style = TextStyle(fontFeatureSettings = Type.tabular),
                maxLines = 1,
                lineHeight = 13.sp,
            )
            Text(
                // 板 `.ring small{font-size:7.5px}` ⇒ 6.6sp，落在本工程的 floor(8sp) 之下，
                // 取 8sp（`Type.micro`）。这一格只有「分钟」两个字，不影响读表。
                text = "分钟",
                fontSize = Type.micro,
                color = accent.copy(alpha = 0.72f),
                maxLines = 1,
                lineHeight = 12.sp,   // 第 9 轮审计：#11 的地板是 10sp，9sp 的行高连字都放不下
            )
        }
    }
}

@Composable
private fun FocusButton(
    label: String,
    filled: Boolean,
    lineInk: Color,
    primary: Color,
    onFilled: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    // 板 `.btn{font-size:10.5px;font-weight:650;padding:8px 13px;border-radius:999px}`
    // × 0.886 ⇒ 9–10sp 字、11.5×7dp 内边距。
    // `.btn.ghost` 的描边是 **--line-strong**（rgba(26,25,23,.16)），
    // 不是「墨色 40%」—— 原来那条边在天空上重得像支笔勾过。
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    // 按下缩、松手过冲一点点再停（2026-11-01 抖音那条「PRESS SCALE」）。
    // 同一枚 source 必须交给下面 clickable，否则这里收不到按下 —— 也就会与它抢手势。
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = label,
        fontSize = Type.summary,
        fontWeight = Type.weightBody,
        // 板 `.btn.ghost{color:var(--sub)}` = #736E64 —— **不是墨黑**。
        // 上一版这里给 lineInk（黄昏/深色时是近白、浅色时是近黑），主/次对比被抹平：
        // 主按钮靠填色分主次，次按钮的字必须退到 --sub（复评 2026-10-22）。
        color = if (filled) onFilled else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .pressSpring(interaction)
            .clip(shape)
            .background(if (filled) primary else Color.Transparent)
            .then(
                if (filled) Modifier
                else Modifier.border(
                    1.dp,
                    if (isDark) Color(0xFFFFFFFF).copy(alpha = 0.18f)
                    else Color(0xFF1A1917).copy(alpha = 0.16f),
                    shape,
                )
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 11.5.dp, vertical = 7.dp),
    )
}

/**
 * 今天的時間線（紙面卡，不再疊一層玻璃）。
 *
 * 為什麼用紙面而不是玻璃：玻璃是**這片天空**的語彙，一屏上只留「此刻」那一塊；
 * 再疊兩三塊半透明，就會變成「發白/看不清」的老問題（材質厚度要單調）。
 *
 * 時間**只用 `PeriodClock` 算得出來的值**：學校日曆 + 他填的那份。
 * 算不出就只寫節次 —— 不編一個「10:00」出來。
 */
@Composable
private fun TodayTimelineCard(
    slots: List<TronScheduleSlot>,
    week: Int?,
    now: LocalDateTime,
    times: List<Pair<String, String>>,
    loading: Boolean,
    failure: String?,
    onOpenTimetable: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 正文前图标（业主 2026-11-07：「再写一套图标放在正文前来标识」）。
                // 分区标题前那一枚，与设置页的彩色磁贴不是同一套：这里是**线性细笔**，
                // 尺寸跟着字走（15dp），不抢标题的视觉重量。
                BodyMark(
                    icon = MarkIcon.Schedule,
                    tint = colors.primary,
                    modifier = Modifier.padding(end = 7.dp),
                )
                Text(
                    text = "今天的安排",
                    fontSize = Type.body,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (week == null) "整周" else "第 " + week + " 周",
                    fontSize = Type.caption,
                    color = colors.primary,
                    modifier = Modifier.clickable(onClickLabel = "打开课表") { onOpenTimetable() },
                )
            }
            Spacer(Modifier.height(6.dp))
            // 先判数据在不在路上/是不是取不到，再判「今天有没有课」——
            // 顺序反了就会在课表还在加载时写「今天没有课」，那是在替学校服务器撒谎。
            if (loading) {
                Text(
                    text = "正在取得课表…",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )
                return@Column
            }
            if (failure != null) {
                Text(
                    text = "课表没取到，暂时画不出今天的时间线。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )
                return@Column
            }
            if (week == null) {
                Text(
                    text = "还不知道目前是第几教学周，先看整周课表。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )
                return@Column
            }
            slots.forEachIndexed { idx, slot ->
                val start = parseClock(PeriodClock.startOf(times, slot.periodStart))
                val end = parseClock(PeriodClock.endOf(times, slot.periodEnd))
                val nowT = now.toLocalTime()
                val isPast = end != null && !nowT.isBefore(end)
                val isNow = start != null && end != null &&
                    !nowT.isBefore(start) && nowT.isBefore(end)
                TimelineRow(
                    slot = slot,
                    start = PeriodClock.startOf(times, slot.periodStart),
                    isPast = isPast,
                    isNow = isNow,
                    last = idx == slots.lastIndex,
                )
            }
    }
}

@Composable
private fun TimelineRow(
    slot: TronScheduleSlot,
    start: String?,
    isPast: Boolean,
    isNow: Boolean,
    last: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    // 板 --faint（时间列、空心点、副行都吃它）：浅 #9B9589。
    // onSurfaceVariantSummary 是 --sub(#736E64)，比板上的淡字深一档。
    val faint = if (colors.background.luminance() < 0.5f) Color(0xFF83888C) else Color(0xFF9B9589)
    val contentAlpha = if (isPast) 0.46f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 行高由内容决定：课名折两行时行会变高。时间轴必须跟着这段高度画，
            // 否则固定 38dp 的线会在每一行中间断掉，看上去像一节节碎掉的虚线。
            .height(IntrinsicSize.Min)
            .padding(vertical = 7.dp)
            .alpha(contentAlpha),
    ) {
        Column(
            // 板 `.trow .time{width:46px}` × 0.886 ⇒ **41dp**，而且是**左对齐**
            //（`Alignment.End` 会让时间的起点比分区头右移 23dp，复评量的就是这条）。
            // 「第8-9节」那种长串在节次列里，不在这个时间列里，所以 41dp 够用。
            modifier = Modifier.width(41.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = start ?: "",
                // 板 `.trow .time{font-size:10.5px;font-weight:600;color:var(--faint)}` ⇒ 9.3sp 淡字
                fontSize = Type.caption,
                fontWeight = Type.weightBody,
                color = faint,
                style = TextStyle(fontFeatureSettings = Type.tabular),
                maxLines = 1,
            )
            // 板子时间列只有一行「15:20」；节次挪到右边副行，不占第二行。
        }
        // 時間軸：每行自己畫一段，最後一行不往下接。點與點之間是連續的。
        // 板 `.trow .rail{width:14px}` + `::before{left:5px;width:1px;background:var(--line-strong)}`
        //    + `i{left:2px;top:5px;width:7px;height:7px;border:1.5px solid var(--faint);background:var(--phone)}`
        //    + `.current i{background:var(--accent);border-color:var(--accent);box-shadow:0 0 0 4px var(--accent-soft)}`
        // × 0.886 ⇒ 轨宽 12.4dp、线 0.3dp、点直径 6.2dp（描边 1.3dp）、当前点外圈 3.5dp 的 accent-soft。
        // 原来 radius=3.5f/4.5f 用的是**像素**，在这台 3.5x 屏上只有 1dp —— 点小到看不见。
        Canvas(
            modifier = Modifier
                // 轨宽就是板 `.trow .rail{width:14px}` ⇒ 12.4dp，不再另包 4dp
                //（那 8dp 让课名从 69dp 漂到 72dp+，整列右移一格）。
                .width(12.4.dp)
                .fillMaxHeight(),
        ) {
            val cx = size.width / 2f
            drawLine(
                color = if (colors.background.luminance() < 0.5f)
                    Color(0xFFFFFFFF).copy(alpha = 0.18f)
                else Color(0xFF1A1917).copy(alpha = 0.16f),
                // 末行从**点下方**起画：原来那一段 0→45% 整条被点本身盖住，
                // 末行等于没有轨（复评 2026-10-21）。
                start = Offset(cx, if (last) size.height * 0.28f else 0f),
                end = Offset(cx, size.height),
                strokeWidth = 0.9.dp.toPx(),
            )
            val dotY = size.height * 0.28f
            val r = 3.1.dp.toPx()
            if (isNow) {
                drawCircle(
                    color = colors.primary.copy(alpha = 0.11f),
                    radius = r + 3.5.dp.toPx(),
                    center = Offset(cx, dotY),
                )
                drawCircle(color = colors.primary, radius = r, center = Offset(cx, dotY))
            } else {
                // 板上未选中的点是个**空心圈**（底色 = 纸面，描边 = --faint）
                drawCircle(color = colors.background, radius = r, center = Offset(cx, dotY))
                drawCircle(
                    color = faint,
                    radius = r,
                    center = Offset(cx, dotY),
                    style = Stroke(width = 1.3.dp.toPx()),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.courseName.ifBlank { "（未命名课程）" },
                    // 板 `.trow .name{font-size:12.5px;font-weight:650}` × 0.886 ⇒ 11sp
                    fontSize = Type.secondary,
                    fontWeight = Type.weightBody,
                    color = if (isNow) colors.primary else colors.onBackground,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (isNow) {
                    Text(
                        // 板 `.trow .now{font-size:8.5px;font-weight:700;color:var(--accent);
                        //      background:var(--accent-soft);padding:3px 6px}` ⇒ 7.5sp/8sp、2.7×5.3dp
                        text = "现在",
                        fontSize = Type.micro,
                        fontWeight = Type.weightTitle,
                        color = colors.primary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(Dims.chipCorner))
                            .background(colors.primary.copy(alpha = 0.11f))
                            .padding(horizontal = 5.5.dp, vertical = 2.5.dp),
                    )
                }
            }
            val meta = buildList {
                if (slot.periodLabel.isNotBlank()) add(slot.periodLabel)
                if (slot.room.isNotBlank()) add(slot.room)
                if (slot.teacher.isNotBlank()) add(slot.teacher)
                if (slot.slotLabel.isNotBlank()) add(slot.slotLabel)
                if (slot.statusLabel.isNotBlank()) add(slot.statusLabel)
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    fontSize = Type.caption,
                    color = if (slot.statusLabel.isNotBlank()) colors.error
                    else colors.onSurfaceVariantSummary,
                    maxLines = 2,
                )
            }
        }
    }
}

/** 進行中的簽到：時效性最高，放在時間線下面，一眼看到。 */
@Composable
private fun RollcallBanner(title: String, deadline: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dims.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Dims.chipCorner))
                    .background(Color(0xFFD14A2E).copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                TileGlyph(TileIcon.Rollcall, Color(0xFFD14A2E), Modifier.size(21.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "现在可以签到" },
                    fontSize = Type.body,
                    fontWeight = Type.weightBody,
                    color = colors.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = if (deadline.isBlank()) "点一下去签到" else "截止 $deadline · 点一下去签到",
                    fontSize = Type.caption,
                    color = Color(0xFFD14A2E),
                )
            }
            Text(text = "›", fontSize = Type.title, color = colors.onSurfaceVariantSummary)
        }
    }
}

/**
 * 首页「课堂签到」一格（稿 `home`：`h3 课堂签到` + `button.btn 进入` + 一句 notice）。
 *
 * 与 [RollcallBanner] 的区别：Banner 只在**真有一场进行中的签到**时才有东西可画；
 * 这一格是**常驻分区**——没有要签的到也是一种状态，如实说没有，比整格消失更可信
 * （消失会让人以为是加载失败）。
 */
@Composable
private fun RollcallSection(
    open: TronRollcall?,
    onOpen: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BodyMark(
                // MarkIcon 里没有「签到」这一枚；签到站的入口形状是扫码，用 Scan。
                icon = MarkIcon.Scan,
                tint = colors.primary,
                modifier = Modifier.padding(end = 7.dp),
            )
            Text(
                text = "课堂签到",
                fontSize = Type.body,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "进入",
                fontSize = Type.caption,
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "进入签到站") { onOpen() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        if (open == null) {
            Text(
                text = "现在没有进行中的签到。",
                fontSize = Type.secondary,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            RollcallBanner(
                title = open.title,
                deadline = open.endTime,
                onClick = onOpen,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 稿原文。它说的是一件**产品承诺**（这一页不会替你签到），所以逐字照抄。
        Text(
            text = "进入签到站查看最新状态；本页不自动发起签到。",
            fontSize = Type.micro,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

/**
 * 页尾那颗描边按钮（稿 `.bottom-action .btn.outline`）。
 *
 * 为什么自己画而不用 Miuix 的 TextButton：稿上这两颗是 44dp 高、圆角 12、1dp 描边、文字用主色，
 * 而 TextButton 的默认高度是 36dp、没有描边。改全局 Button 令牌会牵动所有页面（`DesignTokensTest`
 * 钉着那一档），所以只在这一处按稿画。
 */
@Composable
private fun BottomActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClickLabel = text) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = Type.secondary,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 要处理：只列前三件，剩下的去待办页。 */
@Composable
private fun TodoSummaryCard(
    state: ListState<TodoBoard>,
    /** 这一刻真的在等一场取数（由调用点按「没有结论 + 有一笔在途」算好）。 */
    fetching: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val board = (state as? ListState.Content)?.items?.firstOrNull()
    // ★ 跨來源按「急迫程度」排，而不是按分组顺序取前三条。
    //   分组顺序是「学在城院在前、门户在后」，于是门户里明天就截止的那条永远露不出来；
    //   过期的（还没交的旧作业）也一直被压在下面 —— 那等于帮人把迟交的事藏起来。
    //   没有截止时间的条目排在最后，保持它们原有的相对顺序（不编一个时间）。
    val items = board?.groups?.flatMap { it.items }.orEmpty()
        .let { TodoNext.ordered(it, java.time.LocalDateTime.now()) }
    // ★ 这里以前写的是 state is ListState.FirstLoad。单独用它不成立：startRefresh 在没有内容时
    //   原样返回 FirstLoad（ListState.kt:130），于是 FirstLoad 既可能是「正在取」、也可能是
    //   「还没人发起过」。两件事分开问，合并点放在调用点。
    val expired = state is ListState.SessionExpired
    val error = (state as? ListState.Failed)?.error?.userText
    // 未知不得当成 0（data/ListState.kt 的红线）：只有真取到了才敢给数字，其余一律「—」。
    // 曾经这里是 if (all.isEmpty()) "0"，于是加载中/登录失效都显示成「0 件待办」。
    val countText = when (state) {
        is ListState.Content -> board?.knownTotal?.toString() ?: "—"
        is ListState.Empty -> "0"
        else -> "—"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开待办") { onClick() }
            .padding(top = 2.dp, bottom = 2.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BodyMark(
                    icon = MarkIcon.Todo,
                    tint = colors.primary,
                    modifier = Modifier.padding(end = 7.dp),
                )
                Text(
                    text = "要处理",
                    fontSize = Type.body,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = countText,
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                        style = TextStyle(fontFeatureSettings = Type.tabular),
                    )
                    Spacer(Modifier.width(8.dp))
                    // 稿 `home` 这一格的 meta 是「全部待办 ›」。计数是我们多出来的一条：
                    // 取不到时是「—」而不是 0（ListState 红线），所以两个都留：先给数，再给出口。
                    Text(
                        text = "全部待办 ›",
                        fontSize = Type.caption,
                        color = colors.primary,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                fetching -> Text(
                    text = "正在整理待办…",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )

                error != null -> Text(
                    text = error,
                    fontSize = Type.caption,
                    color = colors.error,
                    maxLines = 2,
                )

                // 取不到 ≠ 没有。只有真拿到一张板、且板里确实一件都没有，才敢说这句话。
                expired -> Text(
                    text = "登录已失效，待办取不到。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )

                board == null -> Text(
                    text = "待办还没取到。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )

                items.isEmpty() -> Text(
                    text = "没有待处理的事。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )

                else -> items.take(3).forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                // 板 `.task .badge{width:28px;height:28px;border-radius:9px;
                                //      background:var(--accent-soft);color:var(--accent)}`（作业那格换 warm）
                                // × 0.886 ⇒ 25dp / 8dp 圆角。
                                // 原来是 `onBackground.copy(0.05)` 的一块**灰方底** —— 复评点名的
                                // 「每个图标都被一个四方块框住」就是这里：板上的底是**带色的**，
                                // 而且浅到只够把字形托出来。
                                .size(25.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (t.title.contains("作业") || t.title.contains("报告") ||
                                        t.title.contains("实验")
                                    ) Color(0xFFB65E3D).copy(alpha = 0.10f)
                                    else colors.primary.copy(alpha = 0.11f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val homework = t.title.contains("作业") || t.title.contains("报告") ||
                                t.title.contains("实验")
                            // 板两档：作业 = 暖底 + **文件图形**；普通待办 = accent-soft 底 + 勾。
                            // 未完成的事不能画勾（复评 2026-10-22）。
                            TileGlyph(
                                if (homework) TileIcon.File else TileIcon.Todo,
                                if (homework) Color(0xFFB65E3D) else colors.primary,
                                Modifier.size(13.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = t.title,
                                fontSize = Type.secondary,
                                fontWeight = Type.weightBody,
                                color = colors.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = listOfNotNull(
                                t.subtitle.takeIf { it.isNotBlank() },
                                t.timeText.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(
                                    text = meta,
                                    fontSize = Type.micro,
                                    color = colors.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = "查看",
                            fontSize = Type.caption,
                            color = colors.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
    }
}

/**
 * 首页「校园跑」一格。
 *
 * 稿 `home` 的结构是：`h3 校园跑` + meta `记录 ›`，然后一张 paper：
 * `本学期累计` / `仅查看记录` / 大数字 + `/ N km` / 进度条 / 一行来源说明。
 *
 * 改之前是把公里数放在标题右端、下面挂一个 25dp 的彩色方块 + 一句状态 —— 那是
 * 「列表条目」的骨架，而这格在稿上是一张**成绩卡**（数字要大，进度要有形）。
 *
 * 红线不变（`data/ListState.kt`）：取不到就写「—」，不画 0.0 公里、不画空进度条当真。
 */
@Composable
private fun RunSummaryCard(
    state: ListState<SztySnapshot>,
    /** 这一刻真的在等一场取数（由调用点按「没有结论 + 有一笔在途」算好）。 */
    fetching: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val snapshot = (state as? ListState.Content)?.items?.firstOrNull()
    val run = snapshot?.run
    val stale = (state as? ListState.Content)?.meta?.stale == true
    val expired = state is ListState.SessionExpired
    val error = (state as? ListState.Failed)?.error?.userText
    val km = run?.runKm
    val target = run?.runTargetKm
    val status = when {
        fetching -> "正在取校园跑…"
        expired -> "登录已失效，校园跑取不到。"
        error != null -> "校园跑那份没取到：" + error
        // Empty 有两个来源（没接过数智体育 / 接上了但一块都没取到），对人来说下一步一样：进去接。
        run == null -> "还没接上数智体育 · 点一下去接"
        km == null -> "这份汇总里没有公里数字"
        else -> runRequirementText(run) + if (stale) " · 这是上次存的" else ""
    }
    val ratio = if (km != null && target != null && target > 0) {
        (km / target).coerceIn(0.0, 1.0)
    } else {
        null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "打开校园跑") { onClick() }
            .padding(top = 2.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BodyMark(
                icon = MarkIcon.Run,
                tint = colors.primary,
                modifier = Modifier.padding(end = 7.dp),
            )
            Text(
                text = "校园跑",
                fontSize = Type.body,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "记录 ›",
                fontSize = Type.caption,
                color = colors.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本学期累计",
                fontSize = Type.body,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            // 稿原文「仅查看记录」：这一格点进去也只能看，不能补跑或修改。
            Text(
                text = "仅查看记录",
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            // 里程是这一页唯一「每次刷新都可能变」的数字 ⇒ 它走码表滚动，
            // 变了能看见，而不是原地换一次字（2026-11-01 抖音那条的「滚动码表」）。
            OdometerText(
                text = if (km == null) "—" else kmText(km),
                fontSize = 30.sp,
                fontWeight = Type.weightTitle,
                color = colors.primary,
                style = TextStyle(fontFeatureSettings = Type.tabular),
            )
            Text(
                text = if (target == null) " 公里" else " / " + kmText(target) + " km",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // 进度条：只在**两个数都真取到**时才画。缺一个就画不出来 ——
        // 用 0 长度冒充「还没跑」与用满格冒充「已达标」都是编。
        if (ratio != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.primary.copy(alpha = 0.13f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primary),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        // 稿是「数智体育 · 合成样例，不是你的跑步记录」。我们是真取数，所以写来源与状态：
        // 同一行既交代数据从哪来，也交代这次有没有取到 —— 不另开一行说失败。
        Text(
            // 底行：来源 + 状态。status 里已经出现「数智体育」就不再前缀一次 ——
            // 真机上先量到过「数智体育 · 还没接上数智体育 · 点一下去接」：
            // 第一次修用 startsWith 判，但未接入那支的 status 是「还没接上数智体育 · 点一下去接」，
            // 首字是「还」不是「数」，前缀又加了一遍（2026-11-08 12:26 大字号截图复现）。
            // 判「是否已经提到来源」要看 contains，不是 startsWith。
            text = if (status.contains("数智体育")) status else "数智体育 · " + status,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 卡片副行：只说「要求多少 / 几次」。
 *
 * 为什么不重用数智体育页那句「已跑 X 公里 · 要求 Y 公里 · N 次」：
 * 这里的公里数已经是右边那个大字，副行再念一遍「已跑 16.8 公里」是同一句话说两次。
 */
private fun runRequirementText(run: SztyRun): String {
    val parts = mutableListOf<String>()
    run.runTargetKm?.let { parts += "要求 " + kmText(it) + " 公里" }
    run.runCount?.let { parts += it.toString() + " 次" }
    return if (parts.isEmpty()) "点一下看完整记录" else parts.joinToString(" · ")
}

/** 「此刻」要讲的那一节课：正在上（含剩余时间）或下一节（含距上课时间）。 */
private data class FocusNow(
    val slot: TronScheduleSlot?,
    val ongoing: Boolean,
    val start: LocalTime?,
    val end: LocalTime?,
    /** 正在上 = 距下课分钟数；下一节 = 距上课分钟数。null = 没有可说的那一节。 */
    val minutes: Long?,
    /** 正在上的总时长，用来算倒计时环的比例；下一节为 null。 */
    val totalMinutes: Long?,
)

private fun focusNowOf(
    slots: List<TronScheduleSlot>,
    now: LocalTime,
    times: List<Pair<String, String>>,
): FocusNow {
    val starts = slots.map { parseClock(PeriodClock.startOf(times, it.periodStart)) }
    val ends = slots.map { parseClock(PeriodClock.endOf(times, it.periodEnd)) }
    val ongoing = slots.indices.firstOrNull { i ->
        val s = starts[i]
        val e = ends[i]
        s != null && e != null && !now.isBefore(s) && now.isBefore(e)
    }
    if (ongoing != null) {
        val s = starts[ongoing]!!
        val e = ends[ongoing]!!
        return FocusNow(
            slot = slots[ongoing],
            ongoing = true,
            start = s,
            end = e,
            minutes = java.time.Duration.between(now, e).toMinutes().coerceAtLeast(0),
            totalMinutes = java.time.Duration.between(s, e).toMinutes().coerceAtLeast(1),
        )
    }
    val next = slots.indices.firstOrNull { i ->
        val s = starts[i]
        s != null && s.isAfter(now)
    }
    if (next != null) {
        val s = starts[next]!!
        return FocusNow(
            slot = slots[next],
            ongoing = false,
            start = s,
            end = ends[next],
            minutes = java.time.Duration.between(now, s).toMinutes().coerceAtLeast(0),
            totalMinutes = null,
        )
    }
    return FocusNow(null, false, null, null, null, null)
}

private fun parseClock(raw: String?): LocalTime? {
    val t = raw?.trim().orEmpty()
    if (t.length < 4) return null
    return runCatching { LocalTime.parse(t) }.getOrNull()
}

private val TIME_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 下拉时天空最多再长这么高。再长就把整块画布推出屏幕，收益也不再有。 */
private val PULL_SKY_MAX_EXTRA = 200.dp

/** 画布长高的换算系数：照片底边按 `skyBottomY` 的 0.80 走 ⇒ 1 / 0.80 = 1.25。 */
private const val PULL_SKY_GAIN = 1.25f

/** 模糊半径 = 盒高增量 × 这个系数。200dp 拉满时 20dp 半径，松手跟着回 0。 */
private const val PULL_BLUR_PER_DP = 0.10f
