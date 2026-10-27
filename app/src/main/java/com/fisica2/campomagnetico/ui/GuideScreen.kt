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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    StepList(vm)

    // Cómo debe quedar el celular + brújula objetivo y en vivo
    PositionPanel(attitude, check, step.target, vm.theory.declinationDeg)

    // Lectura ahora vs esperada
    ReadingVsExpected(vm, vm.expectedFor(index))

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

/** Lista desplegable con todos los pasos para saltar directamente a uno (por ejemplo "Plano hacia el norte"). */
@Composable
private fun StepList(vm: MainViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, enabled = !vm.capturing) {
        Text(if (open) "▲ Ocultar la lista de pasos" else "▼ Ver todos los pasos / ir a uno")
    }
    if (open) {
        Card(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.padding(8.dp)) {
                vm.guideSteps.forEachIndexed { i, st ->
                    val done = i in vm.guideDone
                    val current = i == vm.guideIndex
                    TextButton(
                        onClick = { vm.goToGuideStep(i); open = false },
                        enabled = !vm.capturing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${i + 1}. ${st.title}" + (if (done) "  ✔" else "") + (if (current) "  ◀ actual" else ""),
                            Modifier.fillMaxWidth(),
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
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
