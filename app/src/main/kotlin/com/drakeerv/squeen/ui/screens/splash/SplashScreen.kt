package com.drakeerv.squeen.ui.screens.splash

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drakeerv.squeen.BuildConfig
import com.drakeerv.squeen.R
import com.drakeerv.squeen.data.ApiResult
import com.drakeerv.squeen.data.SpeedQueenRepository
import com.drakeerv.squeen.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

sealed interface SplashDestination {
    data object Loading : SplashDestination
    data object NeedsLogin : SplashDestination
    data object SignedIn : SplashDestination
    data class VerifyEmail(val user: User) : SplashDestination
    data class ForceUpgrade(val message: String, val url: String?) : SplashDestination
}

class SplashViewModel(private val repository: SpeedQueenRepository) : ViewModel() {

    private val _state = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val state: StateFlow<SplashDestination> = _state.asStateFlow()

    init {
        check()
    }

    fun check() {
        viewModelScope.launch {
            _state.value = SplashDestination.Loading

            // 1. minimum version gate (best effort)
            when (val version = repository.minAppVersion()) {
                is ApiResult.Success -> {
                    val minimum = version.data.minVersion
                    if (!minimum.isNullOrBlank() && compareVersions(BuildConfig.API_VERSION, minimum) < 0) {
                        _state.value = SplashDestination.ForceUpgrade(
                            version.data.forceUpgradeMessage
                                ?: "A newer version of the app is required to continue. Please update to keep using the app.",
                            null,
                        )
                        return@launch
                    }
                }
                is ApiResult.Failure -> Unit // best effort, continue
            }

            if (!repository.isLoggedIn()) {
                _state.value = SplashDestination.NeedsLogin
                return@launch
            }

            // 2. session restore
            when (val result = repository.getUser()) {
                is ApiResult.Success -> {
                    val user = result.data
                    _state.value = when {
                        needsVerification(user) -> SplashDestination.VerifyEmail(user)
                        else -> SplashDestination.SignedIn
                    }
                }
                is ApiResult.Failure -> {
                    if (result.unauthorized) repository.clearSession()
                    _state.value = SplashDestination.NeedsLogin
                }
            }
        }
    }

    private fun needsVerification(user: User): Boolean {
        val mode = repository.loginMode()
        if (mode == "email" && !user.isEmailVerified && user.email.isNotEmpty()) return true
        if (mode == "phone" && !user.hasVerifiedPhoneNumber && user.phoneNumbers.isNotEmpty()) return true
        return false
    }

    companion object {
        fun compareVersions(current: String, minimum: String): Int {
            val a = current.split('.').mapNotNull { it.trim().toIntOrNull() }
            val b = minimum.split('.').mapNotNull { it.trim().toIntOrNull() }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }
    }
}

@Composable
fun SplashScreen(
    onLoggedIn: () -> Unit,
    onLoggedOut: () -> Unit,
    onVerifyEmailNeeded: () -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state) {
        when (state) {
            is SplashDestination.SignedIn -> onLoggedIn()
            is SplashDestination.NeedsLogin -> onLoggedOut()
            is SplashDestination.VerifyEmail -> onVerifyEmailNeeded()
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_notification_wash),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(72.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Speed Queen",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator()
        }
    }

    val upgrade = state as? SplashDestination.ForceUpgrade
    if (upgrade != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Update required") },
            text = { Text(upgrade.message) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(upgrade.url ?: "market://details?id=${context.packageName}"),
                    )
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"),
                                )
                            )
                        }
                }) { Text("Update") }
            },
        )
    }
}
