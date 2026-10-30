package com.muyao.wanzaihyperos.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * release 包的守门测试（2026-10-30）。
 *
 * 这一组盯的是「只有出正式包时才会咬人的事」——debug 包天天跑，错了立刻看得见；
 * release 包一个月才出一次，错了要到用户手机上才发现。所以把三件事钉成测试：
 *
 *  1. **WebView 调试端口不许打开**。setWebContentsDebuggingEnabled(true) 在 debug 包无害，
 *     在 release 包等于把整个 WebView 的 DevTools 端口暴露给同机的任何应用。
 *     Android 的默认行为是「只有 app debuggable 时才开」，所以正确做法是**一句都不写**。
 *  2. **签名配置必须能读到仓库外的 key**，且口令文件不存在时要跳过而不是崩。
 *     .gitignore 必须挡住 key 与口令——漏一条就是把私钥推到公共仓库。
 *  3. 口令文件必须按 **UTF-8 Reader** 读。Properties.load(InputStream) 是 ISO-8859-1，
 *     路径里带中文用户名会被解成乱码（2026-10-30 现场踩到：Keystore file not found）。
 */
class ReleaseReadinessTest {

    private val root: File = File(System.getProperty("user.dir")).let { run ->
        val parent = run.parentFile
        if (parent != null && File(parent, "app/src/main/java").isDirectory) parent else run
    }

    private fun read(rel: String): String = File(root, rel).readText()

    @Test
    fun `不许打开 WebView 调试端口`() {
        val offenders = SourceScan.sources()
            .filter { (_, text) -> text.contains("setWebContentsDebuggingEnabled") }
            .map { it.first }
        assertTrue("这些文件打开了 WebView 调试端口：" + offenders, offenders.isEmpty())
    }

    @Test
    fun `签名配置从仓库外读 key 且缺文件时跳过`() {
        val gradle = read("app/build.gradle.kts")
        assertTrue("没有声明 signingConfig",
            gradle.contains("signingConfig = signingConfigs.create(\"release\")"))
        assertTrue("没有从 WZ_KEYSTORE_PROPS 取口令文件路径", gradle.contains("WZ_KEYSTORE_PROPS"))
        assertTrue("没有先判断文件是否存在就读（缺 key 会让 release 直接崩）",
            gradle.contains("if (propsFile.isFile)"))
    }

    @Test
    fun `口令文件必须用 UTF-8 Reader 读`() {
        val gradle = read("app/build.gradle.kts")
        assertTrue("没有用 UTF-8 Reader 读口令文件（InputStream 是 ISO-8859-1，中文路径会变乱码）",
            gradle.contains("reader(Charsets.UTF_8).use { props.load(it) }"))
        assertFalse("还在用 InputStream 读口令文件",
            gradle.contains("propsFile.inputStream().use"))
    }

    @Test
    fun `密钥与口令必须被 gitignore 挡住`() {
        val ignore = read(".gitignore")
        listOf("keystore.properties", "*.jks").forEach { pat ->
            assertTrue(".gitignore 少了 " + pat + "，私钥会被推到公共仓库",
                ignore.lineSequence().any { it.trim() == pat })
        }
    }
}
