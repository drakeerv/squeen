package com.drakeerv.squeen.ui.screens.payments

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.PaymentMethod
import com.drakeerv.squeen.ui.components.EmptyState
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class PaymentMethodsUiState(
    val loading: Boolean = true,
    val methods: List<PaymentMethod> = emptyList(),
    val geoBoundaryId: String? = null,
    val error: String? = null,
)

class PaymentMethodsViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentMethodsUiState())
    val state: StateFlow<PaymentMethodsUiState> = _state.asStateFlow()

    fun load(accountId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val geoBoundaryId = (repository.getUser() as? ApiResult.Success)
                ?.data?.customerGeoBoundaries?.firstOrNull()?.id
            when (val result = repository.paymentMethods(accountId)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, methods = result.data, geoBoundaryId = geoBoundaryId) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message, geoBoundaryId = geoBoundaryId) }
            }
        }
    }

    fun setDefault(accountId: String, methodId: String) {
        viewModelScope.launch {
            when (val result = repository.setDefaultPaymentMethod(methodId, true)) {
                is ApiResult.Success -> load(accountId)
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun remove(accountId: String, methodId: String) {
        viewModelScope.launch {
            when (val result = repository.removePaymentMethod(methodId)) {
                is ApiResult.Success -> load(accountId)
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    accountId: String,
    onBack: () -> Unit,
    onAddCard: (geoBoundaryId: String) -> Unit = {},
    viewModel: PaymentMethodsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current

    LaunchedEffect(accountId) { viewModel.load(accountId) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Payment Methods") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { onAddCard(state.geoBoundaryId.orEmpty()) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add card") },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.methods.isEmpty() -> EmptyState(
                    title = "No payment methods",
                    message = "Add a credit or debit card to pay for your laundry.",
                )
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.methods, key = { it.id }) { method ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(method.display, style = MaterialTheme.typography.titleMedium)
                                    val exp = listOfNotNull(method.expMonth, method.expYear).joinToString("/")
                                    if (exp.isNotBlank()) {
                                        Text(
                                            "Expires $exp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.setDefault(accountId, method.id) }) {
                                    Icon(
                                        if (method.isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Set default",
                                        tint = if (method.isDefault) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.remove(accountId, method.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove card")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
