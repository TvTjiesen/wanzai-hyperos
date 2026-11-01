package com.muyao.wanzaihyperos.ui.main.pages

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.muyao.wanzaihyperos.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.BackgroundPreset
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.PillSwitch
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.MotionPrefs
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurfaceId
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurfaceKind
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import com.muyao.wanzaihyperos.ui.home.SkyPreset
import androidx.compose.foundation.gestures.detectTransformGestures
import com.muyao.wanzaihyperos.ui.home.coverSlackPx
import com.muyao.wanzaihyperos.ui.home.drawCoverBitmap
import com.muyao.wanzaihyperos.data.schedule.PeriodClock
import com.muyao.wanzaihyperos.data.schedule.jwVerdictOf
import com.muyao.wanzaihyperos.data.schedule.pickSlots
import com.muyao.wanzaihyperos.ui.home.SkyPreview
import com.muyao.wanzaihyperos.ui.home.decodeWallpaper
import com.muyao.wanzaihyperos.ui.home.deleteWallpaper
import com.muyao.wanzaihyperos.ui.home.loadWallpaperFrom
import com.muyao.wanzaihyperos.ui.home.skyLookOf
import com.muyao.wanzaihyperos.ui.home.wallpaperFile
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 「外觀」子頁（v2）：壁紙 / **相冊取景（v3 #10）** / 柔化 / 暗度 / 液態玻璃 / 首頁密度與模組。
 *
 * ⚠️ 這行註釋以前還寫著「應用整理」，但這一頁**從來沒有**過那個區塊
 * （`apps-label` 那一節下面只有一顆「網頁服務收成一行」的開關）。
 * 常用那一排的钉/撤在**应用页自己的「整理」模式**里（`AppsSectionPage`），不在这里 ——
 * 顶栏那颗「整理」目前仍然跳到这一页，这一处不一致留着，记在 `docs/V3-CHECKLIST.md` #7 那行。
 * （写注释时别在这页里照抄那套 `key = ` 的写法：`LazyColumnKeyUniquenessTest` 是按**文本**扫的，
 * 注释里出现同样的字面量会被它算成一条真的 key，然后报"重复 key"。）
 *
 * ## 為什麼是殼內覆蓋層，不是新路由
 *
 * 它只改「這一台手機怎麼顯示」，不屬於任何一個板塊；進出時底欄應該被蓋住
 * （否則他會以為還在板塊裡、卻點不動）。所以由 `WanzaiMainShell` 用一層
 * 全屏覆蓋渲染，關掉就回到原來那一頁 —— 不新增 Route、不動根導航。
 *
 * ## 為什麼所有東西都只落在 UiPrefs
 *
 * 這裡每一項都必須**冷啟動還在**。憑據不進來（`UiPrefs` 的紅線）；
 * 壁紙檔案也只存在應用私有目錄，不上傳。
 */
