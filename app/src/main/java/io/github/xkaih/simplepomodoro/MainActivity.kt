package io.github.xkaih.simplepomodoro

import android.R
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.*
import io.github.xkaih.simplepomodoro.databinding.ActivityMainBinding
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var timerManager: TimerManager
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        /*binding.button.setOnClickListener { timerManager.reset() }*/

        val playIconAnimator = MyAnimator(
            binding.playIcon,
            "playtopause",
            68,
            41L
        )

        binding.settingsIcon.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }


        binding.playPomodoroButton.setOnClickListener {
            if (timerManager.isRunning.value) {
                timerManager.pause()
            } else {
                if (timerManager.timeLeft.value > 0) {
                    timerManager.resume()
                } else {
                    timerManager.start()
                }
            }
        }

        binding.timeLeftText.setOnClickListener {
            timerManager.reset()
        }

        val prefs = getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
        timerManager = TimerManager(prefs)


        lifecycleScope.launch {
            timerManager.timeLeft.collect { time ->
                val seconds = time / 1000
                val minutes = seconds / 60
                val secs = seconds % 60
                binding.timeLeftText.text = "%02d:%02d".format(minutes, secs)
            }
        }

        lifecycleScope.launch {
            timerManager.isRunning.collect { value ->
                if (value)
                    playIconAnimator.startForward()
                else
                    playIconAnimator.startBackward()
            }
        }

    }

}
