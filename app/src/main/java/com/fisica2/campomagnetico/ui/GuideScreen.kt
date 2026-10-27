package com.fisica2.campomagnetico.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fisica2.campomagnetico.Attitude
import com.fisica2.campomagnetico.AttitudeMath
import com.fisica2.campomagnetico.AttitudeTarget
import com.fisica2.campomagnetico.CsvExporter
import com.fisica2.campomagnetico.GuideCheck
import com.fisica2.campomagnetico.MagMath
import kotlin.math.cos
import kotlin.math.sin

private val OK_GREEN = Color(0xFF2E7D32)
private val WRONG_ORANGE = Color(0xFFE65100)
private val NORTH_RED = Color(0xFFD32F2F)
private val MAG_GRAY = Color(0xFF757575)

/** Pestaña "Guía": dice paso a paso cómo poner el celular, con brújula e indicaciones, y captura. */
@Composable
fun GuideScreen(vm: MainViewModel) {
    if (!vm.hasMagnetometer) {
        WarningBox("Este teléfono no tiene magnetómetro. La app no puede medir el campo.")
        return
    }
    val steps = vm.guideSteps
    val index = vm.guideIndex
    val r = vm.live

    Section("Cómo funciona") {
        Note("1. Lee la instrucción del paso.")
        Note("2. Mueve el celular hasta que todo se ponga verde (la brújula y las indicaciones te dicen hacia dónde).")
        Note("3. Toca Capturar y no toques el celular durante 3 segundos. La app pasa sola al siguiente paso.")
    }

    if (r != null) {
        if (MagMath.isOutOfRange(r.b.norm) || MagMath.isLowAccuracy(r.accuracy)) WarningBox(INTERFERENCE_TEXT)
        val e = r.earth
        if (e != null && MagMath.isVerticalSignWrong(e.z, vm.theory.z)) WarningBox(FIELD_UP_TEXT)
    }

    if (index >= steps.size) {
        FinishedCard(vm)
        vm.lastGuideCapture?.let { CaptureCard(it, onDelete = { vm.deleteCapture(it) }) }
        return
    }

    val step = steps[index]
    val attitude = r?.attitude
    val check = attitude?.let { AttitudeMath.check(it, step.target) }

    // Progreso
    Text(
        "Paso ${index + 1} de ${steps.size}",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 6.dp),
    )
    LinearProgressIndicator(
        progress = { vm.guideDone.size.toFloat() / steps.size },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )

    Section(step.title) {
        Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
    }

    // Brújula + estado
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (attitude == null || check == null) {
            Note("Esperando datos del magnetómetro y del sensor de gravedad…", )
            return@Card
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            Compass(attitude, step.target, check, vm.theory.declinationDeg)
        }
        StatusBanner(check)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Note("▲ = parte de arriba del celular · N rojo = norte geográfico · N gris = norte magnético (brújula) · ◆ = hacia donde debe apuntar")
        }
        AngleRows(attitude, step.target, check)
    }

    // Lectura ahora vs esperada
    val expected = vm.expectedFor(index)
    if (r != null && expected != null) {
        Section("Lectura del celular") {
            TableRow(listOf("", "Ahora", "Esperado"), header = true, firstWeight = 0.6f)
            TableRow(listOf("Bx", ut(r.b.x), ut(expected.x)), firstWeight = 0.6f)
            TableRow(listOf("By", ut(r.b.y), ut(expected.y)), firstWeight = 0.6f)
            TableRow(listOf("Bz", ut(r.b.z), ut(expected.z)), firstWeight = 0.6f)
            TableRow(listOf("|B|", ut(r.b.norm), ut(expected.norm)), firstWeight = 0.6f)
            Note("Los valores esperados son la proyección del campo teórico sobre los ejes del celular en la posición del paso.")
        }
    }

    // Botones
    when {
        vm.capturing -> {
            Note("Capturando… ${vm.captureCount} muestras. No toques el celular.")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        vm.countdown > 0 -> {
            Text(
                "La captura empieza en ${vm.countdown} s. Pon el celular en posición y suéltalo.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            OutlinedButton(onClick = { vm.cancelCountdown() }) { Text("Cancelar") }
        }
        else -> {
            val ok = check?.ok == true
            Button(
                onClick = { vm.captureGuideStep() },
                colors = if (ok) ButtonDefaults.buttonColors(containerColor = OK_GREEN) else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { Text(if (ok) "Capturar (3 s)" else "Capturar aunque no esté en verde") }
            OutlinedButton(
                onClick = { vm.captureGuideStep(delaySeconds = 5) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Capturar en 5 s (para soltar el celular)") }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { vm.goToGuideStep(index - 1) }, enabled = index > 0 && !vm.capturing) { Text("◀ Anterior") }
        TextButton(onClick = { vm.restartGuide() }, enabled = !vm.capturing) { Text("Reiniciar") }
        TextButton(onClick = { vm.goToGuideStep(index + 1) }, enabled = !vm.capturing) { Text("Saltar ▶") }
    }

    vm.lastGuideCapture?.let { last ->
        Text("Última captura", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        CaptureCard(last, onDelete = { vm.deleteCapture(last) })
    }

    Section("Sobre esta brújula") {
        Note("La brújula usa el mismo magnetómetro del celular: toma el norte magnético del campo medido y lo gira " +
            "la declinación teórica D = ${deg(vm.theory.declinationDeg)} para marcar el norte geográfico. Sirve para " +
            "colocar el celular en cada posición, pero no mide D: para medir la declinación, compara con una línea " +
            "marcada con Google Maps (experimento 1).")
        Note("Si hay un metal o un imán cerca, la brújula también se equivoca. Revisa que |B| esté cerca de 31 μT.")
    }
}

@Composable
private fun StatusBanner(check: GuideCheck) {
    val color = if (check.ok) OK_GREEN else WRONG_ORANGE
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
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
            androidx.compose.foundation.layout.Column(Modifier.padding(12.dp)) {
                check.hints.forEach { hint ->
                    Text("→ $hint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AngleRows(a: Attitude, t: AttitudeTarget, check: GuideCheck) {
    androidx.compose.foundation.layout.Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
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

private fun headingText(h: Double): String {
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
private fun Compass(a: Attitude, target: AttitudeTarget, check: GuideCheck, declinationDeg: Double) {
    val measurer = rememberTextMeasurer()
    val dial = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outline
    val ringColor = if (check.headingOk) OK_GREEN else WRONG_ORANGE
    Canvas(Modifier.size(240.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 22.dp.toPx()

        drawCircle(ringColor, radius, c, style = Stroke(width = 4.dp.toPx()))
        // Marcas cada 30°, ancladas al norte geográfico.
        for (k in 0 until 12) {
            val ang = k * 30.0 - a.headingDeg
            val outer = polar(c, radius, ang)
            val inner = polar(c, radius - (if (k % 3 == 0) 14.dp.toPx() else 8.dp.toPx()), ang)
            drawLine(faint, inner, outer, strokeWidth = 2.dp.toPx())
        }
        // Letras de los puntos cardinales geográficos.
        val letters = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "O" to 270.0)
        for ((label, bearing) in letters) {
            val p = polar(c, radius + 12.dp.toPx(), bearing - a.headingDeg)
            val color = if (label == "N") NORTH_RED else dial
            val layout = measurer.measure(label, TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        // Aguja del norte geográfico (roja) y del norte magnético (gris).
        drawNeedle(c, radius - 32.dp.toPx(), -a.headingDeg, NORTH_RED, 6.dp.toPx())
        drawNeedle(c, radius - 44.dp.toPx(), declinationDeg - a.headingDeg, MAG_GRAY, 3.dp.toPx())
        // Hacia dónde debe apuntar la punta.
        if (!target.headingDeg.isNaN()) {
            val p = polar(c, radius, target.headingDeg - a.headingDeg)
            val s = 9.dp.toPx()
            val diamond = Path().apply {
                moveTo(p.x, p.y - s); lineTo(p.x + s, p.y); lineTo(p.x, p.y + s); lineTo(p.x - s, p.y); close()
            }
            drawPath(diamond, ringColor)
        }
        // Parte de arriba del celular (fija, dentro del anillo).
        val tip = Path().apply {
            val apex = c.y - radius + 6.dp.toPx()
            moveTo(c.x, apex)
            lineTo(c.x - 10.dp.toPx(), apex + 18.dp.toPx())
            lineTo(c.x + 10.dp.toPx(), apex + 18.dp.toPx())
            close()
        }
        drawPath(tip, dial)
        drawCircle(dial, 5.dp.toPx(), c)
    }
}

private fun DrawScope.drawNeedle(c: Offset, length: Float, angleDeg: Double, color: Color, width: Float) {
    drawLine(color, c, polar(c, length, angleDeg), strokeWidth = width)
    drawCircle(color, width, polar(c, length, angleDeg))
}

/** Punto a [r] del centro en el ángulo [deg] medido en sentido horario desde arriba. */
private fun polar(c: Offset, r: Float, deg: Double): Offset {
    val a = Math.toRadians(deg)
    return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
}

@Composable
private fun FinishedCard(vm: MainViewModel) {
    val context = LocalContext.current
    Section("¡Guía terminada!") {
        Text("Capturaste ${vm.guideDone.size} de ${vm.guideSteps.size} pasos. Ahora exporta el CSV y revisa la " +
            "pestaña Capturas: en cada captura está la tabla medido vs. esperado.")
        Button(
            onClick = {
                try {
                    CsvExporter.share(context, vm.captures.toList(), vm.roundCsv)
                } catch (e: ActivityNotFoundException) {
                    vm.message = "No hay ninguna app para compartir el archivo."
                } catch (e: Exception) {
                    vm.message = "No se pudo exportar: ${e.message}"
                }
            },
            enabled = vm.captures.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { Text("Exportar CSV") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.goToGuideStep(vm.guideSteps.size - 1) }) { Text("◀ Último paso") }
            OutlinedButton(onClick = { vm.restartGuide() }) { Text("Empezar de nuevo") }
        }
    }
}
