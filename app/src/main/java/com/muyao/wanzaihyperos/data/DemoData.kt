package com.muyao.wanzaihyperos.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Store

/**
 * ⚠️ **本文件全部是「展示用示例数据」（demo data），不是真实接口数据。**
 *
 * A1 阶段只做 UI 骨架，**不接学校任何接口**（网络层属于 A2/A3）。
 * 这里的数据有两个来源，都不冒充实时数据：
 *
 * 1. **应用目录**：条目 id / 名称 / 域名 取自 Flutter 版逆向文档
 *    `docs/FEATURE_MAP.md`（2026-10-08 门户实测），属历史快照；
 *    **分组是我们按后端子系统整理的展示用分组，不是门户服务端下发的分组名**。
 * 2. **新闻 / 消息 / 待办计数**：纯构造的示例，**不代表学校当前内容**。
 *
 * 界面必须在显眼处标注这些数据是展示用（见 `DemoBanner`），
 * 服务端真实数据接进来后（A2/A3）整体替换本文件的实现，函数签名保持不变。
 */

/** 应用落在哪个后端子系统。分组展示与将来的登录态共用策略都按它区分。 */
enum class PortalSystem(val label: String, val defaultTint: Color) {
    /** 一站式学工平台（最大子系统，单一 SPA、路径路由）。 */
    CJH52("场景管理", Color(0xFF108EE9)),

    /** 传统 JSP 审批表单（名字叫 dingding，实为学校自建页面）。 */
    JSP("办事审批", Color(0xFF6B7A99)),

    /** 各自独立域名的系统（一卡通 / 图书馆 / 公寓 …）。 */
    STANDALONE("独立系统", Color(0xFF12A594)),

    /** 地址是字面内网 IP，校外必然打不开 —— 界面要优雅降级，不能崩。 */
    CAMPUS_ONLY("仅校园网", Color(0xFF8A6FD1)),
}

/**
 * 一条应用入口。
 *
 * @param id 门户下发的应用 id（来自 FEATURE_MAP 历史实测）。
 * @param name 应用名（门户下发的就是「学在城院」这种口语名，不美化）。
 * @param url 应用地址原样保留（将来要拼 tempcode，A3 才做）。
 * @param icon 本地推断的图标 —— **不下载门户的 `app_img`**：
 *   87 张外链图 = 87 次请求，直接踩 QPS≤1 红线（见 docs/FEATURE_MAP.md §四.4）。
 * @param tint 图标底色。门户给了 `app_color` 就用，缺省按子系统给一个。
 */
data class PortalApp(
    val id: Int,
    val name: String,
    val url: String,
    val system: PortalSystem,
    val subtitle: String? = null,
    val icon: ImageVector = MiuixIcons.GridView,
    val tint: Color? = null,
) {
    val effectiveTint: Color get() = tint ?: system.defaultTint

    /** 这条入口是不是「字面内网地址」—— 只有这种才必然校外打不开。 */
    val needsCampusNetwork: Boolean get() = system == PortalSystem.CAMPUS_ONLY
}

/** 应用页的一个分组。 */
data class AppGroup(val title: String, val apps: List<PortalApp>)

/** 新闻条目（展示用）。 */
data class DemoNews(
    val id: Int,
    val title: String,
    val group: String,
    val publishedAt: String,
)

/** 消息条目（展示用）。[unread] 决定是否画未读点。 */
data class DemoMessage(
    val id: String,
    val title: String,
    val summary: String,
    val group: String,
    val updatedAt: String,
    val unread: Boolean,
)

/** 消息分组 + 它的未读数（展示用）。 */
data class DemoMessageGroup(val name: String, val unread: Int)

/** 待办三计数。真实取不到时界面显示「—」而**不是 0**（见 [DemoCatalog.todoExplanation]）。 */
data class TodoCount(
    val pending: Int,
    val applied: Int,
    val finished: Int,
)

