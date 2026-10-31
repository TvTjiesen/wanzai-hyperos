package com.muyao.wanzaihyperos.data.szty

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asDoubleOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 数智体育（ty.hzcu.edu.cn）的三块只读数据。**只读**，一条写接口都不碰。
 *
 * ## 为什么字段全是「可能没有」
 *
 * 三块数据的服务端形状都不稳定，实测（2026-10-31 真账号）：
 *
 * 1. 体测 grade 是字符串，可能是 "80.0" 也可能是 "缺项"；
 *    单项 data 可能是空串（50 米没测）—— 空串必须读成「未测」，**不是 0**。
 * 2. 校园跑里「跑步」那条有 targetDistance，「总目标」那条没有；
 *    只有「总目标」有 targetNumber。两条要分开取，**不能相加**。
 * 3. 一次都没签到时 sign/getSignClassStage 回 rel="没有签到期次"、obj=null，
 *    这是**正常态**不是错误。
 *
 * 所以这里一律用可空字段 + [SztyParsers] 的宽松读取，
 * 界面上「没取到」与「确实是 0」必须长得不一样。
 */

/** 体测单项。value 为空（含服务端给的空串）表示**未测**。 */
data class SztyFitnessItem(
    val name: String,
    val value: String?,
    val score: Double?,
)

/** 一个学年的体测成绩。 */
data class SztyFitnessYear(
    /** 学年标签（服务端的 seasonName，缺失时退回 year / title）。 */
    val year: String,
    /** 服务端原文的成绩，可能不是数字（"缺项"）。 */
    val gradeText: String?,
    /** 能解析成数字的成绩；"缺项" 时为 null。 */
    val score: Double?,
    /** 等级（"良好" / "优秀"），可能是空串。 */
    val level: String?,
    val items: List<SztyFitnessItem>,
)

/**
 * 校园跑。
 *
 * run* 来自服务端那两条汇总里的「跑步」一条，
 * total* 来自「总目标」一条（含其它类型，**不是跑步的**）。
 */
data class SztyRun(
    val runKm: Double?,
    val runTargetKm: Double?,
    val runCount: Int?,
    val totalKm: Double?,
    val totalCount: Int?,
    val totalTargetCount: Int?,
)

/** 体育课班级（一学期通常只有一条）。 */
data class SztyPeClass(
    val classId: Int,
    val name: String,
    val week: String?,
    val periods: String?,
    val address: String?,
)

/** 一次课的签到记录。status 是服务端原文（"已签到" / "请假" / "未签到"）。 */
data class SztyAttendance(
    val title: String,
    val status: String,
)

/** 汇总。服务端只给已签到/迟到/请假/旷课四个数，缺一个就不显示那一个。 */
data class SztyAttendanceSummary(
    val signed: Int?,
    val late: Int?,
    val leave: Int?,
    val absent: Int?,
    /** 服务端自己写的一句话（"旷课0次,请假2次…"），直接用它，不自己拼。 */
    val headStatistics: String?,
)

/**
 * 一屏能渲染的全部内容 + **哪些块没取到**。
 *
 * notes 是给界面看的诚实说明：三块数据各自可以失败，
 * 一块失败不该把另外两块也藏起来，但也不能假装它俩都成功。
 */
data class SztySnapshot(
    val fitness: List<SztyFitnessYear> = emptyList(),
    val run: SztyRun? = null,
    val peClass: SztyPeClass? = null,
    val attendance: List<SztyAttendance> = emptyList(),
    val attendanceSummary: SztyAttendanceSummary? = null,
    val notes: List<String> = emptyList(),
)

/**
 * 把服务端的 obj 解析成上面那些类型。
 *
 * 全是不碰网络的纯函数 —— 真机上的形状靠它们兜住，单测直接喂真响应片段。
 */
object SztyParsers {

    /** health/getMyGrade 的 obj（按学年倒序的数组）。 */
    fun fitnessYears(root: JsonElement?): List<SztyFitnessYear> {
        val rows = root.asArrayOrNull() ?: return emptyList()
        return rows.mapNotNull { row ->
            val o = row.asObjectOrNull() ?: return@mapNotNull null
            val year = o.text("seasonName") ?: o.text("year") ?: o.text("title") ?: ""
            SztyFitnessYear(
                year = year,
                gradeText = o.text("grade"),
                score = o["allScore"].asDoubleOrNull(),
                level = o.text("level"),
                items = fitnessItems(o["scoreData"]),
            )
        }
    }

