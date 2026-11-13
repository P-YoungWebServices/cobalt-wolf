package com.pyoungws.cobaltwolf.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import com.pyoungws.cobaltwolf.isTelevision
import kotlinx.coroutines.launch

/**
 * Lets a LazyColumn scroll with the D-pad on TV even when its items have no focusable
 * children (read-only lists, logs, info pages). The list becomes a focus target itself;
 * while it holds focus, Up/Down scroll three quarters of a viewport per press. Keys are
 * only consumed while the list itself is focused, so directional navigation between
 * focusable children elsewhere on the screen is unaffected. No-op on phones/tablets.
 */
fun Modifier.tvListScroll(state: LazyListState): Modifier = composed {
    if (!LocalContext.current.isTelevision()) return@composed this
    val scope = rememberCoroutineScope()
    var selfFocused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { selfFocused = it.isFocused }
        .focusable()
        .onKeyEvent { event ->
            if (!selfFocused || event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val delta = when (event.key) {
                Key.DirectionDown -> state.layoutInfo.viewportSize.height.toFloat() * 0.75f
                Key.DirectionUp -> -state.layoutInfo.viewportSize.height.toFloat() * 0.75f
                else -> return@onKeyEvent false
            }
            if ((delta > 0 && !state.canScrollForward) || (delta < 0 && !state.canScrollBackward)) return@onKeyEvent false
            scope.launch { state.scroll { scrollBy(delta) } }
            true
        }
}
