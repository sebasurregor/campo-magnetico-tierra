package com.fisica2.campomagnetico

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Orientación del teléfono (rumbo, inclinación de la punta y giro lateral) y pasos del modo guiado.
 * Kotlin puro, sin clases de Android, para poder probarlo con JUnit.
 *
 * Convenciones (las mismas de MagMath):
 *  - Ejes del teléfono: plano y pantalla arriba, x a la derecha, y hacia la parte superior, z sale de la pantalla.
 *  - Rumbo: ángulo de la parte superior del teléfono respecto al norte GEOGRÁFICO, positivo hacia el este.
 *  - Inclinación de la punta: cuánto baja la parte superior bajo la horizontal (positivo = punta hacia abajo).
 *  - Giro lateral: giro sobre el eje largo; 0° = pantalla arriba, 90° = borde derecho abajo, 180° = boca abajo.
 */

/** Orientación del teléfono respecto a la Tierra, en grados. */
data class Attitude(val headingDeg: Double, val pitchDeg: Double, val rollDeg: Double)

/**
 * Posición objetivo de un paso guiado. Un ángulo NaN significa "no importa".
 * Las tolerancias son en grados.
 */
data class AttitudeTarget(
    val headingDeg: Double,
    val pitchDeg: Double,
    val rollDeg: Double,
    val headingTol: Double = 6.0,
    val pitchTol: Double = 5.0,
    val rollTol: Double = 6.0,
) {
    /** La misma posición con 0 en lugar de los ángulos que no importan (para calcular la lectura esperada). */
    fun asAttitude() = Attitude(
        headingDeg = if (headingDeg.isNaN()) 0.0 else headingDeg,
        pitchDeg = if (pitchDeg.isNaN()) 0.0 else pitchDeg,
        rollDeg = if (rollDeg.isNaN()) 0.0 else rollDeg,
    )
}

/** Resultado de comparar la orientación actual con la del paso. */
data class GuideCheck(
    val headingOk: Boolean,
    val pitchOk: Boolean,
    val rollOk: Boolean,
    /** Indicaciones en lenguaje sencillo, en el orden en que conviene seguirlas. */
    val hints: List<String>,
) {
    val ok: Boolean get() = headingOk && pitchOk && rollOk
}

object AttitudeMath {
    /** Lleva un ángulo al intervalo (−180°, 180°]. */
    fun normalize180(deg: Double): Double {
        var a = deg % 360.0
        if (a <= -180.0) a += 360.0
        if (a > 180.0) a -= 360.0
        return a
    }

    /**
     * Orientación a partir de la matriz [r] de SensorManager.getRotationMatrix (por filas), que lleva
     * vectores del teléfono al marco (Este, Norte MAGNÉTICO, Arriba). Sus columnas son los ejes del
     * teléfono expresados en ese marco. Como ese norte es el magnético, al rumbo se le suma la
     * declinación D para referirlo al norte geográfico.
     *
     * Si el eje y está casi vertical (más de 60° respecto a la horizontal), su dirección horizontal ya
     * no sirve como rumbo; entonces se usa la del eje z (hacia dónde mira la pantalla), con el signo que
     * mantiene el mismo rumbo que tenía la punta antes de inclinarse.
     */
    fun fromRotation(r: DoubleArray, declinationDeg: Double): Attitude {
        require(r.size == 9) { "La matriz de rotación debe tener 9 elementos" }
        val xUp = r[6]
        val yE = r[1]; val yN = r[4]; val yUp = r[7]
        val zE = r[2]; val zN = r[5]; val zUp = r[8]
        val magneticHeading = if (hypot(yE, yN) >= 0.5) {
            Math.toDegrees(atan2(yE, yN))
        } else {
            val s = if (yUp < 0) 1.0 else -1.0
            Math.toDegrees(atan2(s * zE, s * zN))
        }
        val pitch = Math.toDegrees(asin((-yUp).coerceIn(-1.0, 1.0)))
        val roll = Math.toDegrees(atan2(-xUp, zUp))
        return Attitude(normalize180(magneticHeading + declinationDeg), pitch, roll)
    }

    /**
     * Ejes x, y, z del teléfono en el marco geográfico (Este, Norte, Arriba) para una orientación:
     * primero se gira sobre la mesa (rumbo), luego se baja la punta (giro sobre x) y por último se gira
     * sobre el eje largo (giro sobre y).
     */
    fun phoneAxes(a: Attitude): Triple<Vec3, Vec3, Vec3> {
        val h = Math.toRadians(a.headingDeg)
        val p = Math.toRadians(a.pitchDeg)
        val r = Math.toRadians(a.rollDeg)
        val x0 = Vec3(cos(h), -sin(h), 0.0)
        val y0 = Vec3(sin(h), cos(h), 0.0)
        val z0 = Vec3(0.0, 0.0, 1.0)
        // Bajar la punta: y se inclina hacia abajo y z hacia adelante.
        val y1 = y0 * cos(p) + z0 * (-sin(p))
        val z1 = y0 * sin(p) + z0 * cos(p)
        // Girar sobre el eje largo: el borde derecho (x) baja y la pantalla (z) mira hacia la derecha.
        val x2 = x0 * cos(r) + z1 * (-sin(r))
        val z2 = x0 * sin(r) + z1 * cos(r)
        return Triple(x2, y1, z2)
    }

