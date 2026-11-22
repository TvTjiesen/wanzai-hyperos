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
 * 3. **凭据/会话类端点不缓存**（[FORBIDDEN] 的黑名单）：`/api/profile` 之类
 *    带个人标识的虽然也只是他自己的数据，但缓存它没有收益，少一处就多一处安全。
 *
 * ## 这些文件里是他本人的课表/账单/成绩
 *
 * 存在 App 私有目录（别的 App 读不到），設定页给了一行**看得见的大小 + 一键清除**
 * —— 不能让他手机里悄悄存着一堆个人数据而他自己不知道、也清不掉。
 *
 * 2026-11-07 又加了一层：**按领域分开清**（[domains] / [clearDomain]）。
 * 一键全清是最后手段；他多半只想清掉"成绩那一类"，留下课表。
 */
class ResponseCache(
    dir: File,
    /** 每次取都传进来，方便单测（不依赖真实时钟）。 */
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val root = File(dir, "http_cache").apply { runCatching { mkdirs() } }
    private var clearGeneration = 0L

    @Synchronized
    internal fun writeGeneration(): Long = clearGeneration

    @Synchronized
    internal fun putIfCurrent(
        source: String,
        identity: RequestIdentity,
        path: String,
        body: String,
        generation: Long,
        isCurrent: () -> Boolean,
    ): Boolean {
        if (clearGeneration != generation || !isCurrent()) return false
        val key = scopedKey(source, identity, path)
        putEntry(key, path.substringBefore('?'), body)
        if (!isCurrent()) {
            invalidateEntry(key)
            return false
        }
        return true
    }

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
    private fun getEntry(key: String, maxAgeMs: Long): String? {
        val f = file(key)
        if (!f.isFile) return null
        val age = nowMs() - f.lastModified()
        if (age < 0 || age > maxAgeMs) return null      // 时钟被改过 ⇒ 当没有
        return runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Identity-scoped entries never fall back to the old path-only cache. */
    @Synchronized
    fun get(source: String, identity: RequestIdentity, path: String, maxAgeMs: Long): String? =
        getEntry(scopedKey(source, identity, path), maxAgeMs)

    @Synchronized
    fun put(source: String, identity: RequestIdentity, path: String, body: String) {
        putEntry(scopedKey(source, identity, path), path.substringBefore('?'), body)
    }

    @Synchronized
    fun invalidate(source: String, identity: RequestIdentity, path: String) {
        invalidateEntry(scopedKey(source, identity, path))
    }

    private fun scopedKey(source: String, identity: RequestIdentity, path: String): String =
        "v2:${source.length}:$source:${identity.ownerKey.length}:${identity.ownerKey}:${identity.generation}:$path"

    private fun putEntry(key: String, displayPath: String, body: String) {
        if (body.length > MAX_BODY) return               // 异常大的响应不存（别把他手机塞满）
        runCatching {
            val target = file(key)
            val temp = File.createTempFile("cache-", ".tmp", root)
            try {
                temp.writeText(body)
                check(temp.renameTo(target))
                target.setLastModified(nowMs())
            } finally {
                temp.delete()
            }
            // 侧车文件：把**原始路径**留下来。
            //
            // 为什么非存不可：正文文件名是 path 的 SHA-1（[file] 的注释解释了为什么不能
            // 直接拿 URL 当文件名），而 SHA-1 **不可逆**。不另存一份原文，就永远只能
            // 告诉他"这里有 312 KB"，说不出"这 312 KB 是课表还是成绩"，
            // 也就谈不上按领域清理。
            //
            // 侧车里只有路径：没有正文、没有 cookie、没有 token。
            meta(key).writeText(displayPath)
        }
    }

    private fun invalidateEntry(key: String) {
        runCatching { file(key).delete() }
        runCatching { meta(key).delete() }
    }

    /** 占了多少字节（設定页那一行显示用）。侧车不算 —— 它只有几十字节，算进去只是噪声。 */
    @Synchronized
    fun usedBytes(): Long = runCatching {
        root.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.sumOf { it.length() } ?: 0L
    }.getOrDefault(0L)

    @Synchronized
    fun entries(): Int = runCatching {
        root.listFiles()?.count { it.isFile && it.name.endsWith(".json") } ?: 0
    }.getOrDefault(0)

    /** @return 清掉的字节数（让界面能说「已清除 240 KB」，而不是只说「已清除」）。 */
    @Synchronized
    fun clearAll(): Long {
        clearGeneration++
        val n = usedBytes()
        runCatching { root.listFiles()?.forEach { it.delete() } }
        return n
    }

    /**
     * 按领域分组的缓存占用（建议五：分域清理）。
     *
     * 分组键是**原始请求路径的第 3 段**（`/api/v2/todo/...` → `todo`），
     * 不是文件大小、也不是时间 —— 用户要清的是"某一类数据"，只有路径能说出这件事。
     *
     * 没有侧车的旧条目统一归到 [UNKNOWN_DOMAIN]，界面上照实写「旧版未分类」，
     * 而不是把它算进某一类里假装分得清。
     */
    @Synchronized
    fun domains(): List<CacheDomain> {
        val files = runCatching {
            root.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
        }.getOrNull().orEmpty()
        if (files.isEmpty()) return emptyList()
        val bucket = HashMap<String, MutableList<File>>()
        files.forEach { f ->
            val key = f.name.removeSuffix(".json")
            val path = runCatching {
                File(root, key + META).takeIf { it.isFile }?.readText()
            }.getOrNull()
            val name = if (path.isNullOrBlank()) UNKNOWN_DOMAIN else domainOf(path)
            bucket.getOrPut(name) { mutableListOf() }.add(f)
        }
        return bucket.map { (name, fs) ->
            CacheDomain(name = name, entries = fs.size, bytes = fs.sumOf { it.length() })
        }.sortedByDescending { it.bytes }
    }

    /** 清掉某一领域。@return 清掉的字节数。 */
    @Synchronized
    fun clearDomain(name: String): Long {
        clearGeneration++
        var freed = 0L
        runCatching {
            root.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                if (f.name.endsWith(META)) {
                    // 侧车跟着正文一起走：正文没了，侧车就是垃圾。
                    val key = f.name.removeSuffix(META)
                    if (!File(root, key + ".json").isFile) f.delete()
                    return@forEach
                }
                if (!f.name.endsWith(".json")) return@forEach
                val key = f.name.removeSuffix(".json")
                val path = runCatching {
                    File(root, key + META).takeIf { it.isFile }?.readText()
                }.getOrNull()
                val owner = if (path.isNullOrBlank()) UNKNOWN_DOMAIN else domainOf(path)
                if (owner == name) {
                    freed += f.length()
                    f.delete()
                    File(root, key + META).delete()
                }
            }
        }
        return freed
    }

    /**
     * 键 = 路径的 SHA-1。
     *
     * 為什麼不直接用 URL 当文件名：查询串里有 `?`、`&`、`%`、中文，
     * 拼出来的文件名在有的文件系统上直接创建失败（然后就是"缓存永远不生效"这种查不动的错）。
     */
    private fun file(path: String): File = File(root, sha1(path) + ".json")

    /** 侧车：与 [file] 同键，后缀 [META]，内容是原始路径。 */
    private fun meta(path: String): File = File(root, sha1(path) + META)

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

        /** 侧车后缀（不用 .json，免得被 entries() 当成一条缓存）。 */
        const val META = ".path"

        /** 没有侧车的旧条目（本功能上线前写下的）。 */
        const val UNKNOWN_DOMAIN = "旧版未分类"

        /** 路径里只有纯数字段的那些接口，统一显示成这个。 */
        const val DIGIT_DOMAIN = "按 id 的接口"

        /**
         * 路径 → 用户看得懂的领域名。
         *
         * 只认第 3 段（`/api/v2/todo` → `todo`），认不出来就**保留原名** ——
         * 不要乱翻译成「其他」：那会把两类数据混进一个桶，清一边顺手清掉另一边。
         */
        fun domainOf(path: String): String {
            val segs = path.substringBefore('?').trim('/').split('/').filter { it.isNotBlank() }
            val key = when {
                segs.size >= 3 && segs[0] == "api" -> segs[2]
                segs.size >= 2 && segs[0] == "api" -> segs[1]
                segs.isNotEmpty() -> segs[0]
                else -> UNKNOWN_DOMAIN
            }
            // 认不出就照实显示原始段，但**纯数字段**（`/api/v2/1/...` 这类按 id 走的接口）
            // 显示成「按 id 的接口」—— 用户看不出 `1` 是什么，那是我们没给名字，不是他该猜的。
            return LABELS[key] ?: if (key.all { it.isDigit() }) DIGIT_DOMAIN else key
        }

        val LABELS = mapOf(
            "todo" to "待办",
            "my-courses" to "我的课程",
            "course" to "课程内容",
            "calendar" to "校历与日程",
            "notice" to "公告",
            "assignment" to "作业",
            "attendance" to "考勤",
            "grade" to "成绩",
            "todos" to "待办",
            "profile" to "我的资料",
            "my" to "我的资料",
            "exam" to "考试安排",
            "exams" to "考试安排",
            "score" to "成绩",
            "scores" to "成绩",
        )

        fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

/**
 * 一类缓存占用的快照。
 *
 * @param name 领域名（多半来自 [ResponseCache.domains] 的分组，已转成用户看得懂的中文）
 * @param entries 条数
 * @param bytes 字节数
 */
data class CacheDomain(
    val name: String,
    val entries: Int,
    val bytes: Long,
)
