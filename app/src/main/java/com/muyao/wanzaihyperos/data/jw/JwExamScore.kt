package com.muyao.wanzaihyperos.data.jw

import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * 一筆**考試安排**（`gnmkdm=N358105`）。
 *
 * ## 契約（真機探測 2026-10-13，逐字抄自回應）
 *
 * ```json
 * {"xh_id":"12345678","bj":"软件工程9901","cdbh":"未放开不可查",
 *  "ksmc":"2026-2027学年第1学期开学补考","kssj":"未放开不可查","kch":"C01113",
 *  "kkxy":"计算机与计算科学学院","kcmc":"数据挖掘技术与应用","pycc":"本科",
 *  "njmc":"2025","jxbmc":"数据挖掘技术与应用C01113",
 *  "sjbh":"统一-2026-2027学年第1学期开学补考-C01113",
 *  "xf":"3.0","xnmc":"2026-2027","xm":"某某某",
 *  "jsxx":"J05011/王贵","xqmmc":"1","cdmc":"未放开不可查","cdxqmc":"未放开不可查"}
 * ```
 *
 * ### ★★ 最重要的一條：「未放开不可查」是**服務端的策略**，不是解析錯誤
 *
 * 實測這台部署把 `kssj`（考試時間）、`cdmc`（考場）、`cdbh`（座位號）、
 * `cdxqmc`、`zwh`、`cdjc` **全部**回成字串 `"未放开不可查"` ——
 * 也就是**學校還沒對外開放考試時間與考場查詢**。
 *
 * 所以界面必須：
 * - **原樣顯示服務端的說法**（「未放开不可查」就是它給的答案）；
 * - **不可以**自己編一個時間，也不可以顯示成空白讓人以為「沒安排」。
 *
 * 這與全專案「不替服務端撒謊」是同一條紀律，但這裡更進一步：
 * **服務端的「查不到」本身就是要轉達的信息**。
 */
data class JwExam(
    /** 課程名（`kcmc`）。 */
    val courseName: String,
    /** 考試名稱（`ksmc`，如「2026-2027學年第1學期開學補考」）。 */
    val examName: String,
    /** 考試時間（`kssj`）。**可能是「未放开不可查」**。 */
    val time: String,
    /** 考場（`cdmc`）。**可能是「未放开不可查」**。 */
    val room: String,
    /** 座位號（`zwh` / `cdbh`）。**可能是「未放开不可查」**。 */
    val seat: String,
    /** 課程號（`kch`）。 */
    val courseCode: String,
    /** 學分（`xf`）。 */
    val credit: String,
    /** 教師（`jsxx`，格式 `工號/姓名`）。 */
    val teacher: String,
    /** 開課學院（`kkxy`）。 */
    val college: String,
    /** 學年（`xnmc`，如「2026-2027」）。 */
    val academicYear: String,
    /** 學期（`xqmmc`，如「1」）。 */
    val term: String,
    /** 考試批次說明（`sjbh`）。 */
    val batchNote: String,
) {
    /** 教師顯示名（把 `J05011/王贵` 取成 `王贵`）。空就回空。 */
    val teacherName: String
        get() = teacher.substringAfterLast('/').trim().ifBlank {
            teacher.substringAfterLast(';').trim()
        }

    /**
     * 這筆的考試時間／考場**是不是服務端說「查不到」**。
     *
     * 兩個地方都可能帶這個說法，任一命中就算。
     * 界面據此顯示「學校尚未開放查詢」，而不是空白。
     */
    val restricted: Boolean
        get() = listOf(time, room, seat).any { it.contains(RESTRICTED_MARK) }

    /** 本地化的一句狀態。 */
    val restrictionNote: String?
        get() = if (restricted) "学校尚未开放考试时间与考场查询" else null

    private companion object {
        const val RESTRICTED_MARK = "未放开"
    }
}

object JwExamParser {
    fun parse(page: JwPage): List<JwExam> = page.items.mapNotNull { obj -> parseOne(obj) }