    private fun fitnessItems(root: JsonElement?): List<SztyFitnessItem> {
        val rows = root.asArrayOrNull() ?: return emptyList()
        return rows.mapNotNull { row ->
            val o = row.asObjectOrNull() ?: return@mapNotNull null
            val name = o.text("name") ?: return@mapNotNull null
            SztyFitnessItem(
                name = name,
                // asStringOrNull 已经把空串读成 null —— 「未测」正是这个语义。
                value = o.text("data"),
                score = o["score"].asDoubleOrNull(),
            )
        }
    }

    /**
     * exerciseRecord/getSemesterExerciseList 的 obj。
     *
     * 两条汇总按 info.eventName 区分：「跑步」是校园跑，「总目标」是含其它类型的合计。
     * **取不到任何一条就返回 null**（让界面说「没取到」，而不是显示 0 公里）。
     */
    fun run(root: JsonElement?): SztyRun? {
        val rows = root.asArrayOrNull() ?: return null
        val entries = rows.mapNotNull { it.asObjectOrNull() }
        if (entries.isEmpty()) return null
        val runEntry = entries.firstOrNull { it.eventName() == "跑步" }
        val totalEntry = entries.firstOrNull { it.eventName() == "总目标" }
        if (runEntry == null && totalEntry == null) return null
        return SztyRun(
            runKm = runEntry?.get("effectiveDistance").asDoubleOrNull(),
            runTargetKm = runEntry?.get("targetDistance").asDoubleOrNull()?.takeIf { it > 0.0 },
            runCount = runEntry?.get("number").asIntOrNull(),
            totalKm = totalEntry?.get("effectiveDistance").asDoubleOrNull(),
            totalCount = totalEntry?.get("number").asIntOrNull(),
            totalTargetCount = totalEntry?.get("targetNumber").asIntOrNull()?.takeIf { it > 0 },
        )
    }

    /** teaching/semesterStuEduClass 的 obj：一学期通常一条。 */
    fun peClass(root: JsonElement?): SztyPeClass? {
        val rows = root.asArrayOrNull() ?: return null
        val o = rows.firstNotNullOfOrNull { it.asObjectOrNull() } ?: return null
        val classId = o["classId"].asIntOrNull() ?: return null
        return SztyPeClass(
            classId = classId,
            name = o.text("name").orEmpty(),
            week = o.text("week"),
            periods = o.text("jc"),
            address = o.text("address"),
        )
    }

    /** sign/stuAttendanceList 的 obj。 */
    fun attendance(root: JsonElement?): List<SztyAttendance> {
        val rows = root.asArrayOrNull() ?: return emptyList()
        return rows.mapNotNull { row ->
            val o = row.asObjectOrNull() ?: return@mapNotNull null
            val name = o.text("name") ?: return@mapNotNull null
            SztyAttendance(title = name, status = o.text("signInType").orEmpty())
        }
    }

    /** sign/getSignClassHistory 的 obj。 */
    fun attendanceSummary(root: JsonElement?): SztyAttendanceSummary? {
        val o = root.asObjectOrNull() ?: return null
        return SztyAttendanceSummary(
            signed = o["normal"].asIntOrNull(),
            late = o["late"].asIntOrNull(),
            leave = o["leave"].asIntOrNull(),
            absent = o["absenteeism"].asIntOrNull(),
            headStatistics = o.text("headStatistics"),
        )
    }

    /**
     * exerciseRecord/getAssessmentYears 的 obj → 当前学期号。
     *
     * 取 **termId 最大**的那一个，而不是数组第一个 —— 服务端当前按新到旧给，
     * 但没承诺过顺序；学期号是单调的，按它取不会因为排序变了而错到上一学期。
     */
    fun currentTermId(root: JsonElement?): Int? {
        val rows = root.asArrayOrNull() ?: return null
        // 交接包 API_NOTES.md 第 60-61 行逐字写的是 `obj: [{id:<termId>, ...}]`，
        // 也就是说学期号在 `id` 里，不在 `termId` 里。两个都认：真机只证实了一个字段名，
        // 写死任一个都可能在换学期那天静默退回 null（体育课与签到两块一起消失，还不报错）。
        return rows
            .mapNotNull { row ->
                val o = row.asObjectOrNull() ?: return@mapNotNull null
                o["termId"].asIntOrNull() ?: o["id"].asIntOrNull()
            }
            .maxOrNull()
    }

    private fun JsonObject.eventName(): String? =
        text("info.eventName") ?: text("eventName")

    private fun JsonObject.text(key: String): String? = this[key].asStringOrNull()
}
