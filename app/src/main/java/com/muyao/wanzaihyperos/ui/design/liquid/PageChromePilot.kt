package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「大标题是列表的第 0 项，滚出去之后由顶栏接手小标题」这套接线的**唯一实现**。
 *
 * ## 为什么抽出来（T53 只在課程表那一页做过试点）
 *
 * 试点那段代码里有三件互相牵着的事：列表状态、标题实测高度、折叠进度写回。
 * 「一处修了、别处还错」在这个工程里出现过至少三次（顶栏纱、待办时间差 8 小时、
 * 作业说明的 HTML），所以第二个页面要接的时候先抽成这一份，别再抄第二遍。
 *
 * ## 为什么折叠度只看 `firstVisibleItem`
 *
 * 照参考那份（变焦环 `ui/screen/Page.kt`）的算法，而且它**不接嵌套滚动** ——
 * 这一屏的左右滑已经归"切板块"，再叠一层滚动协议就是两套手势抢同一次拖动。
 *
 * ## ⚠️ 接它的页面要一起做两件事
 *
 * 1. `LazyColumn(state = pilot.listState, …)`：不换上去就没有滚动进度；
 * 2. `contentPadding` 的 `top` **归零**：[LargeTitle] 自带状态栏 inset，
 *    留着原来那 56dp 会出现"标题下面凭空一大块空白"。
 */
class PageChromePilot internal constructor(
    /** 要交给页面的 `LazyColumn(state = …)`。 */
    val listState: LazyListState,
    private val chrome: PageChrome,
) {
    /** 标题实测高度（px）。没量到之前用 1，避免除零。 */
    internal var titleHeightPx by mutableFloatStateOf(1f)

    /** 当前该画的大标题（空 = 这一页不接顶栏，例如首页拿大日期当标题）。 */
    internal var title: String = ""

    /** 只有"在场那一页"能写折叠度，否则离场页会把顶栏状态抢走。 */
    internal var active: Boolean = true

    /**
     * 「**这一页的列表正在滚**」—— 第 9 轮审计必修 4（全工程原本 `scroll` 命中 0 次）。
     *
     * 标准 §4：「静止时停更（内容没变就别重采样），**滚动时降采样**」。
     * 这块界面上的玻璃（顶栏纱 / 底栏 / 搜索钮）都是**每帧各采一次全屏**，
     * 而列表滚动时每一帧背景都在变，正是最贵的时候。
     *
     * ⚠️ kyant 的 backdrop **没有暴露"降采样倍率"**（javap 核过它的 effects API：
     *   只有 `blur` / `lens` / `vibrancy` / 高光），所以这里的"降采样"取的是**能拿到的那个把手**：
     *   滚动期间把**最贵的一步（`lens` 折射，逐像素 SDF）整条关掉 + 模糊减半**，
     *   停手后下一帧恢复。这不是"渲染到半分辨率"，别把它写成那个。
     */
    /**
     * 页面每次重组把列表的滚动状态报进来：
     * `SideEffect { pilot.onScroll(listState.isScrollInProgress) }`。
     * 状态存在 [PageChrome] 上（玻璃那几层是通过 `LocalPageChrome` 读它的），这里只做转发。
     */
    fun onScroll(scrolling: Boolean) {
        chrome.onScroll(scrolling)
    }

    internal fun publish(progress: Float) {
        chrome.progress = progress
    }
}

/**
 * 在页面里取一份顶栏接线。
 *
 * 每次重组都把 `LocalLargeTitle` / `LocalPageActive` 的最新值刷进 pilot ——
 * 它只是持有器（和 [PageChrome] 同类），不是状态源，所以不会造出"两个真相"。
 */
@Composable
fun rememberPageChromePilot(): PageChromePilot {
    val chrome = LocalPageChrome.current
    val largeTitle = LocalLargeTitle.current
    val active = LocalPageActive.current
    val listState = rememberLazyListState()
    val pilot = remember(listState) { PageChromePilot(listState, chrome) }
    pilot.title = largeTitle
    pilot.active = active

    LaunchedEffect(listState, largeTitle, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            if (largeTitle.isEmpty()) 1f
            else if (listState.firstVisibleItemIndex > 0) 1f
            else listState.firstVisibleItemScrollOffset / pilot.titleHeightPx.coerceAtLeast(1f)
        }.collect { pilot.publish(it.coerceIn(0f, 1f)) }
    }
    return pilot
}

/**
 * 把大标题画成**列表的第 0 项**。
 *
 * 标题为空时什么都不画（首页那一支），但页面的 `LazyColumn` 仍然要用
 * [PageChromePilot.listState] —— 否则以后想接回来时，滚动进度是读不到的那种"看起来接了"。
 */
fun LazyListScope.largeTitleItem(
    pilot: PageChromePilot,
    leading: (@Composable () -> Unit)? = null,
) {
    val title = pilot.title
    if (title.isEmpty()) return
    item(key = "large_title") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { if (it.height > 0) pilot.titleHeightPx = it.height.toFloat() },
        ) {
            LargeTitle(text = title, topInset = statusBarTopPadding() + 4.dp, leading = leading)
        }
    }
}

/**
 * 列表底部给底欄留的内距。
 *
 * 留出来不是审美问题，是**功能问题**：内容滚不到玻璃底下，玻璃背后就永远是空白，
 * 那模糊根本看不出效果（业主问"液态玻璃在哪"的根因之一）。
 *
 * **96dp = 板上那根贴底栏占的真实高度**（栏身 58dp + 列表收尾 38dp）。
 * 旧的 132dp 是给「悬浮胶囊」留的 —— 胶囊左右各悬 12dp、下面还有一段空隙，
 * 满宽贴底之后那截空隙不存在了，再留 132 就是板上没有的一段白。
 */
// 业主 2026-10-22 把底栏换回**悬浮玻璃胶囊**（更低、更长）：悬浮件下面还有一段空隙，
// 所以列表底部要比满宽贴底那版（96dp）多留一点，否则最后一行会被胶囊压住半截。
/**
 * 列表底部要给玻璃底栏留的那段内距 —— **必须是底栏真正占掉的高度 + 系统栏 inset**。
 *
 * ★ 独立审计（v3 #12 第 1 轮）：这里以前写死 `108.dp`，**不含系统导航栏 inset**。
 *   底栏自己的实占是 `64dp(条高) + (有 inset ? 2dp + inset : 16dp) + 4dp 余`，
 *   inset = 48dp 时是 **118dp**，比 108 多 10dp（≈35px）⇒ 最后一行会被胶囊压住。
 *   现在读真的 inset（与底栏 `LiquidGlassNavigationBar.kt` 那套算法同一份，不再各猜一个数）。
 */
@androidx.compose.runtime.Composable
fun pageBottomInsetDp(barVisible: Boolean): Dp {
    if (!barVisible) return 44.dp
    val inset = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    val barBottomGap = if (inset != 0.dp) 2.dp + inset else 16.dp
    return 64.dp + barBottomGap + 4.dp
}
