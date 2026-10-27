package com.fisica2.campomagnetico

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import kotlin.math.cos
import kotlin.math.sin

class AttitudeMathTest {
    private val plan = ReferenceValues.PLAN_MEDELLIN
    private val d = plan.declinationDeg

    /** Pasa un vector (Este, Norte, Arriba) geográfico al marco de Android (Este, Norte MAGNÉTICO, Arriba). */
    private fun geoToMag(v: Vec3, dDeg: Double): Vec3 {
        val a = Math.toRadians(dDeg)
        return Vec3(v.x * cos(a) - v.y * sin(a), v.y * cos(a) + v.x * sin(a), v.z)
    }

    /** Matriz que daría getRotationMatrix para el teléfono en la orientación [a] (columnas = ejes del teléfono). */
    private fun rotationFor(a: Attitude, dDeg: Double): DoubleArray {
        val (x, y, z) = AttitudeMath.phoneAxes(a)
        val xm = geoToMag(x, dDeg); val ym = geoToMag(y, dDeg); val zm = geoToMag(z, dDeg)
        return doubleArrayOf(
            xm.x, ym.x, zm.x,
            xm.y, ym.y, zm.y,
            xm.z, ym.z, zm.z,
        )
    }

    private fun assertAngle(expected: Double, actual: Double, tol: Double) =
        assertEquals(0.0, AttitudeMath.normalize180(expected - actual), tol)

    @Test
    fun fromRotation_recoversAttitude() {
        val cases = listOf(
            Attitude(0.0, 0.0, 0.0), Attitude(90.0, 0.0, 0.0), Attitude(180.0, 0.0, 0.0),
            Attitude(-90.0, 0.0, 0.0), Attitude(45.0, 20.0, 0.0), Attitude(0.0, 29.6, 0.0),
            Attitude(0.0, 0.0, 90.0), Attitude(0.0, 0.0, 180.0), Attitude(-30.0, 0.0, -60.0),
        )
        for (a in cases) {
            val got = AttitudeMath.fromRotation(rotationFor(a, d), d)
            assertAngle(a.headingDeg, got.headingDeg, 1e-6)
            assertEquals(a.pitchDeg, got.pitchDeg, 1e-6)
            assertAngle(a.rollDeg, got.rollDeg, 1e-6)
        }
    }

    /** Con la punta casi vertical la dirección se toma de hacia dónde mira la pantalla. */
    @Test
    fun fromRotation_verticalUsesScreenDirection() {
        for (pitch in listOf(70.0, 90.0)) {
            val got = AttitudeMath.fromRotation(rotationFor(Attitude(20.0, pitch, 0.0), d), d)
            assertAngle(20.0, got.headingDeg, 1e-6)
            assertEquals(pitch, got.pitchDeg, 1e-6)
        }
    }

    @Test
    fun expectedReading_tableValues() {
        val x = plan.x; val y = plan.y; val z = plan.z
        fun at(h: Double, p: Double, r: Double) = AttitudeMath.expectedReading(x, y, z, Attitude(h, p, r))
        assertVec(Vec3(y, x, -z), at(0.0, 0.0, 0.0))     // plano, y al norte
        assertVec(Vec3(-x, y, -z), at(90.0, 0.0, 0.0))   // punta al este
        assertVec(Vec3(-y, -x, -z), at(180.0, 0.0, 0.0)) // punta al sur
        assertVec(Vec3(x, -y, -z), at(-90.0, 0.0, 0.0))  // punta al oeste
        assertVec(Vec3(y, z, x), at(0.0, 90.0, 0.0))     // vertical, punta abajo, pantalla al norte
        assertVec(Vec3(z, x, y), at(0.0, 0.0, 90.0))     // de lado, borde derecho abajo
        assertVec(Vec3(-y, x, z), at(0.0, 0.0, 180.0))   // boca abajo
        // Al bajar la punta el ángulo de alineación, Bz se anula.
        val tilt = AttitudeMath.alignPitchDeg(x, z)
        assertEquals(29.6, tilt, 0.1)
        assertEquals(0.0, at(0.0, tilt, 0.0).z, 1e-9)
    }

