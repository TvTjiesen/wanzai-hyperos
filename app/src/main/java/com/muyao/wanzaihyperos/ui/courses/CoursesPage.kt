package com.muyao.wanzaihyperos.ui.courses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.cjh52.Course
import com.muyao.wanzaihyperos.data.cjh52.CourseRepository
import com.muyao.wanzaihyperos.data.cjh52.CourseSlot
import com.muyao.wanzaihyperos.ui.cjh52.CjhChip
import com.muyao.wanzaihyperos.ui.cjh52.CjhHero
import com.muyao.wanzaihyperos.ui.cjh52.CjhKvRow
import com.muyao.wanzaihyperos.ui.cjh52.CjhNotice
import com.muyao.wanzaihyperos.ui.cjh52.CjhOriginSection
import com.muyao.wanzaihyperos.ui.cjh52.CjhStaleNotice
import com.muyao.wanzaihyperos.ui.cjh52.CjhSourceCaption
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「我的課程」原生頁（B1）。
 *
 * ### 這個頁面要解決的核心問題
 *
 * PLAN §B1 要求「模型支援**多周次、多時段、多教室**」。
 * 資料層已經做到了（[Course.slots] 是清單），
 * **但介面也必須把它顯示出來** —— 否則資料層留住的第二個時段，
 * 到了使用者眼前還是消失了，等於沒做。
 *
 * 因此一門課的值欄列出它的**全部時段**（一行一段），不是只挑第一個。
 *
 * ### 與稿 `cjhCourses.png` 的對應
 *
 * 稿上是「課名 → 時段·地點」緊接「教師 / 學期」的**成對鍵值行**，
 * 行與行之間一道細分割線，**沒有卡片外框、沒有頂部統計條**。
 * 以前這裡是一張張 [com.muyao.wanzaihyperos.ui.design.AppCard]，
 * 對照截圖會發現整頁節奏完全不同（卡片陰影把「一份課表」讀成了「一堆卡片」）。
 *
 * ### 狀態處理
 *
 * 走既有的 [toPageState] 橋接（與其它頁面同一套判據），
 * 所以「取不到」與「沒有課」在界面上是不同的：
 * 前者是錯誤頁可重試，後者是空態。
 */
@Composable
fun CoursesPage(
    repository: CourseRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 原生頁取不到資料時（尤其**會話無效**）的出口：改用網頁版。
     *
     * ★ 這條是 B1 的**必要設計**，不是可選項。
     * 原生頁依賴的 CJH52 會話可能失效（門戶 tempcode 有壽命），
     * 所以必須讓使用者有路可走 —— WebView 那條是已被真機走查驗證過的。
     */
    onFallbackToWeb: (() -> Unit)? = null,
    /**
     * 會話是否就緒（由 [com.muyao.wanzaihyperos.ui.web.Cjh52WarmUpGate] 給）。
     *
     * 為 false 時**不要取數** —— 那一定 401，只會讓使用者先看到一個假錯誤。
     * 頁面此時顯示自己的載入態即可。
     */
    ready: Boolean = true,
    /**
     * 稿 `cjhCourses.png` 那枚「CJH52 查课表」的落點。
     *
     * 它**不是** CJH52 的頁面：查課表是門戶（ID 153）那條獨立的課表，
     * 與本頁的 CJH52 課程是兩個資料源。稿上把它放在這頁，
     * 正是為了不讓人以為「這一頁就是全部課表」。為 null 時不畫。
     */
    onOpenTimetable: (() -> Unit)? = null,
    /** 稿 `cjhCourses.png` 那枚「六项服务目录」的落點。為 null 時不畫。 */
    onOpenDirectory: (() -> Unit)? = null,
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val pageState = state.toPageState()
    val courses = (state as? ListState.Content)?.items.orEmpty()

    // 會話就緒後才取數。未就緒時畫面停在 FirstLoad（= 載入態），
    // 這段等待就是預熱的時間（通常 1-2 秒）。
    LaunchedEffect(ready) {
        if (ready && state is ListState.FirstLoad) repository.refresh()
    }

    // ★ 2026-11-08：換掉自拼的 `ThemedScaffold + SmallTopAppBar`。
    //
    //   兩個實打實的毛病：
    //   ① 右上那顆刷新鈕的 `onClick` 是**空的**（`{ /* 由外層 scope 觸發 */ }`）——
    //      它畫得出來、按得下去、什麼都不會發生。這種「看起來能用其實是死的」
    //      按鈕比沒有按鈕更糟：使用者會以為是網路慢，而不是鍵壞了。
    //   ② 全 App 的其它二級頁都走 [SecondaryScaffold]，只有這一頁自己拼頂欄，
    //      字級／內距／返回圖標尺寸遲早和其它頁漂移（本專案吃過三次同樣的虧）。
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val veilProgress by rememberScrollProgress(listState)
    val refresh: () -> Unit = { scope.launch { runCatching { repository.refresh() } } }

    SecondaryScaffold(
        scrollProgress = veilProgress,
        title = "我的课程",
        onBack = onExit,
        modifier = modifier,
    ) { innerPadding ->
        // ★ 只讓 top：底欄由宿主 Scaffold 畫並已讓過，再讓一次會多出一條空白。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "courses-hero") {
                    CjhHero(
                        eyebrow = "CJH52 / 学生服务",
                        title = "我的课程",
                        sub = "独立于教务与学在城院的原生阅读入口。",
                    )
                }
                item(key = "courses-stale") { CjhStaleNotice(pageState) }

                if (courses.isEmpty()) {
                    item(key = "courses-state") {
                        StatePlaceholder(
                            state = pageState,
                            // ★ 原生頁取不到資料時的出口。
                            //
                            // 刻意**在所有非內容狀態**都提供（不只錯誤態）：會話失效、離線、
                            // 甚至空態都給 —— 因為原生頁與網頁版的資料來源雖然同一個接口，
                            // 但網頁版是**已被驗證**的那條路。使用者不該因為我們改用了原生頁
                            // 就失去一條能用的路。
                            onAction = onFallbackToWeb,
                            actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
                        )
                    }
                } else {
                    item(key = "courses-summary") {
                        Text(
                            text = "共 ${courses.size} 门课 · " +
                                "${courses.sumOf { it.slots.size }} 个上课时段",
                            style = TextStyle(fontFeatureSettings = Type.tabular),
                            fontSize = Type.body,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    items(courses, key = { it.id }) { course -> CourseBlock(course) }
                }

                item(key = "courses-notice") {
                    // 稿 `cjhCourses.png` 的說明：這一頁只裝 CJH52 的課程，
                    // 不吞掉「查課表 / 主課表 / 學在課表」三條各自獨立的路。
                    CjhNotice(
                        text = "这是 CJH52 我的课程；查课表 ID153、主课表与学在课表仍独立保留。",
                    )
                }
                if (onOpenTimetable != null) {
                    item(key = "courses-timetable") {
                        CjhChip(
                            label = "CJH52 查课表",
                            onClick = onOpenTimetable,
                            modifier = Modifier.padding(top = Dims.tight),
                        )
                    }
                }
                item(key = "courses-origin") {
                    CjhOriginSection(
                        webLabel = "我的课程完整网页版",
                        onOpenWeb = onFallbackToWeb,
                        onRefresh = refresh,
                        onOpenDirectory = onOpenDirectory,
                    )
                }
                item(key = "courses-source") { CjhSourceCaption("CJH52 接口") }
            }
        }
    }
}

