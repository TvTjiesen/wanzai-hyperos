package com.muyao.wanzaihyperos.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全 App 共用的**版面元件**。
 *
 * ## 這一層解決什麼
 *
 * 業主要求「設計一套新的框架」，並且後面會**整體美化**。
 * 在那之前，同樣的結構（卡片 + 標題 + 副標 + 描述）在每個頁面各寫了一遍，
 * 每份的內距、間距、字級都可能差幾 dp ——
 * 單看每一頁都合理，**放在一起就是不一致**。
 *
 * 這一層把「一則清單項長什麼樣」定下來一次，
 * 於是：
 * - 新頁面只提供**資料**，不提供版面；
 * - 美化時改這裡，**所有用到它的頁面一起變**（這正是業主要的）；
 * - 一致性不靠自律，靠「沒有別的寫法」。
 *
 * ## 與 Miuix 的關係
 *
 * 不重造 Miuix 已有的東西（`Card`/`SmallTitle`/`Button` 都用它的），
 * 只補它沒有的**版面語義**：`InfoCard` / `ListRow` / `MetaLine` 這一類。
 */

/**
 * 頁面的**標準捲動容器**。
 *
 * 用它的理由不只是省字：它把 `screenH`/`screenV`/`cardGap` 一次套好，
 * 所以任何用它的頁面**左右邊距與卡片間距必然一致**。
 *
 * @param header 固定在列表最前面的內容（摘要、提示條）。可為 null。
 */
@Composable
fun AppList(
    modifier: Modifier = Modifier,
    /**
     * 清單的滾動狀態。
     *
     * 給了才能把滾動進度交給 [SecondaryScaffold] 去驅動頂欄漸變紗
     *（見 [rememberScrollProgress]）。不給就自己建一個 —— 這對
     *「不需要紗」的頁面沒有影響。
     */
    state: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    header: (LazyListScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Dims.screenH,
            vertical = Dims.screenV,
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        header?.invoke(this)
        content()
    }
}

/**
 * 一則**清單項**的標準版面。
 *
 * 這是整個框架裡最常用的東西：標題 + 副標 +（可選）右側尾值 +（可選）多行說明。
 *
 * @param trailing 右側尾值（狀態、數字、時間）。**刻意只接受文字**，
 *   不接受任意 Composable —— 那會讓「右側該長什麼樣」失控
 *   （有的頁放按鈕、有的放徽章，美化時就沒法統一調）。
 * @param trailingColor 尾值顏色。預設用 primary；要強調錯誤時傳 error。
 */
/**
 * **全 App 統一的卡片**：Miuix `Card` + 1dp 描邊。
 *
 * ## ★ 為什麼需要這一層（真機量到的數字）
 *
 * 深色下量測（`build/contrast.py` 對真機截圖取像素）：
 *
 * | | 顏色 | 相對亮度 |
 * |---|---|---|
 * | 卡片（Miuix 預設 `surfaceContainer`） | `(36,36,36)` | 0.0176 |
 * | 漸層背景最暗處 | `(15,26,52)` | 0.0109 |
 * | **對比度** | | **1.11 : 1** |
 *
 * 1.11:1 是「肉眼看不出邊界」的程度（正常 UI 分層要 ≥3:1）。
 * 把卡片提亮到 `(40,40,40)` 也只有 1.22:1 —— **仍然不足**。
 *
 * 所以真正的解法是**描邊**：一條 1dp 亮線明確畫出邊界，
 * 而它不改變卡片整體明度（不會像「把卡片調很亮」那樣刺眼）。
 * 這正是規範 §5 說的「描邊只在需要區分同色相鄰兩層時加，寬 1」。
 *
 * ## 為什麼不直接改 Miuix Card
 *
 * 它是函式庫元件、沒有 `border` 參數。而各頁自己包一層的話
 * 「有描邊 / 沒描邊」會不一致 —— 所以收成這一個殼。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** 長按回調。**只在 [onClick] 也給的時候有效**（Miuix Card 的重載限制）。 */
    onLongPress: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val bordered = modifier
        .fillMaxWidth()
        // 描邊畫在卡片**外緣**：`border` 用的是同一個圓角，
        // 所以視覺上像卡片自帶的一圈亮邊，而不是多套一個方框。
        .border(
            width = 1.dp,
            color = AppColors.cardStroke(isDark),
            shape = RoundedCornerShape(Dims.cardCorner),
        )
    when {
        // 帶長按時走 Miuix 的 clickable 重載（它有 built-in 的 press feedback，
        // 自己用 Modifier.clickable 會丟掉那個回饋）。
        onClick != null && onLongPress != null -> Card(
            modifier = bordered,
            cornerRadius = Dims.cardCorner,
            onClick = onClick,
            onLongPress = onLongPress,
            content = content,
        )

        else -> Card(
            modifier = bordered
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            // 圓角與描邊必須一致，否則描邊會浮在圓角外面。
            cornerRadius = Dims.cardCorner,
            content = content,
        )
    }
}

