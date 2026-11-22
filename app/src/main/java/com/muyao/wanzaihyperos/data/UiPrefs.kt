package com.muyao.wanzaihyperos.data

import android.content.Context
import androidx.compose.runtime.getValue
import com.muyao.wanzaihyperos.data.reminder.ClassReminderPlan
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 界面偏好的**唯一落盘点**（SharedPreferences，明文）。
 *
 * ## 为什么现在才加
 *
 * 设置页要做成"真的记住东西"，就必须有落盘：在此之前 `themeMode` 只活在内存里
 * （`AppUiState`），重启就回到「跟随系统」，等于设置页是个摆设。
 * 这一份是**统一**的那一处 —— 不要再在某个页面里偷偷开第二个 SharedPreferences，
 * 否则会出现「一半偏好记得、一半不记得」，那种状态比全不记得更难解释。
 *
 * ## 红线：这里只放界面偏好，**绝不放凭据**
 *
 * 明文存储。凭据一律走 `SecureStore`（加密存储），日志/文件/剪贴板都不行
 * （`net/PortalCredentials.kt`、`session/SecureStore.kt` 那两条纪律）。
 *
 * ## 为什么是 object + Compose 状态
 *
 * 设置页要"改完立刻生效"，同时"下次冷启动也记得"。所以：
 * 值本身是 `mutableStateOf`（改了会重组），写盘在同一个 setter 里做掉，
 * 不给调用方"忘了 save"的机会。
 */
/** 教学周锚点只信这么多天（约一个学期）：再旧就退回「还不知道第几周」，不报错的号。 */
private const val WEEK_ANCHOR_TRUST_DAYS = 120L

object UiPrefs {

    private const val FILE = "wanzai_ui"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_TIMETABLE_GRID = "timetable_grid"
    private const val KEY_REDUCE_TRANSPARENCY = "reduce_transparency"

    /** 返工总单 C4：「减少动态」与「高对比」从系统信号扩成**应用内偏好**（系统那条仍然有效）。 */
    private const val KEY_REDUCE_MOTION = "reduce_motion"
    private const val KEY_HIGH_CONTRAST = "high_contrast_override"
    private const val KEY_PERIOD_TIMES = "period_times"
    private const val KEY_WEEK_ANCHOR = "week_anchor"

    // ── 上课提醒 ────────────────────────────────────────────────────
    // 三件：开关、提前几分钟、上一次真排上了几条。
    // 闹钟清单本身不在偏好里（那是派生快照，见 `ReminderStore`）；
    // 这里只存「他的选择」和「算完的结果条数」。
    private const val KEY_CLASS_REMINDER = "class_reminder"
    private const val KEY_REMINDER_LEAD = "reminder_lead"
    private const val KEY_REMINDER_COUNT = "reminder_count"

    // ── 建议二：提醒隐私与本 App 静音时段（业主 2026-11-07「五项新增设置全做」）──
    // 为什么单独两档而不是一个"勿扰"布尔：用户要的是两件事 ——
    //   · 隐私：通知里不出现课程名与教室（锁屏被别人看到）
    //   · 时段：夜里这段时间别响（但仍排程，静默摘要在，不吞掉事实）
    // 合成一个开关就说不清"我关了到底是不发、还是发但不显示课名"。
    private const val KEY_REMINDER_PRIVATE = "reminder_private"
    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start"
    private const val KEY_QUIET_END = "quiet_end"

    // ── 外观 / 首页（v2）─────────────────────────────────────────────
    private const val KEY_WALLPAPER_PRESET = "wallpaper_preset"
    private const val KEY_BACKGROUND_PRESET = "background_preset"

    /**
     * 容器背景风格：`designed`（保留页内天光与纸色分层，稿子原样）/ `bare`（只留最外层整页背景）。
     *
     * 为什么存字符串而不是布尔：以后真想加第三种（比如「只有卡片、没有天光」）时
     * 不需要再改一次键名，也不会出现「旧布尔值读到一个新语义」的歧义。
     */
    private const val KEY_CONTAINER_STYLE = "container_style"
    private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
    private const val KEY_WALLPAPER_SOFT = "wallpaper_soft"
    private const val KEY_WALLPAPER_REV = "wallpaper_rev"
    // v3 #10：相册壁纸的裁切（归一化位移 + 放大倍数），存在偏好里而不是重画图片文件 ——
    // 原始那张图永远不动，改参数只是换个取景，随时能退回"居中裁满"。
    private const val KEY_WALLPAPER_PAN_X = "wallpaper_pan_x"
    private const val KEY_WALLPAPER_PAN_Y = "wallpaper_pan_y"
    private const val KEY_WALLPAPER_ZOOM = "wallpaper_zoom"
    private const val KEY_HOME_DENSITY = "home_density"
    private const val KEY_GLASS_MODE = "glass_mode"
    private const val KEY_GLASS_OPACITY = "glass_opacity"
    private const val KEY_HOME_MODULES = "home_modules"

    /**
     * 首页模块表的**版本号**（2026-11-01 加「校园跑」时引入）。
     *
     * 这张表的语义是「列出来的就是开着的」，所以新增一个模块**不会**自动出现在旧机器上。
     * 屋主那台盤裡還是 `courses,rollcall,todos,shortcuts` —— 不補這一步，他升級完
     * 打開首頁看不到新卡，等於沒做。版本號讓「補一次」與「他自己關掉」不衝突。
     */
    private const val KEY_HOME_MODULES_REV = "home_modules_rev"

    /** 首页模块表的当前版本（见 [KEY_HOME_MODULES_REV]）。 */
    private const val HOME_MODULES_REV = 2
    private const val KEY_APPS_WEB_COLLAPSED = "apps_web_collapsed"
    // v3 #7：常用那一排的"可自定义"两半 —— 他自己钉的 id，与本 App 记的打开次数。
    private const val KEY_COMMON_PINNED = "common_pinned"
    private const val KEY_APP_OPENS = "app_opens"

