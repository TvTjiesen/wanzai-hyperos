package com.muyao.wanzaihyperos.ui.home

import com.muyao.wanzaihyperos.ui.design.AppColors

internal fun homeUsesLightStatusIcons(
    skyUsesLightIcons: Boolean,
    sampledLuminance: Float?,
    collapseProgress: Float,
): Boolean {
    if (AppColors.veilAlpha(collapseProgress) < 0.5f || sampledLuminance == null) return skyUsesLightIcons
    return sampledLuminance < 0.45f
}
