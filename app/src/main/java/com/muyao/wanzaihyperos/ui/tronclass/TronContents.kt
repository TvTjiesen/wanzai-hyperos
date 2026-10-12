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
private sealed interface TronSub {
    data object None : TronSub
    data object Rollcall : TronSub
    data class Homework(val courseId: Int, val courseName: String) : TronSub
}

@Composable
fun TronHomeContent(
    repository: TronHomeRepository,
    homeworkRepository: HomeworkRepository,
    rollcallRepository: RollcallRepository,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sub by remember { mutableStateOf<TronSub>(TronSub.None) }
    val state by repository.state.collectAsState()

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    when (val s = sub) {
        TronSub.None -> TronHomeBody(
            repository = repository,
            onOpenRollcall = { sub = TronSub.Rollcall },
            onOpenCourse = { id, name -> sub = TronSub.Homework(id, name) },
            onOpenWeb = onOpenWeb,
            modifier = modifier,
        )

        TronSub.Rollcall -> RollcallContent(
            repository = rollcallRepository,
            onBack = { sub = TronSub.None },
            onOpenWeb = onOpenWeb,
            modifier = modifier,
        )

        is TronSub.Homework -> {
            // 載入由「進入那一門課」觸發（作業接口是課程內的）。
            LaunchedEffect(s.courseId) {
                if (s.courseId > 0) {
                    runCatching { homeworkRepository.load(s.courseId, s.courseName) }
                }
            }
            HomeworkContent(
                repository = homeworkRepository,
                onBack = { sub = TronSub.None },
                onOpenWeb = onOpenWeb,
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
