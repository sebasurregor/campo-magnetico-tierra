package com.fisica2.campomagnetico

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Una lectura del magnetómetro con todo lo necesario para pasarla al marco terrestre. */
class MagSample(
    /** Campo calibrado en el marco del teléfono (μT). */
    val b: Vec3,
    /** SensorManager.SENSOR_STATUS_* de esta lectura. */
    val accuracy: Int,
    /** Matriz de getRotationMatrix (teléfono → Este, Norte magnético, Arriba), o null sin gravedad. */
    val rotation: DoubleArray?,
    /** Inclinación según SensorManager.getInclination, convertida a positiva hacia abajo. */
    val androidInclinationDeg: Double?,
    /** Último valor sin calibrar y bias estimado por Android (TYPE_MAGNETIC_FIELD_UNCALIBRATED). */
    val raw: Vec3?,
    val bias: Vec3?,
)

/**
 * Lee el magnetómetro (calibrado y sin calibrar) y la gravedad. Se registra en onResume y se
 * quita en onPause (ver MainActivity), así que nunca lee sensores en segundo plano.
 */
class SensorReader(context: Context, private val onSample: (MagSample) -> Unit) : SensorEventListener {
    private val manager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val magnetic: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val uncalibrated: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
    private val gravitySensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelerometer: Sensor? =
        if (gravitySensor == null) manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null

    val hasMagnetometer: Boolean get() = magnetic != null
    val hasUncalibrated: Boolean get() = uncalibrated != null
    val gravityLabel: String
        get() = when {
            gravitySensor != null -> "sensor de gravedad (TYPE_GRAVITY)"
            accelerometer != null -> "acelerómetro filtrado (TYPE_ACCELEROMETER)"
            else -> "ninguno"
        }

    var running = false
        private set

    private val gravity = FloatArray(3)
    private var hasGravity = false
    private var raw: Vec3? = null
    private var bias: Vec3? = null
    private val rotation = FloatArray(9)
    private val inclination = FloatArray(9)

    fun start() {
        val m = manager ?: return
        if (running) return
        // ~50 Hz para promediar bien; la interfaz se refresca a ~10 Hz (ver MainViewModel).
        listOfNotNull(magnetic, uncalibrated, gravitySensor, accelerometer).forEach {
            m.registerListener(this, it, SAMPLING_US)
        }
        running = true
    }

    fun stop() {
        manager?.unregisterListener(this)
        running = false
        hasGravity = false
        raw = null
        bias = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravity[0] = v[0]; gravity[1] = v[1]; gravity[2] = v[2]
                hasGravity = true
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Filtro pasa-bajos para quedarse con la gravedad y quitar sacudidas.
                if (!hasGravity) {
                    gravity[0] = v[0]; gravity[1] = v[1]; gravity[2] = v[2]
                } else {
                    for (i in 0..2) gravity[i] = ALPHA * gravity[i] + (1 - ALPHA) * v[i]
                }
                hasGravity = true
            }
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> {
                raw = Vec3(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                bias = Vec3(v[3].toDouble(), v[4].toDouble(), v[5].toDouble())
            }
            Sensor.TYPE_MAGNETIC_FIELD -> deliver(event)
        }
    }

    private fun deliver(event: SensorEvent) {
        val v = event.values
        val b = Vec3(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
        var r: DoubleArray? = null
        var incl: Double? = null
        if (hasGravity && SensorManager.getRotationMatrix(rotation, inclination, gravity, v)) {
            r = DoubleArray(9) { rotation[it].toDouble() }
            // getInclination da el ángulo del campo respecto al horizonte con signo positivo hacia
            // ARRIBA; la inclinación geofísica I es positiva hacia abajo, por eso se cambia el signo.
            incl = -Math.toDegrees(SensorManager.getInclination(inclination).toDouble())
        }
        onSample(MagSample(b, event.accuracy, r, incl, raw, bias))
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val SAMPLING_US = 20_000
        const val ALPHA = 0.8f
    }
}
