package com.muyao.wanzaihyperos.data

import java.net.URI

/**
 * 点开一个应用入口之后**该怎么打开**。
 *
 * 门户下发的 87 条入口并不是同一类东西：有的是学校自建系统（要和门户共享登录态），
 * 有的是第三方系统（各自独立登录），有的只在内网可达，还有学在城院这种
 * 「同一套统一身份认证、但需要走手机版免登」的特殊情况。
 * 把它们一律当外链扔给 WebView，结果是**一半的入口点开都是登录页**。
 *
 * 这个策略类把「按什么规则选哪条路」收在一处，且**纯函数、可单测**
 * （不碰网络、不碰 Android API），这样 A3 接真机数据时只需改这里的规则表。
 */
sealed interface AppOpenPlan {

    /** 普通外链：直接用 WebView 打开，不做任何登录态注入。 */
    data class Plain(val url: String, val name: String) : AppOpenPlan

    /**
     * 需要先向门户换一次性的 `tempcode`，再带着它打开。
     *
     * ★ tempcode 是**一次性**的，不可缓存、不可复用（重复使用会被服务端拒）。
     * 所以这条路的调用方必须在**每次点击时**现取，而不是启动时取一批存起来。
     */
    data class TempCode(val url: String, val appId: Int, val name: String) : AppOpenPlan

    /**
     * B17：**智慧公寓**（`zhgy.hzcu.edu.cn`）。
     *
     * 它與其他所有 plan 都不同：**App 無法自己完成登入**。
     * 門戶給的入口 `/mob/zuccding/auth` 是釘釘免登，
     * 授權碼由釘釘容器簽發，瀏覽器拿不到；
     * 而真正的入口要一段只存在於使用者手上連結裡的 `code`。
     *
     * 所以這裡只負責「導到閘門頁」，由那一頁決定是請使用者貼連結、
     * 還是直接載入已設好的頁面（見 `ui/web/ZhgyGatePage.kt`）。
     *
     * 完整證據：`docs/B17-WALKTHROUGH.md`、`docs/B17-ZHGY-LINK-PROBE.txt`。
     */
    data object ZhgyGate : AppOpenPlan

    /**
     * 学在城院（TronClass）手机版免登。
     *
     * 链路（真机实测通过）：
     * ```
     * 注入统一认证 SSO cookie → 打开 openid-connect/auth 种子 URL
     *  → SSO 有效：静默 302，直接发授权码（无感）
     *  → SSO 过期：落到 CAS 表单，手输一次密码
     *  → 回到 mobile.hzcu.edu.cn/identity-web-login-callback
     *  → **SPA 自己**用 code 换 token 并写 localStorage，登录完成
     * ```
     * 关键：**code 之后的每一步都交给 SPA 自己做**。我们既不碰 Keycloak token，
     * 也不手写它的 localStorage 结构 —— 少一处漂移就少一类「升级后打不开」。
     */
    data class TronclassFreeLogin(
        val tileUrl: String,
        val callbackUrl: String,
        val name: String,
    ) : AppOpenPlan

    /**
     * 學在城院走**原生頁**（我的課 + 待交作業）。
     *
     * 與 [TronclassFreeLogin] 並存是刻意的：
     * - 原生頁取不到數（或使用者的學在城院會話失效）時，**回退到**
     *   [TronclassFreeLogin] 那條已驗證的 WebView 免登路 —— 那不是死路，
     *   而是有人手動登一次就能繼續用的活路；
     * - 所以這裡帶上原網頁地址，回退時用它。
     *
     * 學在城院功能極多（討論、測驗、點名…），原生只做最常用的兩塊，
     * 其餘在原生頁底部留「改用網頁版」入口。
     */
    data class NativeTronclass(
        /** 原網頁地址（回退用）。 */
        val tileUrl: String,
        val name: String,
    ) : AppOpenPlan

    /**
     * 只在内网可达（地址是字面内网 IP，或已知仅校园网开放）。
     * 校外打开必然失败，所以**先告诉用户**，而不是让他对着白屏等超时。
     */
    data class CampusOnly(val url: String, val name: String) : AppOpenPlan

