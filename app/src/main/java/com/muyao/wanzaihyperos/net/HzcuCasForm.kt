package com.muyao.wanzaihyperos.net

/**
 * 从 CAS 登录页里提取 `fm1`（账号密码表单）。
 *
 * 为什么自己切字符串而不用 HTML 解析库：这段 HTML 是 Apereo CAS 模板生成的，
 * 结构稳定，而为了读几个 input 就引一个 HTML 解析器进 App 不划算。
 *
 * ### ⚠️ 学校有**两套模板，字段集不一样**（这是本文件的核心事实）
 *
 * | 模板 | 触发条件 | fm1 字段 |
 * |---|---|---|
 * | 桌面版 | 桌面 UA | username / password / authType / lt / execution / _eventId / **validTime** |
 * | **移动版** | **移动 UA（App 用的就是它）** | username / password / authType / lt / execution / _eventId / **randomStr** |
 *
 * 移动版**没有 `validTime`**，多一个 `randomStr`。所以：
 * 1. **绝不能要求「字段一个不少」** —— Flutter 蓝本早先那版就是这么写的，
 *    结果是真机（移动 UA）在第一步就抛「页面结构与实测不一致」，一次都没连上学校。
 * 2. **页面给什么就提交什么**：fm1 里所有带 `name` 的 input 原样进 [pageFields]，
 *    提交时整份带上（浏览器提交表单就是这个语义）。这样学校以后再加隐藏域
 *    （CSRF token 之类）也不用改代码。
 * 3. 只有**登录必需**的字段才做存在性校验：没有 `username`/`password` 就填不了，
 *    没有 `execution`/`_eventId` 就驱动不了 CAS flow。缺这些才抛，并报出缺的是哪个。
 */
class HzcuCasField(val name: String, val value: String) {

    /** 日志形态：**只有名字与值长度，永不含值**。 */
    override fun toString(): String = "$name=<长度 ${value.length}>"
}

