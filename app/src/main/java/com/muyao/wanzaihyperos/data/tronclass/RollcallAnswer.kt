package com.muyao.wanzaihyperos.data.tronclass

/**
 * 簽到的方式（決定提交到哪個端點）。
 *
 * 只放**學生能自己動手**的三種 + 一個通用。雷達與自動簽到**不放進來**：
 * 前者靠定位/信号、後者由系統判定，都不是「點一下就能簽」的東西。
 */
enum class RollcallAnswerKind {
    /** 數字簽到：老師報數字，學生填。 */
    NUMBER,

    /** 自助簽到：學生自己簽（老師開了自助）。 */
    SELF_REGISTRATION,

    /** 掃碼簽到：掃老師的二維碼。 */
    QR,

    /** 通用簽到（源碼的 `/answer`）。 */
    PLAIN,
}

/**
 * 一次簽到提交的結果。
 *
 * ### 為什麼要有這個型別，而不是直接拋異常
 *
 * 簽到**失敗是常態**，而且失敗原因必須**原文轉達給使用者** ——
 * 服務端的消息比我們準得多：
 *
 * ```
 * rollcall_position_invalid   位置不对
 * qr_code_expired             二维码过期
 * invalid_ip_address          IP 不对（不在校园网）
 * rollcall_already_closed     已结束
 * rollcall_unknown_student    你不在名单里
 * ```
 *
 * 這些話直接顯示，使用者立刻知道「該連校園網」還是「該走近教室」。
 * 如果我們把它包成一句「簽到失敗」，使用者只能反覆重試。
 *
 * ⚠️ `message` 來自服務端，屬於**外部文字**：
 * 顯示前要限長（見 [TronclassClient] 的截斷），且絕不寫進日誌。
 */
data class RollcallAnswerResult(
    val ok: Boolean,
    val httpStatus: Int,
    val message: String,
)
