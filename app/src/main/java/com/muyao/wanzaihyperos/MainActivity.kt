package com.muyao.wanzaihyperos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.muyao.wanzaihyperos.session.ZhgySession

class MainActivity : ComponentActivity() {

    /**
     * 從外部 intent 帶進來的智慧公寓連結（B17）。
     *
     * ## 為什麼需要它
     *
     * 智慧公寓的入口 `zhgy.hzcu.edu.cn/mob/zuccindex.html?code=…` 只能在**別的 App**
     * 裡拿到（釘釘／瀏覽器／聊天），而 `code` 是**個人憑證**，只能由使用者帶過來。
     *
     * 有了 manifest 的 deep link 與這個欄位，使用者在任何地方點那個連結、
     * 或把它「分享」到本 App，就**直接落到智慧公寓並自動填好** ——
     * 不必手動複製再貼。
     *
     * ⚠️ 這裡只存「要交給閘門頁的那串文字」，**不解析、不落盤**；
     *   真的抽出 `code` 並存進加密存儲是 `ZhgySession` 的事。
     *   這樣「憑證只經過一個地方」這條紀律才守得住。
     */
    private val incomingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 冷啟動時就帶著連結（使用者從別處點連結把 App 叫起來）。
        incomingLink.value = extractZhgyLink(intent)
        setContent {
            WanzaiApp(
                incomingZhgyLink = incomingLink.value,
                onIncomingZhgyLinkConsumed = { incomingLink.value = null },
            )
        }
    }

    /**
     * App 已經活著時又收到連結。
     *
     * ⚠️ **必須覆寫**，否則「App 開著時點連結」不會有反應 ——
     * 那正是使用者最容易遇到的情形（他剛從別處複製完就切回來）。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractZhgyLink(intent)?.let { incomingLink.value = it }
    }

    /**
     * 從 intent 取出智慧公寓的連結。
     *
     * 只認「VIEW + http(s) + 我們的 host + 有 code」這一種形狀；
     * 其餘一律回 null（**不當通用瀏覽器** ——
     * 別人用奇怪 scheme 叫起來時不該有反應）。
     *
     * ⚠️ 沒有 `code` 的連結也**不收**：開出來只會是錯誤頁，
     * 收下它等於讓使用者看到一個壞頁面還以為 App 壞了。
     */
    private fun extractZhgyLink(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (data.scheme != "http" && data.scheme != "https") return null
        if (data.host != "zhgy.hzcu.edu.cn") return null
        val code = data.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return null
        return ZhgySession.buildEntryUrl(code)
    }
}
