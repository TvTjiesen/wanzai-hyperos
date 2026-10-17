package com.muyao.wanzaihyperos.ui.busyness

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import com.muyao.wanzaihyperos.data.portal.PortalBill
import com.muyao.wanzaihyperos.data.portal.PortalBillBucket
import com.muyao.wanzaihyperos.data.portal.PortalBillRepository
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「我的事務」原生頁（B3）—— 待辦 / 我的申請 / 我的已辦。
 *
 * ### 為什麼這頁值得做（而課表那頁擱置了）
 *
 * 首頁那個「待辦 1 / 申請 1 / 已辦 1」的區塊**只有數字**，
 * 點進去看不到「具體要辦什麼」。要辦事就得回門戶一層層點。
 * 這頁把它補上：**一眼看到要做什麼、什麼時候建的、什麼狀態**。
 *
 * ### 三個 tab 的資料源差別
 *
 * 三個 tab 打的是**三個不同接口**（見 [PortalBillBucket]），
 * 回應形狀高度重疊但有差異（已辦多 `handletime`、申請多 `billownername`/`url`）。
 * 切 tab 時倉庫會先作廢在途回應再清空列表 —— 否則會短暫顯示**上一個 tab 的內容**，
 * 那比空白更糟（使用者會以為「已辦裡有這些待辦」）。
 */
@Composable
fun BusynessPage(
    repository: PortalBillRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    initialBucket: PortalBillBucket = PortalBillBucket.TODO,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val bucket by repository.bucket.collectAsState()
    val pageState = state.toPageState()
    val bills = (state as? ListState.Content)?.items.orEmpty()
    val meta = (state as? ListState.Content)?.meta

    // 首次進入：若倉庫還是「首次載入」，就按指定的 tab 拉一次。
    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) {
            runCatching { repository.selectBucket(initialBucket) }
        }
    }

    ThemedScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                SmallTopAppBar(
                    title = "我的事务",
color = Color.Transparent,
                    subtitle = "来自学校门户",
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(
                                imageVector = MiuixIcons.ChevronBackward,
                                contentDescription = "返回",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { runCatching { repository.refresh() } } }) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = "重新加载",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                // 三個 tab。用枚舉的順序，標籤來自枚舉本身 ——
                // 這樣新增清單時只改枚舉，不會出現「tab 數與介面數不一致」。
                BucketTabs(
                    selected = bucket,
                    onSelect = { target -> scope.launch { runCatching { repository.selectBucket(target) } } },
                )
            }
        },
    ) { innerPadding ->
        if (bills.isEmpty()) {
            StatePlaceholder(
                state = pageState,
                modifier = Modifier.padding(innerPadding),
                // 空態也要說清「是哪個清單空」——三個 tab 的空態長得一樣，
                // 不寫的話使用者會懷疑是不是切錯了。
                detail = if (pageState == PageState.Empty) "「${bucket.label}」目前没有记录" else null,                onRetry = { scope.launch { runCatching { repository.refresh() } } },
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ListHeader(
                        bucket = bucket,
                        count = bills.size,
                        hasMore = meta?.hasMore == true,
                        isStale = pageState == PageState.Stale,
                    )
                }
                items(bills, key = { it.id }) { bill ->
                    BillCard(bill)
                }
                if (meta?.hasMore == true) {
                    item {
                        LoadMoreRow(
                            loading = meta.loadingMore,
                            onClick = { scope.launch { runCatching { repository.loadMore() } } },
                        )
                    }
                }
            }
        }
    }
}

/** 三個清單的切換器。 */
@Composable
private fun BucketTabs(selected: PortalBillBucket, onSelect: (PortalBillBucket) -> Unit) {
    val colors = MiuixTheme.colorScheme
    // 與消息頁同一套做法：用 `LazyRow` + `Card` 而不是 `TabRow`
    //（見 MessagesPage 的說明：選中態由外部單一來源控制，且標籤長時更可讀）。
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PortalBillBucket.entries.size) { index ->
            val item = PortalBillBucket.entries[index]
            val isSelected = item == selected
            Card(onClick = { onSelect(item) }) {
                Text(
                    text = item.label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (isSelected) colors.primary else colors.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }    }
}

/** 頂部說明：當前是哪個清單、共幾條。 */
@Composable
private fun ListHeader(
    bucket: PortalBillBucket,
    count: Int,
    hasMore: Boolean,
    isStale: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            text = "「${bucket.label}」共 $count 条" + if (hasMore) "（还有更多）" else "",
            style = MiuixTheme.textStyles.body1,
            color = colors.onBackground,
        )
        if (isStale) {
            Spacer(Modifier.height(2.dp))
            Text(
                // 與其它頁同一套說法：有舊資料但這次沒刷新成功，
                // 顯示內容 + 說明「可能不是最新」，**不清空成錯誤頁**。
                text = "这次没刷新成功，显示的是上一次的数据",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onBackgroundVariant,
            )
        }
    }
}

/** 一條事務。 */
@Composable
private fun BillCard(bill: PortalBill) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bill.name.ifBlank { "（无标题）" },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (bill.isRejected) {
                    Text(
                        text = "已驳回",
                        style = MiuixTheme.textStyles.footnote2,
                        // 用 error 色標「要留意」——但**不**說「失敗」，
                        // 因為被駁回在流程上是正常結果之一。
                        color = colors.error,
                    )
                }
            }

            if (bill.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = bill.description,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(6.dp))
            val lines = buildList {
                if (bill.ownerName.isNotBlank()) add("发起人：${bill.ownerName}")
                if (bill.createdAt.isNotBlank()) add("建立：${bill.createdAt}")
                // 已辦才有處理時間；有的話優先顯示它（使用者更關心「什麼時候辦完的」）。
                bill.handledAt?.takeIf { it.isNotBlank() }?.let { add("处理：$it") }            }
            if (lines.isNotEmpty()) {
                Text(
                    text = lines.joinToString("　"),
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // ⚠️ `bill.detailUrl` 有意**不顯示、不點擊**：
            //    那個地址可能帶一次性憑據（門戶的 url 常常是 SSO 跳轉鏈），
            //    顯示它等於把憑據印在螢幕上，而點擊它需要先確認目標域是否學校域。
            //    要做點擊得另立任務（要先按 host 驗證，與 A3 的 tempcode 同一條紅線）。
        }
    }
}

/** 「載入更多」。 */
@Composable
private fun LoadMoreRow(loading: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            Text(
                text = "正在加载…",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = "加载更多",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
