package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.HomeworkRepository
import com.muyao.wanzaihyperos.data.tronclass.RollcallRepository
import com.muyao.wanzaihyperos.data.tronclass.TronFeedRepository
import com.muyao.wanzaihyperos.data.tronclass.TronHomeRepository
import com.muyao.wanzaihyperos.data.tronclass.TronMeRepository
import com.muyao.wanzaihyperos.ui.design.BackRow
import androidx.compose.runtime.collectAsState

/**
 * 三個板塊的**內容部分**（不含頂欄與標籤 —— 那些由 [TronShellPage] 提供）。
 *
 * ## 為什麼拆「外殼 + 內容」
 *
 * 兩個好處都直接服務於後面要做的「整體美化」：
 *
 * 1. **板塊不知道自己在哪個容器裡** —— 要從標籤切換改成底部導航、
 *    或改成可滑動分頁，只動外殼，三個板塊一行都不用改；
 * 2. **內容可以單獨走查** —— 不必先搭一整套導航才能看一頁。
 *
 * 這也是為什麼這幾個 `*Content` **不接受 `onExit`**：
 * 「返回」是外殼的事，內容不該管。
 */

/** 學在城院內部的二級頁面（不佔外殼的標籤）。 */
internal sealed interface TronSub {
    data object None : TronSub
    data object Rollcall : TronSub
    data class Homework(val courseId: Int, val courseName: String) : TronSub

    /**
     * 一項作業的詳情 + 提交。
     *
     * ★ 帶 `homeworkId` 與 `title`：詳情倉庫要拿 id 去清單裡**核對**
     *（這個 id 可能來自待辦，不一定等於作業 id —— 見
     * [com.muyao.wanzaihyperos.data.tronclass.LiveHomeworkDetailRepository.resolveIn]），
     * `title` 就是核對用的第二把鑰匙。
     */
    data class HomeworkDetail(
        val courseId: Int,
        val homeworkId: Int,
        val courseName: String,
        val title: String,
    ) : TronSub

    /**
     * 課程詳情。
     *
     * ★ 帶的是整個 [com.muyao.wanzaihyperos.data.tronclass.TronCourse] 而不是 id：
     * 頁首要顯示上課時間，而那個欄位**只有課程清單有**（詳情接口的
     * `classroom_schedule` 實測是 null）。把清單那一份帶進去，
     * 頁首在詳情回來之前就能顯示完整信息。
     */
    data class Course(val course: com.muyao.wanzaihyperos.data.tronclass.TronCourse) : TronSub

    /** 課表（本週 / 全部）。 */
    data object Schedule : TronSub
}

@Composable
internal fun TronHomeContent(
    repository: TronHomeRepository,
    homeworkRepository: HomeworkRepository,
    homeworkDetailRepository: com.muyao.wanzaihyperos.data.tronclass.HomeworkDetailRepository,
    rollcallRepository: RollcallRepository,
    courseDetailRepository: com.muyao.wanzaihyperos.data.tronclass.CourseDetailRepository,
    scheduleRepository: com.muyao.wanzaihyperos.data.tronclass.ScheduleRepository,
    onOpenWeb: () -> Unit,
    /** 目前打開的子頁（由外殼持有，見 [TronShellPage]）。 */
    subState: TronSub,
    /** 切換子頁。 */
    onSub: (TronSub) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 子頁狀態由外殼 [TronShellPage] 持有：子頁打開時外殼收起自己的頂欄與標籤列，
    // 否則課程詳情會在學在城院的頂欄底下再畫一層頂欄（實機上是兩條返回箭頭）。
    val sub = subState

    val state by repository.state.collectAsState()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    when (val s = sub) {
        TronSub.None -> TronHomeBody(
            repository = repository,
            onOpenRollcall = { onSub(TronSub.Rollcall) },
            onOpenSchedule = { onSub(TronSub.Schedule) },
            onOpenCourse = { course -> onSub(TronSub.Course(course)) },
            onOpenWeb = onOpenWeb,
            modifier = modifier,
        )

        TronSub.Rollcall -> RollcallContent(
            repository = rollcallRepository,
            onBack = { onSub(TronSub.None) },
            onOpenWeb = onOpenWeb,
            modifier = modifier,
        )

        TronSub.Schedule -> {
            ScheduleContent(
                repository = scheduleRepository,
                onBack = { onSub(TronSub.None) },
                onOpenCourse = { id, name ->
                    // 課表點課 → 進課程詳情。用**已取到的**課程清單那筆補齊教師等欄位：
                    // 課表格子本身只有課名與課號，不能自己造一個 TronCourse。
                    val known = (state as? ListState.Content)?.items?.firstOrNull()
                        ?.courses?.firstOrNull { it.id == id }
                    onSub(TronSub.Course(
                        known ?: com.muyao.wanzaihyperos.data.tronclass.TronCourse(
                            id = id,
                            name = name,
                            courseCode = "",
                            teachingClass = "",
                            department = "",
                        ),
                    ))
                },
                onOpenWeb = onOpenWeb,
                modifier = modifier,
            )
        }

        is TronSub.Course -> {
            // 載入由「進入那一門課」觸發（詳情接口是課程內的）。
            LaunchedEffect(s.course.id) {
                runCatching { courseDetailRepository.load(s.course) }
            }
            CourseDetailContent(
                repository = courseDetailRepository,
                onBack = { onSub(TronSub.None) },
                // 「看全部作業」複用既有的作業頁（同一份資料不做兩個實作）。
                onOpenHomework = { onSub(TronSub.Homework(s.course.id, s.course.name)) },
                onOpenWeb = onOpenWeb,
                modifier = modifier,
            )
        }

        is TronSub.Homework -> {
            // 載入由「進入那一門課」觸發（作業接口是課程內的）。
            LaunchedEffect(s.courseId) {
                if (s.courseId > 0) {
                    runCatching { homeworkRepository.load(s.courseId, s.courseName) }
                }
            }
            HomeworkContent(
                repository = homeworkRepository,
                onBack = { onSub(TronSub.None) },
                onOpenWeb = onOpenWeb,
                onOpenHomework = { courseId, homeworkId, courseName, title ->
                    onSub(TronSub.HomeworkDetail(courseId, homeworkId, courseName, title))
                },
                modifier = modifier,
            )
        }

        is TronSub.HomeworkDetail -> {
            // 載入由「進入這一項作業」觸發；提交是寫操作，只在這一頁。
            LaunchedEffect(s.homeworkId) {
                if (s.courseId > 0) {
                    runCatching {
                        homeworkDetailRepository.load(s.courseId, s.homeworkId, s.courseName, s.title)
                    }
                }
            }
            HomeworkDetailBody(
                repository = homeworkDetailRepository,
                onOpenWeb = onOpenWeb,
                header = { item { BackRow("返回", onBack = { onSub(TronSub.Homework(s.courseId, s.courseName)) }) } },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun TronFeedContent(
    repository: TronFeedRepository,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TronFeedPage(
        repository = repository,
        modifier = modifier,
        onFallbackToWeb = onOpenWeb,
    )
}

@Composable
fun TronMeContent(
    repository: TronMeRepository,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TronMePage(
        repository = repository,
        modifier = modifier,
        onFallbackToWeb = onOpenWeb,
    )
}
