package com.github.heroslender.lgtvcontroller.ui.editor

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.heroslender.lgtvcontroller.DeviceManager
import com.github.heroslender.lgtvcontroller.domain.model.App
import com.github.heroslender.lgtvcontroller.domain.model.Tv
import com.github.heroslender.lgtvcontroller.storage.TvRepository
import com.github.heroslender.lgtvcontroller.ui.BaseViewModel
import com.github.heroslender.lgtvcontroller.ui.navigation.NavDest
import com.github.heroslender.lgtvcontroller.ui.preferences.InputValidator
import com.github.heroslender.lgtvcontroller.utils.WakeOnLan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvEditViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val tvRepository: TvRepository,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel(deviceManager) {
    var tvUiState by mutableStateOf(
        TvUiState(
            tvDetails = TvDetails(tvId = "Loading...", tvName = "Loading..."),
            displayNameValidator = this::validateDisplayName,
            macAddressValidator = this::validateMacAddress
        )
    )
        private set

    /**
     * The tv as stored when the screen opened. The editor only changes a few of its fields, the
     * rest (apps and inputs loaded from the tv) must be written back untouched.
     */
    private var loadedTv: Tv? = null

    init {
        viewModelScope.launch {
            val id: String = checkNotNull(savedStateHandle[NavDest.EditDevice.tvIdArg])
            val tv = tvRepository.getTvStream(id).first()
            loadedTv = tv
            val details = TvDetails(
                tvId = tv.id,
                tvName = tv.name,
                tvDisplayName = tv.displayName ?: tv.name,
                autoConnect = tv.autoConnect,
                macAddress = tv.macAddress ?: "",
                homeAppId = tv.homeAppId ?: ""
            )
            tvUiState = tvUiState.copy(
                tvDetails = details,
                isValid = validateInput(details),
                apps = tv.apps.sortedBy { it.name.lowercase() }
            )
        }
    }

    fun updateUiState(uiState: TvDetails) {
        val oldState = tvUiState
        tvUiState = tvUiState.copy(tvDetails = uiState, isValid = validateInput(uiState))

        if (tvUiState.isValid && oldState != tvUiState) {
            Log.d("TvEditViewModel", "Updating TV details: $tvUiState")
            save()
        }
    }

    fun validateInput(uiState: TvDetails = tvUiState.tvDetails): Boolean {
        return uiState.tvId != "Loading..." && validateMacAddress(uiState.macAddress) == null
    }

    fun validateMacAddress(macAddress: String): String? {
        // The MAC address is optional, it's only needed for Wake-on-LAN
        return if (macAddress.isBlank() || WakeOnLan.normalizeMac(macAddress) != null) {
            null
        } else {
            "Invalid MAC address, expected format: AA:BB:CC:DD:EE:FF"
        }
    }

    fun validateDisplayName(displayName: String): String? {
        return if (displayName.isBlank()) {
            "Display name cannot be blank"
        } else {
            null
        }
    }

    fun save() {
        if (validateInput()) {
            val tv = tvUiState.tvDetails
                // Remove trailing whitespaces added sometimes with autocomplete
                .run { copy(tvDisplayName = tvDisplayName.trim()) }
                .toTv()
                .let { edited ->
                    // Keep what the editor doesn't know about, otherwise saving would erase the
                    // apps and inputs that were loaded from the tv.
                    loadedTv?.let { edited.copy(apps = it.apps, inputs = it.inputs) } ?: edited
                }

            deviceManager.updateTv(tv)
        }
    }
}

data class TvUiState(
    val tvDetails: TvDetails = TvDetails(),
    val displayNameValidator: InputValidator,
    val isValid: Boolean = false,
    val macAddressValidator: InputValidator = { null },
    val apps: List<App> = emptyList(),
)

data class TvDetails(
    val tvId: String = "",
    val tvName: String = "",
    val tvDisplayName: String = "",
    val autoConnect: Boolean = false,
    val macAddress: String = "",
    val homeAppId: String = "",
)

fun TvDetails.toTv(): Tv = Tv(
    id = tvId,
    name = tvName,
    displayName = tvDisplayName,
    autoConnect = autoConnect,
    macAddress = WakeOnLan.normalizeMac(macAddress),
    homeAppId = homeAppId.trim().ifEmpty { null }
)