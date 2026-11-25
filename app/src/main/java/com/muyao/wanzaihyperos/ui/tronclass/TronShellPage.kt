package com.muyao.wanzaihyperos.ui.tronclass

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.muyao.wanzaihyperos.data.tronclass.HomeworkRepository
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.data.tronclass.RollcallRepository
import com.muyao.wanzaihyperos.data.tronclass.TronFeedRepository
import com.muyao.wanzaihyperos.data.tronclass.TronHomeRepository
import com.muyao.wanzaihyperos.data.tronclass.TronMeRepository
import com.muyao.wanzaihyperos.ui.design.Dims
import top.yukonga.miuix.kmp.basic.TabRow

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
    homeworkDetailRepository: com.muyao.wanzaihyperos.data.tronclass.HomeworkDetailRepository,
    rollcallRepository: RollcallRepository,
    courseDetailRepository: com.muyao.wanzaihyperos.data.tronclass.CourseDetailRepository,
    scheduleRepository: com.muyao.wanzaihyperos.data.tronclass.ScheduleRepository,
    membersRepository: com.muyao.wanzaihyperos.data.tronclass.TronMembersRepository,
    resourcesRepository: com.muyao.wanzaihyperos.data.tronclass.TronResourcesRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** 網頁版出口（學在城院原站）。 */
    onOpenWeb: () -> Unit,
) {
    // 目前選中的板塊。用 Int 而不是枚舉：標籤順序即索引，
    // 少一個需要維護的型別（板塊就三個，不會再長）。
    var tab by remember { mutableIntStateOf(TAB_HOME) }

    // 子頁（課程詳情 / 作業 / 簽到站 / 課表）是**整頁**，自己帶頂欄。
    // 它們打開時外殼必須讓位：否則會在「我的課」頂欄與標籤列底下再畫一層頂欄，
    // 實機上就是兩條返回箭頭、兩層標題（2026-11-08 拍照為證）。
    var navigation by remember { mutableStateOf(TronBackStack()) }
    val sub = navigation.current
    var fullscreen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(sub) {
        if (sub != TronSub.None) fullscreen = true
    }
    BackHandler(enabled = sub != TronSub.None) { navigation = navigation.back() }

    // 頂欄走共享殼（[SecondaryScaffold]）：底下的漸變、安全區、返回鍵、
    // 標題欄高度都只由設計層決定一次。
    //
    // ⚠️ 殼沒有「標籤列」這個插槽 —— 標籤是**這一頁自己的結構**，
    // 放在內容上方（而不是塞進頂欄），所以：
    //   · 頂欄永遠只有一行（52dp），不隨板塊變高變矮；
    //   · 標籤列跟著內容區走，往下滑時板塊內容在它下面捲。
    // 「刷新」要真的刷。刷的是**目前這個板塊** —— 使用者按下去的時候
    // 看到的就是這一頁的資料，刷別的板塊既慢又看不出效果。
    val scope = rememberCoroutineScope()

    val homeContent = remember(homeRepository, feedRepository, meRepository) {
        androidx.compose.runtime.movableContentOf<TronSub, Modifier> { destination, contentModifier ->
            TronHomeContent(
                repository = homeRepository,
                homeworkRepository = homeworkRepository,
                homeworkDetailRepository = homeworkDetailRepository,
                rollcallRepository = rollcallRepository,
                courseDetailRepository = courseDetailRepository,
                scheduleRepository = scheduleRepository,
                membersRepository = membersRepository,
                resourcesRepository = resourcesRepository,
                onOpenWeb = onOpenWeb,
                subState = destination,
                onSub = { navigation = navigation.open(it) },
                modifier = contentModifier,
                onPresentationSettled = { settled ->
                    if (settled == TronSub.None && navigation.current == TronSub.None) fullscreen = false
                },
            )
        }
    }
    if (sub != TronSub.None || fullscreen) {
        homeContent(sub, modifier)
        return
    }

    SecondaryScaffold(
        // 頂欄標題用板塊名（我的課 / 動態 / 我的），不是「學在城院」：
        // 各板塊內容自己會用 hero 寫「LEARNING / 學在城院」，
        // 兩處都寫就是同一句話在螢幕上出現兩次。
        title = TABS[tab].first,
        inlineFeedbackAnchored = tab == TAB_HOME,
        subtitle = TABS[tab].second,
        onBack = onExit,
        modifier = modifier,
        onRefresh = {
            scope.launch {
                runCatching {
                    when (tab) {
                        TAB_HOME -> homeRepository.refresh()
                        TAB_FEED -> feedRepository.refresh()
                        else -> meRepository.refresh()
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 安全區只讓一次：交給殼算好的 top padding。
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            // 標籤列：三個板塊。
            // ⚠️ Miuix 的 TabRow 是**資料驅動**的（`tabs` + `selectedTabIndex`
            //   + `onTabSelected`），不是 Material 那種「自己放 Tab 子項」的用法。
            // 88dp 兩態（選中／未選中同寬）—— 與稿上三個等寬標籤一致。
            com.muyao.wanzaihyperos.ui.design.SlidingChoiceRow(
                labels = TABS.map { it.first },
                selectedIndex = tab,
                onSelect = { tab = it; navigation = TronBackStack() },
                modifier = Modifier.padding(horizontal = Dims.screenH),
            )

            val contentModifier = Modifier.fillMaxSize()

            when (tab) {
                TAB_HOME -> homeContent(sub, contentModifier)
                TAB_FEED -> TronFeedContent(
                    repository = feedRepository,
                    onOpenWeb = onOpenWeb,
                    modifier = contentModifier,
                )
                else -> TronMeContent(
                    repository = meRepository,
                    onOpenResources = { navigation = navigation.open(TronSub.Resources) },
                    onOpenRollcall = { navigation = navigation.open(TronSub.Rollcall) },
                    onOpenWeb = onOpenWeb,
                    modifier = contentModifier,
                )
            }
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