    /**
     * 「我的課程」走**原生頁**（B1）。
     *
     * ### 為什麼不是直接改成原生、把 WebView 那條刪掉
     *
     * WebView 那條是**已被真機走查驗證過**的路徑（A3/A4），
     * 而原生頁是新的。讓兩條並存、由使用者選，好處是：
     * - 原生頁萬一在真實資料上出問題，使用者仍有一條能用的路；
     * - 原生頁的解析假設（B0 契約）可以在真機上與 WebView 的渲染結果**對照**，
     *   而不是只能相信自己的解析。
     *
     * 所以這個 plan 同時帶上 [tileUrl] —— 原生頁必須提供「用網頁版打開」的出口。
     *
     * ### 為什麼需要 tempcode
     *
     * B0 實測（契約文檔 §2.1）：**門戶簽發的 tempcode 本身就是 CJH52 的 Bearer token**。
     * 所以走原生頁時必須先取一次 tempcode 存進會話 ——
     * 沒有它，原生頁發出的請求會是 401。
     */
    data class NativeCourses(
        /** 原始應用地址（cjh52 的課程頁）。作為「改用網頁版」的出口。 */
        val tileUrl: String,
        /** 門戶應用 id（取 tempcode 時要用）。 */
        val appId: Int,
        val name: String,
    ) : AppOpenPlan

    /**
     * 走**原生頁**的 cjh52 功能（B3：日常通知 / 我的考試 / 我的成長）。
     *
     * 與 [NativeCourses] 分開是因為「哪個功能對應哪個原生頁」的映射不同 ——
     * 課程頁的路徑判定（`/course/student`）與這三個不一樣，
     * 混在一個 plan 裡會讓 `openApp` 那邊需要再判一次。
     */
    data class NativeFeature(
        /** 對應的原生頁（枚舉名，與 `Route.Cjh52Feature` 一致）。 */
        val feature: String,
        /** 原始應用地址，作為「改用網頁版」的出口。 */
        val tileUrl: String,
        val appId: Int,
        val name: String,
    ) : AppOpenPlan

    /** 连地址都没有，或地址无法解析。界面上应当把这条入口禁用而不是崩。 */
    data class Unopenable(val name: String, val reason: String) : AppOpenPlan
}

/**
 * 应用开启策略。
 *
 * ### ⚠️ 规则的可信度分级（务必照实读，别把假设当结论）
 *
 * | 规则 | 依据 | 可信度 |
 * |---|---|---|
 * | 学在城院 → 手机版免登 | 真机实测全链路走通 | **已证实** |
 * | 内网地址 → CampusOnly | `PortalSystem.CAMPUS_ONLY` 的既有定义 | **已证实** |
 * | 门户自建系统 → tempcode | 门户 JS 里确实有该接口，但**哪些 app 需要它尚未逐条实测** | **待验证假设** |
 * | 其余 → Plain | 兜底 | 约定 |
 *
 * 「待验证假设」这一行是 A3 的**必查项**：拿真机数据逐条比对，
 * 不符就改 [isPortalBuiltin] 的判定。A2 阶段不假装它已经成立 ——
 * 写死一个错的映射，比留一条明确的待验证标记危险得多。
 */
object AppOpenStrategy {

    /**
     * 是否啟用「我的課程」原生頁（B1）。
     *
     * ### 現在是 `true`（2026-10-11，B1 會話問題已解）
     *
     * 先前的阻塞：門戶簽發的 tempcode 直接當 Bearer 用會被拒（HTTP 401）。
     * 現已找到解法並**用跨環境對照證實**：
     *
     * ```
     * 網頁載入過的碼 → 交給 JVM(OkHttp) 調 → HTTP 200   ✅
     * 門戶新發的碼   → 交給 JVM(OkHttp) 調 → HTTP 401   ❌
     * ```
     *
     * 同一台機器、同一個 OkHttp、同一組請求頭 —— 唯一差別是那枚碼
     * **有沒有被真實瀏覽器載入過**。所以只要先開一次 0 尺寸 WebView
     * 載入帶碼的頁面，碼就可用。實現見 [com.muyao.wanzaihyperos.ui.web.Cjh52WarmUp]。
     *
     * 已逐項排除的候選（都做過對照實驗）：簽名的值與存在性、`App-Code`、
     * `platform`、`lang`、cookie、`Origin`、`Referer`、`Accept`、`Accept-Language`、
     * `User-Agent`、`Accept-Charset`、**HTTP 版本**、**取碼參數**、**正確簽名**、
     * **用 OkHttp 帶碼 GET 一次頁面**。完整記錄見 `docs/B1-WALKTHROUGH.md`。
     *
     * ### 業主先前提的「與學在城院重複」
     *
     * 那條仍然成立，但既然會話問題已解，保留這條原生入口成本很低
     * （UI 與解析都已完成、測試盯著），而原生頁比 WebView 快得多。
     * 若日後仍覺得多餘，把這個常量改回 `false` 即可 —— 一行的事。
     */
    const val ENABLE_NATIVE_COURSES = true

