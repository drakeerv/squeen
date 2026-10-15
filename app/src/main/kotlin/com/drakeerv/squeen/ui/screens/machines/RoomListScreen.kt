package com.drakeerv.squeen.ui.screens.machines

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.Room
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

data class RoomListUiState(
    val loading: Boolean = true,
    val locationId: String = "",
    val locationName: String = "",
    val rooms: List<Room> = emptyList(),
    val error: String? = null,
)

class RoomListViewModel(
    private val repository: SpeedQueenRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RoomListUiState())
    val state: StateFlow<RoomListUiState> = _state.asStateFlow()

    fun load(geoBoundaryId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // Primary source: the geo-boundary payload (rooms hang off child boundaries);
            // the user payload is a fallback for offline starts.
            val contextResult = repository.locationContext(geoBoundaryId)
            val user = (repository.getUser() as? ApiResult.Success)?.data
            val boundary = user?.customerGeoBoundaries?.firstOrNull { it.id == geoBoundaryId }

            val rooms = when {
                contextResult is ApiResult.Success && contextResult.data.boundary?.rooms?.isNotEmpty() == true ->
                    contextResult.data.boundary.rooms
                boundary?.rooms?.isNotEmpty() == true -> boundary.rooms
                else -> emptyList()
            }
            val name = (contextResult as? ApiResult.Success)?.data?.boundary?.displayName
                ?.takeIf { it.isNotBlank() }
                ?: boundary?.displayName.orEmpty()

            if (rooms.isEmpty() && contextResult is ApiResult.Failure && boundary == null) {
                _state.update { it.copy(loading = false, error = contextResult.message) }
                return@launch
            }

            _state.update {
                it.copy(
                    loading = false,
                    locationId = geoBoundaryId,
                    locationName = name,
                    rooms = rooms.sortedBy { room -> room.ordinalValue },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListScreen(
    geoBoundaryId: String,
    onBack: () -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    onFindKiosk: () -> Unit,
    viewModel: RoomListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val favorites = org.koin.compose.koinInject<com.drakeerv.squeen.data.session.FavoritesStore>()
    val favoriteRooms by favorites.rooms.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    LaunchedEffect(geoBoundaryId) { viewModel.load(geoBoundaryId) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = {
                    if (searchActive) {
                        androidx.compose.material3.TextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Search rooms", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)) },
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(state.locationName.ifBlank { "Rooms" })
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            search = ""
                        } else {
                            onBack()
                        }
                    }) {
                        if (searchActive) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search rooms")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onFindKiosk,
                icon = { Icon(Icons.Filled.PointOfSale, contentDescription = null) },
                text = { Text("Load funds") },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.rooms.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load(geoBoundaryId) },
                )
                state.rooms.isEmpty() -> EmptyState(
                    title = "No rooms available",
                    message = "This location doesn't have any laundry rooms yet.",
                )
                else -> Column {
                    val filtered = roomFilter(state.rooms, search)
                    if (filtered.isEmpty()) {
                        EmptyState(
                            title = "No rooms found",
                            message = "No rooms match \"$search\".",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(filtered, key = { it.id }) { room ->
                                RoomCard(
                                    room = room,
                                    favorite = favoriteRooms.any { it.roomId == room.id },
                                    onClick = { onOpenRoom(room.id) },
                                    onToggleFavorite = {
                                        favorites.toggleRoom(
                                            com.drakeerv.squeen.data.session.FavoriteRoom(
                                                roomId = room.id,
                                                roomName = room.name,
                                                locationId = state.locationId,
                                                locationName = state.locationName,
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: Room,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val banner = room.banners.firstOrNull()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (banner?.imageUrl != null) {
                Image(
                    painter = rememberAsyncImagePainter(banner.imageUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.LocalLaundryService,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(room.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val machines = room.machines
                    if (machines.isNotEmpty()) {
                        Text(
                            "${machines.count { it.isWasher }} washers · ${machines.count { it.isDryer }} dryers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (room.subscriptionExpired) {
                    Text(
                        "Unavailable",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun roomFilter(rooms: List<Room>, query: String): List<Room> {
    val q = query.trim()
    if (q.isEmpty()) return rooms
    return rooms.filter { room ->
        room.name.contains(q, ignoreCase = true) ||
            (room.banners.firstOrNull()?.imageUrl == null && room.name.isBlank())
    }
}
