package com.muyao.wanzaihyperos.net

import java.io.File
import java.security.MessageDigest

/**
 * **只读接口**的本地响应缓存（stale 策略见 [get]）。
 *
 * ## 为什么要有它（业主 2026-10-20：「为什么每次我点进应用数据都要重新加载，
 * 这样太浪费时间了，帮我缓存到本地不行吗」）
 *
 * 这个 App 每一次进某一屏都是现打一次网络：学校服务器在国内校园网通常 300~900ms，
 * 一次刷新还要打好几支 —— 加起来就是他所说的"每次都要重新加载"。
 * 数据本身一节课内不会变（课表、待办、账单、成绩都是），重复取没有任何意义。
 *
 * ## 三条边界（做错任何一条都比不缓存更糟）
 *
 * 1. **只缓存 GET**。写操作（签到、交作业）一次都不许走这里 ——
 *    缓存一个写请求的响应等于把"提交成功"这句话存下来重复用，那是假回执。
 * 2. **只缓存 JSON**。被踢回登录页时服务器回的是 HTML，
 *    存下来下次直接回放，界面就会永远显示"上一次的数据"而不知道会话已经死了。
 * 3. **凭据/会话类端点不缓存**（[CACHEABLE] 的黑名单）：`/api/profile` 之类
 *    带个人标识的虽然也只是他自己的数据，但缓存它没有收益，少一处就多一处安全。
 *
 * ## 这些文件里是他本人的课表/账单/成绩
 *
 * 存在 App 私有目录（别的 App 读不到），設定页给了一行**看得见的大小 + 一键清除**
 * —— 不能让他手机里悄悄存着一堆个人数据而他自己不知道、也清不掉。
 */
class ResponseCache(
    dir: File,
    /** 每次取都传进来，方便单测（不依赖真实时钟）。 */
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val root = File(dir, "http_cache").apply { runCatching { mkdirs() } }

    /** 一条 GET 响应能不能缓存。 */
    fun cacheable(path: String, method: String): Boolean {
        if (method != "GET") return false
        val p = path.lowercase()
        if (!p.startsWith("/api/")) return false
        return FORBIDDEN.none { p.contains(it) }
    }

    /**
     * @param maxAgeMs 超过这个年龄就当没有（**不做后台静默更新**：
     *   那样界面会显示一份他看不出新旧的数据，这个工程不接受这种"看起来很快"）。
     */
    fun get(path: String, maxAgeMs: Long): String? {
        val f = file(path)
        if (!f.isFile) return null
        val age = nowMs() - f.lastModified()
        if (age < 0 || age > maxAgeMs) return null      // 时钟被改过 ⇒ 当没有
        return runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun put(path: String, body: String) {
        if (body.length > MAX_BODY) return               // 异常大的响应不存（别把他手机塞满）
        runCatching { file(path).writeText(body) }
    }

    fun invalidate(path: String) { runCatching { file(path).delete() } }

    /** 占了多少字节（設定页那一行显示用）。 */
    fun usedBytes(): Long = runCatching {
        root.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }.getOrDefault(0L)

    fun entries(): Int = runCatching { root.listFiles()?.count { it.isFile } ?: 0 }.getOrDefault(0)

    /** @return 清掉的字节数（让界面能说「已清除 240 KB」，而不是只说「已清除」）。 */
    fun clearAll(): Long {
        val n = usedBytes()
        runCatching { root.listFiles()?.forEach { it.delete() } }
        return n
    }

    /**
     * 键 = 路径的 SHA-1。
     *
     * 為什麼不直接用 URL 当文件名：查询串里有 `?`、`&`、`%`、中文，
     * 拼出来的文件名在有的文件系统上直接创建失败（然后就是"缓存永远不生效"这种查不动的错）。
     */
    private fun file(path: String): File = File(root, sha1(path) + ".json")

    private companion object {
        /**
         * 絕對不緩存的端點（**黑名單**：要緩存的會不斷新增，而不能緩存的少且穩定）。
         *
         * `qr-code` / `scan` 那一條最要緊 —— 那是「把掃到的碼交給服務端認領」，
         * 可能直接記考勤。緩存它的回應，等於把「簽到成功」這句話存下來重複播。
         */
        val FORBIDDEN = listOf(
            "login", "logout", "token", "auth", "qrcode", "qr-code", "captcha", "refresh", "scan",
        )
        const val MAX_BODY = 2_000_000

        fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