    /** TronClass 系的域，命中即走手机版免登。**实测确认**。 */
    private val TRONCLASS_HOSTS = setOf(
        "courses.hzcu.edu.cn",
        "mobile.hzcu.edu.cn",
    )

    /** 学在城院在门户里的名字。名字兜底是因为门户下发的 `url` 有时是空串。 */
    private val TRONCLASS_NAMES = listOf("学在城院", "TronClass", "超星")

    /**
     * 判「是不是门户自建系统」——**待 A3 实测验证的假设**。
     *
     * 依据：门户自己的 JS 里有 `gettempcode` 这类接口，且 `CJH52`（场景管理）
     * 与 `JSP`（办事审批）都是学校自建、与门户共用统一身份认证的页面。
     * 独立第三方系统（`STANDALONE`）各自有登录，不该被塞 tempcode。
     */
    private fun isPortalBuiltin(system: PortalSystem): Boolean =
        system == PortalSystem.CJH52 || system == PortalSystem.JSP

    /**
     * 决定怎么打开 [app]。
     *
     * 纯函数：同样的输入永远给同样的输出，不读全局状态、不发请求。
     */
    fun decide(app: PortalApp): AppOpenPlan {
        val host = hostOf(app.url)

        // ★ B17：**智慧公寓優先判定，而且要在所有 WebView 分支之前**。
        //
        // 為什麼不能讓它落到一般的 WebView：門戶給它的入口
        // `/mob/zuccding/auth` 是釘釘免登，在沒有釘釘的環境裡**必定**顯示
        // 「[1001] 授權碼已過期或不存在」—— 直接開等於給使用者一個壞頁面。
        // 它需要的是一段只存在於使用者手上連結裡的 `code`，
        // 所以導到閘門頁去要那一段（見 ui/web/ZhgyGatePage.kt）。
        if (host == HzcuHosts.ZHGY) {
            return AppOpenPlan.ZhgyGate
        }

        // ★ 顺序要紧：**先判学在城院，再判校园网**。
        //
        // 门户把「学在城院」归在「仅校园网」那一组里（历史实测清单就是这么放的），
        // 但它的手机版是钉钉 H5，**校外完全可用**（真机实测落地 mobile.hzcu.edu.cn/home）。
        // 先判校园网的话，这条入口会被错误地提示成「仅校园网可达」，
        // 用户在校外永远看不到免登入口 —— 功能被一个分类标签吃掉。
        if (isTronclass(host, app.name)) {
            // ★ 走**原生頁**（我的課 + 待交作業）。
            //
            // 先前這裡直接回 TronclassFreeLogin（WebView 免登）。現在改原生 ——
            // 取不到數時由原生頁自己回退到同一條 WebView 路，所以不會變成死路。
            return AppOpenPlan.NativeTronclass(
                tileUrl = app.url.ifBlank { "https://${HzcuHosts.TRONCLASS_MOBILE}" },
                name = app.name,
            )
        }

        if (app.needsCampusNetwork) {
            return AppOpenPlan.CampusOnly(url = app.url, name = app.name)
        }

        if (app.url.isBlank()) {
            return AppOpenPlan.Unopenable(app.name, "门户没有下发地址")
        }
        if (host == null) {
            return AppOpenPlan.Unopenable(app.name, "地址无法解析")
        }

        // ★ 红线：tempcode 是**凭据**，只允许发往学校自己的域。
        //
        // 计划 §A3 必守契約明文写着「原始 URL 先驗證…**避免將憑據帶到外部域名**」。
        // 子系统地址是门户下发的，理论上都指回学校；但只要有一条指向外部域，
        // 拼上 tempcode 就等于把一次性凭据交出去了。所以这里按 host 再验一道：
        // **不是学校域就不拼码**，退回普通外链（打不开是对方的登录页，至少没泄漏）。
        if (isPortalBuiltin(app.system) && isSchoolHost(host)) {
            // ★ B1：其中「我的課程」這一條走**原生頁**。
            //
            // 只放行這一個入口，其餘 cjh52 應用（學生幹部、困難學生、意見直通車……）
            // 仍走 WebView —— 原生只做了課表，把別的也導過去只會得到空白頁。
            // 判據用「host + 路徑」而不是應用名：應用名在門戶上可能改，
            // 而 `/course/student` 是 cjh52 自己的路由（B0 路由表已核對）。
            if (ENABLE_NATIVE_COURSES && isCjh52CoursePage(host, app.url)) {
                return AppOpenPlan.NativeCourses(
                    tileUrl = app.url,
                    appId = app.id,
                    name = app.name,
                )
            }
            // ★ B3 剩餘三項：日常通知 / 我的考試 / 我的成長。
            //
            // 同樣只放行**做了原生頁的那三個路徑**，其餘 cjh52 應用
            // （學生幹部、愛講座、失物招領……）仍走 WebView ——
            // 導過去只會得到空白頁。
            nativeFeatureOf(host, app.url)?.let { feature ->
                return AppOpenPlan.NativeFeature(
                    feature = feature,
                    tileUrl = app.url,
                    appId = app.id,
                    name = app.name,
                )
            }
            return AppOpenPlan.TempCode(url = app.url, appId = app.id, name = app.name)
        }

        // ★ B4 一卡通：原生化**已尝试并放弃**，继续走 WebView。
        //
        // 真机逐步实测（签名公式已用抓包反推验证正确）：
        //   GetAccInfoByPercode → 成功（账号、状态、卡号都拿到了）
        //   GetWalletMoney      → `code 40004 无效的access_token`
        //   getYMUserByUserID   → `code 0 获取参数不能为空`（换不到那个 token）
        //
        // 余额接口要一个 `etToken` 授权头，它来自**第三方认证**（云马
        // `auth.xiaofubao.com`，要先走它自己的授权码流程）。
        // 只做账户不做余额的原生页没有意义（用户打开一卡通就是为了看余额），
        // 所以**不做半个页面**，保留 WebView（真机验证过可用、看到余额、
        // 手动登录一次后浏览器会记住）。
        //
        // 若后续要攻：路径是把云马的授权码流程走通（外部第三方服务，
        // 风险与收益不成比例）。签名公式已记在 docs/B4-WALKTHROUGH.md。

        return AppOpenPlan.Plain(url = app.url, name = app.name)
    }

