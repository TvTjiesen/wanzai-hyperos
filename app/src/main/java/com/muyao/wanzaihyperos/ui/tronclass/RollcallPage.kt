package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerKind
import com.muyao.wanzaihyperos.data.tronclass.RollcallRepository
import com.muyao.wanzaihyperos.data.tronclass.TronRollcall
import com.muyao.wanzaihyperos.ui.common.PageState
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 學在城院 → 簽到（原生）。
 *
 * ### 這一頁的定位
 *
 * **看為主、簽為輔**：
 * - 「有哪些簽到、我簽了沒」是**唯讀**，隨時可看、無風險；
 * - 「簽下去」是**寫操作**，只在進行中、且使用者明確點擊時才出現按鈕。
 *
 * ### ★ 為什麼錯誤訊息直接顯示服務端的原話
 *
 * 簽到失敗是常態，而服務端的訊息**比我們準得多**：
 *
 * | 服務端說 | 使用者該做什麼 |
 * |---|---|
 * | `位置不对` | 走近教室 |
 * | `IP 不对` | 連校園網 |
 * | `二维码过期` | 請老師重新出碼 |
 * | `已结束` | 下次早點 |
 *
 * 包成一句「簽到失敗」等於把可操作的信息丟掉。所以 [answerMessage]
 * 原樣顯示（限長），並且**用顏色區分成功/失敗**。
 *
 * ### 掃碼那條為什麼在這一頁只給「輸入碼」
 *
 * 掃碼的相機模組是獨立工程（見 docs/B9-TRONCLASS-CHECKIN.md）：
 * 學在城院原本靠微信 JSSDK 掃，我們的 WebView 沒有 `window.wx`。
 * 這一頁先讓**掃到的字串可以被貼進來**（朋友代掃、截圖識別都行），
 * 相機那條等後面單獨做。
 */
