package com.muyao.wanzaihyperos.data.account

import com.muyao.wanzaihyperos.session.Cjh52Session
import com.muyao.wanzaihyperos.session.JwSession
import com.muyao.wanzaihyperos.session.PortalSession
import com.muyao.wanzaihyperos.session.SessionState
import com.muyao.wanzaihyperos.session.SztySession
import com.muyao.wanzaihyperos.session.TronclassSession
import com.muyao.wanzaihyperos.session.ZhgySession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * **需要登录的账号源清单**（业主 2026-10-22 v3 #8：设置页把需要登录的账号都列出来）。
 *
 * ## 为什么是六源，不是「一个账号」
 *
 * 全项目只有 [PortalSession] 拿得到学校统一身份认证，但学校各业务系统是**互不相通的登录态**
 * （凭据与寿命各不相同，见各 session 档头）：门户 `ACCESS_TOKEN` 能独立续期、
 * 学在城院是 Keycloak JWT 对（有 refresh）、教务要**学号+密码**且频繁过期、
 * CJH52 是门户签发的 tempcode（还要真实网页激活一次）、智慧公寓的码**只能用户自己粘贴**、
 * 智云课堂的会话在 **WebView 里**（App 既读不到也存不进 [com.muyao.wanzaihyperos.session.SecureStore]）。
 *
 * 合成一行「已登录 / 未登录」就是骗人：他明明教务掉线了，界面却显示已登录。
 * 所以这里**逐源**给状态。
 *
 * ## 四条纪律（写死在这里，界面无从编造）
 *
 * 1. `Unknown`（存储还没读到 / 读失败）**绝不显示成未登录**（理由见 [SessionState] 档头）。
 * 2. **时间戳一律来自存储里真实写入的那一格**；没有就留空或破折号，**绝不拿当前时间凑**。
 *    各源那个时刻的语义不同（认证 / 换票 / 登录 / 取码 / 存码），所以 `timeLabel` 单独存着，
 *    由设置页脚注一次性说清 —— 行内不写标签，写了右侧就放不下（见下条）。
 * 3. **文案长度是量出来的，而且换行点必须落在语义边界上**：03:56 装机图上文字列实测 **243dp**
 *    （屏 411 − 左右 16×2 − 行内距 14×2 − 图标 38 − 间隙 12 − 缝 10 − 状态字 40 − chevron 槽 20，
 *    算式对不齐是正常的，以实测为准），`Type.caption` 11sp 在 1.0 字号一行约 22 字。
 *    ⚠️ 旧规矩「两句拼成一行、合计 ≤ 20 字」是**按 1.0 字号定的**：业主把系统字号调到 1.15，
 *    一行只剩约 19 字 ⇒ 预算内照样折，而折点在句中任意位置，末尾吊一两个字的孤行（v3 #8/#11 第 7 轮）。
 *    现在**两句各占一行**（`SettingsSectionPage` 用换行分），预算改成**逐句 ≤ 12 字**，
 *    由 `AccountSourcesTest.副标题逐句预算守住` 守着 —— 12 字在 1.3 档（约 17 字/行）下也不会自己再折。
 *    02:44 那一版写满两句，五行全被 `maxLines = 2` 无声裁掉后半句；
 *    第 1 轮复审又量到已登录态教务/CJH52 两行 231dp > 226dp 会折成孤行。两次都是「字写多了」。
 * 4. **本文件不出现任何凭据值**；[AccountTarget] 只是「跳到哪个页」。
 *    注意「不代登录」只对**这一屏**成立：教务那一源 App 确实会拿存下的账密替他重登
 *    （`data/jw/JwEngine.kt` 的 `login()` 填 `#yhm`/`#mm` 并点它自己的提交钮，
 *    因为正方没有续期口），所以脚注不许写成「App 不代登录」。
 */
enum class AccountTarget {
    /** 回 App 登录页（统一身份认证）。 */
    PortalLogin,

    /** 「教务系统」原生页（站内填学号+密码）。 */
    JwPage,

    /** 「智慧公寓」闸门页（粘贴入口链接）。 */
    ZhgyPage,

    /** 「数智体育」闸门页（粘贴 token + 数字 userId）。 */
    SztyPage,

    /** 「应用」板块：CJH52 的码与智云课堂的会话都要在那里打开一次网页。 */
    AppsTab,

    /** 没有可跳的入口（界面上这一行**不可点、也不画 chevron**）。 */
    None,
}

enum class AccountStatus {
    /** 存储里有凭据。 */
    LoggedIn,

    /** 存储读成功了，确实没有凭据。 */
    LoggedOut,

    /** 还没读到 / 读失败。**不得当成未登录**。 */
    Unknown,