class HzcuCasForm private constructor(
    /**
     * 表单 action 原文，形如 `/cas/login;jsessionid=XXXX.1425?service=...`。
     * **必须原样使用**：`;jsessionid=` 是 URL 重写的会话凭据，
     * 它和取到这张表单的那次 GET 属于同一个 flow，丢了就提交不回同一个会话。
     */
    val actionPath: String,
    /**
     * fm1 里所有带 `name` 属性的 input，**按页面出现顺序**原样保留。
     *
     * 这是提交内容的唯一真源：[fields] 就是拿它加上账号密码。
     * 同名 input 有多份时取**第一个**（与浏览器一致）。
     */
    val pageFields: Map<String, String>,
) {

    /** 页面里有没有这个字段（诊断用）。 */
    fun has(name: String): Boolean = pageFields.containsKey(name)

    /**
     * 可选字段的读取：页面没给就返回 null，**不补默认值**。
     *
     * 为什么不能补：`validTime=5` 是桌面模板的值，移动模板压根没这个字段。
     * 给移动模板硬塞一个 `validTime=5`，等于凭空发明一个学校没要求的参数 ——
     * 轻则被忽略，重则被 CAS 判为非法请求。**宁可少发。**
     */
    fun optional(name: String): HzcuCasField? =
        pageFields[name]?.let { HzcuCasField(name, it) }

    /** 必需字段：缺了就抛，并报出**缺的是哪个**（省得真机上抓瞎）。 */
    private fun required(name: String): HzcuCasField {
        val value = pageFields[name]
            ?: throw HzcuException.Parse(
                "fm1 表单缺少必需字段 $name，页面结构与实测不一致",
                details = "已读到的字段：${pageFields.keys.joinToString(",")}",
            )
        return HzcuCasField(name, value)
    }

    /** 账号输入框（必需）。 */
    val username: HzcuCasField get() = required(FIELD_USERNAME)

    /** 密码输入框（必需）。**提交时值是 AES 密文，不是明文。** */
    val password: HzcuCasField get() = required(FIELD_PASSWORD)

    /** CAS flow token，实测是空串（**也要提交**）。 */
    val lt: HzcuCasField get() = required(FIELD_LT)

    /** CAS 扁平化 flow 的执行标识，实测 `e1s1`。 */
    val execution: HzcuCasField get() = required(FIELD_EXECUTION)

    /** 提交动作，实测 `submit`。 */
    val eventId: HzcuCasField get() = required(FIELD_EVENT_ID)

    /** 认证方式，实测 `0`。 */
    val authType: HzcuCasField get() = required(FIELD_AUTH_TYPE)

    /** 桌面模板才有的字段（移动模板为 null）。 */
    val validTime: HzcuCasField? get() = optional("validTime")

    /** 移动模板才有的字段（桌面模板为 null）。 */
    val randomStr: HzcuCasField? get() = optional("randomStr")

    /**
     * 按页面 action 解析出可提交的绝对地址。[pageUri] = 取到这条表单的那次 GET。
     *
     * action 已由 [attrOf] 做过实体还原（`&amp;` → `&`），这里只处理空白。
     * 语义与 Dart 的 `Uri.resolve` 一致：补齐 scheme/host，并**丢掉 pageUri 自己的 query**。
     */
    fun submitUri(pageUri: String): String {
        val trimmed = actionPath.trim()
        if (trimmed.isEmpty()) {
            throw HzcuException.Parse("CAS 表单没有 action，无法确定提交地址")
        }
        // 绝对地址直接可用（学校某些阶段会回完整 URL）。
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return try {
            java.net.URI(pageUri).resolve(trimmed).toString()
        } catch (_: Exception) {
            // 退化路径：按其字面绝对路径拼，仍然保留 action 里的 ;jsessionid 与 query。
            if (trimmed.startsWith("/")) {
                val base = runCatching { java.net.URI(pageUri) }.getOrNull()
                    ?: throw HzcuException.Parse("CAS 表单的 action 无法解析", details = "取到表单的地址不是合法 URI")
                val authority = buildString {
                    append(base.scheme).append("://").append(base.host)
                    if (base.port != -1) append(':').append(base.port)
                }
                authority + trimmed
            } else {
                throw HzcuException.Parse("CAS 表单的 action 是相对路径，无法确定提交地址")
            }
        }
    }

    /**
     * 提交用的字段集：**页面给的全部字段** + 账号 + 密文密码。
     *
     * [encryptedPassword] 必须是 [HzcuCasCrypto.encryptPassword] 的产物 ——
     * 这个方法**拒绝看起来像明文的值**，防的是以后有人改 UI 时忘了调加密。
     */
    fun fields(account: String, encryptedPassword: String): LinkedHashMap<String, String> {
        assertNotPlainPassword(encryptedPassword)
        // 先照抄页面，再覆盖两个「由用户填」的字段。顺序保持页面的顺序。
        val result = LinkedHashMap(pageFields)
        result[username.name] = account
        result[password.name] = encryptedPassword
        return result
    }

    /**
     * 浏览器里提交的是 `$('#password').val(aes_pwd)` 之后的表单 —— 密文。
     * 若这里收到一个「不是合法 Base64」的串，说明调用方根本没加密：
     * 与其把用户明文密码发上 **HTTP 明文链路**（红线），不如当场炸。
     */
    private fun assertNotPlainPassword(value: String) {
        // 注意尾部的 `=` 填充：AES 密文按 16 字节成块，Base64 出来长度必是 4 的倍数，
        // 带 0~2 个 `=`。正则漏掉 `=` 会把合法密文误判成明文（蓝本第一版就踩了）。
        val looksBase64 = BASE64_PATTERN.matches(value) && value.length % 4 == 0
        if (!looksBase64) {
            throw HzcuException.Parse("password 字段不是 Base64 密文形态，拒绝提交明文密码")
        }
    }

    /**
     * 日志形态：**字段名与 action 路径，永不含值**。
     *
     * 打的是整份字段集（不只那几个固定字段）：真机上要能一眼看出
     * 「这次拿到的是桌面模板还是移动模板」，就是靠这里列出的字段名。
     */
    override fun toString(): String {
        val names = pageFields.keys.joinToString(",")
        val action = actionPath.substringBefore(';')
        return "HzcuCasForm(action=$action fields=[$names])"
    }

    companion object {
        const val FIELD_USERNAME = "username"
        const val FIELD_PASSWORD = "password"
        const val FIELD_LT = "lt"
        const val FIELD_EXECUTION = "execution"
        const val FIELD_EVENT_ID = "_eventId"
        const val FIELD_AUTH_TYPE = "authType"

        private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]+={0,2}$")

        fun parse(html: String): HzcuCasForm {
            val match = findFormTag(html, "id=\"fm1\"")
                ?: throw HzcuException.Parse(
                    "登录页里找不到 id=\"fm1\" 的表单（页面改版或已被验证码/短信流程接管）",
                )
            val action = attrOf(match.first, "action")
                ?: throw HzcuException.Parse("fm1 表单缺少 action 属性")

            // 只在 fm1 的范围里找 input，否则会读到 fm2（手机验证码登录）的同名字段。
            val form = HzcuCasForm(
                actionPath = action,
                pageFields = collectInputs(match.second),
            )
            // 必需字段在这里校验一次：让「结构不对」在**取表单**这一步就报出来，
            // 而不是等到提交时才炸（那时已经发过一次 POST，白白多打一次学校服务器）。
            form.username
            form.password
            form.lt
            form.execution
            form.eventId
            form.authType
            return form
        }

        /**
         * 收集 fm1 里所有 `<input name="…">`，按出现顺序，**同名取第一个**。
         *
         * 只收带 `name` 的：没有 `name` 的 input 不会随表单提交，
         * 收进来反而会让提交体多出一个浏览器不会发的字段。
         */
        private fun collectInputs(formHtml: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            for (tag in allInputTags(formHtml)) {
                val name = attrOf(tag, "name")
                if (name.isNullOrEmpty()) continue
                if (result.containsKey(name)) continue // 第一个为准
                result[name] = attrOf(tag, "value") ?: ""
            }
            return result
        }
    }
}

