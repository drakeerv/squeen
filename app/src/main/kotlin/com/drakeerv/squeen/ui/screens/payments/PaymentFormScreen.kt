package com.drakeerv.squeen.ui.screens.payments

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.MessageState
import com.drakeerv.squeen.data.net.MachineStatusSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class PaymentFormUiState(
    val loading: Boolean = true,
    val html: String? = null,
    val error: String? = null,
)

class PaymentFormViewModel(
    private val repository: SpeedQueenRepository,
    private val socket: MachineStatusSocket,
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentFormUiState())
    val state: StateFlow<PaymentFormUiState> = _state.asStateFlow()

    fun load(accountId: String, geoBoundaryId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.paymentsForm(accountId, geoBoundaryId, connectionToken = socket.currentConnectionToken)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, html = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }
}

/**
 * Hosted card-entry form for payment gateways without a native integration.
 * The form posts back to the API and the result is confirmed server side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentFormScreen(
    accountId: String,
    geoBoundaryId: String,
    onBack: () -> Unit,
    viewModel: PaymentFormViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(accountId, geoBoundaryId) { viewModel.load(accountId, geoBoundaryId) }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Add Credit or Debit Card") },
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
                state.error != null -> MessageState(
                    icon = Icons.Filled.ErrorOutline,
                    title = "We couldn't open the payment form",
                    message = state.error,
                    actionLabel = "Try again",
                    onAction = { viewModel.load(accountId, geoBoundaryId) },
                )
                state.html != null -> {
                    val html = state.html.orEmpty()
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                loadDataWithBaseURL("https://api.alliancelslabs.com/", html, "text/html", "utf-8", null)
                                webView = this
                            }
                        },
                    )
                }
            }
        }
    }
}
