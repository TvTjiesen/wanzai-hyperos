package com.muyao.wanzaihyperos.ui.cjh52

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.muyao.wanzaihyperos.ui.design.AppList
import com.muyao.wanzaihyperos.ui.design.HintText
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import com.muyao.wanzaihyperos.ui.design.SecondaryScaffold
import com.muyao.wanzaihyperos.ui.design.rememberScrollProgress

/**
 * **CJH52 學生服務**（服务目录页）。
 *
 * ### 为什么需要这一页
 *
 * 稿 `cjh.png` 上它是六项服务的**总目录**：我的课程 / 我的班级 / 日常通知 /
 * 我的考试 / 我的成长 / 爱讲座，每行一句「原生摘要 + 完整 Web」+ `›`。
 *
 * 在此之前这六页在 App 里**各有各的入口、彼此不知道对方存在**：
 * 只能从门户那些应用格子里一条条点进去，一旦想「换一页看看」就得退回应用页重找。
 * 这页就是它们之间的那座桥 —— 也是各页 `六项服务目录` 那枚芯片的落点。
 *
 * ### 六行的顺序照稿不动
 *
 * 顺序是稿定的（课程 → 班级 → 通知 → 考试 → 成长 → 讲座），不按字典序、
 * 也不按我们实现的先后 —— 使用者在这页找的是**他记得的那个位置**。
 *
 * ### 为什么六行是 [CjhRowItem] 而不是 `SettingRow`
 *
 * 稿上这六行是「16dp 线稿图标 + 标题 + 小字 + `›`」，行间只有 1px 分割线；
 * `SettingRow` 是设置页的彩色磁贴款（28dp 圆角色块打底），跟稿不是同一种东西。
 * 图标统一用 [MarkIcon.App]：它的图形就是稿上那枚 2×2 四点方阵。
 *
 * @param onOpenCourses 我的课程（[com.muyao.wanzaihyperos.ui.courses.CoursesPage]）。
 * @param onOpenClazz 我的班级（`CLAZZ`）。
 * @param onOpenNotices 日常通知（`NOTICE`）。
 * @param onOpenExams 我的考试（`EXAM`）。
 * @param onOpenGrowth 我的成长（`GROWTH`）。
 * @param onOpenLectures 爱讲座（`LECTURE`）。
 */
@Composable
fun CjhHomePage(
    onBack: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenClazz: () -> Unit,
    onOpenNotices: () -> Unit,
    onOpenExams: () -> Unit,
    onOpenGrowth: () -> Unit,
    onOpenLectures: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val veilProgress by rememberScrollProgress(listState)

    SecondaryScaffold(
        scrollProgress = veilProgress,
        title = "CJH52 学生服务",
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            AppList(modifier = Modifier.fillMaxSize(), state = listState) {
                item(key = "cjh-home-hero") {
                    CjhHero(
                        eyebrow = "CJH52 / SERVICES",
                        title = "学生服务",
                        sub = "六个原生阅读页，完整操作保留原站出口。",
                    )
                }
                item(key = "cjh-home-courses") {
                    CjhRowItem(
                        title = "我的课程",
                        icon = MarkIcon.App,
                        meta = "原生摘要 + 完整 Web",
                        arrow = true,
                        onClick = onOpenCourses,
                    )
                }
                item(key = "cjh-home-clazz") {
                    CjhRowItem(
                        title = "我的班级",
                        icon = MarkIcon.App,
                        meta = "原生摘要 + 完整 Web",
                        arrow = true,
                        onClick = onOpenClazz,
                    )
                }
                item(key = "cjh-home-notices") {
                    CjhRowItem(
                        title = "日常通知",
                        icon = MarkIcon.App,
                        meta = "原生摘要 + 完整 Web",
                        arrow = true,
                        onClick = onOpenNotices,
                    )
                }
                item(key = "cjh-home-exams") {
                    CjhRowItem(
                        title = "我的考试",
                        icon = MarkIcon.App,
                        meta = "原生摘要 + 完整 Web",
                        arrow = true,
                        onClick = onOpenExams,
                    )
                }
                item(key = "cjh-home-growth") {
                    CjhRowItem(
                        title = "我的成长",
                        icon = MarkIcon.App,
                        meta = "原生摘要 + 完整 Web",
                        arrow = true,
                        onClick = onOpenGrowth,
                    )
                }
                item(key = "cjh-home-lectures") {
                    CjhRowItem(
                        title = "爱讲座",
                        icon = MarkIcon.App,
                        meta = "原生摘要 + 完整 Web",
                        arrow = true,
                        onClick = onOpenLectures,
                    )
                }
                item(key = "cjh-home-hint") {
                    HintText(
                        "说明：六页都只做「读」；报名、提交、修改这类写操作留在原站，" +
                            "各页底部的「完整网页版」可直接过去。",
                    )
                }
            }
        }
    }
}