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
import com.muyao.wanzaihyperos.ui.design.rememberHaptics
import com.muyao.wanzaihyperos.ui.design.rememberMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * 拖拽时的橡皮筋：越拖越沉，但永远拖不出 limit。
 *
 * ★ 从 private 提出来给全站复用（动效标准第 5 步：工程里只允许有这一份橡皮筋，
 *   要跨文件复用就先提出来，别复制第二份公式）。目前两处：
 *   右上角那颗「重启作用域」、课表页的周条横扫切天。
 */
internal fun rubberBand(raw: Float, limit: Float): Float =
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
    val motion = rememberMotion()
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
            // ★★ 第 9 轮审计（业主 13:2x 签字的口径）：**这颗按钮不再是玻璃**。
            //   签字原文：「划出范围就收 —— 玻璃只给浮动面，**开关与返回按钮保持实底 + 描边**」。
            //   它是个"点一下就执行动作"的按钮，不是浮在内容上的面 ⇒ 模糊/折射整条关掉、
            //   垫色提到近实底、不采样页面内容（也就没有了"采到空层画黑"那条路）。
            //   保留的只有**反馈**：按压缩放、拖拽橡皮筋、触觉 —— 那些不是材质。
            blurDp = 0.dp,
            refractionDp = 0.dp,
            tintAlpha = if (isInDarkTheme()) 0.97f else 0.94f,
            // 采样整条关掉（`liveBlur = false` 走的是"不采页面内容"那条分支）
            liveBlur = false,
            baseAlpha = 0f,
            modifier = Modifier
                .size(44.dp)
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 16.dp,
                        color = Color.Black,
                        alpha = if (isInDarkTheme()) 0.35f else 0.10f,
                    ),
                )
                .graphicsLayer {
                    val s = if (motion.canMove) 1f - 0.04f * press.value else 1f
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
                    contentDescription = "刷新",
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
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
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
                            text = "刷新",
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
    val motion = rememberMotion()
    val haptics = rememberHaptics()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        // ★ 0.36 的回弹是从"甩动/拖拽释放"那档借来的，用在一颗按下去的钮上过头了
        //   （业主批准的清单第 4 条：按压反馈该是 100~160ms、0.95~0.98、**不回弹**）。
        //   现在：按下用临界阻尼（不冲过头），松手快速回位。
        animationSpec = spring(
            dampingRatio = if (motion.canMove) 0.72f else 1f,
            stiffness = 900f,
        ),
        label = "searchPress",
    )
    // ★ 「照抄底栏玻璃」的字面做法：底栏那颗的高光是**跟重力转的 BloomStroke**
    //   （`rememberGravityRotatedHighlight(iosIndicatorSpecular, -45f)` + alpha 0.75），
    //   这里直接拿同一份，不再自己写第二个 BloomStroke —— 两块玻璃的光源方向必须一致，
    //   否则同一屏上会出现"底栏的光从左上、右上角的光从别处"。
    val specular = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)
        .copy(alpha = 0.75f)

    // 板 `.icon-btn{width:34px;height:34px}`（× 0.886 ⇒ 30dp）是**描边小圆**那一版；
    // 业主 2026-10-22 v3 清单 #3 直接点名「搜索按钮加大」，所以这里不再回板值。
    // ★ 第 1 轮独立审计（#3 倒退）：工作树一度是 外盒 50 / 盘 40 / 图标 22，
    //   比 `git show HEAD` 的 52/44/22 还**小了 4dp**（盘面积 18627→15394 px²，−17.4%）——
    //   业主说"大一点"，结果改小，这一条按他这次的话定：**盘 52dp / 外盒 56dp / 图标 26dp**。
    // ★ 折射高度必须**小于盘半径**，否则 `liquid/Lens.kt:140` 那条「中心清晰核」的早退永不成立
    //   ⇒ 整盘没有一像素是不折的，看着就是糊成一团的白饼（业主 2026-10-20 报过）。
    //   盘 46dp ⇒ 半径 23dp = **80.5px**（3.5x），而 `GlassTuning.LENS_DEFAULT_DP = 24dp = 84px` —— 不再满足！
    //   B4-01 缩盘时把这条不等式缩反了（660 全绿没抓到：`app/src/test` 里 `lensDp|refraction` 0 命中）。
    //   现在按**这颗盘自己的半径**钳：`min(24dp, 23dp − 3dp) = 20dp = 70px` ⇒ 留 **10.5px** 清核。
    //   ⚠️ 只钳这一处；`GlassTuning.lensDp` 是底栏也在用的旋钮，默认值不许动。
    // ★ 命中区：`.clickable` 原来挂在**盘**上 ⇒ 命中区 = 视觉 = 40dp，低于 Android 官方 48dp 底线，
    //   而 `V3-CHECKLIST.md` 自报的「命中区 ≥44dp」是假的。现在把手势移到外盒（56dp 见下方 Box），
    //   盘只负责画玻璃。
    Box(
        modifier = modifier
            .size(56.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "搜索",
            ) {
                haptics.tick()   // 轻：这一下"点到了"。不给按压做重震动。
                onClick()
            }
            .semantics { role = androidx.compose.ui.semantics.Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        // 盘径与折射高度同源：折射按**这颗盘自己的半径**钳（见上方不等式那段）。
        // 业主 2026-10-25：「首页的搜索按钮太大了，可以小一点」⇒ 盘 46→**42dp**、图标 24→**22dp**、
        // 外盒仍是 56dp（命中区不动，≥44dp 底线是上一轮审计钉住的）。42 仍在 38..48 棘轮带内。
        val discSize = 42.dp
        val refraction = searchRefractionDp(
            lensDp = GlassTuning.lensDp,
            discSizeDp = discSize.value,
        ).dp

        GlassSurface(
            backdrop = backdrop,
            shape = CircleShape,
            // 底栏容器的配比整体搬过来：blur 只有 4dp、折射高度=折射量（都用热调那两个旋钮），
            // 表面铺 surfaceContainer@0.4。上一版这里是 blur 18 / 折射量×0.7 / surface@0.16 ——
            // 折得浅、糊得多，所以他看下来"就是一块白饼，没有玻璃的感觉"。
            blurDp = GlassTuning.barBlurDp.dp,
            // 「磨砂」那一档 = 仍然透光、仍然糊，但**不要那道折射棱**（外观页第三行那格）。
            // 「关闭」不走这里，它由 liveBlur/GlassRenderMode 那条实底分支管（T67 之后一直是对的）。
            refractionDp = if (motion.refractsGlass) refraction else 0.dp,
            tintAlpha = 0.4f,
            // 垫底只留 0.15：它不是外观，是"万一采样层空了"的一层保险色。
            // 上一版这里是 0.62，加上 onDrawSurface 那 0.4 之后**总不透明度 ≈ 77%**，
            // 而底栏容器只有 40% —— 同一屏上底栏是玻璃、这颗是白饼，就是这么来的
            //（真机量：盘内 243/249，盘正下方背景 225 → 基本不透）。
            baseAlpha = 0.15f,
            liveBlur = liveBlur,
            specular = specular,
            modifier = Modifier
                // B4-01（业主 2026-10-23）：「搜索钮再小一点」⇒ 盘径 52→**46dp**。
                // 命中区仍是外层那颗 56dp 的 Box（≥44dp 底线不动）；图标 26→24dp 保持同样的盘内占比。
                // ⚠️ 尺寸只有 `discSize` 这一份：折射高度要按它算（见上），两处各写会再把不等式写反。
                .size(discSize)
                // 柔影也照抄底栏（底栏那条把"边缘一圈奇怪的黑"压掉的阴影）：
                // 玻璃是浮在内容上的，底下必须有一团很淡的影子。
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 16.dp,
                        color = Color.Black,
                        alpha = if (isInDarkTheme()) 0.35f else 0.10f,
                    ),
                )
                .graphicsLayer {
                    // 0.96（原来是 0.92，压得过头，看着像掉下去一块）；
                    // 系统动画关掉时**完全不变形**，只留颜色/涟漪那类反馈。
                    val s = if (motion.canMove) 1f - 0.04f * press else 1f
                    scaleX = s
                    scaleY = s
                },
        ) {
            // ★ 这一层原来在盘上自己画了一圈（上缘白渐变 + 黑描边 + 白描边）：
            //   那是"用画笔画玻璃"，跟底栏的折射高光叠在一起就是两条边，
            //   而且那道 `Color.Black.copy(0.16f)` 的圈在浅色页上看着就是**盘边发黑**。
            //   业主 2026-10-20 报的两条（"不像玻璃" + "会变黑"）都指向这一块 —— 删掉，
            //   玻璃的边缘感全部交给 `specular`（底栏那份 BloomStroke）。
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    // 板上那颗放大镜是**线性**的（`.icon{fill:none;stroke:currentColor}`），
                    // 和底栏那五颗同源：都用 `ic_board_*`，不要再混 Miuix 的面性图标。
                    imageVector = androidx.compose.ui.graphics.vector.ImageVector
                        .vectorResource(com.muyao.wanzaihyperos.R.drawable.ic_board_search),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    // 22dp（2026-10-25 与盘径一起缩：46→42 ⇒ 图标按同一盘内占比 24→22）。
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { contentDescription = "搜索课程、应用、通知" },
                )
            }
        }
    }
}

/**
 * 搜索盘的**折射高度**（dp）：按这颗盘自己的半径钳，保证中心留出清晰核。
 *
 * `liquid/Lens.kt:140` 的着色器里 `if (-sd >= refractionHeight) return content.eval(coord)`
 * 是"中心清晰核"的唯一出口，而 `-sd` 的最大值就是盘半径 ⇒ **折射高度必须小于半径**，
 * 否则整盘每个像素都在折，读起来就是一团白饼（业主 2026-10-20 报的那条）。
 *
 * 留 3dp 余量：`GlassTuning.lensDp` 默认 24dp 在 46dp 盘上会被钳到 20dp，
 * 清核直径 6dp（3.5x 下 10.5px）—— 这是"看得见的一小片清楚"，不是零。
 *
 * ⚠️ 只给搜索盘用；底栏读 `GlassTuning.lensDp` 原值（业主已签，不许跟着钳）。
 * 由 `SearchRefractionTest` 钉住（含变异验收）。
 */
internal fun searchRefractionDp(lensDp: Float, discSizeDp: Float): Float =
    kotlin.math.min(lensDp, (discSizeDp / 2f - 3f).coerceAtLeast(0f))
