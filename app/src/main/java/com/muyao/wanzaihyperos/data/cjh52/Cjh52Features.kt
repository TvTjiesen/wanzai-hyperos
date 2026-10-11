package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asDoubleOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asLongOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.serialization.json.JsonObject

/**
 * B3 剩餘三項的模型：**日常通知 / 我的考試 / 我的成長**。
 *
 * ### 契約來源
 *
 * 全部來自真機取證（`build/three.json`、`build/codes.json`）：
 * 在已認證的 cjh52 頁面上逐頁導航，記錄**頁面自己發的**請求與回應。
 * 這樣拿到的參數是「頁面真實用的」，不是猜的。
 *
 * ### 隱私
 *
 * 這裡只定義**結構**。取證腳本也只記結構（欄位名 + 型別 + 長度），不記值。
 */

// ---------------------------------------------------------------------------
// 日常通知
// ---------------------------------------------------------------------------

/**
 * 一條通知。
 *
 * 契約（`courier/notice_service/find_all_for_reader`）：
 * ```jsonc
 * 請求 { scope: {school, college[], grade[], specialty[], role}, pageSize, pageNum }
 * 回應 data.data[] = { id, title, username, detail, createTime, pubTime, level }
 * ```
 */
data class Cjh52Notice(
    val id: String,
    /** 標題。 */
    val title: String,
    /** 發布人。 */
    val publisher: String,
    /** 正文（可能很長，UI 自己截斷）。 */
    val detail: String,
    /** 建立時間（毫秒時間戳）。 */
    val createdAt: Long?,
    /** 發布時間（毫秒時間戳）。**列表按它排序**。 */
    val publishedAt: Long?,
    /** 級別（如「普通」「重要」）。 */
    val level: String,
) {
    /** 排序用：優先用發布時間，退回建立時間。 */
    val sortKey: Long get() = publishedAt ?: createdAt ?: 0L
}

object Cjh52NoticeParser {
    /** 從 `data.data[]` 解析。 */
    fun parse(rows: List<JsonObject>): List<Cjh52Notice> = rows.mapNotNull { obj ->
        val id = obj["id"].asStringOrNull() ?: return@mapNotNull null
        Cjh52Notice(
            id = id,
            title = obj["title"].asStringOrNull().orEmpty(),
            publisher = obj["username"].asStringOrNull().orEmpty(),
            detail = obj["detail"].asStringOrNull().orEmpty(),
            createdAt = obj["createTime"].asLongOrNull(),
            publishedAt = obj["pubTime"].asLongOrNull(),
            level = obj["level"].asStringOrNull().orEmpty(),
        )
    }
}

// ---------------------------------------------------------------------------
// 我的考試
// ---------------------------------------------------------------------------

/**
 * 一場考試。
 *
 * 契約（`school_manager/exam_service/get_user_exam_list`）：
 * ```jsonc
 * 請求 { pageNum: 0, pageSize: 10, modelCode: "M0017" }   // pageNum 從 0 起！
 * 回應 data.data[]
 * ```
 *
 * ⚠️ **`pageNum` 從 0 開始**（門戶實測值就是 `0`），與「我的事務」那批從 1 起的不同。
 * 兩者都照實作走，不統一 —— 統一會有一邊拿到重複或漏掉第一頁。
 *
 * ⚠️ 本次取證時該帳號**沒有考試**（`data: []`），所以**條目欄位名未取到**。
 * 因此本模型只保留「確定會有的」分頁資訊 + 原始欄位袋，
 * **不編造欄位名**（PLAN 明文要求「未核實接口不編造資料」）。
 */
data class Cjh52Exam(
    /** 原始欄位袋。欄位名未取證前**不猜**，UI 只顯示已確認的欄位。 */
    val raw: JsonObject,
) {
    val id: String? get() = raw["id"].asStringOrNull() ?: raw["sid"].asStringOrNull()
    val title: String? get() = raw["name"].asStringOrNull()
        ?: raw["title"].asStringOrNull()
        ?: raw["examName"].asStringOrNull()
}

/** 分頁外殼（三個功能共用；`data` 裡還有 `total/pageNum/pageSize`）。 */
data class Cjh52Page<T>(
    val items: List<T>,
    /** 服務端說的總數。**只作參考** —— A2 已實測 `total` 不可靠。 */
    val total: Int?,
) {
    /** 是否可能還有下一頁：按**返回條數**判，不看 `total`。 */
    fun hasMore(pageSize: Int): Boolean = items.size >= pageSize
}

object Cjh52ExamParser {
    fun parse(rows: List<JsonObject>): List<Cjh52Exam> = rows.map { Cjh52Exam(it) }
}

// ---------------------------------------------------------------------------
// 我的成長
// ---------------------------------------------------------------------------

