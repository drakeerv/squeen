package com.drakeerv.squeen.ui.screens.auth

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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

data class SignUpUiState(
    val loading: Boolean = false,
    val user: User? = null,
    val error: String? = null,
)

class SignUpViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    fun register(
        firstName: String,
        lastName: String,
        email: String,
        internationalCode: String,
        areaCode: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String,
        optOutSms: Boolean,
        optOutEmail: Boolean,
    ) {
        val error = when {
            firstName.isBlank() -> "Enter your first name"
            lastName.isBlank() -> "Enter your last name"
            email.isBlank() || !email.contains("@") -> "Enter a valid email address"
            password.length < 6 -> "Password must be at least 6 characters"
            password != confirmPassword -> "Passwords do not match"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.setLoginMode("email")
            val result = repository.register(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                email = email.trim(),
                password = password,
                internationalCode = internationalCode.trim().takeIf { phoneNumber.isNotBlank() },
                areaCode = areaCode.trim().takeIf { phoneNumber.isNotBlank() },
                phoneNumber = phoneNumber.trim().takeIf { it.isNotBlank() },
                optOutSms = optOutSms,
                optOutEmail = optOutEmail,
            )
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, user = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onSignedUp: (unverified: Boolean) -> Unit,
    viewModel: SignUpViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var internationalCode by remember { mutableStateOf("1") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var optOutSms by remember { mutableStateOf(false) }
    var optOutEmail by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.user) {
        state.user?.let { user ->
            onSignedUp(!user.isEmailVerified)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Sign Up") },
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
        ) {
            Text("Create your account", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PhoneNumberField(
                countryCode = internationalCode,
                onCountryCodeChange = { internationalCode = it },
                number = phoneNumber,
                onNumberChange = { phoneNumber = it },
                optional = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
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
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = optOutEmail, onCheckedChange = { optOutEmail = it })
                Text("Don't send me promotional emails", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = optOutSms, onCheckedChange = { optOutSms = it })
                Text("Don't send me promotional texts", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val phone = splitPhone(internationalCode, phoneNumber)
                    viewModel.register(
                        firstName, lastName, email, phone.internationalCode, phone.areaCode, phone.contactNumber,
                        password, confirmPassword, optOutSms, optOutEmail,
                    )
                },
                enabled = !state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create account")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Already have an account?", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.TextButton(onClick = onBack) { Text("Log in") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