/** 图标推断：按应用名关键词命中。命中不了退回网格图标 —— **不猜**，也不加占位框。 */
internal fun iconFor(name: String): ImageVector {
    val rules: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("课程", "课表", "教室", "课堂", "班级", "学习", "教学", "实验室", "双创") to MiuixIcons.GridView,
        listOf("考试", "成绩", "综测", "素质", "鉴定", "测评", "问卷", "成长", "发展") to MiuixIcons.File,
        listOf("图书馆", "借阅", "座位", "自习", "考研") to MiuixIcons.Album,
        listOf("一卡通", "缴费", "报销", "贷款", "账单", "水费") to MiuixIcons.BankCards,
        listOf("公寓", "宿舍", "寝室", "住宿", "后勤", "慧生活") to MiuixIcons.Home,
        listOf("心理") to MiuixIcons.Favorites,
        listOf("讲座", "宣传", "新闻", "通知", "公告", "广播") to MiuixIcons.Mic,
        listOf("活动", "二课堂", "劳动", "社团", "迎新") to MiuixIcons.Promotions,
        listOf("报修", "随手拍", "维修") to MiuixIcons.Create,
        listOf("请假", "离校", "准假", "手续", "申请", "审批", "办理", "证明") to MiuixIcons.Edit,
        listOf("电话", "联系") to MiuixIcons.Phone,
        listOf("公交", "班车", "停车", "车辆", "场馆", "功能房", "预约", "借用") to MiuixIcons.Alarm,
        listOf("就业", "招聘", "生涯", "规划", "实习") to MiuixIcons.Store,
        listOf("失物", "招领", "教材", "档案") to MiuixIcons.Folder,
        listOf("安全", "预警", "密码") to MiuixIcons.Lock,
        listOf("一码通", "二维码", "扫码") to MiuixIcons.Scan,
        listOf("信息", "个人", "服务大厅", "一站式") to MiuixIcons.Layers,
        listOf("课程") to MiuixIcons.Contacts,
    )
    for ((keywords, icon) in rules) {
        for (keyword in keywords) if (name.contains(keyword)) return icon
    }
    return MiuixIcons.GridView
}

// --- 构造辅助（把 URL 前缀/子系统/图标收在一处，条目本身只写 id+名字+路径）-----------

private fun cjh(id: Int, name: String, path: String, subtitle: String? = null, tint: Color? = null) =
    PortalApp(id, name, "https://cjh52.hzcu.edu.cn$path", PortalSystem.CJH52, subtitle, iconFor(name), tint)

private fun jsp(id: Int, name: String, path: String, subtitle: String? = null) =
    PortalApp(id, name, "https://dingding.hzcu.edu.cn$path", PortalSystem.JSP, subtitle, iconFor(name))

private fun solo(id: Int, name: String, url: String, subtitle: String? = null, tint: Color? = null) =
    PortalApp(id, name, url, PortalSystem.STANDALONE, subtitle, iconFor(name), tint)

private fun campus(id: Int, name: String, url: String) =
    PortalApp(id, name, url, PortalSystem.CAMPUS_ONLY, "仅校园网可达", iconFor(name))

/**
 * 展示用目录。
 *
 * 分组是「按后端子系统整理的展示用分组」，**不是门户服务端的分组名**；
 * 条目本身来自 FEATURE_MAP 历史实测清单。
 */
object DemoCatalog {

    // ---- 分组 1：学工与课程（cjh52 高频项）------------------------------------------
    private val study: List<PortalApp> = listOf(
        cjh(60, "我的课程", "/course/student", "本学期课程"),
        cjh(153, "查课表", "/course/search", "按周次查询"),
        cjh(211, "我的考试", "/examination", "考试安排"),
        cjh(104, "我的班级", "/clazz/student"),
        cjh(106, "我的成长", "/development/home", "学生发展"),
        cjh(185, "个人发展导航", "/studyguide"),
        cjh(191, "我的信息卡", "/userInfo"),
        cjh(224, "爱规划", "/careerPlan/student/entrance", "城院足迹"),
        cjh(264, "爱考研", "/studyRoom", "自习室"),
        cjh(267, "劳动教育", "/laborManage/stu"),
        cjh(333, "功能房", "/functionRoomReservation", "场地预约"),
    )

    // ---- 分组 2：生活服务 ------------------------------------------------------------
    private val life: List<PortalApp> = listOf(
        solo(55, "一卡通", "http://etcard.hzcu.edu.cn:8088/easytong_app/LoginH5DK?jumpMode=homeIndex", "余额 · 流水", Color(0xFFF5A623)),
        solo(116, "图书馆", "https://libres.hzcu.edu.cn/zucc/mylibrary", "借阅 · 座位", Color(0xFF7B61FF)),
        solo(160, "智慧公寓", "http://zhgy.hzcu.edu.cn/mob/zuccding/auth", "宿舍", Color(0xFF0491FF)),
        solo(316, "智慧后勤", "https://zhhq.hzcu.edu.cn/saas_wap/"),
        solo(274, "慧生活", "https://buyersh.hzcu.edu.cn/pages/org/door"),
        solo(253, "停车早知道", "https://safety.hzcu.edu.cn:8087/h5/"),
        solo(265, "智慧心理", "https://xlzxsd.hzcu.edu.cn/", "心理咨询", Color(0xFFE06AA8)),
        solo(261, "一码通", "http://iapp.hzcu.edu.cn/qr-code", "校园码", Color(0xFF00B2A9)),
        cjh(128, "随手拍", "/repair/repairList", "报修"),
        cjh(183, "我的问卷", "/questionnaire"),
        cjh(233, "辅导员满意度调查", "/instructorSatisfactionSurvey"),
    )

