package com.muyao.wanzaihyperos.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全 App 的**設計令牌**（尺寸 / 間距 / 圓角）。
 *
 * ## 為什麼要有這一層
 *
 * 業主明確說了：**功能做完之後要整體美化**，
 * 而且「美化內容可能包括修改整個頁面的 UI 佈局」。
 *
 * 如果頁面裡到處寫 `padding(14.dp)`、`RoundedCornerShape(12.dp)` 這種字面量，
 * 到時候美化就要**翻遍每一個頁面**去找那些數字 ——
 * 而漏掉一處的結果是「這一頁跟別的不一致」，很難看出來。
 *
 * 把數值收在這裡之後：
 * - 美化只改這一處（例如「卡片圓角從 12 改成 16」）；
 * - 一致性由型別保證（頁面拿不到「14.dp」這種隨手值）；
 * - **改動可以被測試覆蓋**（見 `DesignTokensTest`，它釘住關鍵比例關係）。
 *
 * ## 遵守 Miuix 自己的規範
 *
 * 這裡的語義名（`screenH` / `cardPadding` / `sectionGap`…）刻意與
 * Miuix 的命名風格對齊，避免出現「我們自己一套、Miuix 又一套」的兩套間距體系。
 *
 * ## ⚠️ 使用紀律
 *
 * **新頁面一律用這些令牌，不要再寫 `12.dp`。**
 * 例外只有「這個數值只在這一個地方有意義且永遠不會統一」
 *（例如某個圖示的固定大小），那種情況直接在原地寫並加註釋說明理由。
 */
object Dims {

    // ---- 間距（Spacing）----------------------------------------------------

    /** 螢幕左右邊距。所有列表頁的內容都用它對齊。 */
    /**
     * 正文列左右边距。**板上一切列表/分区头/网格都是 `padding:0 18px`**
     * （`.sec-head/.timeline/.tasklist/.agenda/.inbox/.filters/.appgroup/.wps`），
     * × 0.886 ⇒ **16dp**。原来给 12dp，五屏一起比板窄一圈留白，
     * 卡片、时间线、网格全部错位一格（复评 2026-10-21 量出来 x=42px=12.0dp）。
     */
    val screenH = 16.dp

    /** 螢幕上下邊距（捲動內容的 padding）。 */
    val screenV = 8.dp

    /** 卡片內距。 */
    val cardPadding = 14.dp

    /** 卡片之間的間距。 */
    val cardGap = 10.dp

    /** 區塊（SmallTitle 與其內容）之間的間距。 */
    val sectionGap = 8.dp

    /**
     * 緊湊間距（同一行內的元素之間，如標籤與值）。
     *
     * 比 [sectionGap] 小一半 —— 因為它表達的是「同一個視覺群組內的分隔」，
     * 而 [sectionGap] 是「兩個群組之間」。
     */
    val tight = 4.dp

    /** 一般間距（表單欄位之間）。 */
    val normal = 6.dp

    /** 寬鬆間距（卡片內的段落之間）。 */
    val loose = 10.dp

    // ---- 圓角（Corner）----------------------------------------------------

    /**
     * 卡片圓角。
     *
     * ## 為什麼從 12 提到 18
     *
     * 規範 §5：「小圓角 + 玻璃 = **厚紙板**；圓角越大，折射邊緣越像玻璃。
     * 這是觀感提升最便宜的一處改動。」建議 16~20，取中值 18。
     *
     * ## 與 Miuix 的關係（重要）
     *
     * Miuix `Card` 的**預設圓角是 16**（從 0.9.4 的 `CardDefaults.CornerRadius`
     * 位元組碼讀出來的，`bipush 16` → `Dp(16f)`）。
     * 所以：
     * - 頁面直接用 `Card { }` 時拿到的是 16，**落在規範區間內**，不必每個都改；
     * - 想統一控制時用 `Card(cornerRadius = Dims.cardCorner)` ——
     *   第二個位置參數就是它（`Card(modifier, cornerRadius, insideMargin, colors, …)`）。
     *
     * ⚠️ 這條與 [cardPadding] 有約束：圓角不能超過內距的兩倍，
     * 否則內容會壓到圓角弧線上（`DesignTokensTest` 釘住了這條）。
     * 18 ≤ 14×2=28，成立。
     */
    val cardCorner = 18.dp
    /** 稿 .paper 的圆角：20px × 0.886 ≈ 17.7dp。 */
    val paperCorner = 17.7.dp

