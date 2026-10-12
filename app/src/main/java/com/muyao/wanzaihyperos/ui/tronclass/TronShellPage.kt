package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.tronclass.HomeworkRepository
import com.muyao.wanzaihyperos.data.tronclass.RollcallRepository
import com.muyao.wanzaihyperos.data.tronclass.TronFeedRepository
import com.muyao.wanzaihyperos.data.tronclass.TronHomeRepository
import com.muyao.wanzaihyperos.data.tronclass.TronMeRepository
import com.muyao.wanzaihyperos.ui.design.Dims
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「學在城院」的**外殼**：頂欄 + 三個板塊（我的課 / 動態 / 我的）。
 *
 * ## 為什麼要有這一層（而不是把三頁各自獨立）
 *
 * 業主指出學在城院「有好幾個部分，首頁課程動態和我的界面」。
 * 三頁各自獨立會導致：
 * - 每頁各有一個返回箭頭與標題 → 使用者在「學在城院」內部切換時，
 *   看起來像**反覆離開再進入**這個應用；
 * - 標題列、邊距、返回行為各寫一遍 → 又一個不一致的來源。
 *
 * 這一層把「我現在在學在城院裡面」這件事**表達出來**，
 * 板塊之間用標籤切換，只有最外層才有返回。
 *
 * ## 與美化預留的關係
 *
 * 標籤列的樣式（目前是 Miuix 的 `TabRow`）與各板塊的內容**互不耦合**：
 * 後面要改成底部導航、側滑、或換一套標籤元件，只動這一層。
 * 各板塊只提供內容，不知道自己在哪個容器裡。
 */
@Composable
fun TronShellPage(
    homeRepository: TronHomeRepository,
    feedRepository: TronFeedRepository,
    meRepository: TronMeRepository,
    homeworkRepository: HomeworkRepository,
    rollcallRepository: RollcallRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** 網頁版出口（學在城院原站）。 */
    onOpenWeb: () -> Unit,
) {
    // 目前選中的板塊。用 Int 而不是枚舉：標籤順序即索引，
    // 少一個需要維護的型別（板塊就三個，不會再長）。
    var tab by remember { mutableIntStateOf(TAB_HOME) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                SmallTopAppBar(
                    title = "学在城院",
                    subtitle = TABS[tab].second,
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(
                                imageVector = MiuixIcons.ChevronBackward,
                                contentDescription = "返回",
                                modifier = Modifier.size(Dims.iconNav),
                            )
                        }
                    },
                )
                // 標籤列：三個板塊。
                // ⚠️ Miuix 的 TabRow 是**資料驅動**的（`tabs` + `selectedTabIndex`
                //   + `onTabSelected`），不是 Material 那種「自己放 Tab 子項」的用法。
                //   一開始照 Material 的寫法編不過 —— 這裡照著專案既有的用法。
                TabRow(
                    tabs = TABS.map { it.first },
                    selectedTabIndex = tab,
                    onTabSelected = { tab = it },
                    minWidth = 88.dp,
                    maxWidth = 88.dp,
                    modifier = Modifier.padding(horizontal = Dims.screenH),
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding())

        when (tab) {
            TAB_HOME -> TronHomeContent(
                repository = homeRepository,
                homeworkRepository = homeworkRepository,
                rollcallRepository = rollcallRepository,
                onOpenWeb = onOpenWeb,
                modifier = contentModifier,
            )
            TAB_FEED -> TronFeedContent(
                repository = feedRepository,
                onOpenWeb = onOpenWeb,
                modifier = contentModifier,
            )
            else -> TronMeContent(
                repository = meRepository,
                onOpenWeb = onOpenWeb,
                modifier = contentModifier,
            )
        }
    }
}

/** 標籤：(名稱, 副標題)。順序即索引。 */
private val TABS = listOf(
    "我的课" to "我的课与作业",
    "动态" to "公告与最近动态",
    "我的" to "个人资料与概览",
)

private const val TAB_HOME = 0
private const val TAB_FEED = 1
private const val TAB_ME = 2
