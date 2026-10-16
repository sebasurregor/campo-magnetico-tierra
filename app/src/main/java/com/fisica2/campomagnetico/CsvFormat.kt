package com.fisica2.campomagnetico

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Arma el texto CSV de las capturas (Kotlin puro, sin Android). Separador coma y punto decimal. */
object CsvFormat {
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val COMPARED = listOf("F", "H", "Z", "I", "Bpar")
    private val FLAT = listOf("Bx", "By", "Bz")

    val HEADER: List<String> = listOf(
        "fecha_hora", "orientacion", "nota", "lat", "lon", "alt_m", "muestras", "precision_min",
        "Bx_uT", "By_uT", "Bz_uT", "sd_Bx_uT", "sd_By_uT", "sd_Bz_uT",
        "X_med_uT", "Y_med_uT", "Z_med_uT", "F_med_uT", "H_med_uT", "I_med_deg", "Bpar_med_uT",
        "fuente_teorica", "X_teo_uT", "Y_teo_uT", "Z_teo_uT", "F_teo_uT", "H_teo_uT",
        "I_teo_deg", "D_teo_deg", "Bpar_teo_uT",
    ) + COMPARED.flatMap { k ->
        val unit = if (k == "I") "deg" else "uT"
        listOf("dif_${k}_$unit", "err_${k}_pct")
    } + listOf("Bx_esp_uT", "By_esp_uT", "Bz_esp_uT") + FLAT.flatMap { k ->
        listOf("dif_${k}_uT", "err_${k}_pct")
    } + listOf("Bx_crudo_uT", "By_crudo_uT", "Bz_crudo_uT", "bias_x_uT", "bias_y_uT", "bias_z_uT")

    fun build(captures: List<Capture>, roundCoordinates: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append("\r\n")
        for (c in captures) {
            sb.append(row(c, roundCoordinates, zone).joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    fun row(c: Capture, roundCoordinates: Boolean, zone: ZoneId): List<String> {
        val coord = if (roundCoordinates) "%.2f" else "%.6f"
        val t = c.theory
        val e = c.earth
        val rows = c.comparison().associateBy { it.key }
        val flat = c.orientation == Orientations.FLAT_NORTH
        val expected = MagMath.expectedFlatNorth(t.x, t.y, t.z)

        val values = mutableListOf(
            dateFormat.format(Instant.ofEpochMilli(c.timeMillis).atZone(zone)),
            text(c.orientation),
            text(c.note),
            String.format(Locale.US, coord, c.lat),
            String.format(Locale.US, coord, c.lon),
            num(c.altM, 0),
            c.samples.toString(),
            c.minAccuracy.toString(),
            num(c.bx.mean), num(c.by.mean), num(c.bz.mean),
            num(c.bx.sd), num(c.by.sd), num(c.bz.sd),
            num(e?.x?.mean), num(e?.y?.mean), num(e?.z?.mean), num(e?.f?.mean),
            num(e?.h?.mean), num(e?.i?.mean), num(e?.parallel?.mean),
            c.theorySource,
            num(t.x), num(t.y), num(t.z), num(t.f), num(t.h),
            num(t.inclinationDeg), num(t.declinationDeg), num(t.parallel),
        )
        for (k in COMPARED) {
            values += num(rows[k]?.absDiff)
            values += num(rows[k]?.percent)
        }
        values += if (flat) listOf(num(expected.x), num(expected.y), num(expected.z)) else listOf("", "", "")
        for (k in FLAT) {
            values += num(rows[k]?.absDiff)
            values += num(rows[k]?.percent)
        }
        values += listOf(num(c.raw?.x), num(c.raw?.y), num(c.raw?.z), num(c.bias?.x), num(c.bias?.y), num(c.bias?.z))
        return values
    }

    private fun num(v: Double?, decimals: Int = 3): String =
        if (v == null || v.isNaN() || v.isInfinite()) "" else String.format(Locale.US, "%.${decimals}f", v)

    /** Evita que una hoja de cálculo interprete un texto como fórmula (=, +, -, @). */
    private fun text(s: String): String =
        if (s.isNotEmpty() && s[0] in "=+-@\t\r") "'$s" else s

    private fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
