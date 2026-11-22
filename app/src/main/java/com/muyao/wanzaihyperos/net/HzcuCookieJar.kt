package com.muyao.wanzaihyperos.net

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 最小 Cookie 罐。CAS 链路需要跨请求保留 `CASTGC` / `JSESSIONID` / `route`，
 * 而这三者是**会话凭据**。
 *
 * **按 host 分桶**（RFC 6265 最简版：只认 host，不做 domain 后缀与 path 匹配）。
 * 这不是洁癖：Flutter 蓝本上一版把 cookie 存成一张「全局名→值」的表，
 * 于是同一个罐一旦被 CAS 流程和门户调用共用，`CASTGC` 就会原样发给 `iappdd`、
 * 门户的 `SESSION` 反过来发给 `ca` —— 会话凭据跨系统漂移，正是 CAS 要防的事，
 * 也踩「凭据永不外泄」红线。**分桶之后，A 域的 cookie 在物理上不可能出现在发往 B 域的请求里。**
 *
 * 这里实现 OkHttp 的 [CookieJar] 接口，而不是自己造一套：
 * OkHttp 的 `loadForRequest(url)` 天然是「按目标 host 取」的语义，
 * 与分桶要求完全一致，而且重定向链上的每一跳都会重新问一次罐子
 * —— CAS 恰恰是靠跳转传 `CASTGC` 的，自己实现容易在跳转这一环漏掉。
 *
 * ### 落盘
 * [toJson] / [fromJson] 是**凭据序列化**：调用方只允许把它交给加密存储
 * （[com.muyao.wanzaihyperos.session.SecureStore]），
 * **禁止打印、禁止写普通 SharedPreferences / 文件 / 日志**。
 */
class HzcuCookieJar : CookieJar {

    @Volatile private var boundEpoch: AccountEpoch? = null
    private val byHost = mutableMapOf<String, MutableMap<String, Cookie>>()

    internal fun bindEpoch(epoch: AccountEpoch?) {
        if (epoch == null || boundEpoch === epoch) return
        check(boundEpoch == null) { "Cookie jar already has an owner boundary" }
        boundEpoch = epoch
        epoch.registerReset { clear() }
    }

    internal fun forRequest(epoch: AccountEpoch?, token: Long?): CookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (epoch == null) {
                if (boundEpoch != null) throw CancellationException("Account changed")
                return this@HzcuCookieJar.loadForRequest(url)
            }
            if (token == null) throw CancellationException("Account changed")
            var result: List<Cookie> = emptyList()
            if (!epoch.publish(token) { result = this@HzcuCookieJar.loadForRequest(url) }) {
                throw CancellationException("Account changed")
            }
            return result
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (epoch == null) {
                if (boundEpoch != null) throw CancellationException("Account changed")
                saveCookies(url, cookies)
            } else if (token == null || !epoch.publish(token) { saveCookies(url, cookies) }) {
                throw CancellationException("Account changed")
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (boundEpoch == null) saveCookies(url, cookies)
    }

