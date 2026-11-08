package com.muyao.wanzaihyperos.ui.about

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A6：品牌与图标（二级页，从设置首屏「名称与图标」进来）。
 *
 * ## 结构与换算
 *
 * 一比一照稿 `screens/brand.png`（392×850 画板 ↔ 347dp ⇒ 1px = 0.886dp；
 * 稿内 `--font:1.15` 与手机系统字号 1.15 相抵 ⇒ sp 令牌 = px × 0.886）：
 *
 *   占位框「你的图标」→ display-title「把名字留给你。」→ 一段说明
 *   → section「三个命名方向」+ 一张卡（三条候选名，各带一句）
 *   → 一句「均为讨论候选…」→ section「素材交接」+ 一张卡（一段说明）
 *
 * 稿里**没有**四档尺寸预览、没有折叠的「交付与授权条件」、没有「已定稿」字样 ——
 * 那些是我上一版自己加的，2026-11-08 按业主「跟稿子不一样」的意见全部拆掉。
 * 名称状态仍然是**待定**：稿子标题本身就是「把名字留给你。」。
 *
 * 图标与名称都由业主提供，本页只写交付条件，不把任何生成图当成已交付资产。
 */
@Composable
fun BrandPage(
    onBack: () -> Unit,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme

    // 内容会捲到透明顶栏下面：用「已捲距离 / 一屏」驱动顶栏那层纱，
    // 否则标题会被正文压住（真机在开源许可页看到过）。
    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)
    SecondaryScaffold(
        scrollProgress = veil,
        title = "品牌与图标",
        onBack = onBack,
        onSearch = onSearch,
        modifier = modifier,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding())
                .verticalScroll(scroll)
                .padding(horizontal = Dims.screenH)
                .padding(
                    top = 6.dp,
                    bottom = inner.calculateBottomPadding() + 24.dp,
                ),
        ) {
            // ── 占位框 + 大标题 + 说明 ────────────────────────────────
            IconSlot()
            Spacer(Modifier.height(21.dp))
            Text(
                text = "把名字留给你。",
                fontSize = Type.displayTitle,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Spacer(Modifier.height(11.dp))
            Text(
                text = "当前只锁定界面方向，不锁定名称；先前候选图标不再作为实施素材。",
                fontSize = Type.summary,
                color = colors.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── 三个命名方向 ─────────────────────────────────────────
            SectionHeading(text = "三个命名方向")
            AppCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                    NameLine(name = "汐见", why = "清爽克制，带一点日系日常感；建议副标题「城院校园助手」。")
                    Spacer(Modifier.height(14.dp))
                    NameLine(name = "晴屿", why = "偏温暖、明亮的校园生活气质。")
                    Spacer(Modifier.height(14.dp))
                    NameLine(name = "星汐", why = "二次元气质更明显，适合你的角色图标。")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "均为讨论候选，未检索商标、应用重名或域名，不自动改包名。",
                fontSize = Type.summary,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 2.dp),
            )

            // ── 素材交接 ─────────────────────────────────────────────
            SectionHeading(text = "素材交接")
            AppCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                    Text(
                        text = "由你提供透明前景、背景及希望保留的安全区。原生需分别验收 launcher、" +
                            "圆形遮罩、单色图标与启动页。没有素材前只用占位，不复用旧候选。",
                        fontSize = Type.summary,
                        color = colors.onBackground,
                    )
                }
            }
        }
    }
}

/**
 * 稿 `polish.css:138 .user-icon-slot`：112px 见方 / 28px 圆角 / 1px 虚线 primary，
 * 底色是 `var(--container)`（浅蓝 #E5E9F4），文字 13.8sp 也取 primary。
 * ⇒ 99dp / 25dp 圆角 / 虚线 1dp / `primary.copy(alpha = .10f)`。
 */
@Composable
private fun IconSlot() {
    val colors = MiuixTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .size(99.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(colors.primary.copy(alpha = 0.10f))
                .drawBehind {
                    // 虚线边框：Compose 的 border 不支持虚线，只能自己描。
                    val stroke = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    )
                    drawRoundRect(
                        color = colors.primary,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(25.dp.toPx()),
                        style = stroke,
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = "你的图标",
                fontSize = Type.bodyEmphasis,
                color = colors.primary,
            )
        }
    }
}

/** 稿 `.section-heading`：h3 20.3px→18sp（`Type.title`），上 18px 下 8px。 */
@Composable
private fun SectionHeading(text: String) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = text,
        fontSize = Type.title,
        fontWeight = Type.weightTitle,
        color = colors.onBackground,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 7.dp)
            .semantics { heading() },
    )
}

/** 稿 `.paper h4`（15.8px→14sp）+ `p.small.muted`（13.54→12sp）。 */
@Composable
private fun NameLine(name: String, why: String) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(text = name, fontSize = Type.bodyEmphasis, fontWeight = Type.weightTitle, color = colors.onBackground)
        Spacer(Modifier.height(3.dp))
        Text(text = why, fontSize = Type.summary, color = colors.onSurfaceVariantSummary)
    }
}
