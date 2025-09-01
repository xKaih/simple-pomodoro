package io.github.xkaih.simplepomodoro

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TimerManager(private val prefs: SharedPreferences) {

    companion object {
        private const val DEFAULT_WORK_TIME = 25 * 60 * 1000L // 25
        private const val DEFAULT_SHORT_REST = 5 * 60 * 1000L // 5
        private const val DEFAULT_LONG_REST = 25 * 60 * 1000L // 25
        private const val PREF_END_TIME = "endTime"
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

    private val _remainingTimeForRest = MutableStateFlow(DEFAULT_LONG_REST)
    private val _restingTime = MutableStateFlow(DEFAULT_SHORT_REST)
    private val _longRestingTime = MutableStateFlow(DEFAULT_LONG_REST)

    private var onFinishedTimer: (() -> Unit)? = null

    fun start(durationMs: Long = DEFAULT_WORK_TIME) {
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
                _timeLeft.value = remainingTime.coerceAtLeast(0)
            }

            _isRunning.value = false
            onFinishedTimer?.invoke()

            when (_timerState.value) {
                TimerState.WORK -> {
                    _timerState.value =
                        if (_remainingTimeForRest.value <= 0) TimerState.LONG_REST else TimerState.REST
                    val nextDuration = if (_timerState.value == TimerState.LONG_REST)
                        _longRestingTime.value else _restingTime.value
                    start(nextDuration)
                }
                else -> {
                    _timerState.value = TimerState.WORK
                    start(DEFAULT_WORK_TIME)
                }
            }
        }
    }
}
