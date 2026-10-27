package com.fisica2.campomagnetico

import kotlin.math.abs

object Orientations {
    const val FLAT_NORTH = "Plano, y al norte"
    val USER_CHOICES = listOf(FLAT_NORTH, "Rotación eje z", "Rotación eje x", "Rotación eje y", "Otra")

    const val FLIP_0 = "Asistente 180°: posición 0°"
    const val FLIP_180 = "Asistente 180°: posición 180°"
}

/** Estadísticas de las magnitudes medidas en el marco terrestre durante una captura. */
data class EarthStats(
    val x: Stat,
    val y: Stat,
    val z: Stat,
    val f: Stat,
    val h: Stat,
    val i: Stat,
    val parallel: Stat,
)

/** Una fila de la tabla medido vs. teórico. */
data class ComparisonRow(
    val key: String,
    val label: String,
    val unit: String,
    val measured: Double,
    val theory: Double,
) {
    val absDiff: Double get() = abs(measured - theory)
    val percent: Double get() = MagMath.percentError(measured, theory)
}

/** Captura promediada durante unos segundos. */
data class Capture(
    val id: Long,
    val timeMillis: Long,
    val orientation: String,
    val note: String,
    val lat: Double,
    val lon: Double,
    val altM: Double,
    val samples: Int,
    /** Peor precisión del sensor (SENSOR_STATUS_*) vista durante la captura. */
    val minAccuracy: Int,
    val bx: Stat,
    val by: Stat,
    val bz: Stat,
    /** Null si el teléfono no tiene sensor de gravedad ni acelerómetro. */
    val earth: EarthStats?,
    /** Promedios del sensor sin calibrar y del bias estimado por Android (si el teléfono los tiene). */
    val raw: Vec3?,
    val bias: Vec3?,
    val theorySource: String,
    val theory: ReferenceValues,
    /** Orientación promedio durante la captura (rumbo, inclinación de la punta, giro lateral). */
    val attitude: Attitude? = null,
    /** Lectura esperada para la posición del paso guiado (null si la captura no vino de la guía). */
    val expected: Vec3? = null,
) {
    val deviceMean: Vec3 get() = Vec3(bx.mean, by.mean, bz.mean)

    /** Bx, By, Bz esperados: los del paso guiado o, si es "Plano, y al norte", (Y, X, −Z). */
    fun expectedReading(): Vec3? = expected
        ?: if (orientation == Orientations.FLAT_NORTH) MagMath.expectedFlatNorth(theory.x, theory.y, theory.z) else null

    /** F, H, Z, I y B∥ medidos vs. teóricos; y Bx, By, Bz vs. los esperados cuando se conocen. */
    fun comparison(): List<ComparisonRow> {
        val rows = mutableListOf<ComparisonRow>()
        val t = theory
        earth?.let { e ->
            rows += ComparisonRow("F", "F", "μT", e.f.mean, t.f)
            rows += ComparisonRow("H", "H", "μT", e.h.mean, t.h)
            rows += ComparisonRow("Z", "Z", "μT", e.z.mean, t.z)
            rows += ComparisonRow("I", "I", "°", e.i.mean, t.inclinationDeg)
            rows += ComparisonRow("Bpar", "B∥", "μT", e.parallel.mean, t.parallel)
        }
        expectedReading()?.let { exp ->
            val flat = expected == null
            rows += ComparisonRow("Bx", if (flat) "Bx (≈ Y)" else "Bx", "μT", bx.mean, exp.x)
            rows += ComparisonRow("By", if (flat) "By (≈ X)" else "By", "μT", by.mean, exp.y)
            rows += ComparisonRow("Bz", if (flat) "Bz (≈ −Z)" else "Bz", "μT", bz.mean, exp.z)
        }
        return rows
    }
}

/** Acumula muestras durante una captura y calcula promedios y desviaciones. */
class CaptureAccumulator(private val latDeg: Double) {
    private val device = mutableListOf<Vec3>()
    private val earth = mutableListOf<EarthField>()
    private val raw = mutableListOf<Vec3>()
    private val bias = mutableListOf<Vec3>()
    private var minAccuracy = Int.MAX_VALUE
    private val attitudes = AttitudeAverager()

    val count: Int get() = device.size

    fun add(b: Vec3, e: EarthField?, rawB: Vec3?, biasB: Vec3?, accuracy: Int, attitude: Attitude? = null) {
        device += b
        attitude?.let { attitudes.add(it) }
        e?.let { earth += it }
        rawB?.let { raw += it }
        biasB?.let { bias += it }
        if (accuracy < minAccuracy) minAccuracy = accuracy
    }

    /** Devuelve null si no hubo al menos 2 muestras. */
    fun build(
        id: Long,
        timeMillis: Long,
        orientation: String,
        note: String,
        lat: Double,
        lon: Double,
        altM: Double,
        theorySource: String,
        theory: ReferenceValues,
        expected: Vec3? = null,
    ): Capture? {
        if (device.size < 2) return null
        val earthStats = if (earth.size >= 2) {
            EarthStats(
                x = MagMath.meanSd(earth.map { it.x }),
                y = MagMath.meanSd(earth.map { it.y }),
                z = MagMath.meanSd(earth.map { it.z }),
                f = MagMath.meanSd(earth.map { it.f }),
                h = MagMath.meanSd(earth.map { it.h }),
                i = MagMath.meanSd(earth.map { it.inclinationDeg }),
                parallel = MagMath.meanSd(earth.map { it.parallelToAxis(latDeg) }),
            )
        } else {
            null
        }
        return Capture(
            id = id,
            timeMillis = timeMillis,
            orientation = orientation,
            note = note,
            lat = lat,
            lon = lon,
            altM = altM,
            samples = device.size,
            minAccuracy = minAccuracy,
            bx = MagMath.meanSd(device.map { it.x }),
            by = MagMath.meanSd(device.map { it.y }),
            bz = MagMath.meanSd(device.map { it.z }),
            earth = earthStats,
            raw = mean(raw),
            bias = mean(bias),
            theorySource = theorySource,
            theory = theory,
            attitude = attitudes.mean(),
            expected = expected,
        )
    }

    private fun mean(list: List<Vec3>): Vec3? {
        if (list.isEmpty()) return null
        val n = list.size.toDouble()
        return Vec3(list.sumOf { it.x } / n, list.sumOf { it.y } / n, list.sumOf { it.z } / n)
    }
}
