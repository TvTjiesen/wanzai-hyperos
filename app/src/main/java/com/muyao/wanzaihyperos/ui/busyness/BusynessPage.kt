package com.muyao.wanzaihyperos.ui.busyness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.data.portal.PortalBill
import com.muyao.wanzaihyperos.data.portal.PortalBillBucket
import com.muyao.wanzaihyperos.data.portal.PortalBillRepository
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Mark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「门户我的事务」原生页（B3）—— 待办 / 我的申请 / 我的已办。
 *
 * ### 为什么这页值得做（而课表那页搁置了）
 *
 * 首页那个「待办 1 / 申请 1 / 已办 1」的区块**只有数字**，
 * 点进去看不到「具体要办什么」。要办事就得回门户一层层点。
 * 这页把它补上：**一眼看到要做什么、什么时候建的、什么状态**。
 *
 * ### 三个 tab 的資料源差别
 *
 * 三个 tab 打的是**三个不同接口**（见 [PortalBillBucket]），
 * 回应形状高度重叠但有差异（已办多 `handletime`、申请多 `billownername`/`url`）。
 * 切 tab 时仓库会先作废在途回应再清空列表 —— 否则会短暂显示**上一个 tab 的内容**，
 * 那比空白更糟（用户会以为「已办里有这些待办」）。
 *
 * ### ★ 搜索框是**本地筛**，不是接口搜索
 *
 * [PortalBillRepository] 只有 refresh / selectBucket / loadMore 三个动作，
 * 没有把关键词传给服务端的能力。所以这个搜索框只筛「已经取到手的这几条」，
 * 界面上必须说清这一点（见下面那行 ×说明），不能让它看起来像全库搜索。
 * 要真做服务端搜索，得先在 data/portal 里加接口参数 —— 不在本页的写权限内。
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
    val meta = state.metaOrNull

    // 首次进入：若仓库还是「首次载入」，就按指定的 tab 拉一次。
    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) {
            runCatching { repository.selectBucket(initialBucket) }
        }
    }

    // 输入框里正在打的字 vs 已提交的关键词 —— 两者分开，
    // 否则每敲一个字都会重排列表，长列表上会跳。
    var draft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val shown = remember(bills, query, bucket) { filterBills(bills, query, bucket) }

    val listState = rememberLazyListState()
    val veilProgress by rememberScrollProgress(listState)

    val refresh = { scope.launch { runCatching { repository.refresh() } } }

    SecondaryScaffold(
        title = "门户我的事务",
        subtitle = "待办、我的申请与我的已办，各自完整保留。",
        onBack = onExit,
        scrollProgress = veilProgress,
        onRefresh = { refresh() },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "bz-hero") {
                    SecHero(
                        eyebrow = "PORTAL / 我的事务",
                        title = "把事情办妥",
                        sub = "待办、我的申请与我的已办，各自完整保留。",
                    )
                }
                item(key = "bz-tabs") {
                    BucketSegmented(
                        selected = bucket,
                        onSelect = { target ->
                            // 切桶同时把本地筛清掉：上一个桶的关键词在新桶里没有意义。
                            draft = ""
                            query = ""
                            scope.launch { runCatching { repository.selectBucket(target) } }
                        },
                    )
                }
                item(key = "bz-notice") {
                    SecNotice("事务搜索与办理入口按文档保留；现有 App 原生接线未证齐全。")
                }

                // 只有内容态才给搜索框：加载中/失败时空列表上摆一个搜索框，
                // 用户会以为是「搜不到」而不是「没取到」。
                if (pageState == PageState.Content || pageState == PageState.Stale) {
                    item(key = "bz-search") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "搜索当前事务桶",
                                fontSize = Type.caption,
                                lineHeight = Type.lineCaption,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(bottom = Dims.tight),
                            )
                            TextField(
                                value = draft,
                                onValueChange = { draft = it },
                                label = "名称或状态",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(Dims.tight))
                            Row(horizontalArrangement = Arrangement.spacedBy(Dims.tight)) {
                                Button(
                                    onClick = { query = draft.trim() },
                                    modifier = Modifier.weight(1f),
                                ) { Text("搜索") }
                                TextButton(
                                    text = "清空搜索",
                                    onClick = {
                                        draft = ""
                                        query = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            HintText("搜索只筛已经取到的这几条；接口没有关键词参数，所以它不等于全库搜索。")
                        }
                    }
                }

                if (shown.isEmpty()) {
                    // 空 / 错 / 加载 三态都画成列表里的一项，而不是盖住整页 ——
                    // 页头与分段控件必须一直在，否则用户不知道自己在哪个桶里。
                    item(key = "bz-empty") {
                        when {
                            pageState == PageState.Content || pageState == PageState.Stale ->
                                HintText("当前桶没有匹配事务。换个关键词，或切换其他事务桶。")
                            else -> StatePlaceholder(
                                state = pageState,
                                modifier = Modifier.fillMaxWidth(),
                                detail = if (pageState == PageState.Empty) "“${bucketLabel(bucket)}”目前没有记录" else null,
                                onRetry = { refresh() },
                            )
                        }
                    }
                } else {
                    item(key = "bz-section") {
                        SecSectionHead(
                            title = if (query.isNotBlank()) "搜索结果" else "事务记录",
                            // 稿上的口径：「共 N 条（还有更多）」—— N 是**已经取到的**条数，
                            // 还有更多时如实加一句，不把「取到几条」说成「一共几条」。
                            meta = "共 ${shown.size} 条" + if (meta?.hasMore == true) "（还有更多）" else "",
                        )
                    }
                    items(shown, key = { it.id }) { bill ->
                        BillCard(bill = bill, bucket = bucket)
                    }
                }

                if (meta?.hasMore == true) {
                    item(key = "bz-more") {
                        LoadMoreRow(
                            loading = meta.loadingMore,
                            onClick = { scope.launch { runCatching { repository.loadMore() } } },
                        )
                    }
                }

                item(key = "bz-tail") {
                    HintText(
                        "说明：这里只读门户的事务清单，办理与详情仍回门户原站完成；" +
                            "清单里的详情地址可能带一次性凭据，所以本页不显示、不点击。",
                    )
                }
            }

        }
    }
}

