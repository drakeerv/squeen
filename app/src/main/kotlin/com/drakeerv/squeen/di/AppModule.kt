package com.drakeerv.squeen.di

import androidx.room.Room
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.db.SpeedQueenDatabase
import com.drakeerv.squeen.data.net.MachineStatusSocket
import com.drakeerv.squeen.data.notify.NotificationHelper
import com.drakeerv.squeen.data.notify.WashAlertWatcher
import com.drakeerv.squeen.data.net.NetworkFactory
import com.drakeerv.squeen.data.session.FavoritesStore
import com.drakeerv.squeen.data.session.SessionManager
import com.drakeerv.squeen.data.session.ThemePreferences
import com.drakeerv.squeen.ui.screens.account.ChangePasswordViewModel
import com.drakeerv.squeen.ui.screens.account.NotificationsViewModel
import com.drakeerv.squeen.ui.screens.account.ProfileViewModel
import com.drakeerv.squeen.ui.screens.account.TransactionsViewModel
import com.drakeerv.squeen.ui.screens.auth.ForgotPasswordViewModel
import com.drakeerv.squeen.ui.screens.auth.LoginViewModel
import com.drakeerv.squeen.ui.screens.auth.SignUpViewModel
import com.drakeerv.squeen.ui.screens.auth.VerifyEmailViewModel
import com.drakeerv.squeen.ui.screens.locations.AddLocationViewModel
import com.drakeerv.squeen.ui.screens.locations.FindKioskViewModel
import com.drakeerv.squeen.ui.screens.locations.KioskDepositViewModel
import com.drakeerv.squeen.ui.screens.locations.LocationsViewModel
import com.drakeerv.squeen.ui.screens.machines.AddMachineViewModel
import com.drakeerv.squeen.ui.screens.machines.MachineViewModel
import com.drakeerv.squeen.ui.screens.machines.RoomListViewModel
import com.drakeerv.squeen.ui.screens.machines.RoomViewModel
import com.drakeerv.squeen.ui.screens.machines.WashAlertsViewModel
import com.drakeerv.squeen.ui.screens.payments.AddCreditViewModel
import com.drakeerv.squeen.ui.screens.payments.AutoRechargeViewModel
import com.drakeerv.squeen.ui.screens.payments.PaymentMethodsViewModel
import com.drakeerv.squeen.ui.screens.payments.PaymentFormViewModel
import com.drakeerv.squeen.ui.screens.promotions.PromotionsViewModel
import com.drakeerv.squeen.ui.screens.settings.SettingsViewModel
import com.drakeerv.squeen.ui.screens.cardlink.CardLinkViewModel
import com.drakeerv.squeen.ui.screens.home.HomeViewModel
import com.drakeerv.squeen.ui.screens.splash.SplashViewModel
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {

    single { SessionManager(androidContext()) }
    single { ThemePreferences(androidContext()) }
    single { FavoritesStore(androidContext()) }
    single<Json> { NetworkFactory.json }

    single(named("api")) { NetworkFactory.createOkHttp(get()) }
    single(named("socket")) {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    single { NetworkFactory.createRetrofit(get(named("api"))) }
    single { NetworkFactory.createApi(get()) }

    single {
        Room.databaseBuilder(androidContext(), SpeedQueenDatabase::class.java, "speedqueen.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<SpeedQueenDatabase>().payloadCacheDao() }

    single { SpeedQueenRepository(get(), get(), get(), get()) }
    single { MachineStatusSocket(get(), get(), get(named("socket"))) }
    single { NotificationHelper(androidContext()) }
    single { WashAlertWatcher(get(), get(), get()) }

    // ------------------------------------------------------------------ view models

    viewModel { SplashViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { CardLinkViewModel(get(), get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { SignUpViewModel(get()) }
    viewModel { ForgotPasswordViewModel(get()) }
    viewModel { VerifyEmailViewModel(get()) }

    viewModel { LocationsViewModel(get(), get()) }
    viewModel { AddLocationViewModel(get()) }
    viewModel { KioskDepositViewModel(get(), get()) }
    viewModel { FindKioskViewModel(get()) }
    viewModel { RoomListViewModel(get()) }
    viewModel { RoomViewModel(get(), get()) }
    viewModel { AddMachineViewModel(get()) }
    viewModel { MachineViewModel(get(), get(), get()) }
    viewModel { WashAlertsViewModel(get()) }

    viewModel { PaymentMethodsViewModel(get()) }
    viewModel { PaymentFormViewModel(get(), get()) }
    viewModel { AddCreditViewModel(get()) }
    viewModel { AutoRechargeViewModel(get()) }

    viewModel { ProfileViewModel(get()) }
    viewModel { TransactionsViewModel(get()) }
    viewModel { NotificationsViewModel(get()) }
    viewModel { ChangePasswordViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { PromotionsViewModel(get()) }
}