@Composable
fun AppearancePage(
    onClose: () -> Unit,
    graph: com.muyao.wanzaihyperos.app.AppGraph,
    /** B4-14：主题也收进这一页（跟随系统 / 浅色 / 深色），改完立即生效。 */
    themeMode: com.muyao.wanzaihyperos.ui.AppThemeMode = com.muyao.wanzaihyperos.ui.AppThemeMode.System,
    onThemeMode: (com.muyao.wanzaihyperos.ui.AppThemeMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    // 订阅 MotionPrefs.version：`reduceTransparency` 是 @Volatile 普通字段，
    // 直接读不会让这一页重组 —— 开关会「动了但不变」，正好踩中 Motion.kt 档头警告。
    val motion = rememberMotion()
    val isDark = com.muyao.wanzaihyperos.ui.design.liquid.isInDarkTheme()
    AppearanceStatusIcons(isDark = isDark)

    // ── 预览卡要吃真课表 ─────────────────────────────────────────
    //
    // 復評的判据是「预览里那行字是死的，和首页此刻卡说的不是同一件事」。
    // 这里**不新开一条数据路**：读的是首页同一份 schedule/jw 状态、
    // 走的是同一个 `TodayPlan.of`（时段表也共用 `UiPrefs.periodTimes`），
    // 所以预览和首页那一刻必然一致。取不到就照实写「还在取课表…」/「今天没有课」，
    // **绝不编一节演示课**（工程红线：不伪造学校给不出的数据）。
    val scheduleState by graph.schedule.state.collectAsState()
    val jwTimetableState by graph.jwTimetable.state.collectAsState()
    val previewNow = java.time.LocalDateTime.now()
    // 记住的不只是"今天那几节"，还有**这一屏画的究竟是哪一份** —— 独立审计 v3 #4 第 2 轮
    // 抓到预览把 WAIT / 真没课 / 两份都没取到三种态压成同一句「还没有课表数据 / 在课表页登录一次」，
    // 那是拿一个一元布尔冒充三种状态（与 A-4 同一处病根）。
    val previewPlanAndSource = remember(
        scheduleState, jwTimetableState, previewNow.toLocalDate(), previewNow.hour,
    ) {
        // ★ v3 #4 第 1 轮审计（A-3）：这里以前也手抄了一遍"教务优先"，
        //   没有 WAIT 这一支 ⇒ 预览在教务还在爬时把学在城院那份当成"今天的课"画出来。
        //   现在与首页、课表页同一个 `pickSlots`，`SlotSourceTest` 有源码扫描钉住。
        val jw = (jwTimetableState as? ListState.Content)
            ?.items?.firstOrNull()?.slots.orEmpty()
        val tron = (scheduleState as? ListState.Content)
            ?.items?.firstOrNull()
        val slotPick = pickSlots(
            jwSlots = jw,
            jwVerdict = jwVerdictOf(
                state = jwTimetableState,
                crawling = graph.jwTimetable.isCrawling.value,
                hasSlots = jw.isNotEmpty(),
            ),
            tronSlots = tron?.slots.orEmpty(),
        )
        val slots = slotPick.slots
        val week = UiPrefs.noteWeek(
            (tron?.currentWeek as? com.muyao.wanzaihyperos.data.CountValue.Known)?.value,
            previewNow.toLocalDate().toEpochDay(),
        )
        val plan = if (slots.isEmpty()) {
            null
        } else {
            com.muyao.wanzaihyperos.data.schedule.TodayPlan.of(
                slots = slots,
                week = week,
                now = previewNow,
                times = UiPrefs.periodTimes,
            )
        }
        plan to slotPick.jwVerdict
    }
    val previewPlan = previewPlanAndSource.first
    val previewVerdict = previewPlanAndSource.second

    var note by remember { mutableStateOf<String?>(null) }
    val rev = UiPrefs.wallpaperRev
    val preset = SkyPreset.of(UiPrefs.wallpaperPreset)
    val wallpaper = remember(rev, preset) {
        if (preset == SkyPreset.ALBUM) decodeWallpaper(wallpaperFile(context)) else null
    }
    // 選了相冊但檔案不在：畫面要照實說，不能拿一片自動天空冒充「你的圖」。
    val albumMissing = preset == SkyPreset.ALBUM && wallpaper == null
    val effectivePreset = if (albumMissing) SkyPreset.AUTO else preset

    // 当前选中的全局底色。直接读 AppColors（它内部现取 UiPrefs.backgroundPreset），
    // **不另存一份本地状态** —— 两份状态就会有"点了但没生效"的那种假开关。
    val backgroundChoice = AppColors.background

    // 选完图之后那一段（拷字节 + 解码）必须在 IO 上跑，见下面回调里的注释。
    val cropScope = androidx.compose.runtime.rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // ★ 独立审计（v3 #10 第 1 轮，与 OOM 同优先级）：这一行以前是**同步**解码
        //   （`openInputStream` + 整文件 `copyTo` + **两趟** `BitmapFactory.decodeFile`）
        //   跑在主线程的 Activity 结果回调里 ⇒ 挑一张原图那段时间界面是纯冻的，
        //   而且冻完之后没有任何一句解释。现在：先上屏一句"正在读这张图"，
        //   真正的 IO/解码用 `withContext(Dispatchers.IO)` 挪走，回来再落状态。
        note = "正在读这张图…"
        cropScope.launch {
            val img = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                loadWallpaperFrom(context, uri)
            }
            if (img != null) {
                UiPrefs.saveWallpaperPreset(SkyPreset.ALBUM.id)
                // v3 #10：换了一张图就把取景清回「居中裁满」—— 上一张的位移/倍数
                // 对新那张毫无意义，留着会出现"新图一上来就是切歪的、还找不到原因"。
                UiPrefs.resetWallpaperCrop()
                UiPrefs.bumpWallpaperRev()
                note = "已换成这张图，只保存在本机。"
            } else {
                // 讀不回來就**不動舊圖**：換圖失敗時最糟的是連原本那張也沒了。
                // ⚠️ 这条 else 也是那个 `runCatching` 唯一会把 OOM 吞掉的地方 ⇒ 必须有字上屏。
                note = "这张图读不出来，换一张试试。原来的壁纸没有动。"
            }
        }
    }

    fun pickPhoto() {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.pageBrush(isDark)),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dims.screenH,
                end = Dims.screenH,
                // 這一頁是殼內全屏覆蓋層，Activity 是 edge-to-edge：
                // 不補狀態欄 inset 的話，返回鍵與「外觀」會壓在時鐘/劉海底下。
                top = statusBarTopPadding() + 10.dp,
                // 這一頁蓋住了底欄；底部只要留系統手勢區的呼吸。
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
        ) {
            item(key = "header") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 板 `.subbar .back{width:30px;height:30px;border-radius:50%;
                        //      border:1px solid var(--line);color:var(--sub)}` ⇒ **27dp 描边圆**，
                        // 里面 15px⇒13dp 的箭头。原来用 IconButton 的默认实心底，比板重一整档。
                        Box(
                            modifier = Modifier
                                .size(27.dp)
                                .clip(CircleShape)
                                .border(1.dp, colors.dividerLine, CircleShape)
                                .clickable(onClickLabel = "返回") { onClose() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.ChevronBackward,
                                contentDescription = "返回",
                                tint = colors.onSurfaceVariantSummary,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "外观",
                            // 板 `.subbar h2{font-size:22px;letter-spacing:-.02em}` × 0.886 ⇒ **20sp**。
                            // 子页标题比首页大日期（31sp）还大一档，层级就倒了
                            //（实测墨高 98px ≈ 30sp，是板的 1.5 倍）。
                            fontSize = 20.sp,
                            letterSpacing = (-0.02).em,
                            fontWeight = Type.weightTitle,
                            color = colors.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        // 板子右上角是「实时预览」这颗状态胶囊；「恢复默认」按板子挪到页面底部。
                        Text(
                            text = "实时预览",
                            fontSize = Type.micro,
                            fontWeight = Type.weightTitle,
                            color = colors.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.primary.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            item(key = "preview") {
                PreviewCard(
                    preset = effectivePreset,
                    wallpaper = wallpaper,
                    dim = UiPrefs.wallpaperDim,
                    soft = UiPrefs.wallpaperSoft,
                    plan = previewPlan,
                    slotVerdict = previewVerdict,
                    // v3 #10：预览卡读的是**同一份**取景状态 ⇒ 下面那块裁切框一动，
                    // 这张卡同一帧就跟着变（不另存一份"预览用的参数"，那是第二真相来源）。
                    panX = UiPrefs.wallpaperPanX,
                    panY = UiPrefs.wallpaperPanY,
                    zoom = UiPrefs.wallpaperZoom,
                )
            }


            // ---- 背景 ----------------------------------------------------
            // 业主 2026-10-29：「全局背景的颜色也改改，做个自定义界面把，让用户自己选择
            // 背景颜色，并且推荐几个主流的好看的颜色」。落点选在预览卡正下方 —— 换底色
            // 时上面那张卡同帧变色，不用滚到别处去看效果。
            item(key = "background-label") { SubSection("背景", "五套主流色 · 深浅各一") }
            item(key = "background-presets") {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    BackgroundPreset.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { preset ->
                                BackgroundSwatch(
                                    preset = preset,
                                    selected = backgroundChoice == preset,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    UiPrefs.saveBackgroundPreset(preset.id)
                                    note = null
                                }
                            }
                            // 末行只有两张时不铺满：权重留白，色卡宽度与上一行对齐。
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // ---- 三行控件（板 `.ctl` 一组，**不加「光感 / 材质」这种板上没有的分组头**）----
            //
            // 板上的顺序就是 柔化 → 暗度 → 玻璃（`wz-v2.html` 里那三条 `.ctl`），
            // 而且玻璃那行是**三段**：液态 / 磨砂 / 关闭。
            // 原来这里被拆成「光感」「材质」两个 `.subsec` 头 + 一枚开关：
            // 头是板上没有的层级，开关又少了中间那一档（复评 2026-10-21）。
            item(key = "controls") {
                Column {
                    // B4-14（业主 2026-10-23「深浅色与 everything 收进外观与壁纸」）：
                    // 主题放在这一组的第一行 —— 它和下面三行一样是「一眼看到就改」的东西。
                    // 「跟随」走 `ColorSchemeMode.System`（Miuix 自己读系统深浅色），
                    // 三档都只改 `uiState.themeMode` + `UiPrefs.saveThemeMode`，`WanzaiTheme` 以它为 key 重建控制器
                    // ⇒ **立即生效，而且记住**（杀进程重进不回落；B4-14 工单第 13 轮补的落盘）。
                    CtlRow(label = "主题", first = true) {
                        Segmented(
                            options = listOf(
                                "跟随" to com.muyao.wanzaihyperos.ui.AppThemeMode.System,
                                "浅色" to com.muyao.wanzaihyperos.ui.AppThemeMode.Light,
                                "深色" to com.muyao.wanzaihyperos.ui.AppThemeMode.Dark,
                            ),
                            selectedValue = themeMode,
                            onSelect = onThemeMode,
                        )
                    }
                    CtlRow(label = "柔化") {
                        val soft = UiPrefs.wallpaperSoft
                        Segmented(
                            options = listOf("关" to 0, "轻" to 26, "柔" to 60),
                            selectedValue = if (soft == 0) 0 else if (soft <= 45) 26 else 60,
                            onSelect = { UiPrefs.saveWallpaperSoft(it) },
                        )
                    }
                    CtlRow(label = "暗度") {
                        ValueSlider(
                            value = UiPrefs.wallpaperDim,
                            max = 80,
                            percent = true,
                            onChange = { UiPrefs.saveWallpaperDim(it) },
                        )
                    }
                    CtlRow(label = "玻璃") {
                        Segmented(
                            options = listOf("液态" to "liquid", "磨砂" to "frost", "关闭" to "off"),
                            // 读的是**当前真实材质状态**，不是单独一个字符串：
                            // 设置页那个「减弱透明度」开关也会关掉玻璃，
                            // 两处各读各的就会出现「界面说液态、屏幕上全是实底」。
                            selectedValue = if (motion.reduceTransparency) "off" else UiPrefs.glassMode,
                            onSelect = { mode ->
                                UiPrefs.saveGlassMode(mode)
                                // 三档落到**两件真实发生的事**上，不是画三个样子骗眼睛：
                                //  · 关闭 = 减弱透明度 ⇒ 所有玻璃走近实底，连采样都不做
                                //  · 磨砂 = 仍然透光 + 糊，但**不折射**（折射量归 0）
                                //  · 液态 = 透光 + 糊 + 折射
                                MotionPrefs.setReduceTransparency(mode == "off")
                                MotionPrefs.setRefractsGlass(mode == "liquid")
                                // ★ 第 1 轮审计（#12 假开关）：`saveGlassMode` 现在连带把
                                //   `reduceTransparency` 也写盘了（同一决定的两面）。
                                //   以前这里只改运行期 ⇒ 选「关闭」后重启，分段控件显示「关闭」
                                //   而画面走磨砂。补一条断言在 `GlassModeWiringTest`。
                            },
                        )
                    }
                    // ★ 业主 2026-10-22 13:2x 签字：三档只管**浮动面**，开关与返回键明确不做。
                    //   这句范围说明必须从 `GlassSurfaceId` 那份名单**拼**出来，不许在这里手写清单 ——
                    //   手写的那一句就是"覆盖全应用"这种兑现不了的承诺（第 2 轮踩过的假说法）。
                    //   只拼 `GLASS` 那几面：预览卡现在不是玻璃面（`GlassSurfaceKind.NOT_GLASS`），
                    //   把它写进"作用于"就是替它撒谎 —— 后面单独一句把它说清楚。
                    Text(
                        text = "液态 / 磨砂 / 关闭 —— 作用于" +
                            GlassSurfaceId.entries
                                .filter { it.kind == GlassSurfaceKind.GLASS }
                                .joinToString("、") { it.label } +
                            "这些浮动的面。开关和返回按钮始终是实心，为了让你一眼看出开关状态；" +
                            "上面那张预览卡是同一套天空的直绘（本来就是平的，不随这三档变化）。",
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // ---- 首頁 ----------------------------------------------------
            item(key = "home-label") { SubSection("首页", "立即生效") }
            item(key = "home-density") {
                CtlRow(label = "密度", first = true) {
                    Segmented(
                        options = listOf("舒适" to "comfortable", "紧凑" to "compact"),
                        selectedValue = if (UiPrefs.homeDensity == "compact") "compact" else "comfortable",
                        onSelect = { UiPrefs.saveHomeDensity(it) },
                    )
                }
            }
            item(key = "home-modules") {
                // 板 `.mods{display:flex;flex-wrap:wrap;gap:6px;padding:0 18px}` +
                //   `span{font-size:9.5px;font-weight:600;border:1px solid var(--line-strong);
                //    border-radius:999px;padding:5px 9px}` +
                //   `.on{color:var(--accent);border-color:transparent;background:var(--accent-soft)}`
                // ⇒ 模块开关是一排**胶囊**，点一下就切；不是四行 switch。
                Column {
                    Text(
                        text = "至少留一块。全部关掉的首页只剩一片天空，那不是简洁，是坏了。",
                        fontSize = Type.micro,
                        color = colors.onSurfaceVariantSummary,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(bottom = 7.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.3.dp)) {
                        ModuleChip("今日课程", "courses")
                        ModuleChip("签到", "rollcall")
                        ModuleChip("待办", "todos")
                        ModuleChip("校园跑", "szty")
                    }
                }
            }

            // ---- 應用 ----------------------------------------------------
            item(key = "apps-label") { SubSection("应用") }
            item(key = "apps") {
                BorderedRow(
                    title = "收起网页服务",
                    subtitle = "应用页只留「原生应用」，网页服务排成一行入口。",
                ) {
                    PillSwitch(
                        checked = UiPrefs.appsWebCollapsed,
                        label = "收起网页服务",
                    ) { UiPrefs.saveAppsWebCollapsed(!UiPrefs.appsWebCollapsed) }
                }
            }

            note?.let { msg ->
                item(key = "note") {
                    Text(
                        text = msg,
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            item(key = "footnote") {
                // 板 `.footnote{text-align:center;font-size:8.5px;color:var(--faint);
                // padding:2px 18px 18px;line-height:1.6}` ⇒ 7.5sp（取 floor 8sp）。
                // 原来那句「只改这一台手机的显示…」挂在标题下面当副标题 ——
                // 板上 `.subbar` 只有 返回 + 标题 + 实时预览，没有第二行。
                Text(
                    text = "只改这一台手机的显示 · 壁纸存在本机，不上传、不涉及账号信息。",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                    lineHeight = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "reset") {
                Text(
                    text = "恢复默认",
                    fontSize = Type.body,
                    fontWeight = Type.weightTitle,
                    color = colors.onSurfaceVariantSummary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dims.containerCorner))
                        .background(colors.onBackground.copy(alpha = 0.05f))
                        .clickable(onClickLabel = "恢复默认外观") {
                            UiPrefs.resetAppearance()
                            // 盘改了就把**运行期那一份**一起推（不然要等下次冷启动才生效，
                            // 中间这段时间就是"界面说液态、画面还是关闭"那类脱钩）。
                            MotionPrefs.setReduceTransparency(UiPrefs.reduceTransparency)
                            MotionPrefs.setRefractsGlass(UiPrefs.glassMode == "liquid")
                            // ⚠️ 这里以前紧跟了两发**写死的** `setReduceTransparency(false)` /
                            //   `setRefractsGlass(true)`：今天与 `resetAppearance()` 的默认值碰巧一致，
                            //   但那是"恢复默认"的**第二份定义** —— 哪天默认值改了，上面那两发读盘的就
                            //   被这里盖掉，界面与画面又脱钩（同一类 bug 第 2 轮已经为 `KEY_GLASS_MODE` 修过一次）。
                            note = "已恢复默认外观。"
                        }
                        .padding(vertical = 12.dp),
                )
            }

            // ---- 壁紙 ----------------------------------------------------
            item(key = "wallpaper-label") { SubSection("壁纸", "内置 4 张 · 相册") }
            item(key = "wallpaper-presets") {
                // 板 `.wps{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;padding:0 18px}`
                // + `.wp{border-radius:13px;aspect-ratio:1/.92;border:1.5px solid transparent}`
                //   `.wp.on{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}`
                //   `.wp span{position:absolute;left:0;right:0;bottom:0;padding:10px 5px 4px;
                //    font-size:7px;font-weight:650;color:#fff;text-align:center;
                //    background:linear-gradient(transparent,rgba(0,0,0,.38))}`
                // ⇒ × 0.886：四列、格间距 7dp、圆角 11.5dp、宽:高 = 1:0.92、
                //   选中是 1.3dp accent 边框 + 外面 2.7dp 的 accent-soft 圈，
                //   **名字写在格子里**（底边一条压暗渐变上），不是格子下面一行字。
                // 外面不再套 AppCard：板上这一排是直接铺在纸面上的。
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        "跟随时刻" to SkyPreset.AUTO,
                        "晨雾" to SkyPreset.MORNING,
                        "暮色" to SkyPreset.DUSK,
                        "夜航" to SkyPreset.NIGHT,
                    ).forEach { (name, value) ->
                        WallpaperTile(
                            name = name,
                            preset = value,
                            wallpaper = null,
                            dim = UiPrefs.wallpaperDim,
                            soft = UiPrefs.wallpaperSoft,
                            on = effectivePreset == value,
                            modifier = Modifier.weight(1f),
                        ) {
                            UiPrefs.saveWallpaperPreset(value.id)
                            note = null
                        }
                    }
                }
            }


            item(key = "wallpaper-album") {
                // 板 `.wp.plus` 就是 `.wps` 那 4 列网格里的**第五格**（虚线 + 「+」，
                // 名字在格子正下方）。上一版我把它撑成一个大方块 + 右侧一整行说明，
                // 于是壁纸区的节奏被切断（复评 2026-10-21）。
                val on = preset == SkyPreset.ALBUM
                Row {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 7.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f / 0.92f)
                                .clip(RoundedCornerShape(Dims.wallpaperTileCorner))
                                .background(colors.onBackground.copy(alpha = 0.04f))
                                .border(
                                    1.dp,
                                    if (isDark) Color.White.copy(alpha = 0.18f)
                                    else Tone.ink.copy(alpha = 0.16f),
                                    RoundedCornerShape(Dims.wallpaperTileCorner),
                                )
                                .clickable(onClickLabel = "从相册选择壁纸") {
                                    // 已经在用相册图 ⇒ 再点就是「换一张」，直接开系统选择器；
                                    // 有图但当前用的是预设 ⇒ 这一点先把那张图切回来，不打断他重选。
                                    if (on || wallpaper == null) {
                                        pickPhoto()
                                    } else {
                                        UiPrefs.saveWallpaperPreset(SkyPreset.ALBUM.id)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (wallpaper != null && on) {
                                Image(
                                    bitmap = wallpaper,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    // 板 `.plusmark{font-size:19px;font-weight:300;color:var(--faint)}` ⇒ 17sp
                                    text = "+",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = colors.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Text(
                            text = "相册",
                            fontSize = Type.micro,
                            color = if (on) colors.primary else colors.onSurfaceVariantSummary,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(),
                        )
                    }
                    // 板 `.wps{repeat(4,1fr)}`：第五格换行后右边三列在板上也是空的。
                    // 说明文字挪回网格**下面一整行**（板 `.wphint` 的顺序），不塞进这一列。
                    Spacer(Modifier.weight(3f))
                }
                // 已经存过一张图时，才给「换一张 / 移除」这两件真能做的事。
                if (wallpaper != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (on) "正在用相册那张 · 点一下换一张"
                            else "已存一张相册图 · 点一下切回去",
                            fontSize = Type.micro,
                            color = colors.onSurfaceVariantSummary,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Dims.chipCorner))
                                .clickable(onClickLabel = "从相册选择壁纸") { pickPhoto() }
                                .padding(vertical = 4.dp),
                        )
                        Text(
                            text = "移除",
                            fontSize = Type.micro,
                            color = colors.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dims.chipCorner))
                                .clickable(onClickLabel = "移除自定义壁纸") {
                                    deleteWallpaper(context)
                                    UiPrefs.bumpWallpaperRev()
                                    UiPrefs.saveWallpaperPreset(SkyPreset.AUTO.id)
                                    note = "已移除自定义壁纸。"
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            item(key = "wallpaper-hint") {
                // 板 `.wphint{padding:7px 18px 0;font-size:8.5px;line-height:1.55;
                // color:var(--faint)}` ⇒ 网格下面一整行，7.5sp（取 floor 8sp）。
                Text(
                    text = "天空会跟着时间换 · 相册那张只存在本机，重启后还在。",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                    lineHeight = 12.sp,
                )
            }

            // ---- v3 #10：相册那张的取景（裁切）--------------------------------
            //
            // 只有真的选了相册图、并且当前就在用相册档时才出现 —— 内置天空没有可裁的东西，
            // 摆一个拖了不动的框就是假开关（本工程栽过好几次）。
            if (wallpaper != null && effectivePreset == SkyPreset.ALBUM) {
                item(key = "wallpaper-crop") {
                    WallpaperCropFrame(
                        wallpaper = wallpaper,
                        panX = UiPrefs.wallpaperPanX,
                        panY = UiPrefs.wallpaperPanY,
                        zoom = UiPrefs.wallpaperZoom,
                        onCrop = { x, y, z -> UiPrefs.saveWallpaperCrop(x, y, z) },
                        onReset = {
                            UiPrefs.resetWallpaperCrop()
                            note = "已回到居中裁满。"
                        },
                    )
                }
            }

            item(key = "privacy") {
                Text(
                    text = "壁纸只保存在这台手机的应用私有目录（files/home_wallpaper.img），" +
                        "不会上传到任何服务器；外观偏好与账号凭据分开存放。",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }

        // 滚上去的正文会钻到状态栏底下（edge-to-edge 的正常行为）。
        // 这一页没有顶栏纱，所以在状态栏那一条盖上与纸面同色的窄纱 ——
        // 否则「柔化」会压在 02:01 上，两行字互相打架。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBarTopPadding() + 6.dp)
                .background(AppColors.pageBrush(isDark)),
        )
    }
}


/**
 * 預覽卡：**和首頁同一套天空畫法**，上面按板放日期、一塊玻璃卡與一條底欄示意。
 *
 * 板的幾何（`wz-v2.html`，全部 × 0.886）：
 *  · `.previewwrap{margin:4px 16px 0;height:172px;border-radius:22px;
 *    border:1px solid rgba(255,255,255,.35)}` ⇒ **152dp 高、19.5dp 圓角**
 *  · `.pv-date{left:16px;top:14px;font-size:21px;font-weight:780;letter-spacing:-.04em}`
 *    ＋ `.pv-date small{font-size:8px;letter-spacing:.14em;font-weight:700;opacity:.55}`
 *    ⇒ 18.6sp 的「今天」，上面一行 7sp 拉開字距的日期
 *  · `.pv-card{left:14px;right:14px;bottom:32px;border-radius:15px;padding:9px 11px;
 *    background:linear-gradient(152deg,rgba(255,255,255,.46),rgba(255,255,255,.22));
 *    border:1px solid rgba(255,255,255,.55);backdrop-filter:blur(18px) saturate(1.5)}`
 *    ⇒ 12.4dp 圓角、8×10dp 內距的一塊真玻璃，內容是 `.k`(6.6sp 拉字距)/`.n`(11.5sp/730)/
 *    `.s`(7sp)/`.pill`(7sp 薄荷底)
 *  · `.pv-bar{left:10px;right:10px;bottom:6px;height:22px;border-radius:8px;
 *    background:rgba(255,255,255,.30);border:1px solid rgba(255,255,255,.34)}`
 *    ＋ `i{width:12px;height:3px;border-radius:99px;background:rgba(26,25,23,.28)}`、`i.on{accent}`
 *    ⇒ 19.5dp 高、7dp 圓角的一條迷你底欄，裡面五根小槓，選中那根是薄荷色
 *
 * 卡上那節課**用的是首页同一份 TodayPlan**（真資料），
 * 沒有就照實寫，不編課。
 */
@Composable
private fun PreviewCard(
    preset: SkyPreset,
    wallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    dim: Int,
    soft: Int,
    plan: com.muyao.wanzaihyperos.data.schedule.TodayPlan?,
    /** 教务那份走到哪一步了；`plan == null` 时那句实话只能由它决定（第 3 轮必修 1）。 */
    slotVerdict: com.muyao.wanzaihyperos.data.schedule.JwVerdict =
        com.muyao.wanzaihyperos.data.schedule.JwVerdict.NOT_STARTED,
    panX: Float = 0f,
    panY: Float = 0f,
    zoom: Float = 1f,
) {
    val colors = MiuixTheme.colorScheme
    val look = skyLookOf(preset, LocalTime.now().hour)
    // 预览必须跟首页使用**同一条材质分支**：减弱透明时首页的「此刻」卡退成近实底、
    // 字改主题色。这里若永远画半透明 + 天空墨色，深色主题 + 白天天空会读不出来，
    // 而且预览与实际行为不一致（审查 2026-10-21）。
    val cardOnGlass = rememberMotion().glassIsTranslucent
    val isDark = com.muyao.wanzaihyperos.ui.design.liquid.isInDarkTheme()
    val cardBg = if (cardOnGlass) {
        if (isDark) Tone.deep.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.42f)
    } else {
        colors.surface.copy(alpha = 0.97f)
    }
    val cardBorder = if (cardOnGlass) {
        Color.White.copy(alpha = if (isDark) 0.14f else 0.55f)
    } else {
        colors.onSurfaceVariantSummary.copy(alpha = 0.28f)
    }
    // ★ 卡上的字色必须跟**卡面本身**的明暗走，不能跟天空走：
    //   浅色那档卡面是 `White @ .42` 的一层磨砂，压在**黄昏**天空上时，
    //   卡面实际亮度 ≈ 天空底色×(1−α) + 白×α ≈ 0.75 —— 这时候 `look.ink`（夜/昏是白）
    //   就变成白压白，真机上「今天的课上完了」整行读不出来（2026-10-21 17:04 截图）。
    val cardAlpha = if (cardOnGlass) (if (isDark) 0.55f else 0.42f) else 0.97f
    val cardLum = if (cardOnGlass) {
        look.bottom.luminance() * (1f - cardAlpha) + (if (isDark) 0.16f else 1f) * cardAlpha
    } else {
        colors.surface.luminance()
    }
    val cardInk = if (cardLum > 0.62f) Tone.ink else Tone.inkDark
    val today = LocalDate.now()
    val next = plan?.remaining?.firstOrNull()
    val headline = when {
        plan == null && slotVerdict == com.muyao.wanzaihyperos.data.schedule.JwVerdict.IN_FLIGHT ->
            "正在取教务课表…"
        plan == null && slotVerdict != com.muyao.wanzaihyperos.data.schedule.JwVerdict.HAS_SLOTS ->
            "按学在城院那份预览"
        plan == null -> "还没有课表数据"
        next != null -> next.courseName.ifBlank { "（未命名课程）" }
        else -> plan.headline
    }
    // ★ 第 3 轮审计必修 1：以前这里把 WAIT / 从没取过 / 这次没取到 / 真没课 压成两句，
    //   于是「教务那份这周没给课」会在教务**根本没取过**的机子上出现 —— 那是替学校下结论。
    //   现在一句话对一个态，措辞全交给 `jwVerdictSentence`（首页与这里共用，不会再各写一遍分叉）。
    val sub = when {
        plan == null -> com.muyao.wanzaihyperos.data.schedule.jwVerdictSentence(slotVerdict) +
            when (slotVerdict) {
                com.muyao.wanzaihyperos.data.schedule.JwVerdict.IN_FLIGHT ->
                    "：要走一次网页登录，第一次可能要十几秒"
                com.muyao.wanzaihyperos.data.schedule.JwVerdict.SETTLED_EMPTY,
                com.muyao.wanzaihyperos.data.schedule.JwVerdict.NO_CONCLUSION,
                com.muyao.wanzaihyperos.data.schedule.JwVerdict.NOT_STARTED ->
                    "，这里画的是学在城院那份，没画不存在的一节"
                else -> ""
            }
        next != null -> listOfNotNull(
            next.room.takeIf { it.isNotBlank() },
            PeriodClock.startOf(UiPrefs.periodTimes, next.periodStart)?.takeIf { it.isNotBlank() }
                ?.let { it + " 上课" },
        ).joinToString(" · ").ifBlank { null }
        else -> plan.detail
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .clip(RoundedCornerShape(Dims.cardCorner))
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.35f),
                RoundedCornerShape(Dims.cardCorner),
            ),
    ) {
        SkyPreview(
            preset = preset,
            wallpaper = wallpaper,
            dim = dim,
            soft = soft,
            panX = panX,
            panY = panY,
            zoom = zoom,
            modifier = Modifier.fillMaxSize(),
        )

        // `.pv-date`
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.4.dp),
        ) {
            Text(
                text = "${today.monthValue} 月 ${today.dayOfMonth} 日 · " +
                    WEEKDAYS.getOrElse(today.dayOfWeek.value - 1) { "" },
                fontSize = Type.micro,
                fontWeight = Type.weightTitle,
                letterSpacing = 0.14.em,
                color = look.ink.copy(alpha = 0.55f),
            )
            Text(
                text = "今天",
                // 板 21px/780/-.04em ⇒ 18.6sp
                fontSize = 19.sp,
                fontWeight = Type.weightTitle,
                letterSpacing = (-0.04).em,
                lineHeight = 20.sp,
                color = look.ink,
            )
        }

        // `.pv-bar`：五根小槓 = 五個板塊，選中跟著本頁（外觀從「設置」進，就亮最後一根）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.9.dp, vertical = 0.dp)
                .padding(bottom = 5.3.dp)
                .fillMaxWidth()
                .height(19.5.dp)
                .clip(RoundedCornerShape(Dims.chipCorner))
                .background(
                    if (isDark) Color.White.copy(alpha = 0.09f)
                    else Color.White.copy(alpha = 0.30f),
                )
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.34f),
                    RoundedCornerShape(Dims.chipCorner),
                ),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 板 `.pv-bar` 里面画的是**五个线性图标**（`.pv-bar .icon{width:9px;opacity:.45}`、
            // `.icon.on{color:var(--accent);opacity:.95}`），不是五根灰杠。
            // 用底栏同一套 `ic_board_*`，预览和真机就是同一排字形。
            listOf(
                R.drawable.ic_board_home, R.drawable.ic_board_cal, R.drawable.ic_board_msg,
                R.drawable.ic_board_grid, R.drawable.ic_board_gear,
            ).forEachIndexed { idx, res ->
                Icon(
                    imageVector = ImageVector.vectorResource(res),
                    contentDescription = null,
                    tint = if (idx == 0) colors.primary
                    else colors.onBackground.copy(alpha = if (isDark) 0.42f else 0.45f),
                    modifier = Modifier.size(8.dp),
                )
            }
        }

        // `.pv-card`
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.4.dp)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dims.containerCorner))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(Dims.containerCorner))
                .padding(horizontal = 9.7.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "此刻 · " + LocalTime.now().format(TIME_HM),
                        fontSize = Type.micro,
                        fontWeight = Type.weightTitle,
                        letterSpacing = 0.10.em,
                        color = cardInk.copy(alpha = 0.60f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        // 板 `.pv-card .n{font-size:13px;font-weight:730;letter-spacing:-.02em}` ⇒ 11.5sp
                        text = headline,
                        fontSize = Type.body,
                        fontWeight = Type.weightTitle,
                        letterSpacing = (-0.02).em,
                        color = cardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (sub != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = sub,
                            fontSize = Type.micro,
                            color = cardInk.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (plan != null && plan.totalToday > 0) {
                    // 板 `.pv-card .pill{font-size:8px;font-weight:650;padding:3px 7px;
                    // background:rgba(46,107,100,.16);color:#2E6B64}`
                    Text(
                        text = "今天 ${plan.totalToday} 节",
                        style = TextStyle(fontFeatureSettings = Type.tabular),
                        fontSize = Type.micro,
                        fontWeight = Type.weightBody,
                        color = if (cardOnGlass) Tone.ok else colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 6.2.dp, vertical = 2.7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubSection(text: String, note: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.5.dp, bottom = 4.4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text,
            fontSize = Type.summary,
            fontWeight = Type.weightTitle,
            letterSpacing = (-0.01).em,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (note.isNotEmpty()) {
            Text(
                text = note,
                fontSize = Type.micro,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * 板 `.ctl{display:flex;align-items:center;gap:10px;padding:9px 18px;
 * border-top:1px solid var(--line)}` + `.lab{font-size:11px;font-weight:600;width:52px}`
 * ⇒ 一行：左边 46dp 标签 + 右边控件；行与行之间一条发丝线，第一行不画。
 */
@Composable
private fun CtlRow(
    label: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier) {
        if (!first) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.7.dp)
                    .background(colors.dividerLine),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = label,
                // 板 `.lab{font-size:11px;font-weight:600}` ⇒ 9.7sp
                fontSize = Type.summary,
                fontWeight = Type.weightBody,
                color = colors.onBackground,
                modifier = Modifier.width(46.dp),
            )
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/**
 * 板 `.ctl .seg{display:flex;background:rgba(26,25,23,.045);border-radius:10px;padding:2px}`
 * + `span{flex:1;text-align:center;font-size:9.5px;font-weight:600;color:var(--sub);
 * padding:5px 0;border-radius:8px}` + `.on{background:var(--card);color:var(--ink);
 * box-shadow:0 2px 6px rgba(0,0,0,.08)}`
 * ⇒ 9dp 圆角的一条浅底槽 + 均分的格；选中那格是**纸白 + 墨字 + 一点投影**（不是薄荷底）。
 * 选中**不换字重**（硬规则 1：加粗让文字变宽，整排会横抖）。
 */
@Composable
private fun <T : Any> Segmented(
    options: List<Pair<String, T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val isDark = com.muyao.wanzaihyperos.ui.design.liquid.isInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.chipCorner))
            .background(colors.onBackground.copy(alpha = 0.045f))
            .padding(1.8.dp),
    ) {
        options.forEach { (label, value) ->
            val on = value == selectedValue
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dims.chipCorner))
                    .background(
                        if (on) {
                            if (isDark) Color.White.copy(alpha = 0.12f)
                            else Color.White.copy(alpha = 0.66f)   // 板 --card
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable(onClickLabel = label) { onSelect(value) }
                    .padding(vertical = 4.4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    // 板 `.ctl .seg span{font-size:9.5px}` ⇒ 8.4sp
                    fontSize = Type.caption,
                    fontWeight = Type.weightBody,
                    color = if (on) colors.onBackground else colors.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 板 `.appline{margin:12px 18px 0;padding:10px 12px;border:1px solid var(--line);border-radius:14px}` */
@Composable
private fun BorderedRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.containerCorner))
            .border(1.dp, colors.dividerLine, RoundedCornerShape(Dims.containerCorner))
            .padding(horizontal = 10.6.dp, vertical = 8.9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                // 板 `.appline .t{font-size:11px;font-weight:620}` ⇒ 9.7sp
                fontSize = Type.summary,
                fontWeight = Type.weightBody,
                color = colors.onBackground,
            )
            Text(
                text = subtitle,
                // 板 `.appline .s{font-size:8.5px;color:var(--faint)}` ⇒ 7.5sp（取 floor 8sp）
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
                lineHeight = 12.sp,
            )
        }
        Spacer(Modifier.width(9.dp))
        trailing()
    }
}

/**
 * 壁纸格：板 `.wp{border-radius:13px;aspect-ratio:1/.92;border:1.5px solid transparent}`
 * + `.wp.on{border-color:var(--accent);box-shadow:0 0 0 3px var(--accent-soft)}`
 * + `.wp span{…bottom:0;padding:10px 5px 4px;font-size:7px;font-weight:650;color:#fff;
 *   background:linear-gradient(transparent,rgba(0,0,0,.38))}`
 * ⇒ × 0.886：11.5dp 圆角、1:0.92、1.3dp 边 + 2.7dp 外圈，**名字在格子底边那条压暗渐变上**。
 */
@Composable
private fun WallpaperTile(
    name: String,
    preset: SkyPreset,
    wallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    dim: Int,
    soft: Int,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 0.92f)
                .clip(RoundedCornerShape(Dims.wallpaperTileCorner))
                .then(
                    if (on) {
                        Modifier.border(
                            2.7.dp,
                            colors.primary.copy(alpha = 0.11f),
                            // 同一枚格子的 clip 与描边统一到 `Dims.wallpaperTileCorner`（11.5dp，設計板 .wp）。
                            RoundedCornerShape(Dims.wallpaperTileCorner),
                        )
                    } else {
                        Modifier
                    },
                )
                .border(
                    if (on) 1.3.dp else 0.dp,
                    colors.primary,
                    RoundedCornerShape(Dims.wallpaperTileCorner),
                )
                .clickable(onClickLabel = "壁纸$name") { onClick() },
        ) {
            SkyPreview(
                preset = preset,
                wallpaper = wallpaper,
                dim = dim,
                soft = soft,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(Dims.wallpaperTileCorner)),
                cornerRadius = Dims.wallpaperTileCorner,
            )
            // 压暗条：板上是 `linear-gradient(transparent, rgba(0,0,0,.38))`，
            // 名字直接白字坐在上面 —— 不靠描边、不靠底色。
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(21.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.38f),
                        ),
                    ),
            )
            Text(
                text = name,
                // 板 `.wp span{font-size:7px;font-weight:650}` ⇒ 6.2sp（取 floor 8sp）
                fontSize = Type.micro,
                fontWeight = Type.weightBody,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.5.dp),
            )
        }
    }
}

