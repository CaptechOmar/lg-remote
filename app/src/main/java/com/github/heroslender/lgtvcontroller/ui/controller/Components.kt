package com.github.heroslender.lgtvcontroller.ui.controller

import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.github.heroslender.lgtvcontroller.ui.theme.LGTVControllerTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

val ButtonShape = ShapeDefaults.Large
val ControlsSpacing = 10.dp

@Composable
fun CTextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonShape,
    fontSize: TextUnit = 3.7.em,
    repeatOnHold: Boolean = false,
    onPressedChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickRelease: (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    CButton(
        enabled = enabled,
        shape = shape,
        modifier = modifier,
        repeatOnHold = repeatOnHold,
        onPressedChange = onPressedChange,
        onLongClick = onLongClick,
        onLongClickRelease = onLongClickRelease,
        onClick = onClick
    ) {
        Text(text, fontSize = fontSize)
    }
}

@Composable
fun CIconButton(
    iconId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonShape,
    useDefaultTint: Boolean = false,
    repeatOnHold: Boolean = false,
    onPressedChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val color = if (useDefaultTint) Color.Unspecified else null
    CButton(
        enabled = enabled,
        shape = shape,
        modifier = modifier,
        repeatOnHold = repeatOnHold,
        onPressedChange = onPressedChange,
        onClick = onClick
    ) {
        if (color == null) {
            Icon(painterResource(iconId), contentDescription, modifier = Modifier.size(32.dp))
        } else {
            Icon(
                painterResource(iconId),
                contentDescription,
                modifier = Modifier.size(32.dp),
                tint = color
            )
        }
    }
}

@Composable
fun CIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonShape,
    useDefaultTint: Boolean = false,
    repeatOnHold: Boolean = false,
    onPressedChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    val color = if (useDefaultTint) Color.Unspecified else null
    CButton(
        enabled = enabled,
        shape = shape,
        modifier = modifier,
        repeatOnHold = repeatOnHold,
        onPressedChange = onPressedChange,
        onClick = onClick
    ) {
        if (color == null) {
            Icon(imageVector = imageVector, contentDescription, modifier = Modifier.size(32.dp))
        } else {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(32.dp),
                tint = color
            )
        }
    }
}

@Composable
fun CButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonShape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    repeatOnHold: Boolean = false,
    onPressedChange: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickRelease: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    // Whether the current press already fired repeats, so releasing doesn't fire one more click
    val repeated = remember { BooleanArray(1) }
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnLongClickRelease by rememberUpdatedState(onLongClickRelease)
    val haptics = LocalHapticFeedback.current
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    LaunchedEffect(isPressed) {
        currentOnPressedChange?.invoke(isPressed)
    }

    if (repeatOnHold) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                repeated[0] = false
                delay(HOLD_REPEAT_DELAY_MS)
                repeated[0] = true
                while (true) {
                    currentOnClick()
                    delay(HOLD_REPEAT_INTERVAL_MS)
                }
            }
        }
    }

    // A long press runs [onLongClick] once instead of the normal click. It shares the `repeated`
    // flag with repeatOnHold (they're not meant to be combined) so the release doesn't also click.
    if (onLongClick != null) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                repeated[0] = false
                delay(LONG_PRESS_DELAY_MS)
                repeated[0] = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongClick?.invoke()

                // Stay here until the finger lifts (isPressed changes, which cancels this effect)
                try {
                    awaitCancellation()
                } finally {
                    currentOnLongClickRelease?.invoke()
                }
            }
        }
    }

    Button(
        onClick = {
            if (repeated[0]) {
                repeated[0] = false
            } else {
                onClick()
            }
        },
        interactionSource = interactionSource,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        colors = ButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier,
        content = content
    )
}

/** How long a button has to be held before it starts repeating. */
const val HOLD_REPEAT_DELAY_MS = 450L

/** How long a button has to be held to count as a long press. */
const val LONG_PRESS_DELAY_MS = 500L

/** Time between repeats while a button is held. */
const val HOLD_REPEAT_INTERVAL_MS = 120L

val ColorButtonHeight = 48.dp
val ColorButtonTopSpacing = 30.dp
val ColorButtonRed = Color(0xFFD32F2F)
val ColorButtonGreen = Color(0xFF388E3C)
val ColorButtonYellow = Color(0xFFFBC02D)
val ColorButtonBlue = Color(0xFF1976D2)

/**
 * One of the four colored shortcut keys (red, green, yellow, blue) found on TV remotes.
 */
@Composable
fun ColorButton(
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonColors(
            containerColor = color,
            contentColor = Color.Unspecified,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    ) {}
}

@Composable
fun RowScope.VerticalControls(
    centerText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centerHighlight: Boolean = false,
    topButton: @Composable () -> Unit,
    bottomButton: @Composable () -> Unit,
) {
    Surface(
        shape = ButtonShape,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column {
            topButton()

            Text(
                text = centerText,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = if (centerHighlight) 5.em else 3.em,
                fontWeight = if (centerHighlight) androidx.compose.ui.text.font.FontWeight.Bold else null,
            )

            bottomButton()
        }
    }
}

@Preview(
    showBackground = true,
)
@Composable
fun PreviewLight() {
    LGTVControllerTheme {
        CTextButton("Hello")
    }
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewDark() {
    LGTVControllerTheme {
        CTextButton("Hello")
    }
}

@Preview(
    showBackground = true,
)
@Composable
fun PreviewLightDisabled() {
    LGTVControllerTheme {
        CTextButton("Hello", enabled = false)
    }
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewDarkDisabled() {
    LGTVControllerTheme {
        CTextButton("Hello", enabled = false)
    }
}