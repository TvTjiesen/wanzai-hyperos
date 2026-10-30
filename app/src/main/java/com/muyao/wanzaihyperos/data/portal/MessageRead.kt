package com.muyao.wanzaihyperos.data.portal

/**
 * 消息列表的「已读」本地变换。
 *
 * 只有两个纯函数，不碰网络、不碰状态 —— 这样它可以被单测钉死，
 * 而仓库只负责「什么时候调用它」，不负责「怎么改列表」。
 *
 * 口径来自 docs/B7-WALKTHROUGH.md 第 2 节：
 * 服务端是唯一真相源，本地只做**乐观显示**；写失败必须把那一行退回原样，
 * 否则会出现「界面说已读、服务端说未读」—— 那种状态会让人把没看过的通知漏掉。
 */
internal object MessageRead {

    /** 把这批消息里 id 相符的那一条标成已读。列表本身不变序、不增删。 */
    fun markOne(items: List<PortalMessageItem>, id: String): List<PortalMessageItem> =
        mapOne(items, id) { it.copy(isRead = true) }

    /**
     * 把 id 相符的那一条退回**基线里的已读状态**。
     *
     * 传基线而不是直接写 false：期间列表可能已经刷新过，
     * 服务端若已把它标成已读，硬写 false 等于用旧结论盖掉新事实。
     * 基线里找不到这条（换了分组/换了页）就原样返回，不动任何一行。
     */
    fun revertOne(
        items: List<PortalMessageItem>,
        baseline: List<PortalMessageItem>,
        id: String,
    ): List<PortalMessageItem> {
        val was = baseline.firstOrNull { it.id == id } ?: return items
        return mapOne(items, id) { it.copy(isRead = was.isRead) }
    }

    private inline fun mapOne(
        items: List<PortalMessageItem>,
        id: String,
        change: (PortalMessageItem) -> PortalMessageItem,
    ): List<PortalMessageItem> {
        if (items.none { it.id == id }) return items
        return items.map { if (it.id == id) change(it) else it }
    }
}
