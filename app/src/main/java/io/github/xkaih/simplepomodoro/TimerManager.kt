package io.github.xkaih.simplepomodoro

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TimerManager(private val prefs: SharedPreferences) {

    companion object {
        private var WORK_TIME = 25 * 60 * 1000L // 25
        private var SHORT_REST = 5 * 60 * 1000L // 5
        private var LONG_REST = 25 * 60 * 1000L // 25
        private var LONG_REST_THRESHOLD = 60 * 60 * 1000L // 60
        private const val PREF_END_TIME = "endTime"
        const val PREF_WORK_TIME = "workTime"
        const val PREF_SHORT_REST = "shortRest"
        const val PREF_LONG_REST = "longRest"
        const val PREF_LONG_REST_THRESHOLD = "longRestThreshold"

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

        WORK_TIME = prefs.getLong(PREF_WORK_TIME, WORK_TIME)
        SHORT_REST = prefs.getLong(PREF_SHORT_REST, SHORT_REST)
        LONG_REST = prefs.getLong(PREF_LONG_REST,LONG_REST)
        LONG_REST_THRESHOLD = prefs.getLong(PREF_LONG_REST_THRESHOLD, LONG_REST_THRESHOLD)

        onSharedPreferencesChangeListener = prefs.registerOnSharedPreferenceChangeListener { _, key ->
            Log.i("Preference changed", key.toString())
            when (key){
                PREF_WORK_TIME -> WORK_TIME = prefs.getLong(key, WORK_TIME)
                PREF_LONG_REST -> LONG_REST = prefs.getLong(key, LONG_REST)
                PREF_SHORT_REST -> SHORT_REST = prefs.getLong(key, SHORT_REST)
                PREF_LONG_REST_THRESHOLD -> LONG_REST_THRESHOLD = prefs.getLong(key, LONG_REST_THRESHOLD)
                PREF_END_TIME -> {return@registerOnSharedPreferenceChangeListener}
            }
            reset()
        }
    }

    fun start(durationMs: Long = WORK_TIME) {
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
        job?.cancel()
        _isRunning.value = false
        if (resetTime) {
            _timeLeft.value = 0L
            remainingTime = 0L
        } else {
            remainingTime = _timeLeft.value
        }
        prefs.edit { remove(PREF_END_TIME) }
    }

    private fun runTimer() {
        job?.cancel()
        job = scope.launch {
            while (remainingTime > 0 && isActive) {
                delay(1000)
                remainingTime -= 1000
                LONG_REST_THRESHOLD -= 1000
                _timeLeft.value = remainingTime.coerceAtLeast(0)
            }

            _isRunning.value = false
            onFinishedTimer?.invoke()

            when (_timerState.value) {
                TimerState.WORK -> {
                    _timerState.value =
                        if (LONG_REST_THRESHOLD <= 0)
                            TimerState.LONG_REST
                        else
                            TimerState.REST

                    val nextDuration = if (_timerState.value == TimerState.LONG_REST)
                        LONG_REST
                    else
                        SHORT_REST

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