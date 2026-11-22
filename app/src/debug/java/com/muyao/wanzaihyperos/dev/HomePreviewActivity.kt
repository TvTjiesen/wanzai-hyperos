package com.muyao.wanzaihyperos.dev

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.data.schedule.JwVerdict
import com.muyao.wanzaihyperos.data.schedule.PeriodClock
import com.muyao.wanzaihyperos.data.schedule.TodayPlan
import com.muyao.wanzaihyperos.data.schedule.pickSlots
import com.muyao.wanzaihyperos.data.tronclass.ScheduleSource
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.WanzaiTheme
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import com.muyao.wanzaihyperos.ui.design.liquid.LocalNavBarVisible
import com.muyao.wanzaihyperos.ui.design.liquid.iosHeaderInset
import com.muyao.wanzaihyperos.ui.design.liquid.pageBottomInsetDp
import com.muyao.wanzaihyperos.ui.home.HomeSky
import com.muyao.wanzaihyperos.ui.home.SkyPreset
import com.muyao.wanzaihyperos.ui.home.redesign.HomeQuickActions
import com.muyao.wanzaihyperos.ui.home.redesign.homeDepthCanvasHeight
import com.muyao.wanzaihyperos.ui.home.skyLookOf
import com.muyao.wanzaihyperos.ui.main.pages.FocusGlassCard
import com.muyao.wanzaihyperos.ui.main.pages.HomeSkyHeader
import com.muyao.wanzaihyperos.ui.main.pages.focusNowOf
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** Offline, emulator-only production-component preview; never a complete-home acceptance screen. */
class HomePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val emulator = Build.HARDWARE in setOf("ranchu", "goldfish") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.PRODUCT.startsWith("sdk")
        if (!emulator) {
            Toast.makeText(this, "DEBUG 首页示例仅允许模拟器运行", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        enableEdgeToEdge()
        val scenario = intent.getStringExtra("scenario")
            ?.takeIf { it in setOf("next", "ended", "long", "error", "nocourses", "loading", "waiting", "ongoing", "urgent", "fetching", "unknown") } ?: "next"
        val theme = when (intent.getStringExtra("theme")) {
            "light" -> AppThemeMode.Light
            "dark" -> AppThemeMode.Dark
            else -> AppThemeMode.System
        }
        val compact = intent.getBooleanExtra("compact", false)
        val glassMode = intent.getStringExtra("glassMode")?.takeIf { it in setOf("liquid", "frost", "off") } ?: "liquid"
        val glassOpacity = intent.getIntExtra("glassOpacity", 50).coerceIn(0, 100)
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setRefractsGlass(glassMode == "liquid")
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceTransparency(
            intent.getBooleanExtra("reduceTransparency", false))
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceMotion(
            intent.getBooleanExtra("reduceMotion", false))
        // The manifest gives this activity its own process. Do not load prefs, AppGraph or repositories.
        setContent {
            WanzaiTheme(themeMode = theme) {
                val motion = com.muyao.wanzaihyperos.ui.design.rememberMotion()
                val appearance = io.github.neoruaa.glasmio.core.LocalGlassAppearance.current
                androidx.compose.runtime.CompositionLocalProvider(
                    com.muyao.wanzaihyperos.ui.design.glass.LocalBarMaterial provides
                        com.muyao.wanzaihyperos.ui.design.glass.barMaterialFor(glassMode, motion.glassIsTranslucent),
                    io.github.neoruaa.glasmio.core.LocalGlassAppearance provides appearance.copy(
                        liquidGlassEnabled = glassMode == "liquid" && motion.refractsGlass,
                        reduceTransparency = glassMode == "off" || !motion.glassIsTranslucent,
                        materialAlphaMultiplier = com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(glassOpacity.toFloat()),
                    ),
                ) {
                    HomePreview(scenario = scenario, compact = compact)
                }
            }
        }
    }

    @Composable
    private fun HomePreview(scenario: String, compact: Boolean) {
        val now = remember(scenario) {
            LocalDateTime.of(2026, 11, 10, when (scenario) {
                "ended", "urgent", "fetching" -> 23
                "ongoing" -> 13
                else -> 12
            }, when (scenario) {
                "ended", "urgent", "fetching" -> 0
                "ongoing" -> 45
                else -> 44
            })
        }
        val week = 10
        // Empty overrides deliberately use the production SchoolPeriods table, not account preferences.
        val times = emptyList<Pair<String, String>>()
        val slots = remember(scenario) {
            if (scenario in setOf("error", "nocourses", "loading")) emptyList() else listOf(
                placeholderCourse(1, "示例课程：上午课", 1, 2),
                placeholderCourse(2,
                    if (scenario == "long") "示例课程：现代城市公共空间与跨学科交互系统设计理论及实践专题研究"
                    else "示例课程：数据结构", 6, 7,
                    if (scenario == "long") "示例校区综合实验教学中心东区四号楼 214（跨学科协同实验室）"
                    else "示例教室 214"),
                placeholderCourse(3, "示例课程：程序设计", 8, 9),
            )
        }
        val failure = if (scenario == "error") "教务那份没取到：离线错误示例，未发起网络请求。" else null
        val slotPick = pickSlots(
            jwSlots = slots,
            jwVerdict = when {
                failure != null -> JwVerdict.NO_CONCLUSION
                slots.isEmpty() -> JwVerdict.SETTLED_EMPTY
                else -> JwVerdict.HAS_SLOTS
            },
            tronSlots = emptyList(),
        )
        val todaySlots = slotPick.slots
            .filter { it.dayOfWeek == now.dayOfWeek.value && it.activeIn(week) }
            .sortedBy { it.periodStart }
        val loading = scenario == "loading"
        val rollcall = if (scenario == "waiting") com.muyao.wanzaihyperos.data.tronclass.TronRollcall(
            id = -1, courseId = -1, title = "DEBUG 示例扫码签到", source = "qr",
            status = "in_progress", expired = false, rollcallTime = "12:40", endTime = "13:00",
            numberCode = "", myStatus = "absent",
        ) else null
        val plan = if (failure == null && !loading) {
            TodayPlan.of(slotPick.slots, week, now, times)
        } else null
        val focus = focusNowOf(todaySlots, now.toLocalTime(), times)
        val nextStart = todaySlots.mapNotNull { slot ->
            PeriodClock.startOf(times, slot.periodStart)?.let(LocalTime::parse)
        }.firstOrNull { it.isAfter(now.toLocalTime()) }
        val taskFacts = if (scenario == "unknown")
            com.muyao.wanzaihyperos.ui.design.ai.HomeTaskFacts.Unknown
        else com.muyao.wanzaihyperos.ui.design.ai.HomeTaskFacts.Empty
        val ballState = AiBallStates.ofHome(
            plan = plan,
            loading = loading,
            failure = failure,
            ongoing = focus.ongoing,
            hour = now.hour,
            waitingRollcall = rollcall != null,
            urgentTodo = scenario == "urgent",
            fetching = scenario == "fetching",
            todoFacts = taskFacts,
            rollcallFacts = taskFacts,
            minutesToNextClass = nextStart?.let {
                Duration.between(now.toLocalTime(), it).toMinutes().toInt()
            },
        )
        val factText = AiBallStates.homeFactText(ballState, taskFacts, taskFacts)
        val colors = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
        val preset = SkyPreset.AUTO
        val look = skyLookOf(preset, now.hour)
        val depthTopInset = iosHeaderInset() + 2.dp
        var depthCardTop by remember { mutableStateOf(180f) }
        var depthCardHeight by remember { mutableStateOf(200f) }
        val depthPageBrush = AppColors.pageBrush(colors.background.luminance() < 0.5f)
        SideEffect {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !look.lightIcons
                isAppearanceLightNavigationBars = colors.background.luminance() >= 0.5f
            }
        }
        Box(Modifier.fillMaxSize()) {
            GlasmioBackdropHost(
                modifier = Modifier.fillMaxSize(),
                sampledContent = {
                    Box(Modifier.fillMaxSize().background(colors.background).background(depthPageBrush))
                    HomeSky(
                        preset = preset,
                        wallpaper = null,
                        dim = UiPrefs.wallpaperDim,
                        soft = UiPrefs.wallpaperSoft,
                        hourOverride = now.hour,
                        modifier = Modifier.fillMaxWidth()
                            .height(homeDepthCanvasHeight(depthCardTop, depthCardHeight, false).dp),
                    )
                },
                overlayContent = { skyBackdrop ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Dims.screenH,
                            end = Dims.screenH,
                            top = depthTopInset,
                            bottom = pageBottomInsetDp(LocalNavBarVisible.current) + 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                    ) {
                        item(key = "hero") {
                            HomeSkyHeader(
                                now = now,
                                look = look,
                                remainingCount = plan?.remaining?.size ?: 0,
                                todayCount = todaySlots.size,
                                nextStart = nextStart,
                                loading = loading,
                                failure = failure,
                                compact = compact,
                                slotSource = slotPick.source,
                                jwVerdict = slotPick.jwVerdict,
                                jwIsStale = slots.isNotEmpty(),
                                ballState = ballState,
                                onCardGeometry = { top, height ->
                                    depthCardTop = depthTopInset.value + top
                                    depthCardHeight = height
                                    android.util.Log.d("HomePreviewGeometry",
                                        "scenario=$scenario top=$depthCardTop height=$height compact=$compact")
                                },
                                courseEnabled = scenario != "nocourses",
                                // 与生产同源：事实文案也走 homeFactText，预览不能自造一套口径。
                                taskFactText = factText,
                                courseContent = { status ->
                                    FocusGlassCard(
                                        backdrop = skyBackdrop,
                                        compact = compact,
                                        plan = plan,
                                        focus = focus,
                                        loading = loading,
                                        fetching = scenario == "fetching",
                                        shownFailure = failure,
                                        week = week,
                                        now = now,
                                        look = look,
                                        skyIsPhoto = false,
                                        photoStats = null,
                                        openRollcall = rollcall,
                                        onClick = { testAction("课表") },
                                        onOpenRollcall = { testAction("签到") },
                                        statusText = status,
                                        taskFactText = factText,
                                        ballState = ballState,
                                    )
                                },
                            )
                        }
                        item(key = "quick-actions") {
                            HomeQuickActions(
                                backdrop = skyBackdrop,
                                compact = compact,
                                onOpenTimetable = { testAction("课表") },
                                onOpenJw = { testAction("教务") },
                                onOpenMessages = { testAction("消息") },
                                onOpenAppearance = { testAction("外观") },
                            )
                        }
                    }
                },
            )
            Text(
                text = "DEBUG 示例 · $scenario · ${now.toLocalDate()} ${now.toLocalTime()}\n" +
                    "离线占位 / 缓存来源示例 · 仅首页上部，非完整首页",
                color = Color.White,
                fontSize = Type.micro,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .fillMaxWidth().background(Color(0xFF252525)).padding(8.dp),
            )
        }
    }

    private fun testAction(label: String) {
        Toast.makeText(this, "DEBUG 示例：$label，仅测试，不发送业务请求", Toast.LENGTH_SHORT).show()
    }
}

private fun placeholderCourse(
    id: Int,
    name: String,
    start: Int,
    end: Int,
    room: String = "示例教室 101",
): TronScheduleSlot = TronScheduleSlot(
    courseId = id,
    courseName = name,
    dayOfWeek = 2,
    periodStart = start,
    periodEnd = end,
    weekStart = 10,
    weekEnd = 10,
    raw = "DEBUG 离线占位课程 / 模拟缓存，非账户数据",
    teacher = "示例教师",
    room = room,
    source = ScheduleSource.JIAOWU,
)
