package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.cjh52.Lecture
import com.muyao.wanzaihyperos.data.cjh52.LectureRepository
import com.muyao.wanzaihyperos.data.items
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.BackRow
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.infoItems
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
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
 * 那正是學生挑講座要的資訊，報名則由頁面底部的網頁版入口去做。
 *
 * ### ★ 名額與已報名分開顯示
 *
 * 實測一場講座是「名額 30、已報名 4」。把兩個合成一句「未滿」
 * 會讓使用者不知道自己排第幾、要不要早點報。所以顯示 `4 / 30`。
 *
 * ### 美化預留
 *
 * 全頁只用 `AppList` / `InfoItem` / `HintText` / `ActionRow` / `Dims`，
 * **沒有尺寸字面值**。
 */
@Composable
fun LecturePage(
    repository: LectureRepository,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    val hasMore by repository.hasMore.collectAsState()
    val items = state.items
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.refresh() }
    }

    // ★ 用 Scaffold + SmallTopAppBar，與其他 cjh52 原生頁（考試/通知/班級/成長）一致。
    //   真機上第一版自己用 `BackRow` + `AppList`，結果有兩個問題：
    //   ① 底色透明 → 下面的首頁宮格透上來；
    //   ② 沒有頂部安全區 → 返回鍵被狀態列蓋住。
    //   Scaffold 一次解決兩者（它會處理 insets），而且與同層頁面長得一樣。
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "爱讲座",
                subtitle = "來自場景管理（cjh52）",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.size(Dims.iconNav),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { runCatching { repository.refresh() } } }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(Dims.iconNav),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding())

        if (items.isEmpty()) {
            StatePlaceholder(
                state = state.toPageState(),
                modifier = contentModifier,
                onAction = onOpenWeb,
                actionLabel = "改用网页版",
            )
            return@Scaffold
        }

        AppList(modifier = contentModifier) {
            item { SmallTitle(text = "讲座（${items.size}）") }

            infoItems(items, key = { it.id }) { lecture ->
                LectureItem(lecture)
            }

            if (hasMore) {
                item {
                    Button(
                        onClick = { scope.launch { runCatching { repository.loadMore() } } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("加载更多") }
                }
            }

            item {
                HintText("说明：报名与取消报名是写操作，这里不做 —— 点下方入口去网页版报名。")
            }
            item { ActionRow(title = "去网页版报名", action = "打开", onClick = onOpenWeb) }
        }
    }
}

/** 一場講座：講題 + 講者／單位／時間／地點 + 名額 + 狀態。 */
@Composable
private fun LectureItem(lecture: Lecture) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dims.tight),
    ) {
        InfoItem(
            title = lecture.title,
            subtitle = listOfNotNull(
                lecture.startLocal.takeIf { it.isNotBlank() },
                lecture.address.takeIf { it.isNotBlank() },
            ).joinToString("　").ifBlank { null },
            trailing = lecture.statusLabel,
            trailingColor = if (lecture.canRegister) colors.primary else colors.onSurfaceVariantSummary,
            // 講者與單位放進說明欄 —— 它們對「要不要去聽」很重要。
            body = listOfNotNull(
                listOfNotNull(
                    lecture.speaker.takeIf { it.isNotBlank() },
                    lecture.speakerJob.takeIf { it.isNotBlank() }?.let { "（$it）" },
                ).joinToString("").takeIf { it.isNotBlank() },
                lecture.unit.takeIf { it.isNotBlank() },
                lecture.capacityLabel,
                lecture.deadlineLocal.takeIf { it.isNotBlank() }?.let { "报名截止 $it" },
            ).joinToString("　"),
            bodyMaxLines = 4,
        )
    }
}
