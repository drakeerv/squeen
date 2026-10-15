package com.drakeerv.squeen.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drakeerv.squeen.ui.screens.account.NotificationsScreen
import com.drakeerv.squeen.ui.screens.account.ProfileScreen
import com.drakeerv.squeen.ui.screens.locations.LocationsScreen

/**
 * Signed-in shell: bottom navigation with Home (favourites), Locations, Alerts and Profile.
 * Everything else (rooms, machines, payments…) is pushed on top of this shell.
 */
@Composable
fun MainShell(
    onOpenRooms: (geoBoundaryId: String) -> Unit,
    onOpenRoom: (roomId: String) -> Unit,
    onAddLocation: () -> Unit,
    onTransactions: () -> Unit,
    onPromotions: () -> Unit,
    onSettings: () -> Unit,
    onWebView: (String, String) -> Unit,
    onChangePassword: () -> Unit,
    onFindKiosk: () -> Unit,
    onSignedOut: () -> Unit,
    onCardLink: () -> Unit,
    onPaymentMethods: (accountId: String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        // The tab screens own the status-bar inset through their own top bars; the shell
        // only reserves space for the bottom navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(),
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                    label = { Text("Locations") },
                    colors = NavigationBarItemDefaults.colors(),
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text("Alerts") },
                    colors = NavigationBarItemDefaults.colors(),
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(),
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab.coerceIn(0, 3)) {
                0 -> HomeScreen(
                    onOpenRooms = onOpenRooms,
                    onOpenRoom = onOpenRoom,
                    onAddLocation = onAddLocation,
                    onTransactions = onTransactions,
                    onAddFundsMenu = onFindKiosk,
                    onCardLink = onCardLink,
                )
                1 -> LocationsScreen(
                    insideShell = true,
                    onOpenRooms = onOpenRooms,
                    onOpenRoom = onOpenRoom,
                    onAddLocation = onAddLocation,
                    onNotifications = { tab = 2 },
                )
                2 -> NotificationsScreen(
                    showBackArrow = false,
                    insideShell = true,
                    onBack = {},
                    onOpenRoom = onOpenRoom,
                )
                else -> ProfileScreen(
                    showBackArrow = false,
                    insideShell = true,
                    onBack = {},
                    onChangePassword = onChangePassword,
                    onSignedOut = onSignedOut,
                    onTransactions = onTransactions,
                    onPromotions = onPromotions,
                    onSettings = onSettings,
                    onWebView = onWebView,
                    onCardLink = onCardLink,
                    onPaymentMethods = onPaymentMethods,
                    onAddFundsMenu = onFindKiosk,
                )
            }
        }
    }
}
