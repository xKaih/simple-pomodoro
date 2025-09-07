package io.github.xkaih.simplepomodoro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TimerManager(private val prefs: SharedPreferences, private val notificationManager: NotificationManager, private val mainContext: ComponentActivity) {

    companion object {
        private var workTime = 25 * 60 * 1000L // 25
        private var shortRest = 5 * 60 * 1000L // 5
        private var longRest = 25 * 60 * 1000L // 25
        private var longRestThreshold = 60 * 60 * 1000L // 60
        private const val PREF_END_TIME = "endTime"
        const val PREF_WORK_TIME = "workTime"
        const val PREF_SHORT_REST = "shortRest"
        const val PREF_LONG_REST = "longRest"
        const val PREF_LONG_REST_THRESHOLD = "longRestThreshold"

        const val POMODORO_CHANNEL_ID = "pomodoroChannel"

        val DEFAULT_PREFERENCES_MAP = mapOf<String, Long>(
            PREF_WORK_TIME to 25 * 60 * 1000L,
            PREF_SHORT_REST to 5 * 60 * 1000L,
            PREF_LONG_REST to 25 * 60 * 1000L,
            PREF_LONG_REST_THRESHOLD to 60 * 60 * 1000

        )
    }

    enum class TimerState { WORK, REST, LONG_REST }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var remainingTime: Long = 0L

    private val _timeLeft = MutableStateFlow(0L)
    val timeLeft: StateFlow<Long> = _timeLeft

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _timerState = MutableStateFlow(TimerState.WORK)
    val timerState: StateFlow<TimerState> = _timerState

    private var onSharedPreferencesChangeListener: Unit? = null
    private var onFinishedTimer: (() -> Unit)? = null

    init {
        createChannel()
        workTime = prefs.getLong(PREF_WORK_TIME, workTime)
        shortRest = prefs.getLong(PREF_SHORT_REST, shortRest)
        longRest = prefs.getLong(PREF_LONG_REST,longRest)
        longRestThreshold = prefs.getLong(PREF_LONG_REST_THRESHOLD, longRestThreshold)

        onSharedPreferencesChangeListener = prefs.registerOnSharedPreferenceChangeListener { _, key ->
            Log.i("Preference changed", key.toString())
            when (key){
                PREF_WORK_TIME -> workTime = prefs.getLong(key, workTime)
                PREF_LONG_REST -> longRest = prefs.getLong(key, longRest)
                PREF_SHORT_REST -> shortRest = prefs.getLong(key, shortRest)
                PREF_LONG_REST_THRESHOLD -> longRestThreshold = prefs.getLong(key, longRestThreshold)
                PREF_END_TIME -> {return@registerOnSharedPreferenceChangeListener}
            }
            reset()
        }
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

    private fun sendNotification(title: String, text: String, onGoing: Boolean, id: Int){
        var builder = NotificationCompat.Builder(mainContext, POMODORO_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(onGoing)

        with(NotificationManagerCompat.from(mainContext)){
            if (ActivityCompat.checkSelfPermission(mainContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    mainContext,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
                return
            }
            notify(id,builder.build())
        }

    }

    fun start(durationMs: Long = workTime) {
        stopTimer(resetTime = false)

        remainingTime = durationMs
        _timeLeft.value = remainingTime
        _isRunning.value = true

        val endTime = System.currentTimeMillis() + remainingTime
        prefs.edit { putLong(PREF_END_TIME, endTime) }

        runTimer()
    }

    fun resume() {
        if (remainingTime <= 0L) return

        val endTime = System.currentTimeMillis() + remainingTime
        prefs.edit { putLong(PREF_END_TIME, endTime) }

        _isRunning.value = true
        runTimer()
    }
    fun pause() = stopTimer(resetTime = false)

    fun reset() = stopTimer(resetTime = true)

    fun setOnTimerFinishedListener(callback: () -> Unit) {
        onFinishedTimer = callback
    }

    fun cancelAll() {
        scope.cancel()
    }

    private fun stopTimer(resetTime: Boolean) {
        if (_timerState.value == TimerState.WORK) {
            NotificationManagerCompat.from(mainContext).cancel(1)
        }
        else {
            NotificationManagerCompat.from(mainContext).cancel(2)
        }
        job?.cancel()
        _isRunning.value = false
        if (resetTime) {
            _timeLeft.value = 0L
            remainingTime = 0L
            longRestThreshold = prefs.getLong(PREF_LONG_REST_THRESHOLD, DEFAULT_PREFERENCES_MAP[PREF_LONG_REST_THRESHOLD]!!)
        } else {
            remainingTime = _timeLeft.value
        }
        prefs.edit { remove(PREF_END_TIME) }
    }

    private fun runTimer() {
        job?.cancel()
        job = scope.launch {

            if (_timerState.value == TimerState.WORK) {
                sendNotification("Lets do some work", ":D", true, 1)
                NotificationManagerCompat.from(mainContext).cancel(2)
            }
            else {
                sendNotification("Take a break", "You deserve it", true, 2)
                NotificationManagerCompat.from(mainContext).cancel(1)
            }

            while (remainingTime > 0 && isActive) {
                delay(1000)
                remainingTime -= 1000
                longRestThreshold -= 1000
                _timeLeft.value = remainingTime.coerceAtLeast(0)
            }

            _isRunning.value = false
            onFinishedTimer?.invoke()

            when (_timerState.value) {
                TimerState.WORK -> {
                    _timerState.value =
                        if (longRestThreshold <= 0)
                            TimerState.LONG_REST
                        else
                            TimerState.REST

                    val nextDuration = if (_timerState.value == TimerState.LONG_REST)
                        longRest
                    else
                        shortRest

                    start(nextDuration)
                }
                else -> {
                    _timerState.value = TimerState.WORK
                    start()
                }
            }
        }
    }
}