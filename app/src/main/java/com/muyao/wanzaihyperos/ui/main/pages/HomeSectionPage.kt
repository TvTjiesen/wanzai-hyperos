package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.muyao.wanzaihyperos.ui.home.HomeHero
import com.muyao.wanzaihyperos.ui.home.HomeTile
import com.muyao.wanzaihyperos.ui.home.HomeWindow
import com.muyao.wanzaihyperos.ui.home.TileIcon
import com.muyao.wanzaihyperos.ui.home.wallpaperFile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.glass.GlassSurface
import com.muyao.wanzaihyperos.ui.design.glass.LocalGlassBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首頁板塊：今日課程 / 簽到 / 待辦 / 快捷。
 *
 * 資料來源：
 * - 今日課程：教務系統個人課表，尚未接入（見下）
 * - 簽到：學在城院 rollcall，已原生
 * - 待辦：門戶審批 + 學在城院作業 todoBoard，已原生
 *
 * 為什麼今日課程寫「尚未接入」而不是顯示空：
 * 教務系統的個人課表沒有 JSON 介面（B18 實測，內容直接畫在 HTML 裡），
 * 要另寫 DOM 解析才能取。所以不能顯示「今天沒有課」——
 * 那是在替服務端撒謊（本專案反覆栽的坑）。
 * 要明確說「還沒接入」，讓使用者知道是缺功能，不是沒課。
 */
