package com.example.domarklocka

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

class MainActivity : Activity() {

    private lateinit var timerText: TextView
    private lateinit var statusText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy {
        getSharedPreferences("domarklocka", Context.MODE_PRIVATE)
    }

    private val presetTimes = mutableListOf<Long>()

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
        loadPresets()
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

    private fun loadPresets() {
        if (!prefs.contains("presets")) {
            presetTimes.addAll(
                listOf(
                    30 * 60 * 1000L,
                    25 * 60 * 1000L,
                    2 * 60 * 1000L
                )
            )
            savePresets()
            return
        }

        val json = JSONArray(prefs.getString("presets", "[]"))
        for (i in 0 until json.length()) {
            val time = json.optLong(i, 0L)
            if (time > 0L) presetTimes.add(time)
        }
    }

    private fun savePresets() {
        val json = JSONArray()
        presetTimes.forEach { json.put(it) }
        prefs.edit().putString("presets", json.toString()).apply()
    }

    private fun formatDuration(milliseconds: Long): String {
        val total = milliseconds / 1000L
        val h = total / 3600L
        val m = (total / 60L) % 60L
        val s = total % 60L

        return if (h > 0L) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", total / 60L, s)
        }
    }

    // Accepts seconds, MM:SS or HH:MM:SS.
    private fun parseDuration(value: String): Long? {
        val parts = value.trim().split(":")
        if (parts.isEmpty() || parts.size > 3) return null
        if (parts.any { it.isBlank() || it.toLongOrNull() == null }) {
            return null
        }

        val numbers = parts.map { it.toLong() }
        if (numbers.any { it < 0L }) return null

        val totalSeconds = when (numbers.size) {
            1 -> numbers[0]
            2 -> {
                if (numbers[1] > 59L) return null
                numbers[0] * 60L + numbers[1]
            }
            else -> {
                if (numbers[1] > 59L || numbers[2] > 59L) return null
                if (numbers[0] > (Long.MAX_VALUE / 1000L - 3600L) / 3600L) {
                    return null
                }
                numbers[0] * 3600L + numbers[1] * 60L + numbers[2]
            }
        }

        if (totalSeconds < 1L ||
            totalSeconds > Long.MAX_VALUE / 1000L
        ) return null

        return totalSeconds * 1000L
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

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.BLACK)
        }

