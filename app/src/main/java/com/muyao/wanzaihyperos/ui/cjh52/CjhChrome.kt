package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Mark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.MarkedHeading
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * CJH52 六頁 + 目錄頁共用的正文零件。
 *
 * ### 為什麼收在 `ui/cjh52` 而不是 `ui/design`
 *
 * 這幾支（頁頭三行 / 鍵值行 / 說明條 / 膠囊芯片 / 條目行 / 原站分區）
 * `ui/design` 裡沒有現成實現，而本輪的可寫範圍不含 `ui/design`。
 * 與其在七個文件裡各抄一遍（那正是本專案吃過三次虧的做法），先收在 cjh52 這一包；
 * 等上游決定要不要上提，只改 import 一行。
 *
 * ### 尺寸全部取自令牌，文案全部照稿
 *
 * 稿 `prototype v2.1` 的 `.eyebrow{11.29px}` / `.hero-title{29.35px}` / `.kv` /
 * `.notice` / `.btn` / `--quiet` 都是板上的像素值，這裡一律換算成既有字號檔（×0.886）
 * 與既有 [Dims] 間距，**不新造檔位、不新增顏色字面量**。
 */

/** 頁頭三行：眉題 / 大標題 / 一句副題（稿 `.eyebrow` + `.hero-title` + `.muted`）。 */
@Composable
fun CjhHero(eyebrow: String, title: String, sub: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Dims.tight)) {
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
 * 一行**鍵值**（稿 `.kv`）：標籤在左、值在右、下沿一道細分割線。
 *
 * ### 為什麼不直接用 `KeyValueRow`
 *
 * 共用那支的值是**左對齊**且**沒有分割線**，而稿 `.kv b{text-align:right}` +
 * `border-bottom` 是這一頁面系的主結構 —— 六頁的「不像稿」有一半在這裡。
 * 也不能改共用那支：`jw` / `szty` 等頁按左對齊排得好好的，改它會連帶走形。
 *
 * ### 為什麼標籤寬是「下限」不是「死寬」
 *
 * 同 [com.muyao.wanzaihyperos.ui.design.KeyValueRow] 的教訓：死寬會讓放不下的
 * 標籤自己折行（「時間 / 地點」在窄屏 320dp 上就會），折行比不齊更糟。
 */
@Composable
fun CjhKvRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = Dims.labelColumnLong,
    /**
     * 標籤列改用**比例寬度**（而不是最小寬度）。
     *
     * 為什麼需要：學在城院之外的 CJH52 課程頁把**課名放在標籤位**。
     * 課名一長（「中國近現代史綱要」九個字），最小寬度會被撐到半屏，
     * 時段列只剩一條窄縫 —— 真機上時段被折成四行還被裁掉。
     * 給兩列一個明確比例，長課名自己換行，時段列才有位置。
     */
    labelWeight: Float? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dims.loose),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                fontSize = Type.secondary,
                lineHeight = Type.secondary * Type.LINE_HEIGHT_SMALL,
                color = colors.onSurfaceVariantSummary,
                modifier = if (labelWeight != null) {
                    Modifier.weight(labelWeight)
                } else {
                    Modifier.widthIn(min = labelWidth)
                },
            )
            Spacer(Modifier.width(Dims.normal * 2))
            Text(
                text = value,
                fontSize = Type.secondary,
                lineHeight = Type.secondary * Type.LINE_HEIGHT_SMALL,
                fontWeight = Type.weightBody,
                color = colors.onSurface,
                // 有比例的兩列（課名 / 時段）：值要**左對齊**。右對齊時多行會從左邊参差，
                // 真機上「星期一 · 08:00~09:35 · 教三204 · 1-5 周」折三行後完全讀不出行序。
                textAlign = if (labelWeight != null) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.dividerLine))
    }
}

/**
 * 說明條（稿 `.notice`）：左側一道 2dp 豎線 + 淡底圓角，**不是一張卡片**。
 *
 * 寫法與 `ui/busyness/BusynessPage.kt` 的 `SecNotice` 一致（那邊是真機驗過的）；
 * 不 import 它是因為那個是 `private`。
 */
