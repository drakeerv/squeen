package com.drakeerv.squeen.ui.screens.promotions

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.CampaignPromotion
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class PromotionsUiState(
    val busy: Boolean = false,
    val promotion: CampaignPromotion? = null,
    val error: String? = null,
)

class PromotionsViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PromotionsUiState())
    val state: StateFlow<PromotionsUiState> = _state.asStateFlow()

    fun redeem(code: String) {
        if (code.isBlank()) {
            _state.update { it.copy(error = "Enter a promotion code") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = repository.redeemPromoCode(code.trim(), null)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, promotion = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun dismiss() = _state.update { it.copy(promotion = null) }
    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionsScreen(
    onBack: () -> Unit,
    viewModel: PromotionsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Promotions") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.CardGiftcard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Have a promotion code?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter your code below to claim rewards or credit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(24) },
                label = { Text("Promotion code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.redeem(code) },
                enabled = !state.busy && code.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Redeem")
                }
            }
        }
    }

    state.promotion?.let { promotion ->
        AlertDialog(
            onDismissRequest = { viewModel.dismiss() },
            title = { Text("Promotion applied") },
            text = {
                Text(
                    when {
                        promotion.promotionTypeLookupKey?.contains("REWARD", ignoreCase = true) == true ->
                            "${promotion.promotionValue ?: 0} reward points were added to your account."
                        promotion.promotionValue != null ->
                            "$${promotion.promotionValue} in credit was added to your account."
                        else -> "Your promotion code was applied."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismiss()
                    code = ""
                }) { Text("Done") }
            },
        )
    }
}
