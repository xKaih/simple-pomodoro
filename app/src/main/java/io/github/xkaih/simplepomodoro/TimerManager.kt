package io.github.xkaih.simplepomodoro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
class TimerManager(
    private val context: Context,
    private val prefs: SharedPreferences
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
        const val POMODORO_UPDATE_NOTIFICATION_ID = "pomodoroUpdateChannel"

        val DEFAULT_PREFERENCES_MAP = mapOf(
            PREF_WORK_TIME to 25 * 60 * 1000L,
            PREF_SHORT_REST to 5 * 60 * 1000L,
            PREF_LONG_REST to 25 * 60 * 1000L,
            PREF_LONG_REST_THRESHOLD to 60 * 60 * 1000L
        )
    }

    val serviceReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "io.github.xkaih.simplepomodoro.TIMER_UPDATE") {
                _timeLeft.value = intent.getLongExtra("MINUTES_LEFT", 0L) to (intent.getLongExtra("SECONDS_LEFT", 0L))
            }
            else if (intent?.action == "io.github.xkaih.simplepomodoro.TIMER_FINISH") {
                workedTime += if (_timerState.value == TimerState.WORK) intent.getLongExtra("PASSED_TIME", 0L) else 0L
                _isRunning.value = false
                mustRest = !mustRest
                start()
            }
            else if (intent?.action == "io.github.xkaih.simplepomodoro.TIMER_PAUSE") {
                workedTime += if (_timerState.value == TimerState.WORK) intent.getLongExtra("PASSED_TIME", 0L) else 0L
                _isRunning.value = false
            }
        }
    }
    enum class TimerState { WORK, REST, LONG_REST }

    private var mustRest = false
    private var workedTime = 0L
    private val _timeLeft = MutableStateFlow(Pair(0L, 0L))
    val timeLeft: StateFlow<Pair<Long, Long>> = _timeLeft

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _timerState = MutableStateFlow(TimerState.WORK)
    val timerState: StateFlow<TimerState> = _timerState

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

    // Init pomodoro settings from preferences
    init {
        // Load user settings on init.
        workTime = prefs.getLong(PREF_WORK_TIME, workTime)
        shortRest = prefs.getLong(PREF_SHORT_REST, shortRest)
        longRest = prefs.getLong(PREF_LONG_REST, longRest)
        longRestThreshold = prefs.getLong(PREF_LONG_REST_THRESHOLD, longRestThreshold)

        // Register the listener object (only reacts to settings keys now).
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        val filter = IntentFilter().apply {
            addAction("io.github.xkaih.simplepomodoro.TIMER_UPDATE")
            addAction("io.github.xkaih.simplepomodoro.TIMER_FINISH")
            addAction("io.github.xkaih.simplepomodoro.TIMER_PAUSE")
        }
        ContextCompat.registerReceiver(
            context,
            serviceReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun sendServiceCommand(action: String, durationMs: Long? = null, state: TimerState? = null) {
        val intent = Intent(context, PomodoroService::class.java)
        intent.action = action
        durationMs?.let { intent.putExtra("DURATION_MS", it) }
        state?.let { intent.putExtra("STATE", it.name) }
        ContextCompat.startForegroundService(context, intent)
    }
    fun start(durationMs: Long = workTime) {
        if (workedTime >= longRestThreshold && mustRest) {
            _timerState.value = TimerState.LONG_REST
            workedTime = 0L
        } else if (mustRest) {
            _timerState.value = TimerState.REST
        }
        else
            _timerState.value = TimerState.WORK

        val duration = when (_timerState.value) {
            TimerState.WORK -> workTime
            TimerState.REST -> shortRest
            TimerState.LONG_REST -> longRest
        }
        _isRunning.value = true
        sendServiceCommand("START", duration, _timerState.value)
    }

    fun resume() {
        _isRunning.value = true
        sendServiceCommand("RESUME")
    }

    fun pause() = stopTimer(resetTime = false)
    fun reset() = stopTimer(resetTime = true)
    private fun stopTimer(resetTime: Boolean) {
        if(!PomodoroService.isRunning)
            return

        if (resetTime) {
            sendServiceCommand("RESET")
            _timeLeft.value = Pair(0L, 0L)
            _isRunning.value = false
            mustRest = false
            _timerState.value = TimerState.WORK
            workedTime = 0L
        }
        else {
            sendServiceCommand("PAUSE")
            _isRunning.value = false
        }
    }
}
