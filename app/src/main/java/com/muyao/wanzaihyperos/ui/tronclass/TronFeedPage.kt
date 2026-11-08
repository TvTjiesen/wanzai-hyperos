package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.TronFeedRepository
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.infoItems
import com.muyao.wanzaihyperos.ui.design.InfoItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 學在城院「**課程動態**」。
 *
 * ### 這一頁是什麼
 *
 * 「最近發生了什麼」：公告 + 待交作業 + 我這學期的課。
 * 它是**合成視圖**（見 [TronFeedRepository] 的註釋）——
 * 學在城院沒有一個叫「動態」的接口，這一頁是把已有資料按意義排在一起。
 *
 * ### ★ 為什麼公告要**按課程分組**
 *
 * 稿上（`screens/tron.png` 的「動態」態、`prototype/secondary.js` 的 activity 分頁）
 * 一行是「**课程公告 · 下次课准备** ／ 交互设计基础 · 11月05日」——
 * 也就是說一條公告在稿上**帶著課程名出現**，而不是一張不分課程的流水帳。
 *
 * 不分組的代價是真的：`/api/org-bulletins` 一次回來全校公告與各課公告，
 * 三年級的學生看「下次课准备」根本不知道是哪門課要準備。
 * 所以這裡按 `courseName` 分（空白的歸到「全校公告」），
 * 每組標題右邊寫這一組有幾條 —— 和稿上 `section(title, meta)` 的排法一致。
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

    // 公告按課程分組：空白課程名的（全校公告）排最後 —— 各課的更具體，該先看到。
    val bulletinGroups = feed.bulletins
        .groupBy { it.courseName.trim().ifBlank { "全校公告" } }
        .toList()
        .sortedBy { (name, _) -> if (name == "全校公告") 1 else 0 }

    AppList(modifier = modifier) {
        // ── 課堂動態：稿上這一組叫「课堂动态／公告与待交」 ─────────────────
        item(key = "feed-head") {
            TronSection(
                text = "课堂动态",
                icon = MarkIcon.Message,
                trailing = {
                    Text(
                        text = "公告与待交",
                        fontSize = Type.caption,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
            )
        }

        bulletinGroups.forEach { (courseName, list) ->
            item(key = "bulletin-head-$courseName") {
                TronSection(
                    text = "课程公告 · $courseName",
                    icon = MarkIcon.Pin,
                    trailing = {
                        Text(
                            text = "${list.size} 条",
                            fontSize = Type.caption,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            // 分组条数会随课程数变化，走等宽数字，否则右端会左右跳（#41）。
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Type.tabular),
                        )
                    },
                )
            }
            // 一條公告一張卡：標題 + 課程/作者/時間 + 正文（最多 6 行）。
            // 稿上這一行右邊還有「›」，但公告在這一頁**點不開**（沒有公告詳情接口），
            // 所以不畫那個箭頭 —— 畫了就是騙人點。
            infoItems(list, key = { it.id }) { b ->
                InfoItem(
                    title = b.title.ifBlank { "（无标题公告）" },
                    subtitle = listOfNotNull(
                        b.authorName.takeIf { it.isNotBlank() },
                        b.createdAtLocal,
                    ).joinToString("　"),
                    // 未讀與已讀分得出來，且不只靠顏色：文字寫出來。
                    trailing = when (b.isRead) {
                        false -> "未读"
                        true -> "已读"
                        null -> null
                    },
                    trailingColor = if (b.isRead == false) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    body = b.plainText.takeIf { it.isNotBlank() },
                    bodyMaxLines = 6,
                )
            }
        }

        if (feed.todos.isNotEmpty()) {
            item(key = "todo-head") {
                TronSection(
                    text = "待交作业 · ${feed.todos.size}",
                    icon = MarkIcon.Todo,
                    trailing = {
                        Text(
                            text = "截止优先",
                            fontSize = Type.caption,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                )
            }
            // 有截止時間的排前面（近的在前），沒有時間的排後面 ——
            // 不給它們編一個時間，只是不讓它們插隊。
            val ordered = feed.todos.sortedWith(
                compareBy(nullsLast()) { t: com.muyao.wanzaihyperos.data.tronclass.TronTodo -> t.endTimeMillis },
            )
            infoItems(ordered, key = { it.id }) { t ->
                InfoItem(
                    title = t.title.ifBlank { "（无标题作业）" },
                    subtitle = listOfNotNull(
                        t.courseName.takeIf { it.isNotBlank() },
                        t.localTimeText.takeIf { it.isNotBlank() }?.let { "$it 截止" },
                    ).joinToString("　"),
                    trailing = if (t.locked) "已锁定" else null,
                )
            }
        }

        // 稿上的「我的课」在動態頁也露一截（`feed.courses` 這一塊以前完全沒被畫出來）。
        if (feed.courses.isNotEmpty()) {
            item(key = "course-head") {
                TronSection(
                    text = "我修的课 · ${feed.courses.size}",
                    icon = MarkIcon.Course,
                )
            }
            infoItems(feed.courses, key = { it.id }) { c ->
                InfoItem(
                    title = c.name.ifBlank { "（未命名课程）" },
                    subtitle = c.subtitle.takeIf { it.isNotBlank() },
                )
            }
        }

        item(key = "feed-note") {
            HintText(
                "说明：“动态”是把公告、待交作业与我的课合在一起的视图 —— " +
                    "学在城院本身没有单独的“动态”接口，所以这里不会比原站更全。",
            )
        }
    }
}