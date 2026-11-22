package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.session.InMemorySecureStore
import com.muyao.wanzaihyperos.session.PortalSession
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * **真机/联调用的活体契约测试。**
 *
 * ### 三条纪律（这个文件是灰色地带，所以规则写死在最前面）
 *
 * 1. **凭据不经过对话、不写进任何源码或测试夹具。**
 *    它从一个由环境变量 [ENV_CREDENTIALS_PATH] 指定的文件里读，
 *    文件本身在 `.gitignore` 里（`.secrets/`）。
 *    这个类**只读它，从不打印它** —— 输出的每一行都只含长度、状态码与脱敏链条。
 * 2. **没配凭据就整类跳过**（[Assume]），所以常规 `testDebugUnitTest`
 *    永远是「不需要账号也能全绿」的，不会有人因为缺文件而看到一片红。
 * 3. **不做任何凭据持久化。** token 只活在这个测试进程的内存里
 *    （[InMemorySecureStore]），跑完即散。这样连「测试把 token 落盘」这种
 *    灰色做法都不存在。
 *
 * ### 为什么放在 JVM 单测而不是仪器化测试
 * CAS 登录是纯 HTTP + 加密，**不需要 Android**。放在 JVM 里跑得快、迭代快，
 * 而且能直接用 [TestHttpServer] 之外的真实网络。真机上要验的是 WebView 与
 * 系统集成（A4），不是这套 HTTP 契约。
 *
 * ### 跑之前的准备（业主一次性操作）
 * 建一个文本文件，**两行**：第一行学号，第二行密码。然后：
 * ```powershell
 * $env:WANZAI_LIVE_CREDENTIALS="[local]\.secrets\login.txt"
 * gradle :app:testDebugUnitTest --tests '*HzcuLiveLoginTest*' --no-daemon `
 *        --console=plain "-Dorg.gradle.jvmargs=-Xmx2g"
 * ```
 * 证据写在 `build/a3-live-login-evidence.txt`。
 */
class HzcuLiveLoginTest {

    companion object {
        const val ENV_CREDENTIALS_PATH = "WANZAI_LIVE_CREDENTIALS"

        /** 证据文件名。 */
        const val EVIDENCE_NAME = "a3-live-login-evidence.txt"

        /**
         * 工程根目录。
         *
         * 相对路径在 AGP 单测里是**模块目录**（`app/`）而不是工程根，
         * 一开始把证据写到 `build/…` 就落到了 `app/build/…`，白找了一轮。
         * 这里靠 `settings.gradle.kts` 往上找，别处直接用它。
         */
        fun projectRoot(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            repeat(8) {
                val current = dir ?: return@repeat
                if (File(current, "settings.gradle.kts").isFile) return current
                dir = current.parentFile
            }
            return File(System.getProperty("user.dir") ?: ".")
        }

        /** 证据文件一律写到工程根的 `build/` 下（`build/` 已在 .gitignore 里）。 */
        fun evidenceFile(name: String): File =
            File(projectRoot(), "build/$name").apply { parentFile?.mkdirs() }

        /**
         * 凭据文件的候选位置，按顺序试。
         *
         * 环境变量优先；没有就退回工程内的 `.secrets/login.txt` ——
         * 这样不必依赖「Gradle 测试 JVM 一定继承环境变量」这个假设
         * （实测第一次跑时环境变量确实没传进去，测试被静默跳过）。
         */
        fun credentialsCandidates(): List<File> = buildList {
            System.getenv(ENV_CREDENTIALS_PATH)?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add(File(it)) }
            add(File(projectRoot(), ".secrets/login.txt"))
        }

        /**
         * 从文件读凭据。**第一行学号（去空白），第二行密码（原样，不 trim）**。
         *
         * 密码刻意不 trim：某些密码合法地以空格开头或结尾，trim 会把一个正确的密码
         * 改成一个错的，然后表现为「明明密码没错却登不上」，极难排查。
         */
        fun readCredentials(): Pair<String, String>? {
            LiveNetworkGate.requireOptIn()
            for (file in credentialsCandidates()) {
                if (!file.isFile) continue
                val lines = file.readLines()
                if (lines.size < 2) continue
                // 去掉可能的 UTF-8 BOM（PowerShell 写文件时会加）。
                val account = lines[0].removePrefix("\uFEFF").trim()
                val password = lines[1]
                if (account.isEmpty() || password.isEmpty()) continue
                return account to password
            }
            return null
        }
    }

    @Test
    fun liveCasLoginAgainstRealServer() {
        val credentials = readCredentials()
        Assume.assumeTrue(
            "未配置 $ENV_CREDENTIALS_PATH（或文件不存在/不足两行），跳过活体联调",
            credentials != null,
        )
        // ★ 網絡預檢：連不上就**跳過**，不要報一堆超時 ——
        //   超時只說明當時網絡不通，卻會讓測試報告看起來像代碼壞了。
        //   （2026-10-12 上午真發生過：12 條紅全是活體測試，離線單測一條沒掛。）
        LiveNetworkGate.requirePortal()
        val (account, password) = credentials!!

        // token 只在这个进程的内存里，不落盘。
        val session = PortalSession(InMemorySecureStore())
        val client = HzcuClient.create(credentials = session)
        val service = HzcuCasService.create(client = client, session = session)

        val evidence = StringBuilder()
        evidence.appendLine("# A3 活体联调证据（全部脱敏：只有长度、状态码、键名）")
        evidence.appendLine("# 生成方式：HzcuLiveLoginTest.liveCasLoginAgainstRealServer()")

        val result = try {
            kotlinx.coroutines.runBlocking { service.login(account, password) }
        } catch (error: HzcuException) {
            // 失败时**只记类型与 details**（details 已脱敏），绝不记 message 以外的任何输入。
            evidence.appendLine("结果：失败")
            evidence.appendLine("异常类型：${error.javaClass.simpleName}")
            evidence.appendLine("短文案：${error.message}")
            error.details?.let { evidence.appendLine("详情：\n$it") }
            evidenceFile(EVIDENCE_NAME).writeText(evidence.toString())
            throw AssertionError("活体登录失败，证据见 build/$EVIDENCE_NAME", error)
        }

        evidence.appendLine("结果：成功")
        // ★ 只记长度，不记值。account 也是凭据，同样不记。
        evidence.appendLine("token 长度：${result.accessToken.length}")
        evidence.appendLine("命中的探针：${result.probeName}")
        evidence.appendLine("学号长度：${result.userCode.length}")
        evidence.appendLine("跳转链（已脱敏）：")
        result.chain.forEach { evidence.appendLine(it.toString()) }

        val cookieHosts = client.cookieJar.hostNames
        evidence.appendLine("会话 cookie 的 host：${cookieHosts.joinToString(",")}")
        evidence.appendLine(
            "会话 cookie 的名称：" +
                cookieHosts.joinToString(" ") { host ->
                    "$host=[${client.cookieJar.namesFor(host).joinToString(",")}]"
                },
        )

        File(projectRoot(), "build/$EVIDENCE_NAME").writeText(evidence.toString())

        // 断言本身也要脱敏。
        assertTrue("token 形态不符合预期（长度 ${result.accessToken.length}）", result.accessToken.length in 16..64)
        assertEquals(
            "登录成功后会话里应当有 token",
            result.accessToken.length,
            session.accessToken()?.length,
        )
        assertTrue(
            "登录成功但 cookie 罐是空的 —— 门户 token 与 CAS 会话 cookie 绑定，业务接口会全部 401",
            cookieHosts.isNotEmpty(),
        )
    }
}
