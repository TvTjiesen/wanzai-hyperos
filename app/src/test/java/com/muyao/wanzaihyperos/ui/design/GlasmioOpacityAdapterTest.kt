package com.muyao.wanzaihyperos.ui.design

import com.muyao.wanzaihyperos.ui.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GlasmioOpacityAdapterTest {
    private val repo: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "app/build.gradle.kts").isFile }

    private fun code(path: String) = SourceScan.stripComments(File(repo, path).readText(Charsets.UTF_8))
    private val glass = "third_party/glasmio/glasmio-glass/src/main/java/io/github/neoruaa/glasmio/glass"
    private val app = "app/src/main/java/com/muyao/wanzaihyperos/ui"

    @Test
    fun `appearance defaults to native strength and theme reads live opacity`() {
        val tokens = code("third_party/glasmio/glasmio-core/src/main/java/io/github/neoruaa/glasmio/core/GlassTokens.kt")
        assertTrue("Native default must remain unchanged", "val materialAlphaMultiplier: Float = 1f" in tokens)
        val theme = code("$app/design/glasmio/WanzaiGlasmioTheme.kt")
        assertTrue("Live preference must reach the material appearance", "materialAlphaMultiplier = com.muyao.wanzaihyperos.ui.design.glass.glassOpacityFactor()" in theme)
        assertTrue("Off and accessibility still take precedence", "reduceTransparency = UiPrefs.glassMode == \"off\" || !motion.glassIsTranslucent" in theme)
        assertEquals(1.38f, com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(0f), 0.0001f)
        assertEquals(1f, com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(50f), 0.0001f)
        assertEquals(0.62f, com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(100f), 0.0001f)
    }

    @Test
    fun `native paint and retained sources both carry strength and explicit alpha opts out`() {
        val surface = code("$glass/GlassSurface.kt")
        assertTrue("Resolved navigation material must carry strength", "materialStrength = if (materialTint != null) 1f else appearance.materialAlphaMultiplier" in surface)
        assertTrue("Retained source must match the visible material", "materialStrength = if (navigationSurfaceColor != null) 1f else appearance.materialAlphaMultiplier" in surface)
        assertTrue("Lens source must use the same appearance", "materialStrength = if (appearance.usesBackdrop) appearance.materialAlphaMultiplier else 1f" in surface)
        assertTrue("Opaque mode remains an actual opaque fill", "if (appearance.reduceTransparency) {\n                drawRect(containerColor.copy(alpha = 1f))" in surface)
        assertTrue("Opaque mode must not sample", "backdrop = if (sampleBackdrop && appearance.usesBackdrop) backdrop else emptyBackdrop()" in surface)
    }

    @Test
    fun `strength participates in recipe identity and is not clipped at one`() {
        val renderer = code("$glass/NavigationMaterialRenderer.kt")
        assertTrue("Recipe defaults must preserve original appearance", "val strength: Float = 1f" in renderer)
        assertTrue("Strength changes must invalidate the material program", "strength == other.strength" in renderer)
        assertTrue("Strength must reach both shader endpoints", "opacity.coerceIn(0f, 1f) * recipe.strength" in renderer)
        assertTrue("Accept thicker than the native default", "materialStrength.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 1f" in renderer)
        assertFalse("Do not flatten the entire thicker half of the slider", "recipe.strength.coerceIn(0f, 1f)" in renderer)
        assertTrue("Both appearance endpoints keep the native default", "if (materialOpacity == 1.0) return result;" in renderer && "if (appearance_materialOpacity == 1.0) return result;" in renderer)
        assertEquals("Both endpoints clamp only final premultiplied output", 2, Regex("clamp\\(float3\\(mixed.rgb\\), float3\\(0.0\\), float3\\(mixedAlpha\\)\\)").findAll(renderer).count())
    }

    @Test
    fun `opacity animation fast paths retain both endpoint strengths`() {
        val renderer = code("$glass/NavigationMaterialRenderer.kt")
        assertTrue("RenderEffect opacity-only updates must retain strength", "shader.setFloatUniform(\"materialOpacity\", alpha * recipe.strength)" in renderer)
        assertTrue("RenderEffect alternate keeps its own strength", "shader.setFloatUniform(\"appearance_materialOpacity\", alpha * (recipe.appearanceAlternate ?: recipe).strength)" in renderer)
        assertTrue("RuntimeXfermode opacity-only updates must retain strength", "blender.setFloatUniform(\"materialOpacity\", nextOpacity * value.strength)" in renderer)
        assertTrue("RuntimeXfermode alternate keeps its own strength", "blender.setFloatUniform(\"appearance_materialOpacity\", nextOpacity * (value.appearanceAlternate ?: value).strength)" in renderer)
    }

    @Test
    fun `material strength has three distinct outputs without changing foreground opacity`() {
        val sampled = 0.2f
        val native = 0.6f
        val outputs = listOf(0f, 50f, 100f).map { value ->
            (sampled + (native - sampled) * com.muyao.wanzaihyperos.ui.design.glass.opacityFactorFor(value)).coerceIn(0f, 1f)
        }
        assertEquals("Each endpoint must be distinguishable", 3, outputs.distinct().size)
        assertTrue("More transparent reduces coating strength", outputs[0] > outputs[1] && outputs[1] > outputs[2])
        val theme = code("$app/design/glasmio/WanzaiGlasmioTheme.kt")
        assertFalse("Do not make page content transparent", "Modifier.alpha(" in theme)
        assertFalse("Do not apply strength to foreground roles", "content = legacy.onSurface.copy(alpha" in theme)
    }

    @Test
    fun `morph recipes use the same appearance while explicit tint remains caller owned`() {
        val morph = code("$glass/GlassMorphRenderer.kt")
        assertTrue("Appearance remains part of the remembered renderer", "material, appearance, programs" in morph)
        assertTrue("Dark-fraction material must use strength", "NavigationMaterialRecipe.resolve(presentationDarkFraction, material, materialStrength = appearance.materialAlphaMultiplier)" in morph)
        assertTrue("Endpoint cached material must use strength", "NavigationMaterialRecipe.resolve(isDark, material, materialStrength = appearance.materialAlphaMultiplier)" in morph)
        assertTrue("Explicit tint alpha must not be multiplied twice", "materialStrength = if (tint != null) 1f else appearance.materialAlphaMultiplier" in morph)
    }
}
