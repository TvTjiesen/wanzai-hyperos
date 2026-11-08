package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.account.AccountSource
import com.muyao.wanzaihyperos.data.account.AccountStatus
import com.muyao.wanzaihyperos.data.account.AccountTarget
import com.muyao.wanzaihyperos.data.account.accountFootnoteText
import com.muyao.wanzaihyperos.data.account.snapshotAccountSources
import com.muyao.wanzaihyperos.ui.AppUiState
import com.muyao.wanzaihyperos.ui.design.AppColors
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.PaperCard
import com.muyao.wanzaihyperos.ui.design.SettingGroup
import com.muyao.wanzaihyperos.ui.design.SettingIcon
import com.muyao.wanzaihyperos.ui.design.SettingRow
import com.muyao.wanzaihyperos.ui.design.Tone
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.Type
import com.muyao.wanzaihyperos.ui.design.liquid.statusBarTopPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.muyao.wanzaihyperos.ui.design.ai.AiBall
import com.muyao.wanzaihyperos.ui.design.ai.AiBallStates
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row

/**
 * 「账号」二级页（B4-06，业主 2026-10-23：设置页把六源收进二级页）。
 *
 * ## 收进来的是**布局**，不是信息
 *
 * 六套登录态互不牵连（凭据与寿命各不相同），所以这一页照原样**逐条**列：
 * 状态 · 最近一次真实时间 · 去哪登。设置页首页只留一行「已登录 N 项 / 共 6 项」。
 *
 * ★ 仍然**没有任何代登录**：点一行只是跳到对应的页，账号密码永远由他自己填。
 */
