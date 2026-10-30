package com.muyao.wanzaihyperos.data.tronclass

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 扫到的签到码**解码器**（学校 H5 的 "/j?p=..." 那一种）。
 *
 * ## 这串码长什么样
 *
 * 业主 2026-10-29 真机扫到的原文（files/tron_raw_scanned.txt）：
 * `/j?p=0~<0x10>1g6b!3~0123456789abcdef0123456789abcdef12345678!4~<0x10>5cmw`
 *
 * p= 后面用 ! 分段，每段 "键~值"；键是十进制下标转 36 进制（0=courseId、
 * 3=data、4=rollcallId）。值前面那个 **0x10 是「后面的数字是 36 进制」的标记**，
 * 0x1E / 0x1F 分别是被转义的 ! 与 ~。
 *
 * ## 为什么必须解它（这决定签到能不能用）
 *
 * 官方提交是 PUT /api/rollcall/{id}/answer_qr_rollcall，包体
 * {"data":"<3 号段的值>","deviceId":"<uuid>"}（mobile.hzcu.edu.cn/js/1154.*.js
 * 里的 n={data:r.data,deviceId:D.Z.deviceId} 逐字）。
 * **码里自带 rollcallId** ==> 扫到码不必等这条签到出现在「进行中」清单里，
 * 直接用码里的 id 提交。
 *
 * 解码表来自 mobile.hzcu.edu.cn 的 webpack 模块 84272
 * （chunk-vendors-*.js 偏移 39906 的 la / decodeVal）。
 */
internal object RollcallQrCode {

    /** 值里真的出现 ! / ~ 时的转义形态。 */
    private const val ESC_BANG: Char = '\u001E'
    private const val ESC_TILDE: Char = '\u001F'

    /** 特殊枚举（classroom-exam / feedback / vote）的前缀。 */
    private const val SPECIAL: Char = '\u001A'

    /** 「后面的数字是 36 进制」的前缀。 */
    private const val RADIX: Char = '\u0010'

    /** 键表原文：courseId / activityId / activityType / data / rollcallId / ... 按顺序编号。 */
    private val KEY_NAMES = listOf(
        "courseId", "activityId", "activityType", "data", "rollcallId",
        "groupSetId", "accessCode", "action", "enableGroupRollcall", "createUser", "joinCourse",
    )

    /** 36 进制键 -> 字段名（0->courseId、3->data、4->rollcallId）。 */
    private val KEY_OF: Map<String, String> =
        KEY_NAMES.withIndex().associate { (i, name) -> i.toString(36) to name }

    /** 从一段码里取出来的东西。 */
    data class Payload(
        /** 这条签到的 id（4 号段）。 */
        val rollcallId: Int?,
        /** 课程 id（0 号段）。 */
        val courseId: Int?,
        /** 要原样提交的 data（3 号段）。 */
        val data: String?,
    )

    /**
     * 解一段扫码原文。
     *
     * 支持两种真实形态：
     * 1. /j?p=...（或直接给 p= 的值）—— 学校 H5 的签到码，见文件头；
     * 2. JSON 对象里带 rollcallId / data（官方分发器 de 的另一支）。
     *
     * **解不出就返回 null，不猜。**（猜错等于把别人的签到当成他的。）
     */
    fun parse(raw: String?): Payload? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        if (text.startsWith("{")) return parseJson(text)

        val p = when {
            text.contains("/j?p=") -> text.substringAfter("/j?p=").substringBefore('&')
            text.startsWith("p=") -> text.removePrefix("p=")
            text.contains('~') -> text
            else -> return null
        }
        if (p.isBlank()) return null

        val fields = LinkedHashMap<String, String>()
        p.split('!').filter { it.isNotEmpty() }.forEach { segment ->
            val cut = segment.indexOf('~')
            if (cut > 0 && cut < segment.length - 1) {
                val key = KEY_OF[segment.take(cut)] ?: segment.take(cut)
                fields[key] = segment.substring(cut + 1)
            }
        }
        val data = fields["data"]?.let { unescape(it) }?.takeIf { it.isNotBlank() }
        val rollcallId = fields["rollcallId"]?.let { radixNumber(it)?.toInt() }
        val courseId = fields["courseId"]?.let { radixNumber(it)?.toInt() }
        if (data == null && rollcallId == null) return null
        return Payload(rollcallId = rollcallId, courseId = courseId, data = data)
    }

    /**
     * 提交用的 data。
     *
     * 解得出就用码里的 3 号段；解不出就把原文当 data 交上去 ——
     * 校验在服务端，我们照实提交、把它的原话回给他，比在这里替他猜一个值强。
     */
    fun submissionData(raw: String?): String {
        val text = raw?.trim().orEmpty()
        return parse(text)?.data?.takeIf { it.isNotBlank() } ?: text
    }

    private fun parseJson(text: String): Payload? {
        val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val id = (obj["rollcallId"] as? JsonPrimitive)?.content?.toIntOrNull()
        val data = (obj["data"] as? JsonPrimitive)
            ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
        val courseId = (obj["courseId"] as? JsonPrimitive)?.content?.toIntOrNull()
        if (id == null && data == null) return null
        return Payload(rollcallId = id, courseId = courseId, data = data)
    }

    /** 值里被转义的 ! ~ 还原；带 0x10 前缀的是 36 进制数字。 */
    private fun unescape(value: String): String {
        radixNumber(value)?.let { return it.toString() }
        if (value.startsWith(SPECIAL)) return value
        return value.replace(ESC_TILDE, '~').replace(ESC_BANG, '!')
    }

    /** 0x10 + 36 进制整数。前端对小数另有分支，签到用不到，这里直接判 null。 */
    private fun radixNumber(value: String): Long? {
        if (!value.startsWith(RADIX)) return null
        val body = value.substring(1)
        if (body.isEmpty() || body.contains('.')) return null
        return body.toLongOrNull(36)
    }
}
