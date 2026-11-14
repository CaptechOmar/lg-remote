package com.github.heroslender.lgtvcontroller.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A preference that lets the user pick one of a fixed list of [options], given as pairs of
 * (stored value, label). A [value] that isn't in the list is shown as is.
 */
@Composable
fun ChoicePreference(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        PreferenceSheet(title = title, onDismiss = { showDialog = false }) { close ->
            options.forEach { (optionValue, label) ->
                SheetOption(
                    label = label,
                    selected = optionValue == value,
                    onClick = {
                        onValueChange(optionValue)
                        close()
                    },
                )
            }
        }
    }

    PreferenceItem(
        title = title,
        subtitle = options.firstOrNull { it.first == value }?.second ?: value.ifEmpty { "-" },
        icon = icon,
        onClick = { showDialog = true },
    )
}