    /**
     * 小元件圓角。
     *
     * ⚠️ 規範 §5 說「交互件（按鈕/開關/滑桿/標籤/頭像）**一律全圓**（CircleShape）」，
     * 所以新建的交互件請直接用 `CircleShape`，**不要用這一條**。
     *
     * 這條保留給「非全圓但也需要圓角的小容器」——
     * 例如圖片縮圖、色塊。全圓是主流，方形圓角是例外。
     */
    val chipCorner = 8.dp

    /**
     * 非卡片的容器圓角（提示條、圖示底、搜尋列這種「小托盤」）。
     *
     * ## 為什麼不直接用 [cardCorner]
     *
     * 卡片是「一張卡」的層級，圓角大一點才像玻璃托盤；
     * 而這些是 44~56dp 的**小托盤**，套 18 的圓角會看起來像被削掉一角
     *（圓角相對尺寸太大）。
     *
     * 14 是這些元件在真機上的既有值，**保留** ——
     * 規範也說了「差異不可見的不值得改」。
     */
    val containerCorner = 14.dp

    /**
     * 牆紙格圓角（外觀頁那四格 + 相冊那一格）。
     *
     * 設計板 `.wp{border-radius:13px;aspect-ratio:1/.92}` ⇒ × 0.886 = **11.5dp**。
     *
     * ⚠️ 這一格以前用的是 `Glyph.tileCorner`（**11dp** —— 那是 38dp 列表行磁貼的檔位），
     *    而畫布那邊寫 `11.5.dp` ⇒ **同一格兩塊半徑**（clip 11 / 畫布 11.5）。
     *    現在 clip、描邊、畫布三處統一走這條。
     */
    val wallpaperTileCorner = 11.5.dp

    // ---- 圖示與元件尺寸 ----------------------------------------------------

    /** 頂部導航的圖示大小。 */
    val iconNav = 22.dp

    /** 一般動作圖示（刷新等）。 */
    val iconAction = 20.dp

    /** 小標記點（未讀點）。 */
    val dotSmall = 8.dp

    /**
     * 進度條高度。
     *
     * ⚠️ 原本叫 `barHeight`，但那名字與「頂欄欄身高度」撞語義
     *（`iOS 感` 要求欄身是 52，不是 Material 的 56）。
     * 所以改名騰出 `barHeight` 給頂欄 —— 這一步是**純改名**，
     * 舊名當時全倉 0 處引用，不會破壞任何地方。
     */
    val progressBarHeight = 6.dp

    /**
     * 觸控目標最小邊長。
     *
     * ## 為什麼要單獨一條
     *
     * 視覺尺寸與命中區是兩件事：圖示畫 20dp 很好看，但手指點 20dp 很容易點空。
     * 實測與規範都要求**命中區 ≥ 44**，而視覺尺寸可以更小。
     *
     * 用法：`Modifier.size(Dims.iconAction).minimumInteractiveSize(...)`
     * 或外層包一個 44 的 `Box`（見 `AppLayout` 的 `IconButton` 用法）。
     *
     * 44 來自 iOS HIG 與 Material 的共同下限；**不要再小**。
     */
    val minTouch = 44.dp

    // ---- 頂欄（Bar）-------------------------------------------------------

    /**
     * 頂欄欄身高度。
     *
     * **52 而不是 Material 的 56** —— 差這 4dp 就是「像 iOS」與「像安卓」的分界
     *（OmniRing 真機定稿值，規範 §4）。
     *
     * ⚠️ 這是**欄身**，不含狀態列；頂欄總高 = 狀態列 + 52。
     */
    val barHeight = 52.dp

    // ---- 清單項高度（List item）-------------------------------------------

    /**
     * 單行清單項高度。
     *
     * 三檔（44 / 64 / 76）刻意用「行數」而不是「內容型別」命名：
     * 統一**行高**比統一**字號**更容易看出「整不齊」——
     * 兩張卡片字號一樣但行高差 4dp，列表看起來就是歪的。
     */
    val itemOneLine = 44.dp

    /** 兩行清單項高度（主行 + 副行）。 */
    val itemTwoLine = 64.dp

