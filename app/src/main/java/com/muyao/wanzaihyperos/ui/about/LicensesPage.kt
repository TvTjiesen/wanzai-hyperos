package com.muyao.wanzaihyperos.ui.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import com.muyao.wanzaihyperos.data.Credit
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
fun LicensesPage(
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
        title = "开源与字体",
        onBack = onBack,
        onSearch = onSearch,
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
            // 稿（screens/licenses.png）是一段说明 + **一张卡**，不是三张分组卡：
            // 依赖 / 改写 / 字体在同一份清单里按登记顺序往下排，中间只有分隔线。
            Text(
                text = "沿用 App 当前登记的依赖、改编与字体信息。版本更新后仍须跟随 Gradle 核验。",
                fontSize = Type.summary,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))

            // 顺序＝登记顺序：先依赖，再改写自的开源作品，最后随包字体。
            // 三组共 22 条，一条不少（CreditsTest 对着 Gradle 报告核条数）。
            val all: List<Credit> = Credits.dependencies + Credits.derived + Credits.fonts
            AppCard {
                Column(Modifier.padding(horizontal = 14.5.dp, vertical = 4.dp)) {
                    all.forEachIndexed { i, c ->
                        CreditRow(c)
                        if (i != all.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.dividerLine.copy(alpha = 0.6f)),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * 一条许可记录：名称 / 坐标+版本（等宽数字）/ 许可名。
 *
 * 2026-11-08 从 `AboutPage.kt` 搬到这里：关于页改成稿子的四行摘要之后，
 * 只有许可页渲染明细，函数跟着数据走，避免「关于页留着一个没人调的 public 组件」。
 */
@Composable
private fun CreditRow(c: Credit) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = c.name,
            fontSize = Type.summary,
            fontWeight = Type.weightTitle,
            color = colors.onBackground,
        )
        Text(
            text = c.coordinate + " " + c.version,
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            style = androidx.compose.ui.text.TextStyle(
                fontFeatureSettings = Type.tabular,
            ),
        )
        Text(text = c.license, fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
    }
}
