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
fun AboutPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current

    fun open(url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    SecondaryScaffold(
        title = "关于应用",
        onBack = onBack,
        modifier = modifier,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                // 稿 v2.1 `about.png` 第三行「品牌与图标方案 / 名称、图标、遮罩与小尺寸」。
                // 它是**说明**不是链接：稿上那个 `›` 通向品牌页，而品牌页是评审专用
                // （原型里 3 个命名候选是设计阶段的东西）。名字已定稿，这里只把结果写在行上。
                AboutInfoRow(
                    title = "品牌与图标方案",
                    subtitle = "鲸集 · 学士服鲸鱼娘 · 自适应图标（含圆形与五档密度）",
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

        Spacer(Modifier.height(Dims.cardGap))
        Text(
            text = "开源许可 · 感谢这些项目",
            fontSize = Type.body,
            fontWeight = Type.weightTitle,
            color = colors.onBackground,
        )
        Text(
            text = "下面每一条都对应本工程依赖声明里的一个真实依赖（逐个从 Gradle 报告与 Maven POM 取）。",
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(Dims.tight))
        AppCard {
            Column(Modifier.padding(Dims.cardPadding)) {
                Credits.dependencies.forEachIndexed { i, c ->
                    CreditRow(c)
                    if (i != Credits.dependencies.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        // ── 衍生作品署名（许可义务，比依赖更要紧）──────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "改写自的开源作品",
            fontSize = Type.title,
            fontWeight = Type.weightTitle,
            color = colors.onBackground,
        )
        Text(
            // 许可证名**从数据层取**，不在这里手写（`CreditsTest` 守这条）。
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

        // ── 随包字体 ────────────────────────────────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "随包字体",
            fontSize = Type.title,
            fontWeight = Type.weightTitle,
            color = colors.onBackground,
        )
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
private fun CreditRow(c: Credit) {
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
/** 关于页的一行「标题 + 说明」事实（不可点的信息行，不是链接）。 */
@Composable
private fun AboutInfoRow(title: String, subtitle: String) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = title, fontSize = Type.body, color = colors.onBackground)
        Text(
            text = subtitle,
            fontSize = Type.micro,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
