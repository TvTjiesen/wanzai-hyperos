package com.muyao.wanzaihyperos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.ui.design.Dims

/**
 * 「展示用数据」显式标注条。
 *
 * 方案的硬性规定：**示例数据不得冒充真实数据**。A1 全是示例，
 * 所以首页/应用/消息三页顶部都挂这一条，说清「这些数字不是学校服务器的」。
 * 颜色用 `tertiaryContainer`（信息角色），不是 error —— 它不是故障。
 */
@Composable
fun DemoBanner(
    text: String = "A1 展示版：以下内容为示例数据，尚未接入学校接口",
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(Dims.containerCorner))
            .background(colors.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = MiuixIcons.Info,
            contentDescription = null,
            tint = colors.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onTertiaryContainer,
        )
    }
}

/** 数据新鲜度提示（例如「12 分钟前更新」）。用于说明数字有多新。 */
@Composable
fun FreshnessNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier.padding(horizontal = 28.dp, vertical = 2.dp),
    )
}

/**
 * 页面级状态视图：加载中 / 空 / 错误 / 离线 / 登录失效 / 数据过期。
 *
 * 方案要求「必须覆盖 加载中 / 空 / 错误 / 离线 / 数据过期 / 登录失效」，
 * 这里把它们做成**一个显式枚举**而不是若干个 boolean ——
 * 否则「空」与「还没加载完」会被同一个 `isLoading=false` 混起来，
 * 出现「加载失败却显示『暂无数据』」这种替服务器撒谎的界面。
 */
enum class PageState {
    /** 首次加载中（还没有任何内容可显示）。 */
    Loading,

    /** 加载成功且有内容。 */
    Content,

    /** 加载成功但结果为空（服务端明确说没有）。 */
    Empty,

    /** 取数失败：网络不可达或服务端异常。 */
    Error,

    /** 离线：有上次的数据可显示，但已不新鲜。 */
    Offline,

    /** 数据过期：显示的是缓存，需要提示更新时间。 */
    Stale,

    /** 登录态失效：必须重新登录，且**不能**显示成 0 或空。 */
    SessionExpired,
}

/**
 * 状态占位视图。[onRetry] 为 null 时不画重试按钮（例如「登录失效」应引导去登录）。
 *
 * 文案纪律：说人话、不说内部接口名，并且纠正「未知当 0」——
 * 「取不到」必须与「没有」分开说（这是 Flutter 版业主提过的原始问题）。
 */
@Composable
fun StatePlaceholder(
    state: PageState,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    val colors = MiuixTheme.colorScheme
    if (state == PageState.Content) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state == PageState.Loading) {
            top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                color = colors.primary,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Icon(
                imageVector = if (state == PageState.Error || state == PageState.Offline) MiuixIcons.Refresh else MiuixIcons.Info,
                contentDescription = null,
                tint = when (state) {
                    PageState.Error, PageState.SessionExpired -> colors.error
                    else -> colors.onSurfaceVariantSummary
                },
                modifier = Modifier.size(28.dp),
            )
        }

        Text(
            text = titleFor(state),
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )

        val line = detail ?: detailFor(state)
        if (line.isNotEmpty()) {
            Text(
                text = line,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }

        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) {
                Text(text = "重试")
            }
        }
        if (onAction != null && actionLabel != null) {
            TextButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun titleFor(state: PageState): String = when (state) {
    PageState.Loading -> "正在加载…"
    PageState.Empty -> "暂无内容"
    PageState.Error -> "加载失败"
    PageState.Offline -> "网络不可达"
    PageState.Stale -> "显示的是缓存数据"
    PageState.SessionExpired -> "登录状态已失效"
    PageState.Content -> ""
}

private fun detailFor(state: PageState): String = when (state) {
    PageState.Loading -> ""
    PageState.Empty -> "服务端明确返回了空结果（不是加载失败）"
    PageState.Error -> "取不到内容：网络不可达或服务端异常（不是「没有内容」）"
    PageState.Offline -> "请检查网络后重试；已显示的内容可能不是最新的"
    PageState.Stale -> "下拉刷新可获取最新内容"
    PageState.SessionExpired -> "请到「我的」重新登录后再试"
    PageState.Content -> ""
}

/**
 * 宫格里的一个应用瓷贴。
 *
 * 图标是**本地推断**的，底色优先用门户下发的 `app_color`；
 * 不下载门户的 `app_img`（87 张外链图 = 87 次请求，违反 QPS≤1）。
 */
@Composable
fun AppTile(
    app: PortalApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Dims.containerCorner))
                    .background(app.effectiveTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = app.icon,
                    contentDescription = null,
                    tint = app.effectiveTint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = app.name,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 非宫格场景的一行应用（列表用）。 */
@Composable
fun AppRow(
    app: PortalApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    // ★ 規範 §5：交互件／圖示底一律**全圓**。
                    //   36dp 上套 12 的方圓角，在小尺寸下看起來像「被削過」；
                    //   全圓（CircleShape）才是 iOS 觀感。
                    .clip(CircleShape)
                    .background(app.effectiveTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = app.icon,
                    contentDescription = null,
                    tint = app.effectiveTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MiuixTheme.textStyles.body1,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = app.subtitle ?: app.system.label
                Text(
                    text = sub,
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (app.needsCampusNetwork) {
                Text(
                    text = "校园网",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantActions,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

/** 小色块（用于状态点/未读点）。 */
@Composable
fun Dot(color: Color, size: Int = 8, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}
