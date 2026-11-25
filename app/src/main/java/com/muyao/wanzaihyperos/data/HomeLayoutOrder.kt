package com.muyao.wanzaihyperos.data

/** Stable top-level block IDs shared by the homepage and its layout editor. */
object HomeLayoutOrder {
    val defaults: List<String> = listOf("hero", "quick-actions", "todos", "rollcall", "szty", "timeline")
    private val known = defaults.toSet()

    /** Preserve a user's order, discard obsolete IDs, and append newly introduced sections. */
    fun normalize(order: List<String>): List<String> {
        val retained = order.filter { it in known }.distinct()
        return retained + defaults.filterNot { it in retained }
    }

    fun move(order: List<String>, fromId: String, toId: String): List<String> {
        val normalized = normalize(order).toMutableList()
        val from = normalized.indexOf(fromId)
        val to = normalized.indexOf(toId)
        if (from < 0 || to < 0 || from == to) return normalized
        normalized.add(to, normalized.removeAt(from))
        return normalized
    }
}
