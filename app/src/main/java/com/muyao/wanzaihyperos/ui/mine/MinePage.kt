package com.muyao.wanzaihyperos.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.common.DemoBanner
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.PageState

/**
 * 我的：登录状态 / 重新登录 / 主题切换 / 退出登录。
 *
 * 方案要求「登录状态」显示**忠实**：
 * - 已登录 → 显示账号 + 说明这是展示版（**不谎称**「凭据已加密保存」，
 *   因为 A1 根本没存任何凭据）；
 * - 登录失效 → 明确引导重新登录，**不显示成未登录而不解释**。
 */
@Composable
fun MinePage(
    loggedIn: Boolean,
    account: String,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onRelogin: () -> Unit,
    onLogout: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var sessionExpired by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 12.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的",
                    style = MiuixTheme.textStyles.title4,
                    color = colors.onBackground,
                )
                TextButton(
                    text = "模拟登录失效",
                    onClick = { sessionExpired = !sessionExpired },
                )
            }
        }

        item {
            // A3 起这里说的话必须是真的：App 确实会保存凭据（加密保存）、也确实连了学校服务器。
            // 再写「不保存任何凭据」就是反向撒谎。
            DemoBanner(
                text = "凭据（ACCESS_TOKEN 与 CAS 会话 Cookie）只进加密存储" +
                    "（AES-256-GCM，密钥在 Android Keystore），不落普通文件、不打日志。",
            )
        }

        // ---- 账号卡 ---------------------------------------------------------------
        item {
            SmallTitle(text = "账号")
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.ContactsCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                sessionExpired -> "登录状态已失效"
                                loggedIn -> account.ifEmpty { "已登录" }
                                else -> "未登录"
                            },
                            style = MiuixTheme.textStyles.body1,
                            color = if (sessionExpired) colors.error else colors.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                sessionExpired -> "请重新登录；在重新登录前，其他页面也不应把未知数据显示成 0"
                                loggedIn -> "已通过学校统一身份认证 · 凭据在加密存储中"
                                else -> "登录后可加载真实数据（A3 接入）"
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    text = "重新登录",
                    onClick = {
                        sessionExpired = false
                        onRelogin()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "退出登录",
                    onClick = { showLogoutConfirm = true },
                    enabled = loggedIn,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (sessionExpired) {
            item {
                StatePlaceholder(
                    state = PageState.SessionExpired,
                    onAction = {
                        sessionExpired = false
                        onRelogin()
                    },
                    actionLabel = "去登录",
                )
            }
        }

        // ---- 外观 -----------------------------------------------------------------
        item { SmallTitle(text = "外观") }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        // 用 RadioButtonPreference 而不是手搓「RadioButton + Text」：
                        // 前者整行可点，后者只有那个小圆点能点（真机走查时点「深色」
                        // 文字没反应就是这个原因）。
                        RadioButtonPreference(
                            title = mode.label,
                            summary = if (mode == AppThemeMode.System) {
                                "跟随手机系统的深色/浅色设置"
                            } else {
                                null
                            },
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "主题由根节点唯一持有；页面内不再嵌套主题，避免双重留白与顶栏重叠。",
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
            )
        }

        // ---- 关于 -----------------------------------------------------------------
        item { SmallTitle(text = "关于") }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AboutRow("应用", "玩在城院（Kotlin / HyperOS 版）")
                    AboutRow("当前阶段", "A1 · Miuix UI 骨架与四页导航")
                    AboutRow("示例数据", "是（未接入学校接口）")
                    AboutRow("主题与 Insets", "根节点统一持有")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // 退出登录二次确认（破坏性操作，按 miuix-skill 用 OverlayDialog + 主/次动作）。
    OverlayDialog(
        show = showLogoutConfirm,
        onDismissRequest = { showLogoutConfirm = false },
        title = "退出登录",
        summary = "退出后需要重新输入学号与密码。退出会清掉加密存储里的凭据、会话 Cookie，" +
            "以及学在城院网页版留在本机的登录状态，不留残留。",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { showLogoutConfirm = false },
                modifier = Modifier.weight(1f),
            )
            top.yukonga.miuix.kmp.basic.Button(
                onClick = {
                    showLogoutConfirm = false
                    onLogout()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "退出")
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceVariantSummary)
        Text(text = value, style = MiuixTheme.textStyles.footnote1, color = colors.onSurface)
    }
}