    // ---- 分组 3：消息与通知 ----------------------------------------------------------
    private val notice: List<PortalApp> = listOf(
        cjh(188, "日常通知", "/notice"),
        cjh(315, "我的预警", "/myWarnInfo", "学业预警"),
        cjh(48, "意见直通车", "/suggestion"),
        cjh(328, "安全教育", "/oneStopCommunity/studentSafetyEducation"),
        solo(114, "满意度评价", "https://myd.hzcu.edu.cn"),
        solo(262, "新生报到", "https://freshman.hzcu.edu.cn"),
    )

    // ---- 分组 4：活动与讲座 ----------------------------------------------------------
    private val activity: List<PortalApp> = listOf(
        cjh(51, "爱活动", "/activity/activityList", "校园活动", Color(0xFFFF6B6B)),
        cjh(54, "爱讲座", "/lecture/lectureList", "讲座预告", Color(0xFF00A3C4)),
        cjh(130, "爱劳动", "/labour/labourList"),
        cjh(250, "公寓宣传活动申请", "/studentAffair/application"),
        solo(195, "数智二课堂", "https://ggkt.hzcu.edu.cn/", "行走思政"),
        solo(157, "智云课堂", "https://livemobileapp.hzcu.edu.cn/#/", "直播 · 录播"),
    )

    // ---- 分组 5：办事审批（JSP）-----------------------------------------------------
    private val approval: List<PortalApp> = listOf(
        jsp(49, "学生课程请假", "/default/dingding/leaveSchool/leave_school_apply_dingding.jsp"),
        jsp(90, "本科生电子离校", "/default/dingding/leaveSchool/leave_school_apply_dingding.jsp"),
        jsp(88, "本科生中途离校手续申请", "/default/dingding/unstudent_leave/unstudent_apply_dingding.jsp"),
        jsp(122, "本科生困难学生学杂费缓缴申请", "/default/dingding/difficulty_pay_delay/apply_dingding.jsp"),
        jsp(87, "本科生、研究生校外住宿办理", "/default/dingding/out_school_live/out_school_live_apply_dingding.jsp"),
        jsp(138, "学生公寓大功率电器使用申请", "/default/dingding/big_electric/apply_dingding.jsp"),
        jsp(151, "本科生文明寝室申请", "/default/dingding/studentCultureRoom/student_culture_room_apply_dingding"),
        jsp(141, "本科生免检寝室申请", "/default/dingding/studentDefectFree/student_defect_free_apply_dingding.jsp"),
        jsp(142, "本科生学习型寝室申请", "/default/dingding/studentStudy/student_studing_apply_dingding.jsp"),
        jsp(61, "本科生出国准假信", "/default/dingding/BKstudentleave/studentleave_apply_dingding.jsp"),
        jsp(139, "心灵使者申请", "/default/dingding/heart_messenger/apply_dingding.jsp"),
        jsp(66, "体育场馆租借申请", "/default/dingding/sport_hall/sport_hall_apply_dingding.jsp"),
        jsp(72, "城院公交预约", "/default/dingding/bus_reservation/bus_reservation_list.jsp"),
        jsp(272, "宣传品公共宣传阵地设置申请", "/default/dingding/public_window/public_window_apply_dingding.jsp"),
        jsp(100, "院内宣传阵地使用申报与审批", "/default/dingding/public_window/public_window_apply_dingding.jsp"),
        jsp(92, "Blackboard 密码找回", "/default/dingding/BbEmail/BbEmail_apply_dingding.jsp"),
        jsp(297, "超算中心服务平台", "/default/dingding/supperCenterIndex.jsp"),
        jsp(56, "常用电话", "/default/dingding/phoneList.jsp"),
    )

