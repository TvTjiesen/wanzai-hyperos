package com.muyao.wanzaihyperos.ui.main.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 抬手速度的取法（v3 #5 第 3 轮真机审计 **Block 头条** 的守卫）。
 *
 * 事故原文：一次快甩之后天不翻、条子还被"吸回去"。真机逐帧拟合给出
 * 手指真速度 **−1350 px/s**、而弹簧初速反解 **+975 px/s** —— **符号反了**。
 * 根因是我把 UP 也喂进了 Compose 的 Lsq2 估计器（它的有效窗只有 40ms/≥3 样本，
 * 而 UP 是个零位移的重复点，会把最小二乘的斜率拽偏甚至翻号）。
 *
 * 这条测试钉住替代实现 [releaseVelocityOf] 的两件事：
 *  ① 符号永远与手指方向一致（首尾两点都是手指自己的位置）；
 *  ② 多一个"重复点"只会让**幅值**变小，**不许翻号**（就是这次事故的形状）。
 */
class ReleaseVelocityTest {

    private fun samples(vararg tsAndX: Pair<Long, Float>) = tsAndX.toList()

    /** 一点一点往左挪（每 16ms −10px）⇒ 速度必须为负。 */
    @Test
    fun `向左拖的速度是负的`() {
        val s = samples(
            0L to 800f, 16L to 790f, 32L to 780f, 48L to 770f, 64L to 760f,
        )
        val v = releaseVelocityOf(s, 64L)
        assertEquals("首尾差 760−800 比 64ms ⇒ −625 px/s", -625.0, v.toDouble(), 1.0)
        assertTrue("向左必须是负速度（第 3 轮那个 bug 就是这里反了）", v < 0f)
    }

    /** 向右拖 ⇒ 正速度。 */
    @Test
    fun `向右拖的速度是正的`() {
        val s = samples(0L to 700f, 16L to 710f, 32L to 720f, 48L to 730f, 64L to 740f)
        assertTrue("向右必须是正速度", releaseVelocityOf(s, 64L) > 0f)
    }

    /**
     * **回归**：末尾补一个"手指没动"的重复点（UP 那一下就是这个形状 ——
     * 位置与最后一次 move 相同、时间晚一点点）。幅值可以变小，**符号不许翻**。
     *
     * 上一版（`tracker.addPosition` 每事件都喂）在这一步会给出**正**速度，
     * 于是 `projected` 从 −297.5 塌到 −41.8 ⇒ 快甩不翻天。
     */
    @Test
    fun `补一个重复点不许把符号翻过去`() {
        val s = samples(
            0L to 800f, 16L to 790f, 32L to 780f, 48L to 770f, 64L to 760f,
            89L to 760f, // ← UP：位置与上一帧相同
        )
        val v = releaseVelocityOf(s, 89L)
        assertTrue("补了重复点之后符号必须还是负（实测 $v）", v < 0f)
        assertTrue("幅值应当比不带重复点时小（重复点把斜率拉平）", abs(v) < 625f)
    }

    /**
     * **真机轨迹回归**（第 5 轮审计验收 E）：把真机上那条 −100px/56ms 的记录喂进来，
     * 期望 ≈ **−1786 px/s**（手指真速是 −1774，差在采样时刻的取整）。
     */
    @Test
    fun `真机那条快甩轨迹必须算出接近手指真速的速度`() {
        val real = samples(
            0L to 860f, 11L to 835f, 23L to 810f, 35L to 785f, 46L to 760f, 56L to 760f,
        )
        val v = releaseVelocityOf(real, 56L)
        assertEquals("−100px / 56ms ⇒ ≈ −1786 px/s（手指真速 −1774）", -1785.7, v.toDouble(), 5.0)
        // 而且这个 v 必须让"只走了 99px（不到半格）"的小快甩也翻天：
        // 位移是**负**的（往左拖），别把符号写反 —— 我第一版写成 `99f + v*0.11f`，
        // 于是投影是 +97（显然不翻），差点把它当成"判据有问题"。
        assertEquals(
            "小快甩要翻天：drag −99px（不到半格）时，投影 = −99 + (−1786×0.11) = −295.5 ⇒ 1 格",
            1,
            weekFlipStep(-99f + v * 0.11f, 220.8f),
        )
    }