    /** CJH52 专属：码已经拿到，但还没被真实网页激活过，所以现在**用不了**。 */
    ObtainedNotActivated,

    /** 智云课堂专属：会话在 WebView 里，App 读不到，所以这一源**永远是这个态**（不是「没登录」）。 */
    InWebOnly,
}

/**
 * @param credentialAtMillis 存储里真实写入的时间；`null` = 没写过 / 读不到。
 * @param timeLabel 那个时刻**是什么的时刻**（认证 / 换票 / 登录 / 取码 / 存码）；
 *                  没有可报时刻的源给 `null`。设置页脚注把它们拼成一句说明。
 */
data class AccountSource(
    val id: String,
    val name: String,
    val provides: String,
    val status: AccountStatus,
    val credentialAtMillis: Long?,
    val timeLabel: String?,
    val whereToLogin: String,
    val target: AccountTarget,
) {
    val statusText: String
        get() = when (status) {
            AccountStatus.LoggedIn -> "已登录"
            AccountStatus.LoggedOut -> "未登录"
            AccountStatus.Unknown -> "未读到"
            // 状态位只有约 40dp 的额度，「码有了但没激活」压成三个字，
            // 怎么激活写在副标题里（"在应用开网页激活"）。
            AccountStatus.ObtainedNotActivated -> "未激活"
            AccountStatus.InWebOnly -> "网页内"
        }

    /**
     * 右侧第二行：`08:53` / `昨天` / `4天前` / `10-09`。
     *
     * - 有时刻 ⇒ 只给时刻，**不带语义标签**（标签在脚注里统一解释；带上就把文字列挤到折行）。
     * - 没时刻但**有凭据** ⇒ `—`（这一源确实存着东西，只是没记下时刻，比如老版本存的智慧公寓链接）。
     * - 没时刻也**没凭据** ⇒ 空串：上一行已经写着「未登录」/「未读到」，再补一句就是同一件事说两遍，
     *   还会和副标题尾字粘成一句（03:04 模拟器像素：「…无单独登录口 尚无记录」）。
     */
    fun timeText(
        nowMillis: Long = System.currentTimeMillis(),
        nowZone: ZoneId = ZoneId.systemDefault(),
    ): String = when {
        credentialAtMillis != null -> relativeMoment(credentialAtMillis, nowMillis, nowZone)
        status == AccountStatus.LoggedIn || status == AccountStatus.ObtainedNotActivated -> "—"
        else -> ""
    }
}

