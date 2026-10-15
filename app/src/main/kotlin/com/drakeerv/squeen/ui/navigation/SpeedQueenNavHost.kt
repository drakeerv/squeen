package com.drakeerv.squeen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.drakeerv.squeen.ui.screens.account.ChangePasswordScreen
import com.drakeerv.squeen.ui.screens.account.NotificationsScreen
import com.drakeerv.squeen.ui.screens.account.ProfileScreen
import com.drakeerv.squeen.ui.screens.account.TransactionsScreen
import com.drakeerv.squeen.ui.screens.auth.ForgotPasswordScreen
import com.drakeerv.squeen.ui.screens.auth.LoginScreen
import com.drakeerv.squeen.ui.screens.auth.SignUpScreen
import com.drakeerv.squeen.ui.screens.auth.VerifyEmailScreen
import com.drakeerv.squeen.ui.screens.locations.AddLocationScreen
import com.drakeerv.squeen.ui.screens.locations.FindKioskScreen
import com.drakeerv.squeen.ui.screens.locations.KioskDepositScreen
import com.drakeerv.squeen.ui.screens.locations.LocationsScreen
import com.drakeerv.squeen.ui.screens.machines.AddMachineScreen
import com.drakeerv.squeen.ui.screens.machines.MachineScreen
import com.drakeerv.squeen.ui.screens.machines.RoomListScreen
import com.drakeerv.squeen.ui.screens.machines.RoomScreen
import com.drakeerv.squeen.ui.screens.machines.WashAlertsScreen
import com.drakeerv.squeen.ui.screens.payments.AddCreditScreen
import com.drakeerv.squeen.ui.screens.payments.AutoRechargeScreen
import com.drakeerv.squeen.ui.screens.payments.PaymentFormScreen
import com.drakeerv.squeen.ui.screens.payments.PaymentMethodsScreen
import com.drakeerv.squeen.ui.screens.promotions.PromotionsScreen
import com.drakeerv.squeen.ui.screens.scan.QrScanScreen
import com.drakeerv.squeen.ui.screens.settings.SettingsScreen
import com.drakeerv.squeen.ui.screens.splash.SplashScreen
import com.drakeerv.squeen.ui.screens.web.WebViewScreen