    /** 三行清單項高度（主行 + 副行 + 說明）。 */
    val itemThreeLine = 76.dp

    // ---- 清單（List）------------------------------------------------------

    /**
     * 表格／描述清單的**標籤列寬**。
     *
     * 抽出來是因為它要跨卡片對齊（班級的「學院/專業/年級」、一卡通的「類型/狀態」）
     * —— 各頁各寫一個寬度，看起來就是歪的。
     */
    val labelColumn = 64.dp

    /** 寬一點的標籤列（班級用，標籤是兩個字加間距）。 */
    val labelColumnWide = 72.dp

    /**
     * 再寬一級：容得下五、六個字的標籤（體測項「坐位體前屈」實測 81.4dp）。
     *
     * 有它的理由是「一列之內必須對齊」：把長標籤放進窄列會自己折成兩行，
     * 於是同一張卡裡一半的值從第一行起、一半從第二行起，讀不出對應關係。
     */
    val labelColumnLong = 84.dp
}

/**
 * 全 App 的**字號階梯**。
 *
 * ## 為什麼需要它（現在字號散在頁面裡）
 *
 * Miuix 有自己的 `TextStyles`（title1/2/3、body1/2、footnote…），
 * 但那是**語義名**，實際 sp 值由 Miuix 決定。業主要的美化要求
 * 「主行 13 是最常見那一檔、8 是最小檔」這種**具體刻度**，而 Miuix 的
 * title/body 對應不到這些值上。
 *
 * 所以這裡定義**一份刻度**，頁面按「用途」挑（`Type.body`），
 * 需要時再映射到 Miuix 的樣式（見 [toTextStyle] 的說明）。
 *
 * ## 刻度來源（v2 定稿板 · 2026-10-21 重定標）
 *
 * 舊刻度是 OmniRing 真機上 `grep fontSize` 的**頻次統計**結果（規範 §3），
 * 主行 17 / 最小 11。業主改為「按 v2 板一比一」後，這條絕對值不再成立 ——
 * 板上的手機框 392 CSS px 對應本机 **347dp**，換算係數 **0.886**：
 *
 * | 板上（CSS px） | ×0.886 | 本工程檔位 |
 * |---|---|---|
 * | `.tab span` 8.5 | 7.5 | **8 = micro** |
 * | `.trow .meta` 9.5 | 8.4 | **9 = caption** |
 * | `.arow .meta` 9.5 / `.count` 10.5 | 8.4 / 9.3 | **10 = summary** |
 * | `.trow .name` 12.5 / `.mrow .t` 12 | 11.1 / 10.6 | **11 = secondary** |
 * | `.btn` 10.5 / `.chip` 10（帶内邊距的控件文字） | 9.3 / 8.9 | **12 = bodyEmphasis** |
 * | `.arow .name` 13.5（課表主行） | 12.0 | **13 = body** |
 * | `.sec-head h3` 15 | 13.3 | **13**（分區標題同檔，靠字重分辨） |
 * | `.focus-title` 18 | 16.0 | **16 = title** |
 *
 * 板上另有 42/58px 的大標題（沉浸態），換算 37/51sp —— 那兩處是**場景字**
 *（鎖屏態大時間、頁面大字），不進這份階梯，由頁面按場景寫。
 *
 * ⚠️ 這是**相對刻度**的勝利，不是「字越小越好」：sp 仍跟系統字體縮放走，
 * 開大字的用戶看到的每一檔都比這裡大。floor 由 `DesignTokensTest` 守。
 *
 * ## 四條硬規則（每條都被業主打回過）
 *
 * 1. **不許用字重表達「選中」** —— 加粗讓文字變寬，切頁時整排字會橫向抖一下
 *    （業主原話「奇怪的抖動」）。選中**只用顏色**。
 * 2. **不許插中間值**（16.5、13.5 之類）。要加檔位先想清楚它替代誰。
 * 3. 行高：正文 1.4~1.5、標題 1.15~1.2；
 *    **中文小字（8~13）必須 ≥1.5**，否則行會黏在一起。
 *    （繁體筆畫更密，這條比簡體更要緊。）
 * 4. 數字/英文混排（學號、GPA、時間、教室號、節次）用**等寬數字**，
 *    否則列表裡數字會左右跳。見 [tabular]。
 */
