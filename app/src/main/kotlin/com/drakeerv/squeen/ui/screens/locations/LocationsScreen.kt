package com.drakeerv.squeen.ui.screens.locations

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.drakeerv.squeen.ui.components.AppTopBar
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.BuildConfig
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.CustomerGeoBoundary
import com.drakeerv.squeen.data.model.User
import com.drakeerv.squeen.data.notify.WashAlertWatcher
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

data class LocationsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val locations: List<CustomerGeoBoundary> = emptyList(),
    val user: User? = null,
    val error: String? = null,
    val offline: Boolean = false,
    val openingLocationId: String? = null,
)

class LocationsViewModel(
    private val repository: SpeedQueenRepository,
    private val washAlertWatcher: WashAlertWatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationsUiState())
    val state: StateFlow<LocationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = !refresh, refreshing = refresh, error = null) }
            when (val result = repository.locations(force = refresh)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(loading = false, refreshing = false, locations = result.data, offline = false)
                    }
                    washAlertWatcher.refresh()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.message, offline = result.offline)
                }
            }
        }
    }

    /** Resolves the location context (organization + rooms) and opens the room list. */
    fun openLocation(location: CustomerGeoBoundary, onRooms: (String) -> Unit, onSingleRoom: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(openingLocationId = location.id) }
            when (val result = repository.locationContext(location.id)) {
                is ApiResult.Success -> {
                    val rooms = result.data.boundary?.rooms.orEmpty().filter { !it.subscriptionExpired }
                    _state.update { it.copy(openingLocationId = null) }
                    if (rooms.size == 1) onSingleRoom(rooms.first().id) else onRooms(location.id)
                }
                is ApiResult.Failure -> {
                    // Offline: fall back to any rooms cached in the user payload.
                    val cached = location.rooms
                    _state.update { it.copy(openingLocationId = null) }
                    if (cached.size == 1) onSingleRoom(cached.first().id) else onRooms(location.id)
                }
            }
        }
    }

    fun removeLocation(boundaryId: String) {
        viewModelScope.launch {
            when (val result = repository.removeCustomerGeoBoundary(boundaryId)) {
                is ApiResult.Success -> {
                    _state.update { state -> state.copy(locations = state.locations.filterNot { it.id == boundaryId }) }
                }
                is ApiResult.Failure -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            washAlertWatcher.stop()
            repository.logoutRemote()
            onDone()
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    onOpenRooms: (geoBoundaryId: String) -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    onAddLocation: () -> Unit,
    onNotifications: () -> Unit,
    insideShell: Boolean = false,
    viewModel: LocationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHost.current
    val favorites = org.koin.compose.koinInject<com.drakeerv.squeen.data.session.FavoritesStore>()
    val favoriteLocations by favorites.locations.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.load()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                    title = {
                        if (searchActive) {
                            androidx.compose.material3.TextField(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = {
                                    Text(
                                        "Search locations",
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text("Locations")
                        }
                    },
                    navigationIcon = {
                        if (searchActive) {
                            IconButton(onClick = {
                                searchActive = false
                                search = ""
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
                            }
                        }
                    },
                    actions = {
                        if (searchActive) {
                            // no extra actions while searching
                        } else {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search locations")
                            }
                            IconButton(onClick = { onNotifications() }) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                            }
                            IconButton(onClick = { viewModel.load(refresh = true) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAddLocation,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add location") },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.loading -> LoadingState()
                    state.error != null && state.locations.isEmpty() -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.load() },
                    )
                    state.locations.isEmpty() -> EmptyState(
                        title = "No locations yet",
                        message = "Add a location to view its washers and dryers.",
                    )
                    else -> LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val query = search.trim()
                        val filtered = if (query.isEmpty()) {
                            state.locations
                        } else {
                            state.locations.filter { location ->
                                location.displayName.contains(query, ignoreCase = true) ||
                                    location.organizationName?.contains(query, ignoreCase = true) == true
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                EmptyState(
                                    title = "No locations found",
                                    message = "No locations match \"$query\".",
                                )
                            }
                        }
                        items(filtered, key = { it.id }) { location ->
                            LocationCard(
                                location = location,
                                busy = state.openingLocationId == location.id,
                                favorite = favoriteLocations.any { it.id == location.id },
                                onClick = {
                                    viewModel.openLocation(
                                        location,
                                        onRooms = onOpenRooms,
                                        onSingleRoom = onOpenRoom,
                                    )
                                },
                                onRemove = { viewModel.removeLocation(location.id) },
                                onToggleFavorite = {
                                    favorites.toggleLocation(
                                        com.drakeerv.squeen.data.session.FavoriteLocation(
                                            id = location.id,
                                            name = location.displayName,
                                            organizationName = location.organizationName,
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

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun LocationCard(
    location: CustomerGeoBoundary,
    busy: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    location.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                location.organizationName?.takeIf { it.isNotBlank() && it != location.displayName }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (location.hasUnreadNotifications) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                )
            }
            if (busy) {
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove location",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
