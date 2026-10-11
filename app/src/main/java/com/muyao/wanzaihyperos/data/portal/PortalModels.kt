package com.muyao.wanzaihyperos.data.portal

/**
 * 门户下发的领域模型。**这一层是「服务端原样」的映射**，不含任何展示用的推断。
 *
 * 与 A1 的展示模型（[com.muyao.wanzaihyperos.data.PortalApp]）分开是有意的：
 * - 这里的每个字段都必须能在服务端响应里找到出处（见 `docs/API-PORTAL.md`）；
 * - 展示模型带着「图标用哪一个、底色用哪一个、算不算仅校园网」这类**界面决定**，
 *   那些不该混进数据层，否则将来换图标策略要动解析代码。
 *
 * 两者之间的转换放在 `PortalMappers`，一处可测。
 */

/**
 * 一条应用入口。
 *
 * 字段名对应服务端：`id` / `app_name` / `app_url` / `app_introduction` /
 * `app_color` / `app_img`。
 *
 * ★ `id` + `name` + `url` **三者缺一不可**，缺了整条跳过并计数 ——
 * 不补默认值。理由：没有 `id` 就拼不出 `appid` 参数、没有 `url` 点进去是空白、
 * 没有名字在宫格上是一个无字磁贴。**任何一条都用不了，补默认值只会造出一个假入口。**
 */
data class PortalAppEntry(
    val id: Int,
    val name: String,
    val url: String,
    /** 所属分组名（搜索结果统一打「搜索结果」）。 */
    val groupName: String,
    val introduction: String? = null,
    /** `app_color` 解析出的 ARGB。解析失败为 null，由界面退回子系统默认色。 */
    val colorArgb: Int? = null,
    /**
     * `app_img` 的地址。
     *
     * ⚠️ **不做批量下载**：87 个图标 = 87 次请求，直接违反 QPS≤1 的红线。
     * 这个字段留着是为了将来「只加载可见的那几个」时能用上。
     */
    val iconUrl: String? = null,
) {
    override fun toString(): String = "PortalAppEntry($id, $name)"
}

/** 一个分组。**空分组也要保留**（见 [PortalCatalog.skippedEntries] 的说明）。 */
data class PortalAppGroup(
    val id: Int,
    val name: String,
    val apps: List<PortalAppEntry>,
)

/**
 * 全量目录。
 *
 * @param skippedEntries 因为缺 `id`/`app_name`/`app_url` 而整条跳过的记录数。
 *   **不补默认值，但要让真机上看得到** —— 否则「服务端少发了 3 条」这件事
 *   会静默消失，而「目录按服务端实际资料验收」是 A3 的明文判据。
 */
data class PortalCatalog(
    val groups: List<PortalAppGroup>,
    val allApps: List<PortalAppEntry>,
    val skippedEntries: Int,
) {
    companion object {
        val EMPTY = PortalCatalog(emptyList(), emptyList(), 0)
    }

    fun byId(id: Int): PortalAppEntry? = allApps.firstOrNull { it.id == id }
}

/**
 * 消息分组。
 *
 * 服务端字段：`messagegroup` / `noread` / `isread`。
 * ⚠️ 分组的**标识就是 `messagegroup` 这个名字本身**，不是数字 id。
 */
data class PortalMessageGroup(
    val name: String,
    val unread: Int,
    val isRead: Boolean? = null,
)

/**
 * 一条消息。
 *
 * 服务端字段：`id` / `ticker` / `title` / `message` / `isread` / `openurl` /
 * `userid` / `appid` / `messagegroup` / `createdate` / `lastupdatedate` / `messageid`。
 */
data class PortalMessageItem(
    val id: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val group: String,
    val createdDate: String,
    val lastUpdateDate: String,
    /**
     * 点开消息要去的地址。
     *
     * ★ 门户自己**不换 tempcode**，只是把 `openurl` 交给 WebView；
     * 为空时它 toast「未获取到消息通知地址」。
     * 我们的行为对齐：为空的行**不画箭头也不给点击**。
     */
    val openUrl: String? = null,
)

/** 新闻分组。服务端字段：`id`(字符串) / `newsgroup` / `dataid`(稀疏整数)。 */
data class PortalNewsGroup(
    val id: String,
    val name: String,
    val dataId: Int? = null,
)

/** 新闻条目。服务端字段：`title` / `createdate` / `id`。 */
data class PortalNewsItem(
    val id: String,
    val title: String,
    val createdDate: String,
)

/**
 * 首页「待办事项」三个计数。
 *
 * 服务端字段：`countmyprehandle` / `countmyrequestbill` / `countmyhandled`。
 *
 * ★ 三个字段都**可空**：解析不到就是 `null`（界面显示「—」），
 * **绝不补 0**。补 0 等于告诉用户「你没有待办」，而我们其实不知道。
 */
data class PortalTodoCounts(
    val pending: Int? = null,
    val applied: Int? = null,
    val finished: Int? = null,
) {
    /** 三个都拿不到时视为「完全不知道」，界面不该画这张卡。 */
    val isAllUnknown: Boolean get() = pending == null && applied == null && finished == null
}
