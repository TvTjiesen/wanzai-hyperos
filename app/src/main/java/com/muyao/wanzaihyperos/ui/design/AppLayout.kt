package com.muyao.wanzaihyperos.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
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
    header: (LazyListScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
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
 * **二級頁面的返回列**。
 *
 * 為什麼放進共用層而不是各頁自己寫：它是**同一種語義**（「回上一層」），
 * 而各頁自己寫的版本會在字級、顏色、內距上各不相同 ——
 * 使用者在不同二級頁之間切換時，返回列「看起來不是同一個東西」。
 *
 * ⚠️ 這裡刻意不用 Miuix 的 `TopAppBar`：二級頁是**滑動內容的一部分**，
 * 用固定頂欄會讓它與列表一起捲動時閃動。整頁頂欄是外殼（`TronShellPage`）的事。
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
