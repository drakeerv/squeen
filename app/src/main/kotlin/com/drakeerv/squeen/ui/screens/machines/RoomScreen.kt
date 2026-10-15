package com.drakeerv.squeen.ui.screens.machines

import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dry
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.drakeerv.squeen.data.model.Machine
import com.drakeerv.squeen.data.model.Room
import com.drakeerv.squeen.data.net.MachineStatusSocket
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

data class RoomUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val room: Room? = null,
    val machines: List<Machine> = emptyList(),
    val error: String? = null,
    val live: Boolean = false,
)

class RoomViewModel(
    private val repository: SpeedQueenRepository,
    private val socket: MachineStatusSocket,
) : ViewModel() {

    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    private var currentRoomId: String? = null

    fun load(roomId: String, refresh: Boolean = false) {
        currentRoomId = roomId
        viewModelScope.launch {
            _state.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
            val roomResult = repository.room(roomId)
            val machinesResult = repository.roomMachines(roomId, force = refresh)

            when {
                machinesResult is ApiResult.Success -> {
                    val room = (roomResult as? ApiResult.Success)?.data
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            room = room ?: it.room,
                            machines = machinesResult.data.sortedBy { m -> m.ordinalValue },
                        )
                    }
                }
                machinesResult is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = machinesResult.message)
                }
            }
        }
    }

    fun observeLiveUpdates() {
        viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    is MachineStatusSocket.Event.Connected -> _state.update { it.copy(live = true) }
                    is MachineStatusSocket.Event.Disconnected -> _state.update { it.copy(live = false) }
                    is MachineStatusSocket.Event.ConnectionError -> _state.update { it.copy(live = false) }
                    is MachineStatusSocket.Event.MachineStatus -> {
                        val update = event.update
                        _state.update { state ->
                            state.copy(
                                machines = state.machines.map { machine ->
                                    if (machine.id == update.machineId) {
                                        machine.copy(
                                            statusId = update.statusId ?: machine.statusId,
                                            remainingSeconds = update.remainingSeconds,
                                            remainingVend = update.remainingVend,
                                        )
                                    } else machine
                                }
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun startLive(roomId: String) {
        socket.connect(roomId)
    }

    fun stopLive(roomId: String) {
        // keep the socket alive for the machine screen; only unsubscribe the topic
        socket.unsubscribeRoom(roomId)
    }

    suspend fun resolveScan(roomId: String, machineNumber: String?, machineId: String?): Machine? {
        return when (val result = repository.roomMachineByNumber(roomId, machineNumber, machineId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomId: String,
    onBack: () -> Unit,
    onOpenMachine: (machineId: String) -> Unit,
    onAddMachine: () -> Unit,
    onScanMachine: () -> Unit,
    viewModel: RoomViewModel = koinViewModel(),
    scannedMachineNumber: String? = null,
    scannedMachineId: String? = null,
    onScannedConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val favorites = org.koin.compose.koinInject<com.drakeerv.squeen.data.session.FavoritesStore>()
    val favoriteRooms by favorites.rooms.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<MachineFilter>(MachineFilter.ALL) }
    var search by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) {
        viewModel.load(roomId)
        viewModel.observeLiveUpdates()
        viewModel.startLive(roomId)
    }
    DisposableEffect(roomId) {
        onDispose { viewModel.stopLive(roomId) }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(scannedMachineNumber, scannedMachineId) {
        if (!scannedMachineNumber.isNullOrBlank() || !scannedMachineId.isNullOrBlank()) {
            val machine = viewModel.resolveScan(roomId, scannedMachineNumber, scannedMachineId)
            if (machine != null) {
                onOpenMachine(machine.id)
            } else {
                snackbar.showSnackbar("Invalid machine number")
            }
            onScannedConsumed()
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
                            placeholder = {
                                Text(
                                    "Machine # or name",
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                )
                            },
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
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(state.room?.name?.ifBlank { "Room" } ?: "Room")
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
                    if (searchActive) {
                        // nothing else while searching
                    } else {
                        val isFavorite = favoriteRooms.any { it.roomId == roomId }
                        IconButton(onClick = {
                            favorites.toggleRoom(
                                com.drakeerv.squeen.data.session.FavoriteRoom(
                                    roomId = roomId,
                                    roomName = state.room?.name.orEmpty(),
                                    locationId = state.room?.geoBoundaryId,
                                    locationName = state.room?.name.orEmpty(),
                                )
                            )
                        }) {
                            Icon(
                                if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Find a machine")
                        }
                    if (state.live) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Live",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.load(roomId, refresh = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMachine,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add machine") },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.machines.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load(roomId) },
                )
                state.machines.isEmpty() -> EmptyState(
                    title = "No machines found",
                    message = "This room doesn't have any machines yet.",
                )
                else -> Column {
                    state.room?.banners?.firstOrNull()?.imageUrl?.let { url ->
                        Image(
                            painter = rememberAsyncImagePainter(url),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filter == MachineFilter.ALL,
                            onClick = { filter = MachineFilter.ALL },
                            label = { Text("All") },
                        )
                        FilterChip(
                            selected = filter == MachineFilter.WASHERS,
                            onClick = { filter = MachineFilter.WASHERS },
                            label = { Text("Washers") },
                        )
                        FilterChip(
                            selected = filter == MachineFilter.DRYERS,
                            onClick = { filter = MachineFilter.DRYERS },
                            label = { Text("Dryers") },
                        )
                        FilterChip(
                            selected = filter == MachineFilter.AVAILABLE,
                            onClick = { filter = MachineFilter.AVAILABLE },
                            label = { Text("Available") },
                        )
                    }
                    val machines = when (filter) {
                        MachineFilter.ALL -> state.machines
                        MachineFilter.WASHERS -> state.machines.filter { it.isWasher }
                        MachineFilter.DRYERS -> state.machines.filter { it.isDryer }
                        MachineFilter.AVAILABLE -> state.machines.filter { it.isAvailable }
                    }.let { filtered -> filterMachines(filtered, search) }
                    if (machines.isEmpty()) {
                        com.drakeerv.squeen.ui.components.EmptyState(
                            title = "No machines found",
                            message = "No machines match \"${search.trim()}\".",
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 170.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(machines, key = { it.id }) { machine ->
                                MachineCard(machine = machine, onClick = { onOpenMachine(machine.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class MachineFilter { ALL, WASHERS, DRYERS, AVAILABLE }

@Composable
private fun MachineCard(machine: Machine, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // All roles below follow the active scheme, so Material You re-themes the grid too.
    val containerColor = when {
        !machine.isActive -> scheme.surfaceVariant
        machine.isRunning -> scheme.surfaceContainerHighest
        else -> scheme.primaryContainer
    }
    val contentColor = when {
        !machine.isActive -> scheme.onSurfaceVariant
        machine.isRunning -> scheme.onSurface
        else -> scheme.onPrimaryContainer
    }
    val accent = when {
        !machine.isActive -> scheme.outline
        machine.isRunning -> scheme.primary
        else -> scheme.primary
    }
    Card(
        onClick = onClick,
        enabled = machine.isActive,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            disabledContainerColor = scheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (machine.isRunning) scheme.surface.copy(alpha = 0.55f)
                        else contentColor.copy(alpha = 0.12f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                com.drakeerv.squeen.ui.components.MachineAnimation(
                    isDryer = machine.isDryer,
                    running = machine.isRunning,
                    modifier = Modifier.size(42.dp),
                )
            }
            Column {
                Text(
                    buildString {
                        append(if (machine.isDryer) "Dryer" else "Washer")
                        machine.number.takeIf { it.isNotBlank() }?.let { append(" #$it") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (machine.isRunning) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(accent, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (!machine.isActive) "Unavailable" else com.drakeerv.squeen.ui.machineStatusLabel(machine),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

/** Filters machines by number ("0004", "#4") or name. */
private fun filterMachines(machines: List<com.drakeerv.squeen.data.model.Machine>, query: String): List<com.drakeerv.squeen.data.model.Machine> {
    val q = query.trim().removePrefix("#")
    if (q.isEmpty()) return machines
    return machines.filter { machine ->
        machine.number.contains(q, ignoreCase = true) ||
            machine.name.contains(q, ignoreCase = true) ||
            (q.toIntOrNull()?.let { machine.number.toIntOrNull() == it } == true)
    }
}
