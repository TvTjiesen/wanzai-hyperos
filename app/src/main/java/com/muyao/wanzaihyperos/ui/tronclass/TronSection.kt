package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Mark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 學在城院各頁的分區標題 —— **正文前一枚圖標**。
 *
 * ### 為什麼要這一層，而不是直接用 Miuix 的 `SmallTitle`
 *
 * 業主要求每個區塊標題前有一枚語義圖標（見 `ui/design/BodyMarks.kt` 的說明：
 * 那枚圖標取代的是板上 `.section-heading h3::before` 一條 4×14dp 的斜條 ——
 * 斜條只能說「這裡有分區」，說不出**是哪一個**分區）。
 * Miuix 的 `SmallTitle` 只有一行字，塞不進圖標，所以這裡自己排一行；
 * 排法沿用工程裡已有的 `JwPage.kt:646 SecSectionHead`（同一套 `Type.title` + `Type.weightTitle`）。
 *
 * ### 為什麼是 `internal` 且先在這個目錄裡
 *
 * 學在城院這幾頁的分區標題**長得一樣就該長得一樣** —— 散在各檔裡各排一遍，
 * 早晚出現一邊 18sp 一邊 20sp。共享設計層（`AppLayout.kt`）是 Lead 獨佔的，
 * 所以這一枚先落在本目錄，穩定之後再往上提。
 *
 * @param trailing 右側那一小塊（計數、「查看」之類）。不傳就不佔位。
 */
@Composable
internal fun TronSection(
    text: String,
    icon: MarkIcon,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier.padding(top = Dims.sectionGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        BodyMark(icon = icon, tint = colors.primary, size = Mark.size)
        Text(
            text = text,
            fontSize = Type.title,
            fontWeight = Type.weightTitle,
            color = colors.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            trailing()
        }
    }
}
/**
 * 殼內子頁的**頁頭三行**：眉題 / 大標題 / 一句副題。
 *
 * 照稿 `.eyebrow` + `.hero-title` + `.muted`（`screens/courseAnnouncements.png`、
 * `courseDiscussions.png` 那三行）。排法與 `ui/busyness/BusynessPage.kt:450 SecHero` 一致：
 * 眉題 11sp 加 0.10em 字距、大標題 26sp 收 -0.02em、副題 11sp 灰。
 *
 * ### 為什麼不直接把標題塞進 [com.muyao.wanzaihyperos.ui.design.SecondaryScaffold]
 *
 * 殼的 title 是 52dp 那一行，**跟著頁面固定**；這一塊是列表首項，會隨內容捲走。
 * 稿上兩者同時存在（頂欄「全部课程公告」+ 正文大標題「全部公告」），
 * 且殼的 title 在子頁裡要**保持課名**（Lead 的硬約束：子頁不另起一顆頂欄）。
 */
@Composable
internal fun TronHero(eyebrow: String, title: String, sub: String, icon: MarkIcon? = null) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        // 眉題那一行前放一枚語義圖標 —— 「每個區塊標題前有一枚」這條要求在
        // 這一頁沒有 SmallTitle 可掛（塊頭就是這三行），所以掛在眉題上。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (icon != null) {
                BodyMark(icon = icon, tint = colors.primary, size = Mark.size)
            }
            Text(
                text = eyebrow,
                fontSize = Type.caption,
                fontWeight = Type.weightTitle,
                letterSpacing = 0.10.em,
                color = colors.onSurfaceVariantSummary,
                style = TextStyle(fontFeatureSettings = Type.tabular),
            )
        }
        Text(
            text = title,
            fontSize = Type.displayTitle,
            lineHeight = Type.lineDisplayTitle,
            fontWeight = Type.weightTitle,
            letterSpacing = (-0.02).em,
            color = colors.onBackground,
            modifier = Modifier.padding(top = Dims.tight, bottom = Dims.tight),
        )
        Text(
            text = sub,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Start,
        )
    }
}

/**
 * **說明條**：左緣一道 2dp 豎線 + 淡底（照稿 `.notice`，不是一張卡片）。
 *
 * 與 [com.muyao.wanzaihyperos.ui.design.HintText] 的差別是「輕重」：
 * HintText 是一句腳註，這是**獨立成塊的說明**（稿上 `courseAnnouncements` /
 * `courseDiscussions` 各有一塊）。排法沿用 `ui/busyness/BusynessPage.kt:476 SecNotice`。
 */
@Composable
internal fun TronNotice(text: String, error: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val tint = if (error) colors.error else colors.onSurfaceVariantSummary
    Row(
        modifier = Modifier
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
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.padding(
                horizontal = Dims.cardPadding,
                vertical = Dims.loose,
            ),
        )
    }
}
