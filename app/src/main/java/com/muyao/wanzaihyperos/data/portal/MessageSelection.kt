package com.muyao.wanzaihyperos.data.portal

internal fun reconcileMessageGroup(
    groups: List<PortalMessageGroup>,
    selected: PortalMessageGroup?,
): PortalMessageGroup? = groups.firstOrNull { it.name == selected?.name } ?: groups.firstOrNull()

internal fun newestMessageRows(items: List<PortalMessageItem>): List<PortalMessageItem> =
    items.sortedByDescending { it.createdDate }

internal fun PortalMessageItem.opensPortalTextFirst(selectedGroup: String?): Boolean =
    selectedGroup?.trim() == "活动通知" || group.trim() == "活动通知"