    /** La lectura esperada, pasada al marco terrestre con la matriz de esa orientación, devuelve X, Y, Z. */
    @Test
    fun expectedReading_consistentWithEarthFrame() {
        for (a in listOf(Attitude(0.0, 0.0, 0.0), Attitude(130.0, 40.0, -20.0), Attitude(0.0, 90.0, 0.0))) {
            val b = AttitudeMath.expectedReading(plan.x, plan.y, plan.z, a)
            assertEquals(MagMath.total(plan.x, plan.y, plan.z), b.norm, 1e-9)
            val e = MagMath.deviceToEarth(rotationFor(a, d), b, d)
            assertEquals(plan.x, e.x, 1e-9)
            assertEquals(plan.y, e.y, 1e-9)
            assertEquals(plan.z, e.z, 1e-9)
        }
    }

    @Test
    fun check_givesHints() {
        val target = AttitudeTarget(0.0, 0.0, 0.0)
        assertTrue(AttitudeMath.check(Attitude(3.0, 1.0, -2.0), target).ok)
        val turnLeft = AttitudeMath.check(Attitude(20.0, 0.0, 0.0), target)
        assertFalse(turnLeft.ok)
        assertEquals(listOf("Gira el celular hacia la izquierda 20°"), turnLeft.hints)
        val turnRight = AttitudeMath.check(Attitude(-170.0, 0.0, 0.0), AttitudeTarget(180.0, 0.0, 0.0))
        assertEquals(listOf("Gira el celular hacia la izquierda 10°"), turnRight.hints)
        val lower = AttitudeMath.check(Attitude(0.0, 10.0, 0.0), AttitudeTarget(0.0, 30.0, Double.NaN))
        assertEquals(listOf("Baja la punta del celular 20°"), lower.hints)
        assertTrue(lower.rollOk)
        // El giro de 180° (boca abajo) se compara dando la vuelta: −178° está a 2° de 180°.
        assertTrue(AttitudeMath.check(Attitude(0.0, 0.0, -178.0), AttitudeTarget(0.0, 0.0, 180.0)).ok)
    }

    @Test
    fun smoother_wrapsAround() {
        val s = AttitudeSmoother(alpha = 0.5)
        s.update(Attitude(179.0, 0.0, 179.0))
        val a = s.update(Attitude(-179.0, 0.0, -179.0))
        assertAngle(180.0, a.headingDeg, 0.01)
        assertAngle(180.0, a.rollDeg, 0.01)
    }

    @Test
    fun guideSteps_list() {
        val steps = GuideSteps.build(plan)
        assertEquals(13, steps.size)
        assertEquals(Orientations.FLAT_NORTH, steps[0].orientation)
        assertEquals(GuideKind.FLIP_0, steps[1].kind)
        assertEquals(GuideKind.FLIP_180, steps[2].kind)
        assertEquals(4, steps.count { it.orientation == "Rotación eje z" })
        assertEquals(3, steps.count { it.orientation == "Rotación eje x" })
        assertEquals(3, steps.count { it.orientation == "Rotación eje y" })
    }

    @Test
    fun verticalSign() {
        assertTrue(MagMath.isVerticalSignWrong(-16.3, 15.1))
        assertFalse(MagMath.isVerticalSignWrong(17.0, 15.1))
        assertFalse(MagMath.isVerticalSignWrong(-1.0, 15.1))
    }

    @Test
    fun guidedCapture_csvHasExpectedAndAttitude() {
        val exp = AttitudeMath.expectedReading(plan.x, plan.y, plan.z, Attitude(90.0, 0.0, 0.0))
        val acc = CaptureAccumulator(MagMath.DEFAULT_LAT)
        acc.add(exp, null, null, null, 3, Attitude(88.0, 1.0, 0.0))
        acc.add(exp, null, null, null, 3, Attitude(92.0, -1.0, 0.0))
        val c = acc.build(1, 0, "Rotación eje z", "90° (este)", 6.2442, -75.5812, 1495.0, "Plan", plan, exp)!!
        assertEquals(90.0, c.attitude!!.headingDeg, 1e-9)
        assertEquals(setOf("Bx", "By", "Bz"), c.comparison().map { it.key }.toSet())
        val lines = CsvFormat.build(listOf(c), roundCoordinates = true, zone = ZoneOffset.UTC).lines()
        val header = lines[0].split(",")
        val row = lines[1].split(",")
        assertEquals(header.size, row.size)
        assertEquals("90.0", row[header.indexOf("rumbo_deg")])
        assertEquals(String.format(java.util.Locale.US, "%.3f", exp.x), row[header.indexOf("Bx_esp_uT")])
    }

    private fun assertVec(e: Vec3, a: Vec3) {
        assertEquals(e.x, a.x, 1e-9)
        assertEquals(e.y, a.y, 1e-9)
        assertEquals(e.z, a.z, 1e-9)
    }
}
