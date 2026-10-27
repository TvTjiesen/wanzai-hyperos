package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 页面内容自己的采样层：由"当前这一页"创建，进场时贴给栏类读。
 *
 * 为什么不再由 MainActivity 建一个大家共用（这是 2026-10-15 定位到的根因）：
 * Miuix 0.9.2 的 `LayerBackdropNode.onDetach()` 会把 `backdrop.layerCoordinates`
 * 直接置成 null（反编译 miuix-blur-android-0.9.2 的字节码得到的死证据），而
 * `onGloballyPositioned` 只在真的重新布局时才把坐标补回来。我们原先所有页面在
 * AnimatedContent 里共享同一个对象 → **切页时离场页一 detach 就把坐标清了**，
 * 之后每个 `drawBackdrop` 都采到空层，退化成"半透明但没模糊"。
 * 这就是他报的"只有首次打开正常，之后都不正常"：首次打开还没切过页，坐标仍在。
 *
 * 也不许把 `layerBackdrop` 挂回包着整页的外层：外层再叠 AnimatedContent 自己的
 * graphicsLayer 会让拖拽坐标算歪，真机实测"点击有效、整 App 一律滚不动"
 * （摘掉外层 layerBackdrop 后同一次滑动能改 80 万像素，挂着就是 0）。
 *
 * 生命周期跟着页面 composition 走：AnimatedContent 同时最多存在两份页面，
 * 所以离屏 layer 的峰值就是 2 层，不会随访问过的页面数一直涨。
 *
 * @param recordColor 采样层里先铺的页面底色（透明区域不铺底的话模糊出来是糊成一片而不是"化开"）
 */
@Composable
fun rememberPageBackdrop(recordColor: Color): LayerBackdrop = rememberLayerBackdrop {
    drawRect(recordColor)
    drawContent()
}

/**
 * 写入口：在场页面把自己的采样层贴进来，顶栏/底栏读它的 value。
 *
 * 用 `MutableState` 而不是直接给值：换页时这个引用会变，栏类必须因此重新组合，
 * 否则它们会一直画第一次那一页的层。null = 现在没有任何页面在录内容（刚进场/正在退出）。
 */
val LocalBackdropSink = compositionLocalOf<MutableState<LayerBackdrop?>?> { null }

/**
 * 测量开关（adb `settings put global hzr_glass_off 1`）：true = 页面不挂采样层。
 *
 * 走 CompositionLocal 而不是让页面直接读 GlassTuning：读一个全局对象不会触发重组，
 * 他改完开关界面不动，量出来的数就是假的。
 */
val LocalGlassRecorderOff = compositionLocalOf { false }

/**
 * 自检用：现在这个采样层到底有没有坐标（coordinates == null 就是"实时模糊已经死了"）。
 *
 * 为什么不能只靠截图比像素：浅色主题下页面本身接近纯白，"糊没糊"在像素差上几乎看不出来，
 * 我上一轮就是被这个骗过一次（同一次滑动的位移用图像互相关量出来 255px 和 50px，
 * 换成 uiautomator 里"同一行文字的 y 差"之后两版都是 ~1489px，说明那个图像指标根本不可信）。
 * 坐标这个量 Miuix 只在 internal 面上给，所以反射拿它的方法名（带 Kotlin 修饰后缀）。
 */
fun backdropIsLive(backdrop: Any?): Boolean = backdropHasCoordinates(backdrop)

/**
 * 「这一份采样层**刚刚才换上来**」⇒ 头几帧还不许拿它画玻璃。
 *
 * ## 为什么坐标闸门不够（业主 2026-10-20：「一点进去就看到了个雷霆大黑色搜索框」）
 *
 * `backdropIsLive` 只证明**登记过坐标**。而 `LayerBackdrop` 的 RenderNode 在**第一次被绘制之前**
 * 是一张没有内容的缓冲 —— Miuix 拿它去模糊/折射不会退回"没效果"，而是把**不透明黑**铺出来。
 * 顶栏那条纱整条都踩在这个窗口上（它只铺 0.18~0.45 的浅色，压不住黑），所以"进去那一瞬间"
 * 是黑带，不是白饼。
 *
 * ## 为什么用"换引用后空两帧"而不是"记录有没有画过"
 *
 * 想在绘制阶段写一个状态位告诉组合期"我画过了"——**绘制期写 snapshot 状态会自激重绘**，
 * 轻则掉帧重则卡死。而这里要的其实只是躲开"新层还没画第一帧"这个窗口：
 * 每一页的采样层是自己 `remember` 出来的，**换页/重建 = 换一个实例**，
 * 引用一变就空两帧，正好盖住那个窗口；层一旦开始画，后面每帧都在画，不会再回到空缓冲。
 */
@Composable
fun rememberSampleSettled(backdrop: Any?): Boolean {
    var settled by remember(backdrop) { mutableStateOf(false) }
    LaunchedEffect(backdrop) {
        settled = false
        // 两帧：第一帧给新层做布局，第二帧它必然已经画过一遍了。
        withFrameNanos { }
        withFrameNanos { }
        settled = true
    }
    return settled
}

/**
 * 「这一份采样层登记过坐标」—— 对**任何** Backdrop 都判得动。
 *
 * ★ 独立审计（v3 #12 第 1 轮）之后补的兼容：这里以前写的是 `backdrop as? LayerBackdrop`，
 *   于是 `CombinedBackdrop`（底栏那颗滑块与搜索盘采的就是它，两层）一律判 false，
 *   拿它当闸门的调用点会**全部退成实心底** —— 那就是"修一个空缓冲画黑，顺手把玻璃全打死"。
 *   改反射取 `getLayerCoordinates`（本来就已经在反射了），两类层都判得到。
 */
private fun backdropHasCoordinates(backdrop: Any?): Boolean {
    if (backdrop == null) return false
    return runCatching {
        val m = backdrop.javaClass.methods.firstOrNull { it.name.startsWith("getLayerCoordinates") }
            ?: return false
        m.invoke(backdrop) != null
    }.getOrDefault(false)
}
