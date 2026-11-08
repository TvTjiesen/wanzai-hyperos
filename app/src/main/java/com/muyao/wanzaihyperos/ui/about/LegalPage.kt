package com.muyao.wanzaihyperos.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 法律文本页（用户协议 / 隐私政策共用）。
 *
 * 一页只做三件事：居中标题（由 [SecondaryScaffold] 提供）、逐节小标题 + 正文、
 * 顶部那层随滚动加深的纱。正文全部来自 [LegalText]，这里不写任何业务文案 ——
 * 两份文本的差别只在传入的 [sections]。
 *
 * 节标题用 `Type.title` 并打 `heading()` 语义标记：读屏时能按标题跳读，
 * 也让 `TypeRatchet` 那类"硬编码字号只许降"的守卫不必为这一页破例。
 */
@Composable
fun LegalPage(
    title: String,
    sections: List<LegalSection>,
    onBack: () -> Unit,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scroll = rememberScrollState()
    val veil by rememberScrollProgress(scroll)

    SecondaryScaffold(
        scrollProgress = veil,
        title = title,
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
            sections.forEachIndexed { index, section ->
                if (index > 0) Spacer(Modifier.height(18.dp))
                Text(
                    text = section.title,
                    fontSize = Type.title,
                    lineHeight = Type.title * 1.35f,
                    color = colors.onBackground,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Text(
                    text = section.body,
                    fontSize = Type.summary,
                    lineHeight = Type.summary * 1.6f,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                )
            }

            Text(
                text = "最后更新：2026-11-08",
                fontSize = Type.micro,
                lineHeight = Type.micro * 1.5f,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 22.dp, bottom = 32.dp),
            )
        }
    }
}
