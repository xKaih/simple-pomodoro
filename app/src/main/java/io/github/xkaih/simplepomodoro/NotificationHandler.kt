package io.github.xkaih.simplepomodoro

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.xkaih.simplepomodoro.TimerManager.Companion.POMODORO_CHANNEL_ID

class Notification(val title: String, val text: String, val onGoing: Boolean, val id: Int)

class NotificationHandler(
    private val notificationManager: NotificationManager,
    private val mainContext: Context
) {

    init {
        createChannel()
    }
    private fun createChannel() {
        val channel = NotificationChannel(
            POMODORO_CHANNEL_ID,
            "Pomodoro timer",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Pomodoro" }

        channel.enableVibration(true)
        notificationManager.createNotificationChannel(channel)
    }

    fun sendNotification(notification: Notification) {
        val builder = NotificationCompat.Builder(mainContext, POMODORO_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.text)
            .setOngoing(notification.onGoing)

        with(NotificationManagerCompat.from(mainContext)) {
            if (ActivityCompat.checkSelfPermission(
                    mainContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    mainContext as Activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
                return
            }
            notify(notification.id, builder.build())
        }
    }

    fun cancelNotification(id: Int) {
        NotificationManagerCompat.from(mainContext).cancel(id)
    }
}