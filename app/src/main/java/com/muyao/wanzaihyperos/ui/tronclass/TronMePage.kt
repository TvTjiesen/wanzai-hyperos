package com.muyao.wanzaihyperos.ui.tronclass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.data.tronclass.TronMeRepository
import com.muyao.wanzaihyperos.ui.common.StatePlaceholder
import com.muyao.wanzaihyperos.ui.common.toPageState
import com.muyao.wanzaihyperos.ui.design.ActionRow
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.KeyValueRow
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 學在城院「**我的**」。
 *
 * ### 顯示什麼、不顯示什麼
 *
 * 來源是 `/api/profile`（實測可用）。它回傳的欄位遠多於這裡顯示的 ——
 * 這裡**刻意只留展示需要的**：
 *
 * | 顯示 | 不顯示 | 為什麼不顯示 |
 * |---|---|---|
 * | 姓名、學號 | 手機號、郵箱 | 個資，App 沒有理由把它們載進記憶體只為顯示 |
 * | 院系、年級、班級 | 內部 `id` | 識別碼，界面不該出現 |
 * | 頭像 | `program`／`storage_*` | 與使用者無關的實作欄位 |
 *
 * 這與本專案一貫做法一致（A3 不顯示 `url`、B4 卡號只留後四位）。
 *
 * ### 統計數字：取不到是「—」不是「0」
 *
 * 待辦數／課程數／進行中簽到數都是 `Int?`。
 * 「不知道有幾個」與「一個都沒有」是兩件事 —— 這是 A2 起就有的紀律。
 */
@Composable
fun TronMePage(
    repository: TronMeRepository,
    modifier: Modifier = Modifier,
    onOpenRollcall: (() -> Unit)? = null,
    onFallbackToWeb: (() -> Unit)? = null,
) {
    val state by repository.state.collectAsState()
    val pageState = state.toPageState()
    val content = state as? ListState.Content
    val me = content?.items?.firstOrNull()
    val failures = me?.statsErrors.orEmpty().map { TronSourceFailure("概览统计", it) }
    val meta = content?.meta ?: (state as? ListState.Empty)?.meta ?: ListMeta()
    val hasProblem = tronHasPartialProblem(meta, failures)

    LaunchedEffect(Unit) {
        if (state is ListState.FirstLoad) repository.refresh()
    }

    if (me == null && hasProblem) {
        AppList(modifier = modifier) {
            item { TronPartialNotice(meta, failures, repository::refresh) }
            item { HintText("个人资料与概览尚未确认，不能据此判断没有数据。") }
        }
        return
    }
    if (me == null) {
        StatePlaceholder(
            state = pageState,
            modifier = modifier,
            onAction = onFallbackToWeb,
            actionLabel = if (onFallbackToWeb != null) "改用网页版" else null,
        )
        return
    }

    val colors = MiuixTheme.colorScheme
    AppList(modifier = modifier) {
        if (hasProblem) {
            item { TronPartialNotice(meta, failures, repository::refresh) }
        }
        // ---- 身分卡 --------------------------------------------------------
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dims.cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TronAvatar(me.ownerGeneration, me.profile.userNo, me.profile.name, me.profile.avatarUrl)
                    Spacer(Modifier.height(Dims.loose))
                    Text(
                        text = me.profile.name,
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    if (me.profile.identityLine.isNotBlank()) {
                        Spacer(Modifier.height(Dims.tight))
                        Text(
                            text = me.profile.identityLine,
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        // ---- 統計 ----------------------------------------------------------
        item { TronSection(text = "概览", icon = MarkIcon.Growth) }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Dims.cardPadding)) {
                    Text(
                        text = "待交作业 ${tronStatsCountText(me.stats.todoCount)}　" +
                            "我的课 ${tronStatsCountText(me.stats.courseCount)}　" +
                            "进行中签到 ${tronStatsCountText(me.stats.openRollcallCount)}",
                        style = MiuixTheme.textStyles.body1,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(Dims.tight))
                    HintText("“—”表示还没取到，不是 0。")
                }
            }
        }

        // ---- 學籍信息 ------------------------------------------------------
        item { TronSection(text = "学籍", icon = MarkIcon.Account) }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Dims.cardPadding)) {
                    if (me.profile.orgName.isNotBlank()) {
                        KeyValueRow("学校", me.profile.orgName)
                    }
                    if (me.profile.department.isNotBlank()) {
                        KeyValueRow("院系", me.profile.department)
                    }
                    if (me.profile.grade.isNotBlank()) {
                        KeyValueRow("年级", me.profile.grade)
                    }
                    if (me.profile.klass.isNotBlank()) {
                        KeyValueRow("班级", me.profile.klass)
                    }
                    KeyValueRow("身分", me.profile.roleLabel)
                }
            }
        }

        // ---- 入口 ----------------------------------------------------------
        if (onOpenRollcall != null) {
            item { TronSection(text = "常用", icon = MarkIcon.App) }
            item {
                ActionRow(
                    title = "签到",
                    subtitle = "查看进行中的签到并签到",
                    action = "查看与签到",
                    onClick = onOpenRollcall,
                )
            }
        }

        if (onFallbackToWeb != null) {
            item {
                ActionRow(
                    title = "更多功能",
                    subtitle = "讨论、测验、成绩、通知设置等",
                    action = "改用网页版",
                    onClick = onFallbackToWeb,
                )
            }
        }

        item {
            HintText(
                "说明：这里只显示展示需要的资料（姓名、学号、院系年级班级）。" +
                    "手机号、邮箱等个人资料不会被读取。",
            )
        }
    }
}

/** 數量文字：null → 「—」（**不是 0**）。 */
internal fun tronStatsCountText(v: Int?): String = v?.toString() ?: "—"
