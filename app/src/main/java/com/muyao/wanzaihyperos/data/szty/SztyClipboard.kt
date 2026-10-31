package com.muyao.wanzaihyperos.data.szty

/**
 * 从「剪贴板里那段抓包原文」里捞出 token 与 userId。
 *
 * ### 为什么单独抽出来
 *
 * 用户在手机上复制到的东西**不是干净的一个值**：可能是一整行请求头
 * （`token: SPORT_API_…`）、也可能是整个请求体的 JSON。而 token 是 54 个字符、
 * 带着 `+` `/` `=` `_`，让人照着敲等于让他必然敲错一次，而服务端对敲错的
 * 回应只有一句「token 失效」——错在哪看不出来。
 *
 * 所以这里做两件事：① 从任意一段文本里认出 token（它自带 `SPORT_API_` 前缀，最好认）；
 * ② 认出 userId —— **但只认明确写着 `userId` 的那一种**。
 *
 * ### ★ 为什么 userId 不许「猜出一串数字」
 *
 * 抓包原文里同时存在学号（8 位，例 32500000）和 userId（5 位，例 80000）。
 * 服务端是按 body 里的 userId 认人的，喂学号回去只会得到一条含糊的失败。
 * 放开「取第一个数字」就等于允许把学号填进去 —— 那是个必然错、且错得看不懂的结果。
 * 所以只接受 `userId` 后面紧跟的那个数。
 *
 * 做成纯函数是为了能在单测里把上面这些情况逐条钉住（见 SztyClipboardTest）。
 */
data class SztyPasted(
    val token: String?,
    val userId: Int?,
) {
    val isEmpty: Boolean get() = token == null && userId == null
}

/** 数智体育 token 的固定前缀。界面校验与抽取共用这一个常量，避免两处各写一遍。 */
const val SPORT_TOKEN_PREFIX = "SPORT_API_"

/** token 的字符集：官方是 base64（`+` `/` `=`）+ 下划线。 */
private val TOKEN_RE = Regex("""SPORT_API_[A-Za-z0-9_+/-=]+""")

/** userId：**只认带字段名的**。允许 `userId: 80000` / `"userId":80000` / `userId=80000`。 */
private val USER_ID_RE = Regex("""userId\"?\s*[:=]\s*(\d{1,9})""")

fun parseSztyPaste(clip: String?): SztyPasted {
    if (clip.isNullOrBlank()) return SztyPasted(null, null)
    // 只留「前缀之后还有内容」的匹配：光一个前缀不算 token。
    val token = TOKEN_RE.find(clip)?.value?.takeIf { it.length > SPORT_TOKEN_PREFIX.length }
    val userId = USER_ID_RE.find(clip)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
    return SztyPasted(token = token, userId = userId)
}
