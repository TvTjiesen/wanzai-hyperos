package com.muyao.wanzaihyperos.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusBarPolicyTest {
    @Test fun expandedWallpaperKeepsItsOwnIcons() {
        assertTrue(homeUsesLightStatusIcons(true, 0.95f, 0f))
        assertFalse(homeUsesLightStatusIcons(false, 0.05f, 0.30f))
    }
    @Test fun collapsedIconsUseRealSamplesInsteadOfThemeCoating() {
        assertFalse(homeUsesLightStatusIcons(true, 0.95f, 1f))
        assertTrue(homeUsesLightStatusIcons(false, 0.05f, 1f))
    }
    @Test fun missingSampleKeepsWallpaperAndReverseRestoresIt() {
        assertTrue(homeUsesLightStatusIcons(true, null, 1f))
        assertFalse(homeUsesLightStatusIcons(false, null, 1f))
        assertTrue(homeUsesLightStatusIcons(true, 0.95f, 0f))
    }
}