/** 本地筛：名称、描述、状态三者之一包含关键词即可（忽略大小写）。 */
private fun filterBills(bills: List<PortalBill>, query: String, bucket: PortalBillBucket): List<PortalBill> {
    val q = query.trim()
    if (q.isBlank()) return bills
    val status = bucketLabel(bucket)
    return bills.filter { bill ->
        bill.name.contains(q, ignoreCase = true) ||
            bill.description.contains(q, ignoreCase = true) ||
            status.contains(q, ignoreCase = true) ||
            (bill.isRejected && "已驳回".contains(q, ignoreCase = true))
    }
}

/**
 * 三个桶的**界面标签**。
 *
 * 数据层 [PortalBillBucket.label] 是「我的待办 / 我的申请 / 我的已办」——
 * 那是接口文档里的名字。稿（`transactions.png`）的分段控件第一个写的是「待办」。
 * 这里按稿显示，但**不改数据层的 label**：它还用在别处，改它就等于改全工程口径。
 */
private fun bucketLabel(bucket: PortalBillBucket): String = when (bucket) {
    PortalBillBucket.TODO -> "待办"
    PortalBillBucket.APPLIED -> "我的申请"
    PortalBillBucket.FINISHED -> "我的已办"
}

/**
 * 三段式分段控件（板的 `.segmented`：一条 99px 圆角槽，选中项浮起来）。
 *
 * 没直接用 `TabRow`：那是「宽度固定、居中排」的下划线式标签，
 * 而稿这里是**三等分满宽**的胶囊槽。每一格都撑到 [Dims.minTouch]，
 * 窄屏上也不会把字挤没。
 */