    /**
     * 是不是 cjh52 的「我的課程」頁。
     *
     * **同時**判 host 與路徑前綴：
     * - 只判 host 會把 cjh52 的所有應用都導去課表；
     * - 只判路徑會誤中其它系統裡同名的路徑。
     *
     * 路徑用**前綴**匹配（`/course/student` 之後可能帶 query，也可能帶 `/history` 子頁）——
     * 但 `/course/teacher`（教師端）不會命中，因為前綴是 `/course/student`。
     */
    private fun isCjh52CoursePage(host: String?, url: String): Boolean {
        val h = host?.lowercase() ?: return false
        if (h != HzcuHosts.CJH52) return false
        val path = runCatching { java.net.URI(url).path }.getOrNull() ?: return false
        return path.startsWith("/course/student")
    }

    /**
     * cjh52 的路徑 → 原生頁的枚舉名；不是那三個就 null。
     *
     * **判據只用路徑**（不看應用名）：應用名在門戶上可能改，
     * 而路徑是 cjh52 自己的路由（B0 的路由表已核對過）。
     *
     * 只放行**已經做了原生頁**的三個路徑。其餘一律 null →
     * 退回 WebView。**這是刻意的保守**：導去沒有實作的原生頁只會得到空白。
     */
    private fun nativeFeatureOf(host: String?, url: String): String? {
        val h = host?.lowercase() ?: return null
        if (h != HzcuHosts.CJH52) return null
        val path = runCatching { java.net.URI(url).path }.getOrNull() ?: return null
        return when {
            // 用 startsWith 而不是 ==：門戶下發的地址可能帶子路徑。
            path.startsWith("/notice") -> "NOTICE"
            path.startsWith("/examination") -> "EXAM"
            path.startsWith("/development") -> "GROWTH"
            path.startsWith("/clazz") -> "CLAZZ"
            // B16：愛講座（門戶應用 id 54，路徑 /lecture/lectureList）。
            // ★ 只放行**做了原生頁**的路徑 —— 其餘 cjh52 應用（學生幹部、
            //   困難學生、意見直通車…）仍走 WebView，不然會得到空白原生頁。
            path.startsWith("/lecture") -> "LECTURE"
            else -> null
        }
    }

    /**
     * 是不是学校自己的域。**tempcode 只允许拼给这些域。**
     *
     * 判据是后缀匹配 `hzcu.edu.cn`（含裸域本身）。故意**不**用「包含 hzcu」这种松判据 ——
     * `hzcu.edu.cn.evil.com` 这种域名会骗过 `contains`。
     */
    fun isSchoolHost(host: String): Boolean {
        val normalized = host.lowercase().trim()
        if (normalized.isEmpty()) return false
        return normalized == "hzcu.edu.cn" || normalized.endsWith(".hzcu.edu.cn")
    }

