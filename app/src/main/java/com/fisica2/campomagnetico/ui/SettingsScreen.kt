package com.fisica2.campomagnetico.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fisica2.campomagnetico.ReferenceValues
import com.fisica2.campomagnetico.TheorySource
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel) {
    LocationSection(vm)
    TheorySourceSection(vm)
    PlanSection(vm)

    Section("Exportación CSV") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Redondear coordenadas en el CSV a 2 decimales", Modifier.weight(1f))
            Switch(checked = vm.roundCsv, onCheckedChange = { vm.updateRoundCsv(it) })
        }
        Note("Con 2 decimales la posición queda con una precisión de ~1 km, suficiente para el campo magnético.")
    }

    Section("Privacidad") {
        Note("La app no tiene permiso de internet: nada sale del teléfono salvo el CSV que tú decidas compartir. " +
            "Solo usa la ubicación aproximada, y solo cuando tocas el botón de ubicación con la app abierta. " +
            "Las capturas se guardan en el almacenamiento privado de la app.")
    }
}

@Composable
private fun LocationSection(vm: MainViewModel) {
    // Los campos se reinician cuando cambia la ubicación guardada.
    var latText by rememberSaveable(vm.lat) { mutableStateOf(coord(vm.lat)) }
    var lonText by rememberSaveable(vm.lon) { mutableStateOf(coord(vm.lon)) }
    var altText by rememberSaveable(vm.altM) { mutableStateOf(fmt1(vm.altM)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onLocationPermissionResult(granted)
    }

    Section("Ubicación") {
        Note("Fuente actual: ${vm.locationSource}")
        NumberField("Latitud (°)", latText, { latText = it }, Modifier.fillMaxWidth())
        NumberField("Longitud (°)", lonText, { lonText = it }, Modifier.fillMaxWidth())
        NumberField("Altitud (m)", altText, { altText = it }, Modifier.fillMaxWidth())
        Button(
            onClick = {
                val la = parseNumber(latText)
                val lo = parseNumber(lonText)
                val al = parseNumber(altText)
                when {
                    la == null || la < -90 || la > 90 -> vm.message = "Latitud inválida (debe estar entre −90 y 90)."
                    lo == null || lo < -180 || lo > 180 -> vm.message = "Longitud inválida (debe estar entre −180 y 180)."
                    al == null || al < -1000 || al > 10000 -> vm.message = "Altitud inválida (en metros)."
                    else -> {
                        vm.setManualLocation(la, lo, al)
                        vm.message = "Ubicación guardada."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text("Guardar coordenadas escritas") }
        Button(
            onClick = {
                if (vm.hasLocationPermission()) vm.fetchLocation()
                else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            enabled = !vm.locationBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.locationBusy) "Buscando ubicación…" else "Usar ubicación aproximada del teléfono") }
        OutlinedButton(onClick = { vm.resetLocation() }, modifier = Modifier.fillMaxWidth()) {
            Text("Restablecer Medellín (6.2442, −75.5812, 1495 m)")
        }
        Note("Solo se pide la ubicación APROXIMADA: el campo magnético casi no cambia en unos kilómetros. " +
            "Si la ubicación no trae altitud, se conserva la del campo (1495 m por defecto).")
    }
}

@Composable
private fun TheorySourceSection(vm: MainViewModel) {
    Section("Valores teóricos para comparar") {
        TheorySource.entries.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = vm.theorySource == s, onClick = { vm.updateTheorySource(s) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = vm.theorySource == s, onClick = { vm.updateTheorySource(s) })
                Text(s.label)
            }
        }
        Note("La fuente elegida se usa en la tabla medido vs. teórico y su declinación D se usa para referir " +
            "X e Y al norte geográfico. El modelo de Android usa la ubicación y la fecha actuales.")
    }
}

@Composable
private fun PlanSection(vm: MainViewModel) {
    val p = vm.plan
    var x by rememberSaveable(p) { mutableStateOf(fmtEdit(p.x)) }
    var y by rememberSaveable(p) { mutableStateOf(fmtEdit(p.y)) }
    var z by rememberSaveable(p) { mutableStateOf(fmtEdit(p.z)) }
    var h by rememberSaveable(p) { mutableStateOf(fmtEdit(p.h)) }
    var f by rememberSaveable(p) { mutableStateOf(fmtEdit(p.f)) }
    var i by rememberSaveable(p) { mutableStateOf(fmtEdit(p.inclinationDeg)) }
    var d by rememberSaveable(p) { mutableStateOf(fmtEdit(p.declinationDeg)) }
    var par by rememberSaveable(p) { mutableStateOf(fmtEdit(p.parallel)) }

    Section("Valores del plan (editables)") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("X (μT)", x, { x = it }, Modifier.weight(1f))
            NumberField("Y (μT)", y, { y = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Z (μT)", z, { z = it }, Modifier.weight(1f))
            NumberField("H (μT)", h, { h = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("F (μT)", f, { f = it }, Modifier.weight(1f))
            NumberField("I (°)", i, { i = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("D (°)", d, { d = it }, Modifier.weight(1f))
            NumberField("B∥ (μT)", par, { par = it }, Modifier.weight(1f))
        }
        Button(
            onClick = {
                val values = listOf(x, y, z, h, f, i, d, par).map { parseNumber(it) }
                if (values.any { it == null }) {
                    vm.message = "Hay un valor del plan que no es un número."
                } else {
                    val v = values.map { it!! }
                    vm.updatePlan(ReferenceValues(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]))
                    vm.message = "Valores del plan guardados."
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text("Guardar valores del plan") }
        OutlinedButton(onClick = { vm.updatePlan(ReferenceValues.PLAN_MEDELLIN) }, modifier = Modifier.fillMaxWidth()) {
            Text("Restablecer valores del plan (WMM 2025, Medellín)")
        }
    }
}

private fun coord(v: Double) = String.format(Locale.US, "%.4f", v)
private fun fmtEdit(v: Double) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