scroll.addView(
    layout,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)
        layout.addView(
            makeText("HALVLEKENS LÄNGD", 16f, true),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )

        val total = halfLength / 1000L
        val hours = makeInput("h", (total / 3600L).toString())
        val minutes = makeInput("min", ((total / 60L) % 60L).toString())
        val seconds = makeInput("sek", (total % 60L).toString())

        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

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
                    dp(44)
                )
            )

            fields.addView(
                column,
                LinearLayout.LayoutParams(0, dp(70), 1f)
            )
        }

        addField(hours, "TIM")
        addField(minutes, "MIN")
        addField(seconds, "SEK")

        layout.addView(
            fields,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(72)
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

        layout.addView(
            makeText("SNABBVAL", 14f, true),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(32)
            )
        )

        presetTimes.forEachIndexed { index, time ->
            val preset = makeText(
                formatDuration(time) + "     ✎",
                17f,
                true
            )

            preset.setOnClickListener {
                val secondsTotal = time / 1000L
                hours.setText((secondsTotal / 3600L).toString())
                minutes.setText(((secondsTotal / 60L) % 60L).toString())
                seconds.setText((secondsTotal % 60L).toString())
                message.text = "Vald tid: ${formatDuration(time)}"
            }

            preset.setOnLongClickListener {
                showPresetMenu(index)
                true
            }

            layout.addView(
                preset,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(42)
                )
            )
        }

        val addPreset = makeText("+ LÄGG TILL SNABBVAL", 14f, true)
        addPreset.setOnClickListener {
            showDurationDialog("Nytt snabbval", "05:00") { time ->
                if (presetTimes.contains(time)) {
                    Toast.makeText(this, "Tiden finns redan", Toast.LENGTH_SHORT).show()
                } else {
                    presetTimes.add(time)
                    savePresets()
                    showSettings()
                }
            }
        }

        layout.addView(
            addPreset,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )

        val confirm = makeText("✓", 30f, true)
        confirm.setOnClickListener {
            val h = hours.text.toString().toLongOrNull() ?: 0L
            val m = minutes.text.toString().toLongOrNull() ?: 0L
            val s = seconds.text.toString().toLongOrNull() ?: 0L

            if (h < 0L || h > 999999L ||
                m !in 0L..59L || s !in 0L..59L
            ) {
                message.text = "Kontrollera tiden"
                return@setOnClickListener
            }

            if (h > (Long.MAX_VALUE / 1000L - m * 60L - s) / 3600L) {
                message.text = "Tiden är för lång"
                return@setOnClickListener
            }

            val totalSeconds = h * 3600L + m * 60L + s
            if (totalSeconds < 1L ||
                totalSeconds > Long.MAX_VALUE / 1000L
            ) {
                message.text = "Ange minst 1 sekund"
                return@setOnClickListener
            }

            halfLength = totalSeconds * 1000L

            val imm = getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            showTimer()
        }

        layout.addView(
            confirm,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        setContentView(scroll)
    }

    private fun showPresetMenu(index: Int) {
        if (index !in presetTimes.indices) return

        val options = arrayOf("Ändra tid", "Radera snabbval", "Avbryt")

        AlertDialog.Builder(this)
            .setTitle(formatDuration(presetTimes[index]))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val current = formatDuration(presetTimes[index])
                        showDurationDialog("Ändra snabbval", current) { time ->
                            if (presetTimes.indices.contains(index)) {
                                presetTimes[index] = time
                                savePresets()
                                showSettings()
                            }
                        }
                    }
                    1 -> {
                        presetTimes.removeAt(index)
                        savePresets()
                        showSettings()
                    }
                }
            }
            .show()
    }

    private fun showDurationDialog(
        title: String,
        initial: String,
        onSave: (Long) -> Unit
    ) {
        val input = EditText(this).apply {
            setText(initial)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            selectAll()
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Skriv MM:SS eller HH:MM:SS")
            .setView(input)
            .setPositiveButton("Spara") { _, _ ->
                val time = parseDuration(input.text.toString())
                if (time == null) {
                    Toast.makeText(
                        this,
                        "Ogiltig tid. Exempel: 05:00",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    onSave(time)
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showTimer(startImmediately: Boolean = false) {
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

        if (startImmediately) {
            startTimer()
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
                showFinishedOptions()
            }

            running -> {
                elapsedBeforePause +=
                    SystemClock.elapsedRealtime() - startTime

                running = false
                handler.removeCallbacks(update)
                statusText.text = "PAUSAD"
                vibratePause()
            }

            else -> startTimer()
        }
    }

    private fun startTimer() {
        startTime = SystemClock.elapsedRealtime()
        running = true
        finished = false
        statusText.text = "TRYCK FÖR ATT PAUSA"
        vibrateStart()
        handler.removeCallbacks(update)
        handler.post(update)
    }

    private fun showFinishedOptions() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.BLACK)
        }

        layout.addView(
            makeText("HALVLEKEN SLUT", 17f, true),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val restart = makeText("↻ SAMMA TID", 16f, true)
        restart.setOnClickListener {
            showTimer()
        }

        layout.addView(
            restart,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        )

        val change = makeText("ÄNDRA TID", 15f, true)
        change.setOnClickListener {
            showSettings()
        }

        layout.addView(
            change,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        setContentView(layout)
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
        val seconds = totalSeconds % 60L

        timerText.text = if (halfLength >= 3600000L) {
            String.format(
                "%02d:%02d:%02d",
                hours, (totalSeconds / 60L) % 60L, seconds
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

    private fun vibrateStart() {
        vibrator = getVibrator()
        vibrator?.vibrate(
            VibrationEffect.createOneShot(
                60L,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    private fun vibratePause() {
        vibrator = getVibrator()
        vibrator?.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0L, 70L, 90L, 70L),
                -1
            )
        )
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
