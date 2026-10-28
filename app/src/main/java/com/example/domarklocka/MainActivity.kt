
package com.example.domarklocka

import android.app.Activity
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context
import android.os.SystemClock
import android.os.Handler
import android.os.Looper

class MainActivity : Activity() {

    private lateinit var timerText: TextView
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var finished = false
    private var elapsedBeforePause = 0L
    private var startTime = 0L
    private var halfLength = 30 * 60 * 1000L

    private val update = object : Runnable {
        override fun run() {
            if (running) {
                val elapsed = elapsedBeforePause +
                    (SystemClock.elapsedRealtime() - startTime)

                if (elapsed >= halfLength) {
                    elapsedBeforePause = halfLength
                    running = false
                    finished = true
                    vibrate()
                    statusText.text = "HALVLEKEN SLUT"
                    updateDisplay(halfLength)
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                } else {
                    updateDisplay(elapsed)
                    handler.postDelayed(this, 100)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        timerText = TextView(this).apply {
            text = "00:00"
            textSize = 42f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "TRYCK FÖR ATT STARTA"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(timerText)
            addView(statusText)
        }

        setContentView(layout)

        layout.setOnClickListener {
            when {
                finished -> {
                    elapsedBeforePause = 0L
                    finished = false
                    statusText.text = "TRYCK FÖR ATT STARTA"
                    updateDisplay(0L)
                }
                running -> {
                    elapsedBeforePause +=
                        SystemClock.elapsedRealtime() - startTime
                    running = false
                    handler.removeCallbacks(update)
                    statusText.text = "PAUSAD"
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
                else -> {
                    startTime = SystemClock.elapsedRealtime()
                    running = true
                    statusText.text = "TRYCK FÖR ATT PAUSA"
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                    handler.post(update)
                }
            }
        }
    }

    private fun updateDisplay(elapsed: Long) {
        val seconds = elapsed / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        timerText.text = String.format(
            "%02d:%02d", minutes, remainingSeconds
        )
    }

    private fun vibrate() {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    1000,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(update)
        super.onDestroy()
    }
}
