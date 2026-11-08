package com.muyao.wanzaihyperos.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.muyao.wanzaihyperos.session.ZhgySession
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.Mark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.ResultBanner
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.Type
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * **智慧公寓的入口闸门**：有凭据就开页面，没有就请使用者贴一次链接。
 *
 * ## 为什么需要这一层（而不是直接开网页）
 *
 * 智慧公寓真正的入口是 `/mob/zuccindex.html?code=<JWT>`，
 * 而那段 `code` **只能由使用者自己取得**：门户给的入口是钉钉免登
 *（`/mob/zuccding/auth`），授权码由钉钉容器签发，浏览器里拿不到。
 * 完整证据见 `docs/B17-WALKTHROUGH.md` 与 `docs/B17-ZHGY-LINK-PROBE.txt`。
 *
 * 所以这一页做的事是：
 *
 * ```
 * 没设过 → 请使用者贴上「带 code 的链接」
 * 设过了 → 直接复用 [WebShellPage] 载入（不再经过钉钉那条）
 * ```
 *
 * ## ★ 为什么「贴一次就能一直用」
 *
 * 真机实测：那段 `code` **可重复使用** ——
 * 用 CDP 清掉 cookie 再用**同一个** code 开，照样进得去并重新种下 `ihome`。
 * 所以不必每次要使用者贴；存进加密存储即可。
 *
 * ## 只抽 `code`，不存整条链接
 *
 * 链接里除 `code` 外可能还有带时效或追踪用途的参数，
 * 留着没好处。见 [ZhgySession.extractCode]。
 *
 * ## ★ 凭据纪律：链接是敏感物，不进正文
 *
 * 稿（`apartment.png`）那句「接入只保存本次评审的演示标记，**不读取剪贴板**」
 * 讲的是这一页的底线，本页照它执行：
 *
 * 1. **不后台读剪贴板** —— 只有使用者主动点「从剪贴板粘贴」时才读一次，
 *    读完立刻只抽 `code`，原文不留在任何 state 里（[input] 也会被清空）。
 * 2. **不把链接印在界面上** —— 输入框是使用者自己粘的，`code` 存进去之后
 *    只显示「已设置」，不回显任何片段。
 * 3. 存进 [ZhgySession] 之后，日志里只会出现长度，不会出现值。
 */
