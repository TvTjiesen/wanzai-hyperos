package com.muyao.wanzaihyperos.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.AboutLinks
import com.muyao.wanzaihyperos.data.Credit
import com.muyao.wanzaihyperos.data.Credits
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于 + 感谢专区（B4-10，业主 2026-10-23）。
 *
 * ## 两条纪律写在这一页的实现里
 *
 * 1. **外链只放他给过的**：`AboutLinks` 里的地址来自他本人的
 *    `hzr/app/.../util/Links.kt:13-15`（GitHub 主人 `TvTjiesen`、酷安 `22174009`）。
 *    ⚠️ 业主给的那条是 **OmniRing**（另一个 App）的仓库地址；本 App 自己的库还没建（B4-09），
 *    所以 GitHub 这一行指向**他的主页**（`https://github.com/TvTjiesen`）—— 打开是真页面，
 *    也不会把"这是本 App 的源码"这句话说成假话。B4-09 建库之后把 `AboutLinks.github`
 *    改成那个仓库的地址即可（`CreditsTest` 会守着"非空且 https"）。
 * 2. **许可证逐条来自依赖声明**：`Credits.dependencies` 的每一条都是从前一次
 *    Gradle 依赖报告 + Maven POM 抄下来的（`CreditsTest` 反向对账），这一页只负责**渲染**，
 *    不在这里手写任何一条 —— 手写就会出现"界面上列了一个根本没用的库"。
 */
@Composable
fun AboutPage(
    onBack: () -> Unit,
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

    // 内容会捲到透明顶栏下面：用「已捲距离 / 一屏」驱动顶栏那层纱，
    // 否则标题会被正文压住（真机在开源许可页看到过）。
    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)
    SecondaryScaffold(
        scrollProgress = veil,
        title = "关于应用",
        onBack = onBack,
        modifier = modifier,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Dims.screenH)
                .padding(
                    top = inner.calculateTopPadding(),
                    bottom = inner.calculateBottomPadding(),
                ),
        ) {
            // ── 图标 + 名称（稿 v2.1 `about.png` 顶部）────────────────────────
            // 稿上这里写的是「你的图标 / 名称待定 / 非官方校园聚合客户端设计提案」——
            // 那是命名还没定的时候。现在名字和图标都定稿了，就照实写定稿的结果，
            // 并把「提案」两个字去掉：这不是提案了。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(com.muyao.wanzaihyperos.R.drawable.ic_brand),
                    contentDescription = "鲸集的应用图标",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "鲸集",
                        fontSize = Type.title,
                        fontWeight = Type.weightTitle,
                        color = colors.onBackground,
                    )
                    Text(
                        text = "浙大城市学院校园聚合入口 · 学士服鲸鱼娘",
                        fontSize = Type.micro,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(Dims.cardGap))

        Text(
            text = "版本 ${com.muyao.wanzaihyperos.BuildConfig.VERSION_NAME}（${com.muyao.wanzaihyperos.BuildConfig.VERSION_CODE}）· 非官方客户端",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )

        Spacer(Modifier.height(Dims.cardGap))
        AppCard {
            Column(Modifier.padding(Dims.cardPadding)) {
                LinkRow("GitHub", AboutLinks.github, enabled = AboutLinks.github.isNotBlank()) { open(AboutLinks.github) }
                Spacer(Modifier.height(Dims.tight))
                LinkRow("酷安", AboutLinks.coolapk, enabled = AboutLinks.coolapk.isNotBlank()) { open(AboutLinks.coolapk) }
                Spacer(Modifier.height(Dims.tight))
                // A6/A7：「品牌与图标方案」不再是死行 —— 稿上那个 `›` 通向品牌页，
                // 页里写名称状态（待定）、角色来源与四个尺寸。
                NavRow(
                    title = "品牌与图标方案",
                    subtitle = "鲸集 · 学士服鲸鱼娘 · 名称待业主确认",
                    onClickLabel = "打开品牌与图标方案",
                    onClick = onOpenBrand,
                )
                Spacer(Modifier.height(Dims.tight))
                // A9：开源许可从「内嵌三组清单」收成一行摘要，点进独立页。
                NavRow(
                    title = "开源许可",
                    subtitle = "依赖 " + Credits.dependencies.size + " 条 · 改写自 " + Credits.derived.size + " 件 · 字体 " + Credits.fonts.size + " 款",
                    onClickLabel = "打开开源许可",
                    onClick = onOpenLicenses,
                )
                Spacer(Modifier.height(Dims.tight))
                Text(
                    text = "资料来源：浙大城市学院门户与各业务系统，资料所有权归学校。" +
                    "产品并非学校或 DeepSeek 官方出品；角色致意与正式授权不能混为一谈。" +
                        "本应用与学校无隶属关系，登录用的账号密码始终由你自己输入。",
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }

        // A9：三组清单已搬到 ui/about/LicensesPage.kt（条数与 Credits 数据层一致，
        // 由 LicensesPageOnceTest 断言）。关于页只留摘要入口，见上面那张卡。
        Spacer(Modifier.height(Dims.cardGap))
        Text(
            text = "包名与签名在升级中保持不变；本应用与学校、DeepSeek 均无隶属关系。",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClickLabel = "打开 $label") { onClick() } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = Type.body, color = colors.onBackground)
        Spacer(Modifier.weight(1f))
        Text(
            // 空着就照实写「尚未填写」并且不可点 —— 一条打不开的死链比空着更糟（`CreditsTest` 的注释）。
            text = if (enabled) url.removePrefix("https://").removePrefix("http://") else "尚未填写",
            fontSize = Type.caption,
            color = if (enabled) colors.primary else colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
internal fun CreditRow(c: Credit) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = c.name,
            fontSize = Type.summary,
            fontWeight = Type.weightTitle,
            color = colors.onBackground,
        )
        Text(
            text = "${c.coordinate} ${c.version}".trim(),
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            style = androidx.compose.ui.text.TextStyle(
                fontFeatureSettings = Type.tabular,
            ),
        )
        Text(text = c.license, fontSize = Type.caption, color = colors.onSurfaceVariantSummary)
    }
}
/** 关于页里通向下一级页面的行（标题 + 说明 + ›）。 */
@Composable
private fun NavRow(title: String, subtitle: String, onClickLabel: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.chipCorner))
            .clickable(onClickLabel = onClickLabel) { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, fontSize = Type.body, color = colors.onBackground)
            Text(
                text = subtitle,
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(text = "›", fontSize = Type.body, color = colors.onSurfaceVariantSummary)
    }
}

