package com.muyao.wanzaihyperos.nav

import androidx.compose.animation.core.FastOutSlowInEasing
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavRole
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navDirectionalTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition

/**
 * 路由（二级页）切换动效：**照抄参考（变焦环 `[local]\hzr` MainActivity 的 AnimatedContent）**。
 *
 * ## 为什么要换掉 `NavTransitions.MiuixDefault`
 *
 * 业主要的是「全部套用变焦环的动效」，不是只有底部五个 tab 那一段。
 * tab 那段在 `WanzaiMainShell` 的 `AnimatedContent` 里，数值已经 1:1 抄过去了；
 * 但**点进任何一个二级页**（校历 / 成绩 / 通知 / 待办 / 搜索 / Web 外壳，共 13 条路由）
 * 走的是 `NavDisplay`，那里当时还是 Miuix 预设 —— 预设只有位移 + 轻微压暗，
 * **没有缩放、也没有整页淡入**，看起来就是"硬切"，和他骂的那句"雷霆动效"对得上。
 *
 * ## 为什么不能直接写 `slideInHorizontally(280) { it / 5 }`
 *
 * Miuix 的 nav 只暴露**基于深度**的 API（javap 核过 `miuix-nav` 0.9.4 产物）：
 * `navGraphicsTransition(opaqueDepth, dismissDirection, motion, scrim, block)`
 * + `navDirectionalTransition(push, pop, predictivePop)`，
 * `block` 里能改的只有 graphicsLayer。所以参考那套
 * `EnterTransition + ExitTransition` 的**时长**得换算成一条深度曲线：
 *
 * - 总时长 = 位移的 280ms（`motion` 只有一个，不能每个属性各给一条）；
 * - 淡入在参考里是 200ms ⇒ 深度每走 `1 - 200/280` 就已经不透明了，乘 1.4 再夹 [0,1]；
 * - 淡出 160ms ⇒ 深度走到 `160/280 ≈ 0.571` 就该全透明，乘 1.75 再夹 [0,1]。
 *
 * 位移/缩放的比例（1/5、1/9、0.97、0.98）与参考逐字相同，缓动同为
 * `FastOutSlowInEasing`。**一次只改这一处**，别的玻璃参数一律没动。
 *
 * ## 深度约定（从 `MiuixDefault$lambda$0` 的字节码读出来的，不是猜的）
 *
 * 稳定在栈顶时 `relativeDepth = 0`；进场页 1 → 0，离场页 0 → 1，
 * 所以进场用 `d`、离场用 `1 - d` 的方向是反的，必须按 `role` 分支。
 */
/**
 * @param motionMs 整条深度动画的时长；**0 = 用 Miuix 自带的默认运动曲线**，
 *   这样他能在不重装的前提下 A/B「参考的 280ms」和「库默认」哪个跟底栏更配
 *   （热调键 `wz_nav_ms`，见 [com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.navMs]）。
 */
fun wanzaiRouteTransitions(motionMs: Int): NavTransition {
    val motion = if (motionMs <= 0) {
        NavMotion()
    } else {
        NavMotion(
            NavSettleSpec.Tween(motionMs, FastOutSlowInEasing),
            NavSettleSpec.Tween(motionMs, FastOutSlowInEasing),
            NavSettleSpec.Tween(motionMs, FastOutSlowInEasing),
        )
    }
    // 位移/淡入/淡出的**比例**按 280ms 这条时间轴算出来的（见档头），
    // 时长被热调时比例仍然自洽：它们是无量纲的进度倍数。
    return navDirectionalTransition(
        // 前进：新页从右被推进来，旧页往左被推走
        wanzaiPageTransition(sign = 1f, motion = motion),
        // 后退 / 预测式返回：整体镜像
        wanzaiPageTransition(sign = -1f, motion = motion),
        wanzaiPageTransition(sign = -1f, motion = motion),
    )
}

/** 位移总时长 = 参考里 `tween<IntOffset>(280)`，可被热调覆盖。 */
private const val SLIDE_MS = 280

/** 进场位移：参考 `slideInHorizontally { it / 5 * dir }`。 */
private const val ENTER_SHIFT = 1f / 5f

/** 离场位移：参考 `slideOutHorizontally { -it / 9 * dir }`。 */
private const val EXIT_SHIFT = 1f / 9f

/** 进场起始缩放：参考 `scaleIn(initialScale = 0.97f)`。 */
private const val ENTER_SCALE_FROM = 0.97f

/** 离场目标缩放：参考 `scaleOut(targetScale = 0.98f)`。 */
private const val EXIT_SCALE_TO = 0.98f

/** 淡入 200ms / 总 280ms ⇒ 深度进度要乘这个倍数才对齐时间轴。 */
private const val ENTER_RATE = SLIDE_MS / 200f

/** 淡出 160ms / 总 280ms。 */
private const val EXIT_RATE = SLIDE_MS / 160f

private fun wanzaiPageTransition(sign: Float, motion: NavMotion): NavTransition =
    navGraphicsTransition(
        // 与 `MiuixDefault` 实抄时取的是同一个值（字节码里 `fconst_1`），不改它
        opaqueDepth = 1f,
        motion = motion,
    ) { scope ->
    // `block` 的真身是 `GraphicsLayerScope.(NavTransitionScope) -> Unit`：
    // 接收者是 graphicsLayer，参数才是深度上下文（编译器只在这一种形状下认）。
    val width = scope.layoutSize.width
    val depth = scope.relativeDepth
    val leaving = scope.role == NavRole.Outgoing || scope.role == NavRole.Covered

    if (leaving) {
        // 参考的离场：slideOut 280ms + fadeOut 160ms + scaleOut 0.98
        val fade = (depth * ENTER_RATE).coerceIn(0f, 1f)
        alpha = (1f - depth * EXIT_RATE).coerceIn(0f, 1f)
        translationX = -width * EXIT_SHIFT * depth * sign
        val s = 1f + (EXIT_SCALE_TO - 1f) * fade
        scaleX = s
        scaleY = s
    } else {
        // 参考的进场：slideIn 280ms + fadeIn 200ms + scaleIn 0.97
        val away = 1f - depth
        val fade = (away * ENTER_RATE).coerceIn(0f, 1f)
        alpha = fade
        translationX = width * ENTER_SHIFT * depth * sign
        val s = ENTER_SCALE_FROM + (1f - ENTER_SCALE_FROM) * fade
        scaleX = s
        scaleY = s
    }
}