@Composable
fun ZhgyGatePage(
    session: ZhgySession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 与 Cjh52WarmUpGate 同一种写法：用 remember 记住「现在有没有凭据」，
    // 存进去之后就地在当前页面上换成开页面（不必导航到另一个路由）。
    var hasCode by remember { mutableStateOf(session.hasCode) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(android.content.ClipboardManager::class.java)
    }

    /**
     * 从剪贴板读一行文字并**只取其中可用的部分**。
     *
     * ### ★ 为什么要有这个按钮
     *
     * 使用者的实际流程是「在别的地方复制链接 → 回到这个 App 贴上」。
     * 没有这个按钮时他要长按输入框、选粘贴 —— 而多数输入法在这个框里
     * 长按的行为不一致（有的只给「粘贴」，有的要先全选）。
     * 一键读剪贴板把这件事变成一次点击。
     *
     * ⚠️ 只在用户**主动点按**时才读剪贴板，而且**不保留原文** ——
     * 读到的东西除了抽出 `code` 之外不落任何地方。
     * 这也是 Android 对剪贴板的礼貌做法：不后台轮询、不静默读取。
     */
    fun pasteFromClipboard() {
        val text = runCatching {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        if (text.isNullOrBlank()) {
            error = "剪贴板是空的。请先在别处复制智慧公寓的网址。"
            return
        }
        input = text
        error = null
    }

    val url = remember(hasCode) { if (hasCode) session.entryUrl() else null }
    if (url != null) {
        WebShellPage(
            title = "智慧公寓",
            url = url,
            onExit = onExit,
            modifier = modifier,
            // 设置过之后要留一条**正路**可以撤销 —— 否则用户想换账号或
            // 单纯不想再留着凭据时，只能重装 App（那是我实际踩到过的情况）。
            onClearCredential = {
                session.clear()
                hasCode = false
            },
        )
        return
    }

    // ★ 用共用的 SecondaryScaffold（不透明底色 + 安全区），
    //   不要自己拼 —— 真机上第一版就是因为自己拼而透出下面的首页，
    //   且返回键被状态栏盖住。同一个坑在 B16 也踩过，所以它已抽到设计层。
    SecondaryScaffold(
        title = "智慧公寓",
        subtitle = "从你的入口开始 · 需要一次性的入口链接",
        onBack = onExit,
        modifier = modifier,
    ) { innerPadding ->
        AppList(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            item(key = "zhgy-hero") {
                SecHero(
                    eyebrow = "APARTMENT / 智慧公寓",
                    title = "从你的入口开始",
                    sub = "接入只保存这一次的凭据，不后台读取剪贴板。",
                )
            }

            item(key = "zhgy-notice") {
                SecNotice(
                    "真实链接里的 code 属于敏感凭据，等于一次登录态，不要放到聊天记录或截图里。" +
                        "本页只在你主动点按时读剪贴板，读完只留 code 那一段。",
                )
            }

            error?.let { msg ->
                item(key = "zhgy-error") { ResultBanner(message = msg, ok = false) }
            }

            item(key = "zhgy-field-label") {
                Text(
                    text = "完整接入链接（含 code=）",
                    fontSize = Type.caption,
                    lineHeight = Type.lineCaption,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = Dims.tight),
                )
            }

            item(key = "zhgy-field") {
                TextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = "粘贴智慧公寓的网址（含 code=）",
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ★ 一键从剪贴板贴入：使用者的实际流程就是「别处复制 → 这里贴」。
            item(key = "zhgy-paste") {
                Button(
                    onClick = { pasteFromClipboard() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从剪贴板粘贴") }
            }

            item(key = "zhgy-save") {
                Button(
                    onClick = {
                        val code = ZhgySession.extractCode(input)
                        if (code.isNullOrBlank()) {
                            error = "没认出 code。请粘贴完整网址（里面要有 code=… 那一段）。"
                        } else {
                            session.save(code, System.currentTimeMillis())
                            // 输入框要清掉 —— 画面上不该继续留着凭据。
                            input = ""
                            error = null
                            hasCode = true
                        }
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("校验并使用此入口") }
            }

            // 三条说明原先各占一个 HintText，行间松散；并成一块，每条前一枚图标。
            item(key = "zhgy-help") {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Dims.cardPadding)) {
                        SecHintRow(
                            icon = MarkIcon.Account,
                            text = "这个系统的登录由“钉钉”签发授权码，本 App 拿不到。" +
                                "在能正常打开智慧公寓的地方复制网址（含 code= 的那一串），回到这里粘贴。",
                        )
                        SecHintRow(
                            icon = MarkIcon.Data,
                            text = "说明：只会从你粘贴的网址里抽出 code 那一段，存在手机的加密存储里；" +
                                "其余参数不会保留。这个码可以重复使用，设置一次即可 —— 重装 App 才会清掉。",
                            modifier = Modifier.padding(top = Dims.loose),
                        )
                        SecHintRow(
                            icon = MarkIcon.Warn,
                            text = "为什么不能自动登录：该入口需要钉钉容器签发的授权码，网页环境取不到；" +
                                "门户给的那条路径在没有钉钉时必定显示“授权码已过期”。",
                            modifier = Modifier.padding(top = Dims.loose),
                        )
                    }
                }
            }
            item(key = "zhgy-origin") {
                HintText(
                    "报修、内务分、缴费等仍由智慧公寓原站承载；本 App 不凭业务名称伪造学校表单。",
                )
            }
        }
    }
}

/** 页头三行：眉题 / 大标题 / 一句副题（照稿 `.eyebrow` + `.hero-title` + `.muted`）。 */
@Composable
private fun SecHero(eyebrow: String, title: String, sub: String) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = eyebrow,
            fontSize = Type.caption,
            fontWeight = Type.weightTitle,
            letterSpacing = 0.10.em,
            color = Tone.quiet,
            style = TextStyle(fontFeatureSettings = Type.tabular),
        )
        Text(
            text = title,
            fontSize = Type.displayTitle,
            lineHeight = Type.lineDisplayTitle,
            fontWeight = Type.weightTitle,
            letterSpacing = (-0.02).em,
            color = colors.onBackground,
            modifier = Modifier.padding(top = Dims.tight, bottom = Dims.tight),
        )
        Text(
            text = sub,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = colors.onSurfaceVariantSummary,
            textAlign = TextAlign.Start,
        )
    }
}

/** 说明行：正文前一枚图标 + 一段说明文字（图标与首行文字基线对齐）。 */
@Composable
private fun SecHintRow(icon: MarkIcon, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dims.normal),
    ) {
        BodyMark(
            icon = icon,
            tint = Tone.quiet,
            size = Mark.size,
            modifier = Modifier.padding(top = Dims.tight),
        )
        Text(
            text = text,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 说明条：左侧一道竖线 + 淡底（照稿 `.notice`，不是一张卡片）。 */
@Composable
private fun SecNotice(text: String, error: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val tint = if (error) colors.error else colors.onSurfaceVariantSummary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.55f)),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(tint),
        )
        Text(
            text = text,
            fontSize = Type.caption,
            lineHeight = Type.lineCaption,
            color = tint,
            modifier = Modifier.padding(
                start = Dims.loose,
                end = Dims.cardPadding,
                top = Dims.normal,
                bottom = Dims.normal,
            ),
        )
    }
}