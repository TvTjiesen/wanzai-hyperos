package com.muyao.wanzaihyperos.ui.zy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.ui.web.WebShellPage

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
 */
@Composable
fun ZyReplayPage(
    url: String,
    title: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WebShellPage(
        title = title,
        url = url,
        onExit = onExit,
        modifier = modifier.fillMaxSize(),
    )
}
