package com.muyao.wanzaihyperos.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CAS 密码加密。**这一条完全不需要账号就能验证** ——
 * 蓝本用学校页面自己加载的那份 crypto-js.js 跑出了对照值，我们拿它对答案。
 */
class HzcuCasCryptoTest {

    /**
     * ★ 最关键的断言：必须与 CryptoJS 用**字节 key** 的结果逐字相同。
     *
     * 若这里对不上、而且结果以 `U2FsdGVkX1`（`Salted__`）开头，
     * 说明 key 被当成 passphrase 走了 `EVP_BytesToKey` + CBC —— 学校服务器会拒绝登录。
     */
    @Test
    fun `与 CryptoJS 的已知向量逐字一致`() {
        assertEquals(
            HzcuCasCrypto.KNOWN_ANSWER_CIPHER,
            HzcuCasCrypto.encryptPassword(HzcuCasCrypto.KNOWN_ANSWER_PLAIN),
        )
    }

    @Test
    fun `不是 passphrase 路径的产物`() {
        val cipher = HzcuCasCrypto.encryptPassword("Passw0rd!")
        // `Salted__` 的 Base64 前缀。出现它就说明走错了密钥派生路径。
        assertFalse("走成了 passphrase + CBC", cipher.startsWith("U2FsdGVkX1"))
    }

    /**
     * PKCS#7 的「已经块对齐时仍补整块」规则。
     *
     * 16 字节明文若只补齐到 16，就是 16 字节密文；正确行为是补满一整块 → 32 字节。
     * Base64(32 字节) = 44 字符（带一个 `=`）；Base64(16 字节) = 24 字符（带两个 `=`）。
     */
    @Test
    fun `块对齐的密码仍会多补一整块`() {
        val sixteenBytes = "0123456789abcdef"
        assertEquals(16, HzcuCasCrypto.utf8Length(sixteenBytes))

        val cipher = HzcuCasCrypto.encryptPassword(sixteenBytes)

        assertEquals("少补了一块，16 字节整数倍的密码会登录失败", 44, cipher.length)
        assertTrue(cipher.endsWith("="))
        assertFalse("补成了 16 字节密文（漏了整块填充）", cipher.length == 24)
    }

    @Test
    fun `非块对齐的密码补齐到一个块`() {
        val cipher = HzcuCasCrypto.encryptPassword("abc")
        // 3 字节 → 补到 16 字节 → Base64 24 字符（两个 =）。
        assertEquals(24, cipher.length)
        assertTrue(cipher.endsWith("=="))
    }

    @Test
    fun `按 UTF-8 而不是 UTF-16 或 latin1 计算长度`() {
        // 中文一个字 3 字节（UTF-8）。若误用 UTF-16 会是 2 字节，长度断言立刻暴露。
        assertEquals(6, HzcuCasCrypto.utf8Length("密码"))
        // ASCII 一个字 1 字节；UTF-16 会是 2，latin1 也是 1。
        assertEquals(1, HzcuCasCrypto.utf8Length("a"))
    }

    @Test
    fun `中文密码也能加密且形态合法`() {
        val cipher = HzcuCasCrypto.encryptPassword("密码123")
        assertTrue(cipher.matches(Regex("^[A-Za-z0-9+/]+={0,2}$")))
        assertEquals(0, cipher.length % 4)
    }

    @Test
    fun `空密码被明确拒绝`() {
        val error = runCatching { HzcuCasCrypto.encryptPassword("") }.exceptionOrNull()
        assertTrue("实际是 $error", error is IllegalArgumentException)
        assertEquals("密码为空，不予加密提交", error?.message)
    }

    @Test
    fun `同一个明文每次得到同一密文（ECB 无 IV）`() {
        // 这是 ECB 的性质，也是学校前端的行为。写下来是为了让「有人给它塞了 IV」
        // 这种改动立刻暴露 —— 那会让服务端验不过。
        assertEquals(
            HzcuCasCrypto.encryptPassword("Passw0rd!"),
            HzcuCasCrypto.encryptPassword("Passw0rd!"),
        )
    }
}
