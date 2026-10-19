package com.muyao.wanzaihyperos.ui.design.liquid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 拖拽时的橡皮筋：越拖越沉，但永远拖不出 limit */
private fun rubberBand(raw: Float, limit: Float): Float =
    if (limit <= 0f) 0f else limit * raw.sign * (1f - exp(-abs(raw) / limit))

/**
 * 右上角那颗「重启作用域」。
 *
 * 要求是真液态玻璃 + 按压/拖拽回弹，所以它不是普通 IconButton：
 *  - 玻璃面复用 [GlassSurface]（vibrancy + 折射 + 高光边），和底栏同一套材质；
 *  - 按下缩到 0.88，松手用欠阻尼弹簧弹回（会轻微过冲，这才叫回弹）；
 *  - 能在 ±12dp 里拖着走（橡皮筋），松手弹回原位；
 *  - 位移没超过系统触摸阈值时算点击。
 */
@Composable
fun RestartScopeButton(
    pending: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    /**
     * 要不要真的采页面内容。必须由**调用方**从 `remember(glassTick)` 算好传进来：
     * 在这个函数体里直接读 GlassTuning 是不会触发重组的（热调改了界面不动，
     * 量出来的数就是假的 —— 这个坑我这一轮又踩了一次，见 ui/PageBackdrop.kt 同样的注释）。
     */
    liveBlur: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val dragX = remember { Animatable(0f) }
    val limitPx = with(LocalDensity.current) { 12.dp.toPx() }
    val errorColor = MiuixTheme.colorScheme.error
    val surface = MiuixTheme.colorScheme.surface
    val badge by animateFloatAsState(
        targetValue = if (pending) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f),
        label = "badge",
    )

    Box(modifier = modifier.size(52.dp), contentAlignment = Alignment.Center) {
        GlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            blurDp = 18.dp,
            refractionDp = 20.dp,
            tintAlpha = 0.16f,
            // 默认不采页面内容（原因写在 GlassSurface 的 liveBlur 注释里）：cfg26 实测
            // 打开后它变回实心黑圆盘。现在每页一份采样层 + backdropIsLive 闸门都在了，
            // 用 hzr_glass_btn_live 在真机上 A/B，量出来能糊就把默认改成 1。
            liveBlur = liveBlur,
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    val s = 1f - 0.12f * press.value
                    scaleX = s
                    scaleY = s
                    translationX = dragX.value
                }
                .pointerInput(limitPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        scope.launch { press.animateTo(1f, tween(90)) }
                        var total = 0f
                        var lifted = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                lifted = true
                                break
                            }
                            val d = change.position.x - change.previousPosition.x
                            if (d != 0f) {
                                total += d
                                scope.launch { dragX.snapTo(rubberBand(total, limitPx)) }
                            }
                        }
                        scope.launch {
                            press.animateTo(0f, spring(dampingRatio = 0.36f, stiffness = 560f))
                        }
                        scope.launch {
                            dragX.animateTo(0f, spring(dampingRatio = 0.30f, stiffness = 420f))
                        }
                        if (lifted && abs(total) < viewConfiguration.touchSlop) onClick()
                    }
                },
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = "重新整理",
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (badge > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(13.dp)
                    .graphicsLayer {
                        scaleX = badge
                        scaleY = badge
                    }
                    .background(errorColor, CircleShape)
                    // 一圈底色描边：红点在玻璃上也要有边界
                    .drawBehind {
                        drawCircle(
                            color = surface,
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    },
            )
        }
    }
}

/**
 * iOS 底部动作卡。
 *
 * 弹出时背后是"整屏被糊住 + 压暗"（真 backdrop 模糊），不是单纯盖一层黑；
 * 卡片用弹簧从下面滑上来，取消单独一张卡 —— 它和"危险动作"在 iOS 里不是一个层级。
 */
@Composable
fun RestartScopeSheet(
    show: Boolean,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = { blur(30.dp.toPx(), 30.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.26f)) },
                        )
                    } else {
                        Modifier.background(Color.Black.copy(alpha = 0.42f))
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = show,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                    initialOffsetY = { it },
                ),
                exit = slideOutVertically(
                    animationSpec = spring(dampingRatio = 1.0f, stiffness = 900f),
                    targetOffsetY = { it },
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MiuixTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(26.dp),
                            )
                            // 卡片自己吃掉点击，免得点标题/说明就把卡片关了
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            )
                            .padding(top = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 38.dp, height = 5.dp)
                                .background(
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                    RoundedCornerShape(50),
                                ),
                        )
                        Text(
                            text = "重新整理",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                        Text(
                            text = "确认重启作用域？",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                        )
                        Text(
                            text = "确认",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(onClick = onConfirm)
                                .padding(horizontal = 28.dp, vertical = 14.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(
                                MiuixTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(26.dp),
                            )
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶栏右上角的**搜索玻璃钮**。
 *
 * 业主 2026-10-18 第二轮：上一版把它做成浮在底栏上方的胶囊，
 * 「放你这会误触」—— 它压在课表/列表的内容上，滚动时手指正好从它上面划过。
 * 所以挪到顶栏 `actions` 槽（右上角），那里本来就没有内容手势。
 *
 * 材质仍与底栏、顶栏纱同一套 `GlassSurface`；44dp 可见面 / 52dp 命中区，
 * 按下缩 0.92、松手欠阻尼弹回（和参考那颗圆钮同一节拍）。
 *
 * ⚠️ `backdrop` 必须是**在场那一页**的采样层并过 `backdropIsLive()` 闸门 ——
 * Miuix 对空样本不是「没模糊」而是**画实心黑**（真机量到过）。
 */
@Composable
fun GlassSearchButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    liveBlur: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // 用 clickable + InteractionSource，**不用裸 pointerInput 自己判点击**：
    // 裸手势层不往无障碍树写节点，真机 `uiautomator dump` 里根本找不到它
    //（上一轮踩过：既没法验收，TalkBack 也读不到）。
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.36f, stiffness = 560f),
        label = "searchPress",
    )

    Box(
        modifier = modifier.size(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            blurDp = 18.dp,
            refractionDp = 20.dp,
            tintAlpha = 0.16f,
            liveBlur = liveBlur,
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    val s = 1f - 0.08f * press
                    scaleX = s
                    scaleY = s
                }
                .clickable(interactionSource = interaction, indication = null) { onClick() },
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = MiuixIcons.Search,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { contentDescription = "搜尋課程、應用、通知" },
                )
            }
        }
    }
}
