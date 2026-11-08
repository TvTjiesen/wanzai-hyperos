package com.muyao.wanzaihyperos.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.R
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
 * 关于应用（A7，2026-11-08 按业主口述二次重做）。
 *
 * ## 这一版和稿的差别是业主亲自定的
 *
 * 稿（`finishing.js:194-208`）画的是「虚线占位框 + 名称待定 + 四行（GitHub / 酷安 /
 * 开源许可 / 品牌与图标方案）」。业主 2026-11-08 给的口述把这四项都改了：
 *
 * > 关于页写：柒合院。浙大（city college 不发音）非官方校园聚合客户端。
 * > ……底下再加一个单开一个容器的作者的页面，点进去就是我的 github 主页，
 * > 要显示我的 github 头像。底下的品牌与图标方案可以去掉。……你还可以写一份
 * > 用户协议和隐私政策在最底下。
 *
 * 于是现在的形状是：**真图标（不再占位）→ 柒合院 → 副标题 → 开源许可 / 作者 →
 * 归属说明 → 用户协议 / 隐私政策 → 版本与包名**。
 *
 * ## 三条纪律
 *
 * 1. **图标是他的原图**：`R.drawable.ic_brand` 来自业主给的 `full.png`（1254×1254）——
 *    2026-11-09 按他口述把那张「带奶油底色的小卡片」抠成了**透明底**（RGBA，四角
 *    alpha=0，只留学生服鲸鱼娘本体），所以它能直接贴在页面渐变上；仍然不是生成图，
 *    抠底脚本只删背景像素，没重画任何一笔。
 * 2. **顶栏只留返回箭头**：业主口述去掉右上角的搜索和「关于应用」那一行标题。搜索入口
 *    仍在底栏与设置页，这里少一枚按下去只是重复的按钮。
 * 3. **外链只放他给过的**：GitHub 与酷安都来自 `AboutLinks`（他本人主页）。GitHub
 *    移进作者页，因为那一页要连头像一起展示；关于页只留一个入口。
 * 4. **协议与隐私是本地文本**：`LegalText.kt` 里的常量，字面量写在源码里，不联网、
 *    不拉远程文档 —— 断网时也必须读得到。
 *
 * 换算沿用稿的比例：稿 392px 画板 ↔ 真机 347dp ⇒ 1px = 0.886dp；
 * 稿的 `--font:1.15` 与系统字号相抵 ⇒ **sp = px × 0.886**。
 */
