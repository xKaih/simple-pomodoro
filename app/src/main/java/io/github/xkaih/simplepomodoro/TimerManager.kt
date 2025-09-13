package io.github.xkaih.simplepomodoro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.icu.util.TimeUnit
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.unit.Constraints
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
class TimerManager(
    private val prefs: SharedPreferences,
    private val notificationHandler: NotificationHandler
) {

    companion object {
        private var workTime = 25 * 60 * 1000L
        private var shortRest = 5 * 60 * 1000L
        private var longRest = 25 * 60 * 1000L
        private var longRestThreshold = 60 * 60 * 1000L

        private const val PREF_END_TIME = "endTime"
        private const val PREF_PHASE_START_TIME = "phaseStartTime"   // runtime only
        private const val PREF_TIMER_STATE = "timerState"            // runtime only

        const val PREF_WORK_TIME = "workTime"
        const val PREF_SHORT_REST = "shortRest"
        const val PREF_LONG_REST = "longRest"
        const val PREF_LONG_REST_THRESHOLD = "longRestThreshold"

        const val POMODORO_CHANNEL_ID = "pomodoroChannel"
        val START_NOTIFICATION = Notification(
            "Let's do some work",
            ":D",
            true,
            1
        )

        val REST_NOTIFICATION = Notification(
            "Take a break",
            "You deserve it",
            true,
            2
        )

        var nextNotification: Notification = START_NOTIFICATION

        val DEFAULT_PREFERENCES_MAP = mapOf(
            PREF_WORK_TIME to 25 * 60 * 1000L,
            PREF_SHORT_REST to 5 * 60 * 1000L,
            PREF_LONG_REST to 25 * 60 * 1000L,
            PREF_LONG_REST_THRESHOLD to 60 * 60 * 1000L
        )
    }

    enum class TimerState { WORK, REST, LONG_REST }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var remainingTime: Long = 0L
    private var lastNow: Long = System.currentTimeMillis()

    private val _timeLeft = MutableStateFlow(0L)
    val timeLeft: StateFlow<Long> = _timeLeft

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _timerState = MutableStateFlow(TimerState.WORK)
    val timerState: StateFlow<TimerState> = _timerState

    private var onFinishedTimer: (() -> Unit)? = null

    // Register the listener object so we can unregister later.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        when (key) {
            // React only to user settings changes (update the local variables and reset)
            PREF_WORK_TIME -> {
                workTime = sharedPrefs.getLong(key, workTime)
                reset()
            }
            PREF_SHORT_REST -> {
                shortRest = sharedPrefs.getLong(key, shortRest)
                reset()
            }
            PREF_LONG_REST -> {
                longRest = sharedPrefs.getLong(key, longRest)
                reset()
            }
            PREF_LONG_REST_THRESHOLD -> {
                // This is the user setting for the threshold (the session counter is different)
                longRestThreshold = sharedPrefs.getLong(key, longRestThreshold)
                reset()
            }
            // Ignore runtime keys and other unrelated keys
            PREF_END_TIME, PREF_PHASE_START_TIME, PREF_TIMER_STATE -> {
            }
            else -> {
                // ignore any other keys (safety)
            }
        }
    }

    init {
        // Load user settings on init.
        workTime = prefs.getLong(PREF_WORK_TIME, workTime)
        shortRest = prefs.getLong(PREF_SHORT_REST, shortRest)
        longRest = prefs.getLong(PREF_LONG_REST, longRest)
        longRestThreshold = prefs.getLong(PREF_LONG_REST_THRESHOLD, longRestThreshold)

        // Register the listener object (only reacts to settings keys now).
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    // ---- Start / Resume / Pause / Reset ----

    fun start(durationMs: Long = workTime) {
        stopTimer(resetTime = false)

        remainingTime = durationMs
        _timeLeft.value = remainingTime
        _isRunning.value = true

        val now = System.currentTimeMillis()
        lastNow = now
        val endTime = now + remainingTime

        // Save runtime state to preferences (used to restore after process pause/kill)
        prefs.edit {
            putLong(PREF_END_TIME, endTime)
            putLong(PREF_PHASE_START_TIME, now)
            putString(PREF_TIMER_STATE, _timerState.value.name)
        }

        runTimer()
    }

    fun resume() {
        if (remainingTime <= 0L) return
        val now = System.currentTimeMillis()
        lastNow = now
        val endTime = now + remainingTime
        prefs.edit {
            putLong(PREF_END_TIME, endTime)
            putLong(PREF_PHASE_START_TIME, now)
            putString(PREF_TIMER_STATE, _timerState.value.name)
        }
        _isRunning.value = true
        runTimer()
    }

    fun pause() = stopTimer(resetTime = false)
    fun reset() = stopTimer(resetTime = true)

    fun setOnTimerFinishedListener(callback: () -> Unit) {
        onFinishedTimer = callback
    }

    /**
     * Cancel all coroutines and unregister the prefs listener.
     * Call this when the manager is destroyed to avoid leaks.
     */
    fun cancelAll() {
        scope.cancel()
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) { /* ignore */ }
    }

    private fun stopTimer(resetTime: Boolean) {
        if (_timerState.value == TimerState.WORK)
            notificationHandler.cancelNotification(1)
        else
            notificationHandler.cancelNotification(2)

        job?.cancel()
        _isRunning.value = false

        if (resetTime) {
            // Reset timer and restore the configured threshold
            _timeLeft.value = 0L
            remainingTime = 0L
            _timerState.value = TimerState.WORK
            longRestThreshold = prefs.getLong(
                PREF_LONG_REST_THRESHOLD,
                DEFAULT_PREFERENCES_MAP[PREF_LONG_REST_THRESHOLD]!!
            )
        } else {
            // Keep current remaining time
            remainingTime = _timeLeft.value
        }

        // Remove runtime-only values from prefs (listener ignores these keys)
        prefs.edit {
            remove(PREF_END_TIME)
            remove(PREF_PHASE_START_TIME)
            remove(PREF_TIMER_STATE)
        }
    }

    // ---- Timer loop ----

    private fun runTimer() {
        job?.cancel()
        job = scope.launch {

            // Show the correct persistent notification for the current phase
            if (_timerState.value == TimerState.WORK) {
                nextNotification = START_NOTIFICATION
                notificationHandler.cancelNotification(2)
            } else {
                nextNotification = REST_NOTIFICATION
                notificationHandler.cancelNotification(1)
            }

            notificationHandler.sendNotification(nextNotification)

            // Use persisted endTime as single source of truth
            val endTime = prefs.getLong(PREF_END_TIME, System.currentTimeMillis() + remainingTime)

            while (isActive) {
                val now = System.currentTimeMillis()
                val timeLeftNow = endTime - now
                _timeLeft.value = timeLeftNow.coerceAtLeast(0L)
                remainingTime = _timeLeft.value

                // Decrease the session threshold only while working, using real elapsed time
                if (_timerState.value == TimerState.WORK) {
                    val delta = (now - lastNow).coerceAtLeast(0L)
                    longRestThreshold = (longRestThreshold - delta).coerceAtLeast(0L)
                }
                lastNow = now

                if (timeLeftNow <= 0L) break
                delay(1000L)
            }

            _isRunning.value = false
            onFinishedTimer?.invoke()

            // State transitions
            when (_timerState.value) {
                TimerState.WORK -> {
                    _timerState.value =
                        if (longRestThreshold <= 0L) TimerState.LONG_REST else TimerState.REST

                    val nextDuration = if (_timerState.value == TimerState.LONG_REST) longRest else shortRest
                    start(nextDuration)
                }
                else -> {
                    if (_timerState.value == TimerState.LONG_REST) {
                        // Reset the threshold to configured value after a long rest
                        longRestThreshold = prefs.getLong(
                            PREF_LONG_REST_THRESHOLD,
                            DEFAULT_PREFERENCES_MAP[PREF_LONG_REST_THRESHOLD]!!
                        )
                    }
                    _timerState.value = TimerState.WORK
                    start(workTime)
                }
            }
        }
    }

    // ---- Restore state after reopening ----

    fun restoreState() {
        // Restore the runtime phase if present
        val savedState = prefs.getString(PREF_TIMER_STATE, null)
        if (savedState != null) {
            _timerState.value = runCatching { TimerState.valueOf(savedState) }.getOrElse { TimerState.WORK }
        }

        val endTime = prefs.getLong(PREF_END_TIME, -1L)
        if (endTime <= 0L) {
            _timeLeft.value = 0L
            _isRunning.value = false
            return
        }

        val now = System.currentTimeMillis()
        val left = endTime - now

        if (left > 0L) {
            // Timer still running -> recalculate remaining time
            remainingTime = left
            _timeLeft.value = remainingTime
            _isRunning.value = true

            // If we were in WORK, deduct elapsed time of this phase from the threshold
            if (_timerState.value == TimerState.WORK) {
                val phaseStart = prefs.getLong(PREF_PHASE_START_TIME, now)
                val elapsedWork = (now - phaseStart).coerceAtLeast(0L)
                longRestThreshold = (longRestThreshold - elapsedWork).coerceAtLeast(0L)
            }

            lastNow = now
            runTimer()
        } else {
            // The phase finished while the app was not active -> force the transition
            _isRunning.value = false
            _timeLeft.value = 0L

            when (_timerState.value) {
                TimerState.WORK -> {
                    _timerState.value = if (longRestThreshold <= 0L) TimerState.LONG_REST else TimerState.REST
                    val nextDuration = if (_timerState.value == TimerState.LONG_REST) longRest else shortRest
                    start(nextDuration)
                }
                else -> {
                    if (_timerState.value == TimerState.LONG_REST) {
                        longRestThreshold = prefs.getLong(
                            PREF_LONG_REST_THRESHOLD,
                            DEFAULT_PREFERENCES_MAP[PREF_LONG_REST_THRESHOLD]!!
                        )
                    }
                    _timerState.value = TimerState.WORK
                    start(workTime)
                }
            }
        }
    }
}
