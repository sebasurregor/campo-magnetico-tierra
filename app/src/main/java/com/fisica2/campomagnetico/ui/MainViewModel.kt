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
import com.fisica2.campomagnetico.Attitude
import com.fisica2.campomagnetico.AttitudeMath
import com.fisica2.campomagnetico.AttitudeSmoother
import com.fisica2.campomagnetico.Capture
import com.fisica2.campomagnetico.CaptureAccumulator
import com.fisica2.campomagnetico.CaptureStore
import com.fisica2.campomagnetico.EarthField
import com.fisica2.campomagnetico.GuideKind
import com.fisica2.campomagnetico.GuideStep
import com.fisica2.campomagnetico.GuideSteps
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
    /** Orientación suavizada (null sin gravedad). */
    val attitude: Attitude?,
)

enum class CapturePurpose { NORMAL, FLIP_0, FLIP_180 }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val store = CaptureStore(app)
    private val sensors = SensorReader(app, ::onSample)
    private val locationReader = LocationReader(app)
    private val handler = Handler(Looper.getMainLooper())
    private val smoother = AttitudeSmoother()

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
    private var captureExpected: Vec3? = null
    private var captureFromGuide = false
    private val finishRunnable = Runnable { finishCapture() }

    // --- Modo guiado ---
    /** Paso actual (0 a pasos − 1); igual al número de pasos cuando la guía terminó. */
    var guideIndex by mutableIntStateOf(0)
        private set
    /** Pasos ya capturados en esta sesión. */
    val guideDone = mutableStateListOf<Int>()
    var lastGuideCapture by mutableStateOf<Capture?>(null)
        private set
    /** Segundos que faltan para que empiece una captura con espera (0 = sin espera en curso). */
    var countdown by mutableIntStateOf(0)
        private set
    private var pendingGuideStep: Int = -1
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (countdown <= 1) {
                countdown = 0
                beginGuidedCapture(pendingGuideStep)
            } else {
                countdown -= 1
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    // --- Asistente de 180° ---
    var flip0 by mutableStateOf<Capture?>(null)
        private set
    var flip180 by mutableStateOf<Capture?>(null)
        private set

    var message by mutableStateOf<String?>(null)

    /** Valores con los que se compara y con cuya declinación se corrige el norte. */
    val theory: ReferenceValues get() = if (theorySource == TheorySource.MODEL) model else plan

    /** Pasos del modo guiado (dependen de los valores teóricos elegidos). */
    val guideSteps: List<GuideStep> get() = GuideSteps.build(theory)

    // ---------------------------------------------------------------- Sensores

    fun startSensors() {
        model = computeModel() // la fecha cambia: se recalcula al volver a la app
        sensors.start()
    }

    fun stopSensors() {
        sensors.stop()
        smoother.reset()
        if (countdown > 0) {
            handler.removeCallbacks(countdownRunnable)
            countdown = 0
        }
        if (capturing) {
            cancelCapture()
            message = "Captura cancelada: la app pasó a segundo plano."
        }
    }

    private fun onSample(s: MagSample) {
        val t = theory
        val earth = s.rotation?.let { MagMath.deviceToEarth(it, s.b, t.declinationDeg) }
        val attitude = s.rotation?.let { AttitudeMath.fromRotation(it, t.declinationDeg) }
        val smooth = attitude?.let { smoother.update(it) }
        accumulator?.let {
            it.add(s.b, earth, s.raw, s.bias, s.accuracy, attitude)
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
                attitude = smooth,
            )
        }
    }

    // ---------------------------------------------------------------- Capturas

    fun startCapture(
        orientation: String,
        note: String,
        purpose: CapturePurpose = CapturePurpose.NORMAL,
        expected: Vec3? = null,
        fromGuide: Boolean = false,
    ) {
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
        captureExpected = expected
        captureFromGuide = fromGuide
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
            expected = captureExpected,
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
        if (captureFromGuide) {
            lastGuideCapture = capture
            if (guideIndex !in guideDone) guideDone.add(guideIndex)
            guideIndex = (guideIndex + 1).coerceAtMost(guideSteps.size)
        }
    }

    // ---------------------------------------------------------------- Modo guiado

    /** Lectura (Bx, By, Bz) esperada en la posición del paso [index]. */
    fun expectedFor(index: Int): Vec3? {
        val step = guideSteps.getOrNull(index) ?: return null
        val t = theory
        return AttitudeMath.expectedReading(t.x, t.y, t.z, step.target.asAttitude())
    }

    /** Captura del paso actual; con [delaySeconds] > 0 espera antes de empezar (para soltar el celular). */
    fun captureGuideStep(delaySeconds: Int = 0) {
        if (capturing || countdown > 0) return
        if (guideSteps.getOrNull(guideIndex) == null) return
        if (delaySeconds <= 0) {
            beginGuidedCapture(guideIndex)
        } else {
            pendingGuideStep = guideIndex
            countdown = delaySeconds
            handler.postDelayed(countdownRunnable, 1_000L)
        }
    }

    fun cancelCountdown() {
        handler.removeCallbacks(countdownRunnable)
        countdown = 0
    }

    private fun beginGuidedCapture(index: Int) {
        val step = guideSteps.getOrNull(index) ?: return
        if (index != guideIndex) return
        val purpose = when (step.kind) {
            GuideKind.FLIP_0 -> CapturePurpose.FLIP_0
            GuideKind.FLIP_180 -> CapturePurpose.FLIP_180
            GuideKind.NORMAL -> CapturePurpose.NORMAL
        }
        startCapture(step.orientation, step.note, purpose, expectedFor(index), fromGuide = true)
    }

    fun goToGuideStep(index: Int) {
        if (capturing || countdown > 0) return
        guideIndex = index.coerceIn(0, guideSteps.size)
    }

    fun restartGuide() {
        if (capturing) return
        cancelCountdown()
        guideIndex = 0
        guideDone.clear()
        lastGuideCapture = null
    }

    fun startFlip0() = startCapture(Orientations.FLIP_0, "", CapturePurpose.FLIP_0)
    fun startFlip180() = startCapture(Orientations.FLIP_180, "", CapturePurpose.FLIP_180)

    fun resetFlip() {
        flip0 = null
        flip180 = null
    }

    fun deleteCapture(c: Capture) {
        captures.remove(c)
        if (lastGuideCapture?.id == c.id) lastGuideCapture = null
        if (flip0?.id == c.id) flip0 = null
        if (flip180?.id == c.id) flip180 = null
        store.save(captures)
    }

    fun deleteAllCaptures() {
        captures.clear()
        lastGuideCapture = null
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