    /** 样本不足 3 个 ⇒ **0**（按纯位置判定），不许拿两点硬凑一个数。 */
    @Test
    fun `样本不足就返回 0`() {
        assertEquals(0f, releaseVelocityOf(samples(0L to 800f, 16L to 790f), 16L), 0f)
        assertEquals(0f, releaseVelocityOf(emptyList(), 100L), 0f)
    }

    /** 窗口外的老样本不参与（否则"停下来之后抬手"会拿着几百毫秒前的速度去投影）。 */
    @Test
    fun `窗口外的样本不参与`() {
        val s = samples(
            0L to 1000f, 10L to 900f, // 已经是很久以前
            200L to 700f, 210L to 699f, 220L to 698f, // 最近这 20ms 基本没动
        )
        val v = releaseVelocityOf(s, 220L)
        // 最近 80ms 只剩 (200,700)(210,699)(220,698) ⇒ −2px/20ms = **−100 px/s**（缓慢左漂）。
        // 关键是它**不能**被那两点老样本（0→10ms 里掉了 100px，−10000 px/s）拽走。
        assertTrue("老样本不许参与（实得 $v，若掺进老样本会是 −10000 量级）", abs(v) < 200f)
        assertTrue("方向仍然是左", v < 0f)
    }

    /**
     * 把"快甩翻天 / 慢拖不翻"这条判据的算术一起钉住（`projected = 位移 + v·0.11`，
     * 阈值 = 半格 110.4px）。这条是业主真正会看到的那件事。
     */
    @Test
    fun `快甩翻天慢拖不翻天的算术`() {
        // ★ 第 5 轮审计（测试守卫生效性）：**判据必须调生产函数**。
        //   以前这里自己写了一遍 `step()` + 本地 `cellPx` ⇒ 生产除数从 220.8 改成 110.4 它照样绿
        //   （等于一行生产代码都没守到）。现在调 `weekFlipStep`（生产里同一份实现）。
        val cellPx = 220.8f
        fun step(drag: Float, v: Float): Int =
            weekFlipStep(drag + v * 0.11f, cellPx)

        // 符号约定：`step = round(−projected / 半格)`，`next = sel + step`
        // ⇒ 向左拖（投影为负）时 step 为**正**，落到后一天。
        // 快甩：手指 −150px、速度 −1350 px/s ⇒ 投影 −298.5 ⇒ 298.5/220.8 = 1.35 ⇒ step +1 ⇒ 翻天
        assertEquals("快甩必须翻天", 1, step(-150f, -1350f))
        // 慢拖同样 −150px、速度 ≈ 0 ⇒ 只看位置 150/220.8 = 0.68 ⇒ 仍然翻天（位置够半格多）
        assertEquals("慢拖到一格半也该翻天", 1, step(-150f, 0f))
        // ⚠️ 门槛在 **110.4px（半格）**：`weekFlipStep` 里除的是**整格 220.8**，`roundToInt` 在 0.5 处进位
        //   （我第一版把除数写成"半格"，门槛算成 55.2px 还据此写了注释 —— 已改准）。
        assertEquals("慢拖 93px（0.42 格）⇒ 不翻", 0, step(-93f, 0f))
        assertEquals("慢拖 80px（0.36 格）⇒ 不翻", 0, step(-80f, 0f))
        // 两条**复刻已实测的读数**（让这条测试能对表，而不是另一套算术）：
        //  第 2 轮真机：慢拖 −93px、v = 0 ⇒ projected −93 ⇒ step 0 ⇒ **不翻** ✓
        assertEquals("复刻第 2 轮真机读数：慢拖 −93 / v=0 ⇒ 不翻", 0, step(-93f, 0f))
        //  第 3 轮真机：同一次快甩、但 v 被算成 +975（符号反了）⇒ projected = −93+107 = +14 ⇒ 不翻 ✗
        assertEquals("复刻第 3 轮事故：−93px 快甩 + v 反号(+975) ⇒ 不翻天", 0, step(-93f, +975f))
        assertEquals("同一只手如果 v 没反号(−1379) ⇒ projected −245 ⇒ 翻天", 1, step(-93f, -1379f))
    }
}