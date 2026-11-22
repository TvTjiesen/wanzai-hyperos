package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.jw.OwnerSnapshotStore
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Content only, in private storage. Session-bearing destination URLs are never persisted. */
internal class PortalMessageSnapshot(file: File?, epoch: AccountEpoch) {
    private val store = OwnerSnapshotStore(file, "portal-message-groups", epoch)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Row(
        val id: String, val title: String, val message: String, val read: Boolean,
        val group: String, val created: String, val updated: String,
    )
    @Serializable private data class Group(
        val name: String, val unread: Int, val rows: List<Row>, val page: Int,
        val hasMore: Boolean, val checked: Long?,
    )
    @Serializable private data class Payload(val selected: String?, val groups: List<Group>)
    data class Restored(
        val selected: String?, val groups: List<PortalMessageGroup>,
        val states: Map<String, ListState<PortalMessageItem>>,
    )

    fun read(owner: Long): Restored? = runCatching {
        val payload = json.decodeFromString<Payload>(store.read(owner) ?: return null)
        require(payload.groups.size <= 20)
        require(payload.groups.map { it.name }.distinct().size == payload.groups.size)
        val states = payload.groups.associate { group ->
            require(group.name.isNotBlank() && group.page in 1..1000 && group.rows.size <= 2000)
            val items = group.rows.map { row ->
                require(row.id.isNotBlank() && row.group == group.name)
                PortalMessageItem(row.id, row.title, row.message, row.read, row.group, row.created, row.updated)
            }.distinctBy { it.id }
            val meta = ListMeta(page = group.page, hasMore = group.hasMore,
                updatedAtMillis = group.checked, stale = true)
            group.name to if (items.isEmpty()) ListState.Empty(meta) else ListState.Content(items, meta)
        }
        Restored(payload.selected, payload.groups.map { PortalMessageGroup(it.name, it.unread) }, states)
    }.getOrNull()

    fun write(owner: Long, selected: String?, groups: List<PortalMessageGroup>, states: Map<String, ListState<PortalMessageItem>>) {
        val payload = groups.take(20).mapNotNull { group ->
            val state = states[group.name] ?: return@mapNotNull null
            val meta = when (state) {
                is ListState.Content -> state.meta
                is ListState.Empty -> state.meta
                else -> return@mapNotNull null
            }
            val items = (state as? ListState.Content)?.items.orEmpty()
            if (items.size > 2000) return@mapNotNull null
            Group(group.name, group.unread, items.map { row ->
                Row(row.id, row.title, row.message, row.isRead, group.name, row.createdDate, row.lastUpdateDate)
            }, meta.page, meta.hasMore, meta.updatedAtMillis)
        }
        store.write(owner, json.encodeToString(Payload(selected, payload)))
    }
}
