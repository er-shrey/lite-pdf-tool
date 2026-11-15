package com.shrey.litepdf.imagetopdf

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import java.util.UUID

/** Reads size, EXIF orientation and DPI without decoding pixels. */
object ImageProbe {
    fun probe(resolver: ContentResolver, uri: Uri): SelectedImage? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var orientation = ExifInterface.ORIENTATION_NORMAL
        var dpiX: Double? = null
        var dpiY: Double? = null
        runCatching {
            resolver.openInputStream(uri)?.use { s ->
                val exif = ExifInterface(s)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val unit = exif.getAttributeInt(ExifInterface.TAG_RESOLUTION_UNIT, 2)
                val factor = if (unit == 3) 2.54 else 1.0
                dpiX = exif.getAttributeDouble(ExifInterface.TAG_X_RESOLUTION, 0.0).takeIf { it > 0 }?.times(factor)
                dpiY = exif.getAttributeDouble(ExifInterface.TAG_Y_RESOLUTION, 0.0).takeIf { it > 0 }?.times(factor)
            }
        }

        var name = uri.lastPathSegment ?: "image"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        SelectedImage(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = name,
            widthPx = opts.outWidth,
            heightPx = opts.outHeight,
            exifOrientation = orientation,
            dpiX = dpiX,
            dpiY = dpiY ?: dpiX,
            sizeBytes = size,
        )
    }.getOrNull()
}