/** 板 `.mods span`：一排胶囊 chip，点上切。 */
@Composable
private fun ModuleChip(label: String, id: String) {
    val colors = MiuixTheme.colorScheme
    val on = id in UiPrefs.homeModules
    Text(
        text = label,
        // 板 `.mods span{font-size:9.5px;font-weight:600;padding:5px 9px}` ⇒ 8.4sp / 4.4×8dp
        fontSize = Type.caption,
        fontWeight = Type.weightBody,
        color = if (on) colors.primary else colors.onSurfaceVariantSummary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) colors.primary.copy(alpha = 0.11f) else Color.Transparent)
            .then(
                if (on) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp,
                        if (com.muyao.wanzaihyperos.ui.design.liquid.isInDarkTheme())
                            Color.White.copy(alpha = 0.18f)
                        else Tone.ink.copy(alpha = 0.16f),
                        RoundedCornerShape(50),
                    )
                },
            )
            .clickable(onClickLabel = label) {
                // 至少留一块：全关掉的首页只剩一片天空，那不是简洁是坏了。
                if (on && UiPrefs.homeModules.size > 1) {
                    UiPrefs.setHomeModule(id, false)
                } else if (!on) {
                    UiPrefs.setHomeModule(id, true)
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.4.dp),
    )
}

