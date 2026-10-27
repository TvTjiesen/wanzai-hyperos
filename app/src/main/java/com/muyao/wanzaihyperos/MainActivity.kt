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

    /**
     * **只在可调试包**生效：`adb shell am start -n com.muyao.wanzaihyperos/.MainActivity --ei debug_section 1`
     * 让界面直接落在某个板块（0 首页 / 1 课程表 / 2 消息 / 3 应用 / 4 设置）。
     *
     * ## 为什么加这个
     *
     * 真机验收要靠 `uiautomator dump` 拿控件中心点才能点底栏 —— 而这台 HyperOS 3
     * 对这个 Compose 界面**会返回 `null root node`**（实测两次，dump 出来 0 个节点）。
     * 语义树一失效，剩下的唯一"坐标来源"就是照缩放图用眼睛估，那正是最硬的那条禁令。
     * 所以把导航做成**不需要坐标的一条命令**：换页、换板块、复现"每次进来都重新加载"
     * 这类只有重复进入才看得到的现象，都靠它，不靠手指。
     *
     * ⚠️ release 包读不到（`FLAG_DEBUGGABLE` 关不掉就没这个口子），正式使用者不会碰到；
     *    它也不写任何持久化状态 —— 只改内存里的 `uiState.selectedTab`。
     */
    private val debugSection = mutableStateOf<DebugSection?>(null)

    // #42 前半截：投递对象必须每次都不相等，否则同值重放是空操作。
    // 它的语义是「这一次投递要去哪个板块」，不是「当前在哪个板块」。
    // 后半截在 AndroidManifest.xml —— MainActivity 没有 launchMode 时，
    // am start 与外部 deep link 都会再叠一个实例，onNewIntent 根本不跑。
    private val debugSectionInbox = DebugSectionInbox()

    private fun readDebugSection(it: Intent?): DebugSection? {
        val debuggable =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return null
        return debugSectionInbox.deliver(it?.getIntExtra("debug_section", -1) ?: -1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ★ 偏好必须在**第一帧之前**读进来（`UiPrefs.load` 幂等，WanzaiRoot 里那次照旧无害）。
        //   放在 `LaunchedEffect` 里等于"第一帧用默认值，第二帧才用他的值"——
        //   真机量过：課表那一屏冷启动第一帧会闪一句「还没取到教学周」，
        //   因为周次锚点还躺在盘里没读出来。他要的是"进来就是那张课表"，不是闪一下。
        com.muyao.wanzaihyperos.data.UiPrefs.load(this)
        // ★ 独立审计（v3 #12 第 2 轮 P2#6）：把**运行期那两份**也在这里一起推平。
        //   以前放在 `WanzaiRoot` 的 `LaunchedEffect(app)` 里 ⇒ 回填**晚于第一帧**：
        //   存的是 frost/off 时，冷启动一定先按默认 LIQUID 画一帧、下一帧才跳材质，
        //   正好是"进来闪一下"那一类。盘在这里已经读完了，紧跟着推是同一帧内的事。
        //   （`WanzaiRoot` 里那两条保留：未登录路径与登录完成后的刷新还要靠它。）
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setReduceTransparency(
            com.muyao.wanzaihyperos.data.UiPrefs.reduceTransparency,
        )
        com.muyao.wanzaihyperos.ui.design.MotionPrefs.setRefractsGlass(
            com.muyao.wanzaihyperos.data.UiPrefs.glassMode == "liquid",
        )
        // 冷啟動時就帶著連結（使用者從別處點連結把 App 叫起來）。
        incomingLink.value = extractZhgyLink(intent)
        debugSection.value = readDebugSection(intent)
        setContent {
            WanzaiApp(
                incomingZhgyLink = incomingLink.value,
                onIncomingZhgyLinkConsumed = { incomingLink.value = null },
                debugSection = debugSection.value,
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
        readDebugSection(intent)?.let { debugSection.value = it }
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

/**
 * 一次 debug_section 投递（#42）。
 *
 * 带 seq 是为了让每一次投递都互不相等：Compose 的 LaunchedEffect 以它为键，
 * 先手动切走再要求回到同一板块时必须能再触发一次。只带 Int 时同值写入不会让
 * MutableState 换引用，副作用不重跑，界面一动不动 —— 那正是 #42 的另一半。
 */
data class DebugSection(val section: Int, val seq: Long)

/** 发号器：只增不减，进程内唯一（见 [DebugSection]）。 */
class DebugSectionInbox {

    private var lastSeq = 0L

    /** 非调试包、越界值一律回 null；0..4 之外不接受（与 MainSection 的 0..4 对齐）。 */
    fun deliver(raw: Int): DebugSection? {
        if (raw !in 0..4) return null
        lastSeq += 1
        return DebugSection(raw, lastSeq)
    }
}
