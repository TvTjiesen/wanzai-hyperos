package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.data.tronclass.ScanRouter
import com.muyao.wanzaihyperos.ui.cjh52.CjhChipFlow
import com.muyao.wanzaihyperos.ui.cjh52.CjhChipSpec
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.Mark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.components.WanzaiButton as Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局扫一扫。
 *
 * 它自己**不做任何事**，只负责判和转交（判据在 [ScanRouter]）：
 *
 * | 扫到 | 去处 |
 * |---|---|
 * | `http(s)://…` | 交给 [onWeb] 开 WebView |
 * | 学在城院的签到码 | 交给 [onRollcall] 带去签到页（**提交仍由他按**） |
 * | 其它 | 留在本页、说明这是什么情况，等他决定接着扫还是退出 |
 *
 * ## 三种结果的处理，各自为什么这样
 *
 * 1. **网址** → 直接进 [onWeb]。掃完就走，不留在相機裡 ——
 *    使用者掃網址是為了看那個頁面，不是為了繼續掃。
 * 2. **签到码** → 只想把码**带过去**（[ScanHandoff] 那条一次性中转），
 *    提交仍然要他在签到页按。掃到就自動提交的話，一次誤掃就是一筆記錯的到課記錄。
 * 3. **未知** → **不跳走**（这是稿上的要求：未知码不跳走）。
 *    留在此页，把刚才那串码原样摆出来，两个出口：「继续扫描」/「退出扫描」。
 *
 * ## ★ 为什么未知码要**停下来**，而不是自动接着扫
 *
 * 这一版以前是 `attempt++` 直接重新起一次扫码。问题在相机：
 * 认出那张码之后，**画面里还是那张码** —— 重建 `QrScannerPage` 会把去重状态
 *（`reported`）一起清掉，下一帧立刻又命中、又判成未知、又重建……
 * 表现就是提示行疯狂闪烁、相机反复初始化。这不是「连续帧自动提交」，
 * 但同样是一個人為的循環。
 *
 * 所以现在改成：未知码 → 停在这一张结果卡上，**由他按「继续扫描」才重开**
 *（`attempt++` 只在这里发生）。这与稿上那句「同一次识别只保留一份结果；
 * 继续扫描会显式重置」是同一件事。
 */
