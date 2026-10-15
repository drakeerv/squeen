package com.drakeerv.squeen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.drakeerv.squeen.ui.components.LocalSnackbarHost
import com.drakeerv.squeen.ui.navigation.SpeedQueenNavHost
import androidx.compose.runtime.getValue
import com.drakeerv.squeen.data.session.ThemePreferences
import com.drakeerv.squeen.ui.theme.SpeedQueenTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        // Navigation state is only restored when it was written by this exact build.
        // Without this, a back stack saved by an older version can reference routes that
        // no longer exist (e.g. after a rename), which renders as a blank screen.
        val statePrefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val installStamp = runCatching {
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        val lastStamp = statePrefs.getLong(KEY_NAV_STATE_STAMP, 0L)
        val restoreSavedState = savedInstanceState != null && lastStamp == installStamp
        if (!restoreSavedState) {
            statePrefs.edit().putLong(KEY_NAV_STATE_STAMP, installStamp).apply()
        }

        super.onCreate(if (restoreSavedState) savedInstanceState else null)
        setContent {
            val themePreferences = koinInject<ThemePreferences>()
            val dynamicColor by themePreferences.dynamicColor.collectAsStateWithLifecycle()
            SpeedQueenTheme(dynamicColor = dynamicColor) {
                val snackbarHostState = remember { SnackbarHostState() }
                val navController = rememberNavController()

                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSnackbarHost provides snackbarHostState
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        containerColor = MaterialTheme.colorScheme.background,
                    ) { _ ->
                        SpeedQueenNavHost(navController = navController)
                    }
                }
            }
        }
    }

    companion object {
        private const val KEY_NAV_STATE_STAMP = "nav_state_stamp"
    }
}