@Composable
private fun BucketSegmented(selected: PortalBillBucket, onSelect: (PortalBillBucket) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.background)
            .border(1.dp, colors.dividerLine, RoundedCornerShape(percent = 50))
            .padding(Dims.tight),
        horizontalArrangement = Arrangement.spacedBy(Dims.tight),
    ) {
        PortalBillBucket.entries.forEach { item ->
            val on = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dims.minTouch)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (on) colors.surface else Color.Transparent)
                    .clickableNoRipple(onClick = { onSelect(item) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bucketLabel(item),
                    fontSize = Type.caption,
                    fontWeight = if (on) Type.weightTitle else Type.weightBody,
                    color = if (on) colors.primary else colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 可点击 + 有命中区 + **保留无障碍语义**的小助手。
 *
 * 不用 `AppCard` 包一格：`AppCard` 自带边框与阴影，九个格子摞在槽里会显得脏。
 */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(clickable(onClick = onClick))

/** 顶部说明：当前是哪一类、共几条（及「这次没刷新成功」）。 */
@Composable
private fun SecSectionHead(title: String, meta: String?, icon: MarkIcon = MarkIcon.Todo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dims.sectionGap),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BodyMark(icon = icon, tint = MiuixTheme.colorScheme.primary, size = Mark.size)
            Text(
                text = title,
                fontSize = Type.title,
                fontWeight = Type.weightTitle,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
        if (meta != null) {
            Text(
                text = meta,
                fontSize = Type.caption,
                lineHeight = Type.lineCaption,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = TextStyle(fontFeatureSettings = Type.tabular),
            )
        }
    }
}

/** 一条事务。 */
@Composable
private fun BillCard(bill: PortalBill, bucket: PortalBillBucket) {
    val colors = MiuixTheme.colorScheme
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bill.name.ifBlank { "（无标题）" },
                    fontSize = Type.body,
                    fontWeight = Type.weightTitle,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // 待办是「要你动手」，申请/已办是「看结果」—— 动词不同。
                    text = if (bucket == PortalBillBucket.TODO) "办理 ›" else "详情 ›",
                    fontSize = Type.caption,
                    lineHeight = Type.lineCaption,
                    color = colors.primary,
                )
            }

            if (bill.description.isNotBlank()) {
                Spacer(Modifier.height(Dims.tight))
                Text(
                    text = bill.description,
                    fontSize = Type.secondary,
                    lineHeight = Type.lineCaption,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(Dims.normal))
            val lines = buildList {
                if (bill.createdAt.isNotBlank()) add(bill.createdAt)
                add(bucketLabel(bucket))
                if (bill.isRejected) add("已驳回")
                if (bill.ownerName.isNotBlank()) add("发起人：${bill.ownerName}")
                bill.handledAt?.takeIf { it.isNotBlank() }?.let { add("处理：$it") }
            }
            Text(
                text = lines.joinToString(" · "),
                fontSize = Type.caption,
                lineHeight = Type.lineCaption,
                color = if (bill.isRejected) colors.error else Tone.quiet,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // ⚠️ `bill.detailUrl` 有意**不显示、不点击**：
            //    那个地址可能带一次性凭据（门户的 url 常常是 SSO 跳转链），
            //    显示它等于把凭据印在屏幕上，而点击它需要先确认目标域是否学校域。
        }
    }
}

/** 「加载更多」。 */
@Composable
private fun LoadMoreRow(loading: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dims.tight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            Text(
                text = "正在加载…",
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            TextButton(
                text = "加载更多",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 页头三行：眉题 / 大标题 / 一句副题（照稿 `.eyebrow` + `.hero-title` + `.muted`）。 */
@Composable
private fun SecHero(eyebrow: String, title: String, sub: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = eyebrow,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            letterSpacing = 0.10.em,
            color = Tone.quiet,
            style = TextStyle(fontFeatureSettings = Type.tabular),
        )
        Text(
            text = title,
            fontSize = Type.displayTitle,
            lineHeight = Type.lineDisplayTitle,
            fontWeight = Type.weightTitle,
            letterSpacing = (-0.02).em,
            color = colors.onBackground,
            modifier = Modifier.padding(top = Dims.tight, bottom = Dims.tight),
        )
        Text(
            text = sub,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Start,
        )
    }
}

/** 说明条：左侧一道竖线 + 淡底（照稿 `.notice`，不是一张卡片）。 */
@Composable
private fun SecNotice(text: String, error: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val tint = if (error) colors.error else colors.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(tint),
        )
        Text(
            text = text,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = tint,
            modifier = Modifier.padding(
                start = Dims.loose,
                end = Dims.cardPadding,
                top = Dims.normal,
                bottom = Dims.normal,
            ),
        )
    }
}
