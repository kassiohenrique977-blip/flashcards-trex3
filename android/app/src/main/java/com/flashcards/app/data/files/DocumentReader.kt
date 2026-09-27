package com.flashcards.app.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.flashcards.core.importer.ImportDocument
import com.flashcards.core.importer.ImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Lê um arquivo escolhido pelo seletor do sistema (Storage Access Framework). */
class DocumentReader(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    /** @throws ImportException se o arquivo não puder ser aberto ou for grande demais. */
    suspend fun read(uri: Uri): ImportDocument = withContext(Dispatchers.IO) {
        try {
            val name = displayName(uri) ?: uri.lastPathSegment ?: "arquivo"
            val bytes = resolver.openInputStream(uri)?.use(::readLimited)
                ?: throw ImportException("Não foi possível abrir o arquivo.")
            ImportDocument(name, bytes)
        } catch (_: IOException) {
            throw ImportException("Não foi possível ler o arquivo.")
        } catch (_: SecurityException) {
            throw ImportException("Sem permissão para ler o arquivo.")
        }
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) {
                throw ImportException("Arquivo grande demais (máximo de ${MAX_BYTES / (1024 * 1024)} MB).")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