object Type {

    /** 頁面大標題（**折疊的那個**，是列表首項而不是頂欄的一部分）。 */
    val largeTitle = 34.sp

    /** 日期主导的首页：两种密度共用一组可调刻度。 */
    val homeDate = 38.sp
    val homeDateCompact = 32.sp
    val lineHomeDate = 44.sp
    val lineHomeDateCompact = 38.sp
    val homeFocus = 28.sp
    val homeFocusCompact = 24.sp
    val lineHomeFocus = 34.sp
    val lineHomeFocusCompact = 30.sp

    /** 沉浸態標題、品牌名。 */
    val displayTitle = 26.sp

    /** 分區主標題。 */
    val title = 18.sp

    /** **列表主行、卡片標題、按鈕文字** —— 頻次最高 = 預設主文字。 */
    val body = 15.sp

    /** 稍強調的正文、控件內文字（按鈕、chip）。少用。 */
    val bodyEmphasis = 14.sp

    /** 正文。 */
    val secondary = 13.sp

    /** 摘要、設定項副標題。 */
    val summary = 12.sp

    /** 說明、時間戳、腳註。 */
    val caption = 11.sp

    /** 最小可用檔（底欄 tab 標籤、角標）。**再小不許。** */
    val micro = 10.sp

    /**
     * 密排行高（只给**格子里的课名**这种「一行容不下就整块作废」的地方用）。
     *
     * 常規小字行高是 1.55 倍（`LINE_HEIGHT_SMALL`），11sp 字實占 17sp。
     * 但課表格子只有 45.49dp 寬、兩節的塊只有 62dp 高：按 1.55 倍排，
     * 3 行課名 + 1 行教室 = 4×17 = 68dp ⇒ 教室那行必然被切在底邊（2026-10-26 真機實測「教六…」）。
     * 12sp 行高配 11sp 字仍是 1.09 倍行距，CJK 字面高度約 0.88em，行與行不會相碰；
     * 換來的是同一門課能多顯示一行。這是「看得清是哪門課」優先於「行距好看」的取捨。
     */
    val lineTight = 12.sp

    /**
     * 大標題的行高（單行版：`displayTitle` 26sp × 1.08）。
     *
     * 為什麼行高也要進令牌：`TypeRatchetTest` 钉的是「`fontSize` / `lineHeight` 的裸字面量總數
     * 只許降不許升」。這三個值是 2026-11-07 复刻稿的三行大標題時第一次需要單獨命名的 ——
     * 之前它們散在 `IosChrome.kt` 裡當裸 `.sp` 寫著，字號階梯一動就漏改它們。
     */
    val lineDisplayTitle = 28.sp

    /** 大標題的行高（三行版：`displayTitle` 26sp × 1.15；字大了行距要鬆一點才不擠）。 */
    val lineDisplayLoose = 30.sp

    /** 副行的行高（`caption` 11sp × 1.45，正好落在 `LINE_HEIGHT_BODY` 上）。 */
    val lineCaption = 16.sp

    /** 行高倍數：正文。 */
    const val LINE_HEIGHT_BODY = 1.45f

    /** 行高倍數：標題（緊一點，看起來才「實」）。 */
    const val LINE_HEIGHT_TITLE = 1.18f

    /** 行高倍數：中文小字（11~13）。**必須 ≥1.5**，否則行會黏。 */
    const val LINE_HEIGHT_SMALL = 1.55f

    /** 字重：標題/主行用。 */
    val weightTitle = FontWeight.Bold

    /** 字重：主行（Medium 而不是 Bold —— 17 用 Bold 會太重）。 */
    val weightBody = FontWeight.Medium

    /** 字重：正文/說明（Regular）。 */
    val weightRegular = FontWeight.Normal

    /**
     * 等寬數字（`tnum`）。
     *
     * 用途：學號、GPA、時間、教室號、節次、金額 ——
     * 一般字型的數字是**比例寬**（1 比 0 窄），列表裡逐行對齊時數字會左右跳。
     *
     * 用法：`TextStyle(fontFeatureSettings = Type.tabular)`。
     */
    const val tabular = "tnum"
}

