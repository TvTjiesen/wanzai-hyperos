package com.muyao.wanzaihyperos.data.cjh52

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.cjh52.Cjh52UserContext
import com.muyao.wanzaihyperos.data.failWith
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.settleFirstPage
import com.muyao.wanzaihyperos.data.startRefresh
import com.muyao.wanzaihyperos.net.Cjh52Client
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.asArrayOrNull
import com.muyao.wanzaihyperos.net.asIntOrNull
import com.muyao.wanzaihyperos.net.asObjectOrNull
import com.muyao.wanzaihyperos.net.asStringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 我的班級（原生化）。
 *
 * 契約（真機取證）：
 * ```
 * get_user_by_token → data.clazz.sid
 * POST clazz_service/get_clazz_info_plus_by_id {clazzSid}
 *   → data = { sid, clazz, specialty, grade, number, collegeName,
 *              teacherInfoList[], counselorInfoList[] }
 * ```
 * `teacherInfoList[]` / `counselorInfoList[]` 每項含 `name` / `position` / `phone`。
 */
data class ClazzInfo(
    val name: String,
    val specialty: String,
    val grade: String,
    val collegeName: String,
    /** 班級人數（`number`）。 */
    val headcount: Int?,
    /** 班主任（`teacherInfoList` 裡 position 含「班主任」的，取不到就用第一個）。 */
    val teacherName: String,
    /** 輔導員。 */
    val counselorName: String,
)

object ClazzParser {
    fun parse(data: JsonObject?): ClazzInfo? {
        data ?: return null
        val name = data["clazz"].asStringOrNull().orEmpty()
        if (name.isEmpty()) return null
        return ClazzInfo(
            name = name,
            specialty = data["specialty"].asStringOrNull().orEmpty(),
            grade = data["grade"].asStringOrNull().orEmpty(),
            collegeName = data["collegeName"].asStringOrNull().orEmpty(),
            headcount = data["number"].asIntOrNull(),
            teacherName = pickName(data["teacherInfoList"].asArrayOrNull()),
            counselorName = pickName(data["counselorInfoList"].asArrayOrNull()),
        )
    }

    /** 取第一項的 name（服務端通常只放一個班主任/輔導員）。 */
    private fun pickName(list: kotlinx.serialization.json.JsonArray?): String =
        list?.firstNotNullOfOrNull {
            it.asObjectOrNull()?.get("name").asStringOrNull()?.takeIf { n -> n.isNotBlank() }
        }.orEmpty()
}

interface ClazzRepository {
    val state: StateFlow<ListState<ClazzInfo>>
    suspend fun refresh()
}

class LiveClazzRepository(
    private val client: Cjh52Client,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : ClazzRepository {

    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<ClazzInfo>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<ClazzInfo>> = _state.asStateFlow()
    private val inFlight = Mutex()

    init {
        epoch.registerReset {
            guard.invalidate()
            _state.value = ListState.FirstLoad
        }
    }

    override suspend fun refresh() {
        val token = guard.begin()
        inFlight.withLock {
            if (!guard.isCurrent(token)) return
            if (!guard.publish(token) { _state.value = _state.value.startRefresh() }) return
            val result = catchingActive {
                val me: Cjh52UserContext = client.currentUser()
                if (me.clazzSid.isEmpty()) {
                    throw com.muyao.wanzaihyperos.net.HzcuException.Parse("门户没有返回班级 sid")
                }
                val data = client.postForObject(
                    HzcuEndpoints.CJH52_CLAZZ_INFO_PATH,
                    buildJsonObject { put("clazzSid", me.clazzSid) },
                )
                ClazzParser.parse(data)
                    ?: throw com.muyao.wanzaihyperos.net.HzcuException.Parse("班级详情缺少 clazz")
            }
            guard.publish(token) {
                _state.value = result.fold(
                    onSuccess = { _state.value.settleFirstPage(listOf(it), clock()) { c -> c.name } },
                    onFailure = { _state.value.failWith(it.asCjh52Failure()) },
                )
            }
        }
    }
}
