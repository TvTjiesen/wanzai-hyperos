package com.muyao.wanzaihyperos.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **延遲出現的載入指示器**。
 *
 * ## ★ 為什麼要延遲（規範 §10）
 *
 * 規範原話：「**超過 1.5s 才出現 spinner**（更快就別閃）。」
 *
 * 這不是潔癖。多數校園介面其實 **200~600ms** 就回來了，
 * 而 spinner 一閃而過的效果是「**頁面抖了一下**」——
 * 比什麼都不顯示更讓人不安（眼睛會追那個閃動）。
 *
 * 所以：**先等，真的久了才顯示**。快的時候使用者只看到「內容直接出現」。
 *
 * ## 為什麼不是「先顯示骨架」
 *
 * 骨架要對每一種列表的形狀建模（課表格子、成績行、公告列表各不相同）。
 * 而本專案的多數頁面本身就是「一列卡片」，骨架和真實內容長得幾乎一樣——
 * 那就退化成「先畫一堆灰條再換成卡片」，反而更閃。
 * 所以這裡選**空白等待 + 延遲 spinner**，而不是骨架。
 *
 * @param loading 是否正在載入
 * @param modifier 修飾符
 * @param label 載入時的文字（可空；空了就只有 spinner）
 */
@Composable
fun DelayedLoading(
    loading: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    // 先用「不可見」開局，等夠久才切到「顯示」。
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(loading) {
        if (!loading) {
            visible = false
            return@LaunchedEffect
        }
        // 延遲期間若 loading 變成 false，這個協程會被取消 —— 於是 visible 永遠不會變 true。
        // 這正是我們要的：「快就別閃」。
        kotlinx.coroutines.delay(Motion.SPINNER_DELAY_MS)
        visible = true
    }

    // 沒載入、或還沒到顯示時間 → **什麼都不畫**。
    // 不是「畫了但透明」：那仍然佔位且參與佈局。
    if (!loading || !visible) return

    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InfiniteProgressIndicator(
            color = colors.primary,
            modifier = Modifier.size(28.dp),
        )
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}