/**
 * 全 App 的**動效令牌**。
 *
 * 同樣為「整體美化」預留：改動效的節奏（快慢、曲線）比改顏色更需要全局一致，
 * 而散落的 `tween(300)` 是沒法統一調整的。
 *
 * ## 時長階梯（真機統計值，別再隨手寫新數）
 *
 * | 時長 | 用途 |
 * |------|------|
 * | 90 | 按下回饋（**必須快**） |
 * | 150 | 淡入淡出、小位移、交叉淡入 |
 * | 320 | 展開／收起（FastOutSlowIn） |
 * | 420 | 頁面切換 |
 * | 520 | 大形變 |
 */
object Motion {
    /** 按下回饋。**必須快**，慢了手感會「黏」。 */
    const val PRESS_MS = 90

    /** 短動效（小位移、淡入淡出、交叉淡入）。 */
    const val FAST_MS = 150

    /** 一般動效（頁面內的元素出現）。 */
    const val NORMAL_MS = 250

    /** 展開／收起（配 FastOutSlowIn）。 */
    const val EXPAND_MS = 320

    /** 頁面切換。 */
    const val PAGE_MS = 420

    /** 大形變（Morph）。 */
    const val MORPH_MS = 520

    /** 載入 spinner 的延遲：**超過這個時間才出現**，更快就別閃。 */
    const val SPINNER_DELAY_MS = 1500L

    // ── 彈簧（v2 定稿的外觀浮層）──────────────────────────────────────
    //
    // 浮層屬「偶爾」檔：允許一點回彈，但不過頭。退場走臨界阻尼 + 更高剛度，
    // 這樣它總比進場先穩下來。寫成常量的理由是 `MotionGuardTest` 的棘輪：
    // 同一組數散在五個檔案裡各寫一遍遲早分叉，收在令牌裡才只有一處。
    const val OVERLAY_IN_DAMPING = 0.86f
    const val OVERLAY_IN_STIFFNESS = 520f
    const val OVERLAY_OUT_DAMPING = 1.0f
    const val OVERLAY_OUT_STIFFNESS = 900f
}

/**
 * 圖標磁貼的**底色三檔**（B4-13：三檔原本散在 `SettingsSectionPage` / `AccountsPage` 裡各寫一遍十六進制）。
 *
 * 這三枚是淺色板的飽和色，白字壓在上面 **4.5–6.2:1**（最低是橙 `#B65E3D` 那檔 4.51，
 * 綠 6.17、灰 5.07），深色板上同樣成立 ⇒ 只收口，不改值。
 */
object Tone {
    /**
     * 已登錄 / 正常。
     *
     * 2026-10-29 业主「我不喜欢绿色」⇒ 青绿 #2E6B64 换成靛蓝 **#3E4E9E**。
     * 白图形压它 7.53:1；对纸面 6.95、对卡片 7.34、对课块底 6.34 —— 四层全过 4.5。
     *
     * ★ 2026-11-07 起它还多一个用法：设置行图标改裸线稿后，**它直接当笔画色**
     *   （以前是压在磁贴底下的背景色）。当文字/线条用时要求 ≥4.5:1，上面那四层读数仍然成立。
     */
    val ok = Color(0xFF3E4E9E)

    /** 拿到了但沒激活 / 需要他動一下。对纸面 5.29、对卡片 5.05（当笔画色用也过 4.5）。 */
    val warn = Color(0xFFB65E3D)

    /**
     * 未登錄 / 中性說明。
     *
     * 2026-10-27 冷白纸面（#F4F6F8）之后重挑：`#696E74` 对最暗那档 `#ECF0F4` 只剩 **4.49:1**，
     * 差 0.01 不达标。现在是 **#666B71** —— 对纸面 4.96、对最暗档 4.69、对卡片 5.24、对课块底 4.53，
     * 四层全过 4.5；比原值只深 3 个灰阶，观感不变。
     */
    val quiet = Color(0xFF666B71)

    /**
     * 暗檔的 [quiet]。
     *
     * [quiet] 是按纸底挑的：换成冷白纸面后对 #F4F6F8 是 4.96:1，但换到暗档的卡片底 #222526 上只剩
     * **3.05:1** —— 2026-10-27 在模拟器深色档量到整周板轴上的「09:50 / 11:30」就是这个数，
     * 10sp 小字远不够。这个值对暗卡片 5.94:1、对暗页底 6.80:1。
     */
    val quietDark = Color(0xFFA3A9AF)

