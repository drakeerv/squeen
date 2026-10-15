package com.drakeerv.squeen.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.data.session.FavoriteLocation
import com.drakeerv.squeen.data.session.FavoriteRoom
import com.drakeerv.squeen.data.session.FavoritesStore
import com.drakeerv.squeen.data.session.SessionManager
import com.drakeerv.squeen.ui.components.AppTopBar
import com.drakeerv.squeen.ui.components.EmptyState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class HomeUiState(
    val userName: String = "",
    val balanceCents: Int = 0,
    val hasBalance: Boolean = false,
    val accountId: String? = null,
    val geoBoundaryId: String? = null,
)

class HomeViewModel(
    private val repository: SpeedQueenRepository,
    private val favorites: FavoritesStore,
    private val session: SessionManager,
) : ViewModel() {

    val favoriteLocations: StateFlow<List<FavoriteLocation>> = favorites.locations
    val favoriteRooms: StateFlow<List<FavoriteRoom>> = favorites.rooms
    val displayName: String = session.userId.take(8)

    private val state = kotlinx.coroutines.flow.MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val user = (repository.getUser() as? ApiResult.Success)?.data
            val account = user?.accounts?.firstOrNull()
            val sub = account?.subAccounts?.firstOrNull()
            state.update {
                it.copy(
                    userName = user?.firstName.orEmpty(),
                    balanceCents = sub?.balance?.toInt() ?: 0,
                    hasBalance = sub != null,
                    accountId = account?.id,
                    geoBoundaryId = sub?.geoBoundaryId,
                )
            }
        }
    }

    fun unfavoriteRoom(room: FavoriteRoom) {
        favorites.toggleRoom(room)
    }

    fun unfavoriteLocation(location: FavoriteLocation) {
        favorites.toggleLocation(location)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenRooms: (geoBoundaryId: String) -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    onAddLocation: () -> Unit,
    onTransactions: () -> Unit,
    onAddFundsMenu: () -> Unit,
    onCardLink: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locations by viewModel.favoriteLocations.collectAsStateWithLifecycle()
    val rooms by viewModel.favoriteRooms.collectAsStateWithLifecycle()
    val snackbar = com.drakeerv.squeen.ui.components.LocalSnackbarHost.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refresh() }

    androidx.compose.material3.Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    Text(
                        if (state.userName.isBlank()) "Home" else "Hi, ${state.userName}",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------------------------------------------------- balance + actions
            if (state.hasBalance) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Account balance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    com.drakeerv.squeen.ui.Money.fromMinorUnits(state.balanceCents),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            androidx.compose.material3.FilledTonalIconButton(
                                onClick = onAddFundsMenu,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add funds")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickAction(icon = Icons.Filled.QrCode2, label = "Scan & pay", modifier = Modifier.weight(1f), onClick = onCardLink)
                    QuickAction(icon = Icons.Filled.History, label = "History", modifier = Modifier.weight(1f), onClick = onTransactions)
                }
            }

            // ---------------------------------------------------------- favourite locations
            item {
                SectionTitle("Favorite locations")
            }
            if (locations.isEmpty()) {
                item {
                    EmptySection(
                        text = "Star a location on the Locations tab to see it here.",
                    )
                }
            } else {
                items(locations, key = { "loc-${it.id}" }) { location ->
                    FavoriteRow(
                        icon = Icons.Filled.Storefront,
                        title = location.name,
                        subtitle = location.organizationName,
                        onClick = { onOpenRooms(location.id) },
                        onUnstar = { viewModel.unfavoriteLocation(location) },
                    )
                }
            }

            // ---------------------------------------------------------- favourite rooms
            item {
                SectionTitle("Favorite rooms")
            }
            if (rooms.isEmpty()) {
                item {
                    EmptySection(
                        text = "Star a room from any room list to keep it one tap away.",
                    )
                }
            } else {
                items(rooms, key = { "room-${it.roomId}" }) { room ->
                    FavoriteRow(
                        icon = Icons.Filled.LocalLaundryService,
                        title = room.roomName,
                        subtitle = room.locationName,
                        onClick = { onOpenRoom(room.roomId) },
                        onUnstar = { viewModel.unfavoriteRoom(room) },
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun EmptySection(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun FavoriteRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onUnstar: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            androidx.compose.material3.IconButton(onClick = onUnstar) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Remove from favorites",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
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
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
