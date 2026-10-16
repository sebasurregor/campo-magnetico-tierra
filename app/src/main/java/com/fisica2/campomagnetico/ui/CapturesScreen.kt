package com.fisica2.campomagnetico.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fisica2.campomagnetico.Capture
import com.fisica2.campomagnetico.CsvExporter
import com.fisica2.campomagnetico.MagMath
import com.fisica2.campomagnetico.Orientations
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Composable
fun CapturesScreen(vm: MainViewModel) {
    var orientation by rememberSaveable { mutableStateOf(Orientations.FLAT_NORTH) }
    var note by rememberSaveable { mutableStateOf("") }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // RF6
    Section("Captura promediada (3 s)") {
        Note("Elige la orientación, deja el teléfono quieto y toca Capturar.")
        Orientations.USER_CHOICES.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = orientation == option, onClick = { orientation = option }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = orientation == option, onClick = { orientation = option })
                Text(option)
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(80) },
            label = { Text("Nota opcional (ej. ángulo 90°)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        CaptureProgress(vm)
        Button(
            onClick = { vm.startCapture(orientation, note) },
            enabled = !vm.capturing,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { Text("Capturar") }
    }

    FlipWizard(vm)

    // RF10
    Section("Capturas guardadas (${vm.captures.size})") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            ) { Text("Exportar CSV") }
            OutlinedButton(
                onClick = { confirmDeleteAll = true },
                enabled = vm.captures.isNotEmpty(),
            ) { Text("Borrar todas") }
        }
        Note(
            if (vm.roundCsv) "Las coordenadas del CSV se redondean a 2 decimales (Configuración)."
            else "Las coordenadas del CSV NO se redondean (Configuración).",
        )
    }

    vm.captures.forEach { c -> CaptureCard(c, onDelete = { vm.deleteCapture(c) }) }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Borrar todas las capturas") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllCaptures(); confirmDeleteAll = false }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CaptureProgress(vm: MainViewModel) {
    if (vm.capturing) {
        Note("Capturando… ${vm.captureCount} muestras. No muevas el teléfono.")
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
    }
}

/** RF8: asistente de dos capturas con giro de 180° sobre la mesa. */
@Composable
private fun FlipWizard(vm: MainViewModel) {
    Section("Asistente de desfase (giro de 180°)") {
        Note("1. Pon el teléfono plano sobre la mesa, pantalla arriba, y toca \"Capturar 0°\".")
        Note("2. Gíralo 180° sobre la mesa (sin levantarlo ni voltearlo) y toca \"Capturar 180°\".")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = { vm.startFlip0() }, enabled = !vm.capturing) {
                Text(if (vm.flip0 == null) "Capturar 0°" else "Repetir 0°")
            }
            Button(onClick = { vm.startFlip180() }, enabled = !vm.capturing && vm.flip0 != null) {
                Text("Capturar 180°")
            }
        }
        CaptureProgress(vm)
        val c0 = vm.flip0
        val c180 = vm.flip180
        if (c0 != null) Note("0° capturado: Bx = ${ut(c0.bx.mean)}, By = ${ut(c0.by.mean)}")
        if (c0 != null && c180 != null) {
            Note("180° capturado: Bx = ${ut(c180.bx.mean)}, By = ${ut(c180.by.mean)}")
            val res = MagMath.flip180(c0.deviceMean, c180.deviceMean)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Con la lectura calibrada:")
            TableRow(listOf("", "B real", "Desfase"), header = true, firstWeight = 0.5f)
            TableRow(listOf("x", ut(res.realX), ut(res.offsetX)), firstWeight = 0.5f)
            TableRow(listOf("y", ut(res.realY), ut(res.offsetY)), firstWeight = 0.5f)
            val raw0 = c0.raw
            val raw180 = c180.raw
            if (raw0 != null && raw180 != null) {
                val rr = MagMath.flip180(raw0, raw180)
                Text("Con la lectura sin calibrar:", Modifier.padding(top = 6.dp))
                TableRow(listOf("", "B real", "Desfase", "Bias Android"), header = true, firstWeight = 0.5f)
                TableRow(listOf("x", ut(rr.realX), ut(rr.offsetX), ut(c0.bias?.x)), firstWeight = 0.5f)
                TableRow(listOf("y", ut(rr.realY), ut(rr.offsetY), ut(c0.bias?.y)), firstWeight = 0.5f)
            }
            Note("B_real = (B(0°) − B(180°)) / 2   ·   desfase = (B(0°) + B(180°)) / 2   (para x e y)")
            TextButton(onClick = { vm.resetFlip() }) { Text("Reiniciar asistente") }
        }
        Note("Este giro NO corrige z: al girar sobre la mesa, la componente z del campo no cambia de signo, " +
            "así que no se puede separar del desfase en z.")
    }
}

@Composable
private fun CaptureCard(c: Capture, onDelete: () -> Unit) {
    val time = TIME_FORMAT.format(Instant.ofEpochMilli(c.timeMillis).atZone(ZoneId.systemDefault()))
    val title = c.orientation + if (c.note.isNotBlank()) " — ${c.note}" else ""
    Section(title) {
        Note("$time · ${c.samples} muestras · lat ${fmt1(c.lat)}°, lon ${fmt1(c.lon)}°, alt ${fmt1(c.altM)} m")
        if (MagMath.isOutOfRange(c.deviceMean.norm) || MagMath.isLowAccuracy(c.minAccuracy)) {
            WarningBox(INTERFERENCE_TEXT)
        }
        Text("Marco del teléfono (promedio ± desviación):")
        ValueRow("Bx", pm(c.bx, "μT"))
        ValueRow("By", pm(c.by, "μT"))
        ValueRow("Bz", pm(c.bz, "μT"))
        c.earth?.let { e ->
            Text("Marco terrestre:", Modifier.padding(top = 6.dp))
            ValueRow("X", pm(e.x, "μT"))
            ValueRow("Y", pm(e.y, "μT"))
            ValueRow("Z", pm(e.z, "μT"))
            ValueRow("H", pm(e.h, "μT"))
            ValueRow("F", pm(e.f, "μT"))
            ValueRow("I", pm(e.i, "°"))
            ValueRow("B∥", pm(e.parallel, "μT"))
        } ?: Note("Sin datos de gravedad: no hay valores en el marco terrestre.")

        // RF7
        val rows = c.comparison()
        if (rows.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Medido vs. teórico (${c.theorySource})")
            TableRow(listOf("", "Medido", "Teórico", "|Dif.|", "Error"), header = true)
            rows.forEach { row ->
                TableRow(
                    listOf(
                        row.label,
                        withUnit(row.measured, row.unit),
                        withUnit(row.theory, row.unit),
                        withUnit(row.absDiff, row.unit),
                        pct(row.percent),
                    ),
                )
            }
        }
        TextButton(onClick = onDelete) { Text("Borrar esta captura") }
    }
}
