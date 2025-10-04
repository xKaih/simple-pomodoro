package io.github.xkaih.simplepomodoro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Signal the service that the alarm has fired so it can finish the session
        val serviceIntent = Intent(context, PomodoroService::class.java).apply {
            action = "ALARM_FIRED"
        }
        // Service should already be running in foreground; deliver command without attempting a new FGS start
        context.startService(serviceIntent)
    }
}
