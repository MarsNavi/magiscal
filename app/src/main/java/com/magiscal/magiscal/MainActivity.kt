package com.magiscal.magiscal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDateTime

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var expressionText: TextView
    private lateinit var resultText: TextView

    private val tokens = mutableListOf<String>()
    private val operators = setOf("+", "-", "×", "÷")
    private var currentInput = StringBuilder()

    private lateinit var sensorManager: SensorManager
    private var orientationSensor: Sensor? = null
    private var isFaceDown = false

    private var inputLocked = false
    private var trickInjected = false

    private data class SessionRecord(
        val startedAtMillis: Long,
        val plusInputs: MutableList<BigDecimal>
    )

    private var sessionRecord = SessionRecord(
        startedAtMillis = System.currentTimeMillis(),
        plusInputs = mutableListOf()
    )

    companion object {
        private const val KEY_TOKENS = "key_tokens"
        private const val KEY_CURRENT_INPUT = "key_current_input"
        private const val KEY_INPUT_LOCKED = "key_input_locked"
        private const val KEY_TRICK_INJECTED = "key_trick_injected"
        private const val KEY_IS_FACE_DOWN = "key_is_face_down"
        private const val KEY_SESSION_STARTED_AT = "key_session_started_at"
        private const val KEY_SESSION_PLUS_INPUTS = "key_session_plus_inputs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        expressionText = findViewById(R.id.expressionText)
        resultText = findViewById(R.id.resultText)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (savedInstanceState == null) {
            clearAll(startNewRecord = true)
        } else {
            restoreState(savedInstanceState)
            updateDisplay()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_TOKENS, ArrayList(tokens))
        outState.putString(KEY_CURRENT_INPUT, currentInput.toString())
        outState.putBoolean(KEY_INPUT_LOCKED, inputLocked)
        outState.putBoolean(KEY_TRICK_INJECTED, trickInjected)
        outState.putBoolean(KEY_IS_FACE_DOWN, isFaceDown)
        outState.putLong(KEY_SESSION_STARTED_AT, sessionRecord.startedAtMillis)
        outState.putStringArrayList(
            KEY_SESSION_PLUS_INPUTS,
            ArrayList(sessionRecord.plusInputs.map { it.toPlainString() })
        )
    }

    override fun onResume() {
        super.onResume()
        orientationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) return

        val z = event.values[2]
        val nowFaceDown = z < -7f
        val nowFaceUp = z > 7f

        if (nowFaceDown && !isFaceDown) {
            isFaceDown = true
            onFlipFaceDown()
        } else if (nowFaceUp && isFaceDown) {
            isFaceDown = false
            onFlipFaceUp()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op.
    }

    fun onButtonClick(view: View) {
        if (inputLocked) return

        val text = (view as Button).text.toString()

        when (text) {
            "AC" -> clearAll(startNewRecord = true)
            "C" -> {
                currentInput = StringBuilder()
                updateDisplay()
            }
            "+/-" -> toggleSign()
            "%" -> applyPercent()
            "=" -> evaluateAndShow()
            "+", "-", "×", "÷" -> onOperatorPressed(text)
            else -> onInputPressed(text)
        }
    }

    private fun onInputPressed(value: String) {
        if (value == ".") {
            if (currentInput.contains(".")) return
            if (currentInput.isEmpty()) currentInput.append("0")
        } else if (currentInput.toString() == "0") {
            currentInput = StringBuilder()
        }

        currentInput.append(value)
        updateDisplay()
    }

    private fun onOperatorPressed(op: String) {
        if (currentInput.isNotEmpty()) {
            tokens.add(currentInput.toString())
            if (op == "+") {
                sessionRecord.plusInputs.add(currentInput.toString().toBigDecimalOrZero())
            }
            currentInput = StringBuilder()
        } else if (tokens.isEmpty()) {
            return
        } else if (tokens.last() in operators) {
            tokens[tokens.lastIndex] = op
            updateDisplay()
            return
        }

        tokens.add(op)
        updateDisplay()
    }

    private fun evaluateAndShow() {
        if (currentInput.isNotEmpty()) {
            tokens.add(currentInput.toString())
            currentInput = StringBuilder()
        }

        if (tokens.isEmpty()) {
            updateDisplay()
            return
        }

        if (tokens.last() in operators) {
            tokens.removeAt(tokens.lastIndex)
        }

        val value = evaluateTokens(tokens)
        if (value == null) {
            resultText.text = "Error"
            tokens.clear()
            return
        }

        val formatted = formatNumber(value)
        expressionText.text = tokens.joinToString(separator = "")
        resultText.text = formatted

        tokens.clear()
        currentInput.append(formatted)
    }

    private fun toggleSign() {
        if (currentInput.isEmpty()) {
            currentInput.append("-")
        } else if (currentInput.startsWith("-")) {
            currentInput.deleteCharAt(0)
        } else {
            currentInput.insert(0, "-")
        }
        updateDisplay()
    }

    private fun applyPercent() {
        if (currentInput.isEmpty() || currentInput.toString() == "-") return
        val value = currentInput.toString().toBigDecimalOrZero()
            .divide(BigDecimal(100), MathContext.DECIMAL64)
        currentInput = StringBuilder(formatNumber(value))
        updateDisplay()
    }

    private fun onFlipFaceDown() {
        if (!trickInjected) {
            injectForcedAddend()
            trickInjected = true
        }
        inputLocked = true
    }

    private fun onFlipFaceUp() {
        inputLocked = false
    }

    private fun injectForcedAddend() {
        val target = buildTimeNumber().toBigDecimal()
        val sum = sessionRecord.plusInputs.fold(BigDecimal.ZERO) { acc, item -> acc + item }
        val forced = target.subtract(sum).add(BigDecimal(2))
        val forcedText = formatNumber(forced)

        if (tokens.isEmpty() && currentInput.isEmpty()) {
            currentInput.append(forcedText)
            updateDisplay()
            return
        }

        if (currentInput.isNotEmpty()) {
            tokens.add(currentInput.toString())
            currentInput = StringBuilder()
        }

        if (tokens.isNotEmpty()) {
            if (tokens.last() in operators) {
                tokens[tokens.lastIndex] = "+"
            } else {
                tokens.add("+")
            }
        }

        currentInput.append(forcedText)
        updateDisplay()
    }

    private fun buildTimeNumber(): Long {
        val now = LocalDateTime.now()
        val month = now.monthValue.toString()
        val day = now.dayOfMonth.toString().padStart(2, '0')
        val hour = now.hour.toString().padStart(2, '0')
        val minute = now.minute.toString().padStart(2, '0')
        return (month + day + hour + minute).toLong()
    }

    private fun evaluateTokens(inputTokens: List<String>): BigDecimal? {
        if (inputTokens.isEmpty()) return BigDecimal.ZERO

        val values = ArrayDeque<BigDecimal>()
        val ops = ArrayDeque<String>()

        fun applyTop(): Boolean {
            if (ops.isEmpty() || values.size < 2) return false
            val op = ops.removeLast()
            val b = values.removeLast()
            val a = values.removeLast()
            val result = when (op) {
                "+" -> a + b
                "-" -> a - b
                "×" -> a * b
                "÷" -> {
                    if (b.compareTo(BigDecimal.ZERO) == 0) return false
                    a.divide(b, MathContext.DECIMAL64)
                }
                else -> return false
            }
            values.addLast(result)
            return true
        }

        fun precedence(op: String): Int = when (op) {
            "×", "÷" -> 2
            "+", "-" -> 1
            else -> 0
        }

        for (token in inputTokens) {
            if (token in operators) {
                while (ops.isNotEmpty() && precedence(ops.last()) >= precedence(token)) {
                    if (!applyTop()) return null
                }
                ops.addLast(token)
            } else {
                values.addLast(token.toBigDecimalOrZero())
            }
        }

        while (ops.isNotEmpty()) {
            if (!applyTop()) return null
        }

        return values.lastOrNull()
    }

    private fun clearAll(startNewRecord: Boolean) {
        tokens.clear()
        currentInput = StringBuilder()
        inputLocked = false
        isFaceDown = false

        if (startNewRecord) {
            trickInjected = false
            sessionRecord = SessionRecord(
                startedAtMillis = System.currentTimeMillis(),
                plusInputs = mutableListOf()
            )
        }

        expressionText.text = ""
        resultText.text = "0"
    }

    private fun restoreState(bundle: Bundle) {
        tokens.clear()
        tokens.addAll(bundle.getStringArrayList(KEY_TOKENS) ?: emptyList())
        currentInput = StringBuilder(bundle.getString(KEY_CURRENT_INPUT, ""))
        inputLocked = bundle.getBoolean(KEY_INPUT_LOCKED, false)
        trickInjected = bundle.getBoolean(KEY_TRICK_INJECTED, false)
        isFaceDown = bundle.getBoolean(KEY_IS_FACE_DOWN, false)

        val startAt = bundle.getLong(KEY_SESSION_STARTED_AT, System.currentTimeMillis())
        val plusInputs = (bundle.getStringArrayList(KEY_SESSION_PLUS_INPUTS) ?: emptyList())
            .map { it.toBigDecimalOrZero() }
            .toMutableList()

        sessionRecord = SessionRecord(startedAtMillis = startAt, plusInputs = plusInputs)
    }

    private fun updateDisplay() {
        val expression = buildString {
            append(tokens.joinToString(separator = ""))
            append(currentInput.toString())
        }

        expressionText.text = expression
        resultText.text = when {
            currentInput.isNotEmpty() && currentInput.toString() != "-" -> currentInput.toString()
            tokens.isNotEmpty() && tokens.last() !in operators -> tokens.last()
            else -> "0"
        }
    }

    private fun formatNumber(value: BigDecimal): String {
        return value.stripTrailingZeros().toPlainString()
    }

    private fun String.toBigDecimalOrZero(): BigDecimal {
        return try {
            this.toBigDecimal()
        } catch (_: NumberFormatException) {
            BigDecimal.ZERO
        }
    }
}
