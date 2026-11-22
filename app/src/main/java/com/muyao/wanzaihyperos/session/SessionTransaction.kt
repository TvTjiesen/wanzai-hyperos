package com.muyao.wanzaihyperos.session

/** One editor commit covers all credentials; device identity and preferences are retained. */
class SessionTransaction(
    private val store: SecureStore,
    private val portal: PortalSession,
    private val tron: TronclassSession,
    private val cjh: Cjh52Session,
    private val residence: ZhgySession,
    private val jw: JwSession,
    private val sports: SztySession,
) {
    private val secondary = listOf(tron, cjh, residence, jw, sports)
    private val secondaryKeys = listOf(
        TronclassSession.KEY_TOKENS, TronclassSession.KEY_SSO, TronclassSession.KEY_SESSION_ID,
        TronclassSession.KEY_COOKIES, TronclassSession.KEY_SAVED_AT, TronclassSession.KEY_DISPLAY_NAME,
        Cjh52Session.KEY_TOKEN, Cjh52Session.KEY_OBTAINED_AT, Cjh52Session.KEY_ACTIVATED,
        ZhgySession.KEY_CODE, ZhgySession.KEY_OBTAINED_AT,
        JwSession.KEY_ACCOUNT, JwSession.KEY_PASSWORD, JwSession.KEY_LAST_LOGIN,
        SztySession.KEY_TOKEN, SztySession.KEY_USER_ID, SztySession.KEY_LAST_SYNC, SztySession.KEY_SAVED_AT,
    )

    fun savePortal(token: String, account: String?, cookies: String) {
        var replaced = false
        synchronized(store) {
            replaced = portal.userCode() != account
            portal.save(token, account, cookies,
                extraChanges = if (replaced) secondaryKeys.associateWith { null } else emptyMap(),
                notify = false)
            if (replaced) secondary.forEach { it.acceptCommittedClear(notify = false) }
        }
        portal.notifyCommittedChange()
        if (replaced) secondary.forEach { it.notifyCommittedChange() }
    }

    fun clearAll() {
        val all = listOf(portal) + secondary
        synchronized(store) {
            val keys = secondaryKeys + listOf(PortalSession.KEY_ACCESS_TOKEN, PortalSession.KEY_USER_CODE,
                PortalSession.KEY_SAVED_AT, PortalSession.KEY_PORTAL_COOKIES)
            store.writeAtomically(keys.associateWith { null })
            all.forEach { it.acceptCommittedClear(notify = false) }
        }
        all.forEach { it.notifyCommittedChange() }
    }
}