/**
 * 一門課：兩行鍵值（稿 `cjhCourses.png` 的節奏）。
 *
 * 第一行標籤是**課名**、值是**時段·地點**；第二行是「教師 / 學期」。
 * 這不是隨意排的：稿上就是靠「課名左、時段右」讓一屏能掃完整週課表。
 *
 * ### 學期為什麼寫「—」
 *
 * `Course` 契約裡**沒有學期欄位**（真機取證確認：id/name/classNo/collegeName/
 * teacherName/courseCode/nature/slots）。稿上那個「2026秋季」是原型的樣例值，
 * 抄過來等於替所有課程宣稱一個學期。所以照「未知寫 —」的規矩處理。
 */
@Composable
private fun CourseBlock(course: Course) {
    CjhKvRow(
        label = course.name.ifBlank { "（未命名课程）" },
        value = if (course.slots.isEmpty()) {
            "尚未排定上课时段"
        } else {
            course.slots.joinToString("\n") { slotText(it) }
        },
        // 課名當標籤位，必須給比例寬度：長課名會把時段列擠成一條窄縫（真機實測折四行＋裁切）。
        labelWeight = 0.46f,
    )
    CjhKvRow(
        label = "教师 / 学期",
        value = (course.teacherName.ifBlank { "—" }) + " / —",
    )
}

/**
 * 單個時段：`星期二 13:30~15:05 · 教六310 · 1-8 周`。
 *
 * 每一項缺失就整段不出現（不留「· ·」），週次解析不出來就不寫週次 ——
 * 這門課有些時段是「單雙週」的，猜一個 1-16 週會讓學生走錯教室。
 */
private fun slotText(slot: CourseSlot): String {
    // 分兩行：`星期X 時段` 一行、`地點 · 周次` 一行。
    // 擠成一行時右列只有半屏寬，實測「教六205」會被從中間折成「教六」/「205」，
    // 一個地名被拆成兩截比多一行更難讀。
    val whenPart = buildList {
        if (slot.day.isNotBlank()) add(slot.day)
        if (slot.start.isNotBlank() && slot.end.isNotBlank()) {
            add(slot.start.removeSuffix(":00") + "~" + slot.end.removeSuffix(":00"))
        }
    }.joinToString(" ")
    val wherePart = buildList {
        if (slot.displayLocation.isNotBlank()) add(slot.displayLocation)
        slot.weekRange?.let { add("${it.first}-${it.last} 周") }
    }.joinToString(" · ")
    return listOf(whenPart, wherePart).filter { it.isNotBlank() }.joinToString("\n")
}
