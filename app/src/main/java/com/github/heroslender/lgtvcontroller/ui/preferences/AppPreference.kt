package com.github.heroslender.lgtvcontroller.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.heroslender.lgtvcontroller.R.string
import com.github.heroslender.lgtvcontroller.domain.model.App

/**
 * A preference that lets the user pick one of the [apps] installed on the TV, or type in an app
 * ID by hand (for apps the TV doesn't list). An empty [value] means "none / default".
 */
@Composable
fun AppPreference(
    title: String,
    value: String,
    apps: List<App>,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AppPickerDialog(
            title = title,
            value = value,
            apps = apps,
            onSelected = {
                onValueChange(it)
                showDialog = false
            },
            onDismissRequest = { showDialog = false },
        )
    }

    val currentLabel = when {
        value.isBlank() -> stringResource(string.tv_home_button_default)
        else -> apps.firstOrNull { it.id == value }?.let { "${it.name} (${it.id})" } ?: value
    }

    PreferenceItem(
        title = title,
        subtitle = currentLabel,
        icon = icon,
        onClick = { showDialog = true },
    )
}

@Composable
fun AppPickerDialog(
    title: String,
    value: String,
    apps: List<App>,
    onSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    PreferenceSheet(title = title, onDismiss = onDismissRequest) { close ->
        if (apps.isEmpty()) {
            PreferenceItem(
                title = stringResource(string.app_picker_no_apps),
                subtitle = null,
            )
        }

        SheetOption(
            label = stringResource(string.tv_home_button_default),
            selected = value.isBlank(),
            onClick = {
                onSelected("")
                close()
            },
        )

        apps.forEach { app ->
            SheetOption(
                label = app.name,
                selected = app.id == value,
                onClick = {
                    onSelected(app.id)
                    close()
                },
            )
        }

        var customId by remember {
            mutableStateOf(if (apps.none { it.id == value }) value else "")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = customId,
                onValueChange = { customId = it },
                label = { Text(stringResource(string.app_picker_custom_id)) },
                singleLine = true,
                modifier = Modifier.weight(1F),
            )
            TextButton(
                enabled = customId.isNotBlank(),
                onClick = {
                    onSelected(customId.trim())
                    close()
                },
            ) {
                Text(stringResource(string.app_picker_use_custom_id))
            }
        }
    }
}
