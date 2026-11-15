package com.shrey.litepdf.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore

/** Saves generated PDFs to Documents/Lite-PDF-Tool/ through MediaStore (no storage permission). */
object PdfStore {
    const val FOLDER = "Lite-PDF-Tool"
    val RELATIVE_PATH = "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER/"
    val DISPLAY_LOCATION = "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER"

    data class Saved(val uri: Uri, val fileName: String, val sizeBytes: Long, val relativePath: String)

    private fun collection(): Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Creates a hidden (pending) entry; MediaStore picks `name (1).pdf` etc. if the name is taken. */
    fun createPending(context: Context, fileName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection(), values)
            ?: throw java.io.IOException("Could not create the file in $DISPLAY_LOCATION")
    }

    /** Makes the file visible and reads back what was actually stored. */
    fun publish(context: Context, uri: Uri): Saved {
        val resolver = context.contentResolver
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val saved = Saved(uri, c.getString(0), c.getLong(1), c.getString(2) ?: "")
                if (saved.relativePath.trimEnd('/') != RELATIVE_PATH.trimEnd('/')) {
                    throw java.io.IOException("File was saved to ${saved.relativePath} instead of $DISPLAY_LOCATION")
                }
                return saved
            }
        }
        throw java.io.IOException("The saved file could not be found")
    }

    fun delete(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** Deletes half-written files left behind if the app was killed while generating. */
    fun deleteStalePending(context: Context) {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.RELATIVE_PATH}=?")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(RELATIVE_PATH))
        }
        val stale = ArrayList<Uri>()
        runCatching {
            context.contentResolver.query(collection(), arrayOf(MediaStore.MediaColumns._ID), args, null)?.use { c ->
                while (c.moveToNext()) stale += Uri.withAppendedPath(collection(), c.getLong(0).toString())
            }
        }
        stale.forEach { delete(context, it) }
    }

    fun freeBytes(): Long = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }
        .getOrDefault(Long.MAX_VALUE)
}
