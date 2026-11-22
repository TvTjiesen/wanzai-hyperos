package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.jw.OwnerSnapshotStore
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.catchingActive
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Private content only: destination URLs and transport/session envelopes never enter this codec. */
internal class PortalNewsSnapshot(file: File?, private val epoch: AccountEpoch) {
    private val store = OwnerSnapshotStore(file, "portal-news-groups", epoch)
    private val json = Json { ignoreUnknownKeys = true }

    data class Entry(val group: PortalNewsGroup, val state: ListState<PortalNewsItem>)
    data class Restored(val selected: PortalNewsGroup?, val entries: List<Entry>)

    @Serializable private data class Row(val id: String, val title: String, val created: String)
    @Serializable private data class Group(
        val id: String, val name: String, val cataId: Int?, val rows: List<Row>,
        val page: Int, val hasMore: Boolean, val checked: Long,
    )
    @Serializable private data class Selection(val id: String, val cataId: Int?)
    @Serializable private data class Payload(
        val version: Int = 1, val selected: Selection?, val groups: List<Group>,
    )

    private fun validate(payload: Payload) {
        require(payload.version == 1 && payload.groups.size in 1..32)
        require(payload.groups.map { it.id to it.cataId }.distinct().size == payload.groups.size)
        payload.groups.forEach { group ->
            require(group.id.isNotBlank() && group.id.length <= 512 && group.name.length <= 512)
            require(group.page in 1..1000 && group.rows.size <= 2000 && group.checked >= 0)
            require(group.rows.map { it.id }.distinct().size == group.rows.size)
            group.rows.forEach { row ->
                require(row.id.isNotBlank() && row.id.length <= 512)
                require(row.title.isNotBlank() && row.title.length <= 8192 && row.created.length <= 128)
            }
        }
        payload.selected?.let { selected ->
            require(payload.groups.any { it.id == selected.id && it.cataId == selected.cataId })
        }
    }

    fun read(owner: Long): Restored? {
        var restored: Restored? = null
        epoch.publishData(owner) {
            restored = catchingActive {
                val payload = json.decodeFromString<Payload>(store.read(owner) ?: return@catchingActive null)
                validate(payload)
                val entries = payload.groups.map { group ->
                    val meta = ListMeta(page = group.page, hasMore = group.hasMore,
                        updatedAtMillis = group.checked, stale = true)
                    val rows = group.rows.map { PortalNewsItem(it.id, it.title, it.created) }
                    Entry(PortalNewsGroup(group.id, group.name, group.cataId),
                        if (rows.isEmpty()) ListState.Empty(meta) else ListState.Content(rows, meta))
                }
                val selected = payload.selected?.let { key ->
                    entries.first { it.group.id == key.id && it.group.dataId == key.cataId }.group
                }
                Restored(selected, entries)
            }.getOrNull()
        }
        return restored
    }

    /** Call only after a confirmed successful window/page. Invalid input leaves the old file intact. */
    fun write(owner: Long, selected: PortalNewsGroup?, entries: List<Entry>): Boolean {
        var written = false
        epoch.publishData(owner) {
            written = catchingActive {
                val groups = entries.map { entry ->
                    val state = entry.state
                    val meta = when (state) {
                        is ListState.Content -> state.meta
                        is ListState.Empty -> state.meta
                        else -> return@catchingActive false
                    }
                    if (meta.refreshing || meta.loadingMore) return@catchingActive false
                    val checked = meta.updatedAtMillis ?: return@catchingActive false
                    val rows = (state as? ListState.Content)?.items.orEmpty().map {
                        Row(it.id, it.title, it.createdDate)
                    }
                    Group(entry.group.id, entry.group.name, entry.group.dataId,
                        rows, meta.page, meta.hasMore, checked)
                }
                val selection = selected?.takeIf { group ->
                    groups.any { it.id == group.id && it.cataId == group.dataId }
                }?.let { Selection(it.id, it.dataId) }
                val payload = Payload(selected = selection, groups = groups)
                validate(payload)
                store.write(owner, json.encodeToString(payload))
            }.getOrDefault(false)
        }
        return written
    }
}
