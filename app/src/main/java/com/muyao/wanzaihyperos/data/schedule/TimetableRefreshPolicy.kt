package com.muyao.wanzaihyperos.data.schedule

import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.metaOrNull
import com.muyao.wanzaihyperos.net.isCredentialRejected

const val TIMETABLE_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L

/**
 * **自动**刷新该不该发（手动点「刷新」不走这里）。
 *
 * 三道闸门，顺序有意义：
 * 1. 已经在刷 ⇒ 不重复发；
 * 2. **上次是账号密码被明确拒绝 ⇒ 不再自动发**（见 [isCredentialRejected] 的说明）。
 *    没有这一条时，教务那份的 15 分钟循环会拿同一对错密码一直敲门 ——
 *    2026-11-22 真机上就是这么把学校 CAS 的验证码敲出来的；
 * 3. 缓存新鲜期（[TIMETABLE_REFRESH_INTERVAL_MS]）。
 *
 * ★ 第 2 条只挡「自动」。用户手动点刷新仍然会发 —— 他可能刚去改过密码，
 *   那一次必须真的去试，否则应用永远停在「密码不对」而不给他改正的机会。
 */
fun shouldRefreshTimetable(state: ListState<*>, now: Long): Boolean {
    // 没有旧数据时凭据被拒会降级成 SessionExpired（`failWith` 的既有行为），
    // 这一支没有 meta，必须单独挡 —— 否则「错密码」会绕过下面那道闸门继续被自动重发。
    if (state is ListState.SessionExpired) return false
    val meta = state.metaOrNull
    if (meta?.refreshing == true) return false
    if (meta?.lastRefreshError.isCredentialRejected) return false
    val updated = meta?.updatedAtMillis ?: return true
    return state !is ListState.Content && state !is ListState.Empty ||
        now < updated || now - updated >= TIMETABLE_REFRESH_INTERVAL_MS
}
