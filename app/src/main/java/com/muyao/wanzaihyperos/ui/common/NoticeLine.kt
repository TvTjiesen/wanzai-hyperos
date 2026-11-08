package com.muyao.wanzaihyperos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 说明条（v2.1 稿的 `.notice`）：**左侧一道 2dp 竖线 + 淡底**，不是一张卡片。
 *
 * ## 为什么抽到 common
 *
 * 稿子里 `.notice` 出现在十几个页面（首页签到说明、无障碍页脚、提醒页排程说明、
 * 课表来源说明…），此前 `ui/busyness` 与 `ui/cjh52` 各自抄了一份私有副本。
 * 那两份是同一视觉的两次实现，改一处另一处就漂 —— 这一批对稿开始把
 * 「页面级说明条」收敛到这里，新页面直接用，不再各自 fork。
 *
 * 视觉来源：`BusynessPage.kt` 的 `SecNotice`（真机验过的那份），
 * 与稿 `.notice` 的「竖线 + 淡底 + 圆角只收右侧两角」一致。
 *
 * @param error 用错误色（取数失败、权限被拒这类需要处理的说明）。
 */
@Composable
fun NoticeLine(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val tint = if (error) colors.error else colors.onSurfaceVariantSummary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.55f)),
        verticalAlignment = Alignment.CenterVertically,
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
