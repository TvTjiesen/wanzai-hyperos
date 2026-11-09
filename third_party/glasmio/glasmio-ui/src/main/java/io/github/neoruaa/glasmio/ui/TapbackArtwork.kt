package io.github.neoruaa.glasmio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.icons.SfIcon

/** Semantic reactions backed by host icons or Apache-2.0 Material vectors, not Apple artwork. */
enum class GlasmioMessageReactionArtwork {
    Heart, ThumbsUp, ThumbsDown, Laugh, Exclamation, Question,
}

internal fun tapbackArtworkSymbol(artwork: GlasmioMessageReactionArtwork): String = when (artwork) {
    GlasmioMessageReactionArtwork.Heart -> "heart.fill"
    GlasmioMessageReactionArtwork.ThumbsUp -> "hand.thumbsup.fill"
    GlasmioMessageReactionArtwork.ThumbsDown -> "hand.thumbsdown.fill"
    GlasmioMessageReactionArtwork.Laugh -> "face.smiling.fill"
    GlasmioMessageReactionArtwork.Exclamation -> "exclamationmark"
    GlasmioMessageReactionArtwork.Question -> "questionmark"
}

@Composable
internal fun TapbackArtwork(artwork: GlasmioMessageReactionArtwork, modifier: Modifier = Modifier) {
    SfIcon(tapbackArtworkSymbol(artwork), null, modifier, size = 28.dp)
}
