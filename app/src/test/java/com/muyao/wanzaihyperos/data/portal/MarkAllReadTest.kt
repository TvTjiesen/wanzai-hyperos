package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.net.HzcuEndpoints
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分组全部已读：端点拼法与接线守卫。
 *
 * 契约为 POST /api/app/ummessage/setallmessageread/{分组名字}
 * （docs/B7-WALKTHROUGH.md 第 1 节，门户前端原文）。
 * 这里盯两件容易悄悄坏掉的事：
 * ① 路径里拼的是**名字**（中文照拼），不是 id；
 * ② 从组头按钮到仓库方法这条线必须真的接死 —— 只写一半，界面会「点了没反应」。
 */
class MarkAllReadTest {

    private val root = File(System.getProperty("user.dir")).let { run ->
        // Gradle 的测试工作目录是模块目录（app/），源码在它的上一级。
        val parent = run.parentFile
        if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else run
    }

    private fun source(rel: String): String = File(root, rel).readText()

    /** 截到下一个分隔符为止：只夹一段实现来看，不被文件后半截的别的函数干扰。 */
    private fun cut(text: String, delimiter: String): String {
        val at = text.indexOf(delimiter)
        return if (at < 0) text else text.substring(0, at)
    }

    @Test
    fun `分组名字拼在路径上、不是 id`() {
        assertEquals(
            "/api/app/ummessage/setallmessageread/活动通知",
            HzcuEndpoints.portalMessageReadAllPath("活动通知"),
        )
        // 名字两端带空白是输入噪声，不该变成路径的一部分。
        assertEquals(
            "/api/app/ummessage/setallmessageread/讲座通知",
            HzcuEndpoints.portalMessageReadAllPath("  讲座通知  "),
        )
    }

    @Test
    fun `分组名字为空就不发请求`() {
        assertNull(HzcuEndpoints.portalMessageReadAllPath(""))
        assertNull(HzcuEndpoints.portalMessageReadAllPath("   "))
    }

    @Test
    fun `全部已读是 POST 无请求体、不许本地减一`() {
        val api = source("app/src/main/java/com/muyao/wanzaihyperos/net/PortalApi.kt")
        val body = cut(api.substringAfter("suspend fun markAllMessagesRead"), "suspend fun")
        assertTrue("markAllMessagesRead 必须是 portalPost + 空 fields", "fields = emptyMap()" in body)
        // B7 §2 第 ⑤ 条：服务端总未读 304 而各组 noread 之和 302，本地算术只会越偏。
        val repo = source("app/src/main/java/com/muyao/wanzaihyperos/data/portal/PortalRepositories.kt")
        val impl = cut(repo.substringAfter("override suspend fun markAllRead"), "override suspend fun")
        assertTrue("写成功后必须重取原代次列表", "refreshMessages(token)" in impl)
        assertTrue("不重取分组计数，胶囊上还挂着旧的未读数", "refreshGroups(groupToken ?: return false)" in impl)
        assertTrue("过时代次不得重取或发布新账号数据", "if (!ok || !guard.isCurrent(token)) return false" in impl)
        assertTrue("不许本地减一", "unread - 1" !in impl && "unread--" !in impl)
    }

    @Test
    fun `移除旧通知入口后该页不再触发已读写操作`() {
        val page = source("app/src/main/java/com/muyao/wanzaihyperos/ui/main/pages/NotifySectionPage.kt")
        assertTrue("不保留不可达确认框", "confirmReadAll" !in page)
        assertTrue("不保留写入口", "ReadAllAction(" !in page)
        assertTrue("不调用已读回调", "onReadAll(it.name)" !in page)
        assertTrue("门户其他功能不删除", "graph.news" in page && "graph.broadcasts" in page)
    }
}
