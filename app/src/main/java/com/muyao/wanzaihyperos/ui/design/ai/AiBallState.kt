package com.muyao.wanzaihyperos.ui.design.ai

/**
 * AI 小球要表达的**十二件事**（不是十二种动画，是十二种事实）。
 *
 * 保留开源引擎 `grok-ball` 的状态 id 与动作归属（MIT，github.com/tycoding/grok-ball）。
 * 眼形改为应用自己的安全平行胶囊策略，由 `tools/gen_ai_ball_glyphs.py` 生成并校验；
 * 不再从旧渲染 DOM 导入眼环。表情只是表现，事实继续由业务数据与文字说明。
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
 * [Idle] 与 [Loading] 现在具有不同静态开合与宽度，减少动态时也能区分。
 * [Focus] 使用平行窄开眼形，不把眼睛向中间聚拢；未知与失败不会被替换成无课。
 * 十二态始终保留，文字与来源事实负责解释表情不能表达的差别。
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
