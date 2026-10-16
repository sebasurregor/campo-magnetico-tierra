package com.fisica2.campomagnetico.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.fisica2.campomagnetico.Capture
import com.fisica2.campomagnetico.CaptureAccumulator
import com.fisica2.campomagnetico.CaptureStore
import com.fisica2.campomagnetico.EarthField
import com.fisica2.campomagnetico.LocationReader
import com.fisica2.campomagnetico.MagMath
import com.fisica2.campomagnetico.MagSample
import com.fisica2.campomagnetico.Orientations
import com.fisica2.campomagnetico.ReferenceValues
import com.fisica2.campomagnetico.SensorReader
import com.fisica2.campomagnetico.Settings
import com.fisica2.campomagnetico.TheoryModel
import com.fisica2.campomagnetico.TheorySource
import com.fisica2.campomagnetico.Vec3

/** Lo que muestra la pestaña "En vivo" (se refresca ~10 veces por segundo). */
data class LiveReading(
    val b: Vec3,
    val accuracy: Int,
    val earth: EarthField?,
    val parallel: Double?,
    val androidInclinationDeg: Double?,
    val raw: Vec3?,
    val bias: Vec3?,
    val declinationUsedDeg: Double,
)

enum class CapturePurpose { NORMAL, FLIP_0, FLIP_180 }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val store = CaptureStore(app)
    private val sensors = SensorReader(app, ::onSample)
    private val locationReader = LocationReader(app)
    private val handler = Handler(Looper.getMainLooper())

    val hasMagnetometer = sensors.hasMagnetometer
    val hasUncalibrated = sensors.hasUncalibrated
    val gravityLabel = sensors.gravityLabel

    var live by mutableStateOf<LiveReading?>(null)
        private set
    private var lastUiUpdate = 0L

    // --- Ubicación ---
    var lat by mutableStateOf(settings.lat)
        private set
    var lon by mutableStateOf(settings.lon)
        private set
    var altM by mutableStateOf(settings.altM)
        private set
    var locationSource by mutableStateOf(settings.locationSource)
        private set
    var locationBusy by mutableStateOf(false)
        private set

    // --- Valores teóricos ---
    var theorySource by mutableStateOf(settings.theorySource)
        private set
    var plan by mutableStateOf(settings.plan)
        private set
    var model by mutableStateOf(computeModel())
        private set

    var roundCsv by mutableStateOf(settings.roundCsvCoordinates)
        private set

    // --- Capturas ---
    val captures = mutableStateListOf<Capture>().apply { addAll(store.load()) }
    var capturing by mutableStateOf(false)
        private set
    var captureCount by mutableIntStateOf(0)
        private set
    private var accumulator: CaptureAccumulator? = null
    private var captureOrientation = ""
    private var captureNote = ""
    private var capturePurpose = CapturePurpose.NORMAL
    private val finishRunnable = Runnable { finishCapture() }

    // --- Asistente de 180° ---
    var flip0 by mutableStateOf<Capture?>(null)
        private set
    var flip180 by mutableStateOf<Capture?>(null)
        private set

    var message by mutableStateOf<String?>(null)

    /** Valores con los que se compara y con cuya declinación se corrige el norte. */
    val theory: ReferenceValues get() = if (theorySource == TheorySource.MODEL) model else plan

    // ---------------------------------------------------------------- Sensores

    fun startSensors() {
        model = computeModel() // la fecha cambia: se recalcula al volver a la app
        sensors.start()
    }

    fun stopSensors() {
        sensors.stop()
        if (capturing) {
            cancelCapture()
            message = "Captura cancelada: la app pasó a segundo plano."
        }
    }

    private fun onSample(s: MagSample) {
        val t = theory
        val earth = s.rotation?.let { MagMath.deviceToEarth(it, s.b, t.declinationDeg) }
        accumulator?.let {
            it.add(s.b, earth, s.raw, s.bias, s.accuracy)
            captureCount = it.count
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastUiUpdate >= UI_PERIOD_MS) {
            lastUiUpdate = now
            live = LiveReading(
                b = s.b,
                accuracy = s.accuracy,
                earth = earth,
                parallel = earth?.parallelToAxis(lat),
                androidInclinationDeg = s.androidInclinationDeg,
                raw = s.raw,
                bias = s.bias,
                declinationUsedDeg = t.declinationDeg,
            )
        }
    }

    // ---------------------------------------------------------------- Capturas

    fun startCapture(orientation: String, note: String, purpose: CapturePurpose = CapturePurpose.NORMAL) {
        if (capturing) return
        if (!hasMagnetometer) {
            message = "Este teléfono no tiene magnetómetro."
            return
        }
        if (!sensors.running) {
            message = "Los sensores no están activos."
            return
        }
        accumulator = CaptureAccumulator(lat)
        captureOrientation = orientation
        captureNote = note.trim().take(80)
        capturePurpose = purpose
        captureCount = 0
        capturing = true
        handler.postDelayed(finishRunnable, CAPTURE_MS)
    }

    private fun cancelCapture() {
        handler.removeCallbacks(finishRunnable)
        accumulator = null
        capturing = false
    }

    private fun finishCapture() {
        val acc = accumulator ?: return
        accumulator = null
        capturing = false
        val now = System.currentTimeMillis()
        val capture = acc.build(
            id = now,
            timeMillis = now,
            orientation = captureOrientation,
            note = captureNote,
            lat = lat,
            lon = lon,
            altM = altM,
            theorySource = theorySource.label,
            theory = theory,
        )
        if (capture == null) {
            message = "No llegaron suficientes muestras del sensor. Intenta de nuevo."
            return
        }
        captures.add(0, capture)
        store.save(captures)
        when (capturePurpose) {
            CapturePurpose.FLIP_0 -> {
                flip0 = capture
                flip180 = null
            }
            CapturePurpose.FLIP_180 -> flip180 = capture
            CapturePurpose.NORMAL -> Unit
        }
    }

    fun startFlip0() = startCapture(Orientations.FLIP_0, "", CapturePurpose.FLIP_0)
    fun startFlip180() = startCapture(Orientations.FLIP_180, "", CapturePurpose.FLIP_180)

    fun resetFlip() {
        flip0 = null
        flip180 = null
    }

    fun deleteCapture(c: Capture) {
        captures.remove(c)
        if (flip0?.id == c.id) flip0 = null
        if (flip180?.id == c.id) flip180 = null
        store.save(captures)
    }

    fun deleteAllCaptures() {
        captures.clear()
        resetFlip()
        store.save(captures)
    }

    // ---------------------------------------------------------------- Ubicación

    fun hasLocationPermission() = locationReader.hasPermission()

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            fetchLocation()
        } else {
            message = "Sin permiso de ubicación: se usan las coordenadas guardadas (por defecto, Medellín). " +
                "Puedes escribirlas a mano."
        }
    }

    fun fetchLocation() {
        if (locationBusy) return
        locationBusy = true
        locationReader.requestOnce { loc ->
            locationBusy = false
            if (loc == null) {
                message = "No se pudo obtener la ubicación (¿ubicación desactivada o sin señal?). " +
                    "Se conservan las coordenadas actuales; puedes escribirlas a mano."
            } else {
                // La ubicación aproximada casi nunca trae altitud: si no la trae, se deja la del campo.
                val alt = if (loc.hasAltitude()) loc.altitude else altM
                applyLocation(loc.latitude, loc.longitude, alt, "Teléfono (aproximada)")
            }
        }
    }

    fun setManualLocation(newLat: Double, newLon: Double, newAlt: Double) =
        applyLocation(newLat, newLon, newAlt, "Manual")

    fun resetLocation() = applyLocation(
        MagMath.DEFAULT_LAT, MagMath.DEFAULT_LON, MagMath.DEFAULT_ALT_M, Settings.DEFAULT_LOCATION_SOURCE,
    )

    private fun applyLocation(newLat: Double, newLon: Double, newAlt: Double, source: String) {
        lat = newLat
        lon = newLon
        altM = newAlt
        locationSource = source
        settings.lat = newLat
        settings.lon = newLon
        settings.altM = newAlt
        settings.locationSource = source
        model = computeModel()
    }

    private fun computeModel(): ReferenceValues =
        TheoryModel.fromAndroid(lat, lon, altM, System.currentTimeMillis())

    // ---------------------------------------------------------------- Configuración

    fun updateTheorySource(source: TheorySource) {
        theorySource = source
        settings.theorySource = source
    }

    fun updatePlan(values: ReferenceValues) {
        plan = values
        settings.plan = values
    }

    fun updateRoundCsv(value: Boolean) {
        roundCsv = value
        settings.roundCsvCoordinates = value
    }

    override fun onCleared() {
        handler.removeCallbacksAndMessages(null)
        sensors.stop()
    }

    private companion object {
        const val CAPTURE_MS = 3_000L
        const val UI_PERIOD_MS = 100L
    }
}
