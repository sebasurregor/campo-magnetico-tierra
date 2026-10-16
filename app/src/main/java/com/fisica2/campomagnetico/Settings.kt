package com.fisica2.campomagnetico

import android.content.Context
import android.content.SharedPreferences

enum class TheorySource(val label: String) {
    PLAN("Valores del plan"),
    MODEL("Modelo de Android (GeomagneticField)"),
}

/** Preferencias en el almacenamiento privado de la app (MODE_PRIVATE). */
class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var lat: Double
        get() = getDouble("lat", MagMath.DEFAULT_LAT)
        set(v) = putDouble("lat", v)

    var lon: Double
        get() = getDouble("lon", MagMath.DEFAULT_LON)
        set(v) = putDouble("lon", v)

    var altM: Double
        get() = getDouble("alt", MagMath.DEFAULT_ALT_M)
        set(v) = putDouble("alt", v)

    var locationSource: String
        get() = prefs.getString("fuente_ubicacion", null) ?: DEFAULT_LOCATION_SOURCE
        set(v) = prefs.edit().putString("fuente_ubicacion", v).apply()

    var theorySource: TheorySource
        get() = runCatching { TheorySource.valueOf(prefs.getString("fuente_teorica", null) ?: "") }
            .getOrDefault(TheorySource.PLAN)
        set(v) = prefs.edit().putString("fuente_teorica", v.name).apply()

    var roundCsvCoordinates: Boolean
        get() = prefs.getBoolean("redondear_csv", true)
        set(v) = prefs.edit().putBoolean("redondear_csv", v).apply()

    var plan: ReferenceValues
        get() {
            val d = ReferenceValues.PLAN_MEDELLIN
            return ReferenceValues(
                x = getDouble("plan_x", d.x),
                y = getDouble("plan_y", d.y),
                z = getDouble("plan_z", d.z),
                h = getDouble("plan_h", d.h),
                f = getDouble("plan_f", d.f),
                inclinationDeg = getDouble("plan_i", d.inclinationDeg),
                declinationDeg = getDouble("plan_d", d.declinationDeg),
                parallel = getDouble("plan_bpar", d.parallel),
            )
        }
        set(v) {
            putDouble("plan_x", v.x)
            putDouble("plan_y", v.y)
            putDouble("plan_z", v.z)
            putDouble("plan_h", v.h)
            putDouble("plan_f", v.f)
            putDouble("plan_i", v.inclinationDeg)
            putDouble("plan_d", v.declinationDeg)
            putDouble("plan_bpar", v.parallel)
        }

    private fun getDouble(key: String, default: Double): Double =
        if (prefs.contains(key)) java.lang.Double.longBitsToDouble(prefs.getLong(key, 0L)) else default

    private fun putDouble(key: String, v: Double) {
        prefs.edit().putLong(key, java.lang.Double.doubleToRawLongBits(v)).apply()
    }

    companion object {
        const val DEFAULT_LOCATION_SOURCE = "Por defecto (Medellín)"
    }
}
