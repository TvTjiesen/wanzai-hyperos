package com.muyao.wanzaihyperos.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppearanceSettingsTest {
    @Test fun snapshotRoundTripPreservesEveryAppearanceChoice() {
        val original = AppearanceSettings(theme = "Dark", background = "sakura", glass = "frost", opacity = 73,
            container = "bare", sky = "album", dim = 65, soft = 90, panX = -.4f, panY = .6f, zoom = 2.5f,
            density = "compact", modules = setOf("todos"), appsCollapsed = true, reduceTransparency = true)
        assertEquals(original, Json.decodeFromString<AppearanceSettings>(Json.encodeToString(original)).validated())
    }
    @Test fun defaultAndAllHiddenModulesAreValid() {
        AppearanceSettings().validated()
        AppearanceSettings(modules = emptySet()).validated()
    }
    @Test fun corruptSelectionsAreRejectedBeforeSaving() {
        listOf(AppearanceSettings(theme = "invalid"), AppearanceSettings(background = "invalid"),
            AppearanceSettings(sky = "invalid"), AppearanceSettings(zoom = Float.NaN),
            AppearanceSettings(panX = 2f), AppearanceSettings(opacity = 101),
            AppearanceSettings(modules = setOf("unknown"))).forEach {
            assertTrue(runCatching { it.validated() }.isFailure)
        }
    }
}