    /** 亮面墨色（板 `--ink`）。以前在预览/卡片里散写了 7 处 `Color(0xFF1A1917)`。 */
    val ink = Color(0xFF1A1917)

    /** 暗面墨色（板 `body.dark --ink`）。 */
    val inkDark = Color(0xFFF2EFE7)

    /** 预览卡片压在照片上的那层深底（比纯黑暖一点，压出来不发灰）。 */
    val deep = Color(0xFF28302F)
}

/**
 * 整周课块的**一套实色**（B4-04 v4，2026-10-24）。
 *
 * 为什么不再用「同色相 + 55% alpha」：那套的填充对纸面最坏只有 **1.09:1**，
 * 块和底几乎同色，课表看上去是一片糊的米色；描边对填充也只有 1.42。
 * 现在课块是一块**实色浅底**，颜色信息只放在左边缘那条 3dp 色脊上。
 *
 * 所有数字都是算出来的，不是挑出来的（扫全 360 色相、不是只扫我这周这 13 门）：
 * - 色脊 hsl(h, 0.55, 0.32) 对块底最坏 **3.58:1**（对白卡 4.29）⇒ 过 WCAG 1.4.11 的 3:1
 * - 块底对白卡 **1.19:1**、对纸 **1.10:1** ⇒ 只是「看得出有个块」，**不假装**过 1.4.11
 * - 墨 / 教室 / 老师 对块底 **14.78 / 5.76 / 5.02**（深档 11.77 / 7.71 / 6.13）
 * - 撞课徽标字 #A9522F 压 #F7EAE4 **4.53:1**（直接拿 Tone.warn 只有 3.83，不够 4.5）
 */
object Chip {
    /** 课块实色底（亮档 / 深档）。 */
    val face = Color(0xFFE8ECF0)
    val faceDark = Color(0xFF2C2F2E)

    /** 1dp 可见边：只求「看得见这条边」，不求过对比度（对块底 1.14 / 1.33）。 */
    val edge = Color(0xFFD9DFE5)
    val edgeDark = Color(0xFF3E4240)

    /** 教室（第二级墨）：对块底 5.76 / 深档 7.71。 */
    val ink2 = Color(0xFF575D63)
    val ink2Dark = Color(0xFFC9C3B8)

    /** 老师（第三级墨）：对块底 5.02 / 深档 6.13。不许用 opacity 造这一级（0.66 的墨只有 3.x）。 */
    val ink3 = Color(0xFF5E646A)
    val ink3Dark = Color(0xFFA6A095)

    /** 撞课徽标：底 + 字。 */
    val badgeFace = Color(0xFFF7EBE8)
    val badgeFaceDark = Color(0xFF3A2620)
    val badgeInk = Color(0xFFA9522F)
    val badgeInkDark = Color(0xFFF0B49C)

    /**
     * 左缘色脊：**十枚手选色**，按课程序号轮转。
     *
     * 旧写法是 Color.hsl((courseId * 137.5) % 360, 0.55, 0.32) —— 全色相轮转，
     * 实测对块底 #E8ECF0 的对比度从 4.09（青绿 174°）到 9.57（紫 264°），差 2.3 倍，
     * 一排课看过去就是「有的重有的轻」，业主说的「乱」就是这个。
     * 手选这十枚把明度压在同一段里：浅档对块底最坏 **4.85**、对卡 5.61；
     * 深档十枚对 #2C2F2E 最坏 **4.64**。全部过 WCAG 1.4.11 的 3:1 且留足余量。
     * 顺序是排过的：相邻两枚色相差 ≥24°，撞在一起也认得出是两门课。
     *
     * 2026-10-29 随主色换靛蓝：原来十枚的头两枚（青绿 #2F6B6E / #3A6180）与末枚
     * （#3F6B4F）都偏绿，业主说不要绿，整表重排成「蓝 → 紫 → 酒红 → 赭 → 钢蓝」，
     * 相邻色相差仍 ≥24°。
     */
    private val spineLight = intArrayOf(
        0xFF3E4E9E.toInt(), 0xFF4A3F8C.toInt(), 0xFF5F3E8A.toInt(), 0xFF7A3E7A.toInt(),
        0xFF8C3E62.toInt(), 0xFF8C4248.toInt(), 0xFF8C5238.toInt(), 0xFF7A6030.toInt(),
        0xFF4A5578.toInt(), 0xFF3E6A8C.toInt(),
    )

