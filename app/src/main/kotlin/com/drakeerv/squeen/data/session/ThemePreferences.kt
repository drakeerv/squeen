package com.drakeerv.squeen.data.session

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User theme preferences (Material You opt-in). */
class ThemePreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    companion object {
        private const val PREFS_FILE = "theme_prefs"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}
