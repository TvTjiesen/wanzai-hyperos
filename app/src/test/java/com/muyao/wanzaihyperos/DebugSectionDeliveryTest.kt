package com.muyao.wanzaihyperos

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **#42 守卫**：`debug_section` 在「App 已经活着时再投一次」必须真的生效。
 *
 * ## 为什么要有它
 *
 * 这条调试口子坏过两次，两次都不是「值传错了」，而是**根本没人接收**：
 *
 * 1. `MainActivity` 没有 `launchMode` ⇒ 默认 `standard` ⇒ `am start` 与外部 deep link
 *    都会**再叠一个实例**，`onNewIntent` 一次都不跑（实测 `Hist` 从 1 变 2，
 *    画面退回登录页）。用户侧看得见的那条「App 开著时点链接要直接进去」也被同一个原因破坏。
 * 2. 值本身是 `Int?`。先手动切到别的板块、再投同一个板块时，`mutableStateOf` 写入同值
 *    不换引用 ⇒ `LaunchedEffect(debugSection)` 的键没变 ⇒ 副作用不重跑 ⇒ 界面一动不动。
 *
 * 所以这里守的不是「号码对不对」，而是**这条通道的结构**：投递对象每次都不相等、
 * manifest 声明了 `singleTop`、消费端按键取 `.section`。
 *
 * ⚠️ 本文件是**结构守卫**，它不能替代真机/模拟器上的端到端复核
 *    （证据在 `verify/42-debug-section/`）。
 */
class DebugSectionDeliveryTest {

    // ── 一、投递对象每次都不同 ────────────────────────────────────────────

    @Test
    fun `同一板块投两次必须交出两个不相等的投递对象`() {
        val inbox = DebugSectionInbox()
        val a = inbox.deliver(3)
        val b = inbox.deliver(3)
        assertNotNull("3 是合法板块号，不该被拒", a)
        assertNotNull("3 是合法板块号，不该被拒", b)
        assertEquals("板块号本身要原样带过去", 3, a!!.section)
        assertEquals("板块号本身要原样带过去", 3, b!!.section)
        assertNotEquals(
            "同值重放必须产生新对象 —— 否则 LaunchedEffect 的键不变，界面不会动（#42 后半截）",
            a,
            b,
        )
    }

    @Test
    fun `序号只增不减且不重复`() {
        val inbox = DebugSectionInbox()
        val seqs = (0..4).map { inbox.deliver(it)!!.seq }
        assertEquals("五次投递应当拿到五个互不相同的序号", 5, seqs.toSet().size)
        assertEquals("序号应当严格递增（它就是用来'每次都不一样'的）", seqs.sorted(), seqs)
    }

    @Test
    fun `越界的板块号一律不收`() {
        val inbox = DebugSectionInbox()
        assertNull("负数是非法板块号", inbox.deliver(-1))
        assertNull("5 越界（MainSection 只有 0..4）", inbox.deliver(5))
        assertNull("99 越界", inbox.deliver(99))
        // 拒收不该消耗序号：否则「投错一次」会把后面的序号推进一格。
        assertEquals("被拒的投递不该占序号", 1L, inbox.deliver(0)!!.seq)
    }

    // ── 二、接收端（manifest + WanzaiRoot）结构 ───────────────────────────

    @Test
    fun `MainActivity 必须声明 singleTop 否则 onNewIntent 永远不跑`() {
        val manifest = File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        val start = manifest.indexOf("android:name=\".MainActivity\"")
        assertTrue("manifest 里找不到 MainActivity 的 activity 声明", start >= 0)
        val end = manifest.indexOf('>', start)
        val block = manifest.substring(start, end)
        assertTrue(
            "MainActivity 缺 android:launchMode=\"singleTop\"：standard 启动模式下 am start 与 " +
                "外部 deep link 都会再叠一个实例，onNewIntent 不跑（#42 前半截）。实际声明：$block",
            block.contains("android:launchMode=\"singleTop\""),
        )
    }

    @Test
    fun `WanzaiRoot 必须按整次投递当键并读 section`() {
        val src = File("src/main/java/com/muyao/wanzaihyperos/WanzaiRoot.kt")
            .readText(Charsets.UTF_8)
        val noComment = com.muyao.wanzaihyperos.ui.SourceScan.stripComments(src)
        assertFalse(
            "debugSection 的参数类型退回 Int? 了 —— 同值重放会变成空操作",
            noComment.contains("debugSection: Int?"),
        )
        assertTrue(
            "LaunchedEffect 的键必须还是那个投递对象",
            noComment.contains("LaunchedEffect(debugSection)"),
        )
        assertTrue(
            "消费端必须读 .section；直接赋值 it 是把投递对象当板块号用",
            noComment.contains("uiState.selectedTab = it.section"),
        )
    }
}