    @Synchronized
    private fun saveCookies(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host.lowercase()
        if (host.isEmpty()) return
        val bucket = byHost.getOrPut(host) { mutableMapOf() }
        for (cookie in cookies) {
            if (cookie.name.isEmpty()) continue
            // 空值 = 服务端在删这个 cookie（Set-Cookie: name=; Max-Age=0）。
            if (cookie.value.isEmpty()) bucket.remove(cookie.name) else bucket[cookie.name] = cookie
        }
        if (bucket.isEmpty()) byHost.remove(host)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        byHost[url.host.lowercase()]?.values?.toList() ?: emptyList()

    // ---------------------------------------------------------------------------
    // 诊断：只暴露「名」，永不暴露「值」
    // ---------------------------------------------------------------------------

    val hostNames: Set<String> get() = synchronized(this) { byHost.keys.toSet() }

    /** 某个 host 名下的 cookie 名（不含值），给测试和诊断用。 */
    @Synchronized
    fun namesFor(host: String): Set<String> = byHost[host.lowercase()]?.keys?.toSet() ?: emptySet()

    /** 某个 host 名下有没有这个 cookie（诊断用，不返回值）。 */
    @Synchronized
    fun hasCookie(host: String, name: String): Boolean =
        byHost[host.lowercase()]?.containsKey(name) == true

    val isEmpty: Boolean get() = synchronized(this) { byHost.isEmpty() }

    @Synchronized
    fun clear() = byHost.clear()

    /**
     * ⚠️ **返回值就是凭据**：调用方只允许把它交给 WebView 的 CookieManager
     * 或加密存储，禁止打印、禁止拼进异常/日志。
     */
    @Synchronized
    fun valueOf(host: String, name: String): String? =
        byHost[host.lowercase()]?.get(name)?.value

    /** 某 host 名下所有 cookie 的 `name=value` 计数（不含值），用于诊断。 */
    @Synchronized
    fun countFor(host: String): Int = byHost[host.lowercase()]?.size ?: 0

    // ---------------------------------------------------------------------------
    // 落盘 / 恢复
    // ---------------------------------------------------------------------------

    /**
     * 序列化成 JSON（host → {name: value}）。
     *
     * **这是凭据**：调用方只允许把它交给加密存储，禁止打印、禁止写普通文件。
     */
    @Synchronized
    fun toJson(): String {
        val root = buildJsonObject {
            for ((host, bucket) in byHost) {
                if (bucket.isEmpty()) continue
                putJsonObject(host) {
                    for ((name, cookie) in bucket) put(name, cookie.value)
                }
            }
        }
        return root.toString()
    }

    /**
     * 用 [toJson] 的产物**就地替换**当前内容。
     *
     * 与 [fromJson] 的区别很重要：OkHttp 的 `OkHttpClient` 持有的是**这个对象**的引用，
     * 换成一个新罐子不会生效（请求照旧走旧罐）。所以恢复必须改自己的内容，
     * 不能返回新对象。
     */
    @Synchronized
    fun loadFrom(raw: String?) {
        val restored = fromJson(raw)
        byHost.clear()
        byHost.putAll(restored.byHost)
    }

    companion object {
        /**
         * 从 [toJson] 的产物恢复。格式不对时**静默返回空罐** ——
         * 宁可让用户重新登录一次，也不要因为一份坏 cookie 把首页拖崩。
         */
        fun fromJson(raw: String?): HzcuCookieJar {
            val jar = HzcuCookieJar()
            if (raw.isNullOrBlank()) return jar
            return try {
                val root = Json.parseToJsonElement(raw) as? JsonObject ?: return jar
                for ((hostKey, value) in root) {
                    val cookies = value as? JsonObject ?: continue
                    val host = hostKey.lowercase()
                    if (host.isEmpty()) continue
                    val bucket = mutableMapOf<String, Cookie>()
                    for ((name, element) in cookies) {
                        val text = (element as? JsonPrimitive)?.contentOrNull ?: continue
                        if (name.isEmpty() || text.isEmpty()) continue
                        // 单个 cookie 造不出来（比如 host 是非法域名）不该毒掉整个罐子。
                        val cookie = runCatching {
                            Cookie.Builder()
                                .name(name)
                                .value(text)
                                .hostOnlyDomain(host)
                                .path("/")
                                .build()
                        }.getOrNull() ?: continue
                        bucket[name] = cookie
                    }
                    if (bucket.isNotEmpty()) jar.byHost[host] = bucket
                }
                jar
            } catch (_: Exception) {
                HzcuCookieJar()
            }
        }
    }

    /** 只输出 host 与 cookie 名，**值永不外泄**。 */
    @Synchronized
    override fun toString(): String =
        "HzcuCookieJar(" + byHost.entries.joinToString(" ") { (host, bucket) ->
            "$host:${bucket.keys.joinToString(",")}"
        } + ")"
}
