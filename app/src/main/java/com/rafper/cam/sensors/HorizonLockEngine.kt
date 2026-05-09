package com.rafper.cam.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.rafper.cam.camera.HorizonLockMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Tuning principal de qualidade:
 * - horizonAlpha: suavização de roll para evitar saltos.
 * - deadZoneDegrees: ignora micro variações de mão.
 * - assistMultiplier/fullMultiplier: força por modo.
 * - maxCorrectionDegrees: limite de correção.
 */
class HorizonLockEngine(context: Context) : SensorEventListener {
    data class Output(
        val rollDegrees: Float = 0f,
        val horizonStrength: Float = 0f,
        val microJitter: Float = 0f
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _output = MutableStateFlow(Output())
    val output: StateFlow<Output> = _output

    private var mode: HorizonLockMode = HorizonLockMode.OFF
    private var gyroFiltered = 0f

    private val horizonAlpha = 0.12f
    private val gyroAlpha = 0.16f
    private val deadZoneDegrees = 0.35f
    private val assistMultiplier = 0.45f
    private val fullMultiplier = 1f
    private val maxCorrectionDegrees = 15f

    fun start(mode: HorizonLockMode) {
        this.mode = mode
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun setMode(mode: HorizonLockMode) {
        this.mode = mode
        if (mode == HorizonLockMode.OFF) _output.value = Output()
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val rollRate = event.values[2]
                gyroFiltered += (rollRate - gyroFiltered) * gyroAlpha
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (mode == HorizonLockMode.OFF) return
                val gravRoll = Math.toDegrees(atan2(event.values[0], event.values[1]).toDouble()).toFloat()
                val gyroCompensation = gyroFiltered * 0.9f
                val sensorFusedRoll = gravRoll + gyroCompensation
                val strength = when (mode) {
                    HorizonLockMode.OFF -> 0f
                    HorizonLockMode.ASSIST -> assistMultiplier
                    HorizonLockMode.FULL_LOCK -> fullMultiplier
                }
                val target = (sensorFusedRoll * strength).coerceIn(-maxCorrectionDegrees, maxCorrectionDegrees)
                val current = _output.value.rollDegrees
                val damped = current + (target - current) * horizonAlpha
                val corrected = if (abs(damped) < deadZoneDegrees) 0f else damped
                val jitter = sqrt((event.values[0] * event.values[0] + event.values[1] * event.values[1]).toDouble()).toFloat()
                _output.value = Output(
                    rollDegrees = corrected,
                    horizonStrength = strength,
                    microJitter = jitter
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
