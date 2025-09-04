package io.github.xkaih.simplepomodoro

import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.text.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import java.util.Timer

class SettingsActivity : AppCompatActivity() {

    private lateinit var pomodoroSettingsMap: Map<EditText, String>
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = getSharedPreferences("pomodoro_prefs", MODE_PRIVATE)

        pomodoroSettingsMap = mapOf(
            findViewById<EditText>(R.id.editWorkTime) to TimerManager.PREF_WORK_TIME,
            findViewById<EditText>(R.id.editShortRest) to TimerManager.PREF_SHORT_REST,
            findViewById<EditText>(R.id.editLongRest) to TimerManager.PREF_LONG_REST,
            findViewById<EditText>(R.id.editLongRestThreshold) to TimerManager.PREF_LONG_REST_THRESHOLD

        )

        updateSettingsFromPreferences()

        findViewById<ImageView>(R.id.backIcon).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.clearPreferences).setOnClickListener {
            pomodoroSettingsMap.forEach { (key, value) ->
                prefs.edit { remove(value) }
                updateSettingsFromPreferences()
            }
        }


        pomodoroSettingsMap.forEach { (key, value) ->
            var isUpdating = false
            key.doAfterTextChanged { text ->
                if (!isUpdating) {
                    val num = text?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
                    if (num > 120) {
                        isUpdating = true
                        key.setText("120")
                        key.setSelection(key.text.length)
                        isUpdating = false
                    }
                    if(num <= 0) {
                        isUpdating = true
                        key.setText("1")
                        key.setSelection(key.text.length)
                        isUpdating = false
                    }

                    prefs.edit { putLong(value, num.toLong() * 60 * 1000) }
                }
            }
        }
    }

    fun updateSettingsFromPreferences() {
        pomodoroSettingsMap.forEach { (key, value) ->
            val setting = prefs.getLong(value,-1)
            if(setting == -1L)
            {
                prefs.edit {putLong(value, TimerManager.DEFAULT_PREFERENCES_MAP[value]!!)}
            }
            key.setText((prefs.getLong(value,0) / 1000 / 60).toString())
        }
    }
}