package com.muyao.wanzaihyperos.ui.zy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.web.WebShellPage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **回放播放頁**：用智雲課堂自己的播放頁。
 *
 * ## ★ 為什麼播放交給 WebView，而清單是原生
 *
 * 業主問過「播放帮我跳转到第三方浏览器行不行」。
 * 我實測了那條路（見 `docs/B19-WALKTHROUGH.md`）：
 *
 * - **技術上可行** —— 影片檔在 `resourcese.hzcu.edu.cn`，
 *   `curl` **不帶任何 cookie** 就能下載（`HTTP 206`、`video/mp4`），
 *   所以第三方瀏覽器不會被要求重登。
 * - **但不是最優**，兩個實質代價：
 *   1. **離開 App** —— 看完要手動切回來；
 *   2. **觀看進度不會上報** —— 那頁會呼叫
 *      `courseapi/v2/course/record-create`（記錄觀看）與
 *      `behavior/suspend`（暫停上報），**那是學校認定學習進度的依據**。
 *      自己在外面播，等於「看了等於沒看」。
 *
 * 也評估過**自己寫播放器**：影片 4.5 GB / 12436 秒，要處理邊下邊播、
 * 斷線重連、進度上報，還要重做 PPT 同步與 AI 筆記 —— 收益/風險不成比例。
 *
 * → 所以：**清單原生、播放用它的頁面**。這是收益與風險最平衡的切法。
 *
 * ## 與普通 WebShell 的差別
 *
 * 它需要**帶著登入態**（那批 `.hzcu.edu.cn` 域 cookie）。
 * 幸好那是 WebView 全域共享的 —— 前面 [ZyPage] 那個隱藏 WebView
 * 已經把會話建立好了，這裡直接用即可。
 *
 * ## ★ 未匹配兜底頁（v2.1 稿）
 *
 * 稿 `zyReplay.png` 畫的**不是播放器**，而是「回放課程未匹配」這一態：
 *
 * > ZHIYUN / REPLAY ／ 回放課程未匹配 ／ 不以另一門課代替未知課程。 ／ [選擇智雲課程]
 *
 * 這一態原本在 App 裡**根本進不來**：`title` 為空時照樣把空字串拼進副標題，
 * 然後照樣開 WebView，使用者看到的是智雲自己的 404 或空白頁 ——
 * 比一句「未匹配」更讓人心裡沒底。
 *
 * 所以規則是：**拿不到課名就當未匹配**（位址裡的 course_id/sub_id 對不出課名，
 * 表示那場回放不屬於本週清單裡的任何一門課）。此時不猜、不用別門課頂替，
 * 只把選擇權交回使用者。
 */
@Composable
fun ZyReplayPage(
    url: String,
    title: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 未匹配時那顆「选择智云课程」的落點 —— 回課程清單重挑。
     *
     * 為 null 時不畫那顆鈕（例如深鏈直達、堆疊裡沒有清單頁）。
     */
    onPickCourse: (() -> Unit)? = null,
) {
    // 課名空白 = 這次回放對不出是哪門課。不要拿「智雲官方回放」硬頂上去。
    if (title.isBlank() || url.isBlank()) {
        ReplayUnmatched(onExit = onExit, onPickCourse = onPickCourse, modifier = modifier)
        return
    }

    // v2.1 稿：顶栏「智云官方回放」，课名进副标题（「设计史 · 官方播放出口」）。
    // 地址里只有 course_id/sub_id，课名是 WanzaiRoot 传进来的 `title`；
    // 别的 Web 壳仍由 WebShellPage 用当前地址的主机名作副标题。
    val replaySubtitle = if (title.isBlank()) "官方播放出口" else title + " · 官方播放出口"
    WebShellPage(
        title = "智云官方回放",
        url = url,
        onExit = onExit,
        subtitle = replaySubtitle,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * 「回放課程未匹配」—— 稿 `zyReplay.png` 那一頁。
 *
 * 只有三行字和一顆鈕，但每一句都在擋一個具體的壞習慣：
 * 「不以另一門課代替未知課程」寫在畫面正中間，
 * 是給**後來的實作者**看的：別為了「讓頁面看起來有內容」而拿鄰近的課頂替。
 */
@Composable
private fun ReplayUnmatched(
    onExit: () -> Unit,
    onPickCourse: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SecondaryScaffold(
        title = "智云官方回放",
        subtitle = "未匹配到课程",
        onBack = onExit,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ReplayHero(
                eyebrow = "ZHIYUN / REPLAY",
                title = "回放课程未匹配",
                sub = "不以另一门课代替未知课程。",
            )
            if (onPickCourse != null) {
                Spacer(Modifier.height(Dims.sectionGap))
                Button(
                    onClick = onPickCourse,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("选择智云课程") }
            }
        }
    }
}

/**
 * 頁頭三行（.eyebrow / .hero-title / .muted）。
 *
 * 與 [ZyPage] 裡那份同形：字號換算見那裡的註釋
 * （`.eyebrow{11.29px}`⇒`Type.caption`、`.hero-title{29.35px}`⇒`Type.displayTitle`）。
 * 只有二十來行且共用位置在 `ui/design` / `ui/common`（不歸本輪可寫範圍），
 * 所以先各自持有一份。
 */
@Composable
private fun ReplayHero(eyebrow: String, title: String, sub: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.padding(bottom = Dims.tight)) {
        Text(
            text = eyebrow,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            letterSpacing = 0.10.em,
            color = colors.onSurfaceVariantSummary,
            style = TextStyle(fontFeatureSettings = Type.tabular),
        )
        Text(
            text = title,
            fontSize = Type.displayTitle,
            fontWeight = Type.weightTitle,
            letterSpacing = (-0.02).em,
            lineHeight = Type.lineDisplayTitle,
            color = colors.onBackground,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = sub,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
