package io.github.xkaih.simplepomodoro

import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.core.content.edit

class TimerManager(private val prefs: SharedPreferences) {
    private var job: Job? = null
    private var remainingTime: Long = 0L
    private val _timeLeft = MutableStateFlow(0L)
    private val _resting = MutableStateFlow(false)
    private val _remaningTimeForRest = MutableStateFlow(3600L) //60 minutes
    val timeLeft: StateFlow<Long> = _timeLeft

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var onFinishedTimer: (() -> Unit)? = null
    fun start(durationMs: Long) {
        job?.cancel()
        remainingTime = durationMs

        val endTime = System.currentTimeMillis() + remainingTime
        prefs.edit().putLong("endTime", endTime).apply()

        _timeLeft.value = remainingTime
        _isRunning.value = true

        runTimer()
    }
    fun resetAll() {
        job?.cancel()
        _timeLeft.value = 0L
        _isRunning.value = false
        prefs.edit { remove("endTime") }
    }
    fun pause() {
        job?.cancel()
        _isRunning.value = false
        remainingTime = _timeLeft.value
        prefs.edit { remove("endTime") }
    }

    fun resume() {
        if (remainingTime <= 0L) return
        val endTime = System.currentTimeMillis() + remainingTime
        prefs.edit { putLong("endTime", endTime) }

        _isRunning.value = true
        runTimer()
    }

    fun reset() {
        job?.cancel()
        _timeLeft.value = 0L
        remainingTime = 0L
        _isRunning.value = false
        prefs.edit { remove("endTime") }
    }

    private fun runTimer() {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            while (_timeLeft.value > 0 && isActive) {
                delay(1000)
                val remaining = prefs.getLong("endTime", 0L) - System.currentTimeMillis()
                _timeLeft.value = remaining.coerceAtLeast(0)
                if (!_resting.value)
                    _remaningTimeForRest.value -= 1L
            }
            _isRunning.value = false
            _resting.value = !_resting.value
            if(_resting.value)
                if (_remaningTimeForRest.value <= 0)
                    start(25*60*1000L)
                else
                    start(5*60*1000L)
            else
                start(25*60*1000L)

        }
    }

    fun setOnTimerFinishedListener(callback: (()-> Unit)){
        onFinishedTimer = callback
    }
}
