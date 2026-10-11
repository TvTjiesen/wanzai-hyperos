package com.muyao.wanzaihyperos.data.portal

import androidx.compose.ui.graphics.Color
import com.muyao.wanzaihyperos.data.AppGroup
import com.muyao.wanzaihyperos.data.DemoMessage
import com.muyao.wanzaihyperos.data.PortalApp
import com.muyao.wanzaihyperos.data.PortalSystem
import com.muyao.wanzaihyperos.data.iconFor

/**
 * 把门户下发的**真实条目**映射成界面用的展示模型。
 *
 * ### 为什么要分两层
 * 服务端给的是「有什么」（`app_name` / `app_url` / `app_color` / `app_img`），
 * 界面要的是「怎么画」（用哪个内置图标、底色是什么、算不算仅校园网）。
 * 后者是**界面决定**，不该混进解析器 —— 否则将来换一套图标要动解析代码。
 *
 * ### 三条映射纪律
 * 1. **不下载 `app_img`。** 78 个图标 = 78 次请求，直接违反 QPS≤1 的红线。
 *    图标按名字本地推断（与 A1 展示数据同一套规则），`iconUrl` 留着备用。
 * 2. **`app_color` 解析失败就用子系统默认色**，不硬塞一个颜色。
 * 3. **子系统分类（[inferSystem]）只是「分组展示」的启发式**，
 *    **不是**权威判定 —— 「该走 tempcode 还是普通外链」由
 *    `AppOpenStrategy` 按实测规则决定，两者不要互相引用，免得纠缠在一起。
 */

/** 真实条目 → 界面模型。 */
fun PortalAppEntry.toUiApp(): PortalApp = PortalApp(
    id = id,
    name = name,
    url = url,
    system = inferSystem(url),
    subtitle = introduction?.takeIf { it.isNotBlank() },
    icon = iconFor(name),
    tint = colorArgb?.let { Color(it) },
)

/** 真实分组 → 界面分组。 */
fun PortalAppGroup.toUiGroup(): AppGroup = AppGroup(
    title = name,
    apps = apps.map { it.toUiApp() },
)

fun PortalCatalog.toUiGroups(): List<AppGroup> = groups.map { it.toUiGroup() }

/**
 * 真实消息 → 界面模型。
 *
 * `lastupdatedate` 为空时退回 `createdate`：界面上那一行是「更新时间」，
 * 但更新时间缺失时显示创建时间比显示空白有用。
 */
fun PortalMessageItem.toUiMessage(): DemoMessage = DemoMessage(
    id = id,
    title = title,
    summary = message,
    group = group,
    updatedAt = if (lastUpdateDate.isNotBlank()) lastUpdateDate else createdDate,
    unread = !isRead,
)

/** 字面 IPv4（校内地址）——这种地址校外必然打不开。 */
private val LITERAL_IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

/**
 * 按地址推断「分组展示用」的子系统。
 *
 * | 判定 | 结果 | 依据 |
 * |---|---|---|
 * | host == `cjh52.hzcu.edu.cn` | [PortalSystem.CJH52] | 一站式学工平台，实测域名 |
 * | host == `dingding.hzcu.edu.cn` | [PortalSystem.JSP] | 传统 JSP 审批表单，实测域名 |
 * | 字面内网 IP | [PortalSystem.CAMPUS_ONLY] | 校外不可达 |
 * | host 含 `webvpn` | [PortalSystem.CAMPUS_ONLY] | 校内资源的 VPN 网关，按 A1 的分组惯例归此组 |
 * | 其它 | [PortalSystem.STANDALONE] | 各自独立域名的系统 |
 */
fun inferSystem(url: String): PortalSystem {
    val host = com.muyao.wanzaihyperos.data.AppOpenStrategy.hostOf(url)
        ?: return PortalSystem.STANDALONE
    return when {
        host == "cjh52.hzcu.edu.cn" -> PortalSystem.CJH52
        host == "dingding.hzcu.edu.cn" -> PortalSystem.JSP
        LITERAL_IPV4.matches(host) -> PortalSystem.CAMPUS_ONLY
        host.contains("webvpn") -> PortalSystem.CAMPUS_ONLY
        else -> PortalSystem.STANDALONE
    }
}
