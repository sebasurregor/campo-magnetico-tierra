package com.fisica2.campomagnetico

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import kotlin.math.cos
import kotlin.math.sin

class MagMathTest {
    private val plan = ReferenceValues.PLAN_MEDELLIN
    private val lat = MagMath.DEFAULT_LAT

    // a) B∥ con X = 26.6, Z = 15.1 y φ = 6.2442° da ≈ 24.8 μT
    @Test
    fun parallelComponent_medellin() {
        assertEquals(24.8, MagMath.parallelToAxis(26.6, 15.1, 6.2442), 0.05)
    }

    // b) H, F e I con los valores de referencia dan 26.9 μT, 30.8 μT y 29.3° (±0.2)
    @Test
    fun horizontalTotalInclination_reference() {
        val h = MagMath.horizontal(26.6, -3.7)
        assertEquals(26.9, h, 0.2)
        assertEquals(30.8, MagMath.total(26.6, -3.7, 15.1), 0.2)
        assertEquals(29.3, MagMath.inclinationDeg(h, 15.1), 0.2)
    }

    @Test
    fun declination_reference() {
        assertEquals(-8.0, MagMath.declinationDeg(26.6, -3.7), 0.2)
    }

    // c) "plano, y al norte" da Bx = Y, By = X, Bz = −Z
    @Test
    fun flatNorth_expectedReading() {
        val b = MagMath.expectedFlatNorth(plan.x, plan.y, plan.z)
        assertEquals(plan.y, b.x, 1e-12)
        assertEquals(plan.x, b.y, 1e-12)
        assertEquals(-plan.z, b.z, 1e-12)
    }

    /**
     * c) también por el camino completo: se arma la matriz que daría getRotationMatrix para un teléfono
     * plano con y hacia el norte GEOGRÁFICO (el marco de Android usa el norte magnético, girado D),
     * se genera la lectura del teléfono y se comprueba que es (Y, X, −Z) y que se recupera X, Y, Z.
     */
    @Test
    fun flatNorth_throughRotationMatrix() {
        val x = plan.x; val y = plan.y; val z = plan.z
        val dDeg = MagMath.declinationDeg(x, y)
        val d = Math.toRadians(dDeg)
        val h = MagMath.horizontal(x, y)
        // Columnas = ejes del teléfono en (Este magnético, Norte magnético, Arriba)
        val r = doubleArrayOf(
            cos(d), -sin(d), 0.0,
            sin(d), cos(d), 0.0,
            0.0, 0.0, 1.0,
        )
        val world = Vec3(0.0, h, -z) // en el marco magnético la componente este es 0
        val b = transposeTimes(r, world)

        assertEquals(y, b.x, 1e-9)
        assertEquals(x, b.y, 1e-9)
        assertEquals(-z, b.z, 1e-9)

        val e = MagMath.deviceToEarth(r, b, dDeg)
        assertEquals(x, e.x, 1e-9)
        assertEquals(y, e.y, 1e-9)
        assertEquals(z, e.z, 1e-9)
    }

    /** El resultado en el marco terrestre no depende de la orientación del teléfono. */
    @Test
    fun earthFrame_independentOfOrientation() {
        val dDeg = plan.declinationDeg
        val worldMag = Vec3(0.0, 26.9, -15.1)
        val reference = MagMath.deviceToEarth(identity(), worldMag, dDeg)
        for ((ax, az) in listOf(30.0 to 0.0, 90.0 to 45.0, -60.0 to 170.0, 180.0 to -90.0)) {
            val r = multiply(rotZ(az), rotX(ax))
            val b = transposeTimes(r, worldMag)
            assertEquals(worldMag.norm, b.norm, 1e-9) // |B| se conserva al rotar
            val e = MagMath.deviceToEarth(r, b, dDeg)
            assertEquals(reference.x, e.x, 1e-9)
            assertEquals(reference.y, e.y, 1e-9)
            assertEquals(reference.z, e.z, 1e-9)
            assertEquals(29.3, e.inclinationDeg, 0.2)
        }
    }

    // d) la fórmula de desfase a 180° recupera un campo conocido al que se le sumó un bias
    @Test
    fun flip180_recoversFieldAndBias() {
        val field = Vec3(-3.7, 26.6, -15.1)
        val bias = Vec3(12.5, -40.0, 7.0)
        // Al girar 180° sobre la mesa, x e y del campo cambian de signo; el bias gira con el teléfono.
        val b0 = field + bias
        val b180 = Vec3(-field.x, -field.y, field.z) + bias
        val res = MagMath.flip180(b0, b180)
        assertEquals(field.x, res.realX, 1e-9)
        assertEquals(field.y, res.realY, 1e-9)
        assertEquals(bias.x, res.offsetX, 1e-9)
        assertEquals(bias.y, res.offsetY, 1e-9)
    }

