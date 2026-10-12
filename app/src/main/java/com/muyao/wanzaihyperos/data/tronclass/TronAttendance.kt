package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 某門課的**出勤記錄**（每一次點名我出席的狀況）。
 *
 * ### 契約（活體探測 2026-10-12）
 *
 * ```
 * GET /api/course/{courseId}/student/{studentId}/rollcalls
 * → { rollcalls: [{
 *       rollcall_id, student_rollcall_id, title, rollcall_time, type, source,
 *       status, rollcall_status, student_status, student_status_detail,
 *       is_number, is_radar, is_expired, scored, published_at
 *   }] }   // 實測 5 條
 * ```
 *
 * ⚠️ 路徑是 `course`（**單數**）+ `/student/{我}/rollcalls`。
 * 同一個站的 `/api/courses/{id}/rollcalls` 是**老師端**的（實測 403「您沒有權限」）——
 * 這就是為什麼學號一定要帶上：這個端點問的是「**我**在這門課的出席」。
 *
 * ### ★ 為什麼值得做（B2「考勤」跳過的是另一件事）
 *
 * 業主跳過的 B2 是**門戶/cjh52 的考勤**（另一套系統）。
 * 這裡是學在城院的點名記錄：每次點名的日期、方式、我到了沒有 ——
 * 學生真正會回頭查的東西（「老師說我缺勤，我到底到沒到」）。
 */
data class TronAttendance(
    /** 點名 id（`rollcall_id`）。 */
    val rollcallId: Int,
    /** 標題（`title`，實測是「2026.09.15 09:39」）。 */
    val title: String,
    /** 點名時間（本地顯示）。空字串＝解析不出來。 */
    val timeLocal: String,
    /** 點名方式（`source`，如 `qr` / `number`）。 */
    val source: String,
    /** 我在這次點名的狀態（`student_status`）。 */
    val myStatus: String,
) {
    /** 方式翻成人話；認不出來原樣顯示（**不編**）。 */
    val sourceLabel: String
        get() = when (source) {
            "qr", "qr_rollcall", "roomis" -> "扫码"
            "number" -> "数字"
            "radar" -> "雷达"
            "self_registration" -> "自助"
            "manual" -> "老师点名"
            "auto_attendance" -> "自动"
            "merged_rollcall" -> "合并"
            else -> source.ifBlank { "点名" }
        }

    /** 出席狀態翻成人話；認不出來原樣顯示。 */
    val statusLabel: String
        get() = when (myStatus) {
            "on_call" -> "已到"
            "on_leave" -> "请假"
            "absent" -> "缺勤"
            "late" -> "迟到"
            "" -> "—"
            else -> myStatus
        }

    /** 是否算「到了」（界面用它上色）。 */
    val attended: Boolean get() = myStatus == "on_call"
}

object TronAttendanceParser {

    /**
     * 外層接 `{rollcalls:[...]}`、`{data:[...]}` 與裸 `[...]` 三種
     *（這個站的回應外層不統一，寧可多接一種）。
     */
    fun parseList(root: kotlinx.serialization.json.JsonElement?): List<TronAttendance> {
        val array = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is JsonObject -> root["rollcalls"].asArrayOrNull() ?: root["data"].asArrayOrNull()
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { it.asObjectOrNull() }.mapNotNull { parseOne(it) }
    }

    fun parseOne(obj: JsonObject): TronAttendance? {
        val id = obj["rollcall_id"].asIntOrNull() ?: return null
        val raw = obj["rollcall_time"].asStringOrNull().orEmpty()
        return TronAttendance(
            rollcallId = id,
            title = obj["title"].asStringOrNull().orEmpty(),
            timeLocal = TronTodoParser.parseIsoMillis(raw)
                ?.let { formatTronLocalTime(it) }
                .orEmpty(),
            source = obj["source"].asStringOrNull().orEmpty(),
            myStatus = obj["student_status"].asStringOrNull().orEmpty(),
        )
    }
}
