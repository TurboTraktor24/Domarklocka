
package com.example.domarklocka

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var timerText: TextView
    private lateinit var statusText: TextView

    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var finished = false
    private var elapsedBeforePause = 0L
    private var startTime = 0L
    private var halfLength = 30 * 60 * 1000L

    private var vibrator: Vibrator? = null

    private var longPressTriggered = false
    private var resetConfirmationArmed = false
    private var confirmingTap = false

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSettings()
    }

    private fun makeText(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun makeInput(
        hintText: String,
        initial: String
    ): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(initial)
            textSize = 18f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            setSelectAllOnFocus(true)
            setPadding(dp(2), 0, dp(2), 0)
        }
    }

    private fun showSettings() {
        running = false
        finished = false
        longPressTriggered = false
        resetConfirmationArmed = false
        confirmingTap = false

        handler.removeCallbacks(update)
        handler.removeCallbacks(resetHold)
        stopVibration()

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.BLACK)
        }

        layout.addView(
            makeText("HALVLEKENS LÄNGD", 16f, true),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(45)
            )
        )

        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val hours = makeInput("h", "0")
        val minutes = makeInput("min", "30")
        val seconds = makeInput("sek", "0")

        fun addField(field: EditText, label: String) {
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }

            column.addView(
                makeText(label, 12f),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(24)
                )
            )

            column.addView(
                field,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
                )
            )

            fields.addView(
                column,
                LinearLayout.LayoutParams(0, dp(75), 1f)
            )
        }

        addField(hours, "TIM")
        addField(minutes, "MIN")
        addField(seconds, "SEK")

        layout.addView(
            fields,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(80)
            )
        )

        val message = makeText("", 12f)

        layout.addView(
            message,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28)
            )
        )

        val confirm = makeText("✓", 30f, true)

        confirm.setOnClickListener {
            val h = hours.text.toString().toLongOrNull() ?: 0L
            val m = minutes.text.toString().toLongOrNull() ?: 0L
            val s = seconds.text.toString().toLongOrNull() ?: 0L

            if (h < 0 || h > 999999L ||
                m !in 0L..59L || s !in 0L..59L
            ) {
                message.text = "Kontrollera tiden"
                return@setOnClickListener
            }

            val totalSeconds = h * 3600L + m * 60L + s

            if (totalSeconds < 1L) {
                message.text = "Ange minst 1 sekund"
                return@setOnClickListener
            }

            halfLength = totalSeconds * 1000L

            val imm = getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

            imm.hideSoftInputFromWindow(
                window.decorView.windowToken, 0
            )

            showTimer()
        }

        layout.addView(
            confirm,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        )

        setContentView(layout)
    }

    private fun showTimer() {
        elapsedBeforePause = 0L
        running = false
        finished = false
        resetConfirmationArmed = false
        longPressTriggered = false
        confirmingTap = false

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        timerText = makeText("00:00", 42f, true)
        statusText = makeText("TRYCK FÖR ATT STARTA", 12f)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)

            addView(
                timerText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(65)
                )
            )

            addView(
                statusText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(35)
                )
            )
        }

        setContentView(layout)
        updateDisplay(0L)

        layout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    confirmingTap = resetConfirmationArmed

                    if (!running && !finished &&
                        !resetConfirmationArmed
                    ) {
                        handler.postDelayed(resetHold, 3000L)
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(resetHold)

                    if (longPressTriggered) {
                        longPressTriggered = false
                        true
                    } else if (confirmingTap) {
                        confirmingTap = false
                        resetConfirmationArmed = false
                        showSettings()
                        true
                    } else {
                        handleShortTap()
                        true
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(resetHold)
                    longPressTriggered = false
                    confirmingTap = false
                    true
                }

                else -> true
            }
        }
    }

    private val resetHold = Runnable {
        if (!running && !finished &&
            !resetConfirmationArmed
        ) {
            longPressTriggered = true
            resetConfirmationArmed = true
            statusText.text = "TRYCK FÖR ATT ÄNDRA TID"
        }
    }

    private fun handleShortTap() {
        when {
            finished -> {
                stopVibration()
                showSettings()
            }

            running -> {
                elapsedBeforePause +=
                    SystemClock.elapsedRealtime() - startTime

                running = false
                handler.removeCallbacks(update)
                statusText.text =
                    "PAUSAD - HÅLL 3 SEK FÖR ÅTERSTÄLLNING"
            }

            else -> {
                startTime = SystemClock.elapsedRealtime()
                running = true
                statusText.text = "TRYCK FÖR ATT PAUSA"
                handler.post(update)
            }
        }
    }

    private val update = object : Runnable {
        override fun run() {
            if (!running) return

            val elapsed = elapsedBeforePause +
                (SystemClock.elapsedRealtime() - startTime)

            if (elapsed >= halfLength) {
                elapsedBeforePause = halfLength
                running = false
                finished = true
                handler.removeCallbacks(this)

                updateDisplay(halfLength)
                statusText.text = "HALVLEKEN SLUT"
                startRepeatingVibration()
            } else {
                updateDisplay(elapsed)
                handler.postDelayed(this, 100L)
            }
        }
    }

    private fun updateDisplay(elapsed: Long) {
        val totalSeconds = elapsed / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds / 60L) % 60L
        val seconds = totalSeconds % 60L

        timerText.text = if (halfLength >= 3600000L) {
            String.format(
                "%02d:%02d:%02d", hours, minutes, seconds
            )
        } else {
            String.format(
                "%02d:%02d", totalSeconds / 60L, seconds
            )
        }
    }

    private fun getVibrator(): Vibrator {
        return if (android.os.Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager

            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun startRepeatingVibration() {
        vibrator = getVibrator()

        if (vibrator?.hasVibrator() == true) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 700L, 300L),
                    0
                )
            )
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(update)
        handler.removeCallbacks(resetHold)
        stopVibration()
        super.onDestroy()
    }
}