    /**
     * Lectura (Bx, By, Bz) que debería marcar el teléfono en la orientación [a], para un campo con
     * componentes X (norte), Y (este) y Z (hacia abajo). Es la proyección del campo sobre cada eje.
     */
    fun expectedReading(x: Double, y: Double, z: Double, a: Attitude): Vec3 {
        val (ax, ay, az) = phoneAxes(a)
        val b = Vec3(y, x, -z) // (Este, Norte, Arriba)
        return Vec3(dot(b, ax), dot(b, ay), dot(b, az))
    }

    /** Ángulo (bajo la horizontal) al que hay que bajar la punta, con y al norte geográfico, para que Bz = 0. */
    fun alignPitchDeg(x: Double, z: Double): Double = Math.toDegrees(atan2(z, x))

    fun check(current: Attitude, target: AttitudeTarget): GuideCheck {
        val hints = mutableListOf<String>()

        var pitchOk = true
        if (!target.pitchDeg.isNaN()) {
            val d = target.pitchDeg - current.pitchDeg
            pitchOk = abs(d) <= target.pitchTol
            if (!pitchOk) {
                hints += if (d > 0) "Baja la punta del celular ${deg(d)}" else "Sube la punta del celular ${deg(d)}"
            }
        }

        var rollOk = true
        if (!target.rollDeg.isNaN()) {
            val d = normalize180(target.rollDeg - current.rollDeg)
            rollOk = abs(d) <= target.rollTol
            if (!rollOk) {
                hints += if (d > 0) "Inclina el celular hacia la derecha ${deg(d)} (borde derecho hacia abajo)"
                else "Inclina el celular hacia la izquierda ${deg(d)} (borde izquierdo hacia abajo)"
            }
        }

        var headingOk = true
        if (!target.headingDeg.isNaN()) {
            val d = normalize180(target.headingDeg - current.headingDeg)
            headingOk = abs(d) <= target.headingTol
            if (!headingOk) {
                hints += if (d > 0) "Gira el celular hacia la derecha ${deg(d)}" else "Gira el celular hacia la izquierda ${deg(d)}"
            }
        }
        return GuideCheck(headingOk, pitchOk, rollOk, hints)
    }

    private fun deg(d: Double) = "${abs(d).roundToInt()}°"

    private fun dot(a: Vec3, b: Vec3) = a.x * b.x + a.y * b.y + a.z * b.z
}

/**
 * Suaviza la orientación (promedio móvil exponencial) para que la brújula no tiemble.
 * Los ángulos que dan la vuelta (rumbo y giro) se promedian con su seno y coseno.
 */
class AttitudeSmoother(private val alpha: Double = 0.15) {
    private var hs = 0.0
    private var hc = 1.0
    private var p = 0.0
    private var rs = 0.0
    private var rc = 1.0
    private var initialized = false

    fun reset() {
        initialized = false
    }

    fun update(a: Attitude): Attitude {
        val h = Math.toRadians(a.headingDeg)
        val r = Math.toRadians(a.rollDeg)
        if (!initialized) {
            hs = sin(h); hc = cos(h); p = a.pitchDeg; rs = sin(r); rc = cos(r)
            initialized = true
        } else {
            hs += alpha * (sin(h) - hs)
            hc += alpha * (cos(h) - hc)
            p += alpha * (a.pitchDeg - p)
            rs += alpha * (sin(r) - rs)
            rc += alpha * (cos(r) - rc)
        }
        return Attitude(Math.toDegrees(atan2(hs, hc)), p, Math.toDegrees(atan2(rs, rc)))
    }
}

/** Acumula orientaciones para sacar el promedio de una captura. */
class AttitudeAverager {
    private var hs = 0.0
    private var hc = 0.0
    private var p = 0.0
    private var rs = 0.0
    private var rc = 0.0
    var count = 0
        private set

    fun add(a: Attitude) {
        val h = Math.toRadians(a.headingDeg)
        val r = Math.toRadians(a.rollDeg)
        hs += sin(h); hc += cos(h); p += a.pitchDeg; rs += sin(r); rc += cos(r)
        count++
    }

    fun mean(): Attitude? = if (count == 0) null else Attitude(
        Math.toDegrees(atan2(hs, hc)),
        p / count,
        Math.toDegrees(atan2(rs, rc)),
    )
}

enum class GuideKind { NORMAL, FLIP_0, FLIP_180 }

