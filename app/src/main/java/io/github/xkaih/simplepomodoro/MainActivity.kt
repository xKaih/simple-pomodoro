package io.github.xkaih.simplepomodoro

import android.R
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
import io.github.xkaih.simplepomodoro.databinding.ActivityMainBinding
import kotlinx.coroutines.Runnable

class MainActivity : ComponentActivity() {

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
        val playIconAnimator = MyAnimator(
            binding.imageView2,
            "playtopause",
            68,
            41L
        )
        binding.imageButton.setOnClickListener { playIconAnimator.toggle() }
    }

}