    private fun isTronclass(host: String?, name: String): Boolean {
        if (host != null && host in TRONCLASS_HOSTS) return true
        return TRONCLASS_NAMES.any { name.contains(it, ignoreCase = true) }
    }

    /** 取 URL 的 host（小写）。解析不了返回 null，**不抛异常**。 */
    fun hostOf(url: String): String? {
        if (url.isBlank()) return null
        return runCatching { URI(url.trim()).host?.lowercase() }.getOrNull()
    }

    /**
     * 拼「换码后」的子系统地址。**逐字照抄门户自己的 JS**
     * （`docs/API-PORTAL.md` §3 Step 3，从门户反混淆代码里扒出来的）。
     *
     * ```js
     * if (color != null && color != "") url += "&dd_nav_bgcolor=" + color.replace('#','');
     * url += "&appid=" + app.id + "&userid=-1" + "&code=" + app_type
     *      + "&trackid=" + Date.now() + "&devicetype=5" + "&mode=" + MODE
     *      + "&tempcode=" + tempcode;
     * if (url.indexOf("?") == -1) url = url.replace("&", "?");   // 首个 & 要换成 ?
     * ```
     *
     * ### 两个必须照抄、不能「顺手改好」的地方
     * 1. **首个 `&` 换 `?` 只在该地址原本没有 query 时做。** 门户 JS 用的就是
     *    `indexOf("?") == -1` 这个判据；改成「总是补 `?`」会让本来带 query 的地址多出一个 `?`。
     * 2. **`trackid` 用毫秒时间戳**，由调用方传进来（不在这里读时钟）——
     *    这样它是纯函数，可以单测，也不会在重算时悄悄变值。
     *
     * [colorArgb] 为 null 时**整段 `dd_nav_bgcolor` 都不加**（门户 JS 也是这个语义：
     * 颜色为空就不加）。这个参数只是钉钉容器的导航栏配色提示，在我们自己的 WebView 里没用，
     * 但**保留不影响**，且少了它就与门户行为不一致。
     */
    fun buildTempCodeUrl(
        appUrl: String,
        appId: Int,
        colorArgb: Int?,
        tempCode: String,
        nowMillis: Long,
        userTypeCode: String = "",
        mode: String = "prod",
    ): String {
        val builder = StringBuilder(appUrl)
        if (colorArgb != null) {
            // 门户给的是 "#FF108ee9" 这种，去掉 '#' 原样带上；这里从 ARGB 还原成 8 位大写十六进制。
            builder.append("&dd_nav_bgcolor=").append(String.format("%08X", colorArgb))
        }
        builder.append("&appid=").append(appId)
            .append("&userid=-1")
            .append("&code=").append(userTypeCode)
            .append("&trackid=").append(nowMillis)
            .append("&devicetype=5")
            .append("&mode=").append(mode)
            .append("&tempcode=").append(tempCode)

        val url = builder.toString()
        return if (!url.contains('?')) url.replaceFirst("&", "?") else url
    }
}

/**
 * 策略里要用到的域名。
 *
 * 单独一个简短常量组，是为了**不让 `data` 层依赖 `net` 层** ——
 * 应用开启策略是纯数据逻辑，不该因为要用一个域名字符串就把整个网络层拖进来
 * （那会让它在单测里需要 OkHttp）。
 * 值必须与 `net.HzcuEndpoints` 保持一致，**改一处必须改两处**；
 * 单测 `AppOpenStrategyTest` 里有一条断言专门盯这个一致性。
 */
object HzcuHosts {
    const val TRONCLASS_MOBILE = "mobile.hzcu.edu.cn"
    const val TRONCLASS_DESKTOP = "courses.hzcu.edu.cn"
    const val IDENTITY = "identity.hzcu.edu.cn"

    /** CJH52（場景管理）。B1 的原生課表走這個域。 */
    const val CJH52 = "cjh52.hzcu.edu.cn"

    /**
     * 智慧公寓（B17）。
     *
     * ⚠️ 這是**唯一一個需要使用者提供憑據**的子系統 ——
     * 門戶給的入口是釘釘免登，瀏覽器拿不到授權碼。
     * 見 [AppOpenPlan.ZhgyGate] 與 `docs/B17-WALKTHROUGH.md`。
     */
    const val ZHGY = "zhgy.hzcu.edu.cn"
}
