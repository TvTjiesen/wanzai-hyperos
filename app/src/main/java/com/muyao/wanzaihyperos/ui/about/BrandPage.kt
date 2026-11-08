package com.muyao.wanzaihyperos.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A6：品牌与图标（二级页，从设置首屏「名称与图标」进来）。
 *
 * ## 这一页的立场：把「还没定」的事照实写出来
 *
 * 业主 2026-11-07 说过现用名不满意（「名称再想一个，这个我不满意，听不够可爱」），
 * 所以这一页的名称状态只能是 **待定** —— 不接受任何「已定稿」的说法，
 * 也不把 AI 生成的候选图当成已经交付的资产。
 *
 * 页面上每一条硬信息都写明来源：名字是他给的、图标也是他给的，
 * 本应用只负责把它们装进包、并说明交付条件。
 */
@Composable
fun BrandPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    var showDelivery by remember { mutableStateOf(false) }

    // 内容会捲到透明顶栏下面：用「已捲距离 / 一屏」驱动顶栏那层纱，
    // 否则标题会被正文压住（真机在开源许可页看到过）。
    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)
    SecondaryScaffold(
        scrollProgress = veil,
        title = "名称与图标",
        subtitle = "待业主确认",
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
            // ── 眉标 + 大标题 ──────────────────────────────────────────
            Text(
                text = "RECOMMENDED NAME · 待审核",
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "鲸集",
                fontSize = Type.displayTitle,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                // 命名理由写在这里，不写「已定稿」。
                text = "现用名。取「鲸」的体量与「集」的聚合，呼应校园入口把六七个系统收进一处的做法。" +
                    "业主 2026-11-07 表示不够满意，仍在再想 —— 所以这个名字**还不是最终名**。",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )

            // ── 名字候选卡 ────────────────────────────────────────────
            Spacer(Modifier.height(Dims.cardGap))
            AppCard {
                Column(Modifier.padding(Dims.cardPadding)) {
                    NameRow(status = "现用名", name = "鲸集", why = "体量 + 聚合，最好念，两字。", chosen = true)
                    Spacer(Modifier.height(10.dp))
                    NameRow(status = "备选一", name = "鲸集校园", why = "限定范围，适合应用商店重名时分栏。", chosen = false)
                    Spacer(Modifier.height(10.dp))
                    NameRow(status = "备选二", name = "潮汐集", why = "呼应课表的节次起伏；缺点是四个字念着更绕。", chosen = false)
                }
            }

            // ── 角色 ─────────────────────────────────────────────────
            Spacer(Modifier.height(Dims.cardGap))
            Text(
                text = "角色",
                fontSize = Type.body,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(Dims.tight))
            AppCard {
                Row(
                    modifier = Modifier.padding(Dims.cardPadding).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(com.muyao.wanzaihyperos.R.drawable.ic_brand),
                        contentDescription = "应用图标",
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "学士服鲸鱼娘",
                            fontSize = Type.body,
                            fontWeight = Type.weightTitle,
                            color = colors.onBackground,
                        )
                        Text(
                            text = "角色与图标由业主提供。本应用只做包内集成（含圆形遮罩与五档密度），" +
                                "不把任何生成图当作已交付的最终资产。",
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            // ── 四个尺寸预览 ──────────────────────────────────────────
            Spacer(Modifier.height(Dims.cardGap))
            Text(
                text = "小尺寸预览",
                fontSize = Type.body,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(Dims.tight))
            AppCard {
                Row(
                    modifier = Modifier.padding(Dims.cardPadding).fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    listOf(32, 48, 64, 96).forEach { px ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(com.muyao.wanzaihyperos.R.drawable.ic_brand),
                                contentDescription = "${px}px 图标预览",
                                modifier = Modifier.size(px.dp).clip(RoundedCornerShape((px / 4).dp)),
                            )
                            Text(
                                text = "${px}px",
                                fontSize = Type.micro,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            // ── 提示条：还没做的检索 ───────────────────────────────────
            Spacer(Modifier.height(Dims.cardGap))
            Text(
                text = "尚未做商标与各应用商店的重名检索。名称与图标都要等业主逐项确认，" +
                    "确认之前不对外宣称。",
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dims.chipCorner))
                    .background(colors.onBackground.copy(alpha = 0.05f))
                    .padding(10.dp),
            )

            // ── 折叠：交付与授权条件 ───────────────────────────────────
            Spacer(Modifier.height(Dims.cardGap))
            Text(
                text = if (showDelivery) "交付与授权条件 ▴" else "交付与授权条件 ▾",
                fontSize = Type.body,
                fontWeight = Type.weightTitle,
                color = colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dims.chipCorner))
                    .background(colors.onBackground.copy(alpha = 0.04f))
                    .clickable { showDelivery = !showDelivery }
                    .padding(12.dp),
            )
            if (showDelivery) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.padding(horizontal = 4.dp)) {
                    DeliveryLine("前景层", "108dp 画布内的图形，四周留 4dp 余量。")
                    DeliveryLine("背景层", "纯色 #FAF6EB（与 `ic_launcher_background` 同值）。")
                    DeliveryLine("安全区", "圆形遮罩下按 66dp 直径验收，四角内容会被裁掉。")
                    DeliveryLine("单色版", "通知栏与快捷方式另需要一版单色剪影，单独验。")
                    DeliveryLine("授权", "角色形象的使用范围由业主书面确认；本应用只在包内使用。")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 名字候选的一行：状态 + 名字 + 一句理由。 */
@Composable
private fun NameRow(status: String, name: String, why: String, chosen: Boolean) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = status,
            fontSize = Type.micro,
            color = if (chosen) colors.primary else colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(52.dp).padding(top = 3.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(text = name, fontSize = Type.body, color = colors.onBackground)
            Text(text = why, fontSize = Type.micro, color = colors.onSurfaceVariantSummary)
        }
    }
}

/** 交付条件的一条。 */
@Composable
private fun DeliveryLine(label: String, text: String) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.width(56.dp),
        )
        Text(text = text, fontSize = Type.caption, color = colors.onBackground, modifier = Modifier.weight(1f))
    }
}