    /** 主题：跟随系统 / 浅色 / 深色。存的是枚举名，**不存序号**（序号会随枚举顺序漂）。 */
    var themeModeName by mutableStateOf("System")
        private set

    /**
     * 課表排版：true = 五天課表板（一天一欄），false = 按天列表。
     *
     * v2 把**列表**（帶節次時間軸、今天的課靠上）定為第一眼看到的排版，
     * 網格降為第二選擇；所以默認從 true 改成 false。已經自己選過並落盤的仍照他的選。
     */
    var timetableGrid by mutableStateOf(false)
        private set

    /**
     * 减弱玻璃透明度：true = 所有半透明面退成"近实底 + 明确描边"，不做模糊与折射。
     *
     * ⚠️ **Android 没有系统级的"减弱透明度"开关**（那是 iOS/macOS 的），所以这条只能我们自己给。
     *   界面上也因此**不写"跟随系统"** —— 系统里没有这个东西可跟，写了就是骗人。
     *   另外两条（动画时长缩放、高对比文字）是真·系统信号，走
     *   [com.muyao.wanzaihyperos.ui.design.MotionPrefs]。
     */
    var reduceTransparency by mutableStateOf(false)
        private set

    /**
     * 应用内「减少动态」。true = 只保留淡入淡出，位移/弹簧一律不动。
     *
     * 与系统开关是**或**关系：系统关了也动不了，系统开着但这里关了同样动不了。
     * 稿要的是应用内自己那条，系统那条降级成次级说明（返工总单 A3/C4）。
     */
    var reduceMotion by mutableStateOf(false)
        private set

    /** 应用内「高对比显示」：强制实色承载层。与系统高对比是**或**关系。 */
    var highContrastOverride by mutableStateOf(false)
        private set

    /**
     * 節次時間表：每個元素是「上課時間,下課時間」，第 N 個對應第 N 節。
     *
     * ⚠️ **默認是空的**。學校接口沒有「節次→時間」表（`TronScheduleSlot` 檔頭那條），
     *   編一個默認值就是造數據 —— 所以沒填之前課表左列只標節次號，填了才顯示時間。
     */
    var periodTimes by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    /** 上课前提醒。**默认关** —— 没经他同意就弹通知，是替他做决定。 */
    var classReminderEnabled by mutableStateOf(false)
        private set

    /**
     * 提前几分钟。只认 [ClassReminderPlan.LEAD_CHOICES]；盘里是别的数（旧版本或手改）
     * 就退回默认 —— 不把一个实际不生效的分钟数显示在界面上。
     */
    var classReminderLeadMinutes by mutableStateOf(ClassReminderPlan.DEFAULT_LEAD_MINUTES)
        private set

    /** 上一次真排上了几条（**-1 = 还没排过**）。只用来照实回答，不参与任何判断。 */
    var reminderPlannedCount by mutableStateOf(-1)
        private set

    /**
     * 提醒隐私：通知里**不出现课程名与教室**，只说"即将上课"。
     *
     * 默认 true。理由：这不是功能开关，是**泄露面** —— 默认不泄露比默认方便更对；
     * 想看课名的人主动关掉它只要一步，反过来（默认显示、事后才知道锁屏被看到）没法挽回。
     */
    var reminderPrivate by mutableStateOf(true)
        private set

    /**
     * 静音时段：这段时间内**不发声音**，但仍保留一条无声摘要。
     *
     * 默认关（22:00–07:00 只是候选值，不替他开着）。
     * ★ 只约束本 App 的上课提醒；不碰系统勿扰，也不宣称影响别的客户端。
     */
    var quietHoursEnabled by mutableStateOf(false)
        private set

    /** 静音开始时（0..23，整点）。 */
    var quietHoursStart by mutableStateOf(22)
        private set

    /** 静音结束时（0..23，整点）。跨午夜是正常情况（22 → 7）。 */
    var quietHoursEnd by mutableStateOf(7)
        private set

    // ── 首页外观（v2「外观」子页）───────────────────────────────────
    //
    // 这几项和「主题模式」一样是**纯界面偏好**，所以都落在这一个文件里：
    // 外观页改一下、首页立刻重组，冷启动又还在。不要另开第二个 SharedPreferences。

    /** 首页模块 id：顺序也是外观页里列的先后顺序。 */
    /**
     * 首页模块 id（顺序 = 外观页里列的先后）。
     *
     * ⚠️ 「shortcuts」已删（2026-10-23 工单第 13 轮）：它在首页**零消费**（首页只判 courses/rollcall/todos），
     * 拨了它什么都不会变 —— 那就是个假开关。补消费分支等于顺手加需求，所以按口径删掉，
     * 由 `AppearanceOneStopTest` 守着「每个 id 在首页必须有读点」。
     */
    private val HOME_MODULES = listOf("courses", "rollcall", "todos", "szty")

    /**
     * 各版本新加的模組 id（遷移用，按版本往後追加）。
     *
     * 只補**新加的那幾個**，不是把整張表蓋回去 —— 否則他之前親手關掉的課程/待辦
     * 會在升級後自己亮起來，那比不補更糟。
     */
    private val HOME_MODULES_ADDED_IN_V2 = listOf("szty")

    private val HOME_DENSITIES = listOf("comfortable", "compact")
    private val GLASS_MODES = listOf("liquid", "frost", "off")

    /**
     * 壁纸预设白名单。
     *
     * ★ G01（本轮修复）：这里原来漏了 `slate`。
     *
     * `SkyPreset` 里明明有 `SLATE("slate","青灰",…)`，外观页也给了「青灰」那张
     * 磁贴，但 `saveWallpaperPreset` 用 `name.takeIf { it in WALLPAPER_PRESETS } ?: "auto"`
     * 过滤 —— 于是**选青灰落盘变 auto**，重启后回到"跟随时刻"。
     * 用户看到的是"挑了青灰，第二天自己变回去了"。
     *
     * 注意：`slate` 属于**壁纸**域，不属于**背景色**域。`BACKGROUND_PRESETS` 里
     * 依然不能有它（那是五套页面底色，青灰是天空，两回事）。
     */
    private val WALLPAPER_PRESETS =
        listOf("auto", "morning", "dusk", "night", "slate", "aurora", "blossom", "album")

