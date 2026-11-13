package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.VisualTransformation

/**
 * An [OutlinedTextField] for TV: focusing it with the D-pad never opens the on-screen
 * keyboard. The field stays read-only until the user presses centre/enter while it is
 * focused, then becomes editable and shows the IME. Losing focus makes it read-only again.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var editable by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val dpadExit = mapOf(
        Key.DirectionUp to FocusDirection.Up,
        Key.DirectionDown to FocusDirection.Down,
        Key.DirectionLeft to FocusDirection.Left,
        Key.DirectionRight to FocusDirection.Right,
    )

    LaunchedEffect(editable) { if (editable) keyboard?.show() }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                // Preview, not onKeyEvent: an editable text field consumes direction keys
                // for cursor movement before a regular handler ever sees them, so the
                // remote stays trapped once edit mode is on. Preview sees keys first.
                val dir = dpadExit[event.key]
                when {
                    event.type != KeyEventType.KeyDown -> false
                    dir != null && !(editable && (dir == FocusDirection.Left || dir == FocusDirection.Right)) -> {
                        // Left/right keep their cursor role while editing; everything else
                        // exits edit mode and moves focus — a single-line field has no
                        // vertical cursor movement anyway.
                        if (editable) {
                            editable = false
                            keyboard?.hide()
                        }
                        focusManager.moveFocus(dir)
                        true
                    }
                    editable && event.key == Key.Back -> {
                        editable = false
                        keyboard?.hide()
                        true
                    }
                    !editable && (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                        editable = true
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { if (!it.isFocused) editable = false },
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        isError = isError,
        supportingText = supportingText,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        readOnly = !editable,
    )
}

/** Full-width variant used by the top search fields on TV screens. */
@Composable
fun TvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
) = TvTextField(value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(), placeholder = placeholder)
