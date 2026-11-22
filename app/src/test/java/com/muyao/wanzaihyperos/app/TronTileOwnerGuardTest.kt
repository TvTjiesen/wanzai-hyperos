package com.muyao.wanzaihyperos.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source guard for the two defects confirmed by the independent read-only audit. */
class TronTileOwnerGuardTest {

    private val root = File(System.getProperty("user.dir")).let { run ->
        val parent = run.parentFile
        if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else run
    }

    private fun source(path: String) = File(root, path).readText()

    @Test
    fun `学在城院磁贴不再吞取消或拿旧账号 token 兜底`() {
        val root = source("app/src/main/java/com/muyao/wanzaihyperos/WanzaiRoot.kt")
        val tile = root.substringAfter("is AppOpenPlan.TronclassFreeLogin")
            .substringBefore("// 普通外鏈")
        val resolver = source("app/src/main/java/com/muyao/wanzaihyperos/ui/web/WebTargetResolver.kt")
        assertTrue("入口只保存稳定业务目标", "WebTarget.tronclass()" in tile && "identityTokenUrl" !in tile)
        assertTrue("续期必须抛回取消", "throw cancelled" in resolver)
        assertTrue("打开前核对账号代次", "if (!lease.isCurrent()) return null" in resolver)
        assertTrue("落盘必须过 publishData", "epoch.publishData(owner)" in resolver)
        assertTrue("落盘必须再核对次级凭据", "publishCredential(originalRevision) { save(fresh) }" in resolver)
        assertTrue("同账号续期不增代次", "renewal = true" in resolver)
        assertTrue("不能再用 runCatching 吞取消", "runCatching" !in resolver)
    }

    @Test
    fun `次级凭据变化只清缓存不推进账号代次`() {
        val graph = source("app/src/main/java/com/muyao/wanzaihyperos/app/AppGraph.kt")
        val observer = graph.substringAfter("private fun watchSecondary")
            .substringBefore("fun fetch(")
        assertTrue("仍要清缓存并恢复 cookie", "httpCache.clearAll()" in observer &&
            "client.restoreCookieJar()" in observer)
        assertTrue("不能取消在途请求", "invalidateCurrent()" !in observer)
    }
}