    /** 背景预设的可接受值。与 `ui/design/BackgroundPreset.entries` 的 id 一一对应；
     *  这里是**字符串白名单**而不是直接引用枚举：偏好层不 import UI 层（同一纪律，见 `saveReduceTransparency`）。 */
    private val BACKGROUND_PRESETS = listOf("porcelain", "cream", "mist", "sakura", "lake")

    /** 容器背景风格的可接受值，与 `ui/design/ContainerStyle.entries` 的 id 一一对应。 */
    private val CONTAINER_STYLES = listOf("designed", "bare")


    /**
     * 壁纸预设：`auto`（跟着时间走的内置天空）/ `morning` / `dusk` / `night` / `album`。
     *
     * `album` 只在应用私有目录真有一张图时才有意义；文件没了就退回 `auto`
     *（**不显示一个坏掉的相册壁纸**）。
     * ★ 判据的真实落点是 `HomeStudio.decodeWallpaper`（第 6 轮审计指出：这句注释以前指的
     *   `wallpaperExists` **全工程根本不存在**，grep 唯一命中就是这条注释自己）。
     */
    /** 全局背景预设 id（业主 2026-10-29「让用户自己选择背景颜色」）。读它的人是 `AppColors`。 */
    var backgroundPreset by mutableStateOf("porcelain")
        private set

    /**
     * 容器背景风格 id：`designed` = 保留页内天光与纸张底色（稿子原样）；
     * `bare` = 去掉容器背景，只留最外层那一层整页背景。
     *
     * 读它的人是 `WanzaiRoot`（根上读一次，经 `LocalContainerStyle` 往下传），
     * 以及设置页/外观页那个分段控件。
     */
    var containerStyle by mutableStateOf("designed")
        private set

    var wallpaperPreset by mutableStateOf("auto")
        private set

    /** 壁纸上的暗度（0..80）：保证白字在浅色天空上也读得清。 */
    var wallpaperDim by mutableStateOf(24)
        private set

    /** 柔化（0..100）：在天空上盖一层同色调薄雾，让下面压着的玻璃字更安静。 */
    var wallpaperSoft by mutableStateOf(26)
        private set

    /**
     * v3 #10：相册壁纸的取景。`pan ∈ [-1, 1]`（±1 就是推到还能动的边上，不会露底）、
     * `zoom ≥ 1`（1 = 以前的"居中裁满"）。内置天空那几档用不上这三个数。
     */
    var wallpaperPanX by mutableStateOf(0f)
        private set
    var wallpaperPanY by mutableStateOf(0f)
        private set
    var wallpaperZoom by mutableStateOf(1f)
        private set

    /** 壁纸变化的版本号：换图/删图后 +1，让 `remember` 过的位图与预览跟着失效。 */
    var wallpaperRev by mutableStateOf(0)
        private set

    /**
     * 玻璃档位：`liquid`（透光 + 糊 + 折射，默认）/ `frost`（透光 + 糊，**不折射**）/
     * `off`（不透明，走近实底）。对应板上外观页那三格 `.ctl .seg`「液态 / 磨砂 / 关闭」。
     *
     * ★ 这一档**不是装饰**：`off` 由 [com.muyao.wanzaihyperos.ui.design.MotionPrefs.reduceTransparency]
     *   实现（连采样层都不挂，见 T67 那条黑玻璃事故），`frost` 由
     *   `MotionPrefs.refractsGlass = false` 实现（折射量归 0，只留模糊）。
     *   两者都必须落到真实的绘制分支上，否则设置项就是骗眼睛的。
     */
    /**
     * 应用页「整理」模式的开关 —— **故意不落盘**：这是当场的一次整理，重启还替他开着整理模式
     * 会让人以为界面坏了。
     *
     * ★ 为什么要放在这里而不是页面里 `remember`（独立审计 v3 #7 第 1 轮 B-10 / 第 3 轮必修 7）：
     *   板上的「整理」是**顶栏右边那颗**（`WanzaiMainShell` 里的 `AppsTidyButton`），
     *   而它以前 `onClick = { showAppearance = true }` 跳去外观页 —— 同屏两个「整理」两种含义。
     *   现在两处共用这一份状态，顶栏那颗真的进/出这一页的整理模式。
     */
    val appsTidy = mutableStateOf(false)

    /**
     * 玻璃面通透度：0..100，默认 **50**（= 原 App 液态/磨砂的透明度中值）。
     *
     * 业主 2026-11-07 对稿：设计稿 appearance 页有「通透度 · 新增」滑块，
     * 左「更厚实」右「更通透」，默认 50%。落点不是再画一层膜，而是
     * `ui/design/liquid/IosChrome.kt` 里 `GlassSurface` 那颗 `baseAlpha` 乘一个系数 ——
     * 所有玻璃面（顶栏纱 / 首页焦点卡 / 搜索 / 底栏）同源，
     * 与设计稿「只影响承载层，正文保持清楚」那句一致。
     */
    var glassOpacity by mutableStateOf(50)
        private set

    var glassMode by mutableStateOf("liquid")
        private set

    /** 首页密度：`comfortable`（默认）/ `compact`。只影响间距，不改信息量。 */
    var homeDensity by mutableStateOf("comfortable")
        private set