@Composable
fun CjhNotice(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val tint = if (error) colors.error else colors.onSurfaceVariantSummary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(tint),
        )
        Text(
            text = text,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = tint,
            modifier = Modifier.padding(
                start = Dims.loose,
                end = Dims.cardPadding,
                top = Dims.normal,
                bottom = Dims.normal,
            ),
        )
    }
}

/**
 * 「保留舊資料」那條說明（稿 `stale()` 的三句之二）。
 *
 * 只有真的**取不到新資料但手上有舊資料**時才畫 —— 正常刷新時畫一條
 * 「刷新中，保留上次內容…」會讓每次下拉都閃一條灰帶，那不是資訊而是噪音。
 *
 * @return 該頁面沒有「舊資料」情形時什麼都不畫。
 */
@Composable
fun CjhStaleNotice(state: PageState, modifier: Modifier = Modifier) {
    when (state) {
        PageState.Offline -> CjhNotice(
            text = "离线 · 保留上次缓存正文与原更新时间；当前不可写，不会自动重试提交。",
            modifier = modifier,
            error = true,
        )
        PageState.Stale -> CjhNotice(
            text = "刷新失败 / 部分来源不可用，保留旧内容。未知项显示 —。",
            modifier = modifier,
            error = true,
        )
        else -> Unit
    }
}

/**
 * 一枚膠囊芯片（稿 `.btn` / `.btn.outline`）。
 *
 * 命中區撐到 [Dims.minTouch]（44dp）：稿上這幾枚是 `min-height:50px`（≈43dp），
 * 手指按下去正好；字不變大，只是可點範圍到了規範線。
 *
 * @param emphasis 稿上「六项服务目录」那枚是 `container` 底 + 主色字，其餘是透明底 + 描邊。
 */
@Composable
fun CjhChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .heightIn(min = Dims.minTouch)
            .clip(shape)
            .background(if (emphasis) colors.surfaceContainer else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (emphasis) Color.Transparent else colors.dividerLine,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dims.cardPadding, vertical = Dims.normal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = Type.body,
            fontWeight = Type.weightBody,
            color = if (emphasis) colors.primary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 一排會**換行**的芯片（稿 `.chip-list` 是 `flex-wrap`）。
 *
 * 不用 `LazyRow`：芯片只有兩三枚，橫向滾動在窄屏（320dp）上會把
 * 「我的成長完整网页版」這類長標籤藏到屏幕外，使用者看不出還有第二枚。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CjhChipFlow(chips: List<CjhChipSpec>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dims.normal),
        verticalArrangement = Arrangement.spacedBy(Dims.normal),
    ) {
        chips.forEach { chip ->
            CjhChip(label = chip.label, onClick = chip.onClick, emphasis = chip.emphasis)
        }
    }
}

/** [CjhChipFlow] 的一枚：文案 + 動作 + 是否是稿上那枚高亮款。 */
class CjhChipSpec(
    val label: String,
    val onClick: () -> Unit,
    val emphasis: Boolean = false,
)

/**
 * 一條「原站與狀態」分區標題 + 一排芯片動作。
 *
 * 稿上是 `section-heading`（h3「原站与状态」+ 右側 meta）+ `chip-list` 四枚：
 * 「…完整网页版」「刷新」「演示未激活」「六项服务目录」。
 *
 * ★ **「演示未激活」不做**：那是原型自己的評審開關（一盞「這頁的樣例沒接真數據」
 *   的狀態燈）。本工程這幾頁接的是真接口，「未激活」這個狀態在這裡不存在，
 *   照抄只會多一個永遠亮不起來的按鈕。
 *
 * ★ **末尾那行 `来源：本地模拟样例 · 演示时间 …` 也不做**：它逐字自稱「样例」「演示」，
 *   本工程顯示的是真實接口回來的數據，照抄等於對使用者說謊。
 *
 * @param webLabel 完整網頁版那一枚的文案（每頁不同，逐字照稿）。
 * @param onOpenWeb 為 null 時不畫那一枚（沒有網頁版出口的頁面）。
 * @param onOpenDirectory 「六项服务目录」→ 回 cjh52 服務目錄頁。
 */
