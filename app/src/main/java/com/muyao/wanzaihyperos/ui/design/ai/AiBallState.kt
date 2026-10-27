package com.muyao.wanzaihyperos.ui.design.ai

/**
 * AI 小球要表达的**十二件事**（不是十二种动画，是十二种事实）。
 *
 * 每一个都对应开源引擎 `grok-ball` 里一个**已有的表情 id**（MIT，github.com/tycoding/grok-ball）。
 * 括号里是引擎 id —— 矢量路径由 `tools/gen_ai_ball_glyphs.py` 从真实渲染 DOM 烘进来，
 * 不是照截图描的。
 *
 * 生命周期： [Sleep] 00 · [WakeUp] 01 · [Idle] 02 · [Curious] 03
 * 工作状态： [Thinking] 30 · [Busy] 32 · [Done] 33 · [Error] 34 · [Waiting] 35 · [Loading] 36 · [Searching] 40
 * 注意力：   [Focus] 16
 *
 * ## 取值口径（不许为了「看起来有意思」自己编）
 *
 * 每个页面只取一个状态，取值来自 `ListState`、`TodayPlan` 或已经算好的计划对象。
 *
 * - [Sleep]     深夜且今天没有课、没有待办（23:00–06:00）—— 真的没事可做才睡。
 * - [WakeUp]    早上第一次进首页（06:00–08:00）—— 「刚醒」，只此一次。
 * - [Idle]      数据已经拿到且为空。
 * - [Curious]   从「首次加载」刚刚变成「有内容」的那一秒 —— 它看见东西了。
 * - [Focus]     24 小时内有一件待办到期。
 * - [Thinking]  有一次取数正在路上。
 * - [Busy]      有一节课正在进行（`FocusNow.ongoing`）。
 * - [Done]      今天的课全上完了。
 * - [Error]     这一页真的失败了（`ListState.Failed` / `ListState.SessionExpired`）。
 * - [Waiting]   有一张签到在等你答（`TronRollcall.needsCamera && unanswered`）—— 它比你先知道。
 * - [Loading]   首次加载，或数量类字段是 `CountValue.Unknown` —— **不知道也必须有一张脸**
 *               （`data/ListState.kt` 第一节的三条红线）。
 * - [Searching] 资源库正在检索（教务 / 门户未读）。
 *
 * ## 刻意不用的四张脸
 *
 * 引擎还有 `13 惊讶` / `21 生气` / `17 慌张` / `12 失落`。不用，因为它们都是
 * 「AI 对你」的表情 —— 登录失效时给一张生气脸，等于 App 在骂用户。**错误只用 [Error]**。
 *
 * ⚠ [Idle] 与引擎的 `02`、[Loading] 与 `36` 是同一张脸 —— 已核：这两个表情的矢量路径
 * 逐字节相同（`tools/gen_ai_ball_glyphs.py` 打印的两条 `pts` 一致），只在 `y` 平移上差
 * 0.07 个球径单位。所以 [Loading] 必须靠「眼睛扫一次」与 [Idle] 区分，不能靠静止帧。
 */
enum class AiBallState {
    Sleep,
    WakeUp,
    Idle,
    Curious,
    Focus,
    Thinking,
    Busy,
    Done,
    Error,
    Waiting,
    Loading,
    Searching,
    ;

    /**
     * 这两张脸「在干活」：眼睛会左右扫一次（`AiBall` 里那段扫眼动效）。
     *
     * 为什么不是全部十二张都动：静止是常态，动要有理由。只有「我在等一样东西」
     * 的时候扫眼才是真信息，其余时候动就是装饰。
     */
    val isWorking: Boolean
        get() = this == Loading || this == Searching
}
