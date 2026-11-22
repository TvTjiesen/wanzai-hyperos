package com.muyao.wanzaihyperos.ui.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.ui.components.WanzaiButton
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/** Keep mounted content while the shared coordinator restores the official session. */
@Composable
internal fun Cjh52WarmUpGate(
    recovery: Cjh52Recovery,
    lease: WebDisplayLease,
    content: @Composable (ready: Boolean) -> Unit,
) {
    val status by recovery.status.collectAsState()
    val scope = rememberCoroutineScope()
    var ready by remember(recovery, lease) { mutableStateOf(status == Cjh52Recovery.Status.Ready) }
    LaunchedEffect(recovery, lease) {
        val owner = lease.generation ?: return@LaunchedEffect
        if (lease.isCurrent() && recovery.ensure(owner) != null) ready = true
    }
    LaunchedEffect(status) {
        if (status == Cjh52Recovery.Status.Ready && lease.isCurrent()) ready = true
    }
    Column(Modifier.fillMaxSize()) {
        if (status == Cjh52Recovery.Status.Failed) {
            Text("柒合院连接未完成。可重试连接，或打开本页官网入口完成登录后再重试。")
            WanzaiButton(onClick = {
                val owner = lease.generation
                if (owner != null && lease.isCurrent()) scope.launch {
                    ready = false
                    ready = recovery.ensure(owner, manual = true) != null
                }
            }) { Text("重新连接柒合院") }
        } else if (status == Cjh52Recovery.Status.Connecting) {
            Text("正在连接柒合院…")
        }
        Box(Modifier.weight(1f)) { content(ready && lease.isCurrent()) }
    }
}
