package com.drakeerv.squeen.ui.screens.locations

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.net.MachineStatusSocket
import com.drakeerv.squeen.data.jsonapi.primitiveContent
import com.drakeerv.squeen.data.jsonapi.safeObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class KioskDepositUiState(
    val creating: Boolean = true,
    val sessionActive: Boolean = false,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val error: String? = null,
    val secondsLeft: Int = 180,
)

class KioskDepositViewModel(
    private val repository: SpeedQueenRepository,
    private val socket: MachineStatusSocket,
) : ViewModel() {

    private val _state = MutableStateFlow(KioskDepositUiState())
    val state: StateFlow<KioskDepositUiState> = _state.asStateFlow()

    private var sessionId: String? = null

    fun start(vtmId: String, roomId: String?) {
        if (vtmId.isBlank()) {
            _state.update { it.copy(creating = false, failed = true, error = "This value station is not available.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            socket.connect(roomId)
            when (val result = repository.vtmSession(vtmId, roomId.orEmpty(), socket.currentConnectionToken)) {
                is ApiResult.Success -> {
                    val id = findSessionId(result.data?.toString())
                    if (id == null) {
                        _state.update { it.copy(creating = false, failed = true, error = "The value station did not start a session.") }
                    } else {
                        sessionId = id
                        _state.update { it.copy(creating = false, sessionActive = true, secondsLeft = 180) }
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(creating = false, failed = true, error = result.message) }
            }
        }
    }

    private fun findSessionId(raw: String?): String? {
        val json = raw?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).safeObject() }.getOrNull() } ?: return null
        return json["sessionId"].primitiveContent()
    }

    fun observeSocket() {
        viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    is MachineStatusSocket.Event.KioskSessionStatus -> {
                        if (sessionId == null || event.sessionId == sessionId) {
                            when (event.status.uppercase()) {
                                "COMPLETED" -> _state.update { it.copy(completed = true, sessionActive = false) }
                                "FAILED", "EXPIRED", "CANCELLED" -> _state.update {
                                    it.copy(failed = true, sessionActive = false, error = "The deposit was not completed.")
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun startTimeoutCountdown() {
        viewModelScope.launch {
            while (_state.value.sessionActive && _state.value.secondsLeft > 0) {
                delay(1000)
                _state.update { it.copy(secondsLeft = it.secondsLeft - 1) }
            }
            if (_state.value.sessionActive) {
                _state.update { it.copy(sessionActive = false, failed = true, error = "The deposit session expired. Please try again.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskDepositScreen(
    vtmId: String,
    roomId: String?,
    onDone: () -> Unit,
    viewModel: KioskDepositViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.observeSocket()
        viewModel.start(vtmId, roomId)
        viewModel.startTimeoutCountdown()
    }

    Scaffold(
        topBar = { AppTopBar(title = { Text("Load Funds") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                state.creating -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting to the value station…", textAlign = TextAlign.Center)
                }
                state.completed -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Funds added", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Your deposit was completed. Your balance may take a moment to update.", textAlign = TextAlign.Center)
                }
                state.failed -> {
                    Icon(
                        Icons.Filled.PointOfSale,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Deposit not completed", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(state.error.orEmpty(), textAlign = TextAlign.Center)
                }
                else -> {
                    Icon(
                        Icons.Filled.PointOfSale,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Finish your deposit", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Follow the instructions on the value station to complete your deposit. This screen will update automatically.",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("${state.secondsLeft}s", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.completed) "Done" else "Close")
            }
        }
    }
}