/**
 * 一块背景色卡。
 *
 * 色卡**画的是这一套真实的渐层**（[BackgroundPreset.lightStops] 三档），不是一坨近似色：
 * 用户在设置里看到的就是回首页看到的那三档。深色档由主题切换时自动跟（枚举里
 * lightStops/darkStops 是成对的），所以这里只画亮档，不在选色区掺一份"当前深浅"的临时态。
 *
 * 色值全部来自 [BackgroundPreset]，本文件里不写任何一个十六进制字面量
 *（`IconSystemTest` 盯着这三个页面的硬编码颜色）。
 */
@Composable
private fun BackgroundSwatch(
    preset: BackgroundPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(Dims.wallpaperTileCorner)
    Column(
        modifier = modifier.clickable(onClickLabel = preset.label) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 0.62f)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = preset.lightStops,
                        start = Offset(0f, 0f),
                        end = Offset(600f, 400f),
                    ),
                )
                .border(
                    width = if (selected) 1.6.dp else 0.8.dp,
                    color = if (selected) colors.primary else colors.dividerLine,
                    shape = shape,
                ),
        ) {
            // 选中不只在描边上说话：右下角一颗实心勾，色弱也分得清。
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        fontSize = Type.micro,
                        fontWeight = Type.weightTitle,
                        color = colors.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = preset.label,
            fontSize = Type.micro,
            fontWeight = if (selected) Type.weightTitle else Type.weightBody,
            color = if (selected) colors.primary else colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(Dims.chipCorner))
            .background(
                if (selected) colors.primary.copy(alpha = 0.14f)
                else colors.surfaceContainer.copy(alpha = 0.55f),
            )
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        colors.primary.copy(alpha = 0.42f),
                        RoundedCornerShape(Dims.chipCorner),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClickLabel = label) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = Type.caption,
            fontWeight = if (selected) Type.weightBody else Type.weightBody,
            color = if (selected) colors.primary else colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

