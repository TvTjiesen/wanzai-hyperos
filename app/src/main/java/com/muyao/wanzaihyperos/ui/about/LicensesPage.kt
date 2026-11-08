package com.muyao.wanzaihyperos.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.Credits
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A9：开源许可与字体（三级页，从关于页「开源许可」进来）。
 *
 * ## 为什么单独一页
 *
 * 关于页的职责是「这个应用是谁、从哪来」；许可清单是**法律义务**，
 * 三组加起来二十多条，压在关于页底部会把「资料来源」这段要紧的说明挤没。
 *
 * ## 数据一条都不在这页手写
 *
 * 全部来自 [Credits]（依赖 / 改写自的开源作品 / 随包字体），
 * 而 `Credits` 又由 `CreditsTest` 对着 Gradle 依赖报告反向核验。
 * 这一页只负责渲染 —— 在这里补一条就会变成「界面上列了一个根本没用的库」。
 */
@Composable
fun LicensesPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme

    // 内容会捲到透明顶栏下面：用「已捲距离 / 一屏」驱动顶栏那层纱，
    // 否则标题会被正文压住（真机在开源许可页看到过）。
    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)
    SecondaryScaffold(
        scrollProgress = veil,
        title = "开源许可",
        subtitle = "依赖 / 改写 / 字体",
        onBack = onBack,
        modifier = modifier,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Dims.screenH)
                .padding(
                    top = inner.calculateTopPadding() + 6.dp,
                    bottom = inner.calculateBottomPadding(),
                ),
        ) {
            Text(
                text = "下面每一条都对应本工程依赖声明里的一个真实依赖，版本取解析后的实际版本；" +
                    "许可证名从各自 POM 取，不凭记忆写。升级依赖后重跑 `CreditsTest` 会把它对齐。",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )

            // ── 依赖 ───────────────────────────────────────────────────
            Spacer(Modifier.height(Dims.cardGap))
            Text(text = "依赖", fontSize = Type.body, fontWeight = Type.weightTitle, color = colors.onBackground)
            Spacer(Modifier.height(Dims.tight))
            AppCard {
                Column(Modifier.padding(Dims.cardPadding)) {
                    Credits.dependencies.forEachIndexed { i, c ->
                        CreditRow(c)
                        if (i != Credits.dependencies.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // ── 改写自的开源作品（许可义务，比依赖更要紧）──────────────
            Spacer(Modifier.height(24.dp))
            Text(
                text = "改写自的开源作品",
                fontSize = Type.title,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
            )
            Text(
                // 许可证名从数据层取，不在这里手写（`CreditsTest` 守这条）。
                text = "这些不是依赖，是把别人的实现改写进了本工程 —— " +
                    "${Credits.derived.map { it.license }.distinct().joinToString(" / ")} 要求署名。",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(Dims.tight))
            AppCard {
                Column(Modifier.padding(Dims.cardPadding)) {
                    Credits.derived.forEachIndexed { i, c ->
                        CreditRow(c)
                        if (i != Credits.derived.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // ── 随包字体 ───────────────────────────────────────────────
            Spacer(Modifier.height(24.dp))
            Text(text = "随包字体", fontSize = Type.title, fontWeight = Type.weightTitle, color = colors.onBackground)
            Spacer(Modifier.height(Dims.tight))
            AppCard {
                Column(Modifier.padding(Dims.cardPadding)) {
                    Credits.fonts.forEachIndexed { i, c ->
                        CreditRow(c)
                        if (i != Credits.fonts.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