/** Un paso del modo guiado. */
data class GuideStep(
    val title: String,
    val instruction: String,
    /** Etiqueta con la que se guarda la captura (la misma de la pestaña Capturas). */
    val orientation: String,
    val note: String,
    val target: AttitudeTarget,
    val kind: GuideKind = GuideKind.NORMAL,
)

object GuideSteps {
    private const val FREE = Double.NaN

    /** Los 13 pasos: plano al norte, giro de 180° y las rotaciones en z, x e y. */
    fun build(theory: ReferenceValues): List<GuideStep> {
        val tilt = AttitudeMath.alignPitchDeg(theory.x, theory.z)
        val tiltText = "${tilt.roundToInt()}°"
        val flatNorth = AttitudeTarget(0.0, 0.0, 0.0)
        val sameAsStep1 = "Es la misma posición del paso 1: celular plano, pantalla arriba y parte de arriba " +
            "hacia el norte. Si ya está así, solo toca Capturar."
        return listOf(
            GuideStep(
                "Plano hacia el norte",
                "Pon el celular plano sobre la mesa, con la pantalla hacia arriba. Gíralo sobre la mesa, sin " +
                    "levantarlo, hasta que todo se ponga verde: la parte de arriba quedará apuntando al norte geográfico.",
                Orientations.FLAT_NORTH, "rumbo 0°", flatNorth,
            ),
            GuideStep(
                "Giro de 180° (1 de 2)",
                "No muevas el celular de la posición anterior y toca Capturar. Es la primera mitad de la " +
                    "corrección del desfase del celular.",
                Orientations.FLIP_0, "", flatNorth, GuideKind.FLIP_0,
            ),
            GuideStep(
                "Giro de 180° (2 de 2)",
                "Gira el celular media vuelta sobre la mesa, sin levantarlo, hasta que la parte de arriba " +
                    "apunte al sur. Bx y By deberían cambiar de signo y Bz quedarse casi igual.",
                Orientations.FLIP_180, "", AttitudeTarget(180.0, 0.0, 0.0), GuideKind.FLIP_180,
            ),
            GuideStep("Rotación en z: 0°", sameAsStep1, "Rotación eje z", "0° (norte)", flatNorth),
            GuideStep(
                "Rotación en z: 90°",
                "Celular plano. Gíralo un cuarto de vuelta hacia la derecha sobre la mesa: la parte de arriba " +
                    "debe apuntar al este.",
                "Rotación eje z", "90° (este)", AttitudeTarget(90.0, 0.0, 0.0),
            ),
            GuideStep(
                "Rotación en z: 180°",
                "Celular plano. Sigue girando hacia la derecha hasta que la parte de arriba apunte al sur.",
                "Rotación eje z", "180° (sur)", AttitudeTarget(180.0, 0.0, 0.0),
            ),
            GuideStep(
                "Rotación en z: 270°",
                "Celular plano. Sigue girando hacia la derecha hasta que la parte de arriba apunte al oeste.",
                "Rotación eje z", "270° (oeste)", AttitudeTarget(-90.0, 0.0, 0.0),
            ),
            GuideStep("Rotación en x: 0°", sameAsStep1, "Rotación eje x", "0° (plano)", flatNorth),
            GuideStep(
                "Rotación en x: $tiltText",
                "Con la parte de arriba hacia el norte, baja la punta del celular unos $tiltText hacia la mesa " +
                    "(el borde de abajo queda levantado). Apóyalo en un libro o una caja para que quede quieto. " +
                    "En esta posición Bz debería quedar cerca de 0: el celular queda alineado con las líneas de campo.",
                "Rotación eje x", "$tiltText (Bz ≈ 0)", AttitudeTarget(0.0, tilt, FREE, pitchTol = 3.0),
            ),
            GuideStep(
                "Rotación en x: 90°",
                "Pon el celular vertical, con la punta hacia abajo y la pantalla mirando hacia el norte. " +
                    "Apóyalo contra una caja o un libro.",
                "Rotación eje x", "90° (vertical)", AttitudeTarget(0.0, 90.0, FREE, pitchTol = 6.0),
            ),
            GuideStep("Rotación en y: 0°", sameAsStep1, "Rotación eje y", "0° (plano)", flatNorth),
            GuideStep(
                "Rotación en y: 90°",
                "Con la parte de arriba hacia el norte, acuesta el celular de lado: el borde derecho toca la " +
                    "mesa y la pantalla mira hacia el este. Apóyalo en un libro.",
                "Rotación eje y", "90° (de lado)", AttitudeTarget(0.0, 0.0, 90.0),
            ),
            GuideStep(
                "Rotación en y: 180°",
                "Aquí no vas a ver la pantalla. Toca \"Capturar en 5 s\", pon el celular boca abajo con la " +
                    "parte de arriba hacia el norte y suéltalo. Espera unos 10 segundos y voltéalo: si la captura " +
                    "salió bien, la app ya habrá terminado la guía.",
                "Rotación eje y", "180° (boca abajo)", AttitudeTarget(0.0, 0.0, 180.0),
            ),
        )
    }
}