/**
 * 自繪滑桿。
 *
 * 為什麼不用第三方：這個工程沒有現成的 Slider 用法，而 Miuix 的版本要接
 * 一整條 interaction/語義。這裡只需要「拖到哪就是哪、放開才落盤」——
 * 拖的過程中**不寫 SharedPreferences**（否則一次拖動會寫幾十次）。
 */
@Composable
private fun ValueSlider(
    value: Int,
    max: Int,
    /** 板 `.ctl .val` 写的是「38%」这种带单位的数，不是裸整数。 */
    percent: Boolean = false,
    onChange: (Int) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var widthPx by remember { mutableIntStateOf(1) }
    // ★ 状态对象必须只创建一次。上一版是 `remember(value)`：落盘后 value 一变，
    //   remember 会造一个新的 MutableState，而 pointerInput 里捕获的仍是旧对象 ⇒
    //   第二次拖动只改一个没人读的孤儿状态，拇指不动、松手才跳一下（审查 2026-10-21）。
    var local by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { local = value.toFloat() }

    fun apply(x: Float) {
        local = (x / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f) * max
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 板 `.ctl .track{flex:1;height:4px;border-radius:99px;background:var(--line-strong)}` ⇒ 3.5dp 高
        // 命中区仍然保持 30dp（板上拇指能抓），只是**看得见的那条**收成 3.5dp。
        //
        // ★ 這裡必須是 `weight(1f)`，不能是 `fillMaxWidth()`：Row 裡 fillMaxWidth 會把
        //   右邊那列 `.val`（板 30px 寬的數字）整個擠出畫面 —— 17:15 截圖裡暗度的
        //   「24」就是這樣消失的。
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = local,
                        range = 0f..max.toFloat(),
                    )
                    setProgress { target ->
                        local = target.coerceIn(0f, max.toFloat())
                        onChange(local.roundToInt())
                        true
                    }
                }
                .then(
                    Modifier.pointerInput(max) {
                        detectHorizontalDragGestures(
                            onDragStart = { off -> apply(off.x) },
                            onHorizontalDrag = { change, _ -> apply(change.position.x) },
                            onDragEnd = { onChange(local.roundToInt()) },
                            onDragCancel = { onChange(local.roundToInt()) },
                        )
                    },
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                // ★ 這裡必須用 dp.toPx()：直接寫 5f/9f 是「像素」，
                //   在 3x 屏上軌道只有 1.7dp、拇指 3dp —— 看上去像一條頭髮絲。
                // 板 `.ctl .track{height:4px}` / `b{width:14px;height:14px}` × 0.886
                // ⇒ **轨道 3.5dp、拇指 12.4dp**。原来 5dp/18dp 是一根粗管顶着一颗大珠。
                val trackH = 3.5.dp.toPx()
                val thumbR = 6.2.dp.toPx()
                val r = CornerRadius(trackH, trackH)
                drawRoundRect(
                    color = colors.onSurfaceVariantSummary.copy(alpha = 0.20f),
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(size.width, trackH),
                    cornerRadius = r,
                )
                val frac = (local / max.toFloat()).coerceIn(0f, 1f)
                drawRoundRect(
                    color = colors.primary,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(size.width * frac, trackH),
                    cornerRadius = r,
                )
                val thumbX = (size.width * frac).coerceIn(thumbR, size.width - thumbR)
                // 板 `b{background:#fff;box-shadow:0 2px 6px rgba(0,0,0,.25);border:1px solid rgba(0,0,0,.06)}`
                // —— 拇指是一颗**白珠**，不是薄荷环。投影用一条更深的下缘近似（Canvas 里没有阴影）。
                drawCircle(
                    color = Color.Black.copy(alpha = 0.10f),
                    radius = thumbR,
                    center = Offset(thumbX, cy + 0.8.dp.toPx()),
                )
                drawCircle(
                    // 这一处在 Canvas 的绘制作用域里（非 @Composable），只能用同一条表达式的纯计算版
                    color = if (colors.background.luminance() < 0.5f) colors.surface else Color.White,
                    radius = thumbR,
                    center = Offset(thumbX, cy),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.06f),
                    radius = thumbR,
                    center = Offset(thumbX, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.6.dp.toPx()),
                )
            }
        }
        // 板 `.ctl .val{font-size:9.5px;color:var(--faint);width:30px;text-align:right;
        // font-variant-numeric:tabular-nums}` ⇒ 8.4sp / 26.5dp 右对齐等宽数字
        Text(
            text = if (percent) "${local.roundToInt()}%" else "${local.roundToInt()}",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            style = TextStyle(fontFeatureSettings = Type.tabular),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            // 板 `.val{width:30px}` ⇒ 26.5dp 装得下两位，但**这台机 3.5x + 9sp 等宽数字**
            // 实测会把「24」折成两行（17:04 截图）。给 34dp，右对齐不变。
            modifier = Modifier.width(34.dp),
        )
    }
}