/** 今天给到点、昨天/前天给词、一周内给「N天前」、再往外给日期（**不编「很久以前」**）。 */
internal fun relativeMoment(millis: Long, nowMillis: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val back = ChronoUnit.DAYS.between(at.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
    return when {
        back <= 0L -> at.format(HOUR_MINUTE)
        back == 1L -> "昨天"
        back == 2L -> "前天"
        back in 3..6 -> "${back}天前"
        else -> at.format(DAY_FORMAT)
    }
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun statusOf(state: SessionState): AccountStatus = when (state) {
    SessionState.LoggedIn -> AccountStatus.LoggedIn
    SessionState.LoggedOut -> AccountStatus.LoggedOut
    SessionState.Unknown -> AccountStatus.Unknown
}

/**
 * 读一遍六个源，产出设置页要画的那六行。
 *
 * ★ 一进来先把五个会话都 `ensureLoaded()` 一次：[com.muyao.wanzaihyperos.session.StoredSession.state]
 * 是**故意不触发加载**的（它把「还没读」和「读了没有」分成两件事），
 * 不预热就是把每次冷读的第一屏都画成「未读到」—— 那是工具自己的产物，不是他的登录态。
 * 读失败由 `StoredSession` 降级成 `Unknown`，这里不吞也不抛。
 *
 * 首次加载会**同步碰加密存储**，所以这个函数必须在 IO 线程调
 * （设置页 `withContext(Dispatchers.IO)` 里算好再上屏，见 `SettingsSectionPage`）。
 */
fun snapshotAccountSources(
    portal: PortalSession,
    tronclass: TronclassSession,
    jw: JwSession,
    cjh52: Cjh52Session,
    zhgy: ZhgySession,
    szty: SztySession,
): List<AccountSource> {
    portal.ensureLoaded()
    tronclass.ensureLoaded()
    jw.ensureLoaded()
    cjh52.ensureLoaded()
    zhgy.ensureLoaded()
    szty.ensureLoaded()
    val cjh52Status = when {
        statusOf(cjh52.state) != AccountStatus.LoggedIn -> statusOf(cjh52.state)
        cjh52.isUsable -> AccountStatus.LoggedIn
        else -> AccountStatus.ObtainedNotActivated
    }
    return listOf(
        AccountSource(
            id = "portal",
            name = "统一身份认证 · 门户",
            provides = "应用、待办、消息、账单",
            status = statusOf(portal.state),
            credentialAtMillis = portal.savedAtMillis(),
            timeLabel = "认证",
            whereToLogin = "在登录页",
            target = AccountTarget.PortalLogin,
        ),
        AccountSource(
            id = "tronclass",
            name = "学在城院（TronClass）",
            provides = "课程、签到、作业",
            status = statusOf(tronclass.state),
            credentialAtMillis = tronclass.savedAtMillis(),
            timeLabel = "换票",
            whereToLogin = "掉线就重登门户",
            target = AccountTarget.None,
        ),
        AccountSource(
            id = "jw",
            name = "教务系统",
            provides = "课表、考试、成绩",
            status = statusOf(jw.state),
            credentialAtMillis = jw.lastLoginMillis(),
            timeLabel = "登录",
            whereToLogin = "教务页填账密",
            target = AccountTarget.JwPage,
        ),
        AccountSource(
            id = "cjh52",
            name = "场景管理（CJH52）",
            provides = "讲座、通知、成长",
            status = cjh52Status,
            credentialAtMillis = cjh52.obtainedAtMillis(),
            timeLabel = "取码",
            whereToLogin = "在应用开网页激活",
            target = AccountTarget.AppsTab,
        ),
        AccountSource(
            id = "zhgy",
            name = "智慧公寓",
            provides = "报修、内务分、缴费",
            status = statusOf(zhgy.state),
            credentialAtMillis = zhgy.obtainedAtMillis(),
            timeLabel = "存码",
            whereToLogin = "只能自己粘链接",
            target = AccountTarget.ZhgyPage,
        ),
        // 数智体育：**没有可用的账密登录**（真学号+真密码与乱写密码回一字不差的同一条错，
        // 它把账密丢给学校统一身份认证而那一跳一律拒），所以走「粘 token + 数字 userId」。
        // 时刻标签写「粘贴」——它不是我们换来的票，是用户手动给的。
        AccountSource(
            id = "szty",
            name = "数智体育",
            provides = "体测、校园跑、签到",
            status = statusOf(szty.state),
            credentialAtMillis = szty.savedAtMillis(),
            timeLabel = "粘贴",
            whereToLogin = "粘 token 和编号",
            target = AccountTarget.SztyPage,
        ),
        // 智云课堂：`data/zy/ZyRepository.kt` 档头写明它「沿用统一身份认证会话」，
        // 但那枚 cookie 在 WebView 里（`ZyEngine` 读 `localStorage.yjUserInfo`），
        // 设置页这一屏读不到 ⇒ 状态**只能是 `InWebOnly`**，给它 `LoggedOut` 就是替学校撒谎。
        //
        // ⚠️ 文案不许承诺「在那里能登录」：`ui/zy/ZyPage.kt` 那颗「去智云课堂登录」
        //   的 `onClick = onExit` 只是返回上一页，App 内目前**没有**通向它网页登录口的路
        //   （缺口记在 `review/REPORT.md` 的 v3 #8 一节，与「设置页缺退出登录口」同批；
        //   那份清单由并行的另一个会话在写，不往它里面塞字）。
        AccountSource(
            id = "zy",
            name = "智云课堂",
            provides = "本周课表、回放",
            status = AccountStatus.InWebOnly,
            credentialAtMillis = null,
            timeLabel = null,
            whereToLogin = "在应用里打开它",
            target = AccountTarget.AppsTab,
        ),
    )
}

/**
 * 「账号」那一组下面的说明。
 *
 * ★ 单独成函数而不是内联在 composable 里：这一句在 v3 #8 第 1 轮**踩过一次红线**
 *   （写成「App 不代填凭据、不代登录」，而教务那一源 App 确实会拿存下的账密替他重登）。
 *   踩过谎的文案必须有测试守着，所以它离开 UI 层、进数据层，由 `AccountSourcesTest` 断言。
 *
 * 语义标签（认证 / 换票 / 登录 / 取码 / 存码）从快照里拼出来，加了源就不会漏解释。
 */
fun accountFootnoteText(sources: List<AccountSource>): String {
    val labels = sources.mapNotNull { it.timeLabel }.joinToString(" / ")
    return buildString {
        if (labels.isNotEmpty()) append("右侧时间是最近一次拿到该源凭据的时刻（").append(labels).append("）。")
        append("教务例外：学校那边没有续期，App 会用加密存储里的账密替你重新登录。")
        append("智云课堂的会话在网页里，这一屏读不到它。")
    }
}
