package com.drakeerv.squeen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import com.drakeerv.squeen.di.appModule

class SpeedQueenApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) org.koin.core.logger.Level.INFO else org.koin.core.logger.Level.ERROR)
            androidContext(this@SpeedQueenApp)
            modules(appModule)
        }
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val washAlert = NotificationChannel(
            CHANNEL_WASH_ALERT,
            getString(R.string.channel_wash_alert),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = getString(R.string.channel_wash_alert_description) }
        val general = NotificationChannel(
            CHANNEL_GENERAL,
            getString(R.string.channel_general),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = getString(R.string.channel_general_description) }
        manager.createNotificationChannels(listOf(washAlert, general))
    }

    companion object {
        const val CHANNEL_WASH_ALERT = "wash_alerts"
        const val CHANNEL_GENERAL = "general"
    }
}
