package com.muyao.wanzaihyperos.data.cjh52

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 成长/通知/考试三个 CJH52 仓库的刷新生命周期契约（2026-11-22 复查发现的成长卡转圈缺陷的回归护栏）：
 * 1. `inFlight.withLock` 必须包在 try/finally 里——第二次刷新在锁上等待时被取消的话，
 *    它从不进入 lambda，只有外层 finally 收得了转圈标志（同 LectureRepository 的放置位置）。
 * 2. 失败折返走 retainCachedOnFailure：登录过期不清空已加载内容，只标 stale。
 */
class Cjh52RefreshSafetyTest {

    private val source: String =
        File("src/main/java/com/muyao/wanzaihyperos/data/cjh52/Cjh52FeatureRepositories.kt")
            .readText(Charsets.UTF_8)

    @Test
    fun 每个refresh的互斥锁都包在tryFinally里() {
        val wrapped = Regex("""try \{\s*inFlight\.withLock \{""").findAll(source).count()
        assertTrue("三个仓库的 inFlight.withLock 都要包 try/finally，实测只有 $wrapped 处", wrapped >= 3)
    }

    @Test
    fun finally必须收转圈标志() {
        val finishes = Regex("finishRefresh\\(\\)").findAll(source).count()
        assertTrue("每个 refresh 至少一处 finally 要调 finishRefresh()，实测 $finishes 处", finishes >= 3)
    }

    @Test
    fun 失败保留旧内容不再裸用failWith() {
        assertFalse(
            "失败折返不允许裸 failWith（登录过期会清空内容）",
            source.contains("onFailure = { _state.value.failWith("),
        )
        val retained = Regex("retainCachedOnFailure\\(").findAll(source).count()
        assertTrue("Notice/Exam 的失败折返也要走 retainCachedOnFailure，实测 $retained 处", retained >= 3)
    }
}