    /**
     * 首页显示哪些模块：逗号分隔的 id（见 [HOME_MODULES]）。
     *
     * 用「**列出来的就是开着的**」而不是「列出来的是关掉的」：以后加新模块时，
     * 旧用户的偏好串里没有它 ⇒ 默认是**关**，不会突然多出一块他没同意的东西。
     * 这与「新增功能默认不打扰」一致；想开就去外观页勾上。
     */
    var homeModules by mutableStateOf(HOME_MODULES.toSet())
        private set

    /** 应用页：把「网页服务」收成一行入口（默认展开，保持与应用页原有信息量一致）。 */
    var appsWebCollapsed by mutableStateOf(false)
        private set

    /**
     * v3 #7：他在「常用」那一排**亲手钉住**的应用 id，按钉的顺序（先钉的在前）。
     * 空 = 一条都没钉，那一排完全交给 [com.muyao.wanzaihyperos.data.apps.pickCommon] 的默认算法。
     */
    var commonPinned by mutableStateOf(emptyList<Int>())
        private set

    /**
     * v3 #7：本 App 自己记的打开次数（id → 次数）。
     *
     * ⚠️ 只记**从这个 App 里点出去**的那几下（应用页的格子与整理页），
     * 学校那边到底用没用我们看不见，也**不假装看得见** ——
     * 所以这一档在 [com.muyao.wanzaihyperos.data.apps.pickCommon] 里排在学校名单之后。
     */
    var appOpenCounts by mutableStateOf(emptyMap<Int, Int>())
        private set

    /**
     * 課表**來源**這條偏好已經刪掉（业主 2026-10-20：「数据来源那行去掉，统一用教务系统的」）。
     *
     * 為什麼不留一個「默認值」：留著就等於留一條**没人能改、也没人读**的配置，
     * 而它看起來像还能选。現在課表固定讀教務（學在城院那份只在教务取不到時當兜底），
     * 代碼裡就一處事實，不在偏好裡。
     */
    var themeCustomization by mutableStateOf(ThemeCustomization())
        private set

    private var loaded = false

    /** 冷启动时调一次（幂等）。 */
    fun load(context: Context) {
        holder = context.applicationContext
        if (loaded) return
        runCatching {
            com.muyao.wanzaihyperos.ui.home.AppearanceUndo.recoverInterrupted(context)
            com.muyao.wanzaihyperos.ui.home.WallpaperLibrary.recoverPending(context)
        }
        loaded = true
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        // 旧存档里可能残留已移除的字段（如贴纸 stickers）；忽略未知键，否则整块主题设置会被丢回默认值。
        themeCustomization = runCatching { kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<ThemeCustomization>(p.getString("theme_customization_v1", null) ?: "{}").validated() }.getOrDefault(ThemeCustomization())
        themeModeName = p.getString(KEY_THEME, "System") ?: "System"
        timetableGrid = p.getBoolean(KEY_TIMETABLE_GRID, false)
        reduceTransparency = p.getBoolean(KEY_REDUCE_TRANSPARENCY, false)
        reduceMotion = p.getBoolean(KEY_REDUCE_MOTION, false)
        highContrastOverride = p.getBoolean(KEY_HIGH_CONTRAST, false)
        classReminderEnabled = p.getBoolean(KEY_CLASS_REMINDER, false)
        classReminderLeadMinutes = p.getInt(KEY_REMINDER_LEAD, ClassReminderPlan.DEFAULT_LEAD_MINUTES)
            .takeIf { it in ClassReminderPlan.LEAD_CHOICES } ?: ClassReminderPlan.DEFAULT_LEAD_MINUTES
        reminderPlannedCount = p.getInt(KEY_REMINDER_COUNT, -1)
        reminderPrivate = p.getBoolean(KEY_REMINDER_PRIVATE, true)
        quietHoursEnabled = p.getBoolean(KEY_QUIET_ENABLED, false)
        quietHoursStart = p.getInt(KEY_QUIET_START, 22).coerceIn(0, 23)
        quietHoursEnd = p.getInt(KEY_QUIET_END, 7).coerceIn(0, 23)
        periodTimes = (p.getString(KEY_PERIOD_TIMES, "") ?: "")
            .split("|")
            .mapNotNull { row ->
                val parts = row.split(",")
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
            }
        (p.getString(KEY_WEEK_ANCHOR, "") ?: "")
            .split(":")
            .takeIf { it.size == 2 }
            ?.let { (d, w) ->
                val day = d.toLongOrNull()
                val week = w.toIntOrNull()
                if (day != null && week != null && week > 0) weekAnchor = day to week
            }

        wallpaperPreset = (p.getString(KEY_WALLPAPER_PRESET, "auto") ?: "auto")
            .takeIf { it in WALLPAPER_PRESETS } ?: "auto"
        backgroundPreset = (p.getString(KEY_BACKGROUND_PRESET, "porcelain") ?: "porcelain")
            .takeIf { it in BACKGROUND_PRESETS } ?: "porcelain"
        containerStyle = (p.getString(KEY_CONTAINER_STYLE, "designed") ?: "designed")
            .takeIf { it in CONTAINER_STYLES } ?: "designed"
        wallpaperDim = p.getInt(KEY_WALLPAPER_DIM, 24).coerceIn(0, 80)
        wallpaperSoft = p.getInt(KEY_WALLPAPER_SOFT, 26).coerceIn(0, 100)
        wallpaperPanX = p.getFloat(KEY_WALLPAPER_PAN_X, 0f).coerceIn(-1f, 1f)
        wallpaperPanY = p.getFloat(KEY_WALLPAPER_PAN_Y, 0f).coerceIn(-1f, 1f)
        wallpaperZoom = p.getFloat(KEY_WALLPAPER_ZOOM, 1f).coerceAtLeast(1f)
        wallpaperRev = p.getInt(KEY_WALLPAPER_REV, 0)
        homeDensity = (p.getString(KEY_HOME_DENSITY, "comfortable") ?: "comfortable")
            .takeIf { it in HOME_DENSITIES } ?: "comfortable"
        glassOpacity = p.getInt(KEY_GLASS_OPACITY, 50).coerceIn(0, 100)
        glassMode = (p.getString(KEY_GLASS_MODE, "liquid") ?: "liquid")
            .takeIf { it in GLASS_MODES } ?: "liquid"
        val modules = (p.getString(KEY_HOME_MODULES, null))
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
        // 模块表的版本迁移（见 KEY_HOME_MODULES_REV）：rev 1 ⇒ 2 补上「校园跑」。
        // 只寫 rev 不寫表是錯的：下次冷啟動照舊盤讀回來，新模組又消失一次。
        val modulesRev = p.getInt(KEY_HOME_MODULES_REV, 1)
        if (modules != null) {
            if (modulesRev < HOME_MODULES_REV) {
                val merged = modules + HOME_MODULES_ADDED_IN_V2
                homeModules = merged
                // 落盤時按 HOME_MODULES 過一遍：盤裡那些已經刪掉的舊 id（例如 shortcuts）順手清掉。
                p.edit()
                    ?.putString(KEY_HOME_MODULES, HOME_MODULES.filter { it in merged }.joinToString(","))
                    ?.putInt(KEY_HOME_MODULES_REV, HOME_MODULES_REV)
                    ?.apply()
            } else {
                homeModules = modules
            }
        }
        appsWebCollapsed = p.getBoolean(KEY_APPS_WEB_COLLAPSED, false)
        commonPinned = (p.getString(KEY_COMMON_PINNED, null) ?: "")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
        appOpenCounts = (p.getString(KEY_APP_OPENS, null) ?: "")
            .split(",")
            .mapNotNull { kv ->
                val parts = kv.split(":")
                val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
                val n = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@mapNotNull null
                if (n <= 0) null else id to n
            }
            .toMap()
    }

