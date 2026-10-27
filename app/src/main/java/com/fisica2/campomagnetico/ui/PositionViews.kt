package com.fisica2.campomagnetico.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fisica2.campomagnetico.Attitude
import com.fisica2.campomagnetico.AttitudeMath
import com.fisica2.campomagnetico.AttitudeTarget
import com.fisica2.campomagnetico.GuideCheck
import com.fisica2.campomagnetico.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal val OK_GREEN = Color(0xFF2E7D32)
internal val WRONG_ORANGE = Color(0xFFE65100)
internal val NORTH_RED = Color(0xFFD32F2F)
internal val MAG_GRAY = Color(0xFF757575)
private val TOP_ACCENT = Color(0xFFF57C00)
private val SCREEN_BLUE = Color(0xFF64B5F6)
private val BODY_DARK = Color(0xFF37474F)
private val BACK_GRAY = Color(0xFF546E7A)

private val OK_CHECK = GuideCheck(headingOk = true, pitchOk = true, rollOk = true, hints = emptyList())

/**
 * Todo lo que ayuda a poner el celular en una posición:
 *  1. "Así debe quedar el celular": dibujos vistos desde arriba y, si hace falta, de lado o de frente.
 *  2. "Así debe verse la brújula" (posición correcta) junto a "Así está ahora" (en vivo).
 *  3. Indicaciones en palabras y tabla de ángulos.
 * [attitude] y [check] son null mientras no llegan datos de los sensores.
 */
@Composable
fun PositionPanel(attitude: Attitude?, check: GuideCheck?, target: AttitudeTarget, declinationDeg: Double) {
    Section("Así debe quedar el celular") {
        PositionDiagrams(target, declinationDeg)
    }
    Section("La brújula de la app") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Así debe verse", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Compass(target.asAttitude(), target, OK_CHECK, declinationDeg, 148.dp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Así está ahora", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (attitude != null && check != null) {
                    Compass(attitude, target, check, declinationDeg, 148.dp)
                } else {
                    Text("Esperando sensores…", Modifier.width(148.dp).padding(top = 40.dp), textAlign = TextAlign.Center)
                }
            }
        }
        Note("Gira el celular hasta que la brújula de la derecha quede igual a la de la izquierda: el rombo ◆ " +
            "debe quedar justo arriba, en el triángulo ▲ (la parte de arriba del celular).")
        Note("N rojo = norte geográfico · aguja gris = norte magnético (hacia donde apunta una brújula normal).")
        if (target.headingDeg.isNaN().not() && (abs(target.pitchDeg.takeIf { !it.isNaN() } ?: 0.0) > 60.0)) {
            Note("Con el celular vertical, la dirección se toma de hacia dónde mira la pantalla.")
        }
    }
    if (attitude != null && check != null) {
        StatusBanner(check)
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { AngleRows(attitude, target, check) }
    }
}

/** Dibujos de cómo debe quedar el celular: siempre desde arriba y, si se inclina o se gira, la otra vista. */
@Composable
fun PositionDiagrams(target: AttitudeTarget, declinationDeg: Double) {
    val a = target.asAttitude()
    val pitched = abs(a.pitchDeg) > 1.0
    val rolled = abs(AttitudeMath.normalize180(a.rollDeg)) > 1.0
    val second = when {
        pitched -> PhoneViewKind.SIDE
        rolled -> PhoneViewKind.FRONT
        else -> null
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(PhoneViewKind.TOP.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            PhoneDiagram(a, PhoneViewKind.TOP, declinationDeg, 150.dp)
        }
        if (second != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(second.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                PhoneDiagram(a, second, declinationDeg, 150.dp)
            }
        }
    }
    Note(describe(a))
    Note("Flecha naranja = parte de arriba del celular · azul = pantalla · gris = parte de atrás · " +
        "⊙ la punta sale hacia ti · ⊗ la punta se aleja de ti.")
    Note("La brujulita de la esquina es una brújula de verdad: su aguja apunta ${abs(declinationDeg).roundToInt()}° " +
        (if (declinationDeg < 0) "a la izquierda (oeste)" else "a la derecha (este)") + " del norte geográfico.")
}

