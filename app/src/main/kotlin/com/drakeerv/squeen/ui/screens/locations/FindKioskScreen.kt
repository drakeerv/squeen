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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class FindKioskUiState(
    val accountId: String? = null,
    val geoBoundaryId: String? = null,
)

class FindKioskViewModel(
    private val repository: com.drakeerv.squeen.data.SpeedQueenRepository,
) : androidx.lifecycle.ViewModel() {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(FindKioskUiState())
    val state: kotlinx.coroutines.flow.StateFlow<FindKioskUiState> = _state

    init {
        viewModelScope.launch {
            val user = (repository.getUser() as? com.drakeerv.squeen.data.ApiResult.Success)?.data
            val account = user?.accounts?.firstOrNull()
            _state.value = FindKioskUiState(
                accountId = account?.id,
                geoBoundaryId = account?.subAccounts?.firstOrNull()?.geoBoundaryId,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindKioskScreen(
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onAddFunds: (accountId: String, geoBoundaryId: String) -> Unit = { _, _ -> },
    viewModel: FindKioskViewModel = org.koin.androidx.compose.koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Add funds to your account",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            // ---- saved card first: most people already have a card on file
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Pay with a saved card", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Top up instantly with a card that is already on your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val accountId = state.accountId
                            if (accountId != null) onAddFunds(accountId, state.geoBoundaryId.orEmpty())
                        },
                        enabled = state.accountId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Choose amount") }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- value station (cash) option
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Load cash at a value station", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Scan the QR code on the value station screen, then follow the instructions on the machine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onScanQr,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan value station QR")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
