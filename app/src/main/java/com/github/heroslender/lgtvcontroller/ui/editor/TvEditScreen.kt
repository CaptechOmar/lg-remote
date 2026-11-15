package com.github.heroslender.lgtvcontroller.ui.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info as InfoOutlined
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import com.github.heroslender.lgtvcontroller.ui.preferences.PreferenceGroup
import com.github.heroslender.lgtvcontroller.ui.preferences.PreferenceItem
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.github.heroslender.lgtvcontroller.ui.preferences.PreferenceSheet
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import com.github.heroslender.lgtvcontroller.utils.AppLog
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.heroslender.lgtvcontroller.ControllerTopAppBar
import com.github.heroslender.lgtvcontroller.R.string
import com.github.heroslender.lgtvcontroller.device.OK_LONG_PRESS_HOLD
import com.github.heroslender.lgtvcontroller.ui.ConnectedDeviceScaffold
import com.github.heroslender.lgtvcontroller.ui.preferences.AppPreference
import com.github.heroslender.lgtvcontroller.ui.preferences.ChoicePreference
import com.github.heroslender.lgtvcontroller.ui.preferences.SwitchPrefence
import com.github.heroslender.lgtvcontroller.ui.preferences.TextPrefence
import com.github.heroslender.lgtvcontroller.ui.theme.LGTVControllerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun TvEditPreview() {
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
            TvEditBody(
                uiState = TvUiState(
                    TvDetails(
                        "gads-sdgfds-g-fdsgfdgdf-sdfsdf",
                        "LG WebOs Someting",
                        "Living Room TV",
                        true
                    ),
                    { null },
                    false
                ),
                onValueChange = {},
                modifier = Modifier
                    .padding(
                        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                        top = innerPadding.calculateTopPadding()
                    )
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvEditScreen(
    tvEditViewModel: TvEditViewModel = hiltViewModel(),
    navigateBack: () -> Unit,
    navigateToAbout: () -> Unit = {},
) {
    val textInputState by tvEditViewModel.tvTextInputState.collectAsState()

    ConnectedDeviceScaffold(
        errorFlow = tvEditViewModel.errors,
        textInputState = textInputState,
        topBar = {
            ControllerTopAppBar(
                title = stringResource(string.edit_tv_title),
                navigateUp = navigateBack,
            )
        },
    ) {
        TvEditBody(
            uiState = tvEditViewModel.tvUiState,
            onValueChange = tvEditViewModel::updateUiState,
            modesAvailable = tvEditViewModel.modesAvailable,
            pictureMode = tvEditViewModel.pictureMode,
            soundMode = tvEditViewModel.soundMode,
            onPictureMode = tvEditViewModel::changePictureMode,
            onSoundMode = tvEditViewModel::changeSoundMode,
            onOpenAbout = navigateToAbout,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
fun TvEditBody(
    uiState: TvUiState,
    onValueChange: (TvDetails) -> Unit,
    modifier: Modifier = Modifier,
    modesAvailable: Boolean = false,
    pictureMode: String? = null,
    soundMode: String? = null,
    onPictureMode: (String) -> Unit = {},
    onSoundMode: (String) -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val tvDetails = uiState.tvDetails
    val context = LocalContext.current
    var showMacHelp by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(16.dp, 8.dp)) {
        PreferenceGroup("Connection") {
            SwitchPrefence(
                title = "Auto Connect",
                subtitle = "Automatically connect to this device when the app starts",
                value = tvDetails.autoConnect,
                icon = Icons.Filled.Autorenew,
                onValueChange = { newValue ->
                    onValueChange(tvDetails.copy(autoConnect = newValue))
                }
            )

            TextPrefence(
                title = stringResource(string.tv_mac_address),
                value = tvDetails.macAddress,
                icon = Icons.Filled.SettingsEthernet,
                inputValidation = uiState.macAddressValidator,
                onValueChange = { newValue ->
                    onValueChange(tvDetails.copy(macAddress = newValue.trim()))
                },
                trailing = {
                    IconButton(onClick = { showMacHelp = true }) {
                        Icon(
                            imageVector = Icons.Outlined.InfoOutlined,
                            contentDescription = "How to find the MAC address",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )

            if (showMacHelp) {
                PreferenceSheet(
                    title = "Finding the MAC address",
                    onDismiss = { showMacHelp = false },
                ) { close ->
                    Text(
                        text = "The MAC address lets the app wake the TV from standby. On the TV:\n\n" +
                            "1. Press the Settings button and open All Settings.\n" +
                            "2. Go to General, then Devices (or Network).\n" +
                            "3. Open TV Management (or Network Status) and pick your connection, Wi-Fi or Ethernet.\n" +
                            "4. Look for \"MAC address\" and type it in here, for example A8:23:FE:12:34:56.\n\n" +
                            "Use the address of the connection the TV uses (Wi-Fi or wired). The names of the menus differ a little between webOS versions, and your router's list of connected devices shows it too.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    TextButton(
                        onClick = close,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Got it") }
                }
            }

            TextPrefence(
                title = "TV IP address (for reliable connection)",
                value = tvDetails.ipAddress,
                icon = Icons.Filled.Router,
                inputValidation = uiState.ipAddressValidator,
                onValueChange = { newValue ->
                    onValueChange(tvDetails.copy(ipAddress = newValue.trim()))
                }
            )
        }

        PreferenceGroup("Remapping") {
            AppPreference(
                title = stringResource(string.tv_home_button_app),
                value = tvDetails.homeAppId,
                apps = uiState.apps,
                icon = Icons.Filled.Home,
                onValueChange = { newValue ->
                    onValueChange(tvDetails.copy(homeAppId = newValue))
                }
            )

            ChoicePreference(
                title = stringResource(string.tv_ok_long_press),
                value = tvDetails.okLongPressAction,
                options = OkLongPressOptions,
                icon = Icons.Filled.TouchApp,
                onValueChange = { newValue ->
                    onValueChange(tvDetails.copy(okLongPressAction = newValue))
                }
            )

            SwitchPrefence(
                title = "Brightness buttons",
                subtitle = "Show brightness buttons instead of the channel buttons on the remote",
                value = tvDetails.brightnessControls,
                icon = Icons.Filled.BrightnessHigh,
                onValueChange = { newValue ->
                    onValueChange(tvDetails.copy(brightnessControls = newValue))
                }
            )

            SwitchPrefence(
                title = "Number pad",
                subtitle = "Replace the Exit button with a 123 button that opens a number pad",
                value = tvDetails.numberPad,
                icon = Icons.Filled.Dialpad,
                onValueChange = { newValue ->
                    onValueChange(tvDetails.copy(numberPad = newValue))
                }
            )
        }

        PreferenceGroup("TV settings") {
            if (modesAvailable) {
                // These change the TV right away, they are not saved in the app
                ChoicePreference(
                    title = "Picture mode",
                    value = pictureMode ?: "",
                    options = PictureModes,
                    icon = Icons.Filled.Image,
                    onValueChange = onPictureMode,
                )

                ChoicePreference(
                    title = "Sound mode",
                    value = soundMode ?: "",
                    options = SoundModes,
                    icon = Icons.Filled.MusicNote,
                    onValueChange = onSoundMode,
                )
            }

            TextPrefence(
                title = "Display name",
                value = tvDetails.tvDisplayName,
                icon = Icons.Filled.Edit,
                inputValidation = uiState.displayNameValidator,
                onValueChange = { newValue ->
                    onValueChange(tvDetails.copy(tvDisplayName = newValue.trim()))
                }
            )
        }

        PreferenceGroup("About") {
            TextPrefence(
                title = "TV name",
                value = tvDetails.tvName,
                icon = Icons.Filled.Tv,
            )

            TextPrefence(
                title = "TV ID",
                value = tvDetails.tvId,
                icon = Icons.Filled.Tag,
            )

            PreferenceItem(
                title = "About app",
                subtitle = "Version, source code, credits and more",
                icon = Icons.Filled.Info,
                subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onOpenAbout,
            )
        }

        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "LG Remote connection log")
                    putExtra(Intent.EXTRA_TEXT, AppLog.dump())
                }
                context.startActivity(Intent.createChooser(send, "Share connection log"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Share connection log")
        }
    }
}


/** Picture modes of recent LG TVs (the TV doesn't list them). Pairs of (webOS value, label). */
private val PictureModes = listOf(
    "vivid" to "Vivid",
    "standard" to "Standard",
    "eco" to "Eco",
    "cinema" to "Cinema",
    "expert1" to "Expert (Bright Room)",
    "expert2" to "Expert (Dark Room)",
    "game" to "Game Optimizer",
    "filmMaker" to "Filmmaker Mode",
    "sports" to "Sports",
)

private val SoundModes = listOf(
    "standard" to "Standard",
    "movie" to "Movie",
    "music" to "Music",
    "sports" to "Sports",
    "game" to "Game",
    "news" to "News",
    "aiSoundPlus" to "AI Sound+",
)

/**
 * What a long press on OK can do. The TV can't be sent a "held" key, so a long press sends a
 * different key instead. The first value is the name of a DeviceControllerButton.
 */
private val OkLongPressOptions = listOf(
    "" to "Normal OK (default)",
    "MENU" to "Menu",
    "INFO" to "Info",
    "QMENU" to "Quick settings",
    "BACK" to "Back",
    "EXIT" to "Exit",
    "HOME" to "Home",
    "RED" to "Red",
    "GREEN" to "Green",
    "YELLOW" to "Yellow",
    "BLUE" to "Blue",
    OK_LONG_PRESS_HOLD to "Hold the pointer button (experimental)",
)