/**
 * 這一頁蓋住狀態欄時，圖標要跟著頁面底色走。
 *
 * 首頁在夜航天空上會把圖標改成白色；外觀頁是淺色/深色紙面，
 * 不接這一道就會出現「黑圖標壓在深色紙上」——而且只在從夜航進來時發生。
 */
@Composable
private fun AppearanceStatusIcons(isDark: Boolean) {
    val context = LocalContext.current
    val window = (context as? Activity)?.window ?: return
    DisposableEffect(isDark, window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val prevStatus = controller.isAppearanceLightStatusBars
        val prevNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
        onDispose {
            controller.isAppearanceLightStatusBars = prevStatus
            controller.isAppearanceLightNavigationBars = prevNav
        }
    }
}

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val TIME_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * v3 #10：相册壁纸的**取景框**（拖动 + 双指缩放）。
 *
 * ## 为什么这一框要自己算 slack
 *
 * 手指拖的是像素，落盘的是归一化的 `pan ∈ [-1,1]`。换算必须用
 * [com.muyao.wanzaihyperos.ui.home.coverSlackPx] —— 和首页 `drawCoverBitmap`
 * 同一个函数，两边各写一份就会漂，漂了就是"这里看着没切到、首页却切过头露白边"。
 *
 * ## 为什么这框是 1:0.62
 *
 * 首页那条天空是「屏宽 × 250dp」，1080×2400 上约等于 1:0.62。
 * 用同样的比例，框里看到的构图就是首页 will 看到的构图（WYSIWYG）；
 * 这一页左右各有 12dp 内边距，所以宽度比首页窄一点点 —— 取景是归一化的，不影响结果。
 */