@Composable
fun SpeedQueenNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onLoggedIn = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onVerifyEmailNeeded = {
                    navController.navigate(Routes.VERIFY_EMAIL) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onSignedIn = {
                    navController.navigate(Routes.MAIN) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onSignUp = { navController.navigate(Routes.SIGN_UP) },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                onVerifyEmail = { navController.navigate(Routes.VERIFY_EMAIL) },
            )
        }

        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onBack = { navController.popBackStack() },
                onSignedUp = { unverified ->
                    if (unverified) {
                        navController.navigate(Routes.VERIFY_EMAIL) { popUpTo(Routes.SIGN_UP) { inclusive = true } }
                    } else {
                        navController.navigate(Routes.MAIN) { popUpTo(Routes.LOGIN) { inclusive = true } }
                    }
                },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.VERIFY_EMAIL) {
            VerifyEmailScreen(
                onVerified = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.VERIFY_EMAIL) { inclusive = true }
                    }
                },
                onSignOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MAIN) {
            com.drakeerv.squeen.ui.screens.home.MainShell(
                onOpenRooms = { geoBoundaryId -> navController.navigate(Routes.roomList(geoBoundaryId)) },
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
                onAddLocation = { navController.navigate(Routes.ADD_LOCATION) },
                onTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onPromotions = { navController.navigate(Routes.PROMOTIONS) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onWebView = { title, url -> navController.navigate(Routes.webView(title, url)) },
                onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                onFindKiosk = { navController.navigate(Routes.FIND_KIOSK) },
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
                onCardLink = { navController.navigate(Routes.CARD_LINK) },
                onPaymentMethods = { accountId -> navController.navigate(Routes.paymentMethods(accountId)) },
            )
        }

        composable(Routes.ADD_LOCATION) { entry ->
            val savedState = entry.savedStateHandle
            val locationNumber by savedState.getStateFlow<String?>("locationNumber", null).collectAsStateWithLifecycle()
            val valueCenterId by savedState.getStateFlow<String?>("valueCenterId", null).collectAsStateWithLifecycle()
            AddLocationScreen(
                onBack = { navController.popBackStack() },
                onScanQr = {
                    navController.navigate(Routes.qrScan(QrMode.LOCATION))
                },
                onLocated = { geoBoundaryId ->
                    navController.navigate(Routes.roomList(geoBoundaryId)) {
                        popUpTo(Routes.ADD_LOCATION) { inclusive = true }
                    }
                },
                scannedLocationNumber = locationNumber,
                scannedValueCenterId = valueCenterId,
                onScannedConsumed = {
                    savedState["locationNumber"] = null
                    savedState["valueCenterId"] = null
                },
            )
        }

        composable(Routes.FIND_KIOSK) {
            FindKioskScreen(
                onBack = { navController.popBackStack() },
                onScanQr = { navController.navigate(Routes.qrScan(QrMode.KIOSK)) },
                onAddFunds = { accountId, geoBoundaryId ->
                    navController.navigate(Routes.addCredit(accountId, geoBoundaryId))
                },
            )
        }

        composable(
            Routes.KIOSK_DEPOSIT,
            arguments = listOf(
                navArgument("vtmId") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            KioskDepositScreen(
                vtmId = android.net.Uri.decode(entry.arguments?.getString("vtmId").orEmpty()),
                roomId = android.net.Uri.decode(entry.arguments?.getString("roomId").orEmpty()).takeIf { it.isNotBlank() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            Routes.ROOM_LIST,
            arguments = listOf(navArgument("geoBoundaryId") { type = NavType.StringType }),
        ) { entry ->
            RoomListScreen(
                geoBoundaryId = entry.arguments?.getString("geoBoundaryId").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
                onFindKiosk = { navController.navigate(Routes.FIND_KIOSK) },
            )
        }

        composable(
            Routes.ROOM,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
        ) { entry ->
            val savedState = entry.savedStateHandle
            val machineRoomId by savedState.getStateFlow<String?>("machineRoomId", null).collectAsStateWithLifecycle()
            val machineNumber by savedState.getStateFlow<String?>("machineNumber", null).collectAsStateWithLifecycle()
            val machineId by savedState.getStateFlow<String?>("machineId", null).collectAsStateWithLifecycle()
            RoomScreen(
                roomId = entry.arguments?.getString("roomId").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenMachine = { id ->
                    navController.navigate(Routes.machine(entry.arguments?.getString("roomId").orEmpty(), id))
                },
                onAddMachine = {
                    navController.navigate(Routes.addMachine(entry.arguments?.getString("roomId").orEmpty()))
                },
                onScanMachine = { navController.navigate(Routes.qrScan(QrMode.MACHINE)) },
                scannedMachineNumber = if (machineRoomId == entry.arguments?.getString("roomId")) machineNumber else null,
                scannedMachineId = if (machineRoomId == entry.arguments?.getString("roomId")) machineId else null,
                onScannedConsumed = {
                    savedState["machineRoomId"] = null
                    savedState["machineNumber"] = null
                    savedState["machineId"] = null
                },
            )
        }

        composable(
            Routes.ADD_MACHINE,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
        ) { entry ->
            val savedState = entry.savedStateHandle
            val machineNumber by savedState.getStateFlow<String?>("machineNumber", null).collectAsStateWithLifecycle()
            val machineId by savedState.getStateFlow<String?>("machineId", null).collectAsStateWithLifecycle()
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            AddMachineScreen(
                roomId = roomId,
                onBack = { navController.popBackStack() },
                onMachineFound = { foundMachineId ->
                    navController.navigate(Routes.machine(roomId, foundMachineId)) {
                        popUpTo(Routes.addMachine(roomId)) { inclusive = true }
                    }
                },
                onScanQr = { navController.navigate(Routes.qrScan(QrMode.MACHINE)) },
                scannedMachineNumber = machineNumber,
                scannedMachineId = machineId,
                onScannedConsumed = {
                    savedState["machineRoomId"] = null
                    savedState["machineNumber"] = null
                    savedState["machineId"] = null
                },
            )
        }

        composable(
            Routes.MACHINE,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("machineId") { type = NavType.StringType },
            ),
        ) { entry ->
            MachineScreen(
                roomId = entry.arguments?.getString("roomId").orEmpty(),
                machineId = entry.arguments?.getString("machineId").orEmpty(),
                onBack = { navController.popBackStack() },
                onAddCredit = { accountId, geoBoundaryId ->
                    navController.navigate(Routes.addCredit(accountId, geoBoundaryId))
                },
                onPaymentMethods = { accountId -> navController.navigate(Routes.paymentMethods(accountId)) },
            )
        }

        composable(
            Routes.PAYMENT_METHODS,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
        ) { entry ->
            PaymentMethodsScreen(
                accountId = entry.arguments?.getString("accountId").orEmpty(),
                onBack = { navController.popBackStack() },
                onAddCard = { geoBoundaryId ->
                    navController.navigate(
                        Routes.paymentForm(entry.arguments?.getString("accountId").orEmpty(), geoBoundaryId)
                    )
                },
            )
        }

        composable(
            Routes.PAYMENT_FORM,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("geoBoundaryId") { type = NavType.StringType },
            ),
        ) { entry ->
            PaymentFormScreen(
                accountId = entry.arguments?.getString("accountId").orEmpty(),
                geoBoundaryId = entry.arguments?.getString("geoBoundaryId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.ADD_CREDIT,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("geoBoundaryId") { type = NavType.StringType },
            ),
        ) { entry ->
            AddCreditScreen(
                accountId = entry.arguments?.getString("accountId").orEmpty(),
                geoBoundaryId = entry.arguments?.getString("geoBoundaryId").orEmpty(),
                onBack = { navController.popBackStack() },
                onAutoRecharge = {
                    navController.navigate(
                        Routes.autoRecharge(
                            entry.arguments?.getString("accountId").orEmpty(),
                            entry.arguments?.getString("geoBoundaryId").orEmpty(),
                        )
                    )
                },
            )
        }

        composable(
            Routes.AUTO_RECHARGE,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("geoBoundaryId") { type = NavType.StringType },
            ),
        ) { entry ->
            AutoRechargeScreen(
                accountId = entry.arguments?.getString("accountId").orEmpty(),
                geoBoundaryId = entry.arguments?.getString("geoBoundaryId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CARD_LINK) {
            com.drakeerv.squeen.ui.screens.cardlink.CardLinkScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TRANSACTIONS) {
            TransactionsScreen(
                onBack = { navController.popBackStack() },
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onWashAlerts = { navController.navigate(Routes.WASH_ALERTS) },
                onOpenRoom = { roomId -> navController.navigate(Routes.room(roomId)) },
                onWebView = { title, url -> navController.navigate(Routes.webView(title, url)) },
            )
        }

        composable(Routes.WASH_ALERTS) {
            WashAlertsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PROMOTIONS) {
            PromotionsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Routes.QR_SCAN,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val mode = runCatching { QrMode.valueOf(entry.arguments?.getString("mode").orEmpty()) }
                .getOrDefault(QrMode.LOCATION)
            QrScanScreen(
                mode = mode,
                onBack = { navController.popBackStack() },
                onValueCenterId = { valueCenterId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("valueCenterId", valueCenterId)
                    navController.popBackStack()
                },
                onLocationNumber = { locationNumber ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("locationNumber", locationNumber)
                    navController.popBackStack()
                },
                onMachineScanned = { roomId, machineNumber, machineId ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set("machineRoomId", roomId)
                        set("machineNumber", machineNumber)
                        set("machineId", machineId)
                    }
                    navController.popBackStack()
                },
                onKioskScanned = { vtmId, roomId ->
                    navController.navigate(Routes.kioskDeposit(vtmId, roomId))
                },
            )
        }

        composable(
            Routes.WEBVIEW,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType },
                navArgument("accountId") { type = NavType.StringType; defaultValue = "" },
                navArgument("kind") { type = NavType.StringType; defaultValue = "web" },
            ),
        ) { entry ->
            WebViewScreen(
                title = android.net.Uri.decode(entry.arguments?.getString("title").orEmpty()),
                url = android.net.Uri.decode(entry.arguments?.getString("url").orEmpty()),
                accountId = entry.arguments?.getString("accountId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
