package com.fisica2.campomagnetico

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda la lista de capturas en un JSON dentro del almacenamiento privado (filesDir). */
class CaptureStore(context: Context) {
    private val file = File(context.filesDir, "capturas.json")

    fun load(): List<Capture> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(captures: List<Capture>) {
        val arr = JSONArray()
        captures.forEach { arr.put(toJson(it)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    private fun toJson(c: Capture) = JSONObject().apply {
        put("id", c.id)
        put("time", c.timeMillis)
        put("orientation", c.orientation)
        put("note", c.note)
        putNum("lat", c.lat)
        putNum("lon", c.lon)
        putNum("alt", c.altM)
        put("samples", c.samples)
        put("minAccuracy", c.minAccuracy)
        put("bx", stat(c.bx))
        put("by", stat(c.by))
        put("bz", stat(c.bz))
        c.earth?.let { e ->
            put("earth", JSONObject().apply {
                put("x", stat(e.x)); put("y", stat(e.y)); put("z", stat(e.z))
                put("f", stat(e.f)); put("h", stat(e.h)); put("i", stat(e.i))
                put("par", stat(e.parallel))
            })
        }
        c.raw?.let { put("raw", vec(it)) }
        c.bias?.let { put("bias", vec(it)) }
        put("theorySource", c.theorySource)
        put("theory", JSONObject().apply {
            val t = c.theory
            putNum("x", t.x); putNum("y", t.y); putNum("z", t.z); putNum("h", t.h); putNum("f", t.f)
            putNum("i", t.inclinationDeg); putNum("d", t.declinationDeg); putNum("par", t.parallel)
        })
    }

    private fun fromJson(o: JSONObject): Capture {
        val t = o.getJSONObject("theory")
        return Capture(
            id = o.getLong("id"),
            timeMillis = o.getLong("time"),
            orientation = o.getString("orientation"),
            note = o.optString("note", ""),
            lat = o.num("lat"),
            lon = o.num("lon"),
            altM = o.num("alt"),
            samples = o.getInt("samples"),
            minAccuracy = o.getInt("minAccuracy"),
            bx = stat(o.getJSONObject("bx")),
            by = stat(o.getJSONObject("by")),
            bz = stat(o.getJSONObject("bz")),
            earth = o.optJSONObject("earth")?.let { e ->
                EarthStats(
                    x = stat(e.getJSONObject("x")), y = stat(e.getJSONObject("y")), z = stat(e.getJSONObject("z")),
                    f = stat(e.getJSONObject("f")), h = stat(e.getJSONObject("h")), i = stat(e.getJSONObject("i")),
                    parallel = stat(e.getJSONObject("par")),
                )
            },
            raw = o.optJSONObject("raw")?.let { vec(it) },
            bias = o.optJSONObject("bias")?.let { vec(it) },
            theorySource = o.optString("theorySource", ""),
            theory = ReferenceValues(
                x = t.num("x"), y = t.num("y"), z = t.num("z"), h = t.num("h"), f = t.num("f"),
                inclinationDeg = t.num("i"), declinationDeg = t.num("d"), parallel = t.num("par"),
            ),
        )
    }

    private fun stat(s: Stat) = JSONObject().apply { putNum("m", s.mean); putNum("s", s.sd) }
    private fun stat(o: JSONObject) = Stat(o.num("m"), o.num("s"))
    private fun vec(v: Vec3) = JSONObject().apply { putNum("x", v.x); putNum("y", v.y); putNum("z", v.z) }
    private fun vec(o: JSONObject) = Vec3(o.num("x"), o.num("y"), o.num("z"))

    // JSONObject no acepta NaN: se guarda como null y se lee de vuelta como NaN.
    private fun JSONObject.putNum(key: String, v: Double) {
        put(key, if (v.isNaN() || v.isInfinite()) JSONObject.NULL else v)
    }

    private fun JSONObject.num(key: String): Double = if (isNull(key)) Double.NaN else getDouble(key)
}