/**
 * KQA 三維分數（成長頁的核心數字）。
 *
 * 契約（`model/query_service/query_one_kqa_score`，body `{userId}`）：
 * ```jsonc
 * 回應 data = { Q: number, GY: number, K: number }
 * ```
 * 三個字母是門戶自己的維度代號（Q=知識 / GY=… / K=…）——
 * **我們不翻譯成中文**：不知道確切含義就別造詞，界面上原樣顯示代號。
 */
data class Cjh52KqaScore(
    val q: Double?,
    val gy: Double?,
    val k: Double?,
) {
    val isEmpty: Boolean get() = q == null && gy == null && k == null
}

/**
 * 體測成績一項。
 *
 * 契約（`school_manager/physique_service/find_my_physique_scores`，body `{}`）：
 * `data[] = { score, name, schoolYear, comprehensiveScore, id }`
 */
data class Cjh52PhysiqueScore(
    val id: String,
    val name: String,
    val schoolYear: String,
    val score: Double?,
    val comprehensiveScore: Double?,
)

/**
 * 學分達成情況。
 *
 * 契約（`studyguide/quality_service/get_my_quality_course_score`，body `{}`）：
 * `data = { totalCredits, compulsoryCredits, compulsoryRequirements, avgScore,
 *           compulsoryCourseNumber, compulsoryCourseTotal, details[] }`
 */
data class Cjh52CreditProgress(
    val totalCredits: Double?,
    val compulsoryCredits: Double?,
    val compulsoryRequirements: Double?,
    val avgScore: Double?,
    /** 已修必修課門數。 */
    val compulsoryCourseNumber: Int?,
    /** 必修課總門數。 */
    val compulsoryCourseTotal: Int?,
) {
    /** 必修課完成比例（0..1）；分母缺失或為 0 時 null —— **不編造 0%**。 */
    val compulsoryRatio: Double?
        get() {
            val done = compulsoryCourseNumber ?: return null
            val total = compulsoryCourseTotal ?: return null
            if (total <= 0) return null
            return (done.toDouble() / total).coerceIn(0.0, 1.0)
        }
}

/**
 * 行為記錄的一個月（熱力圖用）。
 *
 * 契約（`model/kqa_statistical_service/get_events_by_user_id_and_years_group_by_month`）：
 * `data = { min, max, results[] = { count, year, month, totalScore } }`
 */
data class Cjh52ActivityMonth(
    val year: String,
    val month: String,
    val count: Int?,
    val totalScore: Double?,
)

/** 解析器：成長頁的幾個回應。全部**寬進嚴出**（缺欄位給 null，不拋）。 */
object Cjh52GrowthParser {

    fun parseKqaScore(data: JsonObject?): Cjh52KqaScore? {
        data ?: return null
        return Cjh52KqaScore(
            q = data["Q"].asDoubleOrNull(),
            gy = data["GY"].asDoubleOrNull(),
            k = data["K"].asDoubleOrNull(),
        )
    }

    fun parsePhysique(rows: List<JsonObject>): List<Cjh52PhysiqueScore> = rows.mapNotNull { obj ->
        val id = obj["id"].asStringOrNull() ?: return@mapNotNull null
        Cjh52PhysiqueScore(
            id = id,
            name = obj["name"].asStringOrNull().orEmpty(),
            schoolYear = obj["schoolYear"].asStringOrNull().orEmpty(),
            score = obj["score"].asDoubleOrNull(),
            comprehensiveScore = obj["comprehensiveScore"].asDoubleOrNull(),
        )
    }

    fun parseCreditProgress(data: JsonObject?): Cjh52CreditProgress? {
        data ?: return null
        return Cjh52CreditProgress(
            totalCredits = data["totalCredits"].asDoubleOrNull(),
            compulsoryCredits = data["compulsoryCredits"].asDoubleOrNull(),
            compulsoryRequirements = data["compulsoryRequirements"].asDoubleOrNull(),
            avgScore = data["avgScore"].asDoubleOrNull(),
            compulsoryCourseNumber = data["compulsoryCourseNumber"].asIntOrNull(),
            compulsoryCourseTotal = data["compulsoryCourseTotal"].asIntOrNull(),
        )
    }

    /** 從 `data.results[]` 解析行為記錄。 */
    fun parseActivityMonths(data: JsonObject?): List<Cjh52ActivityMonth> {
        val results = data?.get("results").asArrayOrNull() ?: return emptyList()
        return results.mapNotNull { it.asObjectOrNull() }.mapNotNull { obj ->
            val year = obj["year"].asStringOrNull() ?: return@mapNotNull null
            val month = obj["month"].asStringOrNull() ?: return@mapNotNull null
            Cjh52ActivityMonth(
                year = year,
                month = month,
                count = obj["count"].asIntOrNull(),
                totalScore = obj["totalScore"].asDoubleOrNull(),
            )
        }
    }
}
