package com.muyao.wanzaihyperos.ui.web

import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.AppOpenStrategy
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.nav.Route
import com.muyao.wanzaihyperos.nav.WebTarget
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.TronclassBoot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A display lease never changes owner, even if the account switches while resolving. */
class WebDisplayLease(private val epoch: AccountEpoch) {
    val generation: Long? = epoch.identity()?.generation
    private var credentialGate: ((() -> Unit) -> Boolean)? = null
    internal fun bindCredential(gate: (() -> Unit) -> Boolean) { credentialGate = gate }
    fun isCurrent(): Boolean = publish {}
    fun publish(action: () -> Unit): Boolean {
        val owner = generation ?: return false
        var accepted = false
        epoch.publishData(owner) {
            if (epoch.identity()?.generation == owner) {
                accepted = credentialGate?.invoke(action) ?: run { action(); true }
            }
        }
        return accepted
    }
}

internal class TronWebCredentials(val access: String?, val refresh: String?, val cookies: String? = null) {
    override fun toString(): String = "TronWebCredentials"
}

internal class WebTargetResolver(
    private val issueCode: suspend () -> String?,
    private val snapshot: () -> TronWebCredentials,
    private val renew: suspend (String, Long) -> TronWebCredentials?,
    private val save: (TronWebCredentials) -> Unit,
    private val revision: () -> Long,
    private val publishCredential: (Long, () -> Unit) -> Boolean = { expected, action ->
        if (revision() == expected) { action(); true } else false
    },
) {
    suspend fun resolve(route: Route.Web, lease: WebDisplayLease): String? {
        val owner = lease.generation ?: return null
        if (!lease.isCurrent()) return null
        currentCoroutineContext().ensureActive()
        return when (route.target.kind) {
            WebTarget.Kind.BLOCKED -> null
            WebTarget.Kind.DIRECT -> route.target.address
            WebTarget.Kind.CJH52, WebTarget.Kind.PORTAL_TEMP -> {
                val code = issueCode() ?: return null
                currentCoroutineContext().ensureActive()
                if (!lease.isCurrent()) return null
                // Keep credentials outside the fragment and never save an unactivated code as a session.
                val parts = route.target.addressForDisplay().split('#', limit = 2)
                AppOpenStrategy.buildTempCodeUrl(parts[0], route.appId, route.target.colorArgb, code, System.currentTimeMillis()) +
                    if (parts.size == 2) "#${parts[1]}" else ""
            }
            WebTarget.Kind.TRONCLASS -> {
                var credentials = TronWebCredentials(null, null)
                var originalRevision = -1L
                var captured = false
                if (!lease.publish {
                        originalRevision = revision()
                        captured = publishCredential(originalRevision) { credentials = snapshot() }
                    } || !captured) return null
                val fresh = credentials.refresh?.takeIf { it.isNotBlank() }?.let { refreshToken ->
                    try { renew(refreshToken, owner) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
                currentCoroutineContext().ensureActive()
                if (!lease.isCurrent() || revision() != originalRevision) return null
                val result = if (fresh != null) {
                    var saved = false
                    if (!lease.publish {
                            saved = publishCredential(originalRevision) { save(fresh) }
                        } || !saved) return null
                    TronclassBoot.identityTokenUrl(fresh.access.orEmpty(), fresh.refresh)
                } else if (!credentials.access.isNullOrBlank()) {
                    TronclassBoot.identityTokenUrl(credentials.access!!, credentials.refresh)
                } else {
                    HzcuEndpoints.TRONCLASS_MOBILE_TILE
                }
                // The same store-lock gate remains active through UI publication and initial loadUrl.
                lease.bindCredential { action -> publishCredential(originalRevision, action) }
                if (lease.publish {}) result else null
            }
        }
    }
}

internal suspend fun resolveWebTarget(app: AppGraph, route: Route.Web, lease: WebDisplayLease): String? {
    var cookies: String? = null
    val resolver = WebTargetResolver(
        issueCode = { app.api.issueTempCode() },
        snapshot = {
            cookies = app.tronclassSession.ssoCookiesJson()
            TronWebCredentials(app.tronclassSession.accessToken(), app.tronclassSession.refreshToken(), cookies)
        },
        renew = { refresh, owner ->
            app.casService.refreshTronclassTokens(refresh, ownerToken = owner).let {
                TronWebCredentials(it.accessToken, it.refreshToken, cookies)
            }
        },
        save = { app.tronclassSession.saveTokens(it.access.orEmpty(), it.refresh, ssoCookiesJson = it.cookies, renewal = true) },
        revision = { app.tronclassSession.credentialRevision() },
        publishCredential = app.tronclassSession::publishCredential,
    )
    return resolver.resolve(route, lease)
}
