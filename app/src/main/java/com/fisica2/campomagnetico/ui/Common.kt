package com.fisica2.campomagnetico.ui

import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fisica2.campomagnetico.Stat
import java.util.Locale

// ---------------------------------------------------------------- Formato (1 decimal, unidades visibles)

fun fmt1(v: Double?): String =
    if (v == null || v.isNaN() || v.isInfinite()) "—" else String.format(Locale.US, "%.1f", v)

fun ut(v: Double?): String = "${fmt1(v)} μT"
fun deg(v: Double?): String = "${fmt1(v)}°"
fun pct(v: Double?): String = "${fmt1(v)} %"
fun withUnit(v: Double?, unit: String): String = if (unit == "°") deg(v) else "${fmt1(v)} $unit"
fun pm(s: Stat, unit: String): String =
    if (unit == "°") "${fmt1(s.mean)}° ± ${fmt1(s.sd)}°" else "${fmt1(s.mean)} ± ${fmt1(s.sd)} $unit"

fun accuracyLabel(a: Int): String = when (a) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Alta (SENSOR_STATUS_ACCURACY_HIGH)"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Media (SENSOR_STATUS_ACCURACY_MEDIUM)"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Baja (SENSOR_STATUS_ACCURACY_LOW)"
    SensorManager.SENSOR_STATUS_UNRELIABLE -> "No confiable (SENSOR_STATUS_UNRELIABLE)"
    SensorManager.SENSOR_STATUS_NO_CONTACT -> "Sin contacto (SENSOR_STATUS_NO_CONTACT)"
    else -> "Desconocida ($a)"
}

/** Acepta coma o punto decimal. */
fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

const val INTERFERENCE_TEXT =
    "Posible interferencia: aleja el celular de metales y aparatos; mueve el teléfono en forma de 8 para recalibrar."

// ---------------------------------------------------------------- Componentes simples

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
fun WarningBox(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text("⚠ $text", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Fila de tabla con columnas de igual peso salvo la primera. */
@Composable
fun TableRow(cells: List<String>, header: Boolean = false, firstWeight: Float = 1.3f) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        cells.forEachIndexed { i, c ->
            TableCell(c, if (i == 0) firstWeight else 1f, header)
        }
    }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, header: Boolean) {
    Text(
        text,
        Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = value.isNotBlank() && parseNumber(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.padding(vertical = 2.dp),
    )
}
