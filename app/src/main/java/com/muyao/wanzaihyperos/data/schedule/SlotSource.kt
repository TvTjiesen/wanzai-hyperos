package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot

/**
 * 课表这一屏**该画哪一份的槽位** —— 全工程唯一的一条规则。
 *
 * 为什么要有这个文件：同一条"教务那份优先、没取到才退学在城院"的判断，
 * 在 [com.muyao.wanzaihyperos.ui.main.pages.TimetableSectionPage] 里抄了两遍
 * （周条一遍、正文一遍），而业主三次报同一个闪（2026-10-20、2026-10-20、2026-10-22 v3 清单 #4）。
 * 前两次的修法都是"给兜底那份加一条说明"，漏掉的是**教务还在爬的时候根本不该画另一份**。
 *
 * ★ 第 1 轮审计（A-3）抓到：抽了函数之后首页与外观预览**还各写一遍**且没有 WAIT ——
 *   冷启动第一帧就落在首页。现在四个调用点（首页 / 课表周条 / 课表正文 / 外观预览）
 *   都只调 [pickSlots]，并由 `SlotSourceTest` 里那条源码扫描钉住。
 * ★ 第 3 轮审计抓到更细的一层：`TRONCLASS` 底下压着**四个**不同子态（从没取过 / 这次没取到 /
 *   登录失效 / 真的这周没课），把它们都说成「教务这周没给课」就是替学校下它没下过的结论。
 *   所以界面措辞只能读 [JwVerdict]，不许从 [SlotSource] 反推。
 */
enum class SlotSource {
    /** 教务系统那份（权威：带老师与教室）。 */
    JIAOWU,

    /** 学在城院那份。只在教务**有结论且没货**、或这一屏根本没法等教务时才允许走到这里。 */
    TRONCLASS,

    /** 真有一趟教务爬取在跑 ⇒ 两份都不画。画错课比空一屏更糟：他会以为要去上课。 */
    WAIT,

    /** 两份都没有。 */
    NONE,
}

/**
 * 教务这一份**走到哪一步了** —— 界面上那一句实话只能由它决定。
 *
 * 为什么要有它（v3 #4 第 3 轮必修 1）：以前页面只知道"该画哪一份"，
 * 于是把"没结论"和"结论是没有课"混成同一句话。红线：绝不编造学校给不出的数据。
 */
enum class JwVerdict {
    /** 这台机子上还没人发起过教务爬取，也没有磁盘快照 —— 这不是"正在取"，也不是"没课"。 */
    NOT_STARTED,

    /** 正有一趟爬取在跑（唯一正面证据 = `JwTimetableRepository.isCrawling` 或 `meta.refreshing`）。 */
    IN_FLIGHT,

    /** 取到了、里面有课。 */
    HAS_SLOTS,

    /** 取到了、学校这份确实是空的。 */
    SETTLED_EMPTY,

    /** 取过了但没结论：失败 / 登录失效 / 读到空表格。 */
    NO_CONCLUSION,
}

/**
 * [JwVerdict] 的唯一算法 —— 页面不许各自判第二遍。
 *
 * ⚠️ 不许再用 `state is ListState.FirstLoad` 当"正在取"：`startRefresh()` 在没有内容时
 *   **原样返回 FirstLoad**（`ListState.kt:130`），所以 FirstLoad 也可能是"还没人发起过"。
 *   那种时候写「正在取教务课表」就是界面在说数据证明不了的话（第 2 轮必修 4）。
 */
fun jwVerdictOf(state: ListState<*>, crawling: Boolean, hasSlots: Boolean): JwVerdict {
    if (hasSlots) return JwVerdict.HAS_SLOTS
    val refreshing = when (state) {
        is ListState.Content -> state.meta.refreshing
        is ListState.Empty -> state.meta.refreshing
        is ListState.Failed -> state.meta.refreshing
        else -> false
    }
    return when {
        refreshing || crawling -> JwVerdict.IN_FLIGHT
        state is ListState.FirstLoad -> JwVerdict.NOT_STARTED
        state is ListState.Content || state is ListState.Empty -> JwVerdict.SETTLED_EMPTY
        else -> JwVerdict.NO_CONCLUSION   // Failed / SessionExpired
    }
}

/**
 * [pickSlotSource] 加上"那就画这一份的格子" —— 页面只该调这一个函数。
 *
 * WAIT / NONE 返回的 [SlotPick.slots] 是**空的**：不是"今天没课"，是"这一屏此刻不该画格子"，
 * 界面必须按 [SlotPick.jwVerdict] 自己写一句实话，不许把空列表当"没课"渲染。
 */
fun pickSlots(
    jwSlots: List<TronScheduleSlot>,
    jwVerdict: JwVerdict,
    tronSlots: List<TronScheduleSlot>,
): SlotPick {
    val source = when {
        jwSlots.isNotEmpty() -> SlotSource.JIAOWU
        jwVerdict == JwVerdict.IN_FLIGHT -> SlotSource.WAIT
        jwVerdict == JwVerdict.SETTLED_EMPTY -> SlotSource.JIAOWU
        tronSlots.isNotEmpty() -> SlotSource.TRONCLASS
        else -> SlotSource.NONE
    }
    return SlotPick(
        source = source,
        slots = when (source) {
            SlotSource.JIAOWU -> jwSlots
            SlotSource.TRONCLASS -> tronSlots
            SlotSource.WAIT, SlotSource.NONE -> emptyList()
        },
        jwVerdict = jwVerdict,
    )
}

/** 一次选择的结论。 */
data class SlotPick(
    val source: SlotSource,
    val slots: List<TronScheduleSlot>,
    /** 界面措辞用这个，别从 [source] 反推（见 [JwVerdict]）。 */
    val jwVerdict: JwVerdict,
) {
    /** 画的是教务那一份。 */
    val fromJiaowu: Boolean get() = source == SlotSource.JIAOWU

    /** 退到学在城院那份 —— 必须标来源，不标就是替教务撒谎。 */
    val fromTronclass: Boolean get() = source == SlotSource.TRONCLASS

    /** 此刻两份都不该画（真有一趟爬取在跑）。 */
    val isWaiting: Boolean get() = source == SlotSource.WAIT

    /** 教务还没取过 ⇒ 界面要照实说"这台机子上还没取过"，不许说"正在取"、也不许说"没课"。 */
    val jwNeverFetched: Boolean get() = jwVerdict == JwVerdict.NOT_STARTED
}

/**
 * 教务这一屏的实话句子 —— 四个子态一句一句分开说（第 3 轮必修 1）。
 *
 * 放在数据层是因为首页、外观预览两处都要说同一句；写两遍就会分叉。
 */
fun jwVerdictSentence(v: JwVerdict): String = when (v) {
    JwVerdict.IN_FLIGHT -> "正在取教务课表…"
    JwVerdict.NOT_STARTED -> "教务那份这台机子上还没取过"
    JwVerdict.NO_CONCLUSION -> "教务那份这次没取到"
    JwVerdict.SETTLED_EMPTY -> "教务那份这周没给课"
    JwVerdict.HAS_SLOTS -> "用的是教务那份"
}
