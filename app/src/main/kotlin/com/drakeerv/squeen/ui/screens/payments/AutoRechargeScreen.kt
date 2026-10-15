package com.drakeerv.squeen.ui.screens.payments

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class AutoRechargeUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val amount: Int = 0,
    val minimum: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class AutoRechargeViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AutoRechargeUiState())
    val state: StateFlow<AutoRechargeUiState> = _state.asStateFlow()

    fun load(accountId: String, geoBoundaryId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.replenishmentDefaults(accountId, geoBoundaryId)) {
                is ApiResult.Success -> {
                    val defaults = result.data
                    _state.update {
                        it.copy(
                            loading = false,
                            enabled = defaults?.autoReplenishmentEnabled ?: false,
                            amount = defaults?.replenishmentValueAmount ?: 0,
                            minimum = defaults?.replenishmentMinimumValueAmount ?: 0,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) = _state.update { it.copy(enabled = enabled) }

    fun setAmount(text: String) {
        val amount = text.filter(Char::isDigit).take(4).toIntOrNull() ?: 0
        _state.update { it.copy(amount = amount) }
    }

    fun save(accountId: String, geoBoundaryId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.setReplenishmentDefaults(accountId, _state.value.amount, _state.value.enabled, geoBoundaryId)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, saved = true) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRechargeScreen(
    accountId: String,
    geoBoundaryId: String,
    onBack: () -> Unit,
    viewModel: AutoRechargeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    var amountText by remember { mutableStateOf("") }

    LaunchedEffect(accountId, geoBoundaryId) {
        viewModel.load(accountId, geoBoundaryId)
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar("Auto recharge settings saved")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Auto Recharge") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) { LoadingState() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Automatic credit purchase", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Automatically add funds to your account when your balance is low.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable auto recharge", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Charge your default card when the balance drops below the minimum",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.enabled, onCheckedChange = { viewModel.setEnabled(it) })
                }
            }

            if (state.enabled) {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = if (amountText.isEmpty()) state.amount.toString() else amountText,
                            onValueChange = {
                                amountText = it
                                viewModel.setAmount(it)
                            },
                            label = { Text("Recharge amount ($)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.minimum > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Recharges when your balance is less than $${state.minimum}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.save(accountId, geoBoundaryId) },
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        }
    }
}