    /**
     * v3 #7：钉住 / 撤下某一格常用。撤下后它回到默认算法的池子里，不会被永久藏起来。
     *
     * ★ 钉到第 5 个**不是静默丢掉**（独立审计抓到过这个）：常用恒 ≤4 是硬判据，
     *   所以这里按"最早钉的先撤"挤掉一个，界面上那一行也会提前说明。
     */
    fun toggleCommonPinned(id: Int, limit: Int = com.muyao.wanzaihyperos.data.apps.COMMON_LIMIT) {
        commonPinned = if (id in commonPinned) {
            commonPinned - id
        } else {
            (commonPinned + id).takeLast(limit.coerceAtLeast(1))
        }
        prefs()?.edit()?.putString(KEY_COMMON_PINNED, commonPinned.joinToString(","))?.apply()
    }

    /** v3 #7：记一次"从他手里点出去了"。只在应用页/整理页点格子时调，不做任何预测。 */
    fun recordAppOpen(id: Int) {
        val next = appOpenCounts + (id to ((appOpenCounts[id] ?: 0) + 1))
        appOpenCounts = next
        prefs()?.edit()
            ?.putString(KEY_APP_OPENS, next.entries.joinToString(",") { "${it.key}:${it.value}" })
            ?.apply()
    }

    /** 恢复默认：把本 App 记的打开次数与他钉的记录一起清掉。 */
    fun clearCommonOverrides() {
        commonPinned = emptyList()
        appOpenCounts = emptyMap()
        prefs()?.edit()?.remove(KEY_COMMON_PINNED)?.remove(KEY_APP_OPENS)?.apply()
    }

    fun saveThemeMode(name: String) {
        themeModeName = name
        prefs()?.edit()?.putString(KEY_THEME, name)?.apply()
    }

    /**
     * 只落盘。**运行期那份（`MotionPrefs`）由写它的那一侧同步** ——
     * 偏好层反过来 import UI 层，就会变成"数据依赖界面"，以后拆包/上单测都拆不开。
     */
    fun saveReduceTransparency(on: Boolean) {
        reduceTransparency = on
        prefs()?.edit()?.putBoolean(KEY_REDUCE_TRANSPARENCY, on)?.apply()
    }

    fun saveReduceMotion(on: Boolean) {
        reduceMotion = on
        prefs()?.edit()?.putBoolean(KEY_REDUCE_MOTION, on)?.apply()
    }

    fun saveHighContrastOverride(on: Boolean) {
        highContrastOverride = on
        prefs()?.edit()?.putBoolean(KEY_HIGH_CONTRAST, on)?.apply()
    }

    fun saveTimetableGrid(grid: Boolean) {
        timetableGrid = grid
        prefs()?.edit()?.putBoolean(KEY_TIMETABLE_GRID, grid)?.apply()
    }

    fun savePeriodTimes(rows: List<Pair<String, String>>) {
        periodTimes = rows
        prefs()?.edit()?.putString(
            KEY_PERIOD_TIMES,
            rows.joinToString("|") { "${it.first},${it.second}" },
        )?.apply()
    }

    fun saveClassReminderEnabled(on: Boolean) {
        classReminderEnabled = on
        // 关掉时「已排几条」也退回未知：留着旧数会在关掉的下一刻还写着「已排 6 条」。
        if (!on) reminderPlannedCount = -1
        prefs()?.edit()?.putBoolean(KEY_CLASS_REMINDER, on)?.apply()
    }

    fun saveReminderLead(minutes: Int) {
        val v = minutes.takeIf { it in ClassReminderPlan.LEAD_CHOICES }
            ?: ClassReminderPlan.DEFAULT_LEAD_MINUTES
        classReminderLeadMinutes = v
        prefs()?.edit()?.putInt(KEY_REMINDER_LEAD, v)?.apply()
    }

    fun saveReminderCount(count: Int) {
        reminderPlannedCount = count
        prefs()?.edit()?.putInt(KEY_REMINDER_COUNT, count)?.apply()
    }

    fun saveReminderPrivate(on: Boolean) {
        reminderPrivate = on
        prefs()?.edit()?.putBoolean(KEY_REMINDER_PRIVATE, on)?.apply()
    }