@Composable
fun AccountsPage(
    graph: AppGraph,
    uiState: AppUiState,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit = {},
    onOpenJw: () -> Unit = {},
    onOpenZhgy: () -> Unit = {},
    onOpenSzty: () -> Unit = {},
    onOpenApps: () -> Unit = {},
    /**
     * 刷新键：**必须**随「回到这一页」变化。
     *
     * ⚠️ 08:2x 的只读审计抓到过：这里原来写的是 `LaunchedEffect(Unit)`，而 Miuix 的导航
     * **不会把被盖住的那一屏移出组合** ⇒ 从登录页返回时这一屏根本没重新取快照，
     * 六行还停在「未登录」。现在键 = 返回栈深度 + 登录态（`WanzaiRoot` 传进来）。
     */
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    var sources by remember { mutableStateOf<List<AccountSource>>(emptyList()) }
    // 退出登录之后要重取一次快照（清完凭据，六行必须立刻变成"未登录"）
    var reload by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var loggingOut by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(refreshKey, uiState.loggedIn, uiState.displayAccount, reload) {
        sources = withContext(Dispatchers.IO) {
            snapshotAccountSources(
                portal = graph.portalSession,
                tronclass = graph.tronclassSession,
                jw = graph.jwSession,
                cjh52 = graph.cjh52Session,
                zhgy = graph.zhgySession,
                szty = graph.sztySession,
            )
        }
    }
    fun open(target: AccountTarget) = when (target) {
        AccountTarget.PortalLogin -> onOpenLogin()
        AccountTarget.JwPage -> onOpenJw()
        AccountTarget.ZhgyPage -> onOpenZhgy()
        AccountTarget.SztyPage -> onOpenSzty()
        AccountTarget.AppsTab -> onOpenApps()
        AccountTarget.None -> Unit
    }

    SecondaryScaffold(
        // ★ 返工总单 A1：标题按稿写成「账号与连接」（原「账号」与设置页那一行不一致）。
        title = "账号与连接",
        onBack = onBack,
        modifier = modifier,
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dims.screenH)
                .padding(
                    top = inner.calculateTopPadding(),
                    bottom = inner.calculateBottomPadding(),
                ),
        ) {
        // ── 首屏英雄区（稿 v2.1 `accounts.png` 顶部三行）──────────────
        // 稿：「账号连接 / 校园账号 / 1 / 7 个来源已设置 · 不是服务器在线证明」。
        // 第三行那句是**反过来说明**的：写「登录成功」会让人以为别的来源跟着通了。
        run {
            // 「已设置」只算三种确有其事的：有凭据、码已拿到但未激活、网页内独立登录。
            // `Unknown`（还没读到）**不算**——把读不到说成「没设置」和说成「设置了」都是撒谎。
            val done = sources.count {
                it.status == AccountStatus.LoggedIn ||
                    it.status == AccountStatus.ObtainedNotActivated ||
                    it.status == AccountStatus.InWebOnly
            }
            // 返工总单 B6：首个区块标题左边挂一颗 44dp 的球（稿 `.account-head
            // .ball-button{justify-self:start}`）。脸色由本页真数据算：sources 还没
            // 读到任何一条 => Loading；有一条 Unknown => 仍算不知道；都有结果 => Idle。
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dims.tight, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiBall(
                    state = AiBallStates.ofAccounts(sources.takeIf { it.isNotEmpty() }),
                    diameter = AiBall.DIAMETER_PAGE,
                    hitSlop = AiBall.INLINE_SLOP,
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                Text(
                    text = "账号连接",
                    fontSize = Type.micro,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(0.08f, androidx.compose.ui.unit.TextUnitType.Em),
                    fontWeight = Type.weightTitle,
                    color = colors.primary,
                )
                Text(
                    text = "校园账号",
                    fontSize = Type.displayTitle,
                    fontWeight = Type.weightTitle,
                    color = colors.onBackground,
                    // 首屏大字是这一页的标题：给 heading 语义，读屏可以直接跳到这里，
                    // 而不是逐个读完七个来源才发现「这是一页账号」。
                    modifier = Modifier.padding(top = 3.dp).semantics { heading() },
                )
                Text(
                    text = done.toString() + " / " + sources.size + " 个来源已设置 · 不是服务器在线证明",
                    fontSize = Type.micro,
                    color = colors.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                            }}
        }
        // ★ 判据用**数据层的真实状态**，不用 `uiState.loggedIn`：后者全工程只有
        //   `WanzaiRoot` 两处置 true、**0 处置 false** ⇒ 点了「退出登录」六行变未登录、
        //   头部还写着「当前身份 …」（工单第 14 轮点名的那条自相矛盾）。
        val anySignedIn = sources.any { it.status == AccountStatus.LoggedIn }
        // 身份单独一行；「各系统互不牵连」那句已经在英雄区第三行说过（
        // 「不是服务器在线证明」），这里再写一遍就是同一句话占两行。
        if (anySignedIn && uiState.displayAccount.isNotBlank()) {
            Text(
                text = "当前身份 " + uiState.displayAccount,
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = Type.tabular),
            )
        }

        Spacer(Modifier.height(Dims.cardGap))
        if (sources.isEmpty()) {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Shield,
                    tint = Tone.quiet,
                    title = "正在读取各系统的登录状态",
                    subtitle = "只读这台手机里的加密存储，不向学校发请求",
                )
            }
        } else {
            // 稿 .paper.flush：来源列表是一张纸，行自带内距与分隔线。
            PaperCard {
                sources.forEach { source ->
                    AccountSourceRow(
                        source = source,
                        onClick = if (source.target == AccountTarget.None) null else {
                            { open(source.target) }
                        },
                    )
                }
            }
            Spacer(Modifier.height(Dims.cardGap))
            Text(
                text = accountFootnoteText(sources),
                fontSize = Type.caption,
                color = colors.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(Dims.cardGap))
        // ── 退出登录（B4-06 第 2 处真缺陷：`AppGraph.logout()` 之前全工程 0 调用点）──
        //
        // 只在他确实有登录态时出现 —— 一个永远点了没反应的按钮比没有按钮更糟。
        // 这是**写操作里唯一不需要二次确认的**：它只清本机凭据，不向学校发任何请求。
        if (sources.any { it.status == AccountStatus.LoggedIn }) {
            SettingGroup {
                SettingRow(
                    icon = SettingIcon.Shield,
                    tint = Tone.warn,
                    title = if (loggingOut) "正在退出…" else "退出登录",
                    subtitle = "清掉这台手机上保存的各系统登录态；学校网页端不受影响",
                    onClick = if (loggingOut) null else {
                        {
                            loggingOut = true
                            scope.launch {
                                runCatching { graph.logout() }
                                reload++
                                loggingOut = false
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(Dims.cardGap))
        }
        }
    }
}

/**
 * 一行账号源。**只有这一处调用点**（设置首页只列聚合行，不再逐条渲染）：
 * 从设置页的 `forEach` 里搬出来时保留成函数，是为了以后再加一处入口时不至于抄第二份。
 */
@Composable
internal fun AccountSourceRow(source: AccountSource, onClick: (() -> Unit)?) {
    val colors = MiuixTheme.colorScheme
    SettingRow(
        icon = glyphFor(source),
        tint = tileTintFor(source.status),
        title = source.name,
        // 两句各占一行，**不靠折行分家**（v3 #8/#11，业主 1.15 字号实测）：
        // 拼成一整串时，11sp 下每行都在句中任意位置折出第二行、末尾吊着一两个字。
        subtitle = "${source.provides}\n${source.whereToLogin}",
        onClick = onClick,
        // 六行**全部**要这个槽：不可点的那行只是槽里空着，右边缘才与另外几行在同一根线上。
        trailingChevron = true,
        trailing = {
            val timeLine = source.timeText()
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = source.statusText,
                    fontSize = Type.summary,
                    color = statusTextColor(source.status),
                )
                if (timeLine.isNotEmpty()) {
                    Text(
                        text = timeLine,
                        fontSize = Type.caption,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        },
    )
}

/** 每源一枚图形，**一行一枚**：共用图形就等于告诉用户「这两行是一回事」。 */
private fun glyphFor(source: AccountSource): SettingIcon = when (source.id) {
    "portal" -> SettingIcon.Shield
    "tronclass" -> SettingIcon.Cap
    "jw" -> SettingIcon.Doc
    "cjh52" -> SettingIcon.Badge
    "zhgy" -> SettingIcon.House
    "szty" -> SettingIcon.Dumbbell
    else -> SettingIcon.Screen
}

/**
 * 行首图形的**笔画色**（2026-11-07 起不再有磁贴底色，见 `SettingsChrome.SettingGlyph`）。
 *
 * 三档沿用状态语义：登入 = 靛蓝 `Tone.ok`、拿到了没激活 = 橙 `Tone.warn`、其余 = 灰 `Tone.quiet`。
 * 断言的是**线条对卡面**的对比度 —— `ok` 7.34:1、`warn` 5.05:1、`quiet` 5.24:1（深色档 6.57/5.90/5.94），
 * 三档都过 WCAG 1.4.11 的 3:1（非文字图形）且都过 4.5:1（按文字判也够）。
 * 原来那句「白字压底色 4.5–6.2:1」随磁贴一起作废。
 */
private fun tileTintFor(status: AccountStatus): Color = when (status) {
    AccountStatus.LoggedIn -> Tone.ok
    AccountStatus.ObtainedNotActivated -> Tone.warn
    AccountStatus.LoggedOut, AccountStatus.Unknown, AccountStatus.InWebOnly -> Tone.quiet
}

/**
 * 状态**文字**色。★ 不能复用上面那三档：把浅色板的 #2E6B64 当深色模式下的 12sp 文字，
 * 对卡底只有 2.4–3.0:1（AA 要 4.5），换主题令牌是 7.2–8.9:1（第 3 轮复审计算出来的）。
 */
@Composable
private fun statusTextColor(status: AccountStatus): Color {
    val colors = MiuixTheme.colorScheme
    return when (status) {
        AccountStatus.LoggedIn -> colors.primary
        AccountStatus.ObtainedNotActivated -> colors.error
        AccountStatus.LoggedOut, AccountStatus.Unknown, AccountStatus.InWebOnly ->
            colors.onSurfaceVariantSummary
    }
}