    // ---- 分组 6：就业与成长 ----------------------------------------------------------
    private val career: List<PortalApp> = listOf(
        solo(162, "就业服务", "https://career.hzcu.edu.cn/"),
        cjh(280, "高质量就业基地", "/highQualityEmployment/stuHome"),
        cjh(330, "优秀毕业生申请", "/outstandingGraduate"),
        cjh(204, "勤工助学", "/workstudy/student/recruit"),
        cjh(227, "创新创业情况", "/innovationForZUCC"),
        cjh(209, "综合素质测评", "/comprehensiveEvaluation"),
        cjh(259, "综测鉴定表备案", "/comprehensiveEvaluation/appraisal"),
        cjh(105, "素质申报", "/collect/list", null, Color(0xFFE8A317)),
        cjh(155, "学生干部", "/studentCadre/StudentPostList"),
        cjh(230, "特色班报名", "/characteristicClass"),
        cjh(258, "助学贷款回执填写", "/stuLoanReceipt"),
        cjh(335, "博士站咨询预约", "/phd-consult/student/bookList"),
        cjh(332, "一站式线上服务大厅", "/oneStopCommunity/home"),
        solo(300, "双创竞赛", "https://ca.hzcu.edu.cn/cas/login?service=https://scsystem.hzcu.edu.cn:83/auth/private-auth-login"),
        solo(214, "教材缴费", "https://ijw.hzcu.edu.cn/sso/ddszLogin?url=jcjsgl%2FjcjfMobile"),
        solo(248, "党员证明", "https://report.hzcu.edu.cn/pluto/index4.jsp"),
        solo(273, "团员证明", "https://report.hzcu.edu.cn/pluto/index4.jsp"),
        solo(240, "买书即报销", "https://hzcubuy.icaifang.cn/mobile/hzcuddpage"),
        solo(172, "公共场馆借用", "https://eoa.hzcu.edu.cn:9000/wui/cas-entrance.jsp"),
        jsp(169, "教职工固定车辆申请", "/default/dingding/phoneList.jsp", "旧域名"),
    )

    // ---- 分组 7：仅校园网内可开 ------------------------------------------------------
    private val campusOnly: List<PortalApp> = listOf(
        campus(271, "研究生迎新驾驶舱", "https://sjfx-hzcu-edu-cn-s.webvpn1.hzcu.edu.cn:8118/goto/88867d22c3a2821f7fe1d879a07a5e70"),
        campus(266, "本科生迎新驾驶舱", "https://sjfx-hzcu-edu-cn-s.webvpn1.hzcu.edu.cn:8118/goto/192d7e2e"),
        campus(174, "实验室综合管理平台", "https://lab-hzcu-edu-cn.webvpn1.hzcu.edu.cn:8118/admin/caslogin?loginType=mobile"),
        campus(317, "医学实验室预约", "http://10.61.132.121/thirdlogin.html#/index?typeCode=22"),
        solo(156, "学在城院", "https://mobile.hzcu.edu.cn/", "数智一课堂"),
    )

    /** 7 个展示用分组。 */
    val groups: List<AppGroup> = listOf(
        AppGroup("学工与课程", study),
        AppGroup("生活服务", life),
        AppGroup("消息与通知", notice),
        AppGroup("活动与讲座", activity),
        AppGroup("办事审批", approval),
        AppGroup("就业与成长", career),
        AppGroup("仅校园网内可开", campusOnly),
    )

    /** 全部条目（分组内可能有重复 id，这里按 id 去重，与门户「跨分组有重复」的实测一致）。 */
    val all: List<PortalApp> = groups.flatMap { it.apps }.distinctBy { it.id }

    /** 首页宫格：门户首页是「常用 14 个 + 更多」。 */
    val frequent: List<PortalApp> = listOf(
        all.first { it.id == 156 }, // 学在城院
        all.first { it.id == 60 },  // 我的课程
        all.first { it.id == 153 }, // 查课表
        all.first { it.id == 211 }, // 我的考试
        all.first { it.id == 55 },  // 一卡通
        all.first { it.id == 116 }, // 图书馆
        all.first { it.id == 261 }, // 一码通
        all.first { it.id == 106 }, // 我的成长
        all.first { it.id == 188 }, // 日常通知
        all.first { it.id == 51 },  // 爱活动
        all.first { it.id == 54 },  // 爱讲座
        all.first { it.id == 49 },  // 学生课程请假
        all.first { it.id == 105 }, // 素质申报
        all.first { it.id == 160 }, // 智慧公寓
    )

    /** 新闻分组名 —— 这 5 个是门户 `listnewsgroup` 的实测分组名。 */
    val newsGroups: List<String> = listOf("教育教学", "科学研究", "校务管理", "会议通知", "其它通知")