    fun saveQuietHoursEnabled(on: Boolean) {
        quietHoursEnabled = on
        prefs()?.edit()?.putBoolean(KEY_QUIET_ENABLED, on)?.apply()
    }

    fun saveQuietHours(start: Int, end: Int) {
        quietHoursStart = start.coerceIn(0, 23)
        quietHoursEnd = end.coerceIn(0, 23)
        prefs()?.edit()
            ?.putInt(KEY_QUIET_START, quietHoursStart)
            ?.putInt(KEY_QUIET_END, quietHoursEnd)
            ?.apply()
    }

    /**
     * 现在这一刻是否落在静音时段里。
     *
     * 跨午夜要处理对：22→7 表示 [22,24) ∪ [0,7)，不是 "22 ≤ h < 7"（那个式子永远为假）。
     */
    fun inQuietHours(hourOfDay: Int): Boolean {
        if (!quietHoursEnabled) return false
        val h = hourOfDay.coerceIn(0, 23)
        return if (quietHoursStart <= quietHoursEnd) {
            h in quietHoursStart until quietHoursEnd
        } else {
            h >= quietHoursStart || h < quietHoursEnd
        }
    }

    // ── 外观 / 首页 ─────────────────────────────────────────────────

    fun saveWallpaperPreset(name: String) {
        val v = name.takeIf { it in WALLPAPER_PRESETS } ?: "auto"
        wallpaperPreset = v
        prefs()?.edit()?.putString(KEY_WALLPAPER_PRESET, v)?.apply()
    }

    fun saveBackgroundPreset(id: String) {
        val v = id.takeIf { it in BACKGROUND_PRESETS } ?: "porcelain"
        backgroundPreset = v
        prefs()?.edit()?.putString(KEY_BACKGROUND_PRESET, v)?.apply()
    }

    /**
     * 改容器背景风格。非法值一律落回 `designed`（稿子原样是安全的那一版）。
     *
     * 只有两份状态要同步：这里的 `mutableStateOf`（重组用）与盘上的字符串。
     * 不需要像 `reduceTransparency` 那样再叫醒 `MotionPrefs` —— 那边是 `@Volatile` 字段，
     * 这里读的就是 Compose 状态本身。
     */
    fun saveContainerStyle(id: String) {
        val v = id.takeIf { it in CONTAINER_STYLES } ?: "designed"
        containerStyle = v
        prefs()?.edit()?.putString(KEY_CONTAINER_STYLE, v)?.apply()
    }

    fun saveWallpaperDim(value: Int) {
        val v = value.coerceIn(0, 80)
        wallpaperDim = v
        prefs()?.edit()?.putInt(KEY_WALLPAPER_DIM, v)?.apply()
    }

    fun saveWallpaperSoft(value: Int) {
        val v = value.coerceIn(0, 100)
        wallpaperSoft = v
        prefs()?.edit()?.putInt(KEY_WALLPAPER_SOFT, v)?.apply()
    }

    /**
     * v3 #10：存一次取景（裁切）。三个数一起写 —— 拖动过程中每帧都会调，
     * 分开写会出现"位移落盘了、倍数还没落"的半状态，预览卡就会跳一下。
     */
    fun saveWallpaperCrop(panX: Float, panY: Float, zoom: Float) {
        val x = panX.coerceIn(-1f, 1f)
        val y = panY.coerceIn(-1f, 1f)
        val z = zoom.coerceAtLeast(1f)
        wallpaperPanX = x
        wallpaperPanY = y
        wallpaperZoom = z
        prefs()?.edit()
            ?.putFloat(KEY_WALLPAPER_PAN_X, x)
            ?.putFloat(KEY_WALLPAPER_PAN_Y, y)
            ?.putFloat(KEY_WALLPAPER_ZOOM, z)
            ?.apply()
    }

