package com.drakeerv.squeen.ui.screens.account

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.MailboxNotification
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

data class NotificationsUiState(
    val loading: Boolean = true,
    val notifications: List<MailboxNotification> = emptyList(),
    val error: String? = null,
)

class NotificationsViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.notifications()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, notifications = result.data) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun markRead(id: String, read: Boolean) {
        viewModelScope.launch {
            repository.setNotificationRead(id, read)
            _state.update { state ->
                state.copy(notifications = state.notifications.map { if (it.id == id) it.copy(isRead = read) else it })
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteNotification(id)) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(notifications = state.notifications.filterNot { it.id == id })
                }
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    showBackArrow: Boolean = true,
    insideShell: Boolean = false,
    viewModel: NotificationsViewModel = koinViewModel(),
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
        contentWindowInsets = if (insideShell) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },

        topBar = {
            AppTopBar(
                title = { Text("Notifications") },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.notifications.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load() },
                )
                state.notifications.isEmpty() -> EmptyState(
                    title = "No notifications yet",
                    message = "Promotions and notifications you receive will appear here as they arrive.",
                )
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.notifications, key = { it.id }) { notification ->
                        NotificationCard(
                            notification = notification,
                            onToggleRead = { viewModel.markRead(notification.id, !notification.isRead) },
                            onDelete = { viewModel.delete(notification.id) },
                            onOpenRoom = {
                                notification.roomId?.let(onOpenRoom)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: MailboxNotification,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    onOpenRoom: () -> Unit,
) {
    Card(
        onClick = onOpenRoom,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (notification.isRead) Icons.Filled.MarkEmailRead else Icons.Filled.Drafts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    notification.subject ?: "Notification",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                notification.body?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                notification.sentAt?.take(10)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column {
                IconButton(onClick = onToggleRead) {
                    Icon(
                        if (notification.isRead) Icons.Filled.Drafts else Icons.Filled.MarkEmailRead,
                        contentDescription = "Toggle read",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
