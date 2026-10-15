package com.drakeerv.squeen.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.WashAlert
import com.drakeerv.squeen.data.session.ThemePreferences
import com.drakeerv.squeen.ui.components.AppTopBar
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class SettingsUiState(
    val loading: Boolean = true,
    val userName: String = "",
    val userEmail: String = "",
    val pushEnabled: Boolean = false,
    val washAlerts: List<WashAlert> = emptyList(),
    val accountId: String? = null,
    val geoBoundaryId: String? = null,
    val dynamicColor: Boolean = false,
    val dynamicColorSupported: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val repository: SpeedQueenRepository,
    private val themePreferences: ThemePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            dynamicColor = themePreferences.dynamicColor.value,
            dynamicColorSupported = themePreferences.dynamicColorSupported,
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val user = (repository.getUser() as? ApiResult.Success)?.data
            val alerts = (repository.washAlerts() as? ApiResult.Success)?.data.orEmpty()
            _state.update {
                it.copy(
                    loading = false,
                    userName = user?.fullName.orEmpty(),
                    userEmail = user?.email.orEmpty(),
                    pushEnabled = true,
                    washAlerts = alerts,
                    accountId = user?.accounts?.firstOrNull()?.id,
                    geoBoundaryId = user?.customerGeoBoundaries?.firstOrNull()?.id,
                )
            }
        }
    }

    fun setPushNotifications(enabled: Boolean) {
        viewModelScope.launch {
            val result = if (enabled) repository.registerPushDevice() else repository.unregisterPushDevice()
            when (result) {
                is ApiResult.Success -> _state.update { it.copy(pushEnabled = enabled) }
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        themePreferences.setDynamicColor(enabled)
        _state.update { it.copy(dynamicColor = enabled) }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onWashAlerts: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    onWebView: (String, String) -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
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

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("Settings") },
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---------------------------------------------------------- notifications
            SettingsSection(title = "Notifications") {
                SettingsEntry(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Wash alerts",
                    subtitle = when {
                        state.washAlerts.isEmpty() -> "No active alerts"
                        else -> "${state.washAlerts.size} machine${if (state.washAlerts.size == 1) "" else "s"} watched"
                    },
                    onClick = onWashAlerts,
                )
                SettingsDivider()
                SettingsEntry(
                    icon = Icons.Filled.NotificationsNone,
                    title = "Push notifications",
                    subtitle = "Device alerts and offers",
                    onClick = { viewModel.setPushNotifications(!state.pushEnabled) },
                    trailing = {
                        Switch(
                            checked = state.pushEnabled,
                            onCheckedChange = { viewModel.setPushNotifications(it) },
                        )
                    },
                )
            }

            // ---------------------------------------------------------- appearance
            SettingsSection(title = "Appearance") {
                SettingsEntry(
                    icon = Icons.Filled.Palette,
                    title = "Material You",
                    subtitle = if (state.dynamicColorSupported) {
                        "Colors follow your wallpaper"
                    } else {
                        "Requires Android 12 or newer"
                    },
                    enabled = state.dynamicColorSupported,
                    onClick = { if (state.dynamicColorSupported) viewModel.setDynamicColor(!state.dynamicColor) },
                    trailing = {
                        Switch(
                            checked = state.dynamicColor && state.dynamicColorSupported,
                            enabled = state.dynamicColorSupported,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                        )
                    },
                )
            }

            // ---------------------------------------------------------- about
            SettingsSection(title = "About") {
                SettingsEntry(
                    icon = Icons.Filled.Gavel,
                    title = "Terms of Service",
                    subtitle = null,
                    onClick = { onWebView("Terms of Service", "https://sq.alsgtg.com/terms/en-us") },
                )
                SettingsDivider()
                SettingsEntry(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = null,
                    onClick = { onWebView("Privacy Policy", "https://sq.alsgtg.com/privacy/en-us") },
                )
            }

            Text(
                "Speed Queen ${com.drakeerv.squeen.BuildConfig.VERSION_NAME} " +
                    "(${com.drakeerv.squeen.BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.06f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

private fun initialsOf(name: String): String =
    name.split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "SQ" }
