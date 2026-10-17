package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **設定板塊**：帳號、外觀、關於。
 *
 * ## 為什麼不把「重新登入／退出登入」放在這裡的第一屏
 *
 * 它們是**破壞性操作**（清憑據、踢回登入頁）。放在最顯眼的位置會誤觸。
 * 所以：
 * - 帳號區只**顯示**狀態（誰登入著）；
 * - 真正會動憑據的操作收在「帳號」卡片的下方，並用次要色而不是警示色 ——
 *   警示色留給「真的要出事」的地方（規範 §2：強調色只用兩處）。
 *
 * ## 為什麼沒有「開發者選項」
 *
 * 這一版是對外可用的版本。調參頁（規範 §15 建議的）應該由 **debug 建置**
 * 提供，而不是出現在使用者的設定裡。
 */
@Composable
fun SettingsSectionPage(
    graph: AppGraph,
    uiState: AppUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dims.screenH,
            end = Dims.screenH,
            top = 56.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dims.cardGap),
    ) {
        item(key = "title") {
            Text(
                text = "設定",
                fontSize = Type.largeTitle,
                fontWeight = Type.weightTitle,
                color = colors.onBackground,
                lineHeight = Type.largeTitle * Type.LINE_HEIGHT_TITLE,
            )
        }

        // ---- 帳號 ----------------------------------------------------------
        item(key = "account-label") { GroupLabel("帳號") }
        item(key = "account") {
            AppCard {
                Column(Modifier.padding(Dims.cardPadding)) {
                    Text(
                        text = if (uiState.loggedIn) uiState.displayAccount else "未登入",
                        fontSize = Type.body,
                        fontWeight = Type.weightBody,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (uiState.loggedIn) {
                            "已通過學校統一身份認證"
                        } else {
                            "登入後才能取用需要身份的功能"
                        },
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Dims.cardGap)) {
                        SettingChip("重新登入") { onOpenSettings() }
                        SettingChip("退出登入") { onOpenSettings() }
                    }
                }
            }
        }

        // ---- 外觀 ----------------------------------------------------------
        item(key = "appearance-label") { GroupLabel("外觀") }
        item(key = "appearance") {
            AppCard {
                Column(Modifier.padding(Dims.cardPadding)) {
                    AppThemeMode.entries.forEach { mode ->
                        ThemeRow(
                            label = mode.label,
                            selected = uiState.themeMode == mode,
                            onClick = { uiState.themeMode = mode },
                        )
                    }
                }
            }
        }

        // ---- 關於 ----------------------------------------------------------
        item(key = "about-label") { GroupLabel("關於") }
        item(key = "about") {
            AppCard {
                Column(Modifier.padding(Dims.cardPadding)) {
                    AboutLine("應用名稱", "玩在城院")
                    AboutLine("版本", rememberAppVersion())
                    AboutLine("資料來源", "浙大城市學院門戶與各業務系統")
                }
            }
        }

        // 弱化資訊（規範 §12 第 5 條：11~12sp 小灰字，不佔卡片）
        item(key = "footer") {
            Text(
                text = "本應用為非官方客戶端，資料所有權歸浙大城市學院。",
                fontSize = Type.micro,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        fontSize = Type.summary,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun ThemeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = Type.body,
            // ★ 選中**只用顏色**，不改字重（規範紅線 #6：改字重會讓整排字橫向抖動）
            fontWeight = FontWeight.Normal,
            color = if (selected) colors.primary else colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(text = "✓", fontSize = Type.body, color = colors.primary)
        }
    }
}

@Composable
private fun SettingChip(label: String, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            // `Modifier.background` 是擴展函式，接在鏈上（不能 `.then(background(...))`）
            .background(
                color = colors.secondaryContainer,
                shape = androidx.compose.foundation.shape.CircleShape,
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = label, fontSize = Type.summary, color = colors.onSecondaryContainer)
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            text = label,
            fontSize = Type.caption,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, fontSize = Type.caption, color = colors.onBackground)
    }
}

/**
 * 版本號。
 *
 * ## 為什麼從 `PackageManager` 讀而不是用 `BuildConfig`
 *
 * `BuildConfig` 只有在該建置變體開了 `buildFeatures.buildConfig = true` 時才存在。
 * 這個專案沒開（AGP 8 之後預設關），所以直接引用會編譯失敗。
 *
 * `PackageManager` 則是**任何變體都有**，而且讀到的就是實際安裝的版本 ——
 * 對「關於」頁來說那才是使用者真正要的資訊。
 *
 * **讀不到就回「—」**，不編一個版本號。
 */
@Composable
private fun rememberAppVersion(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember {
        runCatching {
            @Suppress("DEPRECATION")
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "—"
    }
}
