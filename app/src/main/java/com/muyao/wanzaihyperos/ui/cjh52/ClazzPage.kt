package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.cjh52.ClazzInfo
import com.muyao.wanzaihyperos.data.cjh52.ClazzRepository
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import kotlinx.coroutines.launch

/**
 * 我的班級（原生化）。
 *
 * 稿 `classInfo.png`：頁頭三行 → 一組鍵值行（班級 / 院系·年級 / …）→「原站与状态」。
 * 這是「看一次就夠」的頁面，不需要列表。
 *
 * ### 為什麼沒有卡片外框
 *
 * 稿上這一組資料是 `class="surface"` 的容器，而 `style.css` / `polish.css` 裡
 * **沒有 `.surface` 規則** —— 也就是說稿子本身這塊就沒有邊框，只有行間細分割線。
 * 以前這裡套了一張 [com.muyao.wanzaihyperos.ui.design.AppCard]，
 * 對照截圖會發現整頁的節奏都變了（卡片陰影把「一組鍵值」讀成了「一張信息卡」）。
 *
 * ### 缺欄位寫「—」而不是留空
 *
 * 班級資料是學校接口回來的，人數/班主任/輔導員經常有空。留空會讓右側值列
 * 看起來像渲染失敗；寫 0 更是撒謊。所以統一走 [orDash]。
 */
@Composable
fun ClazzPage(
    repository: ClazzRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
    ready: Boolean = true,
    /**
     * 稿 `classInfo.png` 那枚「六项服务目录」的落點 —— 回 CJH52 服務目錄頁。
     *
     * 為 null 時不畫那一枚（獨立入口打開、棧裡沒有目錄頁時）。
     */
    onOpenDirectory: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val clazz = (state as? ListState.Content)?.items?.firstOrNull()

    LaunchedEffect(ready) {
        if (ready && (state is ListState.FirstLoad || state is ListState.SessionExpired)) {
            repository.refresh()
        }
    }

    val listState = rememberLazyListState()
    val veilProgress by rememberScrollProgress(listState)
    val refresh: () -> Unit = { scope.launch { runCatching { repository.refresh() } } }

    SecondaryScaffold(
        scrollProgress = veilProgress,
        title = "我的班级",
        onBack = onExit,
        modifier = modifier,
    ) { innerPadding ->
        // ★ 頂欄不掛刷新鍵：稿 `classInfo.png` 的頂欄只有「返回」與「放大鏡」，
        //   刷新是「原站與狀態」那排芯片裡的一枚。以前這裡掛了頂欄刷新，
        //   同一頁就有兩個刷新入口，兩邊狀態還各轉各的。
        //
        //   稿 `classInfo.png`：這一頁的順序是「頁頭三行 → 班級資料 → 原站與狀態」。
        //   所以 hero 與芯片行**不論有沒有資料都在**（它們是這一頁的骨架，
        //   不是「有資料才長出來」的裝飾），只有中間那組鍵值行隨狀態變。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "clazz-hero") {
                    CjhHero(
                        eyebrow = "CJH52 / 学生服务",
                        title = "我的班级",
                        sub = "独立于教务与学在城院的原生阅读入口。",
                    )
                }
                item(key = "clazz-stale") { CjhStaleNotice(pageState) }
                if (clazz == null) {
                    item(key = "clazz-state") {
                        StatePlaceholder(
                            state = pageState,
                            onAction = onFallbackToWeb,
                            actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
                        )
                    }
                } else {
                    item(key = "clazz-fields") { ClazzFields(clazz) }
                }
                item(key = "clazz-origin") {
                    CjhOriginSection(
                        webLabel = "我的班级完整网页版",
                        onOpenWeb = onFallbackToWeb,
                        onRefresh = refresh,
                        onOpenDirectory = onOpenDirectory,
                    )
                }
                item(key = "clazz-source") { CjhSourceCaption("CJH52 接口") }
            }
        }
    }
}

/**
 * 班級資料那一組鍵值行。
 *
 * 欄位是接口真有的六項（[ClazzInfo]），標籤沿用稿上的「班級 / 院系·年級」兩個口徑，
 * 其餘按實際欄位名排 —— 稿上第三行「班級資料 / 公開樣例；不展示真實成員名單」
 * 是原型自己的樣例聲明，我們拿的是真數據，照抄那行反而是假的，所以換成真欄位。
 */
@Composable
private fun ClazzFields(clazz: ClazzInfo) {
    CjhKvRow(label = "班级", value = orDash(clazz.name))
    CjhKvRow(
        label = "院系 / 年级",
        value = orDash(
            listOf(clazz.collegeName, clazz.grade)
                .filter { it.isNotBlank() }
                .joinToString(" / "),
        ),
    )
    CjhKvRow(label = "专业", value = orDash(clazz.specialty))
    CjhKvRow(
        label = "人数",
        value = clazz.headcount?.let { "$it 人" } ?: "—",
    )
    CjhKvRow(label = "班主任", value = orDash(clazz.teacherName))
    CjhKvRow(label = "辅导员", value = orDash(clazz.counselorName))
}

/** 空白即未知 —— 統一寫全角破折號，不寫 0、不寫「暂无」。 */
private fun orDash(value: String): String = value.ifBlank { "—" }