@Composable
fun RollcallPage(
    repository: RollcallRepository,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onFallbackToWeb: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val items = (state as? ListState.Content)?.items.orEmpty()

    /** 上一次簽到的結果訊息（成功或失敗），顯示在頂部。 */
    var answerMessage by remember { mutableStateOf<String?>(null) }
    var answerOk by remember { mutableStateOf(false) }
    /** 正在提交的簽到 id（避免連點兩次送兩筆）。 */
    var submitting by remember { mutableStateOf<Int?>(null) }
    /** 正在掃碼的簽到 id（null = 沒在掃）。 */
    var scanningFor by remember { mutableStateOf<Int?>(null) }

    // ★ 掃碼頁：掃到之後**解析出真正的憑據**再提交。
    //   碼裡是一層 JSON（憑據在 scan_code.scan_result），
    //   直接把整串送去會被服務端拒 —— 見 QrPayloadParser 的註釋。
    if (scanningFor != null) {
        val targetId = scanningFor!!
        QrScannerPage(
            onExit = { scanningFor = null },
            onScanned = { raw ->
                scanningFor = null
                val payload = com.muyao.wanzaihyperos.data.tronclass.QrPayloadParser.extract(raw)
                if (payload == null) {
                    answerOk = false
                    answerMessage = "这不是签到二维码（内容里没有签到信息）"
                } else {
                    submitting = targetId
                    scope.launch {
                        val result = runCatching {
                            repository.answer(targetId, RollcallAnswerKind.QR, payload)
                        }.getOrElse { e ->
                            com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerResult(
                                ok = false,
                                httpStatus = 0,
                                message = "提交失败：${e.javaClass.simpleName}",
                            )
                        }
                        answerOk = result.ok
                        answerMessage = if (result.ok) "扫码签到成功" else result.message
                        submitting = null
                        if (result.ok) runCatching { repository.refresh() }
                    }
                }
            },
        )
        return
    }

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "签到",
                subtitle = "学在城院",
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { runCatching { repository.refresh() } } }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = "重新加载",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                // ★ 結果訊息**必須在空態也顯示**。
                //   先前它只寫在列表分支裡 —— 那時「簽到成功/失敗」
                //   在沒有進行中簽到時**永遠看不到**（真機上就是這個現象：
                //   掃完碼回來一片空白，不知道成沒成）。
                answerMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MiuixTheme.textStyles.body1,
                        color = if (answerOk) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                StatePlaceholder(
                    state = pageState,
                    modifier = Modifier.weight(1f),
                    detail = if (pageState == PageState.Empty) "目前没有进行中的签到" else null,
                    onAction = onFallbackToWeb,
                    actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                answerMessage?.let { msg ->
                    item {
                        Text(
                            text = msg,
                            style = MiuixTheme.textStyles.body2,
                            color = if (answerOk) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                items(items, key = { it.id }) { r ->
                    RollcallCard(
                        rollcall = r,
                        submitting = submitting == r.id,
                        onScan = { scanningFor = r.id },
                        onSubmit = { kind, code ->
                            submitting = r.id
                            scope.launch {
                                val result = runCatching {
                                    repository.answer(r.id, kind, code)
                                }.getOrElse { e ->
                                    com.muyao.wanzaihyperos.data.tronclass.RollcallAnswerResult(
                                        ok = false,
                                        httpStatus = 0,
                                        message = "提交失败：${e.javaClass.simpleName}",
                                    )
                                }
                                answerOk = result.ok
                                answerMessage = if (result.ok) "签到成功" else result.message
                                submitting = null
                                // 簽完重取一次，讓「我簽了沒」反映最新狀態。
                                if (result.ok) runCatching { repository.refresh() }
                            }
                        },
                    )
                }
                item {
                    Text(
                        text = "说明：签到是否成功由学校服务器判定（位置、IP、二维码时效都在那边校验）。" +
                            "「扫码签到」目前需要把扫到的内容粘贴进来 —— 相机扫码是独立功能，稍后单独做。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RollcallCard(
    rollcall: TronRollcall,
    submitting: Boolean,
    onSubmit: (RollcallAnswerKind, String?) -> Unit,
    /** 打開相機掃碼（只對掃碼類簽到有意義）。 */
    onScan: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var codeInput by remember(rollcall.id) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rollcall.title.ifBlank { "签到" },
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = rollcall.myStatusLabel,
                    style = MiuixTheme.textStyles.footnote2,
                    color = if (rollcall.myStatus == "on_call") colors.primary else colors.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            val meta = buildList {
                add(rollcall.sourceLabel)
                if (rollcall.rollcallTime.isNotBlank()) add(rollcall.rollcallTime)
                if (!rollcall.isOpen) add("已结束")
            }
            Text(
                text = meta.joinToString("　"),
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
            )

            // 只有「進行中 + 我不知道自己簽過沒／還沒簽」才給按鈕。
            // 已簽的不再給 —— 避免使用者手滑重複提交。
            if (rollcall.isOpen && rollcall.myStatus != "on_call") {
                Spacer(Modifier.height(10.dp))
                when {
                    rollcall.source.contains("number", true) -> {
                        Text(
                            text = "输入老师报的数字",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextField(
                            value = codeInput,
                            onValueChange = { codeInput = it },
                            label = "数字",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onSubmit(RollcallAnswerKind.NUMBER, codeInput) },
                            enabled = codeInput.isNotBlank() && !submitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (submitting) "提交中…" else "签到") }
                    }

                    rollcall.source.contains("self", true) -> {
                        Button(
                            onClick = { onSubmit(RollcallAnswerKind.SELF_REGISTRATION, null) },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (submitting) "提交中…" else "自助签到") }
                    }

                    rollcall.source.contains("qr", true) || rollcall.source.contains("roomis", true) -> {
                        // ★ 首選「相機掃碼」（原生自己掃，不依賴微信 JSSDK）。
                        Button(
                            onClick = { onScan() },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("打开相机扫码") }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "掃不到？也可以把扫到的内容粘贴到这里",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextField(
                            value = codeInput,
                            onValueChange = { codeInput = it },
                            label = "扫码结果",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onSubmit(RollcallAnswerKind.QR, codeInput) },
                            enabled = codeInput.isNotBlank() && !submitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (submitting) "提交中…" else "提交粘贴的内容") }
                    }
                }
            }
        }
    }
}
