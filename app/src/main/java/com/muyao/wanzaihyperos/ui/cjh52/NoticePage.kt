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
import com.muyao.wanzaihyperos.data.cjh52.Cjh52Notice
import com.muyao.wanzaihyperos.data.cjh52.NoticeRepository
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.MarkedHeading
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 日常通知（B3）。
 *
 * ### 為什麼做它
 *
 * 它是**「要我讀的公告」**：學校/學院發的、按你的院系年級專業篩過的。
 * 以前只能在門戶裡一層層點進去，現在原生一頁看完。
 *
 * ### 契約（真機實測）
 *
 * ```
 * POST courier/notice_service/find_all_for_reader
 *   { scope: {school, college[], grade[], specialty[], role:"stu"}, pageSize: 10, pageNum: 0 }
 *   → data.data[] = { id, title, username, detail, createTime, pubTime, level }
 * ```
 * `scope` 的四個值從 `get_user_by_token` 推出來 —— 不是硬編碼院系。
 *
 * ### 與稿 `cjhNotices.png` 的關係
 *
 * 稿上這一頁只有**一行**：標題 + 小字「11月05日 16:00 · CJH52 日常通知」+ `›`，
 * 行與行之間一道細分割線，**沒有分區標題、沒有卡片外框**。
 * 所以這裡不再有「通知 · 共 N 條」那行 —— 稿上沒有它，
 * 而「有幾條」在一屏能滾動的清單裡本來就是多餘的數字。
 */
@Composable
fun NoticePage(
    repository: NoticeRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
    ready: Boolean = true,
    /** 稿 `cjhNotices.png` 那枚「六项服务目录」的落点。为 null 时不画。 */
    onOpenDirectory: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val notices = (state as? ListState.Content)?.items.orEmpty()

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
        title = "日常通知",
        onBack = onExit,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "notice-hero") {
                    CjhHero(
                        eyebrow = "CJH52 / 学生服务",
                        title = "日常通知",
                        sub = "独立于教务与学在城院的原生阅读入口。",
                    )
                }
                item(key = "notice-stale") { CjhStaleNotice(pageState) }
                if (notices.isEmpty()) {
                    item(key = "notice-state") {
                        StatePlaceholder(
                            state = pageState,
                            detail = if (pageState == PageState.Empty) "目前没有通知" else null,
                            onAction = onFallbackToWeb,
                            actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
                        )
                    }
                } else {
                    item(key = "notice-heading") {
                        MarkedHeading(
                            text = "通知",
                            icon = MarkIcon.Message,
                            trailing = {
                                Text(
                                    text = "共 ${notices.size} 条" +
                                        if (pageState == PageState.Stale) "（上次的）" else "",
                                    // 会变的计数走等宽数字，数字宽度一变整行会左右跳（#41）。
                                    style = TextStyle(fontFeatureSettings = Type.tabular),
                                    fontSize = Type.caption,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            },
                        )
                    }
                    items(notices, key = { it.id }) { notice -> NoticeRow(notice, onFallbackToWeb) }
                }
                item(key = "notice-origin") {
                    CjhOriginSection(
                        webLabel = "日常通知完整网页版",
                        onOpenWeb = onFallbackToWeb,
                        onRefresh = refresh,
                        onOpenDirectory = onOpenDirectory,
                    )
                }
                item(key = "notice-source") { CjhSourceCaption("CJH52 接口") }
            }
        }
    }
}

/**
 * 一條通知。稿 `cjhNotices.png` 的行：標題 + 小字 + `›`。
 *
 * ### 小字為什麼是「時間 · 發佈方」
 *
 * 稿上是「11月05日 16:00 · CJH52 日常通知」—— 時間 + 來源。
 * 我們的來源是接口回的真發佈方（學院/教務處…），空的時候才退回「CJH52 日常通知」。
 * 時間取 [Cjh52Notice.sortKey]（發布時間優先、退回創建時間），
 * **null 或 ≤0 時整段不畫** —— 1970 年那條假時間比沒有時間更糟。
 *
 * ### 為什麼正文只給兩行
 *
 * `detail` 是接口回來的全文 HTML 去標籤後的正文。整段塞進列表行會讓一條通知
 * 佔掉一屏，而「讀全文」的正路是原站。這裡給兩行讓使用者能判斷要不要點。
 */
@Composable
private fun NoticeRow(notice: Cjh52Notice, onOpenWeb: (() -> Unit)?) {
    val meta = buildList {
        formatMillis(notice.sortKey)?.let(::add)
        add(notice.publisher.ifBlank { "CJH52 日常通知" })
        notice.level?.takeIf { it.isNotBlank() && it != "普通" }?.let(::add)
    }.joinToString(" · ")
    CjhRowItem(
        title = notice.title.ifBlank { "（无标题）" },
        icon = MarkIcon.Message,
        meta = meta,
        body = notice.detail.takeIf { it.isNotBlank() },
        bodyMaxLines = 2,
        arrow = onOpenWeb != null,
        onClick = onOpenWeb,
    )
}

/**
 * 毫秒 → 稿上的日期寫法（`11月05日 16:00`）。
 *
 * null 或 ≤0 一律回 null：接口沒給時間時畫 `1970-01-01` 等於在說謊。
 */
private fun formatMillis(millis: Long?): String? {
    if (millis == null || millis <= 0L) return null
    return SimpleDateFormat("M月dd日 HH:mm", Locale.CHINA).format(Date(millis))
}