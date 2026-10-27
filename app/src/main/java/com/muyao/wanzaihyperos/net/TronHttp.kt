package com.muyao.wanzaihyperos.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 学在城院**原生接口**用的 OkHttp 配置 —— 签到/交作业这些**写操作**就是走它。
 *
 * ## 为什么抽成工厂
 *
 * 上一版它内联在 `AppGraph` 里，而单测自己 `OkHttpClient.Builder()` 另起一台
 * （`retryOnConnectionFailure` 吃的是 OkHttp 默认 **true**）⇒
 * **谁把生产这台改坏，测试全绿、业主却被重复提交签到**。
 * 现在生产与测试用**同一支**；测试只在它上面 `.newBuilder()` 加一条改主机的拦截器。
 *
 * ## `retryOnConnectionFailure(false)` 是红线
 *
 * 签到/交作业是写操作 ⇒ **永不重试**。OkHttp 默认会在连接失败时重试一次，
 * 对 GET 无所谓，对 PUT 提交就是"可能送两笔记录"。
 * `TronHttp` 由 `RollcallWriteGuardTest` 守（把它翻成 true ⇒ 守卫红）。
 */
fun tronHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
    .build()
