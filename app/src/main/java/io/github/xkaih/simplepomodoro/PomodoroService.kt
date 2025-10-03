package io.github.xkaih.simplepomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.xkaih.simplepomodoro.TimerManager.Companion.POMODORO_CHANNEL_ID
import io.github.xkaih.simplepomodoro.TimerManager.Companion.POMODORO_UPDATE_NOTIFICATION_ID

class PomodoroService : Service() {

    companion object {
        var isRunning = false
    }
    private var timer: CountDownTimer? = null
    private var isPaused: Boolean = false
    private var timeLeft = 0L
    private var durationMs = 0L
    private var state = ""

    private var playSound = true
    private fun createChannel() {
        val channelList = listOf(

            NotificationChannel(
                POMODORO_CHANNEL_ID,
                "Pomodoro timer alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pomodoro"
                enableVibration(true)
                    },

            NotificationChannel(
                POMODORO_UPDATE_NOTIFICATION_ID,
                "Pomodoro timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pomodoro"
                setSound(null, null)
                enableVibration(false)
            }
        )

        for (channel in channelList) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
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

    override fun onCreate() {
        isRunning = true
        createChannel()
        super.onCreate()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PAUSE" -> pauseTimer()
            "RESET" -> resetTimer()
            "RESUME" -> resumeTimer()
            else -> {
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
        timer = object: CountDownTimer(durationMs, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished
                val tL = calculateTimeFromMs(timeLeft)


                val notification: Notification =
                    if (state == "WORK") {
                        buildNotification("Pomodoro Running", "Focus on your task: %02d:%02d".format(tL.first, tL.second))
                    }
                    else {
                        buildNotification("Nice work!", "Take a break: %02d:%02d".format(tL.first, tL.second))
                    }

                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(1, notification)

                if (playSound)
                    playSound = false

                val updateIntent = Intent("io.github.xkaih.simplepomodoro.TIMER_UPDATE")
                updateIntent.putExtra("MINUTES_LEFT", tL.first)
                updateIntent.putExtra("SECONDS_LEFT", tL.second)
                sendBroadcast(updateIntent)
            }

            override fun onFinish() {
                val finishIntent = Intent("io.github.xkaih.simplepomodoro.TIMER_FINISH")
                finishIntent.putExtra("PASSED_TIME", durationMs)
                playSound = true
                sendBroadcast(finishIntent)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.start()
    }

    private fun pauseTimer() {
        if (!isPaused) {
            timer?.cancel()
            isPaused = true
            val pauseIntent = Intent("io.github.xkaih.simplepomodoro.TIMER_PAUSE")
            pauseIntent.putExtra("PASSED_TIME", durationMs - timeLeft)
            sendBroadcast(pauseIntent)
        }
    }

    private fun resetTimer() {
        timer?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isPaused = false
        timeLeft = 0L
        durationMs = 0L
    }

    private fun resumeTimer() {
        if (isPaused) {
            durationMs = timeLeft
            startTimer()
            isPaused = false
        }
    }
    private fun startForegroundService() {
        val tL = calculateTimeFromMs(durationMs)
        startForeground(1, buildNotification("Pomodoro Running", "Focus on your task: %02d:%02d".format(tL.first, tL.second)))
        startTimer()
    }

    override fun onDestroy() {
        isRunning = false
        playSound = true
        timer?.cancel()
        super.onDestroy()
    }
}