    internal fun publishRecoveredWallpaper(panX: Float, panY: Float, zoom: Float, revision: Int) {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            wallpaperPanX = panX
            wallpaperPanY = panY
            wallpaperZoom = zoom
            wallpaperPreset = "album"
            containerStyle = "designed"
            wallpaperRev = revision
        }
    }

    /** 退回「居中裁满」—— 换壁纸时也要清掉，否则新图会沿用上一张的取景。 */
    fun resetWallpaperCrop() {
        saveWallpaperCrop(0f, 0f, 1f)
    }

    /**
     * 换过/删过壁纸后调一次：版本号一变，所有 `remember` 过这张图的界面都会重读文件。
     *
     * 只改文件不通知的话，首页会继续画旧位图 —— 用户看到的是「换了壁纸没反应」。
     */
    fun bumpWallpaperRev() {
        wallpaperRev += 1
        prefs()?.edit()?.putInt(KEY_WALLPAPER_REV, wallpaperRev)?.apply()
    }

    /**
     * 存玻璃档位，并**在同一次写盘里把「减弱透明度」那条一起改掉**。
     *
     * ★ 独立审计（v3 #12 第 1 轮，严重度高于所有外观项）：外观页那颗原来只调
     *   `MotionPrefs.setReduceTransparency(...)`（运行期），**没调 `saveReduceTransparency`**（落盘）。
     *   后果是重启后 `WanzaiRoot` 按盘里的 `reduceTransparency=false` 推回，
     *   而 `glassMode` 存的还是 `"off"` ⇒ **分段控件显示「关闭」、画面走磨砂** ——
     *   业主点名禁的就是这种假开关。
     *   修法放在这里而不是调用点：这两条键**天生是同一个决定的两面**，
     *   谁都可以忘记调第二句，但一个函数不能忘记自己的后半段。
     */
    fun saveGlassMode(mode: String) {
        val v = mode.takeIf { it in GLASS_MODES } ?: "liquid"
        val off = v == "off"
        glassMode = v
        reduceTransparency = off
        prefs()?.edit()
            ?.putString(KEY_GLASS_MODE, v)
            ?.putBoolean(KEY_REDUCE_TRANSPARENCY, off)
            ?.apply()
    }

    /**
     * 存通透度。取值夹到 0..100；写盘与运行期状态一次完成，
     * 不另留"调用点记得再推一次"的第二份责任。
     */
    fun saveGlassOpacity(v: Int) {
        val n = v.coerceIn(0, 100)
        glassOpacity = n
        prefs()?.edit()?.putInt(KEY_GLASS_OPACITY, n)?.apply()
    }

    fun saveHomeDensity(name: String) {
        val v = name.takeIf { it in HOME_DENSITIES } ?: "comfortable"
        homeDensity = v
        prefs()?.edit()?.putString(KEY_HOME_DENSITY, v)?.apply()
    }

    fun setHomeModule(id: String, on: Boolean) {
        if (id !in HOME_MODULES) return
        // 至少留一块：全关掉的首頁只剩一片天空，那不是「简洁」，是坏了。
        if (!on && homeModules.size <= 1) return
        val next = if (on) homeModules + id else homeModules - id
        homeModules = next
        // 顺序固定：偏好串稳定，diff 才有意义（也方便人肉排查）。
        prefs()?.edit()?.putString(KEY_HOME_MODULES, HOME_MODULES.filter { it in next }.joinToString(","))
            ?.apply()
    }

    fun saveAppsWebCollapsed(collapsed: Boolean) {
        appsWebCollapsed = collapsed
        prefs()?.edit()?.putBoolean(KEY_APPS_WEB_COLLAPSED, collapsed)?.apply()
    }

    fun appearanceSnapshot() = AppearanceSettings(
        themeModeName, backgroundPreset, glassMode, glassOpacity, containerStyle, wallpaperPreset,
        wallpaperDim, wallpaperSoft, wallpaperPanX, wallpaperPanY, wallpaperZoom,
        homeDensity, homeModules.toSet(), appsWebCollapsed, reduceTransparency, themeCustomization, reduceMotion, highContrastOverride,
    )

    /** Commit only appearance keys, then publish the same snapshot as one UI change. */
    fun applyAppearance(value: AppearanceSettings, persistSynchronously: Boolean = false) {
        val v = value.validated()
        val editor = prefs()?.edit()
            ?.putString(KEY_THEME, v.theme)
            ?.putString(KEY_BACKGROUND_PRESET, v.background)
            ?.putString(KEY_GLASS_MODE, v.glass)
            ?.putInt(KEY_GLASS_OPACITY, v.opacity)
            ?.putString(KEY_CONTAINER_STYLE, v.container)
            ?.putString(KEY_WALLPAPER_PRESET, v.sky)
            ?.putInt(KEY_WALLPAPER_DIM, v.dim)
            ?.putInt(KEY_WALLPAPER_SOFT, v.soft)
            ?.putFloat(KEY_WALLPAPER_PAN_X, v.panX)
            ?.putFloat(KEY_WALLPAPER_PAN_Y, v.panY)
            ?.putFloat(KEY_WALLPAPER_ZOOM, v.zoom)
            ?.putString(KEY_HOME_DENSITY, v.density)
            ?.putString(KEY_HOME_MODULES, HOME_MODULES.filter { it in v.modules }.joinToString(","))
            ?.putInt(KEY_HOME_MODULES_REV, HOME_MODULES_REV)
            ?.putBoolean(KEY_APPS_WEB_COLLAPSED, v.appsCollapsed)
            ?.putBoolean(KEY_REDUCE_TRANSPARENCY, v.reduceTransparency)
            ?.putBoolean(KEY_REDUCE_MOTION, v.reduceMotion)
            ?.putBoolean(KEY_HIGH_CONTRAST, v.highContrast)
            ?.putString("theme_customization_v1", kotlinx.serialization.json.Json.encodeToString(ThemeCustomization.serializer(), v.customization))
        if (persistSynchronously) check(editor == null || editor.commit()) { "Appearance preferences could not be saved" }
        else editor?.apply()
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            themeCustomization = v.customization
            reduceMotion = v.reduceMotion; highContrastOverride = v.highContrast
            themeModeName = v.theme
            backgroundPreset = v.background
            glassMode = v.glass
            glassOpacity = v.opacity
            containerStyle = v.container
            wallpaperPreset = v.sky
            wallpaperDim = v.dim
            wallpaperSoft = v.soft
            wallpaperPanX = v.panX; wallpaperPanY = v.panY; wallpaperZoom = v.zoom
            homeDensity = v.density; homeModules = v.modules.toSet()
            appsWebCollapsed = v.appsCollapsed; reduceTransparency = v.reduceTransparency
        }
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceMotion(v.reduceMotion)
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setHighContrastOverride(v.highContrast)
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceTransparency(v.reduceTransparency)
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setRefractsGlass(v.glass == "liquid")
    }

    /** 外观页「恢复默认」：只重置外观，不碰周次锚点、节次时间这些学习数据。 */
    fun resetAppearance() {
        wallpaperPreset = "auto"
        backgroundPreset = "porcelain"
        containerStyle = "designed"
        // ★ 独立审计（v3 #10 第 1 轮④）：这颗按钮以前只清 preset/dim/soft 三条，
        //   **不清取景三元组** ⇒ 他按过「恢复默认」之后再点相册那一格，
        //   旧图带着旧裁切原地复活（名不副实）。现在一起清。
        //   （文件本身不动：`deleteWallpaper` 要 Context，且"读不回来就不动旧图"那条规矩还在。）
        wallpaperPanX = 0f
        wallpaperPanY = 0f
        wallpaperZoom = 1f
        wallpaperRev += 1
        wallpaperDim = 24
        wallpaperSoft = 26
        homeDensity = "comfortable"
        homeModules = HOME_MODULES.toSet()
        reduceTransparency = false
        // ★ 独立审计（v3 #12 第 2 轮 P1#5）：这条以前只写 `KEY_REDUCE_TRANSPARENCY=false`，
        //   **不清 `KEY_GLASS_MODE`** ⇒ 「选关闭 → 恢复默认 → 杀进程重进」之后，
        //   盘里 glassMode 还是 "off"，分段控件显示「关闭」而画面走液态 —— 又一枚假开关。
        //   运行期那一份（`MotionPrefs`）由调用点推：偏好层不 import UI 层，这条纪律不动。
        glassMode = "liquid"
        glassOpacity = 50
        // 这一项也长在同一张外观页上（「收起网页服务」），漏了它「恢复默认」就不完整：
        // 应用页仍然是收起的，用户会以为按钮没生效（审查 2026-10-21）。
        appsWebCollapsed = false
        prefs()?.edit()
            ?.putString(KEY_WALLPAPER_PRESET, "auto")
            ?.putString(KEY_BACKGROUND_PRESET, "porcelain")
            ?.putString(KEY_CONTAINER_STYLE, "designed")
            ?.putInt(KEY_WALLPAPER_DIM, 24)
            ?.putInt(KEY_WALLPAPER_SOFT, 26)
            ?.putString(KEY_HOME_DENSITY, "comfortable")
            ?.putString(KEY_HOME_MODULES, HOME_MODULES.joinToString(","))
            ?.putInt(KEY_HOME_MODULES_REV, HOME_MODULES_REV)
            ?.putBoolean(KEY_REDUCE_TRANSPARENCY, false)
            ?.putString(KEY_GLASS_MODE, "liquid")
            ?.putInt(KEY_GLASS_OPACITY, 50)
            ?.putBoolean(KEY_APPS_WEB_COLLAPSED, false)
            // ★ 返工总单 C2：上面清了内存里的取景三元组，但写盘链漏了这三个 key
            //   ⇒ 「恢复默认 → 杀进程 → 再点相册」旧裁切原地复活。
            //   rev 也要写：它才是让图片缓存失效的那一位。
            ?.putFloat(KEY_WALLPAPER_PAN_X, 0f)
            ?.putFloat(KEY_WALLPAPER_PAN_Y, 0f)
            ?.putFloat(KEY_WALLPAPER_ZOOM, 1f)
            ?.putInt(KEY_WALLPAPER_REV, wallpaperRev)
            ?.apply()
    }

    // 没 load 过时返回 null：单测/预览里不会真去碰 Android 存储。
    private var holder: Context? = null

    /**
     * 教学周锚点：`epochDay` 那天是第 `week` 周。
     *
     * ## 为什么要有它（业主 2026-10-20：「我不想每次进来都要看到他重新加载一遍」）
     *
     * 课表**内容**已经能秒开（教务那份落盘了），但**"现在是第几周"以前只有一个来源**：
     * 学在城院那支 `teachingWeeks()`。于是教务快照明明在手上，那一屏还是要等网络，
     * 显示一句「正在加载课表…」—— 他看到的"每次进来都重新加载"就是这一下。
     *
     * ## 为什么这样是推算、不是编数据
     *
     * 学期不会中途重排周次，所以「锚点 + 过了几个整周」就是学校自己会给的那个号。
     * 每次真取到周次都会把锚点往前推一次 ⇒ **自动纠偏**；锚点超过 [WEEK_ANCHOR_TRUST_DAYS]
     * 天就不再信它（宁可退回"还不知道"，也不报一个错的周次号）。
     */
    var weekAnchor by mutableStateOf<Pair<Long, Int>?>(null)
        private set

    fun saveWeekAnchor(epochDay: Long, week: Int) {
        if (week <= 0) return
        weekAnchor = epochDay to week
        prefs()?.edit()?.putString(KEY_WEEK_ANCHOR, "$epochDay:$week")?.apply()
    }

    /**
     * 用锚点推算今天第几周；没有锚点、锚点太旧、或算出来不是正数 ⇒ null。
     *
     * ⚠️ 只算**整周**（`/7`）：周次是按教学周走的，同周内过几天都还是同一周。
     */
    fun weekFromAnchor(todayEpochDay: Long): Int? =
        weekAnchor?.let { (day, week) -> weekFrom(day, week, todayEpochDay) }

    /**
     * 界面问「现在是第几周」的**唯一入口**：拿到权威值就顺手记锚点，没拿到就用锚点推算。
     *
     * 放在这一处而不是各页各写一遍，是因为"记锚点"这件事只要有一页忘了，
     * 那一页就永远在等网络（业主报的"每次进来都重新加载"正是这个形状）。
     */
    fun noteWeek(known: Int?, todayEpochDay: Long): Int? {
        if (known != null && known > 0) saveWeekAnchor(todayEpochDay, known)
        return if (known != null && known > 0) known else weekFromAnchor(todayEpochDay)
    }

    private fun prefs() = holder?.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 教学周锚点存成 `"锚点那天:那天是第几周"`。 */
    private fun parseWeekAnchor(raw: String): Pair<Long, Int>? {
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val day = parts[0].toLongOrNull() ?: return null
        val week = parts[1].toIntOrNull() ?: return null
        return if (week > 0) day to week else null
    }
}

/**
 * 锚点推算的**纯算术**那一半，单独拿出来是为了能被单测覆盖（对象里的状态在单测里不好摆）。
 *
 * 三种情况一律回 null，绝不报错的号：锚点在未来、锚点旧过 [trustDays]、算出来不是正数。
 */
fun weekFrom(
    anchorEpochDay: Long,
    anchorWeek: Int,
    todayEpochDay: Long,
    trustDays: Long = WEEK_ANCHOR_TRUST_DAYS,
): Int? {
    val passed = todayEpochDay - anchorEpochDay
    if (passed < 0 || passed > trustDays) return null
    return (anchorWeek + (passed / 7).toInt()).takeIf { it > 0 }
}
