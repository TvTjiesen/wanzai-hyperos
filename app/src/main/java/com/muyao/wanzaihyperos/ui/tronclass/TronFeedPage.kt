package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.TronFeedRepository
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.infoItems
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * 學在城院「**課程動態**」。
 *
 * ### 這一頁是什麼
 *
 * 「最近發生了什麼」：公告 + 待交作業 + 我這學期的課。
 * 它是**合成視圖**（見 [TronFeedRepository] 的註釋）——
 * 學在城院沒有一個叫「動態」的接口，這一頁是把已有資料按意義排在一起。
 *
 * ### 為什麼用 [AppList] 而不是直接 LazyColumn
 *
 * 那個是新的版面框架：它一次套好左右的 `screenH`、上下的 `screenV`、
 * 卡片之間的 `cardGap`。所以這一頁**不需要也不可能**寫錯邊距 ——
 * 這也是為後面「整體美化」準備的：改一處，全 App 一起變。
 */
@Composable
fun TronFeedPage(
    repository: TronFeedRepository,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val feed = (state as? ListState.Content)?.items?.firstOrNull()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    if (feed == null || feed.isEmpty) {
        StatePlaceholder(
            state = pageState,
            modifier = modifier,
            detail = if (pageState == PageState.Empty) "目前没有动态" else null,
            onAction = onFallbackToWeb,
            actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
        )
        return
    }

    AppList(modifier = modifier) {
        if (feed.bulletins.isNotEmpty()) {
            item { SmallTitle(text = "公告（${feed.bulletins.size}）") }
            infoItems(feed.bulletins, key = { it.id }) { b ->
                InfoItem(
                    title = b.title,
                    subtitle = listOfNotNull(
                        b.courseName.takeIf { it.isNotBlank() },
                        b.authorName.takeIf { it.isNotBlank() },
                        b.createdAtLocal,
                    ).joinToString("　"),
                    body = b.content.takeIf { it.isNotBlank() },
                )
            }
        }

        if (feed.todos.isNotEmpty()) {
            item { SmallTitle(text = "待交作业（${feed.todos.size}）") }
            infoItems(feed.todos, key = { it.id }) { t ->
                InfoItem(
                    title = t.title,
                    subtitle = listOfNotNull(
                        t.courseName.takeIf { it.isNotBlank() },
                        t.localTimeText.takeIf { it.isNotBlank() },
                    ).joinToString("　"),
                    trailing = if (t.locked) "已锁定" else null,
                )
            }
        }

        item {
            HintText(
                "说明：「动态」是把公告、待交作业与我的课合在一起的视图 —— " +
                    "学在城院本身没有单独的「动态」接口，所以这里不会比原站更全。",
            )
        }
    }
}