// ---------------------------------------------------------------------------
// 极简 HTML 切片（都在文件作用域，方便单测直接打）
// ---------------------------------------------------------------------------

/** 找到包含 [needle] 的 `<form>` 开标签及其内部 HTML。找不到返回 null。 */
internal fun findFormTag(html: String, needle: String): Pair<String, String>? {
    var cursor = 0
    while (true) {
        val start = html.indexOf("<form", cursor)
        if (start < 0) return null
        val gt = html.indexOf('>', start)
        if (gt < 0) return null
        val openTag = html.substring(start, gt + 1)
        if (openTag.contains(needle)) {
            val bodyStart = gt + 1
            val end = html.indexOf("</form>", bodyStart)
            if (end < 0) {
                throw HzcuException.Parse("fm1 表单没有闭合")
            }
            return openTag to html.substring(bodyStart, end)
        }
        cursor = gt + 1
    }
}

/** fm1 内部所有 `<input …>` 开标签的原文。 */
internal fun allInputTags(formHtml: String): Sequence<String> = sequence {
    var cursor = 0
    while (true) {
        val start = formHtml.indexOf("<input", cursor)
        if (start < 0) return@sequence
        val gt = formHtml.indexOf('>', start)
        if (gt < 0) return@sequence
        yield(formHtml.substring(start, gt + 1))
        cursor = gt + 1
    }
}

/**
 * 读 `attr="value"`。
 *
 * ⚠️ 只认**双引号**形式：CAS 模板固定用双引号（实测两套模板都是）。
 * 顺手支持单引号会带来「值里含单引号」的边界情况，而收益为零。
 */
internal fun attrOf(tag: String, attr: String): String? {
    val needle = "$attr=\""
    val start = tag.indexOf(needle)
    if (start < 0) return null
    val from = start + needle.length
    val end = tag.indexOf('"', from)
    if (end < 0) return null
    return unescapeHtml(tag.substring(from, end))
}

/** 只做 CAS 模板里会出现的那几个实体。 */
internal fun unescapeHtml(value: String): String = value
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
