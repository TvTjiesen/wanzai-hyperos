package com.muyao.wanzaihyperos.ui.design.ai

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.account.AccountSource
import com.muyao.wanzaihyperos.data.account.AccountStatus
import com.muyao.wanzaihyperos.data.account.AccountTarget
import com.muyao.wanzaihyperos.data.schedule.TodayPlan
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 小球五态的口径守卫：**「不知道」不许长成「没有」**。
 *
 * 为什么要有这条测试：这个缺陷在本项目里犯过一次 —— 消息页角标把非 Content 一律 `?: 0`，
 * 于是「没取到」在屏幕上长成「0 条待处理」（见 `NotifySectionPage.kt` 里那段注释）。
 * 小球比角标更危险：角标写 0 至少还是个数字，一张「今天没有课」的脸是在替数据源撒谎。
 *
 * 五条判据对应 `docs/B4-12-PAGES-DESIGN.md` §五 的五态表；判的是**函数返回值**，
 * 不是屏幕上写了什么字 —— 屏幕上看不出 Idle 与 Loading 的静止帧差别（同一张脸，只差眼睛扫一次）。
 */
class AiBallStatesTest {

    private fun slot(id: Int = 1) = TronScheduleSlot(
        courseId = id,
        courseName = "课 $id",
        dayOfWeek = 1,
        periodStart = 1,
        periodEnd = 1,
        weekStart = 1,
        weekEnd = 16,
        raw = "周一第1节",
    )

    private fun plan(remaining: Int, total: Int) = TodayPlan(
        headline = "今天",
        detail = null,
        remaining = List(remaining) { slot(it + 1) },
        totalToday = total,
        clockKnown = true,
    )

    // ── 1. 通用列表 ────────────────────────────────────────────────

    @Test
    fun 首次加载是正在读不是没有() {
        assertEquals(AiBallState.Loading, AiBallStates.ofList(ListState.FirstLoad))
    }

    @Test
    fun 还没挂上仓库时同样是正在读() {
        assertEquals(
            "null 状态被当成了「没有」—— 仓库还没吐出第一帧时，我们什么都不知道",
            AiBallState.Loading,
            AiBallStates.ofList(null),
        )
    }

    @Test
    fun 真的失败了才给红脸() {
        val failed = ListState.Failed(HzcuException.LoginExpired("需要先登录才能取数据"))
        assertEquals(AiBallState.Error, AiBallStates.ofList(failed))
        assertEquals(AiBallState.Error, AiBallStates.ofList(ListState.SessionExpired))
    }

    @Test
    fun 拿到货和确实空都是待机() {
        assertEquals(AiBallState.Idle, AiBallStates.ofList(ListState.Content(listOf(slot()))))
        assertEquals(AiBallState.Idle, AiBallStates.ofList(ListState.Empty()))
    }

    // ── 2. 数量类 ──────────────────────────────────────────────────

    @Test
    fun 数量不知道时是正在读() {
        assertEquals(AiBallState.Loading, AiBallStates.ofCount(null))
    }

    @Test
    fun 数量是零也是待机() {
        assertEquals(AiBallState.Idle, AiBallStates.ofCount(0))
    }

    // ── 3. 首页「此刻」卡的优先级 ─────────────────────────────────

    @Test
    fun 首页失败优先于一切() {
        val s = AiBallStates.ofHome(plan = plan(1, 3), loading = true, failure = "取不到", ongoing = true)
        assertEquals(
            "真失败了却画了别的脸 —— 这会把故障说成好消息",
            AiBallState.Error,
            s,
        )
    }

    @Test
    fun 首页加载中不许画成没有课() {
        val s = AiBallStates.ofHome(plan = plan(0, 0), loading = true, failure = null, ongoing = false)
        assertEquals(AiBallState.Loading, s)
    }

    @Test
    fun 首页在上的那节课是进行中() {
        assertEquals(
            AiBallState.Busy,
            AiBallStates.ofHome(plan = plan(2, 3), loading = false, failure = null, ongoing = true),
        )
    }

    @Test
    fun 首页今天没课是待机() {
        assertEquals(
            AiBallState.Idle,
            AiBallStates.ofHome(plan = plan(0, 0), loading = false, failure = null, ongoing = false),
        )
    }

