package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.muyao.wanzaihyperos.data.cjh52.Lecture
import com.muyao.wanzaihyperos.data.cjh52.LectureRepository
import com.muyao.wanzaihyperos.data.items
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.MarkedHeading
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **愛講座**（CJH52）：校內講座清單。**只讀**。
 *
 * ### 為什麼只讀
 *
 * 講座有「報名 / 取消報名」的寫操作接口
 *（`lecture_service/register_lecture` 等），本專案一貫不做寫操作
 *（§B4 充值、§B5 續借、§B8 §4 寫操作清單）。所以這一頁做到
 *「看清楚有什麼講座、還有沒有名額、我簽到了沒」為止 ——
 * 那正是學生挑講座要的信息，報名則由頁面底部的網頁版入口去做。
 *
 * ### ★ 名額與已報名分開顯示
 *
 * 實測一場講座是「名額 30、已報名 4」。把兩個合成一句「未滿」
 * 會讓使用者不知道自己排第幾、要不要早點報。所以顯示 `4 / 30`。
 *
 * ### 稿 `lectures.png` 的結構怎麼落
 *
 * 稿上只有一場講座（三行鍵值 + 一枚「去原站查看并报名」）。真實資料是多場，
 * 所以每一場是一條 [CjhRowItem]：講題 + 時間地點小字 + 右側報名狀態 + 講者/名額說明。
 * 那枚「去原站查看并报名」保留在列表下方 —— 它是**這一頁唯一的寫操作出口**，
 * 也是 §39 驗收要的「不新增自動報名」的具體做法。
 */
@Composable
fun LecturePage(
    repository: LectureRepository,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
    /** 稿 `lectures.png` 那枚「六项服务目录」的落点。为 null 时不画。 */
    onOpenDirectory: (() -> Unit)? = null,
) {
    val state by repository.state.collectAsState()
    val hasMore by repository.hasMore.collectAsState()
    val lectures = state.items
    val scope = rememberCoroutineScope()
    val pageState = state.toPageState()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.refresh() }
    }

    // ★ 用共用的 [SecondaryScaffold]，不要自己拼 Scaffold。
    //   真機上第一版自己用 `BackRow` + `AppList`，結果有兩個問題：
    //   ① 底色透明 → 下面的首頁宮格透上來；
    //   ② 沒有頂部安全區 → 返回鍵被狀態列蓋住。
    //   那個外殼現在抽到設計層了（同一個坑踩三次之後），改動只需一行。
    val listState = rememberLazyListState()
    val veilProgress by rememberScrollProgress(listState)
    val refresh: () -> Unit = { scope.launch { runCatching { repository.refresh() } } }

    SecondaryScaffold(
        scrollProgress = veilProgress,
        title = "爱讲座",
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        // ★ 只讓 top：本頁下方沒有自家的底欄（底欄由宿主 Scaffold 畫並已讓過），
        //   再讓一次 bottom 會在列表末尾多出一整條空白。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                // 稿 `lectures.png`：頁頭三行在列表最上面，空態也在它下面。
                item(key = "lecture-hero") {
                    CjhHero(
                        eyebrow = "CJH52 / 学生服务",
                        title = "爱讲座",
                        sub = "独立于教务与学在城院的原生阅读入口。",
                    )
                }
                item(key = "lecture-stale") { CjhStaleNotice(pageState) }

                if (lectures.isEmpty()) {
                    item(key = "lecture-state") {
                        StatePlaceholder(
                            state = pageState,
                            detail = if (pageState == PageState.Empty) "目前没有讲座" else null,
                            onAction = onOpenWeb,
                            actionLabel = "改用网页版",
                        )
                    }
                } else {
                    item(key = "lecture-heading") {
                        MarkedHeading(
                            text = "讲座",
                            icon = MarkIcon.Library,
                            trailing = {
                                Text(
                                    text = "共 ${lectures.size} 场",
                                    // 会变的计数走等宽数字，数字宽度一变整行会左右跳（#41）。
                                    style = TextStyle(fontFeatureSettings = Type.tabular),
                                    fontSize = Type.caption,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                        )
                    }
                    items(lectures, key = { it.id }) { lecture -> LectureRow(lecture) }

                    if (hasMore) {
                        item(key = "lecture-more") {
                            CjhChip(
                                label = "加载更多",
                                onClick = { scope.launch { runCatching { repository.loadMore() } } },
                                modifier = Modifier.padding(top = Dims.tight),
                            )
                        }
                    }

                    // 稿上這一頁那枚「去原站查看并报名」的對應物。
                    item(key = "lecture-register") {
                        CjhChip(
                            label = "去原站查看并报名",
                            onClick = onOpenWeb,
                            modifier = Modifier.padding(top = Dims.tight),
                        )
                    }
                    item(key = "lecture-note") {
                        CjhNotice(
                            text = "报名与取消报名是写操作，这里不做 —— 点上方入口去网页版报名。",
                        )
                    }
                }

                item(key = "lecture-origin") {
                    CjhOriginSection(
                        webLabel = "爱讲座完整网页版",
                        onOpenWeb = onOpenWeb,
                        onRefresh = refresh,
                        onOpenDirectory = onOpenDirectory,
                    )
                }
                item(key = "lecture-source") { CjhSourceCaption("CJH52 接口") }
            }
        }
    }
}

/**
 * 一場講座：講題 + 時間·地點 + 報名狀態（右側）。
 *
 * ### 狀態色只分兩檔
 *
 * 「報名中」用主色、「已滿/已結束/已簽到」用次要色 —— 這不是裝飾，
 * 是「現在還能不能去」的開關。至於具體是哪一種，右邊的字自己會說。
 *
 * ### 說明欄的組成順序照實用性排
 *
 * 講者（決定要不要去）→ 單位 → 名額 → 報名截止（決定還來不來得及）。
 * 每一項缺失就整段不出現，不留空括號。
 */
@Composable
private fun LectureRow(lecture: Lecture) {
    val colors = MiuixTheme.colorScheme
    val speaker = listOfNotNull(
        lecture.speaker.takeIf { it.isNotBlank() },
        lecture.speakerJob.takeIf { it.isNotBlank() }?.let { "（$it）" },
    ).joinToString("").takeIf { it.isNotBlank() }

    CjhRowItem(
        title = lecture.title.ifBlank { "（无标题讲座）" },
        icon = MarkIcon.Library,
        meta = listOfNotNull(
            lecture.startLocal.takeIf { it.isNotBlank() },
            lecture.address.takeIf { it.isNotBlank() },
        ).joinToString(" / ").ifBlank { null },
        body = listOfNotNull(
            speaker,
            lecture.unit.takeIf { it.isNotBlank() },
            lecture.capacityLabel,
            lecture.deadlineLocal.takeIf { it.isNotBlank() }?.let { "报名截止 $it" },
        ).joinToString("　"),
        bodyMaxLines = 4,
        trailing = lecture.statusLabel,
        trailingColor = if (lecture.canRegister) colors.primary else colors.onSurfaceVariantSummary,
    )
}