    /** 展示用新闻条目（构造，不代表学校当前内容）。 */
    val news: List<DemoNews> = listOf(
        DemoNews(1, "关于 2026-2027 学年第一学期选课工作的通知", "教育教学", "10-10 09:20"),
        DemoNews(2, "关于开展本学期期中教学检查的安排", "教育教学", "10-09 16:45"),
        DemoNews(3, "关于图书馆国庆假期开放时间调整的说明", "校务管理", "10-08 14:10"),
        DemoNews(4, "某实验室在人工智能方向取得阶段性进展", "科学研究", "10-08 10:02"),
        DemoNews(5, "关于召开本学期第二次教学工作例会的通知", "会议通知", "10-07 17:30"),
        DemoNews(6, "关于校园一卡通系统维护的温馨提示", "其它通知", "10-07 11:05"),
    )

    /** 展示用消息分组 + 未读数（构造）。 */
    val messageGroups: List<DemoMessageGroup> = listOf(
        DemoMessageGroup("系统通知", 2),
        DemoMessageGroup("办事审批", 1),
        DemoMessageGroup("活动通知", 3),
        DemoMessageGroup("讲座通知", 0),
        DemoMessageGroup("学业预警", 1),
        DemoMessageGroup("图书馆", 0),
        DemoMessageGroup("一卡通", 0),
        DemoMessageGroup("宿管通知", 0),
        DemoMessageGroup("其他", 0),
    )

    /** 展示用消息条目（构造）。 */
    val messages: List<DemoMessage> = listOf(
        DemoMessage("m1", "本学期选课结果已发布", "请到“我的课程”查看选课结果，如有疑问请联系教务处。", "系统通知", "10-10 09:25", true),
        DemoMessage("m2", "你的请假申请已通过", "学生课程请假申请已由辅导员审批通过。", "办事审批", "10-10 08:40", true),
        DemoMessage("m3", "“秋日校园跑”活动开始报名", "活动时间 10 月 18 日，名额有限，先到先得。", "活动通知", "10-09 19:10", true),
        DemoMessage("m4", "讲座预告：从大模型到智能体", "10 月 15 日 14:00，图书馆报告厅。", "讲座通知", "10-09 15:00", false),
        DemoMessage("m5", "学业预警提醒", "你有一门课程平时成绩低于预警线，请及时关注。", "学业预警", "10-09 10:30", true),
        DemoMessage("m6", "借阅图书即将到期", "《数据结构与算法分析》将于 10 月 14 日到期。", "图书馆", "10-08 20:15", false),
        DemoMessage("m7", "一卡通消费提醒", "今日食堂消费 12.50 元，余额 86.30 元。", "一卡通", "10-08 12:36", false),
        DemoMessage("m8", "宿舍安全检查通知", "本周三下午将进行宿舍安全例行检查。", "宿管通知", "10-07 18:00", false),
        DemoMessage("m9", "系统维护完成", "门户系统维护已完成，服务已恢复。", "系统通知", "10-07 07:30", true),
        DemoMessage("m10", "“程序设计竞赛”报名开始", "校赛报名截止 10 月 20 日。", "活动通知", "10-06 16:20", true),
        DemoMessage("m11", "第二课堂学分认定结果", "上一学年第二课堂学分认定结果已公示。", "活动通知", "10-06 09:05", true),
        DemoMessage("m12", "关于校园网认证方式调整的说明", "10 月 12 日起启用新的认证入口。", "系统通知", "10-05 14:00", false),
    )

    /** 待办三计数（展示用构造值）。真实取不到时界面显示「—」。 */
    val todoCount = TodoCount(pending = 1, applied = 1, finished = 1)

    /** 待办卡片底部说明。措辞必须说清「这个数字从哪来、有多新」。 */
    const val todoExplanation: String = "展示用示例条数 · 未接入门户待办接口"

    /** 未读数展示值与新鲜度文案。真实未读数有 15 分钟硬下限，所以「多久前取的」是必要信息。 */
    val unreadTotal: Int = messageGroups.sumOf { it.unread }

    /** 数据新鲜度文案（展示用，模拟「数据过期」状态）。 */
    const val freshnessLabel: String = "12 分钟前更新"

    /** 展示用搜索：按名字/副标题过滤。 */
    fun search(query: String): List<PortalApp> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return all.filter { it.name.contains(q, ignoreCase = true) || (it.subtitle?.contains(q, ignoreCase = true) == true) }
    }
}
