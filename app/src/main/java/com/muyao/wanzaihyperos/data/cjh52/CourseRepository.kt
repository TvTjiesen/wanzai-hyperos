package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 課表倉庫（B1）。
 *
 * ### 取數流程（兩步，來自真機取證）
 *
 * ```
 * 1. POST get_user_by_token (body {}) → data.user.sid      ← 上游 id
 * 2. POST get_class_info_by_uid (body {uid, state:"open"}) → data[] → Course
 * ```
 *
 * `sid` **不快取到磁碟**：它是 CJH52 側的使用者識別碼（個資）。
 * 每次取課表都重取一次，代價是一次請求（限流允許），換來的是不多留一份個資。
 *
 * ### 繼承 A2 的紀律
 *
 * - 過時回應由 [StaleGuard] 丟棄（登出 / 重登後不得覆蓋新狀態）；
 * - 刷新失敗**保留已有內容**並標 stale，不清空；
 * - 全部走 [Cjh52Client] → [HzcuClient] 的限流器（不繞過 QPS 紅線）。
 */
interface CourseRepository {
    val state: StateFlow<ListState<Course>>
    suspend fun refresh()
}

class LiveCourseRepository(
    private val client: Cjh52Client,
    private val clock: () -> Long = System::currentTimeMillis,
) : CourseRepository {

    private val guard = StaleGuard()
    private val _state = MutableStateFlow<ListState<Course>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<Course>> = _state.asStateFlow()

    /** 單飛：同一時刻只允許一次請求，避免連點刷新打出兩倍流量。 */
    private val inFlight = Mutex()

    override suspend fun refresh() {
        inFlight.withLock {
            val token = guard.begin()
            _state.value = _state.value.startRefresh()
            val result = runCatching { load() }
            if (!guard.isCurrent(token)) return
            _state.value = result.fold(
                onSuccess = { courses ->
                    _state.value.settleFirstPage(courses, clock()) { it.id }
                },
                onFailure = { _state.value.failWith(it.asCjh52Exception()) },
            )
        }
    }

    private suspend fun load(): List<Course> {
        // 1) 取上游使用者 id。**不持久化**（見類註釋）。
        val me = client.postForObject(HzcuEndpoints.CJH52_GET_USER_BY_TOKEN_PATH)
        val uid = me["user"].asObjectOrNull()?.get("sid").asStringOrNull()
            ?: throw com.muyao.wanzaihyperos.net.HzcuException.Parse(
                "CJH52 沒有回傳使用者 id（user.sid）",
            )

        // 2) 取課表。
        val rows = client.postForArray(
            HzcuEndpoints.CJH52_CLASS_INFO_BY_UID_PATH,
            buildJsonObject {
                put("uid", uid)
                put("state", "open")
            },
        )
        return CourseParser.parse(rows)
    }
}

/**
 * 把任意異常歸一到城院錯誤模型。
 *
 * 為什麼要這一層：`Cjh52Client` 已經拋 [com.muyao.wanzaihyperos.net.HzcuException]，
 * 但 `runCatching` 也可能撈到 `JsonPrimitive` 這種解析期意外。
 * 讓它們統一變成 `Parse`，界面才有一致的文案。
 *
 * 回傳型別**必須是 [HzcuException]**（不是 `Throwable`）：
 * [failWith] 只接受它 —— 見 [ListState] 的簽名。
 */
private fun Throwable.asCjh52Exception(): com.muyao.wanzaihyperos.net.HzcuException =
    if (this is com.muyao.wanzaihyperos.net.HzcuException) this
    else com.muyao.wanzaihyperos.net.HzcuException.Parse(
        message = "課表資料解析失敗",
        cause = this,
    )
