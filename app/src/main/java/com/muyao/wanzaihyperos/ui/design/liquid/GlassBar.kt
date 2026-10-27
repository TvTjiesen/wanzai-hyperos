package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/**
 * 底栏：**业主 2026-10-22 明确要回来的那一版液态玻璃**（会滑过去的玻璃滑块 +
 * 阻尼跟随 + 松手回弹 + 越界橡皮筋），不是 v2 定稿板上那根满宽贴底的薄荷块。
 *
 * ## 这条与板的冲突怎么记（审计不许再按板扣）
 *
 * 板上 `.tabbar` 是「满宽贴底 + --accent-soft 小方块」。业主看完真机后原话：
 * 「帮我把底栏改成之前的液态玻璃，位置稍微低一点，长一点」⇒ **业主的话覆盖板**，
 * 记在 `docs/V3-CHECKLIST.md` 的冲突小节。
 *
 * ## 「低一点、长一点」落在哪两个数上
 *
 *  ★ 这几个数以前记的是「左右 12dp -> 6dp」「底部 12dp -> 4dp」，**与 git 里的实值不符**
 *  （独立审计 v3 #12 第 1 轮点名：照注释复算会算错）。改准：
 *  1. 左右 **26dp -> 6dp**（长：胶囊几乎顶到屏幕两边）；
 *  2. 底部 **12dp -> 4dp**，另有 [IosLiquidGlassNavigationBar] 内部那 **24dp** 内衬
 *     收到 2dp（手势导航）/ 36dp -> 16dp（有虚拟键）—— 上一版注释**漏计了那 24dp 内衬**，
 *     所以"整条往下沉"到底沉了多少，按旧注释算不出来。
 *
 * ## 玻璃三档在这里是真的生效
 *
 *  - 关闭（glassIsTranslucent = false）=> isBlurActive = false，**连采样层都不挂**，
 *    走近实底分支（T67「空样本被画成不透明黑」那条路就此封死）；
 *  - 磨砂（refractsGlass = false）=> 仍透光 + 糊，但 lensDp = 0，折射量归零；
 *  - 液态 => 透光 + 糊 + 折射。
 */
data class GlassNavItem(val label: String, @DrawableRes val iconRes: Int)

@Composable
fun GlassNavBar(
    items: List<GlassNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    blurDp: Float = GlassTuning.BAR_DEFAULT_DP,
    lensDp: Float = GlassTuning.LENS_DEFAULT_DP,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotion()
    // 采样层必须真的录到内容才交给它 —— Miuix 对空样本不是"没模糊"，而是画实心黑。
    val live = backdrop != null && backdropIsLive(backdrop) && motion.glassIsTranslucent
    IosLiquidGlassNavigationBar(
        items = items.map { NavigationItem(it.label, ImageVector.vectorResource(it.iconRes)) },
        selectedIndex = selected,
        onItemClick = onSelect,
        backdrop = backdrop,
        isBlurActive = live,
        blurDp = blurDp,
        lensDp = if (motion.refractsGlass) lensDp else 0f,
        modifier = modifier
            .padding(start = 6.dp, end = 6.dp, bottom = 4.dp)
            .fillMaxWidth(),
    )
}