@Composable
fun AboutPage(
    onBack: () -> Unit,
    onSearch: (() -> Unit)? = null,
    /** A9：开源许可与字体（三级页）。 */
    onOpenLicenses: () -> Unit = {},
    /** 作者页：头像 + GitHub 主页（三级页）。 */
    onOpenAuthor: () -> Unit = {},
    /** 用户协议（三级页）。 */
    onOpenTerms: () -> Unit = {},
    /** 隐私政策（三级页）。 */
    onOpenPrivacy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current

    // 外链一律交给系统浏览器（与作者页同一套做法），应用内不内嵌 WebView。
    fun openUrl(url: String) {
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
        // ★ 2026-11-09 业主口述：关于页顶栏不要搜索图标、也不要「关于应用」那一行字，
        //   整页只留左上角的返回箭头 —— 进来的路只有设置页那一条，不需要在这里再搜一遍。
        title = "",
        onBack = onBack,
        onSearch = null,
        modifier = modifier,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                // 顶栏内距放在滚动容器外面：内容视口从栏下开始，
                // 正文就永远滚不到标题底下（2026-11-08 定案，见回执 §十）。
                .padding(top = inner.calculateTopPadding())
                .verticalScroll(scroll)
                .padding(horizontal = Dims.screenH)
                .padding(
                    top = 6.dp,
                    bottom = inner.calculateBottomPadding() + 24.dp,
                ),
        ) {
            // ── 应用图标（透明底版本，直接铺在页面底色上，没有卡片、没有描边）──
            // ★ 2026-11-09 业主更正：**不要**把应用图标换成头像 —— 头像只作为「作者」
            //   行前面那枚小图标；应用图标留在原位。
            // ★ 2026-11-09 业主口述：「把图标的背景变透明，用那个无背景的图标，图标再大一点」。
            //   原图（`full.png`）是**带奶油色底的一张卡片**，这里换成了抠掉底色的
            //   `ic_brand.png`（RGBA，四角 alpha=0，只留学士服鲸鱼娘本体），
            //   于是图标与页面渐变直接接上，不再像一张小卡片浮在上面。
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_brand),
                    contentDescription = "柒合院的图标",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(168.dp),
                )
            }

            // ── 名称 + 两行副标题（居中）──────────────────────────────────
            Text(
                text = "柒合院",
                fontSize = Type.displayTitle,
                fontWeight = Type.weightTitle,
                letterSpacing = (-0.9).sp,
                lineHeight = Type.displayTitle * 1.18f,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 10.dp),
            )
            Text(
                text = "浙大（city college 不发音）非官方校园聚合客户端\n" +
                    "设计提案 · 原App版本 0.1.0",
                fontSize = Type.summary,
                lineHeight = Type.summary * 1.5f,
                color = colors.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // ── 第一张卡：原项目仓库 + 许可 + 作者 ────────────────────────
            FlushCard {
                AboutRow(
                    mark = MarkIcon.Link,
                    title = "GitHub",
                    subtitle = AboutLinks.repo.removePrefix("https://"),
                    onClick = if (AboutLinks.repo.isNotBlank()) {
                        { openUrl(AboutLinks.repo) }
                    } else {
                        null
                    },
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
                    mark = MarkIcon.Account,
                    title = "作者",
                    subtitle = "TvTjiesen · GitHub 主页与头像",
                    avatar = true,
                    onClick = onOpenAuthor,
                )
            }

            // ── 最底下：用户协议与隐私政策 ────────────────────────────────
            // 2026-11-09 业主（原话）：「把这一页底下的注释全部去掉，就是类似于开源许可下面的小字」
            // → 「我他妈说的是用户协议和隐私政策下面的字」。
            // 这两行原来各挂一句副标题（「使用本应用前请先读这一份」/
            // 「说清楚本机存了什么、什么不会离开手机」），现在只留标题——
            // 标题本身已经把事说清楚了，再补一句等于替读者做决定。
            FlushCard(modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)) {
                AboutRow(
                    mark = MarkIcon.File,
                    title = "用户协议",
                    onClick = onOpenTerms,
                )
                RowDivider()
                AboutRow(
                    mark = MarkIcon.Lock,
                    title = "隐私政策",
                    onClick = onOpenPrivacy,
                )
            }
        }
    }
}

/** 稿 `.paper.flush`：表面色 + 1px 描边 + 20px 圆角 + **无内距**，行自己带内距。 */
@Composable
internal fun FlushCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
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
internal fun RowDivider() {
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
internal fun AboutRow(
    mark: MarkIcon,
    title: String,
    /** 留空就不画副标题（2026-11-09 业主让「下面的字」滚蛋的那几行）。 */
    subtitle: String = "",
    /** 传 true 时这一行左边用作者头像，而不是 [mark] 那枚线性图标（业主 2026-11-09）。 */
    avatar: Boolean = false,
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
            if (avatar) {
                Image(
                    painter = painterResource(R.drawable.author_avatar),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                )
            } else {
                BodyMark(icon = mark, tint = colors.onSurfaceVariantSummary, size = 20.dp)
            }
        }
        Spacer(Modifier.width(10.6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = Type.bodyEmphasis,
                lineHeight = Type.bodyEmphasis * 1.5f,
                color = colors.onBackground,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = Type.caption,
                    lineHeight = Type.caption * 1.5f,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.7.dp),
                )
            }
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