@Composable
fun HomeSectionPage(
    graph: AppGraph,
    onOpenJw: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenTodos: () -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val backdrop = LocalGlassBackdrop.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val rollcallState by graph.rollcall.state.collectAsState()
    val todoState by graph.todoBoard.state.collectAsState()

    /** 哪块磁贴是展开的（同时只允许一块，免得两列各自长高把首屏撑散）。 */
    var expandedTile by remember { mutableStateOf<String?>(null) }

    // ── 视窗的自定义壁纸 ───────────────────────────────────────────
    //
    // 选完**拷进 files 目录**再解码（见 `loadWallpaperFrom`）：只存 URI 的话
    // 重启后权限可能失效，用户会遇到「昨天还有壁纸，今天只剩内置场景」。
    var wallpaper by remember {
        mutableStateOf(com.muyao.wanzaihyperos.ui.home.decodeWallpaper(wallpaperFile(context)))
    }
    val pickWallpaper = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val img = com.muyao.wanzaihyperos.ui.home.loadWallpaperFrom(context, uri)
            // 读不回来就保持原样，**不清空**：用户换图失败时最讨厌的是连旧的也没了
            if (img != null) wallpaper = img
        }
    }

    LaunchedEffect(Unit) {
        // 取数走应用级作用域（`AppGraph.fetch`），不挂在组合生命周期上 ——
        // 见 `AppGraph.ioScope` 里那两条真机根因。
        graph.fetch { graph.rollcall.refresh() }
        graph.fetch { graph.todoBoard.refresh() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            top = 56.dp,
            bottom = 320.dp,   // 留出玻璃底欄的高度 + 一屏餘量：讓最後幾張卡能滾到玻璃底下，玻璃才折射得到東西
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        // ── 今天：超大日期 + 每日一句（业主：「顶部的这个今天可以大一点」）──
        item(key = "hero") {
            HomeHero(modifier = Modifier.padding(bottom = 4.dp))
        }

        // ── 视窗：内置随时间变化的场景，可换自己的壁纸，可放大/收回 ──────
        item(key = "window") {
            HomeWindow(
                wallpaper = wallpaper,
                onPickWallpaper = {
                    pickWallpaper.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onClearWallpaper = {
                    com.muyao.wanzaihyperos.ui.home.deleteWallpaper(context)
                    wallpaper = null
                },
            )
        }

        item(key = "today-courses") {
            GlassCard(
                backdrop = backdrop,
                title = "今日課程",
                actionLabel = "教務系統 ›",
                onAction = onOpenJw,
            ) {
                Text(
                    text = "尚未接入教務系統的個人課表。",
                    fontSize = Type.secondary,
                    color = colors.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "課表板塊目前顯示學在城院的課程；教務課表要另外解析，還沒做。",
                    fontSize = Type.caption,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }

        item(key = "rollcall") {
            GlassCard(
                backdrop = backdrop,
                title = "簽到",
                actionLabel = null,
                onAction = null,
            ) {
                when (val s = rollcallState) {
                    is ListState.FirstLoad -> BodyHint("正在取得簽到狀態…")

                    is ListState.Failed -> BodyError(
                        "取不到簽到狀態",
                        s.error.userText,
                    )

                    is ListState.Content -> {
                        val open = s.items.firstOrNull { !it.expired }
                        if (open == null) {
                            BodyHint("目前沒有進行中的簽到。")
                        } else {
                            Text(
                                text = open.title,
                                fontSize = Type.body,
                                fontWeight = Type.weightBody,
                                color = colors.onBackground,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (open.endTime.isBlank()) {
                                    "可以簽到"
                                } else {
                                    "可以簽到 · 截止 ${open.endTime}"
                                },
                                fontSize = Type.caption,
                                color = colors.primary,
                            )
                        }
                    }

                    else -> BodyHint("目前沒有進行中的簽到。")
                }
            }
        }

        item(key = "todos") {
            GlassCard(
                backdrop = backdrop,
                title = "待辦",
                actionLabel = "全部 ›",
                onAction = onOpenTodos,
            ) {
                when (val s = todoState) {
                    is ListState.FirstLoad -> BodyHint("正在整理待辦…")

                    is ListState.Failed -> BodyError(
                        "取不到待辦",
                        s.error.userText,
                    )

                    is ListState.Content -> {
                        // ⚠️ `todoBoard.state` 是 `ListState<TodoBoard>`，而
                        // `ListState.Content.items` 是 `List<T>` —— 也就是
                        // **List<TodoBoard>**（倉庫把單一 board 包成單元素清單）。
                        // 所以要先取 `firstOrNull()` 才是 TodoBoard 本身。
                        // 這是本專案既有的約定，見 TodoBoardPage 的寫法。
                        val all = s.items.firstOrNull()?.groups?.flatMap { it.items }.orEmpty()
                        if (all.isEmpty()) {
                            BodyHint("沒有待辦事項。")
                        } else {
                            Text(
                                text = "${all.size} 項待處理",
                                fontSize = Type.body,
                                fontWeight = Type.weightBody,
                                color = colors.onBackground,
                            )
                            all.take(3).forEach { t ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "· ${t.title}",
                                    fontSize = Type.caption,
                                    color = colors.onSurfaceVariantSummary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    else -> BodyHint("沒有待辦事項。")
                }
            }
        }

        // 今日更多課程（讓內容延伸到玻璃底下 —— 玻璃要折射真的內容才看得出來）
        item(key = "quick-more") {
            GlassCard(
                backdrop = backdrop,
                title = "本週還有",
                actionLabel = "課表 ›",
                onAction = onOpenTimetable,
            ) {
                BodyHint("到課表板塊看整週安排。")
            }
        }

        // ── 磁贴：两列，点一块**就地展开**（详情从它自己下面滑出，收回也回到这块）──
        //
        // 业主 2026-10-18：「底下按照我给你截图的排版放功能，自己设计加一些小图标，
        // 每个磁贴都能交互，都有流畅优雅的交互动画，动画遵循从哪来到哪去的原则」。
        // 所以这里不弹对话框、不做跳转前的中间页：展开/收起都发生在这块磁贴身上。
        item(key = "tiles") {
            Column(verticalArrangement = Arrangement.spacedBy(Dims.cardGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dims.cardGap)) {
                    HomeTile(
                        label = "課表",
                        value = "本週課",
                        icon = TileIcon.Timetable,
                        tint = colors.primary,
                        expanded = expandedTile == "timetable",
                        onToggle = { expandedTile = if (expandedTile == "timetable") null else "timetable" },
                        modifier = Modifier.weight(1f),
                        detail = {
                            BodyHint("整週安排、切週、網格/列表兩種排版。")
                            TileOpen(label = "打開課表 ›", onClick = onOpenTimetable)
                        },
                    )
                    HomeTile(
                        label = "訊息",
                        value = "門戶訊息",
                        icon = TileIcon.Messages,
                        tint = Color(0xFF3E7BFA),
                        expanded = expandedTile == "messages",
                        onToggle = { expandedTile = if (expandedTile == "messages") null else "messages" },
                        modifier = Modifier.weight(1f),
                        detail = {
                            BodyHint("學校門戶發來的通知，按分組看。")
                            TileOpen(label = "打開訊息 ›", onClick = onOpenMessages)
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dims.cardGap)) {
                    HomeTile(
                        label = "待辦",
                        value = when (val s = todoState) {
                            is ListState.Content -> "${s.items.size} 件"
                            is ListState.FirstLoad -> "整理中…"
                            else -> "看詳情"
                        },
                        icon = TileIcon.Todo,
                        tint = Color(0xFF1FA37A),
                        expanded = expandedTile == "todo",
                        onToggle = { expandedTile = if (expandedTile == "todo") null else "todo" },
                        modifier = Modifier.weight(1f),
                        detail = {
                            BodyHint("作業、報名、繳費都列在這裡。")
                            TileOpen(label = "打開待辦 ›", onClick = onOpenTodos)
                        },
                    )
                    HomeTile(
                        label = "教務",
                        value = "成績/考試",
                        icon = TileIcon.Calendar,
                        tint = Color(0xFFB4552E),
                        expanded = expandedTile == "jw",
                        onToggle = { expandedTile = if (expandedTile == "jw") null else "jw" },
                        modifier = Modifier.weight(1f),
                        detail = {
                            BodyHint("教務系統的考試安排與學生成績（只讀）。")
                            TileOpen(label = "打開教務 ›", onClick = onOpenJw)
                        },
                    )
                }
            }
        }
    }
}

/** 磁贴展开后的「打开」入口：展开看说明、再点才真的走，避免误触跳页。 */
@Composable
private fun TileOpen(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = Type.summary,
        fontWeight = Type.weightBody,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .clickable(onClickLabel = label) { onClick() },
    )
}

@Composable
private fun GlassCard(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    GlassSurface(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dims.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = Type.summary,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                if (actionLabel != null && onAction != null) {
                    Text(
                        text = actionLabel,
                        fontSize = Type.caption,
                        color = colors.primary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onAction() },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun BodyHint(text: String) {
    Text(
        text = text,
        fontSize = Type.secondary,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun BodyError(title: String, detail: String) {
    Text(
        text = title,
        fontSize = Type.body,
        color = MiuixTheme.colorScheme.error,
    )
    if (detail.isNotBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            fontSize = Type.caption,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun QuickCard(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(backdrop = backdrop, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = Type.body,
                fontWeight = Type.weightBody,
                color = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}
