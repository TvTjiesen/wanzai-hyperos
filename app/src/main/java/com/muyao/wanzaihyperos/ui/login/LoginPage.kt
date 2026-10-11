package com.muyao.wanzaihyperos.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.ui.common.DemoBanner

/** 登录表单的显式状态。不把「空」和「还没提交过」混成一个 boolean。 */
private sealed interface LoginFormState {
    data object Idle : LoginFormState
    data object Submitting : LoginFormState
    data class AccountError(val message: String) : LoginFormState
    data class PasswordError(val message: String) : LoginFormState
    data class Failed(val message: String) : LoginFormState
}

/**
 * 登录页。
 *
 * 已覆盖方案要求的状态：输入校验（学号/密码为空、格式明显不对）、
 * 提交中（按钮禁用 + 进度指示，**防重复提交**）、错误提示（字段级 + 整页级分开）、
 * 键盘适配（`imePadding()` + 可滚动列）。
 *
 * ### [onAuthenticate]：接真实认证的口子
 * 传了它就**真的连学校统一身份认证**；不传则保留 A1 的展示分支
 * （只模拟一次往返），这样离线走查不必先有账号。
 *
 * 它的返回值约定是「**null = 成功，非 null = 给用户看的一句错**」而不是抛异常：
 * 登录失败（密码错、账号锁）与网络失败是**两种不同的东西**，
 * 前者要用户重敲，后者只要重试，异常在页面里区分起来很别扭。
 *
 * 凭据纪律：密码只存在于本 Composable 的内存状态里，
 * **不落盘、不打日志、不进 chat**；离开页面即随组合销毁。
 */
@Composable
fun LoginPage(
    onLoggedIn: (account: String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onAuthenticate: (suspend (account: String, password: String) -> String?)? = null,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<LoginFormState>(LoginFormState.Idle) }

    val submitting = state is LoginFormState.Submitting

    // 字段级错误要高亮对应输入框（TextField 没有 isError 参数，
    // 按 miuix-skill 的做法用 textFieldColors + 下方文字，不只靠颜色）。
    val accountError = (state as? LoginFormState.AccountError)?.message
    val passwordError = (state as? LoginFormState.PasswordError)?.message

    val errorColor = colors.error

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // 品牌区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "玩在城院",
                style = MiuixTheme.textStyles.title2,
                color = colors.onBackground,
            )
            Text(
                text = "浙大城市学院校园聚合入口",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onBackgroundVariant,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        DemoBanner(
            text = if (onAuthenticate == null) {
                "A1 展示版：登录不会真正连接学校服务器"
            } else {
                "将连接学校统一身份认证；密码只存在内存与加密存储，不落盘、不进日志"
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        SmallTitle(text = "统一身份认证")

        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = account,
                    onValueChange = {
                        account = it
                        if (state is LoginFormState.AccountError) state = LoginFormState.Idle
                    },
                    label = "学号 / 工号",
                    useLabelAsPlaceholder = true,
                    enabled = !submitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    colors = if (accountError != null) {
                        TextFieldDefaults.textFieldColors(borderColor = errorColor, labelColor = errorColor)
                    } else {
                        TextFieldDefaults.textFieldColors()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (accountError != null) {
                    Text(
                        text = accountError,
                        style = MiuixTheme.textStyles.footnote2,
                        color = errorColor,
                    )
                }

                TextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (state is LoginFormState.PasswordError) state = LoginFormState.Idle
                    },
                    label = "密码",
                    useLabelAsPlaceholder = true,
                    enabled = !submitting,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    colors = if (passwordError != null) {
                        TextFieldDefaults.textFieldColors(borderColor = errorColor, labelColor = errorColor)
                    } else {
                        TextFieldDefaults.textFieldColors()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (passwordError != null) {
                    Text(
                        text = passwordError,
                        style = MiuixTheme.textStyles.footnote2,
                        color = errorColor,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                // 防重复提交：提交中直接忽略。
                if (submitting) return@Button
                val trimmed = account.trim()
                when {
                    trimmed.isEmpty() -> state = LoginFormState.AccountError("请输入学号 / 工号")
                    trimmed.length < 4 -> state = LoginFormState.AccountError("学号看起来不对（至少 4 位）")
                    password.isEmpty() -> state = LoginFormState.PasswordError("请输入密码")
                    password.length < 6 -> state = LoginFormState.PasswordError("密码至少 6 位")
                    else -> {
                        state = LoginFormState.Submitting
                        val authenticate = onAuthenticate
                        scope.launch {
                            if (authenticate == null) {
                                // 展示分支（没有注入真实认证器）：只模拟一次往返，
                                // 让「提交中」与「失败」两个状态可以被走查看到。
                                delay(900)
                                if (trimmed == "0000") {
                                    state = LoginFormState.Failed("认证未通过：学号或密码不正确")
                                } else {
                                    onLoggedIn(trimmed)
                                }
                            } else {
                                // 真实认证。密码只作为参数穿过，绝不进日志/存储。
                                val message = authenticate(trimmed, password)
                                if (message == null) {
                                    onLoggedIn(trimmed)
                                } else {
                                    state = LoginFormState.Failed(message)
                                }
                            }
                        }
                    }
                }
            },
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (submitting) {
                    InfiniteProgressIndicator(
                        color = colors.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Lock,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(text = if (submitting) "正在认证…" else "登录")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 整页级失败提示：与字段级错误分开，避免「密码错了」和「网络不通」混淆。
        val failed = state as? LoginFormState.Failed
        if (failed != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = failed.message,
                            style = MiuixTheme.textStyles.footnote1,
                            color = errorColor,
                            textAlign = TextAlign.Start,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "可重试；若一直失败，请检查学号与密码后重新输入。",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // A1 展示版允许跳过登录直接看界面 —— 这样 UI 走查不必先有真账号。
        TextButton(
            text = "先看看界面（不登录）",
            onClick = onSkip,
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
