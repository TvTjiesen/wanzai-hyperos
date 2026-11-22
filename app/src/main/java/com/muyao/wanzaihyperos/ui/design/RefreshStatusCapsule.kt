package com.muyao.wanzaihyperos.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListMeta
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RefreshStatusCapsule(meta: ListMeta, settledLabel: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val motion = rememberMotion()
    val tint by animateColorAsState(
        if (meta.lastRefreshError != null) colors.error else if (meta.refreshing) colors.primary else colors.onSurfaceVariantSummary,
        animationSpec = if (motion.canMove) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else snap(), label = "refresh-tint",
    )
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite }
        .background(tint.copy(alpha = if (motion.highContrast) .16f else .08f), RoundedCornerShape(24.dp))
        .animateContentSize(if (motion.canMove) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else snap())
        .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (meta.refreshing) 8.dp else 5.dp).background(tint, RoundedCornerShape(50)))
        Text(if (meta.refreshing) "正在更新 · 保留已有内容" else settledLabel,
            color = tint, fontSize = Type.caption, modifier = Modifier.weight(1f, fill = false))
    }
}

/**
 * 全局后台刷新胶囊（动效 9）。
 *
 * 只映射**真实请求状态**：[inFlight] 是 [com.muyao.wanzaihyperos.app.AppGraph] 的
 * `BusyTracker` 在途计数（`fetch` / `runTracked` 的 begin/end），不是任何页面的
 * 「检查时间」——检查时间不得伪装为内容更新（提案 §9 约束）。计数归零后胶囊
 * 先短暂停在「已完成」再收回，用户也可以随时点掉；没有无限转圈。
 */
@Composable
fun GlobalRefreshCapsule(inFlight: StateFlow<Int>, modifier: Modifier = Modifier) {
    val active by inFlight.collectAsState()
    val motion = rememberMotion()
    // 归零后短暂展示「已完成」，再整体收回（无无限转圈：展示态有硬上限）。
    var settledTick by remember { mutableStateOf(0) }
    var wasActive by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active > 0) {
            wasActive = true
            dismissed = false
        } else if (wasActive) {
            wasActive = false
            settledTick++
            delay(1800)
            settledTick = 0
        }
    }
    val showing = (active > 0 && !dismissed) || settledTick > 0
    AnimatedVisibility(
        visible = showing,
        modifier = modifier,
        enter = if (motion.canMove) fadeIn() + slideInVertically { -it / 2 } else fadeIn(),
        exit = if (motion.canMove) fadeOut() + slideOutVertically { -it / 2 } else fadeOut(),
    ) {
        val colors = MiuixTheme.colorScheme
        val tint by animateColorAsState(
            if (active > 0) colors.primary else colors.onSurfaceVariantSummary,
            animationSpec = if (motion.canMove) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else snap(),
            label = "global-refresh-tint",
        )
        Row(Modifier
            .padding(horizontal = 16.dp)
            // 实底浮层：叠加在页头之上也保持可读（此前近透明底与标题文字混成一团）。
            .shadow(if (motion.highContrast) 0.dp else 8.dp, RoundedCornerShape(24.dp))
            .background(colors.surface, RoundedCornerShape(24.dp))
            .clickable { if (active > 0) dismissed = true else settledTick = 0 }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (active > 0) 8.dp else 5.dp).background(tint, RoundedCornerShape(50)))
            Text(
                if (active > 0) "正在更新 · 保留已有内容" else "已完成",
                color = tint, fontSize = Type.caption,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
