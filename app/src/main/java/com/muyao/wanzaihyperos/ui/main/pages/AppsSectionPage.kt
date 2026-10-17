package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.portal.toUiApp
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **應用板塊**：把「已重構（原生）」與「未重構（網頁）」**分開放**。
 *
 * ## 為什麼要分開（業主要求，也是真的有用）
 *
 * 這兩類的**體驗差一個檔次**：
 *
 * | | 已重構 | 未重構 |
 * |---|---|---|
 * | 打開方式 | 原生頁面（即時、可搜尋、跟隨主題） | 內嵌網頁（慢、可能白屏、樣式不受控） |
 * | 斷網 | 有明確錯誤文案 | 可能只是一片白 |
 *
 * 混在一起的話，使用者點下去才知道「這個又要等網頁」。
 * 分開之後**期待是明確的**。
 *
 * ## 怎麼判定「已重構」
 *
 * 用 [AppOpenStrategy.decide] 的結果 —— 那是**唯一**的路由真相來源，
 * 不在這裡另寫一份名單。否則新增一個原生頁卻忘了更新名單，
 * 它就會被錯歸到「未重構」。
 */
@Composable
fun AppsSectionPage(
    graph: AppGraph,
    onOpenApp: (PortalApp) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val catalogState by graph.catalog.state.collectAsState()

    LaunchedEffect(Unit) {
        runCatching { graph.catalog.refresh() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            top = 56.dp,
            bottom = 120.dp,   // 留出玻璃底欄的高度（讓內容能滾到它下面，玻璃才折射得到東西）
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        item(key = "title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "應用",
                    fontSize = Type.largeTitle,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                    lineHeight = Type.largeTitle * Type.LINE_HEIGHT_TITLE,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "搜尋",
                    fontSize = Type.body,
                    color = colors.primary,
                    modifier = Modifier.clickable { onOpenSearch() },
                )
            }
        }

        when (val s = catalogState) {
            is ListState.FirstLoad -> item(key = "loading") {
                Text(
                    "正在取得應用清單…",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(Dims.cardPadding),
                )
            }

            is ListState.Failed -> item(key = "failed") {
                AppCard {
                    Column(Modifier.padding(Dims.cardPadding)) {
                        Text("取不到應用清單", fontSize = Type.body, color = colors.error)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.error.message.orEmpty(),
                            fontSize = Type.caption,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            is ListState.Content -> {
                val groups = s.items
                // 門戶給的是 `PortalAppEntry`（資料層模型），而界面與路由用的是
                // `PortalApp`（展示模型）。用既有的 `toUiApp()` 轉，**不自己造一份** ——
                // 兩份映射遲早會漂移（一邊加了 subtitle、另一邊沒加）。
                val apps = groups.flatMap { g -> g.apps.map { it.toUiApp() } }

                // ★ 用 AppOpenStrategy 的判定結果分類，不另寫名單。
                val (native, web) = apps.partition { app ->
                    com.muyao.wanzaihyperos.data.AppOpenStrategy.decide(app).let { plan ->
                        plan is com.muyao.wanzaihyperos.data.AppOpenPlan.NativeTronclass ||
                            plan is com.muyao.wanzaihyperos.data.AppOpenPlan.NativeCourses ||
                            plan is com.muyao.wanzaihyperos.data.AppOpenPlan.NativeFeature ||
                            plan is com.muyao.wanzaihyperos.data.AppOpenPlan.JwNative ||
                            plan is com.muyao.wanzaihyperos.data.AppOpenPlan.ZyNative
                    }
                }

                item(key = "native-header") {
                    SectionLabel("已重構（原生）", native.size)
                }
                if (native.isEmpty()) {
                    item(key = "native-empty") { SmallHint("還沒有已重構的功能。") }
                } else {
                    native.forEach { app ->
                        item(key = "n-${app.id}") { AppRow(app) { onOpenApp(app) } }
                    }
                }

                item(key = "web-header") {
                    SectionLabel("未重構（網頁）", web.size)
                }
                if (web.isEmpty()) {
                    item(key = "web-empty") { SmallHint("沒有網頁類應用。") }
                } else {
                    web.forEach { app ->
                        item(key = "w-${app.id}") { AppRow(app) { onOpenApp(app) } }
                    }
                }
            }

            else -> item(key = "none") { SmallHint("還沒有應用清單。") }
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = Type.title,
            fontWeight = Type.weightTitle,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            fontSize = Type.caption,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun SmallHint(text: String) {
    Text(
        text = text,
        fontSize = Type.secondary,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(Dims.cardPadding),
    )
}

@Composable
private fun AppRow(app: PortalApp, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dims.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 圖示底：用門戶下發的顏色（`effectiveTint`）。
            // **不下載 app_img** —— 幾十個外鏈圖 = 幾十次請求，會撞限流。
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    // `Modifier.background` 是**擴展函式**，必須接在 Modifier 鏈上。
                    // 寫成 `.then(background(...))` 會因為沒有 receiver 而編譯失敗。
                    .background(app.effectiveTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = app.icon,
                    contentDescription = null,
                    tint = app.effectiveTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    fontSize = Type.body,
                    color = colors.onBackground,
                    maxLines = 1,
                )
                if (!app.subtitle.isNullOrBlank()) {
                    Text(
                        text = app.subtitle.orEmpty(),
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
