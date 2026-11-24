package com.muyao.wanzaihyperos.ui.main.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl

/** The timetable and notices share Glasmio's track, lens, drag and spring. */
@Composable
internal fun <T> ScheduleNoticeSelector(
    items: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    selectOnIndicatorTap: Boolean = false,
    itemContent: (@Composable BoxScope.(T, Boolean) -> Unit)? = null,
) {
    GlasmioSegmentedControl(
        items = items,
        selected = selected,
        onSelected = onSelected,
        modifier = modifier,
        height = height,
        itemContent = itemContent,
        selectOnIndicatorTap = selectOnIndicatorTap,
    )
}

/** Keep the action, keyboard focus and role without a rectangular theme indication. */
internal fun Modifier.scheduleNoticeClick(
    onClickLabel: String,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = null,
    indication = null,
    onClickLabel = onClickLabel,
    role = Role.Button,
    onClick = onClick,
)

/** The row owns the click; this cue must not introduce another focusable button. */
@Composable
internal fun ScheduleNoticeActionCue(text: String, prominent: Boolean) {
    GlassSurface(
        modifier = Modifier.clearAndSetSemantics { },
        shape = CircleShape,
        emphasis = if (prominent) GlassEmphasis.Prominent else GlassEmphasis.Regular,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
