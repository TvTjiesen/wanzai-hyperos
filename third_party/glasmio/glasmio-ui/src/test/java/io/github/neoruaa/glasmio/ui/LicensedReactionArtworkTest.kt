package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.icons.MaterialGlasmioIconResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensedReactionArtworkTest {
    @Test fun allSemanticReactionsKeepDistinctMaterialVectorsWithoutBundledImages() {
        val symbols = GlasmioMessageReactionArtwork.entries.map(::tapbackArtworkSymbol)
        assertEquals(6, symbols.toSet().size)
        val vectors = symbols.map(MaterialGlasmioIconResolver::resolve)
        assertEquals(6, vectors.map { it.name }.toSet().size)
        vectors.forEach { vector ->
            assertTrue(vector.defaultWidth.value > 0f)
            assertTrue(vector.defaultHeight.value > 0f)
        }
    }
}