@Composable
fun CjhOriginSection(
    webLabel: String,
    onOpenWeb: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpenDirectory: (() -> Unit)?,
) {
    MarkedHeading(
        text = "原站与状态",
        icon = MarkIcon.Web,
        modifier = Modifier.padding(top = Dims.sectionGap),
    )
    val chips = buildList {
        if (onOpenWeb != null) add(CjhChipSpec(webLabel, onOpenWeb))
        add(CjhChipSpec("刷新", onRefresh))
        if (onOpenDirectory != null) add(CjhChipSpec("六项服务目录", onOpenDirectory, emphasis = true))
    }
    CjhChipFlow(chips = chips, modifier = Modifier.padding(top = Dims.loose))
}

/**
 * 一條**可點/可讀的條目行**（稿 `.row-setting`）：圖標 + 標題 + 小字 + `›`。
 *
 * 稿上目錄頁（`cjh.png`）與通知頁（`cjhNotices.png`）都是這種行：
 * 左邊一枚 16dp 線稿圖標、中間兩行字、右邊一個箭頭，**行與行之間只有 1px 分割線**，
 * 沒有任何卡片外框。以前這裡用的是 `SettingGroup`/`SettingRow` ——
 * 那是設置頁的彩色磁貼款，跟稿不是同一種東西。
 *
 * @param body 第三行（通知正文）。給 null 就只有兩行。
 * @param onClick 為 null 時**不畫箭頭也不可點** —— 一條看起來能點卻沒反應的行
 *   比沒有箭頭更糟。
 */
@Composable
fun CjhRowItem(
    title: String,
    icon: MarkIcon,
    modifier: Modifier = Modifier,
    meta: String? = null,
    body: String? = null,
    bodyMaxLines: Int = 6,
    trailing: String? = null,
    trailingColor: Color? = null,
    arrow: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .clickable(onClick = onClick)
                            .heightIn(min = Dims.itemTwoLine)
                    } else {
                        Modifier.heightIn(min = Dims.itemTwoLine)
                    },
                )
                .padding(vertical = Dims.loose),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BodyMark(icon = icon, tint = colors.onSurfaceVariantSummary, size = Mark.rowSize)
            Spacer(Modifier.width(Dims.cardGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = Type.body,
                    fontWeight = Type.weightBody,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!meta.isNullOrBlank()) {
                    Text(
                        text = meta,
                        fontSize = Type.caption,
                        lineHeight = Type.lineCaption,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!body.isNullOrBlank()) {
                    Text(
                        text = body,
                        fontSize = Type.secondary,
                        lineHeight = Type.secondary * Type.LINE_HEIGHT_SMALL,
                        color = colors.onSurface,
                        maxLines = bodyMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Dims.tight),
                    )
                }
            }
            if (!trailing.isNullOrBlank()) {
                Spacer(Modifier.width(Dims.tight))
                Text(
                    text = trailing,
                    fontSize = Type.caption,
                    fontWeight = Type.weightBody,
                    color = trailingColor ?: colors.primary,
                    maxLines = 1,
                )
            }
            if (arrow) {
                Box(
                    modifier = Modifier.width(Dims.minTouch),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = "›",
                        fontSize = Type.body,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.dividerLine))
    }
}
/**
 * 頁尾那一行**真實來源**說明（稿尾 `p.caption.muted`）。
 *
 * 稿上原文逐字是「来源：本地模拟样例 · 演示时间 11月06日 09:40 · 不代表学校实时数据」，
 * 其中「本地模拟样例」「演示时间」與那個時間戳都是原型自己的合成標註 ——
 * 本工程這幾頁接的是真接口，照抄等於對使用者說謊（[CjhOriginSection] 已就這一點寫過註釋）。
 *
 * 所以這裡只保留稿那行的**結構**（「來源：X · 口徑」）與真話：來源是真的接口名，
 * 口徑統一寫「以原站為準」，與 tron 組、學在城院側已在用的寫法一致。
 *
 * @param source 真實來源描述，例如「CJH52 接口」「門戶事務接口」。
 */
@Composable
fun CjhSourceCaption(source: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = "来源：$source · 以原站为准",
        fontSize = Type.caption,
        lineHeight = Type.lineCaption,
        color = colors.onSurfaceVariantSummary,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Dims.screenH,
                end = Dims.screenH,
                top = Dims.loose,
                bottom = Dims.sectionGap,
            ),
    )
}
