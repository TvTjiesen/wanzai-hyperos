package com.muyao.wanzaihyperos.dev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.data.tronclass.ScheduleSource
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleParser
import com.muyao.wanzaihyperos.data.tronclass.TronScheduleSlot
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.WanzaiTheme
import com.muyao.wanzaihyperos.ui.design.AppCard
import com.muyao.wanzaihyperos.ui.design.Dims
import com.muyao.wanzaihyperos.ui.main.pages.fullBleedHorizontal
import com.muyao.wanzaihyperos.ui.main.pages.SlotZoomOverlay
import com.muyao.wanzaihyperos.ui.main.pages.WeekBoard
import com.muyao.wanzaihyperos.data.schedule.WeekLayout

/**
 * **僅測試**：課表排版的離線預覽。
 *
 * 時間段落用的是他自己 30 門課裡**真實出現過的寫法**
 *（`docs/B13-TRONCLASS-SCHEDULE.txt` 那 25 種），
 * 課程名與老師/教室是**佔位文字** —— 這頁不接網絡、不接憑據，
 * 唯一用途是讓新版排版（五天全顯示、單層、只顯示本週）能被看見。
 */
class BoardPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 節次時間要讀進來看得到：`clockLine()` 讀的是 `UiPrefs.periodTimes`，
        // 不 load 就永遠是空列表，這一頁就验不到「课表每块课上写着几点到几点」那条路径。
        com.muyao.wanzaihyperos.data.UiPrefs.load(this)
        val week = (intent.getStringExtra("week")?.toIntOrNull() ?: 8)
        setContent {
            WanzaiTheme(themeMode = AppThemeMode.System) {
                PreviewScreen(week = week)
            }
        }
    }

    private companion object {
        /** (佔位課名, 真實時間寫法) —— 時間逐字取自 B13 取證。 */
        val FIXTURE = listOf(
            "线性代数" to "周1第1-2节第1,17周",
            "大学英语" to "周1第3-4节第1,5周;周1第3-4节第7,9周;周1第3-4节第11,17周",
            "计算机网络" to "周2第3-4节第1,3周;周2第3-4节第5,17周",
            "体育" to "周2第1-2节第1,9周;周2第1-2节第11,17周",
            "毛概" to "周2第8-9节第10,16周;周2第8-9节第1,3周;周2第8-9节第5,9周;周2第8-9节第17,17周",
            "数据结构" to "周3第6-7节第1,16周",
            "操作系统" to "周3第8-9节第1,1周;周3第8-9节第5,5周;周3第8-9节第8,9周",
            "大学物理" to "周4第1-4节第1,2周;周4第1-4节第4,17周",
            "软件工程" to "周4第6-7节第1,2周;周4第6-7节第4,17周",
            "算法设计" to "周4第10-11节第12,17周;周4第10-11节第1,2周;周4第10-11节第4,6周",
            "数据库原理" to "周5第1-2节第1,1周;周5第1-2节第4,6周;周5第1-2节第8,15周;周5第1-2节第17,17周",
            "编译原理" to "周5第3-4节第1,8周",
            "撞课演示(占位)" to "周3第6-7节第1,16周",
            "形势与政策" to "周6第1-2节第4,4周",
            "创新创业" to "周7第1-2节第1,1周",
        )

        val TEACHERS = listOf("王波", "李雷", "张敏", "陈立", "刘一", "赵二")

        /**
         * **最坏情况**（`--es week 99` 才渲染）：课名/老师/教室都取"这所学校真可能出现的最长那种"。
         *
         * 为什么要有它（#11 的验收缺口）：格子是 `maxLines = 1 + Ellipsis`，
         * 而字号从 8.5/10 抬到了 11/13 —— 这个工程踩过的坑是
         * **「读屏读得全 ≠ 像素画得出来」**（窄容器 + maxLines=1 会无声裁掉折行的第二行）。
         * 原来的 FIXTURE 里最长的课名只有 6 个字、老师全是 2 个字，**顶不到窄格子的边**，
         * 拿它验收等于没验。这一组只服务像素验收，不进 release。
         */
        val WORST_CASE = listOf(
            Triple(
                "毛泽东思想和中国特色社会主义理论体系概论",  // 21 字，真实存在的通识课名
                "阿依古丽·买买提",                          // 带隔点的长姓名
                "理科四号楼 214（东区）",                    // 带括号与空格的长教室
            ),
        )
    }

    @androidx.compose.runtime.Composable
    private fun PreviewScreen(week: Int) {
        val slots = buildList {
            if (week >= 98) {
                // 最坏情况：只放一块，位置固定在周一 1-2 节 —— 像素验收时一眼就能找到它。
                // ⚠️ 周次必须**含请求的那一周**：以前写死「第1,16周」，`--es week 99` 时
                //   `WeekLayout.forWeek` 判定这块不在本周 ⇒ 板上空空的，看起来像"渲染失败"。
                // `week 98` = 同一组最坏字符串，但占 **1-3 节**（rows=3）⇒ 老师那一行会真的画出来
                //   （`TimetableSectionPage.kt:1126` 的条件是 `rows >= 3`），这样才能验到老师行。
                val span = if (week >= 98 && week < 99) "1-3" else "1-2"
                WORST_CASE.forEach { (name, teacher, room) ->
                    TronScheduleParser.parse(
                        "周1第$span 节第$week,$week 周".replace(" ", ""),
                        courseId = 900,
                        courseName = name,
                    )
                        .slots.forEach { s ->
                            add(
                                s.copy(
                                    teacher = teacher,
                                    room = room,
                                    source = ScheduleSource.JIAOWU,
                                ),
                            )
                        }
                }
                return@buildList
            }
            FIXTURE.forEachIndexed { i, (name, text) ->
                TronScheduleParser.parse(text, courseId = 100 + i, courseName = name)
                    .slots.forEachIndexed { k, s ->
                        // 老師/教室是佔位：教務那份來源才有這兩個欄位，
                        // 這裡放進去只是為了看「有它們時版面会不会撑坏」。
                        add(
                            s.copy(
                                teacher = TEACHERS[(i + k) % TEACHERS.size],
                                room = "理${(i % 4) + 1}-${200 + i * 3}",
                                source = ScheduleSource.JIAOWU,
                            ),
                        )
                    }
            }
        }
        val thisWeek = WeekLayout.forWeek(slots, week)
        val columns = WeekLayout.columns(thisWeek)

        // 2026-10-28 业主要看「点就地放大」的动画：预览页也要能点，
        // 否则动画只能等真页面有课表数据才验得到。
        var picked by remember { mutableStateOf<TronScheduleSlot?>(null) }
        var pickedAnchor by remember { mutableStateOf<Rect?>(null) }
        var zoomClosing by remember { mutableStateOf(false) }
        var pageOrigin by remember { mutableStateOf(Offset.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 2026-10-28 深色档取证：原来写死浅色纸底，切深色后预览页仍是白纸 + 深块，
                // 看着像「深色档把块变黑了」。改成跟真页面同一个主题底色，预览才算数。
                .background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background)
                .onGloballyPositioned { pageOrigin = it.boundsInRoot().topLeft },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Dims.screenH),
            ) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = "排版预览 · 第 $week 周 · 课程名/老师/教室为占位文字（时间是真机取证的真实写法）",
                    fontSize = com.muyao.wanzaihyperos.ui.design.Type.caption,   // 走令牌（#11：debug 界面也算在规则内）
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB4552E),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                // 2026-10-28 业主「把课表的那个背景去掉」：预览页也不再套 AppCard，
                // 否则预览看到的和真页面不是同一个东西。
                WeekBoard(
                    // 与真页面同一条出血：预览页也要吃到屏幕边缘，否则预览看到的宽度不是真的。
                    modifier = Modifier.fullBleedHorizontal(5.dp, Dims.screenH - 5.dp),
                    columns = columns,
                    today = java.time.LocalDate.now().dayOfWeek.value,
                    week = week,
                    courseCount = WeekLayout.courseCount(thisWeek),
                    source = ScheduleSource.JIAOWU,
                    onPick = { s, r -> picked = s; pickedAnchor = r },
                )
            }
            val zoom = picked
            if (zoom != null) {
                SlotZoomOverlay(
                    slot = zoom,
                    anchor = pickedAnchor,
                    pageOrigin = pageOrigin,
                    closing = zoomClosing,
                    onClose = { zoomClosing = true },
                    onClosed = { picked = null; pickedAnchor = null; zoomClosing = false },
                )
            }
            Box(modifier = Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                // 「仅测试」水印：防我哪天把这张图当成真机截图交给他。
                top.yukonga.miuix.kmp.basic.Text(
                    text = "DEBUG ONLY",
                    fontSize = com.muyao.wanzaihyperos.ui.design.Type.micro,     // 9sp 低于 10sp 地板，第 9 轮审计点名
                    color = Color(0x55000000),
                )
            }
        }
    }
}