@Composable
fun InfoItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    trailingColor: androidx.compose.ui.graphics.Color? = null,
    /** 多行說明（作業要求、通知正文）。 */
    body: String? = null,
    /** 說明最多顯示幾行（列表裡不宜太長）。 */
    bodyMaxLines: Int = 3,
    onClick: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.ifBlank { "（无标题）" },
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!trailing.isNullOrBlank()) {
                    Spacer(Modifier.width(Dims.tight))
                    Text(
                        text = trailing,
                        style = MiuixTheme.textStyles.footnote2,
                        color = trailingColor ?: colors.primary,
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(Dims.tight))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(Dims.normal))
                Text(
                    text = body,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurface,
                    maxLines = bodyMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * **鍵值行**（標籤在左、值在右）。
 *
 * 標籤列寬用 [Dims.labelColumn] 固定 —— 這樣跨卡片、跨頁面的值都對齊。
 * 各頁自己寫寬度是「看起來就是歪的」的常見來源。
 */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = Dims.labelColumn,
) {
    val colors = MiuixTheme.colorScheme
    Row(modifier = modifier.padding(vertical = Dims.tight)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.width(labelWidth),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurface,
        )
    }
}

/**
 * 一則**可點入口**（如「簽到 ›」）。
 *
 * 與 [InfoItem] 分開：那個是「顯示一筆資料」，這個是「去做一件事」。
 * 混用會讓使用者分不清哪個能點。
 */
@Composable
fun ActionRow(
    title: String,
    action: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dims.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(Dims.tight))
                    Text(
                        text = subtitle,
                        style = MiuixTheme.textStyles.footnote2,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
            Text(
                text = "$action ›",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.primary,
            )
        }
    }
}

/**
 * 一則**提示文字**（不是錯誤，也不是資料）。
 *
 * 用於「說明：…」這類。抽出來是為了它的**顏色與大小全局一致** ——
 * 散落的 `footnote2 + onSurfaceVariantSummary` 是另一種不一致來源。
 */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier.padding(horizontal = Dims.tight),
    )
}

/**
 * **結果訊息**（成功 / 失敗）。
 *
 * 為什麼單獨一個：簽到這類**寫操作**必須讓使用者清楚看到成沒成，
 * 而「綠/紅」是它的語義。讓每個頁面自己挑顏色，早晚有一頁挑錯
 * （把失敗畫成藍色）。
 */
@Composable
fun ResultBanner(
    message: String,
    ok: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body1,
            color = if (ok) colors.primary else colors.error,
            modifier = Modifier.padding(Dims.cardPadding),
        )
    }
}

/** 清單項的便利擴展：`items(list) { InfoItem(...) }`。 */
fun <T> LazyListScope.infoItems(
    items: List<T>,
    key: ((T) -> Any)? = null,
    row: @Composable (T) -> Unit,
) {
    items(items, key = key) { row(it) }
}

