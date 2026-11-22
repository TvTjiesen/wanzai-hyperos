package com.muyao.wanzaihyperos.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SlidingChoiceRow(
    labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    stableKeys: List<String> = labels.indices.map { it.toString() },
    itemContent: (@Composable (Int, Boolean) -> Unit)? = null,
) {
    require(stableKeys.size == labels.size && stableKeys.distinct().size == stableKeys.size)
    val colors = MiuixTheme.colorScheme
    val motion = rememberMotion()
    var bounds by remember(stableKeys) { mutableStateOf<Map<String, Rect>>(emptyMap()) }
    val focus = remember(stableKeys) { stableKeys.associateWith { FocusRequester() } }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val selectedKey = stableKeys.getOrNull(selectedIndex)
    val target = bounds[selectedKey]
    LaunchedEffect(selectedKey) {
        if (focusedKey != null && focusedKey != selectedKey) selectedKey?.let { focus[it]?.requestFocus() }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(selectedIndex, target, scroll.viewportSize, motion.canMove) {
        val rect = target ?: return@LaunchedEffect
        if (scroll.viewportSize <= 0) return@LaunchedEffect
        val destination = when {
            rect.left < scroll.value -> rect.left.toInt()
            rect.right > scroll.value + scroll.viewportSize -> (rect.right - scroll.viewportSize).toInt()
            else -> scroll.value
        }.coerceIn(0, scroll.maxValue)
        if (motion.canMove) scroll.animateScrollTo(destination) else scroll.scrollTo(destination)
    }
    Box(modifier.horizontalScroll(scroll)) {
        Row(Modifier.selectableGroup().slidingSelectionCarrier(selectedKey, target,
            colors.primary.copy(alpha = if (motion.highContrast) .24f else .14f)),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, label ->
                key(stableKeys[index]) {
                Box(Modifier.onGloballyPositioned { coordinates ->
                    val next = coordinates.boundsInParent()
                    if (bounds[stableKeys[index]] != next) bounds = bounds + (stableKeys[index] to next)
                }.focusRequester(focus.getValue(stableKeys[index])).onFocusChanged {
                    if (it.isFocused) focusedKey = stableKeys[index]
                    else if (focusedKey == stableKeys[index]) focusedKey = null
                }.heightIn(min = 48.dp)
                    .selectable(selected = index == selectedIndex, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    if (itemContent != null) itemContent(index, index == selectedIndex)
                    else Text(label, fontSize = Type.bodyEmphasis,
                        color = if (index == selectedIndex) colors.primary else colors.onSurface,
                        maxLines = 1)
                }
                }
            }
        }
    }
}
