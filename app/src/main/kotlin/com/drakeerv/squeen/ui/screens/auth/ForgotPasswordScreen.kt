package com.drakeerv.squeen.ui.screens.auth

import com.drakeerv.squeen.ui.components.AppTopBar
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

data class ForgotUiState(
    val loading: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
)

class ForgotPasswordViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotUiState())
    val state: StateFlow<ForgotUiState> = _state.asStateFlow()

    fun send(useEmail: Boolean, email: String, internationalCode: String, areaCode: String, phone: String) {
        val error = when {
            useEmail && (email.isBlank() || !email.contains("@")) -> "Enter a valid email address"
            !useEmail && phone.isBlank() -> "Enter your phone number"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = repository.resetPassword(
                email = email.trim().takeIf { useEmail },
                internationalCode = internationalCode.trim().takeIf { !useEmail },
                areaCode = areaCode.trim().takeIf { !useEmail },
                phoneNumber = phone.trim().takeIf { !useEmail },
            )
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, sent = true) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current

    var email by remember { mutableStateOf("") }
    var internationalCode by remember { mutableStateOf("1") }
    var areaCode by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Reset Password") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
        ) {
            if (state.sent) {
                Text("Check your inbox", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "If an account exists for the details you entered, we've sent password reset instructions.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
                return@Column
            }

            Text("We'll send you a link to reset your password.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("or reset with your phone number", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = internationalCode,
                    onValueChange = { internationalCode = it.filter(Char::isDigit).take(3) },
                    label = { Text("+") },
                    singleLine = true,
                    modifier = Modifier.padding(end = 8.dp),
                )
                OutlinedTextField(
                    value = areaCode,
                    onValueChange = { areaCode = it.filter(Char::isDigit).take(5) },
                    label = { Text("Area") },
                    singleLine = true,
                    modifier = Modifier.padding(end = 8.dp),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(12) },
                    label = { Text("Phone") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.send(email.isNotBlank(), email, internationalCode, areaCode, phone) },
                enabled = !state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Send reset link")
                }
            }
        }
    }
}