/** Descripción en palabras de la posición. */
private fun describe(a: Attitude): String {
    val dir = when (AttitudeMath.normalize180(a.headingDeg).roundToInt()) {
        0 -> "al norte"
        90 -> "al este"
        180, -180 -> "al sur"
        -90 -> "al oeste"
        else -> "a ${a.headingDeg.roundToInt()}° del norte"
    }
    val roll = AttitudeMath.normalize180(a.rollDeg)
    return when {
        a.pitchDeg > 60 -> "Celular vertical, con la punta hacia abajo y la pantalla mirando $dir."
        a.pitchDeg > 1 -> "Parte de arriba hacia el norte, con la punta bajada ${a.pitchDeg.roundToInt()}° hacia la mesa."
        abs(roll) > 170 -> "Celular boca abajo, con la parte de arriba $dir."
        roll > 1 -> "Celular de lado (borde derecho sobre la mesa), parte de arriba $dir y pantalla mirando al este."
        roll < -1 -> "Celular de lado (borde izquierdo sobre la mesa), parte de arriba $dir."
        else -> "Celular plano sobre la mesa, pantalla hacia arriba y parte de arriba $dir."
    }
}

enum class PhoneViewKind(val title: String) {
    TOP("Visto desde arriba"),
    SIDE("Visto de lado"),
    FRONT("Visto de frente"),
}

/** Proyección (Este, Norte, Arriba) → plano del dibujo: (derecha, arriba, hacia quien mira). */
private fun project(kind: PhoneViewKind, v: Vec3): Triple<Double, Double, Double> = when (kind) {
    PhoneViewKind.TOP -> Triple(v.x, v.y, v.z)    // desde arriba: este a la derecha, norte arriba
    PhoneViewKind.SIDE -> Triple(v.y, v.z, v.x)   // desde el este mirando al oeste: norte a la derecha
    PhoneViewKind.FRONT -> Triple(v.x, v.z, -v.y) // desde el sur mirando al norte: este a la derecha
}

/**
 * Dibujo del celular en la orientación [a]. El celular se modela como una caja (ancho 0.5, largo 1,
 * grosor 0.09) cuyos ejes salen de AttitudeMath.phoneAxes, y se proyecta según la vista.
 */
