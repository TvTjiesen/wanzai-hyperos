package com.muyao.wanzaihyperos.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 失败折返接线的源码护栏：七个仓库的刷新/加载失败必须走 `retainCachedOnFailure`，
 * 不允许回退成裸 `failWith`——`LoginExpired` 会把已加载内容清成 `SessionExpired`，
 * 缓存保留语义只在 `retainCachedOnFailure` 里（見 `CachedRefreshFailure.kt`）。
 */
class RetainWiringTest {

    private val moduleRoot: File = File(System.getProperty("user.dir")).let { dir ->
        val parent = dir.parentFile
        if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else dir
    }

    private fun code(relPath: String): String =
        File(moduleRoot, "app/src/main/java/com/muyao/wanzaihyperos/$relPath").readText(Charsets.UTF_8)

    private val wired = listOf(
        "data/cjh52/Cjh52FeatureRepositories.kt",
        "data/cjh52/CourseRepository.kt",
        "data/jw/JwRepository.kt",
        "data/portal/PortalRepositories.kt",
        "data/portal/PortalBillRepository.kt",
        "data/portal/PortalBroadcastRepository.kt",
        "data/zy/ZyRepository.kt",
    )

    @Test
    fun 七个文件都接上了RetainCachedOnFailure() {
        wired.forEach { path ->
            assertTrue("$path 缺 retainCachedOnFailure 调用", code(path).contains("retainCachedOnFailure("))
        }
    }

    @Test
    fun 每个文件只导一次RetainCachedOnFailure() {
        val import = Regex("""import com\.muyao\.wanzaihyperos\.data\.repo\.retainCachedOnFailure""")
        wired.forEach { path ->
            assertEquals("$path 的 retainCachedOnFailure import 应恰好一处", 1, import.findAll(code(path)).count())
        }
    }

    @Test
    fun OnFailure折返里不再裸用FailWith() {
        val bare = Regex("""onFailure = \{[^}\n]*\.failWith\(""")
        wired.forEach { path ->
            assertFalse("$path 的 onFailure 折返仍裸用 failWith", bare.containsMatchIn(code(path)))
        }
    }

    @Test
    fun 已知的旧写法都换掉了() {
        val gone = mapOf(
            "data/cjh52/Cjh52FeatureRepositories.kt" to listOf("onFailure = { _state.value.failWith("),
            "data/cjh52/CourseRepository.kt" to listOf("onFailure = { _state.value.failWith("),
            "data/jw/JwRepository.kt" to listOf("flow.value.failWith("),
            "data/portal/PortalRepositories.kt" to listOf(
                "onFailure = { _state.value.failWith(",
                "onFailure = { _groups.value.failWith(",
                "_messages.value.failWith(",
                "content.failWith(",
            ),
            "data/portal/PortalBillRepository.kt" to listOf("current.failWith(failure)"),
            "data/portal/PortalBroadcastRepository.kt" to listOf("current.failWith(failure)"),
            "data/zy/ZyRepository.kt" to listOf("state.failWith(failure)"),
        )
        gone.forEach { (path, snippets) ->
            val source = code(path)
            snippets.forEach { snippet ->
                assertFalse("$path 还留着旧写法：$snippet", source.contains(snippet))
            }
        }
    }
}
