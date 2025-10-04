package io.github.xkaih.simplepomodoro

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import io.github.xkaih.simplepomodoro.TimerManager.Companion.POMODORO_CHANNEL_ID
import io.github.xkaih.simplepomodoro.TimerManager.Companion.POMODORO_UPDATE_NOTIFICATION_ID

class PomodoroService : Service() {

    companion object {
        var isRunning = false
    }
    private var isPaused: Boolean = false
    private var timeLeft = 0L
    private var durationMs = 0L
    private var state = ""

    private var playSound = true

    private var tickHandler: Handler? = null
    private var tickRunnable: Runnable? = null
    private var startElapsedAt = 0L
    private var endElapsedAt = 0L
    private fun createChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Recreate channels to ensure updated vibration pattern applies
        try {
            notificationManager.deleteNotificationChannel(POMODORO_CHANNEL_ID)
        } catch (_: Exception) {}
        try {
            notificationManager.deleteNotificationChannel(POMODORO_UPDATE_NOTIFICATION_ID)
        } catch (_: Exception) {}

        val alertChannel = NotificationChannel(
            POMODORO_CHANNEL_ID,
            "Pomodoro timer alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pomodoro"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1500) // single long vibration
        }

        val updateChannel = NotificationChannel(
            POMODORO_UPDATE_NOTIFICATION_ID,
            "Pomodoro timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Pomodoro"
            setSound(null, null)
            enableVibration(false)
        }

        notificationManager.createNotificationChannel(alertChannel)
        notificationManager.createNotificationChannel(updateChannel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        // Implementation for starting the service in the foreground with a notification
        val channelId = if (playSound) POMODORO_CHANNEL_ID else POMODORO_UPDATE_NOTIFICATION_ID

        // Create a notification and start the service in the foreground
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)


        return builder.build()
    }

    private fun calculateTimeFromMs(millis: Long): Pair<Long, Long> {
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        return Pair(minutes, seconds)
    }

    private fun getAlarmManager(): AlarmManager = getSystemService(AlarmManager::class.java)

    private fun getAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, TimerAlarmReceiver::class.java).apply {
            action = "ALARM"
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(this, 0, intent, flags)
    }

    private fun scheduleAlarm(triggerAtElapsed: Long) {
        val am = getAlarmManager()
        try {
            val canExact = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, getAlarmPendingIntent())
            } else {
                // Fallback to inexact while we guide the user to enable exact alarms
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, getAlarmPendingIntent())
                notifyExactAlarmNeeded()
            }
        } catch (se: SecurityException) {
            // Graceful fallback if permission not granted
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, getAlarmPendingIntent())
            notifyExactAlarmNeeded()
        }
    }

    private fun notifyExactAlarmNeeded() {
        // Nudge the user via a low-importance notification that exact alarms should be enabled
        val n = NotificationCompat.Builder(this, POMODORO_UPDATE_NOTIFICATION_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Enable exact alarms")
            .setContentText("Allow exact alarms in settings for reliable Pomodoro timing")
            .setOngoing(false)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(2, n)
    }

    private fun cancelAlarm() {
        val am = getAlarmManager()
        am.cancel(getAlarmPendingIntent())
    }

    private fun startTickUpdates() {
        if (tickHandler == null) tickHandler = Handler(Looper.getMainLooper())
        val notificationManager = getSystemService(NotificationManager::class.java)
        tickRunnable = Runnable {
            val now = SystemClock.elapsedRealtime()
            timeLeft = (endElapsedAt - now).coerceAtLeast(0L)
            val tL = calculateTimeFromMs(timeLeft)

            val notification: Notification =
                if (state == "WORK") {
                    buildNotification("Pomodoro Running", "Focus on your task: %02d:%02d".format(tL.first, tL.second))
                } else {
                    buildNotification("Nice work!", "Take a break: %02d:%02d".format(tL.first, tL.second))
                }
            notificationManager.notify(1, notification)

            if (playSound) playSound = false

            val updateIntent = Intent("io.github.xkaih.simplepomodoro.TIMER_UPDATE").apply {
                putExtra("MINUTES_LEFT", tL.first)
                putExtra("SECONDS_LEFT", tL.second)
            }
            sendBroadcast(updateIntent)

            if (timeLeft <= 0L) {
                onAlarmFired()
            } else {
                tickHandler?.postDelayed(tickRunnable!!, 1000)
            }
        }
        // Kick off updates immediately
        tickHandler?.post(tickRunnable!!)
    }

    private fun stopTickUpdates() {
        tickHandler?.removeCallbacksAndMessages(null)
        tickRunnable = null
        tickHandler = null
    }

    private fun onAlarmFired() {
        stopTickUpdates()
        cancelAlarm()
        val finishIntent = Intent("io.github.xkaih.simplepomodoro.TIMER_FINISH").apply {
            putExtra("PASSED_TIME", durationMs)
        }
        playSound = true
        sendBroadcast(finishIntent)
        // Keep the service alive to avoid background FGS start restrictions.
        // The next phase will be started by TimerManager using startService since we are already running.
    }

    override fun onCreate() {
        isRunning = true
        createChannel()
        super.onCreate()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                durationMs = intent.getLongExtra("DURATION_MS", 25 * 60 * 1000L)
                state = intent.getStringExtra("STATE") ?: "WORK"
                startForegroundService()
            }
            "PAUSE" -> pauseTimer()
            "RESET" -> resetTimer()
            "RESUME" -> resumeTimer()
            "ALARM_FIRED" -> onAlarmFired()
            else -> {
                // Fallback to START if no action provided
                durationMs = intent?.getLongExtra("DURATION_MS", 25 * 60 * 1000L) ?: return START_NOT_STICKY
                state = intent.getStringExtra("STATE") ?: "WORK"
                startForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
        super.onTimeout(startId, fgsType)
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun startTimer() {
        // Initialize timing references
        startElapsedAt = SystemClock.elapsedRealtime()
        endElapsedAt = startElapsedAt + durationMs
        // Schedule exact alarm for when the session should end (works with Doze)
        scheduleAlarm(endElapsedAt)
        // Begin UI/broadcast updates
        startTickUpdates()
    }

    private fun pauseTimer() {
        if (!isPaused) {
            stopTickUpdates()
            // Compute remaining time based on endElapsedAt
            val now = SystemClock.elapsedRealtime()
            timeLeft = (endElapsedAt - now).coerceAtLeast(0L)
            cancelAlarm()
            isPaused = true
            val pauseIntent = Intent("io.github.xkaih.simplepomodoro.TIMER_PAUSE")
            pauseIntent.putExtra("PASSED_TIME", durationMs - timeLeft)
            sendBroadcast(pauseIntent)
        }
    }

    private fun resetTimer() {
        stopTickUpdates()
        cancelAlarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isPaused = false
        timeLeft = 0L
        durationMs = 0L
        startElapsedAt = 0L
        endElapsedAt = 0L
        playSound = true
    }

    private fun resumeTimer() {
        if (isPaused) {
            durationMs = timeLeft
            isPaused = false
            startTimer()
        }
    }
    private fun startForegroundService() {
        val tL = calculateTimeFromMs(durationMs)
        val title = if (state == "WORK") "Pomodoro Running" else if (state == "REST") "Nice work!" else "Pomodoro"
        val subtitle = if (state == "WORK") "Focus on your task: %02d:%02d".format(tL.first, tL.second) else "Take a break: %02d:%02d".format(tL.first, tL.second)
        startForeground(1, buildNotification(title, subtitle))
        startTimer()
    }

    override fun onDestroy() {
        isRunning = false
        playSound = true
        stopTickUpdates()
        cancelAlarm()
        super.onDestroy()
    }
}