    fun parseOne(obj: JsonObject): JwExam? {
        val courseName = obj["kcmc"].asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        return JwExam(
            courseName = courseName,
            examName = obj["ksmc"].asStringOrNull().orEmpty(),
            time = obj["kssj"].asStringOrNull().orEmpty(),
            room = obj["cdmc"].asStringOrNull().orEmpty(),
            // 座位號兩個欄位都可能是那個說法，取有內容的
            seat = obj["zwh"].asStringOrNull().orEmpty()
                .ifBlank { obj["cdbh"].asStringOrNull().orEmpty() },
            courseCode = obj["kch"].asStringOrNull().orEmpty(),
            credit = obj["xf"].asStringOrNull().orEmpty(),
            teacher = obj["jsxx"].asStringOrNull().orEmpty(),
            college = obj["kkxy"].asStringOrNull().orEmpty(),
            academicYear = obj["xnmc"].asStringOrNull().orEmpty(),
            term = obj["xqmmc"].asStringOrNull().orEmpty(),
            batchNote = obj["sjbh"].asStringOrNull().orEmpty(),
        )
    }
}

/**
 * 一筆**學習成績**（`gnmkdm=N305005`）。
 *
 * ## 契約（真機探測 2026-10-13）
 *
 * ```json
 * {"kcmc":"…","cj":"60","jd":"1.50","xf":"3.0","jsxm":"董婷;王波",
 *  "cjbdsj":"2026-01-20 20:15:53","jgmc":"计算机与计算科学学院",
 *  "jxb_id":"314B…","jxbmc":"(2025-2026-1)…","bfzcj":"60","cjsfzf":"否"}
 * ```
 *
 * ### 欄位說明（實測 + 命名慣例交叉比對）
 *
 * | 欄位 | 意思 |
 * |------|------|
 * | `kcmc` | 課程名 |
 * | `cj` | **成績**（可能是數字或「優秀／良好」這種等第） |
 * | `bfzcj` | 百分制成績（`cj` 是等第時這裡是分數） |
 * | `jd` | **績點** |
 * | `xf` | 學分 |
 * | `jsxm` | 教師名（**多個用 `;` 分隔**） |
 * | `cjbdsj` | 成績公佈時間 |
 *
 * ### ★ 成績可能是「等第」不是數字
 *
 * 實測這筆是 `cj:"60"`（數字），但同一套系統的其它部署會回
 * `cj:"优秀"` / `"良好"` / `"及格"`。所以 [score] 收成**字串**不轉數字 ——
 * 轉不動時丟掉原始信息，而那就是學生要看的東西。
 */
data class JwScore(
    /** 課程名（`kcmc`）。 */
    val courseName: String,
    /** 成績（`cj`）。可能是數字或等第。 */
    val score: String,
    /** 百分制成績（`bfzcj`）。等第制時這裡才有分數。 */
    val rawScore: String,
    /** 績點（`jd`）。 */
    val gradePoint: String,
    /** 學分（`xf`）。 */
    val credit: String,
    /** 教師（`jsxm`，多個用 `;`）。 */
    val teachers: String,
    /** 開課學院（`jgmc`）。 */
    val college: String,
    /** 成績公佈時間（`cjbdsj`）。 */
    val publishedAt: String,
    /** 教學班名（`jxbmc`）。 */
    val className: String,
) {
    /** 教師名用「、」連接（原始是 `;`）。 */
    val teacherLabel: String
        get() = teachers.split(';', '；').map { it.trim() }.filter { it.isNotBlank() }
            .joinToString("、")

    /**
     * 界面顯示的成績。
     *
     * 有 `cj` 就用它；空的才退回 `bfzcj`；都空就是「—」（未知，不是 0）。
     */
    val displayScore: String
        get() = score.ifBlank { rawScore }.ifBlank { "—" }
}

object JwScoreParser {
    fun parse(page: JwPage): List<JwScore> = page.items.mapNotNull { obj -> parseOne(obj) }

    fun parseOne(obj: JsonObject): JwScore? {
        val courseName = obj["kcmc"].asStringOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
        return JwScore(
            courseName = courseName,
            score = obj["cj"].asStringOrNull().orEmpty(),
            rawScore = obj["bfzcj"].asStringOrNull().orEmpty(),
            gradePoint = obj["jd"].asStringOrNull().orEmpty(),
            credit = obj["xf"].asStringOrNull().orEmpty(),
            teachers = obj["jsxm"].asStringOrNull().orEmpty(),
            college = obj["jgmc"].asStringOrNull().orEmpty(),
            publishedAt = obj["cjbdsj"].asStringOrNull().orEmpty(),
            className = obj["jxbmc"].asStringOrNull().orEmpty(),
        )
    }
}
