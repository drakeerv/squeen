package com.drakeerv.squeen.ui.screens.account

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import com.drakeerv.squeen.BuildConfig
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.api.PhoneRef
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.ui.components.LoadingState
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import com.drakeerv.squeen.ui.components.PhoneNumberField
import com.drakeerv.squeen.ui.components.splitPhone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ProfileUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val accountId: String? = null,
    val geoBoundaryId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deletedRequested: Boolean = false,
)

class ProfileViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.getUser()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        user = result.data,
                        accountId = result.data.accounts.firstOrNull()?.id,
                        geoBoundaryId = result.data.accounts.firstOrNull()?.subAccounts?.firstOrNull()?.geoBoundaryId,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun save(
        firstName: String,
        lastName: String,
        email: String,
        optOutSms: Boolean,
        optOutEmail: Boolean,
        internationalCode: String,
        areaCode: String,
        phone: String,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val phoneRef = if (phone.isNotBlank()) {
                PhoneRef(
                    telecomInternationalCode = internationalCode.takeIf { it.isNotBlank() },
                    telecomAreaCode = areaCode.takeIf { it.isNotBlank() },
                    telecomContactNumber = phone,
                )
            } else null
            val result = repository.updateUserProfile(
                firstName = firstName,
                lastName = lastName,
                email = email,
                optOutSms = optOutSms,
                optOutEmail = optOutEmail,
                phone = phoneRef,
                locale = java.util.Locale.getDefault().toLanguageTag(),
            )
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, user = result.data, saved = true) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { repository.logoutRemote() }
    }

    fun requestDeletion() {
        viewModelScope.launch {
            when (val result = repository.requestAccountDeletion()) {
                is ApiResult.Success -> _state.update { it.copy(deletedRequested = true) }
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
    fun consumeSaved() = _state.update { it.copy(saved = false) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onSignedOut: () -> Unit,
    showBackArrow: Boolean = true,
    onTransactions: () -> Unit = {},
    onPromotions: () -> Unit = {},
    onSettings: () -> Unit = {},
    onWebView: (String, String) -> Unit = { _, _ -> },
    onCardLink: () -> Unit = {},
    onPaymentMethods: (accountId: String) -> Unit = {},
    onAddFundsMenu: () -> Unit = {},
    insideShell: Boolean = false,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbar.showSnackbar("Profile updated")
            viewModel.consumeSaved()
        }
    }

    Scaffold(
        contentWindowInsets = if (insideShell) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },

        topBar = {
            AppTopBar(
                title = { Text("My Profile") },
                navigationIcon = {
                    if (showBackArrow) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) { LoadingState() }
            return@Scaffold
        }

        val user = state.user
        var firstName by remember(user) { mutableStateOf(user?.firstName.orEmpty()) }
        var lastName by remember(user) { mutableStateOf(user?.lastName.orEmpty()) }
        var email by remember(user) { mutableStateOf(user?.email.orEmpty()) }
        var optOutSms by remember(user) { mutableStateOf(user?.universalOptOutSms ?: false) }
        var optOutEmail by remember(user) { mutableStateOf(user?.universalOptOutEmail ?: false) }
        var internationalCode by remember(user) { mutableStateOf(user?.phoneNumbers?.firstOrNull()?.internationalCode.orEmpty()) }
        var phone by remember(user) {
            mutableStateOf(
                (user?.phoneNumbers?.firstOrNull()?.areaCode.orEmpty() + user?.phoneNumbers?.firstOrNull()?.contactNumber.orEmpty())
                    .ifBlank { "" },
            )
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
            ProfileSectionTitle("Account")
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("First name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Last name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    PhoneNumberField(
                        countryCode = internationalCode,
                        onCountryCodeChange = { internationalCode = it },
                        number = phone,
                        onNumberChange = { phone = it },
                        label = "Phone",
                        optional = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ProfileSectionTitle("Notifications")
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Promotional emails", style = MaterialTheme.typography.bodyLarge)
                            Text("Receive offers and news by email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = !optOutEmail, onCheckedChange = { optOutEmail = !it })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Promotional texts", style = MaterialTheme.typography.bodyLarge)
                            Text("Receive offers and news by SMS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = !optOutSms, onCheckedChange = { optOutSms = !it })
                    }
                }
            }

            Button(
                onClick = {
                    val split = splitPhone(internationalCode, phone)
                    viewModel.save(firstName, lastName, email, optOutSms, optOutEmail, split.internationalCode, split.areaCode, split.contactNumber)
                },
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save changes")
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Password", style = MaterialTheme.typography.titleSmall)
                            Text("Change your account password", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onChangePassword) { Text("Change") }
                    }
                }
            }

            ProfileSectionTitle("More")
            Card {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    ProfileLink("History", Icons.Filled.History, onTransactions)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileLink("Payment methods", Icons.Filled.CreditCard) {
                        state.accountId?.let(onPaymentMethods)
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileLink("Add funds", Icons.Filled.Payments) { onAddFundsMenu() }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileLink("Scan & pay", Icons.Filled.QrCode2, onCardLink)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileLink("Promotions", Icons.Filled.CardGiftcard, onPromotions)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileLink("Settings", Icons.Filled.Settings, onSettings)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileLink("Terms of Service", Icons.Filled.Gavel, { onWebView("Terms of Service", "https://sq.alsgtg.com/terms/en-us") })
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ProfileLink("Privacy Policy", Icons.Filled.PrivacyTip, { onWebView("Privacy Policy", "https://sq.alsgtg.com/privacy/en-us") })
                }
            }

            OutlinedButton(
                onClick = { viewModel.signOut(); onSignedOut() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }

            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Request account deletion", color = MaterialTheme.colorScheme.error)
            }

            ProfileSectionTitle("About")
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("squeen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "An unofficial, reverse-engineered client for Speed Queen laundry rooms. " +
                            "It talks to the same public API as the official app and is not affiliated " +
                            "with Alliance Laundry Systems.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "squeen v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.Button(
                        onClick = { openObtainium(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Get with Obtainium")
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { openUrl(context, GITHUB_URL) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View source on GitHub")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account") },
            text = { Text("This will request deletion of your account and all associated data. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.requestDeletion()
                }) { Text("Request deletion", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (state.deletedRequested) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Request received") },
            text = { Text("Your account deletion request has been submitted. You have been signed out.") },
            confirmButton = {
                TextButton(onClick = {
                    onSignedOut()
                }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ProfileLink(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ProfileSectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 0.dp),
    )
}

private const val GITHUB_URL = "https://github.com/drakeerv/squeen"
private const val OBTAINIUM_DEEP_LINK = "obtainium://add/$GITHUB_URL"

/** Opens the app in Obtainium; falls back to the GitHub releases page. */
private fun openObtainium(context: android.content.Context) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(OBTAINIUM_DEEP_LINK),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { openUrl(context, "$GITHUB_URL/releases") }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