    @Test
    fun 首页课全上完是已完成() {
        assertEquals(
            AiBallState.Done,
            AiBallStates.ofHome(plan = plan(0, 3), loading = false, failure = null, ongoing = false),
        )
    }

    @Test
    fun 首页计划还没算出来是正在读() {
        assertEquals(
            AiBallState.Loading,
            AiBallStates.ofHome(plan = null, loading = false, failure = null, ongoing = false),
        )
    }

    // ── 3b. 课间：马上要上课也要落到 Focus（返工总单 B9）─────────

    @Test
    fun 距下一节五十九分钟是注意力脸() {
        assertEquals(
            AiBallState.Focus,
            AiBallStates.ofHome(
                plan = plan(1, 3), loading = false, failure = null, ongoing = false,
                minutesToNextClass = 59,
            ),
        )
    }

    @Test
    fun 距下一节整六十分钟不算马上() {
        assertEquals(
            AiBallState.Idle,
            AiBallStates.ofHome(
                plan = plan(1, 3), loading = false, failure = null, ongoing = false,
                minutesToNextClass = 60,
            ),
        )
    }

    @Test
    fun 正在上课压过马上要上课() {
        assertEquals(
            AiBallState.Busy,
            AiBallStates.ofHome(
                plan = plan(2, 3), loading = false, failure = null, ongoing = true,
                minutesToNextClass = 30,
            ),
        )
    }

    @Test
    fun 待办到期与马上要上课都落注意力脸() {
        assertEquals(
            AiBallState.Focus,
            AiBallStates.ofHome(
                plan = plan(1, 3), loading = false, failure = null, ongoing = false,
                urgentTodo = true, minutesToNextClass = 30,
            ),
        )
    }

    @Test
    fun 认不出时间的课不触发课间脸() {
        // 算不出分钟数就是 null —— 「不知道」不许长成「马上要上课」。
        assertEquals(
            AiBallState.Idle,
            AiBallStates.ofHome(
                plan = plan(1, 3), loading = false, failure = null, ongoing = false,
                minutesToNextClass = null,
            ),
        )
    }

    // ── 4. 课表页：两份来源里有一份能用就不算坏 ───────────────────

    @Test
    fun 课表教务挂了但学在城院有货不算错() {
        assertEquals(
            "教务挂了就画红脸 —— 可屏幕上明明有课表（网格是 pickSlots 拼的）",
            AiBallState.Idle,
            AiBallStates.ofTimetable(
                ListState.Content(listOf(slot())),
                ListState.Failed(HzcuException.LoginExpired("教务失效")),
            ),
        )
    }

    @Test
    fun 课表两份都挂才是错() {
        assertEquals(
            AiBallState.Error,
            AiBallStates.ofTimetable(
                ListState.SessionExpired,
                ListState.Failed(HzcuException.LoginExpired("教务失效")),
            ),
        )
    }

    @Test
    fun 课表两份都还在读是正在读() {
        assertEquals(
            AiBallState.Loading,
            AiBallStates.ofTimetable(ListState.FirstLoad, ListState.FirstLoad),
        )
    }

    // ── 5. 设置页账号面 ──────────────────────────────────────────

    private fun src(status: AccountStatus) = AccountSource(
        id = "portal",
        name = "学校门户",
        provides = "门户",
        status = status,
        credentialAtMillis = null,
        timeLabel = null,
        whereToLogin = "设置",
        target = AccountTarget.PortalLogin,
    )

    @Test
    fun 账号还没读到是正在读() {
        assertEquals(AiBallState.Loading, AiBallStates.ofAccounts(null))
        assertEquals(AiBallState.Loading, AiBallStates.ofAccounts(emptyList()))
    }

    @Test
    fun 有一条读不到就还是不知道() {
        assertEquals(
            "AccountStatus.Unknown 被当成了已知 —— 它的注释写着「不得当成未登录」",
            AiBallState.Loading,
            AiBallStates.ofAccounts(listOf(src(AccountStatus.LoggedIn), src(AccountStatus.Unknown))),
        )
    }

    @Test
    fun 全是未登录也算读到了() {
        assertEquals(
            "未登录是待办不是故障，不该画红脸（设计稿 §五：已登录 0 项 -> 02 待机）",
            AiBallState.Idle,
            AiBallStates.ofAccounts(listOf(src(AccountStatus.LoggedOut), src(AccountStatus.LoggedOut))),
        )
    }
}
