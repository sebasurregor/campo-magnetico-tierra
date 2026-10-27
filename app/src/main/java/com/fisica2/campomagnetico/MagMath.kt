package com.fisica2.campomagnetico

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Física del laboratorio en Kotlin puro (sin clases de Android) para poder probarla con JUnit.
 *
 * Convenciones:
 *  - Marco del teléfono: con el teléfono plano y la pantalla hacia arriba, x apunta a la derecha,
 *    y hacia la parte superior y z sale de la pantalla.
 *  - Marco terrestre (NED): X = componente norte (geográfico), Y = componente este,
 *    Z = componente vertical positiva hacia abajo.
 *  - Campos en μT y ángulos en grados.
 */

/** Vector en μT. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    val norm: Double get() = sqrt(x * x + y * y + z * z)

    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
}

/** Campo en el marco terrestre: X norte geográfico, Y este, Z hacia abajo (μT). */
data class EarthField(val x: Double, val y: Double, val z: Double) {
    val h: Double get() = MagMath.horizontal(x, y)
    val f: Double get() = MagMath.total(x, y, z)
    val inclinationDeg: Double get() = MagMath.inclinationDeg(h, z)
    val declinationDeg: Double get() = MagMath.declinationDeg(x, y)
    fun parallelToAxis(latDeg: Double): Double = MagMath.parallelToAxis(x, z, latDeg)
}

/** Valores teóricos (del plan o del modelo de Android), en μT y grados. */
data class ReferenceValues(
    val x: Double,
    val y: Double,
    val z: Double,
    val h: Double,
    val f: Double,
    val inclinationDeg: Double,
    val declinationDeg: Double,
    val parallel: Double,
) {
    companion object {
        /** Plan de trabajo: WMM 2025, Medellín, octubre de 2026 (lat 6.2442, lon −75.5812, 1495 m). */
        val PLAN_MEDELLIN = ReferenceValues(
            x = 26.6, y = -3.7, z = 15.1, h = 26.9, f = 30.8,
            inclinationDeg = 29.3, declinationDeg = -8.0, parallel = 24.8,
        )

        /** Deriva H, F, I, D y B∥ a partir de X, Y, Z. */
        fun fromComponents(x: Double, y: Double, z: Double, latDeg: Double): ReferenceValues {
            val e = EarthField(x, y, z)
            return ReferenceValues(x, y, z, e.h, e.f, e.inclinationDeg, e.declinationDeg, e.parallelToAxis(latDeg))
        }
    }
}

/** Promedio y desviación estándar muestral (n − 1). */
data class Stat(val mean: Double, val sd: Double)

/** Resultado del método de giro de 180° sobre la mesa (solo componentes x e y del teléfono). */
data class FlipResult(val realX: Double, val realY: Double, val offsetX: Double, val offsetY: Double)

object MagMath {
    const val DEFAULT_LAT = 6.2442
    const val DEFAULT_LON = -75.5812
    const val DEFAULT_ALT_M = 1495.0

    /** Rango razonable del campo terrestre; fuera de él es probable que haya interferencia. */
    const val FIELD_MIN_UT = 20.0
    const val FIELD_MAX_UT = 70.0

    /** Valor de SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM (se copia para no depender de Android). */
    const val ACCURACY_MEDIUM = 2

    /** H = √(X² + Y²) */
    fun horizontal(x: Double, y: Double): Double = hypot(x, y)

