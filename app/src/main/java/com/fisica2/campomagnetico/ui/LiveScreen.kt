package com.fisica2.campomagnetico.ui

import androidx.compose.runtime.Composable
import com.fisica2.campomagnetico.MagMath
import com.fisica2.campomagnetico.TheorySource

@Composable
fun LiveScreen(vm: MainViewModel) {
    if (!vm.hasMagnetometer) {
        WarningBox("Este teléfono no tiene magnetómetro (TYPE_MAGNETIC_FIELD). La app no puede medir el campo.")
        return
    }
    val r = vm.live
    if (r == null) {
        Note("Esperando datos del magnetómetro…")
        return
    }

    // RF9: alerta de interferencia
    if (MagMath.isOutOfRange(r.b.norm) || MagMath.isLowAccuracy(r.accuracy)) {
        WarningBox(INTERFERENCE_TEXT)
    }

    // RF1
    Section("Lectura en vivo (TYPE_MAGNETIC_FIELD)") {
        ValueRow("Bx", ut(r.b.x))
        ValueRow("By", ut(r.b.y))
        ValueRow("Bz", ut(r.b.z))
        ValueRow("|B|", ut(r.b.norm))
        ValueRow("Precisión", accuracyLabel(r.accuracy))
        Note("Ejes del teléfono: con el teléfono plano y la pantalla hacia arriba, x apunta a la derecha, " +
            "y hacia la parte superior y z sale de la pantalla.")
    }

    // RF2
    Section("Lectura sin calibrar (TYPE_MAGNETIC_FIELD_UNCALIBRATED)") {
        if (!vm.hasUncalibrated) {
            Note("Este teléfono no tiene el sensor TYPE_MAGNETIC_FIELD_UNCALIBRATED.")
        } else if (r.raw == null || r.bias == null) {
            Note("Esperando datos…")
        } else {
            TableRow(listOf("", "Crudo", "Bias (Android)", "Crudo − bias"), header = true)
            TableRow(listOf("x", ut(r.raw.x), ut(r.bias.x), ut(r.raw.x - r.bias.x)))
            TableRow(listOf("y", ut(r.raw.y), ut(r.bias.y), ut(r.raw.y - r.bias.y)))
            TableRow(listOf("z", ut(r.raw.z), ut(r.bias.z), ut(r.raw.z - r.bias.z)))
            Note("El bias es el desfase (hierro duro) que Android estima y resta para dar la lectura calibrada.")
        }
    }

    // RF5
    Section("Medido en el marco terrestre") {
        val e = r.earth
        if (e == null) {
            Note("No hay sensor de gravedad ni acelerómetro (o aún no llegan datos): no se puede pasar " +
                "la lectura al marco terrestre. Gravedad usada: ${vm.gravityLabel}.")
        } else {
            ValueRow("X (norte)", ut(e.x))
            ValueRow("Y (este)", ut(e.y))
            ValueRow("Z (abajo)", ut(e.z))
            ValueRow("H", ut(e.h))
            ValueRow("F", ut(e.f))
            ValueRow("I", deg(e.inclinationDeg))
            ValueRow("B∥", ut(r.parallel))
            ValueRow("I según getInclination", deg(r.androidInclinationDeg))
            Note("Estos valores no dependen de cómo esté orientado el teléfono. Gravedad: ${vm.gravityLabel}.")
            Note("Ojo: el \"norte\" que da el magnetómetro es el norte MAGNÉTICO. Para referir X e Y al norte " +
                "geográfico se giran un ángulo igual a la declinación teórica D = ${deg(r.declinationUsedDeg)} " +
                "(${vm.theorySource.label}). Por eso D no se mide: se toma de la teoría.")
        }
    }

    // RF4
    Section("Valores teóricos") {
        val p = vm.plan
        val m = vm.model
        val planMark = if (vm.theorySource == TheorySource.PLAN) " ✔" else ""
        val modelMark = if (vm.theorySource == TheorySource.MODEL) " ✔" else ""
        TableRow(listOf("", "Plan$planMark", "Android$modelMark"), header = true, firstWeight = 0.6f)
        TableRow(listOf("X", ut(p.x), ut(m.x)), firstWeight = 0.6f)
        TableRow(listOf("Y", ut(p.y), ut(m.y)), firstWeight = 0.6f)
        TableRow(listOf("Z", ut(p.z), ut(m.z)), firstWeight = 0.6f)
        TableRow(listOf("H", ut(p.h), ut(m.h)), firstWeight = 0.6f)
        TableRow(listOf("F", ut(p.f), ut(m.f)), firstWeight = 0.6f)
        TableRow(listOf("I", deg(p.inclinationDeg), deg(m.inclinationDeg)), firstWeight = 0.6f)
        TableRow(listOf("D", deg(p.declinationDeg), deg(m.declinationDeg)), firstWeight = 0.6f)
        TableRow(listOf("B∥", ut(p.parallel), ut(m.parallel)), firstWeight = 0.6f)
        Note("✔ = valores usados para comparar (se cambia en Configuración).")
        Note("Nota: los valores de GeomagneticField dependen de la versión del modelo geomagnético que trae " +
            "Android en este teléfono; por eso se muestran junto a los del plan.")
        Note("Ubicación: ${fmt1Coord(vm.lat)}, ${fmt1Coord(vm.lon)}, ${fmt1(vm.altM)} m — ${vm.locationSource}.")
    }
}

private fun fmt1Coord(v: Double) = String.format(java.util.Locale.US, "%.4f°", v)
