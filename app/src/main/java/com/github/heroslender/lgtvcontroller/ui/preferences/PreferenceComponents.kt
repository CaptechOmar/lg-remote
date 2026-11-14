package com.github.heroslender.lgtvcontroller.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.heroslender.lgtvcontroller.ControllerTopAppBar
import com.github.heroslender.lgtvcontroller.R.string
import com.github.heroslender.lgtvcontroller.ui.theme.LGTVControllerTheme


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    LGTVControllerTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                ControllerTopAppBar(
                    title = stringResource(string.edit_tv_title),
                    navigateUp = {},
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(
                        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                        top = innerPadding.calculateTopPadding()
                    )
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .padding(15.dp, 10.dp)
            ) {
                PreferenceCategory("General")

                TextPrefence(
                    title = "ID",
                    value = "c93dbf94-d50d-480b-90d3-009c1a2b21c1",
                )

                TextPrefence(
                    title = "Name",
                    value = "LG TV WebOs - Some Version",
                )

                TextPrefence(
                    title = "Display name",
                    value = "Living Room TV",
                    onValueChange = { newValue ->
                        println("New value: $newValue")
                    }
                )

                SwitchPrefence(
                    title = "Auto Connect",
                    subtitle = "Automatically connect to this device when the app starts",
                    value = true,
                    onValueChange = { }
                )

                PreferenceSeparator()
                PreferenceCategory("Toggles")

                SwitchPrefence(
                    title = "Favorite",
                    value = true,
                    onValueChange = { }
                )

                var fav by remember { mutableStateOf(false) }
                SwitchPrefence(
                    title = "Favorite",
                    value = fav,
                    onValueChange = { fav = it }
                )
            }
        }
    }
}

@Composable
fun PreferenceCategory(
    text: String,
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
fun PreferenceSeparator() {
    HorizontalDivider(thickness = 1.dp, modifier = Modifier.padding(bottom = 24.dp))
}

/** A titled group of preference rows shown together in one rounded container. */
@Composable
fun PreferenceGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(bottom = 24.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            content = content,
        )
    }
}

/** One row of a [PreferenceGroup]: optional leading icon, title, a value line and a trailing slot. */
@Composable
fun PreferenceItem(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchPrefence(
    title: String,
    subtitle: String? = null,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
) {
    PreferenceItem(
        title = title,
        subtitle = subtitle ?: if (value) "On" else "Off",
        icon = icon,
        subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = { onValueChange(!value) },
        trailing = {
            Switch(
                checked = value,
                onCheckedChange = { onValueChange(it) },
                thumbContent = {
                    Icon(
                        imageVector = if (value) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}
