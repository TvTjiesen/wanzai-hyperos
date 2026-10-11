package com.muyao.wanzaihyperos.net

/**
 * 网络层向会话层要凭据的**唯一接口**。
 *
 * 为什么要有这层接口，而不是让 [HzcuClient] 直接依赖会话实现：
 * 1. 网络层因此可以**独立测试**（用一个返回固定 token 的假实现即可），
 *    不必把 Keystore / EncryptedSharedPreferences 拖进 JVM 单测；
 * 2. 依赖方向单向：`net` 不认识 `session`，凭据怎么存是会话层的自由，
 *    将来从 EncryptedSharedPreferences 换成别的实现不需要动网络层一行代码。
 *
 * 实现方（[com.muyao.wanzaihyperos.session.PortalSession]）必须保证：
 * - 返回值**绝不落日志**；
 * - 读不到时返回 null（未登录），**不抛异常** —— 抛出去会把首页拖崩。
 */
interface PortalCredentials {

    /** 门户 ACCESS_TOKEN。未登录返回 null。 */
    fun accessToken(): String?

    /**
     * 门户会话 Cookie 的 JSON（host → {name: value}）。
     *
     * ★ 必须与 token 一起发（2026-10-09 真机抓包定位）：
     * 门户的 ACCESS_TOKEN 是**与 CAS 会话 Cookie 绑定**的。只发 token 不带 Cookie，
     * 服务端一律回 `{"code":401,"message":"token 不能为空"}` —— 它对「没带」
     * 和「带了但无效」不做区分。修前 App 的真实请求里一个 Cookie 头都没有，
     * 于是「登录成功」但每个业务接口都 401。
     */
    fun portalCookiesJson(): String?

    /**
     * 把 cookie 罐的 JSON 交回会话层落盘。
     *
     * 由 [HzcuClient.persistCookieJar] 在网络层收到 `Set-Cookie` 之后调用 ——
     * 网络层不认识加密存储，只负责说「这些 cookie 现在是新的了」。
     */
    fun savePortalCookies(json: String)

    /**
     * 登录成功后落盘。token 为空视为脏数据，**实现方必须拒绝** ——
     * 存进去只会让后面每个接口都 401，还不如让调用方立刻发现。
     *
     * ★ 与 [savePortalCookies] 必须**一起**调用（见 [HzcuCasService.finish]）：
     * 门户的 ACCESS_TOKEN 与 CAS 会话 Cookie 绑定，只落其一，业务接口会全部 401。
     */
    fun saveAccessToken(accessToken: String, userCode: String?)

    /** 退出登录：内存与存储一起清。 */
    fun clear()
}
