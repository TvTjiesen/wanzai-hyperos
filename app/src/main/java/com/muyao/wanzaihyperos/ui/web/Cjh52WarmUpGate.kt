package com.muyao.wanzaihyperos.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.muyao.wanzaihyperos.session.Cjh52Session

/**
 * CJH52 的**會話閘門**：所有原生 cjh52 頁面都應該包在它裡面。
 *
 * ### 它解決什麼
 *
 * 門戶簽發的 tempcode **直接當 Bearer 用會被拒**（HTTP 401）。
 * 只有「被真實瀏覽器載入過一次」的碼才可用 —— 這是實測結論，
 * 完整證據鏈見 [Cjh52WarmUp] 的類註釋與 `docs/B1-WALKTHROUGH.md`。
 *
 * 這個閘門把那段「先預熱、再放行」的樣板收在**一處**，
 * 讓每個 cjh52 原生頁不必各寫一遍（寫六遍就會有六處漏掉預熱的機會）。
 *
 * ### ★ 一次預熱，服務全部 cjh52 功能
 *
 * 預熱拿到的 token 是**整個 cjh52 的會話令牌**，不是某個接口專用的。
 * 實測：同一枚 token 先後用於 `get_user_by_token` 與 `get_class_info_by_uid`
 * 都是 `HTTP 200`。所以：
 *
 * - 已經有 token → **直接放行，不預熱**（連課程頁都不需要再開 WebView）；
 * - 沒有 token（首次、或 token 已失效被清掉）→ 才預熱一次。
 *
 * 這也是它比「每個功能各開一次 WebView」好得多的原因。
 *
 * ### 失敗時的態度
 *
 * 預熱失敗（取不到碼、逾時、載入錯誤）**也要放行**。
 * 理由：讓使用者看到真正的錯誤與出口（各頁都有自己的「改用網頁版」），
 * 而不是永遠停在「正在準備」。**卡住是最糟的結果。**
 *
 * @param session cjh52 會話。已有 token 就不預熱。
 * @param issueUrl 取一枚新碼並拼成帶碼的頁面 URL；取不到回 null。
 * @param content 頁面內容。`ready=true` 表示「可以取數了」。
 *   為 false 時頁面應顯示自己的載入態（**不要去請求**，那一定 401）。
 */
@Composable
fun Cjh52WarmUpGate(
    session: Cjh52Session,
    issueUrl: suspend () -> String?,
    content: @Composable (ready: Boolean) -> Unit,
) {
    // ★ 判斷用 `isUsable`（有碼**且已激活**），不是 `hasToken`。
    //
    //   這個區分是必要的，而且實際踩過一次坑：
    //   先前版本在別處把「門戶剛發、還沒走過瀏覽器的碼」存進會話，
    //   閘門看到 hasToken=true 就放行 → 原生請求必定 401。
    //   未激活的碼與已激活的碼在存儲裡長得一樣，只有這個標記能分開它們。
    var ready by remember { mutableStateOf(session.isUsable) }
    var warmUpUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (ready) return@LaunchedEffect
        val url = runCatching { issueUrl() }.getOrNull()
        if (url == null) {
            // 取不到碼：放行讓頁面顯示錯誤與出口，不卡在「正在準備」。
            ready = true
        } else {
            warmUpUrl = url
        }
    }

    if (!ready && warmUpUrl != null) {
        Cjh52WarmUp(
            url = warmUpUrl!!,
            onToken = { token ->
                // token 是憑據：只交給會話（加密存儲），這裡不打印。
                //
                // ★ 兩步都要：save 記下這枚碼，markActivated 記下
                //   「它已被真實瀏覽器載入過」。少了後者，閘門下次仍會認為不可用
                //   （而這是**正確**的保守行為：寧可多預熱一次，也不要發出必定 401 的請求）。
                runCatching {
                    session.save(token, System.currentTimeMillis())
                    session.markActivated()
                }
                ready = true
            },
            onFailure = { ready = true },
        )
    }

    content(ready)
}