@Composable
fun PhoneDiagram(a: Attitude, kind: PhoneViewKind, declinationDeg: Double, size: Dp) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val tableColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.size(size)) {
        val (ax, ay, az) = AttitudeMath.phoneAxes(a)
        val w = 0.5; val l = 1.0; val t = 0.09
        val scale = this.size.minDimension * 0.52f
        val c = Offset(this.size.width / 2f, this.size.height / 2f + if (kind == PhoneViewKind.TOP) 0f else -8.dp.toPx())

        fun toScreen(v: Vec3): Offset {
            val (px, py, _) = project(kind, v)
            return Offset(c.x + (px * scale).toFloat(), c.y - (py * scale).toFloat())
        }
        fun point(sx: Double, sy: Double, sz: Double) = ax * (sx * w / 2) + ay * (sy * l / 2) + az * (sz * t / 2)

        // Fondo: la mesa vista desde arriba o la línea de la mesa vista de lado.
        val corners = buildList {
            for (sx in listOf(-1.0, 1.0)) for (sy in listOf(-1.0, 1.0)) for (sz in listOf(-1.0, 1.0)) add(point(sx, sy, sz))
        }
        if (kind == PhoneViewKind.TOP) {
            val m = 16.dp.toPx()
            drawRoundRect(tableColor, topLeft = Offset(m, m), size = Size(this.size.width - 2 * m, this.size.height - 2 * m),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            label(measurer, "N", Offset(c.x, 8.dp.toPx()), NORTH_RED)
            label(measurer, "S", Offset(c.x, this.size.height - 8.dp.toPx()), ink)
            label(measurer, "E", Offset(this.size.width - 7.dp.toPx(), c.y), ink)
            label(measurer, "O", Offset(7.dp.toPx(), c.y), ink)
            miniCompass(Offset(this.size.width - 34.dp.toPx(), 34.dp.toPx()), 12.dp.toPx(), declinationDeg, ink)
        } else {
            val lowest = corners.maxOf { toScreen(it).y }
            val y = lowest + 2.dp.toPx()
            drawRect(tableColor, topLeft = Offset(4.dp.toPx(), y), size = Size(this.size.width - 8.dp.toPx(), 10.dp.toPx()))
            drawLine(ink, Offset(4.dp.toPx(), y), Offset(this.size.width - 4.dp.toPx(), y), strokeWidth = 2.dp.toPx())
            val (left, right) = if (kind == PhoneViewKind.SIDE) "S" to "N" else "O" to "E"
            label(measurer, "← $left", Offset(20.dp.toPx(), y + 22.dp.toPx()), ink)
            label(measurer, "$right →", Offset(this.size.width - 20.dp.toPx(), y + 22.dp.toPx()), if (right == "N") NORTH_RED else ink)
        }

        // Silueta del celular.
        val hull = convexHull(corners.map { toScreen(it) })
        drawPath(polygon(hull), BODY_DARK)

        // Cara visible: pantalla (azul) o parte de atrás (gris).
        val facing = project(kind, az).third
        if (abs(facing) > 0.05) {
            val sz = if (facing > 0) 1.0 else -1.0
            val face = listOf(point(-0.88, -0.92, sz), point(0.88, -0.92, sz), point(0.88, 0.92, sz), point(-0.88, 0.92, sz))
                .map { toScreen(it) }
            drawPath(polygon(face), if (facing > 0) SCREEN_BLUE else BACK_GRAY)
            if (facing < 0) drawCircle(BODY_DARK, 3.dp.toPx(), toScreen(point(-0.5, 0.78, sz))) // cámara trasera
        }

        // Parte de arriba del celular: borde naranja y flecha (o ⊙/⊗ si apunta hacia o lejos de quien mira).
        drawLine(TOP_ACCENT, toScreen(point(-1.0, 1.0, 0.0)), toScreen(point(1.0, 1.0, 0.0)), strokeWidth = 4.dp.toPx())
        val (yx, yy, yz) = project(kind, ay)
        val start = toScreen(Vec3(0.0, 0.0, 0.0))
        if (hypot(yx, yy) > 0.35) {
            val tip = toScreen(ay * 0.85)
            arrow(start, tip, TOP_ACCENT, 4.dp.toPx())
        } else {
            val r = 9.dp.toPx()
            drawCircle(Color.White, r, start)
            drawCircle(TOP_ACCENT, r, start, style = Stroke(3.dp.toPx()))
            if (yz > 0) {
                drawCircle(TOP_ACCENT, 3.dp.toPx(), start) // ⊙: sale hacia quien mira
            } else {
                val d = r * 0.6f // ⊗: se aleja
                drawLine(TOP_ACCENT, Offset(start.x - d, start.y - d), Offset(start.x + d, start.y + d), strokeWidth = 3.dp.toPx())
                drawLine(TOP_ACCENT, Offset(start.x - d, start.y + d), Offset(start.x + d, start.y - d), strokeWidth = 3.dp.toPx())
            }
        }
    }
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, center: Offset, color: Color) {
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

/** Brújula de verdad en miniatura: la aguja roja apunta al norte magnético (declinación respecto al norte de arriba). */
private fun DrawScope.miniCompass(c: Offset, r: Float, declinationDeg: Double, ink: Color) {
    drawCircle(Color.White, r, c)
    drawCircle(ink, r, c, style = Stroke(1.5.dp.toPx()))
    drawLine(MAG_GRAY, c, polar(c, r * 0.9f, declinationDeg + 180.0), strokeWidth = 3.dp.toPx())
    drawLine(NORTH_RED, c, polar(c, r * 0.9f, declinationDeg), strokeWidth = 3.dp.toPx())
    drawLine(
        ink.copy(alpha = 0.5f), c, polar(c, r * 1.6f, 0.0), strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}

private fun DrawScope.arrow(from: Offset, to: Offset, color: Color, width: Float) {
    drawLine(color, from, to, strokeWidth = width)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = max(1f, hypot(dx, dy))
    val ux = dx / len
    val uy = dy / len
    val h = 10.dp.toPx()
    val head = Path().apply {
        moveTo(to.x + ux * 4.dp.toPx(), to.y + uy * 4.dp.toPx())
        lineTo(to.x - ux * h - uy * h * 0.6f, to.y - uy * h + ux * h * 0.6f)
        lineTo(to.x - ux * h + uy * h * 0.6f, to.y - uy * h - ux * h * 0.6f)
        close()
    }
    drawPath(head, color)
}

private fun polygon(points: List<Offset>) = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (p in points.drop(1)) lineTo(p.x, p.y)
    close()
}

/** Envolvente convexa (cadena monótona de Andrew) para la silueta de la caja proyectada. */
private fun convexHull(points: List<Offset>): List<Offset> {
    val pts = points.distinct().sortedWith(compareBy<Offset>({ it.x }, { it.y }))
    if (pts.size <= 2) return pts
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = mutableListOf<Offset>()
    for (p in pts) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) lower.removeAt(lower.size - 1)
        lower += p
    }
    val upper = mutableListOf<Offset>()
    for (p in pts.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) upper.removeAt(upper.size - 1)
        upper += p
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

