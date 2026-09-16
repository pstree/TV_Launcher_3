package com.github.honqout.tvlauncher3.components.grid

import android.view.KeyEvent
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Vertically center the item at [index] inside the viewport of [state].
 *
 * Does nothing when [index] is out of range, when the grid has no laid out item yet, or when the
 * item is already the one being measured against the viewport centre.
 */
fun centerFocusedItem(
    scope: CoroutineScope,
    state: LazyGridState,
    index: Int,
    itemCount: Int
) {
    if (index !in 0..<itemCount) {
        return
    }
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) {
        return
    }
    val offsetInViewport = index - visibleItems.first().index
    if (offsetInViewport !in visibleItems.indices) {
        scope.launch { state.animateScrollToItem(index) }
        return
    }
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2F
    val focusedItem = visibleItems[offsetInViewport]
    val focusedItemCenter = focusedItem.offset.y + focusedItem.size.height / 2F
    val scrollOffset = focusedItemCenter - viewportCenter
    scope.launch { state.animateScrollBy(scrollOffset) }
}

/**
 * Scroll the item at [index] back into view when it is outside the currently visible window.
 */
fun scrollFocusedItemIntoView(
    scope: CoroutineScope,
    state: LazyGridState,
    index: Int,
    itemCount: Int
) {
    if (index !in 0..<itemCount) {
        return
    }
    val visibleItems = state.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) {
        return
    }
    val offsetInViewport = index - visibleItems.first().index
    if (offsetInViewport < 0 || offsetInViewport >= visibleItems.size) {
        scope.launch { state.animateScrollToItem(index) }
    }
}

/**
 * Keep the focused item visible while navigating a grid with the D-pad.
 *
 * Vertical moves re-center the focused item so it never hugs an edge; horizontal moves only pull it
 * back into the viewport when it scrolled out of sight.
 */
fun Modifier.followGridFocus(
    scope: CoroutineScope,
    state: LazyGridState,
    focusedItemIndex: () -> Int,
    itemCount: () -> Int
): Modifier = onKeyEvent { keyEvent ->
    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_UP) {
        return@onKeyEvent false
    }
    val index = focusedItemIndex()
    val count = itemCount()
    when (keyEvent.key) {
        Key.DirectionUp, Key.DirectionDown -> {
            centerFocusedItem(scope, state, index, count)
            true
        }

        Key.DirectionLeft, Key.DirectionRight -> {
            scrollFocusedItemIntoView(scope, state, index, count)
            true
        }

        else -> false
    }
}
