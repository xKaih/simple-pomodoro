package io.github.xkaih.simplepomodoro

import android.Manifest
import android.R
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

        requestNotificationPermission()
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
                if (timerManager.timeLeft.value.first > 0 || timerManager.timeLeft.value.second > 0) {
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

        timerManager = TimerManager(
            this,
            prefs
        )


        lifecycleScope.launch {
            timerManager.timeLeft.collect { time ->
                binding.timeLeftText.text = "%02d:%02d".format(time.first, time.second)
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

    fun requestNotificationPermission() {
        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    requestNotificationPermission()
                }
            }

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {}
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

}
