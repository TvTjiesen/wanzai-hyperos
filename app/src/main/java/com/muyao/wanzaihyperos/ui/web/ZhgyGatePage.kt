package com.muyao.wanzaihyperos.ui.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.muyao.wanzaihyperos.session.ZhgySession
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField

/**
 * **智慧公寓的入口閘門**：有憑據就開頁面，沒有就請使用者貼一次連結。
 *
 * ## 為什麼需要這一層（而不是直接開網頁）
 *
 * 智慧公寓真正的入口是 `/mob/zuccindex.html?code=<JWT>`，
 * 而那段 `code` **只能由使用者自己取得**：門戶給的入口是釘釘免登
 *（`/mob/zuccding/auth`），授權碼由釘釘容器簽發，瀏覽器裡拿不到。
 * 完整證據見 `docs/B17-WALKTHROUGH.md` 與 `docs/B17-ZHGY-LINK-PROBE.txt`。
 *
 * 所以這一頁做的事是：
 *
 * ```
 * 沒設過 → 請使用者貼上「帶 code 的連結」
 * 設過了 → 直接複用 [WebShellPage] 載入（不再經過釘釘那條）
 * ```
 *
 * ## ★ 為什麼「貼一次就能一直用」
 *
 * 真機實測：那段 `code` **可重複使用** ——
 * 用 CDP 清掉 cookie 再用**同一個** code 開，照樣進得去並重新種下 `ihome`。
 * 所以不必每次要使用者貼；存進加密存儲即可。
 *
 * ## 只抽 `code`，不存整條連結
 *
 * 連結裡除 `code` 外可能還有帶時效或追蹤用途的參數，
 * 留著沒好處。見 [ZhgySession.extractCode]。
 *
 * ## 憑據紀律
 *
 * 存進 [ZhgySession] 之後，日誌裡只會出現長度，不會出現值。
 */
@Composable
fun ZhgyGatePage(
    session: ZhgySession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 與 Cjh52WarmUpGate 同一種寫法：用 remember 記住「現在有沒有憑據」，
    // 存進去之後就地切換成開頁面（不必導航到另一個路由）。
    var hasCode by remember { mutableStateOf(session.hasCode) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val url = remember(hasCode) { if (hasCode) session.entryUrl() else null }
    if (url != null) {
        WebShellPage(
            title = "智慧公寓",
            url = url,
            onExit = onExit,
            modifier = modifier,
        )
        return
    }

    // ★ 用共用的 SecondaryScaffold（不透明底色 + 安全區），
    //   不要自己拼 —— 真機上第一版就是因為自己拼而透出下面的首頁，
    //   且返回鍵被狀態列蓋住。同一個坑在 B16 也踩過，所以它已抽到設計層。
    SecondaryScaffold(
        title = "智慧公寓",
        subtitle = "需要一次性的入口链接",
        onBack = onExit,
        modifier = modifier,
    ) { innerPadding ->
        AppList(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            item {
                HintText(
                    "这个系统的登录由「钉钉」签发授权码，本 App 拿不到，所以需要你提供一次入口链接。\n" +
                        "在能正常打开智慧公寓的地方，把网址（含 code= 的那一串）贴到下面。",
                )
            }

            error?.let { msg -> item { ResultBanner(message = msg, ok = false) } }

            item {
                TextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = "贴上智慧公寓的网址（含 code=）",
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Button(
                    onClick = {
                        val code = ZhgySession.extractCode(input)
                        if (code.isNullOrBlank()) {
                            error = "没认出 code。请贴完整网址（里面要有 code=… 那一段）。"
                        } else {
                            session.save(code, System.currentTimeMillis())
                            // 輸入框要清掉 —— 畫面上不該繼續留著憑據。
                            input = ""
                            error = null
                            hasCode = true
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("使用这个链接") }
            }

            item {
                HintText(
                    "说明：只会从你贴的网址里抽出 code 那一段，存在手机的加密存储里；" +
                        "其余参数不会保留。这个码可以重复使用，设置一次即可。",
                )
            }
            item {
                HintText(
                    "为什么不能自动登录：该入口需要钉钉容器签发的授权码，网页环境取不到；" +
                        "门户给的那条路径在没有钉钉时必定显示「授权码已过期」。",
                )
            }
        }
    }
}
