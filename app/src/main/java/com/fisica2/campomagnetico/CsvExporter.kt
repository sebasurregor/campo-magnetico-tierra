package com.fisica2.campomagnetico

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Crea el CSV SOLO cuando el usuario toca "Exportar", en la caché privada (cache/exports/), y lo
 * comparte por un FileProvider no exportado con permiso de lectura temporal para la app elegida.
 */
object CsvExporter {
    fun share(context: Context, captures: List<Capture>, roundCoordinates: Boolean) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Se borran exportaciones anteriores para no acumular datos.
        dir.listFiles()?.forEach { it.delete() }

        val name = "capturas_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv"
        val file = File(dir, name)
        // BOM UTF-8 para que Excel muestre bien las tildes.
        file.writeText("﻿" + CsvFormat.build(captures, roundCoordinates), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Capturas - campo magnético de la Tierra")
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir CSV"))
    }
}
