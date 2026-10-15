package com.drakeerv.squeen.data.session

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Session and lightweight application state, persisted in SharedPreferences
 * (mirrors the original `ur0` preference helper).
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(prefs.getString(KEY_TOKEN, "").orEmpty())
    val token: String get() = _token.value

    private val _userId = MutableStateFlow(prefs.getString(KEY_USER_ID, "").orEmpty())
    val userId: String get() = _userId.value

    private val _organizationId = MutableStateFlow(prefs.getString(KEY_ORGANIZATION_ID, "").orEmpty())
    val organizationId: String get() = _organizationId.value

    private val _pushToken = MutableStateFlow(ensureDeviceToken())
    val pushToken: String get() = _pushToken.value

    /**
     * Device identifier sent as `littleDuck` on login/register/logout, mirroring
     * the original client: Android ID + hard-coded flavour suffix.
     */
    val deviceId: String by lazy {
        val androidId = runCatching {
            android.provider.Settings.Secure.getString(
                context.applicationContext.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )
        }.getOrNull().orEmpty()
        androidId + DEVICE_SUFFIX
    }

    private val _loggedIn = MutableStateFlow(isLoggedIn)
    val loggedInFlow: StateFlow<Boolean> = _loggedIn.asStateFlow()

    val isLoggedIn: Boolean get() = _token.value.isNotEmpty() && _userId.value.isNotEmpty()

    fun setAuth(token: String?, userId: String?) {
        _token.value = token.orEmpty()
        _userId.value = userId.orEmpty()
        prefs.edit()
            .putString(KEY_TOKEN, _token.value)
            .putString(KEY_USER_ID, _userId.value)
            .apply()
        _loggedIn.value = isLoggedIn
    }

    fun setUserId(userId: String?) {
        _userId.value = userId.orEmpty()
        prefs.edit().putString(KEY_USER_ID, _userId.value).apply()
        _loggedIn.value = isLoggedIn
    }

    fun setOrganizationId(organizationId: String?) {
        _organizationId.value = organizationId.orEmpty()
        prefs.edit().putString(KEY_ORGANIZATION_ID, _organizationId.value).apply()
    }

    var lastGeoBoundaryId: String?
        get() = prefs.getString(KEY_LAST_GEO_BOUNDARY_ID, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_GEO_BOUNDARY_ID, value).apply()
        }

    /** Raw JSON cache of the last known user payload (fast cold start). */
    var cachedUserJson: String?
        get() = prefs.getString(KEY_CACHED_USER, null)
        set(value) {
            prefs.edit().putString(KEY_CACHED_USER, value).apply()
        }

    fun setPushTokenRegistered(token: String) {
        prefs.edit().putString(KEY_PUSH_TOKEN_REGISTERED, token).apply()
    }

    val pushTokenRegistered: String
        get() = prefs.getString(KEY_PUSH_TOKEN_REGISTERED, "").orEmpty()

    /** Pending payment paths: "<path>|<connectionToken>". */
    fun pendingPayments(): Set<String> = prefs.getStringSet(KEY_PENDING_PAYMENTS, null).orEmpty().toSet()

    fun addPendingPayment(pathWithToken: String) {
        val current = pendingPayments().toMutableSet()
        current.add(pathWithToken)
        prefs.edit().putStringSet(KEY_PENDING_PAYMENTS, current).apply()
    }

    fun removePendingPayment(path: String) {
        val current = pendingPayments().toMutableSet()
        current.remove(path)
        current.removeAll { it.substringBefore("|") == path }
        prefs.edit().putStringSet(KEY_PENDING_PAYMENTS, current).apply()
    }

    var languageData: String?
        get() = prefs.getString(KEY_LANGUAGE_DATA, null)
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE_DATA, value).apply()
        }

    /** "email" or "phone", how the user signed in; drives verification routing. */
    var loginMode: String
        get() = prefs.getString(KEY_LOGIN_MODE, "email") ?: "email"
        set(value) {
            prefs.edit().putString(KEY_LOGIN_MODE, value).apply()
        }

    /** Clears all user-scoped data, keeping the device token. */
    fun clear() {
        val deviceToken = _pushToken.value
        prefs.edit().clear().apply()
        _token.value = ""
        _userId.value = ""
        _organizationId.value = ""
        _pushToken.value = deviceToken
        prefs.edit().putString(KEY_DEVICE_TOKEN, deviceToken).apply()
        _loggedIn.value = false
    }

    private fun ensureDeviceToken(): String {
        prefs.getString(KEY_DEVICE_TOKEN, null)?.takeIf { it.isNotEmpty() }?.let { return it }
        val generated = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_DEVICE_TOKEN, generated).apply()
        return generated
    }

    companion object {
        private const val PREFS_FILE = "speedqueen_session"
        private const val KEY_TOKEN = "TOKEN"
        private const val KEY_USER_ID = "USER_ID"
        private const val KEY_ORGANIZATION_ID = "CURRENT_ORGANIZATION_ID"
        private const val KEY_LAST_GEO_BOUNDARY_ID = "LAST_GEO_BOUNDARY_ID"
        private const val KEY_CACHED_USER = "CACHED_USER"
        private const val KEY_PUSH_TOKEN_REGISTERED = "FCM_TOKEN_REGISTERED"
        private const val KEY_PENDING_PAYMENTS = "PENDING_PAYMENT"
        private const val KEY_LANGUAGE_DATA = "LANGUAGE_DATA"
        private const val KEY_LOGIN_MODE = "LOGIN_MODE"
        private const val KEY_DEVICE_TOKEN = "DEVICE_TOKEN"
        private const val DEVICE_SUFFIX = "prodCustomer_app_speed_queen"
    }
}
