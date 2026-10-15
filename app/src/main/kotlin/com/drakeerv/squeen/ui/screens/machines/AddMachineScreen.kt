package com.drakeerv.squeen.ui.screens.machines

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
import androidx.compose.ui.text.input.KeyboardType
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

data class AddMachineUiState(
    val busy: Boolean = false,
    val foundMachineId: String? = null,
    val error: String? = null,
)

class AddMachineViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddMachineUiState())
    val state: StateFlow<AddMachineUiState> = _state.asStateFlow()

    fun resolve(roomId: String, machineNumber: String?, machineId: String?) {
        if (machineNumber.isNullOrBlank() && machineId.isNullOrBlank()) {
            _state.update { it.copy(error = "Enter the machine number to get started") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = repository.roomMachineByNumber(roomId, machineNumber, machineId)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, foundMachineId = result.data.id) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = "Invalid machine number") }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMachineScreen(
    roomId: String,
    onBack: () -> Unit,
    onMachineFound: (machineId: String) -> Unit,
    onScanQr: () -> Unit,
    viewModel: AddMachineViewModel = koinViewModel(),
    scannedMachineNumber: String? = null,
    scannedMachineId: String? = null,
    onScannedConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    var machineNumber by remember { mutableStateOf("") }

    LaunchedEffect(state.foundMachineId) {
        state.foundMachineId?.let(onMachineFound)
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(scannedMachineNumber, scannedMachineId) {
        if (!scannedMachineNumber.isNullOrBlank() || !scannedMachineId.isNullOrBlank()) {
            viewModel.resolve(roomId, scannedMachineNumber, scannedMachineId)
            onScannedConsumed()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Add Machine") },
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
            Text("Add the machine you wish to use", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter the machine number to get started, or scan the QR code on the machine.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = machineNumber,
                onValueChange = { machineNumber = it.filter(Char::isDigit).take(6) },
                label = { Text("Machine number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.resolve(roomId, machineNumber, null) },
                enabled = !state.busy && machineNumber.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Find machine")
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
                Spacer(Modifier.width(8.dp))
                Text("Scan machine QR")
            }
        }
    }
}
