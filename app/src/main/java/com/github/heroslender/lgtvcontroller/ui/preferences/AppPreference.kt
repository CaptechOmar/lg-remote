package com.github.heroslender.lgtvcontroller.ui.preferences

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = { showDialog = true },
            )
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = currentLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun AppPickerDialog(
    title: String,
    value: String,
    apps: List<App>,
    onSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (apps.isEmpty()) {
                    Text(
                        text = stringResource(string.app_picker_no_apps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item {
                        AppPickerRow(
                            label = stringResource(string.tv_home_button_default),
                            selected = value.isBlank(),
                            onClick = { onSelected("") },
                        )
                    }

                    items(apps, key = { it.id }) { app ->
                        AppPickerRow(
                            label = app.name,
                            selected = app.id == value,
                            onClick = { onSelected(app.id) },
                        )
                    }
                }

                var customId by remember {
                    mutableStateOf(if (apps.none { it.id == value }) value else "")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
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
                        onClick = { onSelected(customId.trim()) },
                    ) {
                        Text(stringResource(string.app_picker_use_custom_id))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
