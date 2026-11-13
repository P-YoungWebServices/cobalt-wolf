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
import androidx.compose.ui.input.key.onKeyEvent
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
            .onKeyEvent { event ->
                val dir = dpadExit[event.key]
                if (event.type == KeyEventType.KeyDown && dir != null) {
                    // A focused text field swallows D-pad keys for cursor movement — even
                    // readOnly — so the remote can never leave it. Exit edit mode if needed
                    // and always hand the direction to the focus system instead.
                    if (editable) {
                        editable = false
                        keyboard?.hide()
                    }
                    focusManager.moveFocus(dir)
                    true
                } else if (!editable && event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    editable = true
                    true
                } else {
                    false
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
