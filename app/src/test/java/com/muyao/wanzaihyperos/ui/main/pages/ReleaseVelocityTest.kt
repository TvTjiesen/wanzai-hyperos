package com.muyao.wanzaihyperos.ui.main.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
}
