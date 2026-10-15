package com.drakeerv.squeen.ui.screens.auth

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class VerifyEmailUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val error: String? = null,
    val verified: Boolean = false,
    val codeSent: Boolean = false,
    val busy: Boolean = false,
)

class VerifyEmailViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VerifyEmailUiState())
    val state: StateFlow<VerifyEmailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val result = repository.getUser()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, user = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    /** true when the email (not the phone) is the outstanding verification. */
    fun isEmailMode(): Boolean {
        val user = _state.value.user ?: return true
        return !user.isEmailVerified || user.phoneNumbers.isEmpty()
    }

    fun verifyEmail(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.verifyEmail(code)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, verified = true) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun resendEmail() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.resendVerifyEmail()) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, codeSent = true) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun sendPhoneCode() {
        val phone = _state.value.user?.phoneNumbers?.firstOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.requestPhoneVerification(phone.internationalCode.orEmpty(), phone.compact)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, codeSent = true) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun verifyPhone(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.verifyPhoneNumber(code)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, verified = true) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.logoutRemote() }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@Composable
fun VerifyEmailScreen(
    onVerified: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: VerifyEmailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.codeSent) {
        if (state.codeSent) {
            snackbar.showSnackbar("Verification code sent")
        }
    }

    if (state.loading) {
        LoadingState()
        return
    }

    val user = state.user
    val emailMode = viewModel.isEmailMode()

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            if (emailMode) "Verify your email" else "Verify your phone",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (emailMode) {
                "We sent a verification code to ${user?.email.orEmpty()}. Enter it below to continue."
            } else {
                "We sent a text message to ${user?.phoneNumbers?.firstOrNull()?.formatted.orEmpty()}. Enter the code below to continue."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(8) },
            label = { Text("Verification code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { if (emailMode) viewModel.verifyEmail(code) else viewModel.verifyPhone(code) },
            enabled = code.length >= 4 && !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("Verify")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                if (emailMode) viewModel.resendEmail() else viewModel.sendPhoneCode()
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Resend code") }
        Spacer(Modifier.height(24.dp))
        TextButton(
            onClick = {
                viewModel.signOut()
                onSignOut()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign out") }
    }
}