@Composable
fun ScannerPage(
    onExit: () -> Unit,
    onWeb: (String) -> Unit,
    onRollcall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    /** 上一张码为什么没成 —— 顶在相机下面的提示行里（已知码判错时用不到，留作兜底）。 */
    var notice by remember { mutableStateOf<String?>(null) }

    /** 判不出来的那串码原文；非 null 就表示「停在结果卡上」。 */
    var unknownRaw by remember { mutableStateOf<String?>(null) }

    /**
     * 重新扫一次的代数。
     *
     * [QrScannerPage] 内部用 `reported` 去重（同一张码在连续帧里会重复命中，
     * 不去重会重复回调 —— 而签到是写操作）。那张码判成「不是」之后必须能再扫，
     * 而 `reported` 是 remember 的、自己不会清 —— 换 key 重建它才是真的重开一次。
     *
     * ⚠️ 只在「继续扫描」那里 `attempt++`，不在 `onScanned` 里自动加（见上面那条注释）。
     */
    var attempt by remember { mutableIntStateOf(0) }

    /**
     * 首屏選過的取碼方式；`null` = 還沒選（停在首屏）。
     *
     * 稿上是「hero + 相機/相冊兩顆 chip」的首屏，選完才進取景。
     * 這不是為了多一次點擊 —— 沒有首屏的話，使用者只有「被帶進相機再自己找相冊」
     * 一條路，而相冊那條路在拒了相機權限時是唯一的活路。
     */
    var picked by remember { mutableStateOf<ScanSource?>(null) }
    val raw = unknownRaw
    if (raw == null && picked == null) {
        // ── 首屏：選一種取碼方式（稿上的 hero + 相機/相冊 chips）──────────
        SecondaryScaffold(
            title = "通用扫一扫",
            subtitle = "",
            onBack = onExit,
            modifier = modifier,
            onRefresh = null,
        ) { innerPadding ->
            AppList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                item(key = "scan-home-hero") {
                    TronHero(
                        eyebrow = "SCAN / 通用扫一扫",
                        title = "识别，再决定",
                        sub = "签到码交给签到站；网址使用原站容器；未知码不跳走。",
                        icon = MarkIcon.Scan,
                    )
                }
                item(key = "scan-home-chips") {
                    CjhChipFlow(
                        chips = listOf(
                            CjhChipSpec(
                                label = "相机",
                                onClick = { picked = ScanSource.CAMERA },
                                emphasis = true,
                            ),
                            CjhChipSpec(
                                label = "相册",
                                onClick = { picked = ScanSource.ALBUM },
                            ),
                        ),
                    )
                }
                item(key = "scan-home-note") {
                    HintText("同一次识别只保留一份结果；继续扫描会显式重置，不模拟连续帧自动提交。")
                }
            }
        }
        return
    }
    if (raw != null) {
        // ── 未知码：结果卡 + 显式控制 ───────────────────────────────
        SecondaryScaffold(
            // 稿的 top-title 是「通用扫一掃」：「通用」用來和簽到掃碼區分
            //（本頁會把簽到碼轉交簽到站，`onRollcall` 就是幹這個的）。
            title = "通用扫一扫",
            // 副標題留空：頁面級那三行由下面的 [TronHero] 給，
            // 頂欄再寫一遍「識別，再決定」就是同一句話出現兩次。
            subtitle = "",
            onBack = onExit,
            modifier = modifier,
            // 这一页没有可滚动的清单之外的东西，结果卡一屏放得下；不给刷新。
            onRefresh = null,
        ) { innerPadding ->
            AppList(
                modifier = Modifier
                    .fillMaxSize()
                    // 安全区只让一次：顶栏高度（含状态列）由壳给。
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                // ── 頁面級三行（稿上 eyebrow / hero / 副句）────────────────
                // 副句就是本頁的三條分流規則：簽到碼 → 簽到站、網址 → 原站容器、
                // 未知碼 → 不跳走。原本這句話一個字都沒有，
                // 用戶掃到不同東西會發生什麼，完全猜不到。
                item(key = "unknown-hero") {
                    TronHero(
                        eyebrow = "SCAN / 通用扫一扫",
                        title = "识别，再决定",
                        sub = "签到码交给签到站；网址使用原站容器；未知码不跳走。",
                        icon = MarkIcon.Scan,
                    )
                }
                // 區塊標題走共享組件：手寫的 Row + BodyMark 會漏掉 Dims.sectionGap
                // 的統一節奏（SPEC 的「稿上區塊標題帶圖標，代碼也要有，而且要走共享組件」）。
                item(key = "unknown-head") {
                    TronSection(text = "扫描结果", icon = MarkIcon.Scan)
                }
                item(key = "unknown-card") {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Dims.cardPadding)) {
                            KeyValueRow(
                                "类型",
                                "未知",
                                labelWidth = Dims.labelColumnWide,
                            )
                            // 原样摆出来，不猜它是什么。太长的内容截断（一张码很少长过 1000 字，
                            // 但截断比让它把卡片撑爆好）。
                            KeyValueRow(
                                "内容",
                                raw.take(200).ifBlank { "（空）" } +
                                    if (raw.length > 200) "…" else "",
                                labelWidth = Dims.labelColumnWide,
                            )
                            Spacer(Modifier.height(Dims.tight))
                            HintText("这个码既不是签到码也不是网址。没有替你打开任何页面，也没有提交任何东西。")
                        }
                    }
                }
                item(key = "unknown-actions") {
                    Column(modifier = Modifier.padding(top = Dims.sectionGap)) {
                        Button(
                            onClick = {
                                unknownRaw = null
                                notice = null
                                // ★ 唯一重建扫码的地方：把去重状态一起清掉，真的重开一次。
                                attempt++
                            },
                            // 命中区 ≥44dp：Miuix Button 默认高度已足够，这里再用最小高度兜一层。
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("继续扫描")
                        }
                        Spacer(Modifier.height(Dims.tight))
                        ActionRow(
                            title = "退出扫描",
                            subtitle = "回到刚才的页面",
                            action = "退出",
                            onClick = onExit,
                        )
                    }
                }
                item(key = "unknown-note") {
                    // 稿上這句還有後半截：「不模擬連續幀自動提交」。
                    // 後半截才是重點 —— 它承諾的是相機一直對著碼也不會反覆提交。
                    HintText("同一次识别只保留一份结果；继续扫描会显式重置，不模拟连续帧自动提交。")
                }
            }
        }
        return
    }

    key(attempt) {
        QrScannerPage(
            onExit = onExit,
            onScanned = { scanned ->
                when (val target = ScanRouter.classify(scanned)) {
                    is ScanRouter.Target.Web -> onWeb(target.url)
                    is ScanRouter.Target.Rollcall -> onRollcall(target.raw)
                    ScanRouter.Target.Unknown -> {
                        // 停在这一张上，不再自动重开（见上面那条长注释）。
                        unknownRaw = scanned
                        notice = "这个码既不是签到码也不是网址"
                    }
                }
            },
            modifier = modifier,
            // 标题写死会骗人：从应用页进来时手里那张码多半不是签到码。
            title = "通用扫一扫",
            subtitle = "对准二维码",
            hint = notice ?: "签到码、通知链接、活动页都可以扫",
            noPermissionText = "没有相机权限，无法扫码。可以返回后到应用页再试。",
            // 首屏選了相冊就直接拉起選圖器：省掉「進到相機裡再找底下那顆按鈕」。
            autoOpenGallery = picked == ScanSource.ALBUM,
        )
    }
}

/** 首屏的兩種取碼方式（稿上的「相機 / 相冊」兩顆 chip）。 */
private enum class ScanSource { CAMERA, ALBUM }