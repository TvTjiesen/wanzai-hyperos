package com.muyao.wanzaihyperos.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyao.wanzaihyperos.data.AppearanceSettings
import com.muyao.wanzaihyperos.data.HomeLayoutOrder
import com.muyao.wanzaihyperos.ui.components.WanzaiTextButton
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class HomeLayoutEditing(
    val settings: AppearanceSettings,
    val onMove: (String, String) -> Unit,
    val onToggle: (String) -> Unit,
) {
    fun visible(id: String): Boolean = id !in settings.hiddenHomeSections
}

internal fun homeSectionLabel(id: String): String = when (id) {
    "hero" -> "日期与此刻"
    "quick-actions" -> "快捷入口"
    "todos" -> "待办"
    "rollcall" -> "签到"
    "szty" -> "校园跑"
    "timeline" -> "今日时间线"
    else -> id
}

/** A full-size live card, with its normal actions intercepted only while editing. */
@Composable
internal fun HomeLayoutEditableBlock(
    id: String,
    editing: HomeLayoutEditing?,
    list: LazyListState,
    content: @Composable () -> Unit,
) {
    if (editing == null) { content(); return }
    val latest by rememberUpdatedState(editing)
    var dragging by remember { mutableStateOf(false) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var dragStartItemOffset by remember { mutableFloatStateOf(0f) }
    var dragPointerOffset by remember { mutableFloatStateOf(0f) }
    var lastTarget by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val visible = editing.visible(id)
    val enabled = when (id) {
        "todos", "rollcall", "szty" -> id in editing.settings.modules
        "timeline" -> "courses" in editing.settings.modules
        else -> true
    }
    val order = HomeLayoutOrder.normalize(editing.settings.homeOrder)
    val position = order.indexOf(id)
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    fun updateDragTarget() {
        val items = list.layoutInfo.visibleItemsInfo
        items.firstOrNull { it.key == id }?.let { dragStartItemOffset = it.offset.toFloat() }
        val targetId = items.firstOrNull {
            it.key != id && it.key in HomeLayoutOrder.defaults &&
                pointerY >= it.offset && pointerY < it.offset + it.size
        }?.key as? String
        if (targetId != null && targetId != lastTarget) {
            latest.onMove(id, targetId)
            lastTarget = targetId
        }
    }
    val drag = Modifier.pointerInput(id, list) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                dragStartItemOffset = (item?.offset ?: 0).toFloat()
                dragPointerOffset = offset.y
                pointerY = dragStartItemOffset + offset.y
                lastTarget = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                dragging = true
            },
            onDragEnd = { dragging = false },
            onDragCancel = { dragging = false },
            onDrag = { change, amount ->
                change.consume()
                pointerY += amount.y
                updateDragTarget()
            },
        )
    }
    // Edge scrolling runs only for the one actively held card, not for idle preview cards.
    LaunchedEffect(dragging) {
        while (dragging) {
            val info = list.layoutInfo
            val edge = 80f
            val delta = when {
                pointerY < info.viewportStartOffset + edge -> -18f
                pointerY > info.viewportEndOffset - edge -> 18f
                else -> 0f
            }
            if (delta != 0f) {
                list.scroll { scrollBy(delta) }
                // Both pointerY and item offsets are relative to the viewport, not content.
                // With the finger stationary, only the items move during auto-scroll.
                updateDragTarget()
            }
            delay(24)
        }
    }
    fun moveBy(delta: Int): Boolean {
        val target = order.getOrNull(position + delta) ?: return false
        editing.onMove(id, target)
        return true
    }
    Column(
        Modifier.fillMaxWidth().border(if (dragging) 2.dp else 1.dp,
            if (dragging) colors.primary else colors.onSurface.copy(alpha = .2f), shape)
            .background(colors.surface.copy(alpha = .3f), shape)
            .then(drag)
            .graphicsLayer {
                val itemOffset = list.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == id }?.offset?.toFloat() ?: dragStartItemOffset
                translationY = if (dragging) pointerY - itemOffset - dragPointerOffset else 0f
            }
            .zIndex(if (dragging) 1f else 0f)
            .semantics {
                contentDescription = "${homeSectionLabel(id)}，第 ${position + 1} 项，共 ${order.size} 项，${if (visible) "显示" else "隐藏"}，长按拖动排序"
                customActions = listOf(
                    CustomAccessibilityAction("上移") { moveBy(-1) },
                    CustomAccessibilityAction("下移") { moveBy(1) },
                    CustomAccessibilityAction(if (visible) "隐藏" else "恢复") { editing.onToggle(id); true },
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⠿  ${homeSectionLabel(id)}", fontSize = 14.sp, color = colors.onSurface,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            WanzaiTextButton(if (visible) "隐藏" else "恢复", onClick = { editing.onToggle(id) })
        }
        if (visible && enabled) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(bottom = 6.dp)) {
                Box(Modifier.fillMaxWidth().clearAndSetSemantics { }) { content() }
                // This sibling wins hit testing over buttons/ball inside the real card.
                Box(Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                })
            }
        } else {
            Text(if (!visible) "已隐藏 · 恢复后保留原位置" else "模块已关闭 · 可在下方首页模块中启用", fontSize = 13.sp, color = colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
        }
    }
}
