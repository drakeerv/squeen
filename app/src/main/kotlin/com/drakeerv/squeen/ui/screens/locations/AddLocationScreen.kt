package com.drakeerv.squeen.ui.screens.locations

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class AddLocationUiState(
    val busy: Boolean = false,
    val locatedBoundaryId: String? = null,
    val error: String? = null,
)

class AddLocationViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddLocationUiState())
    val state: StateFlow<AddLocationUiState> = _state.asStateFlow()

    fun submitPin(pin: String) {
        if (pin.trim().length < 4) {
            _state.update { it.copy(error = "Enter the location PIN") }
            return
        }
        lookup { repository.locationContextByLocationNumber(pin.trim()) }
    }

    /** Resolves a scanned value-centre id the same way the QR flow does. */
    fun submitValueCenterId(valueCenterId: String) {
        lookup { repository.locationContextByValueCenterId(valueCenterId) }
    }

    private fun lookup(block: suspend () -> ApiResult<SpeedQueenRepository.LocationContext>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = block()) {
                is ApiResult.Success -> {
                    val boundaryId = result.data.boundary?.id
                    if (boundaryId.isNullOrBlank()) {
                        _state.update { it.copy(busy = false, error = "Invalid location PIN. Please try again") }
                        return@launch
                    }
                    val user = repository.getUser()
                    val known = (user as? ApiResult.Success)?.data?.customerGeoBoundaries?.any { it.id == boundaryId } == true
                    if (known) {
                        _state.update { it.copy(busy = false, locatedBoundaryId = boundaryId) }
                    } else {
                        when (val added = repository.addCustomerGeoBoundary(boundaryId, qrCode = null)) {
                            is ApiResult.Success -> {
                                repository.getUser()
                                _state.update { it.copy(busy = false, locatedBoundaryId = boundaryId) }
                            }
                            is ApiResult.Failure -> _state.update { it.copy(busy = false, error = added.message) }
                        }
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationScreen(
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onLocated: (geoBoundaryId: String) -> Unit,
    scannedLocationNumber: String? = null,
    scannedValueCenterId: String? = null,
    onScannedConsumed: () -> Unit = {},
    viewModel: AddLocationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(state.locatedBoundaryId) {
        state.locatedBoundaryId?.let(onLocated)
    }
    LaunchedEffect(scannedLocationNumber) {
        scannedLocationNumber?.takeIf { it.isNotBlank() }?.let {
            viewModel.submitPin(it)
            onScannedConsumed()
        }
    }
    LaunchedEffect(scannedValueCenterId) {
        scannedValueCenterId?.takeIf { it.isNotBlank() }?.let {
            viewModel.submitValueCenterId(it)
            onScannedConsumed()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Add Location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
        ) {
            Text("Add Location Pin", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tell us where you are by entering your six character Location PIN. It can be found on stickers and posters in the laundry room.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.uppercase().take(8) },
                label = { Text("Location PIN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.submitPin(pin) },
                enabled = !state.busy && pin.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add location")
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onScanQr,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                Text("Scan QR code")            }
        }
    }
}