/**
 * 同 [infoItems]，但**保證 key 唯一**（用下標）。
 *
 * ## 為什麼需要它（真機崩過）
 *
 * 教務系統的考試清單裡，**同一門課會有多場考試**（補考、期末考）；
 * 成績清單裡**重修**也會讓同一個課名出現多次。
 * 用 `courseName + examName` 當 key 會撞，Compose 直接拋
 * `IllegalArgumentException: Key "大学物理(Ⅰ)" was already used` ——
 * **整個 App 崩掉**（不是只畫錯）。
 *
 * 而資料是**一次抓回來的快照**，順序穩定，所以下標是安全且唯一的 key。
 * 內部沒有用 `indexOf`：那個方法對「值相等的兩筆」會回同一個下標，等於沒解決問題。
 */
fun <T> LazyListScope.infoItemsIndexed(
    items: List<T>,
    row: @Composable (T) -> Unit,
) {
    itemsIndexed(items, key = { index, _ -> index }) { _, item -> row(item) }
}

/**
 * **二級頁面的標準外殼**：不透明底色 + 頂欄 + 安全區。
 *
 * ## 為什麼需要它（同一個坑踩了三次）
 *
 * 這個專案的**二級頁**（壓在主殼之上的整屏頁）如果自己用
 * `AppList` 或 `Column` 拼，會**必然**踩到兩個問題：
 *
 * 1. **底色透明** —— `AppList` 只是一個 `LazyColumn`，本身沒有背景；
 *    而主殼那幾頁之所以沒這問題，是因為 `Scaffold` 給了底色。
 *    症狀是「下面的頁面透上來」，看起來像疊圖。
 * 2. **沒有頂部安全區** —— 內容從 y=0 開始畫，返回鍵被狀態列蓋住。
 *
 * 實際發生過的位置：B16 的愛講座、B17 的智慧公寓閘門，
 * 以及第一個自己拼的 cjh52 原生頁。**同一個錯誤連續出現，就是缺一個元件** ——
 * 所以把它抽成這個外殼，而不是在每一頁各修一次。
 *
 * ## 用法
 *
 * ```kotlin
 * SecondaryScaffold(title = "标题", subtitle = "副标题", onBack = onExit) { inner ->
 *     AppList(modifier = Modifier.padding(top = inner.calculateTopPadding())) { ... }
 * }
 * ```
 *
 * @param subtitle 頂欄副標題（來源說明之類）。空字串就不顯示。
 * @param onRefresh 給了就在右上角加一個刷新鈕。
 */
/**
 * **帶漸層背景的 `Scaffold`**。
 *
 * ## 為什麼需要它（真機踩到的）
 *
 * 第 3 步把背景漸層接到主殼與 [SecondaryScaffold] 之後，
 * 真機上發現**有一部分二級頁仍是純黑底** —— 因為它們自己 `Scaffold(...)`，
 * 沒有走共用殼層（當時全倉 12 個頁面是這樣）。
 *
 * 那不是那些頁面寫錯，而是**「漸層」這個決策沒有落進共用層**：
 * 每加一個新頁面就得記得自己套一次，忘一次就出現一塊純色。
 *
 * 所以把「漸層 + 透明 Scaffold」收成這一個函式，
 * 頁面改用 `ThemedScaffold` 就自動一致。
 *
 * ## 為什麼不能只給 `containerColor` 一個漸層色
 *
 * Miuix `Scaffold` 的 `containerColor` 型別是 `Color`（**純色**），
 * 吃不下 `Brush`。所以只能外層包一層 `Box` 畫漸層、
 * 再把 Scaffold 底色設 `Color.Transparent`。
 */
@Composable
fun ThemedScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingToolbar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // 判據：比 `background` 的亮度，而不是猜系統設定
    //（使用者可以強制淺色而系統是深色）。
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.pageBrush(isDark)),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            floatingToolbar = floatingToolbar,
            snackbarHost = snackbarHost,
            content = content,
        )
    }
}

