package com.muyao.wanzaihyperos.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.BuildConfig
import com.muyao.wanzaihyperos.data.AboutLinks
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于应用（B4-10 / A7，2026-11-08 按稿重排）。
 *
 * ## 这一页的形状是照稿子抄的，不是自己设计的
 *
 * 稿 `deliverables/campus-redesign-v2.1/prototype`（`style.css` + `finishing.js:194-208`）：
 * 居中虚线占位框「你的图标」→ 居中大标题「名称待定」→ 居中两行副标题 →
 * 一张**无内距**的卡片（4 行：图标 + 标题 + 小字 + ›）→ 卡外两段说明。
 *
 * 上一版把这四行 + 资料来源全塞进一张带内距的卡里、把标题缩成 18sp 正文，
 * 于是一整页读起来像「设置的续集」而不是「关于」。业主 2026-11-08 直接点出这一点。
 *
 * 换算：稿是 392px 宽的画板，真机 347dp ⇒ 1px = 0.886dp；
 * 稿的 `--font:1.15` 与手机系统字号 1.15 相抵，所以 **sp = px × 0.886**。
 * 大标题 29.35px⇒26sp、行标题 15.8px⇒14sp、行小字 12.42px⇒11sp、
 * 正文小字 13.54px⇒12sp、说明 11.29px⇒10sp、占位框 112px⇒99dp。
 *
 * ## 两条纪律
 *
 * 1. **外链只放他给过的**：`AboutLinks` 来自他本人的 `Links.kt:13-15`；
 *    本 App 自己的仓库还没建（B4-09），GitHub 这一行指向**他的主页**。
 * 2. **许可条数不在这里手写**：这一页只写「依赖 N 条 · 改写自 M 件 · 字体 K 款」的摘要，
 *    明细在 `LicensesPage`，数字取自 `Credits` 数据层（`CreditsTest` 反向对账）。
 */
@Composable
fun AboutPage(
    onBack: () -> Unit,
    onSearch: (() -> Unit)? = null,
    /** A9：开源许可与字体（三级页）。 */
    onOpenLicenses: () -> Unit = {},
    /** A6：品牌与图标（二级页）。 */
    onOpenBrand: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current

    fun open(url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // 内容会捲到透明顶栏下面：用「已捲距离 / 一屏」驱动顶栏那层纱。
    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)
    SecondaryScaffold(
        scrollProgress = veil,
        title = "关于应用",
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
            // ── 你的图标（稿 `.user-icon-slot`：112px 方框、虚线 1px、24px 圆角）──
            // 图标由业主提供，这里给的是**占位框**，不是把生成图当最终资产（A6 红线）。
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 21.dp)
                        .size(99.dp)
                        .clip(RoundedCornerShape(25.dp))
                        // 稿 .user-icon-slot 的底色是 var(--container)（#E5E9F4 淡蓝），
                        // 不是卡片白——用卡片白时这块和后面的卡面糊成一片。
                        .background(colors.primary.copy(alpha = 0.10f))
                        .drawBehind {
                            // 稿 .user-icon-slot 是 border:1px dashed var(--primary)：
                            // 虚线是「这里还空着、等你给素材」的意思，实线会被读成一个输入框。
                            drawRoundRect(
                                color = colors.primary,
                                cornerRadius = CornerRadius(25.dp.toPx()),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                                    ),
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "你的图标",
                        fontSize = Type.bodyEmphasis,
                        color = colors.primary,
                    )
                }
            }

            // ── 名称待定 + 两行副标题（稿：居中）────────────────────────────
            Text(
                text = "名称待定",
                fontSize = Type.displayTitle,
                fontWeight = Type.weightTitle,
                letterSpacing = (-0.9).sp,
                lineHeight = Type.displayTitle * 1.18f,
                color = colors.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 10.dp),
            )
            Text(
                text = "非官方校园聚合客户端\n设计提案 · 原App版本 0.1.0",
                fontSize = Type.summary,
                lineHeight = Type.summary * 1.5f,
                color = colors.onSurfaceVariantSummary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // ── 四行卡（稿 `.paper.flush`：无内距、行间 1px 分隔）────────────
            FlushCard {
                AboutRow(
                    mark = MarkIcon.Link,
                    title = "GitHub",
                    subtitle = "保留原App已有项目链接，不猜造新地址",
                    onClick = if (AboutLinks.github.isNotBlank()) {
                        { open(AboutLinks.github) }
                    } else null,
                )
                RowDivider()
                AboutRow(
                    mark = MarkIcon.Link,
                    title = "酷安",
                    subtitle = "保留原有主页入口",
                    onClick = if (AboutLinks.coolapk.isNotBlank()) {
                        { open(AboutLinks.coolapk) }
                    } else null,
                )
                RowDivider()
                AboutRow(
                    mark = MarkIcon.Book,
                    title = "开源许可",
                    subtitle = "沿用项目完整许可清单",
                    onClick = onOpenLicenses,
                )
                RowDivider()
                AboutRow(
                    mark = MarkIcon.Star,
                    title = "品牌与图标方案",
                    subtitle = "名称、图标、遮罩与小尺寸",
                    onClick = onOpenBrand,
                )
            }

            // ── 卡外两段说明（稿：`.small.muted.gap-top` 与 `.caption.muted`）──
            Text(
                text = "资料来自浙大城市学院门户与各业务系统，内容及数据归学校。" +
                    "产品并非学校或 DeepSeek 官方出品；角色致意与正式授权不能混为一谈。" +
                    "本应用与学校无隶属关系，登录用的账号密码始终由你自己输入。",
                fontSize = Type.summary,
                lineHeight = Type.summary * 1.5f,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "实现保持 com.muyao.wanzaihyperos，不为品牌改名迁移安装包身份。" +
                    "包名与签名在升级中保持不变。当前构建 " + BuildConfig.VERSION_NAME + "。",
                fontSize = Type.micro,
                lineHeight = Type.micro * 1.5f,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 10.dp, bottom = 32.dp),
            )
        }
    }
}

/** 稿 `.paper.flush`：表面色 + 1px 描边 + 20px 圆角 + **无内距**，行自己带内距。 */
@Composable
private fun FlushCard(content: @Composable () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.7.dp))
            .background(colors.surface)
            .border(
                width = 1.dp,
                color = colors.dividerLine.copy(alpha = 0.5f),
                shape = RoundedCornerShape(17.7.dp),
            ),
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.6f)),
    )
}

/**
 * 稿 `.row-setting`：26.6dp 图标格（图标 quiet 色）+ 标题 14sp + 小字 11sp + › 16dp；
 * 行高 67dp（76px），左右内距 14dp / 上下 12dp。
 */
@Composable
private fun AboutRow(
    mark: MarkIcon,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = title) { onClick() } else Modifier)
            // 稿 .row-setting min-height:76px padding:14px 16px：76px×0.886≈67dp、14px≈12.4dp。
            // 不钉这个下限，四行靠内容自己撑只有 43dp，整张卡比稿矮一截、字挤在一起。
            .heightIn(min = 67.dp)
            .padding(horizontal = 14.5.dp, vertical = 12.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.6.dp), contentAlignment = Alignment.Center) {
            BodyMark(icon = mark, tint = colors.onSurfaceVariantSummary, size = 20.dp)
        }
        Spacer(Modifier.width(10.6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = Type.bodyEmphasis,
                lineHeight = Type.bodyEmphasis * 1.5f,
                color = colors.onBackground,
            )
            Text(
                text = subtitle,
                fontSize = Type.caption,
                lineHeight = Type.caption * 1.5f,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.7.dp),
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = colors.onSurfaceVariantSummary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
