package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.data.tronclass.ScanRouter

/**
 * 全局扫一扫。
 *
 * 它自己**不做任何事**，只负责判和转交（判据在 [ScanRouter]）：
 *
 * | 扫到 | 去处 |
 * |---|---|
 * | `http(s)://…` | 交给 [onWeb] 开 WebView |
 * | 学在城院的签到码 | 交给 [onRollcall] 带去签到页（**提交仍由他按**） |
 * | 其它 | 留在本页、说明这是什么情况，接着扫 |
 *
 * 为什么「其它」不退回上一页：扫码本来就是「对准 → 发现不对 → 再对准」的事，
 * 一次认不出就被踢出页面，等于让他重新点一遍入口。相机留着，他挪一下就行。
 */
@Composable
fun ScannerPage(
    onExit: () -> Unit,
    onWeb: (String) -> Unit,
    onRollcall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    /** 上一张码为什么没成 —— 直接顶在相机下面的提示行里。 */
    var notice by remember { mutableStateOf<String?>(null) }

    /**
     * 重新扫一次的代数。
     *
     * [QrScannerPage] 内部用 `reported` 去重（同一张码在连续帧里会重复命中，
     * 不去重会重复回调）。那张码判成「不是」之后必须能再扫，
     * 而 `reported` 是 remember 的、自己不会清 —— 换 key 重建它才是真的重开一次。
     */
    var attempt by remember { mutableIntStateOf(0) }

    key(attempt) {
        QrScannerPage(
            onExit = onExit,
            onScanned = { raw ->
                when (val target = ScanRouter.classify(raw)) {
                    is ScanRouter.Target.Web -> onWeb(target.url)
                    is ScanRouter.Target.Rollcall -> onRollcall(target.raw)
                    ScanRouter.Target.Unknown -> {
                        notice = "这个码既不是签到码也不是网址，换个码再试"
                        attempt++
                    }
                }
            },
            modifier = modifier,
            // 标题写死会骗人：从应用页进来时手里那张码多半不是签到码。
            title = "扫一扫",
            subtitle = "对准二维码",
            hint = notice ?: "签到码、通知链接、活动页都可以扫",
            noPermissionText = "没有相机权限，无法扫码。可以返回后到应用页再试。",
        )
    }
}