@Composable
fun SecondaryScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onRefresh: (() -> Unit)? = null,
    /**
     * 內容的滾動進度（0~1），用來驅動頂欄漸變紗。
     *
     * ## 為什麼要由頁面傳進來
     *
     * 每個二級頁的列表狀態（`LazyListState` / `LazyGridState`）是**頁面自己的**，
     * 殼層拿不到。硬要在殼層內部再建一個滾動容器會變成「兩個列表」。
     *
     * 所以：頁面把自己列表的滾動進度算好傳進來。
     * 不傳（預設 0）就**沒有紗** —— 與「停在頂部時無紗」的行為一致，
     * 不會出現「一進頁面就有一層灰」的錯誤。
     *
     * 怎麼算（頁面側）：
     * ```kotlin
     * val progress by remember {
     *     derivedStateOf {
     *         val info = listState.layoutInfo
     *         val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
     *         val scrolled = -first.offset.toFloat()
     *         val total = (info.totalItemsCount * first.size - info.viewportEndOffset)
     *             .coerceAtLeast(1)
     *         (scrolled / total).coerceIn(0f, 1f)
     *     }
     * }
     * SecondaryScaffold(…, scrollProgress = progress)
     * ```
     */
    scrollProgress: Float = 0f,
    content: @Composable (PaddingValues) -> Unit,
) {
    // 背景漸層交給 [ThemedScaffold] —— 不在這裡再寫一份。
    // 兩份的後果是「改了一處另一處沒改」，本專案已經吃過這種虧。
    ThemedScaffold(
        modifier = modifier,
        topBar = {
            // ★ 頂欄 = 漸變紗 + 透明頂欄。
            //
            // 順序要緊：**紗在下、文字在上**。
            // 反過來（文字先、紗後）會把標題糊掉。
            Box {
                // 紗只覆蓋「欄身 + 尾巴」那一條，不是整頁。
                // progress 為 0 時 [TopVeil] 內部直接 return，連合成層都不建。
                TopVeil(
                    progress = scrollProgress,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                SmallTopAppBar(
                    title = title,
                    subtitle = subtitle,
                    // ★ 頂欄底色**透明**，露出後面的紗與漸層。
                    //
                    // 真機踩到的：預設底色是主題的 `background`（純色），
                    // 於是頂部出現**一條純黑/純白的色帶**壓在漸層上 ——
                    // 規範 §7 明確點名過這個毛病（「頂欄不許有不透明色帶壓在內容上」）。
                    //
                    // 參數名 `color` 是從 0.9.4 位元組碼的 Kotlin 元數據讀出來的
                    //（`C(SmallTopAppBar)N(title,modifier,color:c#ui.graphics.Color,…)`），
                    // 不是猜的。
                    color = Color.Transparent,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.ChevronBackward,
                                contentDescription = "返回",
                                modifier = Modifier.size(Dims.iconNav),
                            )
                        }
                    },
                    actions = {
                        if (onRefresh != null) {
                            IconButton(onClick = onRefresh) {
                                Icon(
                                    imageVector = MiuixIcons.Refresh,
                                    contentDescription = "刷新",
                                    modifier = Modifier.size(Dims.iconNav),
                                )
                            }
                        }
                    },
                )
            }
        },
        content = content,
    )
}
/**
 * **二級頁面的返回列**。
 *
 * 為什麼放進共用層而不是各頁自己寫：它是**同一種語義**（「回上一層」），
 * 而各頁自己寫的版本會在字級、顏色、內距上各不相同 ——
 * 使用者在不同二級頁之間切換時，返回列「看起來不是同一個東西」。
 *
 * ⚠️ 這裡刻意不用 Miuix 的 `TopAppBar`：二級頁是**滑動內容的一部分**，
 * 用固定頂欄會讓它與列表一起捲動時閃動。整頁頂欄是外殼（`TronShellPage`）的事。
 *
 * ⚠️ 用了它**不代表**底色與安全區就對了 —— 那是 [SecondaryScaffold] 的事。
 * 這一列只適合放在已經有外殼的頁面裡（例如 `TronShellPage` 的板塊內部）。
 */
@Composable
fun BackRow(
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onBack() },
    ) {
        Text(
            text = "‹ $label",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(Dims.cardPadding),
        )
    }
}
