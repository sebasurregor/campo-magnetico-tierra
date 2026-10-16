package com.fisica2.campomagnetico

import android.hardware.GeomagneticField

object TheoryModel {
    /**
     * Valores del modelo geomagnético que trae Android (WMM). La versión del modelo depende de la
     * versión de Android del teléfono, por eso la app los muestra junto a los del plan.
     * GeomagneticField entrega X, Y, Z, H y F en nanoteslas: se dividen entre 1000 para pasar a μT.
     */
    fun fromAndroid(latDeg: Double, lonDeg: Double, altM: Double, timeMillis: Long): ReferenceValues {
        val g = GeomagneticField(latDeg.toFloat(), lonDeg.toFloat(), altM.toFloat(), timeMillis)
        val x = g.x / 1000.0
        val z = g.z / 1000.0
        return ReferenceValues(
            x = x,
            y = g.y / 1000.0,
            z = z,
            h = g.horizontalStrength / 1000.0,
            f = g.fieldStrength / 1000.0,
            inclinationDeg = g.inclination.toDouble(),
            declinationDeg = g.declination.toDouble(),
            parallel = MagMath.parallelToAxis(x, z, latDeg),
        )
    }
}
