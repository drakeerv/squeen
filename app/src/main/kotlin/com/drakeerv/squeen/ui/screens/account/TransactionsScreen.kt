package com.drakeerv.squeen.ui.screens.account

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dry
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.Transaction
import com.drakeerv.squeen.ui.components.EmptyState
import com.drakeerv.squeen.ui.components.ErrorState
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class TransactionsUiState(
    val loading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val geoBoundaries: List<Pair<String, String>> = emptyList(),
    val selectedGeoBoundaryId: String? = null,
    val error: String? = null,
    val receiptSent: Boolean = false,
)

class TransactionsViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionsUiState())
    val state: StateFlow<TransactionsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val user = (repository.getUser() as? ApiResult.Success)?.data
            val boundaries = user?.customerGeoBoundaries?.map { it.id to it.displayName }.orEmpty()
            val selected = _state.value.selectedGeoBoundaryId
                ?: user?.customerGeoBoundaries?.firstOrNull()?.id
            when (val result = repository.transactions(selected)) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, transactions = result.data, geoBoundaries = boundaries, selectedGeoBoundaryId = selected)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.message, geoBoundaries = boundaries, selectedGeoBoundaryId = selected)
                }
            }
        }
    }

    fun selectBoundary(id: String) {
        _state.update { it.copy(selectedGeoBoundaryId = id) }
        load()
    }

    fun emailReceipt(transactionId: String) {
        viewModelScope.launch {
            when (val result = repository.emailTransaction(transactionId, java.util.TimeZone.getDefault().id)) {
                is ApiResult.Success -> _state.update { it.copy(receiptSent = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(error = "We couldn't email that receipt right now. Please try again later.")
                }
            }
        }
    }

    fun consumeReceiptSent() = _state.update { it.copy(receiptSent = false) }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    viewModel: TransactionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.receiptSent) {
        if (state.receiptSent) {
            snackbar.showSnackbar("Receipt sent to your email")
            viewModel.consumeReceiptSent()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.transactions.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load() },
                )
                else -> Column {
                    if (state.geoBoundaries.size > 1) {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.geoBoundaries.forEach { (id, name) ->
                                FilterChip(
                                    selected = id == state.selectedGeoBoundaryId,
                                    onClick = { viewModel.selectBoundary(id) },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                    if (state.transactions.isEmpty()) {
                        EmptyState(
                            title = "No transaction history yet",
                            message = "Your laundry payments will appear here.",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.transactions, key = { it.id }) { transaction ->
                                TransactionCard(
                                    transaction = transaction,
                                    onEmailReceipt = { viewModel.emailReceipt(transaction.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(transaction: Transaction, onEmailReceipt: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // type icon in a tonal circle, matching the rest of the app
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        transaction.isDryer -> Icons.Filled.Dry
                        transaction.isWasher -> Icons.Filled.LocalLaundryService
                        else -> Icons.Filled.Payments
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.description
                        ?: transaction.machineName
                        ?: if (transaction.isCredit) "Credit added" else "Laundry payment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                transaction.transactionDate?.take(10)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                val amount = transaction.amountText
                val formatted = amount?.let { com.drakeerv.squeen.ui.Money.format(it) }.orEmpty()
                val isZero = formatted.replace("$", "").toDoubleOrNull() == 0.0
                Text(
                    when {
                        formatted.isBlank() -> ""
                        isZero -> formatted
                        transaction.isCredit -> "+$formatted"
                        else -> "-$formatted"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (transaction.isCredit) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                transaction.balanceText?.let {
                    Text(
                        "Balance " + com.drakeerv.squeen.ui.Money.format(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onEmailReceipt,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Email receipt", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