/** Tabla Bx, By, Bz y |B| de ahora frente a los esperados en la posición elegida. */
@Composable
internal fun ReadingVsExpected(vm: MainViewModel, expected: Vec3?) {
    val r = vm.live
    if (r == null || expected == null) return
    Section("Lectura del celular") {
        TableRow(listOf("", "Ahora", "Esperado"), header = true, firstWeight = 0.6f)
        TableRow(listOf("Bx", ut(r.b.x), ut(expected.x)), firstWeight = 0.6f)
        TableRow(listOf("By", ut(r.b.y), ut(expected.y)), firstWeight = 0.6f)
        TableRow(listOf("Bz", ut(r.b.z), ut(expected.z)), firstWeight = 0.6f)
        TableRow(listOf("|B|", ut(r.b.norm), ut(expected.norm)), firstWeight = 0.6f)
        Note("Los valores esperados son la proyección del campo teórico sobre los ejes del celular en esa posición.")
    }
}

// ---------------------------------------------------------------- Brújula de la app, estado y ángulos

@Composable
internal fun StatusBanner(check: GuideCheck) {
    val color = if (check.ok) OK_GREEN else WRONG_ORANGE
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = Color.White),
    ) {
        if (check.ok) {
            Text(
                "✔ Posición correcta: toca Capturar",
                Modifier.padding(12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Column(Modifier.padding(12.dp)) {
                check.hints.forEach { hint ->
                    Text("→ $hint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun AngleRows(a: Attitude, t: AttitudeTarget, check: GuideCheck) {
    Column(Modifier.padding(12.dp)) {
        TableRow(listOf("", "Ahora", "Objetivo", ""), header = true, firstWeight = 1.4f)
        TableRow(
            listOf("Dirección de la punta", headingText(a.headingDeg), targetText(t.headingDeg, ::headingText), mark(t.headingDeg, check.headingOk)),
            firstWeight = 1.4f,
        )
        TableRow(
            listOf("Inclinación de la punta", deg(a.pitchDeg), targetText(t.pitchDeg, ::deg), mark(t.pitchDeg, check.pitchOk)),
            firstWeight = 1.4f,
        )
        TableRow(
            listOf("Giro sobre el eje largo", deg(a.rollDeg), targetText(t.rollDeg, ::deg), mark(t.rollDeg, check.rollOk)),
            firstWeight = 1.4f,
        )
        Note("Dirección: 0° = norte geográfico, 90° = este, ±180° = sur, −90° = oeste. Inclinación positiva = punta hacia abajo. " +
            "Giro: 0° = pantalla arriba, 90° = de lado (borde derecho abajo), 180° = boca abajo.")
    }
}

private fun targetText(v: Double, f: (Double) -> String) = if (v.isNaN()) "no importa" else f(v)
private fun mark(v: Double, ok: Boolean) = if (v.isNaN()) "" else if (ok) "✔" else "✗"

internal fun headingText(h: Double): String {
    val name = when (AttitudeMath.normalize180(h)) {
        in -22.5..22.5 -> "N"
        in 22.5..67.5 -> "NE"
        in 67.5..112.5 -> "E"
        in 112.5..157.5 -> "SE"
        in -67.5..-22.5 -> "NO"
        in -112.5..-67.5 -> "O"
        in -157.5..-112.5 -> "SO"
        else -> "S"
    }
    return "${deg(h)} ($name)"
}

/**
 * Brújula dibujada respecto al celular: el triángulo de arriba es la parte superior del teléfono.
 * Las letras giran para mostrar dónde están los puntos cardinales geográficos; la aguja gris marca el
 * norte magnético (donde apunta una brújula normal) y el rombo, hacia dónde debe apuntar la punta.
 */
@Composable
internal fun Compass(a: Attitude, target: AttitudeTarget, check: GuideCheck, declinationDeg: Double, size: Dp = 240.dp) {
    val measurer = rememberTextMeasurer()
    val dial = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outline
    val ringColor = if (check.headingOk) OK_GREEN else WRONG_ORANGE
    val letterSize = if (size < 200.dp) 13.sp else 16.sp
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val margin = this.size.minDimension * 0.1f
        val radius = this.size.minDimension / 2f - margin

        drawCircle(ringColor, radius, c, style = Stroke(width = 4.dp.toPx()))
        // Marcas cada 30°, ancladas al norte geográfico.
        for (k in 0 until 12) {
            val ang = k * 30.0 - a.headingDeg
            val outer = polar(c, radius, ang)
            val inner = polar(c, radius * (if (k % 3 == 0) 0.86f else 0.92f), ang)
            drawLine(faint, inner, outer, strokeWidth = 2.dp.toPx())
        }
        // Letras de los puntos cardinales geográficos.
        for ((label, bearing) in listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "O" to 270.0)) {
            val p = polar(c, radius + margin * 0.55f, bearing - a.headingDeg)
            val color = if (label == "N") NORTH_RED else dial
            val layout = measurer.measure(label, TextStyle(color = color, fontSize = letterSize, fontWeight = FontWeight.Bold))
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        // Aguja del norte geográfico (roja) y del norte magnético (gris).
        needle(c, radius * 0.62f, -a.headingDeg, NORTH_RED, radius * 0.06f)
        needle(c, radius * 0.45f, declinationDeg - a.headingDeg, MAG_GRAY, radius * 0.035f)
        // Hacia dónde debe apuntar la punta.
        if (!target.headingDeg.isNaN()) {
            val p = polar(c, radius, target.headingDeg - a.headingDeg)
            val s = radius * 0.1f
            val diamond = Path().apply {
                moveTo(p.x, p.y - s); lineTo(p.x + s, p.y); lineTo(p.x, p.y + s); lineTo(p.x - s, p.y); close()
            }
            drawPath(diamond, ringColor)
        }
        // Parte de arriba del celular (fija, dentro del anillo).
        val tip = Path().apply {
            val apex = c.y - radius + radius * 0.06f
            val tall = radius * 0.17f
            moveTo(c.x, apex)
            lineTo(c.x - tall * 0.55f, apex + tall)
            lineTo(c.x + tall * 0.55f, apex + tall)
            close()
        }
        drawPath(tip, dial)
        drawCircle(dial, radius * 0.045f, c)
    }
}

private fun DrawScope.needle(c: Offset, length: Float, angleDeg: Double, color: Color, width: Float) {
    drawLine(color, c, polar(c, length, angleDeg), strokeWidth = width)
    drawCircle(color, width, polar(c, length, angleDeg))
}

/** Punto a [r] del centro en el ángulo [deg] medido en sentido horario desde arriba. */
internal fun polar(c: Offset, r: Float, deg: Double): Offset {
    val a = Math.toRadians(deg)
    return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
}
