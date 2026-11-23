package com.muyao.wanzaihyperos.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.nav.Route
import com.muyao.wanzaihyperos.nav.WebTarget
import com.muyao.wanzaihyperos.ui.design.ThemedScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import top.yukonga.miuix.kmp.basic.Icon
import com.muyao.wanzaihyperos.ui.components.WanzaiIconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import com.muyao.wanzaihyperos.ui.components.WanzaiTextButton as TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WebTargetPage(app: AppGraph, route: Route.Web, onExit: () -> Unit) {
    // Ordinary remember only: neither a lease nor the resulting credential URL is saveable.
    val lease = remember(app, route) { WebDisplayLease(app.accountEpoch) }
    val revision by app.accountRevision.collectAsState()
    var credentialRevision by remember(app) { mutableStateOf(app.tronclassSession.credentialRevision()) }
    DisposableEffect(app, route.target.kind) {
        val listener = { credentialRevision = app.tronclassSession.credentialRevision() }
        if (route.target.kind == WebTarget.Kind.TRONCLASS) app.tronclassSession.addListener(listener)
        onDispose { app.tronclassSession.removeListener(listener) }
    }
    var resolvedUrl by remember(lease) { mutableStateOf<String?>(null) }
    var loading by remember(lease) { mutableStateOf(true) }
    var retry by remember(lease) { mutableStateOf(0) }
    val current = lease.isCurrent()

    LaunchedEffect(lease, retry, revision, credentialRevision) {
        resolvedUrl = null
        loading = true
        try {
            if (lease.isCurrent() && route.target.kind != WebTarget.Kind.BLOCKED) {
                app.runTracked(lease.generation!!) {
                    val url = resolveWebTarget(app, route, lease)
                    currentCoroutineContext().ensureActive()
                    lease.publish { resolvedUrl = url }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Fixed copy only: exception messages and addresses may contain credentials.
        } finally {
            loading = false
        }
    }

    val url = resolvedUrl
    if (current && url != null) {
        WebShellPage(
            title = route.title,
            url = url,
            onExit = onExit,
            loadInitialUrl = { view, address -> lease.publish { view.loadUrl(address) } },
            publishWebAction = lease::publish,
            onReload = if (route.target.kind in setOf(WebTarget.Kind.CJH52, WebTarget.Kind.PORTAL_TEMP)) {
                { resolvedUrl = null; loading = true; retry++ }
            } else null,
        )
    } else {
        ThemedScaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                SmallTopAppBar(
                    title = route.title,
                    color = Color.Transparent,
                    navigationIcon = {
                        WanzaiIconButton(label = "返回", onClick = onExit) {
                            Icon(MiuixIcons.ChevronBackward, null, tint = MiuixTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(when {
                    route.target.kind == WebTarget.Kind.BLOCKED -> "旧网页或带凭据的链接无法安全恢复，请返回后从应用入口重新打开"
                    !current -> "账号已退出或切换，请返回后在当前账号重新打开"
                    loading -> "正在为当前账号准备网页…"
                    else -> "没能准备网页，请重试（不会使用保存的旧凭据）"
                })
                if (!loading && current && route.target.kind != WebTarget.Kind.BLOCKED) {
                    TextButton(onClick = { retry++ }, text = "重试")
                }
                TextButton(onClick = onExit, text = "返回")
            }
        }
    }
}
