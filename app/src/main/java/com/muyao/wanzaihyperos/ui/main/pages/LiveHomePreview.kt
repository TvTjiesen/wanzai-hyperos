package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.CountValue
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.schedule.*
import com.muyao.wanzaihyperos.ui.design.BackgroundPreset
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.ContainerStyle
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.LocalContainerStyle
import com.muyao.wanzaihyperos.ui.design.ai.AiBallPresentation
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import com.muyao.wanzaihyperos.ui.design.ai.HomeTaskFacts
import com.muyao.wanzaihyperos.ui.design.liquid.iosHeaderInset
import com.muyao.wanzaihyperos.ui.home.*
import com.muyao.wanzaihyperos.ui.home.redesign.HomeQuickActions
import com.muyao.wanzaihyperos.ui.home.redesign.homeDepthCanvasHeight
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDateTime

@Composable
internal fun LiveHomePreview(
    graph: AppGraph,
    preset: SkyPreset,
    image: LoadedWallpaper?,
    crop: WallpaperEntry,
    editing: Boolean,
    onCrop: (WallpaperEntry) -> Unit,
    modifier: Modifier = Modifier,
    dim: Int = UiPrefs.wallpaperDim,
    soft: Int = UiPrefs.wallpaperSoft,
    fullPage: Boolean = false,
    scrollable: Boolean = true,
    appearance: com.muyao.wanzaihyperos.data.AppearanceSettings = UiPrefs.appearanceSnapshot(),
) {
    val schedule by graph.schedule.state.collectAsVisibleStateWithLifecycle()
    val jw by graph.jwTimetable.state.collectAsVisibleStateWithLifecycle()
    val todos by graph.todoBoard.state.collectAsVisibleStateWithLifecycle()
    val rollcalls by graph.rollcall.state.collectAsVisibleStateWithLifecycle()
    val sports by graph.szty.state.collectAsVisibleStateWithLifecycle()
    val crawling by graph.jwTimetable.isCrawling
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val active = com.muyao.wanzaihyperos.ui.design.liquid.LocalPageActive.current
    LaunchedEffect(active) { if (active) while (true) { delay(60_000); now = LocalDateTime.now() } }
    val tron = (schedule as? ListState.Content)?.items?.firstOrNull()
    val jwSlots = (jw as? ListState.Content)?.items?.firstOrNull()?.slots.orEmpty()
    val pick = pickSlots(jwSlots, jwVerdictOf(jw, crawling, jwSlots.isNotEmpty()), tron?.slots.orEmpty())
    val knownWeek = (tron?.currentWeek as? CountValue.Known)?.value
    val week = knownWeek?.takeIf { it > 0 } ?: UiPrefs.weekFromAnchor(now.toLocalDate().toEpochDay())
    val times = UiPrefs.periodTimes
    val confirmedEmpty = pick.jwVerdict == JwVerdict.SETTLED_EMPTY
    val plan = if (pick.slots.isNotEmpty()) TodayPlan.of(pick.slots, week, now, times)
        else if (confirmedEmpty) TodayPlan("今天没有课", null, emptyList(), 0, clockKnown = true) else null
    val today = if (week == null) emptyList() else pick.slots.filter { it.dayOfWeek == now.dayOfWeek.value && it.activeIn(week) }.sortedBy { it.periodStart }
    val focus = focusNowOf(today, now.toLocalTime(), times)
    val loading = !confirmedEmpty && pick.slots.isEmpty() && (pick.isWaiting || schedule is ListState.FirstLoad)
    val failure = if (!confirmedEmpty && pick.slots.isEmpty() && (schedule is ListState.Failed || jw is ListState.Failed || schedule is ListState.SessionExpired || jw is ListState.SessionExpired)) "课表暂未更新" else null
    val openRollcall = (rollcalls as? ListState.Content)?.items?.firstOrNull { !it.expired }
    val allTodos = (todos as? ListState.Content)?.items.orEmpty().flatMap { it.groups }.flatMap { it.items }
    val observation = com.muyao.wanzaihyperos.ui.design.ai.HomeBallFacts.observe(
        pick.source, schedule, jw, todos, rollcalls, HomeTaskFacts.ofTodos(todos), HomeTaskFacts.ofRollcalls(rollcalls),
        crawling, 0, openRollcall?.let { it.unanswered && it.needsCamera } == true,
    )
    val reading = observation.reading
    fun parseClock(raw: String?) = raw?.let { runCatching { java.time.LocalTime.parse(it.trim()) }.getOrNull() }
    val nextStart = today.mapNotNull { parseClock(PeriodClock.startOf(times, it.periodStart)) }.firstOrNull { it.isAfter(now.toLocalTime()) }
    val remaining = today.count { parseClock(PeriodClock.endOf(times, it.periodEnd))?.isAfter(now.toLocalTime()) != false }
    val ball = AiBallStates.ofHome(
        plan, loading, failure, focus.ongoing, now.hour,
        waitingRollcall = openRollcall?.let { it.unanswered && it.needsCamera } == true,
        urgentTodo = AiBallStates.hasUrgentTodo(allTodos, System.currentTimeMillis()),
        fetching = reading, todoFacts = HomeTaskFacts.ofTodos(todos), rollcallFacts = HomeTaskFacts.ofRollcalls(rollcalls),
        minutesToNextClass = nextStart?.let { java.time.Duration.between(now.toLocalTime(), it).toMinutes().toInt() },
    )
    val colors = MiuixTheme.colorScheme
    val bare = LocalContainerStyle.current == ContainerStyle.Bare
    val look = if (bare || appearance.customization.useSystemWallpaper) SkyLook(colors.background, colors.background, colors.background,
        Color.Transparent, Color.Transparent, colors.onBackground, colors.background.luminance() < .5f) else skyLookOf(preset, now.hour)
    val compact = appearance.density == "compact"
    val originalDensity = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.toFloat().coerceAtLeast(1f)
    val latestCrop by rememberUpdatedState(crop)
    val latestOnCrop by rememberUpdatedState(onCrop)
    val viewportHeight = 440f
    val previewScroll = rememberScrollState()
    BoxWithConstraints(modifier) {
        val ratio = (if (fullPage) maxWidth.value / screenWidth
            else minOf(maxWidth.value / screenWidth, maxHeight.value / viewportHeight)).coerceIn(.1f, 1f)
        Box(Modifier.align(Alignment.Center).width((screenWidth * ratio).dp).height(if (fullPage) maxHeight else (viewportHeight * ratio).dp).clip(RoundedCornerShape(22.dp))) {
        CompositionLocalProvider(LocalDensity provides Density(originalDensity.density * ratio, originalDensity.fontScale)) {
            val topInset = iosHeaderInset() + 2.dp
            var cardTop by remember { mutableStateOf(180f) }
            var cardHeight by remember { mutableStateOf(200f) }
            val skyHeight = homeDepthCanvasHeight(cardTop, cardHeight, preset == SkyPreset.ALBUM && image != null)
            val density = LocalDensity.current
            GlasmioBackdropHost(
                modifier = Modifier.fillMaxSize(),
                sampledContent = {
                    if (appearance.customization.useSystemWallpaper && image == null) {
                        Box(Modifier.fillMaxSize().background(colors.background))
                    } else {
                        CompositionLocalProvider(LocalDesktopWallpaper provides image) {
                            ThemePageBackground(Modifier.fillMaxSize(), colors.background.luminance() < .5f, BackgroundPreset.of(appearance.background))
                        }
                    }
                    if (!bare && !appearance.customization.useSystemWallpaper) HomeSky(preset, image?.bitmap, dim, soft,
                        Modifier.fillMaxWidth().height(skyHeight.dp), panX = crop.panX, panY = crop.panY, zoom = crop.zoom, backgroundPreset = BackgroundPreset.of(appearance.background))
                },
                overlayContent = { backdrop ->
                    Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth()
                         .then(if (fullPage && !editing && scrollable) Modifier.verticalScroll(previewScroll) else Modifier)
                        .padding(horizontal = Dims.screenH).padding(top = topInset), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
                        HomeSkyHeader(now, look, remaining, today.size, nextStart, loading, failure,
                            compact, pick.source, pick.jwVerdict, jw.metaOrNull?.stale == true,
                            ball, { top, height -> cardTop = top + topInset.value; cardHeight = height }, "courses" in appearance.modules,
                            courseContent = { status ->
                                FocusGlassCard(backdrop, compact, plan, focus, loading, reading, failure, week, now,
                                    look, !bare && preset == SkyPreset.ALBUM, image?.stats, openRollcall, {}, {}, status, ball)
                            }, ballPresentation = AiBallPresentation.Home)
                        HomeQuickActions(backdrop, compact, {}, {}, {}, {})
                        if (fullPage) {
                            if ("todos" in appearance.modules) com.muyao.wanzaihyperos.ui.home.redesign.HomeDashboardGroup(compact) {
                                TodoSummaryCard(todos, todos is ListState.FirstLoad && reading, {})
                            }
                            if ("rollcall" in appearance.modules || "szty" in appearance.modules) {
                                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if ("rollcall" in appearance.modules) com.muyao.wanzaihyperos.ui.home.redesign.HomeDashboardGroup(compact, Modifier.weight(1f).fillMaxHeight(), narrow = true, stretch = true) {
                                        RollcallSection(openRollcall, {}, narrow = true)
                                    }
                                    if ("szty" in appearance.modules) com.muyao.wanzaihyperos.ui.home.redesign.HomeDashboardGroup(compact, Modifier.weight(1f).fillMaxHeight(), narrow = true, stretch = true) {
                                        RunSummaryCard(sports, sports is ListState.FirstLoad && reading, {}, narrow = true)
                                    }
                                }
                            }
                            if ("courses" in appearance.modules && (loading || failure != null || week == null || today.isNotEmpty())) {
                                com.muyao.wanzaihyperos.ui.home.redesign.HomeDashboardGroup(compact) {
                                    TodayTimelineCard(today, week, now, times, loading, failure, {})
                                }
                            }
                            Spacer(Modifier.height(96.dp))
                        }
                    }
                    if (fullPage && !editing) {
                        com.muyao.wanzaihyperos.ui.design.liquid.GlassNavBar(
                            items = com.muyao.wanzaihyperos.ui.main.MainSection.entries.map {
                                com.muyao.wanzaihyperos.ui.design.liquid.GlassNavItem(it.label, it.iconRes)
                            }, selected = 0, onSelect = {}, backdrop = backdrop,
                            modifier = Modifier.align(Alignment.BottomCenter), systemInset = 0.dp,
                        )
                    }
                    }
                },
            )
            if (editing && image != null && !bare) {
                Box(Modifier.fillMaxSize().pointerInput(image.bitmap, crop.id, skyHeight, appearance.customization.wallpaperRotation) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val current = latestCrop
                        val height = with(density) { skyHeight.dp.toPx() }
                        val nextZoom = (current.zoom * zoom).coerceIn(1f, 4f)
                        val swapped = appearance.customization.wallpaperRotation % 180 != 0
                        val slack = coverSlackPx(if (swapped) image.bitmap.height else image.bitmap.width,
                            if (swapped) image.bitmap.width else image.bitmap.height, size.width.toFloat(), height, nextZoom)
                        latestOnCrop(current.copy(
                            panX = (current.panX + if (slack.first > 1f) pan.x / slack.first else 0f).coerceIn(-1f, 1f),
                            panY = (current.panY + if (slack.second > 1f) pan.y / slack.second else 0f).coerceIn(-1f, 1f),
                            zoom = nextZoom,
                        ))
                    }
                })
            }
        }
        }
    }
}
