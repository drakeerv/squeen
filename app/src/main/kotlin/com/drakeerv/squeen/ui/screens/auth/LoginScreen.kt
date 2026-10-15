package com.drakeerv.squeen.ui.screens.auth

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import com.drakeerv.squeen.ui.components.PhoneNumberField
import com.drakeerv.squeen.ui.components.splitPhone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class LoginUiState(
    val loading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
)

class LoginViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun login(
        useEmail: Boolean,
        email: String,
        internationalCode: String,
        areaCode: String,
        phoneNumber: String,
        password: String,
    ) {
        val error = when {
            useEmail && email.isBlank() -> "Enter your email address"
            useEmail && !email.contains("@") -> "Enter a valid email address"
            !useEmail && phoneNumber.isBlank() -> "Enter your phone number"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.setLoginMode(if (useEmail) "email" else "phone")
            val result = repository.login(
                email = email.trim().takeIf { useEmail },
                internationalCode = internationalCode.trim().takeIf { !useEmail },
                areaCode = areaCode.trim().takeIf { !useEmail },
                phoneNumber = phoneNumber.trim().takeIf { !useEmail },
                password = password,
            )
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, user = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun needsVerification(user: User): Boolean =
        !user.isEmailVerified || (user.phoneNumbers.isNotEmpty() && !user.hasVerifiedPhoneNumber)
}

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    onVerifyEmail: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current

    var useEmail by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var internationalCode by remember { mutableStateOf("1") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(state.user) {
        state.user?.let { user ->
            if (viewModel.needsVerification(user)) onVerifyEmail() else onSignedIn()
        }
    }
    androidx.compose.runtime.LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in to start your laundry",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = useEmail,
                onClick = { useEmail = true },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Email") }
            SegmentedButton(
                selected = !useEmail,
                onClick = { useEmail = false },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Phone") }
        }
        Spacer(Modifier.height(16.dp))

        if (useEmail) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PhoneNumberField(
                countryCode = internationalCode,
                onCountryCodeChange = { internationalCode = it },
                number = phoneNumber,
                onNumberChange = { phoneNumber = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val phone = splitPhone(internationalCode, phoneNumber)
                viewModel.login(useEmail, email, phone.internationalCode, phone.areaCode, phone.contactNumber, password)
            },
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("Log In")
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onForgotPassword) { Text("Forgot password?") }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("Don't have an account?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSignUp) { Text("Sign up") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
