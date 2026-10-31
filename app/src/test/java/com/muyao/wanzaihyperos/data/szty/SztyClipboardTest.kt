package com.muyao.wanzaihyperos.data.szty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剪贴板抽取的守卫。
 *
 * 为什么要有这些断言：这个函数唯一的失败方式是**静默抽错**——
 * 抽出一个看起来像 token 的东西、或者把学号当成 userId 填进去。
 * 两种错在界面上都看不出来，只会变成服务端一句「token 失效」。
 * 所以这里把「抽对」「宁可不抽也不乱抽」两侧都钉住。
 */
class SztyClipboardTest {

    private val token = "SPORT_API_" + "AwQe7Zk1+Qp/xY="

    @Test
    fun `干净的一个 token 就能抽出来`() {
        val got = parseSztyPaste(token)
        assertEquals(token, got.token)
        assertNull(got.userId)
    }

    @Test
    fun `整行请求头里也能抽出来`() {
        val clip = "POST /tyapi/mobile/health/getMyGrade\ntoken: $token\nContent-Type: application/json"
        assertEquals(token, parseSztyPaste(clip).token)
    }

    @Test
    fun `请求体 JSON 里连同 userId 一起抽出来`() {
        val clip = """{"userId":80000,"termId":15}  header token: $token"""
        val got = parseSztyPaste(clip)
        assertEquals(token, got.token)
        assertEquals(80000, got.userId)
    }

    @Test
    fun `紧贴着的形态 userId 与冒号之间没有空格也算`() {
        assertEquals(80000, parseSztyPaste("userId:80000").userId)
        assertEquals(80000, parseSztyPaste("userId=80000").userId)
    }

    @Test
    fun `★ 学号绝不能被当成 userId`() {
        // 真实现场：抓包原文里学号（8 位）和 userId（5 位）同时存在。
        // 没写字段名的裸数字一律不许被认成 userId。
        val clip = "stuNum:32500000 jobNum 32500000 token $token"
        val got = parseSztyPaste(clip)
        assertEquals(token, got.token)
        assertNull("没写 userId 的那一串数字不算 userId，否则会把学号填进去", got.userId)
    }

    @Test
    fun `光有前缀不算 token`() {
        assertNull(parseSztyPaste("SPORT_API_").token)
    }

    @Test
    fun `空剪贴板与空白串都返回空结果`() {
        assertTrue(parseSztyPaste(null).isEmpty)
        assertTrue(parseSztyPaste("").isEmpty)
        assertTrue(parseSztyPaste("   \n  ").isEmpty)
    }

    @Test
    fun `毫无关系的一段话不会抽出任何东西`() {
        assertTrue(parseSztyPaste("明天上午十点打球").isEmpty)
    }

    @Test
    fun `token 里的加号斜杠与等号原样保留`() {
        val got = parseSztyPaste("token=$token&userId=80000")
        assertEquals("base64 的 + / = 都是 token 的一部分，不能被清洗掉", token, got.token)
    }
}
