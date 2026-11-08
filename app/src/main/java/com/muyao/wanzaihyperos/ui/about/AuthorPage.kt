package com.muyao.wanzaihyperos.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.R
import com.muyao.wanzaihyperos.data.AboutLinks
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 作者页（业主 2026-11-08 口述）：
 *
 * > 再加个单开一个容器的作者的页面，点进去就是我的 github 主页，要显示我的 github 头像。
 *
 * ## 头像与链接都是从哪来的
 *
 * - 头像：`R.drawable.author_avatar` —— 取自他的 GitHub 头像
 *   （`https://github.com/TvTjiesen.png`，320×320，构建期一次性落盘）。
 *   **运行时不联网**，断网也画得出来。
 * - GitHub：`AboutLinks.github` = `https://github.com/TvTjiesen`（他本人主页）。
 * - 酷安：`AboutLinks.coolapk` = 他本人的酷安主页。这两个链接原先在关于页，
 *   现在集中到这一页：关于页只留一个「作者」入口，不再摆两条外链。
 *
 * 两条都走系统浏览器（`Intent.ACTION_VIEW`），应用内不内嵌 WebView ——
 * 免得为了一个"关于"页面再引一套网页容器与它的权限。
 */
@Composable
fun AuthorPage(
    onBack: () -> Unit,
    onSearch: (() -> Unit)? = null,
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

    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)

    SecondaryScaffold(
        scrollProgress = veil,
        title = "作者",
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
                .padding(top = 6.dp, bottom = inner.calculateBottomPadding() + 24.dp),
        ) {
            // ── 头像 + 名字 ───────────────────────────────────────────────
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.author_avatar),
                    contentDescription = "作者的 GitHub 头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 22.dp, bottom = 14.dp)
                        .size(88.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.dividerLine.copy(alpha = 0.6f), CircleShape),
                )
            }
            Text(
                text = "TvTjiesen",
                fontSize = Type.displayTitle,
                fontWeight = Type.weightTitle,
                letterSpacing = (-0.9).sp,
                lineHeight = Type.displayTitle * 1.18f,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "柒合院的作者 · 浙大城市学院",
                fontSize = Type.summary,
                lineHeight = Type.summary * 1.5f,
                color = colors.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Spacer(Modifier.height(18.dp))

            // ── 两个主页 ─────────────────────────────────────────────────
            FlushCard {
                AboutRow(
                    mark = MarkIcon.Link,
                    title = "GitHub 主页",
                    subtitle = "github.com/TvTjiesen",
                    onClick = if (AboutLinks.github.isNotBlank()) {
                        { open(AboutLinks.github) }
                    } else null,
                )
                RowDivider()
                AboutRow(
                    mark = MarkIcon.App,
                    title = "酷安主页",
                    subtitle = if (AboutLinks.coolapk.isNotBlank()) {
                        // 2026-11-09 业主：「作者那页加上酷安的描述」——
                        // 只丢一个地址等于什么都没说，补半句这个号在酷安是什么。
                        // 不写「更新只发这里」「有问题必回」这类没发生过的事。
                        "coolapk.com/u/22174009 · 酷安，安卓应用社区"
                    } else {
                        "尚未填写"
                    },
                    onClick = if (AboutLinks.coolapk.isNotBlank()) {
                        { open(AboutLinks.coolapk) }
                    } else null,
                )
            }

            // 2026-11-09 业主：「把底下的注释都去掉」—— 页尾说明不再保留，
            // 只留一点底部余量，免得卡片贴住手势条。
            Spacer(Modifier.height(32.dp))
        }
    }
}
