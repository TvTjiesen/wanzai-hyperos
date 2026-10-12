package com.muyao.wanzaihyperos.net

import org.junit.Assume
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 活體聯調測試的**網絡預檢**。
 *
 * ### 為什麼需要它
 *
 * 專案裡有幾支「打真實學校服務器」的活體測試（登入、門戶接口、待辦、學在城院鏈路）。
 * 它們本來只檢查「有沒有憑據」——**有憑據就真跑**。這在能連通時是對的，
 * 但換個網絡環境（例如校內網段解析到 `10.61.x.x` 卻連不通）就會
 * 報一堆 `RequestTimeout`，讓 `testDebugUnitTest` 的結果**看起來像代碼壞了**。
 *
 * 2026-10-12 上午實際發生過一次：12 條失敗全是活體測試，離線單測一條沒掛。
 * 那種「紅」沒有任何信息量 —— 它只說明當時網絡不通，卻要人去逐條核對。
 *
 * ### 判據
 *
 * 對目標 host:port 做一次**短超時 TCP 連接**（2 秒）：
 * - 連得上 → 放行，測試照常跑；
 * - 連不上 → `Assume` 跳過，並在跳過信息裡**寫明是哪個 host**，
 *   這樣報告裡一眼能看出「跳過」與「失敗」的區別。
 *
 * ⚠️ 只做 TCP 連接，**不發任何 HTTP 請求** —— 預檢本身不該消耗配額、
 * 也不該碰憑據。
 */
object LiveNetworkGate {

    /** 門戶（CAS 與業務接口都在這個域下）。 */
    private const val PORTAL_HOST = "iappdd.hzcu.edu.cn"

    /** 學在城院接口域。 */
    private const val TRONCLASS_HOST = "courses.hzcu.edu.cn"

    private const val TIMEOUT_MS = 2000

    /**
     * 門戶可達性。**不通過就跳過當前測試**（不是失敗）。
     *
     * 呼叫方式：測試體開頭 `LiveNetworkGate.requirePortal()`。
     */
    fun requirePortal() {
        Assume.assumeTrue(
            "門戶 $PORTAL_HOST:80 連不上（當前網絡環境不可達），跳過活體測試",
            reachable(PORTAL_HOST, 80),
        )
    }

    /** 學在城院接口域可達性。 */
    fun requireTronclass() {
        Assume.assumeTrue(
            "學在城院 $TRONCLASS_HOST:443 連不上（當前網絡環境不可達），跳過活體測試",
            reachable(TRONCLASS_HOST, 443),
        )
    }

    /** 一次短超時 TCP 連接。**任何異常都算不可達**（不區分原因，預檢不需要）。 */
    private fun reachable(host: String, port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
            true
        }
    }.getOrDefault(false)
}