    @Test
    fun meanSd_sample() {
        val s = MagMath.meanSd(listOf(1.0, 2.0, 3.0, 4.0))
        assertEquals(2.5, s.mean, 1e-12)
        assertEquals(1.2909944, s.sd, 1e-6)
        assertEquals(0.0, MagMath.meanSd(listOf(5.0)).sd, 0.0)
    }

    @Test
    fun percentError_andRanges() {
        assertEquals(10.0, MagMath.percentError(33.0, 30.0), 1e-9)
        assertTrue(MagMath.percentError(1.0, 0.0).isNaN())
        assertTrue(MagMath.isOutOfRange(15.0))
        assertTrue(MagMath.isOutOfRange(80.0))
        assertTrue(!MagMath.isOutOfRange(30.8))
        assertTrue(MagMath.isLowAccuracy(1))
        assertTrue(!MagMath.isLowAccuracy(3))
    }

    @Test
    fun fromComponents_matchesPlan() {
        val v = ReferenceValues.fromComponents(26.6, -3.7, 15.1, lat)
        assertEquals(plan.h, v.h, 0.1)
        assertEquals(plan.f, v.f, 0.1)
        assertEquals(plan.inclinationDeg, v.inclinationDeg, 0.1)
        assertEquals(plan.declinationDeg, v.declinationDeg, 0.1)
        assertEquals(plan.parallel, v.parallel, 0.1)
    }

    @Test
    fun capture_flatNorth_comparisonAndCsv() {
        val acc = CaptureAccumulator(lat)
        val expected = MagMath.expectedFlatNorth(plan.x, plan.y, plan.z)
        val earth = EarthField(plan.x, plan.y, plan.z)
        acc.add(expected + Vec3(0.1, 0.0, 0.0), earth, null, null, 3)
        acc.add(expected - Vec3(0.1, 0.0, 0.0), earth, null, null, 2)
        assertNull(CaptureAccumulator(lat).build(1, 0, "x", "", 0.0, 0.0, 0.0, "Plan", plan))

        val c = acc.build(1, 0, Orientations.FLAT_NORTH, "=nota", 6.2442, -75.5812, 1495.0, "Plan", plan)!!
        assertEquals(2, c.minAccuracy)
        assertEquals(expected.x, c.bx.mean, 1e-9)
        val rows = c.comparison().associateBy { it.key }
        assertEquals(setOf("F", "H", "Z", "I", "Bpar", "Bx", "By", "Bz"), rows.keys)
        assertEquals(0.0, rows.getValue("Bx").absDiff, 1e-9)

        val csv = CsvFormat.build(listOf(c), roundCoordinates = true, zone = ZoneOffset.UTC).lines()
        val header = csv[0].split(",")
        val row = parseCsvLine(csv[1])
        assertEquals(CsvFormat.HEADER.size, header.size)
        assertEquals(header.size, row.size)
        assertEquals("6.24", row[header.indexOf("lat")])
        assertEquals("-75.58", row[header.indexOf("lon")])
        assertEquals("'=nota", row[header.indexOf("nota")])
        assertEquals(Orientations.FLAT_NORTH, row[header.indexOf("orientacion")])

        val full = CsvFormat.build(listOf(c), roundCoordinates = false, zone = ZoneOffset.UTC).lines()[1].let { parseCsvLine(it) }
        assertEquals("6.244200", full[header.indexOf("lat")])
    }

    // --- utilidades para las pruebas ---

    /** Separa una línea CSV respetando campos entre comillas. */
    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                quoted && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    private fun identity() = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

    private fun rotX(deg: Double): DoubleArray {
        val a = Math.toRadians(deg)
        return doubleArrayOf(1.0, 0.0, 0.0, 0.0, cos(a), -sin(a), 0.0, sin(a), cos(a))
    }

    private fun rotZ(deg: Double): DoubleArray {
        val a = Math.toRadians(deg)
        return doubleArrayOf(cos(a), -sin(a), 0.0, sin(a), cos(a), 0.0, 0.0, 0.0, 1.0)
    }

    private fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { k ->
        val i = k / 3
        val j = k % 3
        (0..2).sumOf { a[i * 3 + it] * b[it * 3 + j] }
    }

    /** Rᵀ·v: pasa un vector del marco (Este, Norte, Arriba) al marco del teléfono. */
    private fun transposeTimes(r: DoubleArray, v: Vec3) = Vec3(
        r[0] * v.x + r[3] * v.y + r[6] * v.z,
        r[1] * v.x + r[4] * v.y + r[7] * v.z,
        r[2] * v.x + r[5] * v.y + r[8] * v.z,
    )
}
