package com.muyao.wanzaihyperos.ui.main

import androidx.compose.runtime.compositionLocalOf

internal class PageEntryGate {
    private val entered = mutableMapOf<String, Long>()

    @Synchronized
    fun claim(page: String, owner: Long): Boolean {
        if (entered[page] == owner) return false
        entered[page] = owner
        return true
    }

    @Synchronized
    fun hasEntered(page: String, owner: Long): Boolean = entered[page] == owner
}

internal val LocalPageEntryGate = compositionLocalOf { PageEntryGate() }
internal val LocalPageEntryOwner = compositionLocalOf { 0L }
internal val LocalPageVisible = compositionLocalOf { true }
