package com.muyao.wanzaihyperos.ui.zy

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.zy.ZyEngine
import com.muyao.wanzaihyperos.data.zy.ZyRepository
import com.muyao.wanzaihyperos.data.zy.ZyScheduleItem
import com.muyao.wanzaihyperos.data.zy.timeRange
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.InfoItem
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.infoItemsIndexed
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **智雲課堂**原生頁：本週課表 + 每堂課的回放入口。
 *
 * ## ★ 這個頁面為什麼也要藏一個 WebView
 *
 * 不是偷懶。它的認證靠一批**域 cookie**（`.hzcu.edu.cn` 下），
 * 而本專案的 cookie 罐是**按 host 精確分桶**的（那個設計是刻意的，
 * 為了不讓 CAS 憑據跨系統漂移）——所以 OkHttp 帶不上那些域 cookie，
 * 打出去的請求會是「未登入」。
 *
 * 讓 WebView 自己發請求，域 cookie 由它自己管，天然正確。
 * 而且**播放頁迟早要用 WebView**（觀看進度上報是學校認定學習進度的依據），
 * 既然那個會話已經在 WebView 裡，取數也用它最一致。
 * 詳見 `data/zy/ZyEngine.kt` 的類註釋。
 *
 * WebView 給 **1 像素**且透明：**不能用 `GONE`**（那不執行 JS，取數會靜靜失敗）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ZyPage(
    repository: ZyRepository,
    engine: ZyEngine,
    onOpenReplay: (ZyScheduleItem) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.week.collectAsState()
    val scope = rememberCoroutineScope()

    // 首次進入就載入（引擎內部會先建／沿用會話）。
    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) runCatching { repository.load() }
    }

    val failed = state as? ListState.Failed
    val needsLogin = failed?.error is HzcuException.LoginExpired
    val items: List<ZyScheduleItem> = (state as? ListState.Content)?.items.orEmpty()

    // 頂欄漸變紗要靠它算進度（見 SecondaryScaffold 的 scrollProgress 說明）。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val veilProgress by com.muyao.wanzaihyperos.ui.design.rememberScrollProgress(listState)

    SecondaryScaffold(
        scrollProgress = veilProgress,
        title = "智云课堂",
        subtitle = "本周课表与回放",
        onBack = onExit,
        onRefresh = { scope.launch { runCatching { repository.load(force = true) } } },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                when {
                    // 沒登入 → 給他一個明確的出口（用網頁登入一次）。
                    // 不做原生登入表單：那是學校統一身份認證的頁面，
                    // 抄一份等於多一處要跟著學校改的地方。
                    needsLogin -> {
                        item {
                            ResultBanner(
                                message = "还没有登录智云课堂。用下面的入口打开它的页面，" +
                                    "登录一次之后这里就会显示你的课表。",
                                ok = false,
                            )
                        }
                        item {
                            // 用 ActionRow 而不是按鈕：這是「去做一件事」，
                            // 與上面的結果橫幅同屬一個語義（見 ActionRow 的註釋）。
                            ActionRow(
                                title = "去智云课堂登录",
                                action = "打开 ›",
                                onClick = onExit,
                            )
                        }
                    }

                    failed != null -> item {
                        ResultBanner(message = failed.error.userText, ok = false)
                    }

                    state is ListState.FirstLoad -> item {
                        HintText("正在登录智云课堂并取课表…")
                    }

                    items.isEmpty() -> item {
                        HintText("本周没有课程安排。")
                    }

                    else -> {
                        // 依日期分組（服務端就是按天給的，這裡保持它的順序）
                        val byDay = items.groupBy { it.day }
                        byDay.forEach { (day, dayItems) ->
                            item(key = "day-$day") {
                                SmallTitle(text = dayText(day))
                            }
                            infoItemsIndexed(dayItems) { c ->
                                InfoItem(
                                    title = c.title,
                                    subtitle = listOfNotNull(
                                        timeRange(c.startAt, c.endAt),
                                        c.room.takeIf { it.isNotBlank() },
                                        c.teacher.takeIf { it.isNotBlank() },
                                    ).joinToString("　"),
                                    // 狀態文字**用服務端給的**（實測「回放」「未开始」…）
                                    trailing = c.displayStatus.takeIf { it != "—" },
                                    trailingColor = if (c.hasReplay) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onBackgroundVariant
                                    },
                                    onClick = if (c.hasReplay) {
                                        { onOpenReplay(c) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }

                item {
                    HintText(
                        "说明：只有学校给了回放的课才能点开播放。" +
                            "播放用的是智云课堂自己的页面（观看进度由学校统计）。",
                    )
                }
            }

            // ---- 看不見的取數器 ------------------------------------------------
            AndroidView(
                modifier = Modifier.size(1.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        layoutParams = ViewGroup.LayoutParams(1, 1)
                        alpha = 0f
                        engine.attach(this)
                    }
                },
            )
            DisposableEffect(Unit) {
                onDispose { engine.detach() }
            }
        }
    }
}

/**
 * `2026-10-12` → 顯示用日期。
 *
 * 目前**原樣顯示**（服務端給的是 `YYYY-MM-DD`，本身就清楚）。
 * 刻意不在這裡做「10月12日 週一」那種美化 ——
 * 那需要解析日期，而解析失敗時就得決定「顯示什麼」，
 * 反而多一個會騙人的地方。美化階段再說。
 */
private fun dayText(day: String): String = day

