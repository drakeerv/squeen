package com.drakeerv.squeen.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drakeerv.squeen.BuildConfig
import com.drakeerv.squeen.MainActivity
import com.drakeerv.squeen.R

/**
 * Local wash-alert notifications.
 *
 * The production app receives machine-complete and wash-alert pushes through FCM; this
 * build has no Firebase project attached, so alerts are raised locally from the live
 * WebSocket status stream while the app process is alive (best effort).
 */
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_WASH_ALERTS,
                    context.getString(R.string.channel_wash_alert),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.channel_wash_alert_description) },
                NotificationChannel(
                    CHANNEL_GENERAL,
                    context.getString(R.string.channel_general),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_general_description) },
            )
        )
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return manager.areNotificationsEnabled()
    }

    fun notifyWashAlert(machineName: String, minutesLeft: Int) {
        notify(
            id = machineName.hashCode() and 0x0FFFFFFF,
            title = "Almost done",
            message = "$machineName has about $minutesLeft min left.",
        )
    }

    fun notifyMachineComplete(machineName: String) {
        notify(
            id = (machineName.hashCode() and 0x0FFFFFFF) + 1,
            title = "Cycle complete",
            message = "Your laundry in $machineName is done.",
        )
    }

    fun notify(title: String, message: String) {
        notify(id = System.currentTimeMillis().toInt(), title = title, message = message)
    }

    private fun notify(id: Int, title: String, message: String) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_WASH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_wash)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setChannelId(CHANNEL_WASH_ALERTS)
            .build()
        runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val CHANNEL_WASH_ALERTS = "wash_alerts"
        const val CHANNEL_GENERAL = "general"
    }
}