    /** F = |B| = √(X² + Y² + Z²) */
    fun total(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    /** I = atan(Z / H), positiva cuando el campo apunta hacia abajo. */
    fun inclinationDeg(h: Double, z: Double): Double = Math.toDegrees(atan2(z, h))

    /** D = atan2(Y, X), positiva hacia el este. */
    fun declinationDeg(x: Double, y: Double): Double = Math.toDegrees(atan2(y, x))

    /**
     * Componente paralela al eje de rotación de la Tierra (sentido hacia el polo norte celeste):
     * B∥ = X·cos(φ) − Z·sin(φ), con φ la latitud geográfica.
     */
    fun parallelToAxis(x: Double, z: Double, latDeg: Double): Double {
        val phi = Math.toRadians(latDeg)
        return x * cos(phi) - z * sin(phi)
    }

    /** Lectura esperada con el teléfono plano, pantalla arriba y el eje y apuntando al norte geográfico. */
    fun expectedFlatNorth(x: Double, y: Double, z: Double): Vec3 = Vec3(y, x, -z)

    /**
     * Pasa la lectura del teléfono [b] al marco terrestre geográfico (NED).
     *
     * [r] es la matriz de 3×3 (por filas) de SensorManager.getRotationMatrix, que lleva vectores del
     * marco del teléfono al marco (Este, Norte, Arriba). Ese "Norte" es el NORTE MAGNÉTICO: Android lo
     * define como la dirección de la componente horizontal del propio campo medido, así que en ese marco
     * la componente este sale ≈ 0. Para referir X e Y al norte geográfico se gira el plano horizontal
     * un ángulo igual a la declinación teórica D (ver [magneticEnuToEarth]). Por eso la app no puede
     * medir D por sí misma: la toma del modelo o del plan.
     */
    fun deviceToEarth(r: DoubleArray, b: Vec3, declinationDeg: Double): EarthField {
        require(r.size == 9) { "La matriz de rotación debe tener 9 elementos" }
        val east = r[0] * b.x + r[1] * b.y + r[2] * b.z
        val northMag = r[3] * b.x + r[4] * b.y + r[5] * b.z
        val up = r[6] * b.x + r[7] * b.y + r[8] * b.z
        return magneticEnuToEarth(east, northMag, up, declinationDeg)
    }

    /**
     * Convierte componentes (Este magnético, Norte magnético, Arriba) a (X norte geográfico, Y este, Z abajo).
     * El norte magnético está girado un ángulo D hacia el este respecto al geográfico, así que:
     *   X = Nm·cos D − Em·sin D
     *   Y = Nm·sin D + Em·cos D
     *   Z = −Arriba
     */
    fun magneticEnuToEarth(eastMag: Double, northMag: Double, up: Double, declinationDeg: Double): EarthField {
        val d = Math.toRadians(declinationDeg)
        val x = northMag * cos(d) - eastMag * sin(d)
        val y = northMag * sin(d) + eastMag * cos(d)
        return EarthField(x, y, -up)
    }

    /** Promedio y desviación estándar muestral. Con una sola muestra la desviación es 0. */
    fun meanSd(values: List<Double>): Stat {
        require(values.isNotEmpty()) { "Se necesita al menos una muestra" }
        val mean = values.sum() / values.size
        if (values.size < 2) return Stat(mean, 0.0)
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return Stat(mean, sqrt(variance))
    }

    /**
     * Corrección por giro de 180° sobre la mesa. Al girar, las componentes x e y del campo cambian de
     * signo pero el desfase (bias) del sensor no, porque gira con el teléfono:
     *   B(0°) = B + b,  B(180°) = −B + b
     *   B_real = (B(0°) − B(180°)) / 2,  desfase = (B(0°) + B(180°)) / 2
     * El eje z no cambia de signo con este giro, así que no se puede corregir así.
     */
    fun flip180(b0: Vec3, b180: Vec3): FlipResult = FlipResult(
        realX = (b0.x - b180.x) / 2.0,
        realY = (b0.y - b180.y) / 2.0,
        offsetX = (b0.x + b180.x) / 2.0,
        offsetY = (b0.y + b180.y) / 2.0,
    )

    /** Error porcentual |medido − teórico| / |teórico| · 100. NaN si el teórico es 0. */
    fun percentError(measured: Double, theory: Double): Double =
        if (theory == 0.0) Double.NaN else abs(measured - theory) / abs(theory) * 100.0

    fun isOutOfRange(f: Double): Boolean = f < FIELD_MIN_UT || f > FIELD_MAX_UT

    /**
     * En Medellín (hemisferio norte magnético) el campo apunta hacia abajo: Z > 0. Si el Z medido tiene
     * claramente el signo contrario al teórico, el magnetómetro del teléfono entrega los ejes invertidos o
     * hay un imán muy cerca. Umbral de 3 μT para no saltar cerca del ecuador magnético.
     */
    fun isVerticalSignWrong(measuredZ: Double, theoryZ: Double): Boolean =
        (theoryZ > 3.0 && measuredZ < -3.0) || (theoryZ < -3.0 && measuredZ > 3.0)

    /** Baja, no confiable o sin contacto (cualquier valor por debajo de MEDIUM). */
    fun isLowAccuracy(accuracy: Int): Boolean = accuracy < ACCURACY_MEDIUM
}
