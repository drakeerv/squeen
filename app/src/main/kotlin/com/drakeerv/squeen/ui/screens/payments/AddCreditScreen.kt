package com.drakeerv.squeen.ui.screens.payments

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.drakeerv.squeen.data.model.PaymentMethod
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class AddCreditUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val methods: List<PaymentMethod> = emptyList(),
    val selectedMethodId: String? = null,
    val busy: Boolean = false,
    val amount: Int = 0,
    val cvv: String = "",
    val needsCvv: Boolean = false,
    val error: String? = null,
    val successAmountCents: Int? = null,
)

class AddCreditViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddCreditUiState())
    val state: StateFlow<AddCreditUiState> = _state.asStateFlow()

    fun load(accountId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val user = (repository.getUser() as? ApiResult.Success)?.data
            val methods = (repository.paymentMethods(accountId) as? ApiResult.Success)?.data.orEmpty()
            _state.update {
                it.copy(
                    loading = false,
                    user = user,
                    methods = methods,
                    selectedMethodId = methods.firstOrNull { m -> m.isDefault }?.id ?: methods.firstOrNull()?.id,
                )
            }
        }
    }

    fun setAmount(major: Int) {
        _state.update { it.copy(amount = major * 100) }
    }

    fun setCustomAmount(text: String) {
        val major = text.filter(Char::isDigit).take(4).toIntOrNull() ?: 0
        _state.update { it.copy(amount = major * 100) }
    }

    fun selectMethod(id: String) {
        _state.update { it.copy(selectedMethodId = id) }
    }

    /**
     * Adds credit through the stored payment method. For gateways that require a
     * hosted form the caller is expected to open the generic payment web view.
     */
    fun addCredit(accountId: String, geoBoundaryId: String) {
        val state = _state.value
        if (state.amount <= 0) {
            _state.update { it.copy(error = "Choose an amount to add") }
            return
        }
        val methodId = state.selectedMethodId
        if (methodId == null) {
            _state.update { it.copy(error = "Add a payment method first") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val sub = state.user?.accounts?.firstOrNull { it.id == accountId }
                ?.subAccounts?.firstOrNull { it.geoBoundaryId == geoBoundaryId }
                ?: state.user?.accounts?.firstOrNull()?.subAccounts?.firstOrNull()
            val result = repository.addCredit(
                accountId = accountId,
                amountInCents = state.amount,
                paymentMethodId = methodId,
                geoBoundaryId = geoBoundaryId,
                description = "add credit",
                cvv = state.cvv.takeIf { it.isNotBlank() },
            )
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, successAmountCents = state.amount) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
    fun dismissSuccess() = _state.update { it.copy(successAmountCents = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCreditScreen(
    accountId: String,
    geoBoundaryId: String,
    onBack: () -> Unit,
    onAutoRecharge: () -> Unit,
    viewModel: AddCreditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    var customAmount by remember { mutableStateOf("") }

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
                title = { Text("Add Funds") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                com.drakeerv.squeen.ui.components.LoadingState()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add Credit To Account", style = MaterialTheme.typography.headlineSmall)

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Amount", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val amounts = listOf(5, 10, 20, 50, 100)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        amounts.forEach { value ->
                            FilterChip(
                                selected = state.amount == value * 100,
                                onClick = { viewModel.setAmount(value); customAmount = "" },
                                label = { Text("$$value") },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customAmount,
                        onValueChange = { customAmount = it.filter(Char::isDigit).take(4) },
                        label = { Text("Custom amount ($)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { viewModel.setCustomAmount(customAmount) }) { Text("Use this amount") }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Payment method", style = MaterialTheme.typography.titleSmall)
                    if (state.methods.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No saved payment methods. Add a card to continue.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.methods.forEach { method ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.selectedMethodId == method.id,
                                    onClick = { viewModel.selectMethod(method.id) },
                                )
                                Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(method.display, style = MaterialTheme.typography.bodyLarge)
                                    if (method.isDefault) {
                                        Text("Default", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.amount > 0) {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$" + "%.2f".format(state.amount / 100.0),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.addCredit(accountId, geoBoundaryId) },
                enabled = !state.busy && state.amount > 0 && state.methods.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add funds")
                }
            }

            TextButton(onClick = onAutoRecharge) { Text("Set up auto recharge") }
        }
    }

    state.successAmountCents?.let { amount ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess() },
            title = { Text("Funds added") },
            text = { Text("$" + "%.2f".format(amount / 100.0) + " was added to your account.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissSuccess()
                    onBack()
                }) { Text("Done") }
            },
        )
    }
}