    /** 深档同序：深底上要抬明度，色相不动。 */
    private val spineDark = intArrayOf(
        0xFF8E9CE8.toInt(), 0xFF9A8CE0.toInt(), 0xFFAE8CDC.toInt(), 0xFFC68CC0.toInt(),
        0xFFD08CA0.toInt(), 0xFFD4948C.toInt(), 0xFFD0A07C.toInt(), 0xFFC4B070.toInt(),
        0xFF93A0C8.toInt(), 0xFF84AECB.toInt(),
    )

    /** courseId 允许是 0（撞课占位），负数也兜住，绝不越界。 */
    fun spineOf(courseId: Int, isDark: Boolean): Color {
        val table = if (isDark) spineDark else spineLight
        val i = ((courseId % table.size) + table.size) % table.size
        return Color(table[i.toInt()])
    }

    /** 色脊宽度（绝对定位画，不占排版宽度）。 */
    const val SPINE_DP = 3f

    /** 课块圆角。小样写 12dp；不动 Dims.cardCorner（18dp，卡片用）。 */
    val corner = 12.dp
}

/**
 * 設置頁圖標的**線性規則**（B4-13）：一枚圖標 = 20dp 畫布 + **0.11**（比例，不是秒）線寬 + 圓頭，
 * 需要圓角的圓/方塊一律 **0.08**（比例）。四條都寫在這裡，別在某一枚圖標裡偷偷改一個數。
 *
 * ※ 上一版把比例寫成「0.11s / 0.08s」，`s` 是畫布邊長的變數、不是秒 —— 已改。
 */
object Glyph {
    /** 畫布邊長。 */
    val size = 20.dp

    /** 線寬 ÷ 邊長（20dp 上 = 2.2dp）。 */
    const val STROKE_RATIO = 0.11f

    /** 圓角 ÷ 邊長（20dp 上 = 1.6dp）。 */
    const val CORNER_RATIO = 0.08f

    /**
     * 圖形**內部那些小矩形**（成績單三行、表格格）的圓角 ÷ 自身短邊。
     * 它和 [CORNER_RATIO] 是兩條規則不是一條：小矩形按畫布比例算，圓角會小到看不出來。
     */
    const val INNER_RECT_CORNER_RATIO = 0.2f

    /**
     * 列表行左邊給圖標留的那一格（圖標畫在它正中）。
     *
     * 2026-11-07 起這一格**不再是彩色磁貼**，只是一段留白：v2.1 稿的設置行左邊
     * 只有一枚裸線圖標（`screens/settings.png` 量到 x=43..62 ⇒ 約 19dp 寬、20dp 左縮排）。
     * 26dp = 20dp 圖標 + 兩側各 3dp，落在 14dp 行內距之後 ⇒ 圖標左緣 17dp、
     * 文字列左緣 52dp（稿上 79px × 0.886 ≈ 70dp 減卡面 18dp ≈ 52dp，對得上）。
     */
    val rowSlot = 26.dp

    /**
     * 舊**磁貼**的邊長（38dp）。
     *
     * ⚠️ **2026-11-07 起退役**：`SettingGlyph` 改成裸線稿後不再有彩色方塊，
     *    這一格連同 [tileCorner] 一起沒有調用點了。令牌與釘值都留著 ——
     *    它們記的是「檔位尺」（38dp 那一批的形），刪掉就丟了換算依據；
     *    但**不許再把 38dp / 11dp 拿去鋪任何新東西**（那是舊口徑）。
     */
    val tile = 38.dp

    /**
     * 舊磁貼的圓角（11dp）—— **2026-11-07 起隨 [tile] 一起退役**，全工程零調用點。
     *
     * 留著是為了 `CornerTierTest` 的檔位階梯（chip 8 / 這一格 11 / 牆紙 11.5 / 托盤 14 / 卡片 18）
     * 仍然完整可比；**別再拿它當「通用圓角」頂任何東西**。
     */
    val tileCorner = 11.dp

    /** 自繪開關的圓鈕直徑（[SettingsChrome] 的 `PillSwitch`）。 */
    val knob = 22.dp
}