@Composable
internal fun WallpaperCropFrame(
    wallpaper: androidx.compose.ui.graphics.ImageBitmap,
    panX: Float,
    panY: Float,
    zoom: Float,
    onCrop: (Float, Float, Float) -> Unit,
    onReset: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column {
        Text(
            text = "取景：一根手指拖动，两根手指缩放",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        // 取景的三个量在**组合期**取快照（`rememberUpdatedState` 不是 composable 上下文以外能调的），
        // 手势协程里读 `.value` 拿到最新值，却不会因为它们变化而重启检测器 —— 见下面 key 的注释。
        val curPanX = rememberUpdatedState(panX)
        val curPanY = rememberUpdatedState(panY)
        val curZoom = rememberUpdatedState(zoom)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 0.62f)
                .clip(RoundedCornerShape(Dims.cardCorner))
                .background(colors.surfaceContainer)
                // ★★ 独立审计（v3 #10 第 1 轮，与 OOM 同优先级）：key 里以前放了
                //   `panX / panY / zoom` —— 而这三个正是**本回调自己每一帧都在写**的状态
                //   （`onCrop` 落到 `UiPrefs.wallpaperPanX/Y/Zoom`）。
                //   后果是标准的那个手感命门：**每个指针事件都取消并重启同一颗手势协程**，
                //   `detectTransformGestures` 的累积锚点被清零、重新 `awaitFirstDown`
                //   ⇒ "拖到一半变黏、要重新按一次才动"。
                //   key 只留 `wallpaper`（换图才该重启检测器）；三个取景量改走
                //   `rememberUpdatedState`，读到最新值但不触发重启。
                .pointerInput(wallpaper) {
                    detectTransformGestures { _, pan, zoomIn, centroid ->
                        val zoomNow = curZoom.value
                        val (sx, sy) = coverSlackPx(
                            imgW = wallpaper.width,
                            imgH = wallpaper.height,
                            boxW = size.width.toFloat(),
                            boxH = size.height.toFloat(),
                            zoom = zoomNow,
                        )
                        val nx = curPanX.value + if (sx > 0f) pan.x / sx else 0f
                        val ny = curPanY.value + if (sy > 0f) pan.y / sy else 0f
                        // 上限 4× 只是防手滑捏到无穷大，不是设计值。
                        onCrop(nx.coerceIn(-1f, 1f), ny.coerceIn(-1f, 1f), (zoomNow * zoomIn).coerceIn(1f, 4f))
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCoverBitmap(
                    wallpaper,
                    panX = panX,
                    panY = panY,
                    zoom = zoom,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (zoom <= 1.001f && panX == 0f && panY == 0f) "当前：居中裁满"
                else "当前：放大 ${"%.1f".format(zoom)}×",
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
            )
            Text(
                text = "重置取景",
                fontSize = Type.micro,
                color = colors.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dims.chipCorner))
                    .clickable(onClickLabel = "重置取景") { onReset() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
