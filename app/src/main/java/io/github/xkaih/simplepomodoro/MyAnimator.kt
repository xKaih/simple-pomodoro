package io.github.xkaih.simplepomodoro

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat

//I just spent like 5 hours just for this...
class MyAnimator(
    private val imageView: ImageView,
    private val baseFrameName: String,
    private val frameCount: Int,
    private val frameDurationMs: Long = 41L,
    private val resources: Resources = imageView.resources,
    private val packageName: String = imageView.context.packageName,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {

    private val frames: List<Drawable> = (1..frameCount).map { i ->
        val id = resources.getIdentifier("$baseFrameName$i", "drawable", packageName)
        require(id != 0) { "Drawable not found: ${baseFrameName}$i" }
        requireNotNull(ResourcesCompat.getDrawable(resources, id, null)) {
            "Drawable null: ${baseFrameName}$i"
        }
    }

    private val lastIndex = frames.lastIndex
    private var index = 0
    private var dir = 1
    private var running = false

    private val task = object : java.lang.Runnable {
        override fun run() {
            if (!running) return

            imageView.setImageDrawable(frames[index])

            index += dir

            if (index in 0..lastIndex) {
                handler.postDelayed(this, frameDurationMs)
            } else {
                index = if (dir == 1) lastIndex else 0
                dir = -dir
                stop()
            }
        }
    }

    fun startForward(fromStart: Boolean = false) {
        handler.removeCallbacks(task)
        dir = 1
        if (fromStart) index = 0
        running = true
        handler.post(task)
    }

    fun startBackward(fromEnd: Boolean = false) {
        handler.removeCallbacks(task)
        dir = -1
        if (fromEnd) index = lastIndex
        running = true
        handler.post(task)
    }

    fun toggle() {
        if (!running) {
            if (dir == 1)
                startForward(true)
            else
                startBackward(true)

            return
        }

        index = (index - dir).coerceIn(0, lastIndex) //Get back to the last frame drawn
        dir = -dir
        index = (index + dir).coerceIn(0, lastIndex) //Move to the new direction
        handler.removeCallbacks(task)
        handler.post(task)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(task)
    }

    fun isRunning(): Boolean = running
    fun currentIndex(): Int = index
    fun direction(): Int = dir
}
