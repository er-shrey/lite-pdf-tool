package com.shrey.litepdf.imagetopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import com.shrey.litepdf.core.pdfgen.JpegParser
import com.shrey.litepdf.core.pdfgen.PageLayout
import com.shrey.litepdf.core.pdfgen.PdfConfiguration
import com.shrey.litepdf.core.pdfgen.PdfWriter
import com.shrey.litepdf.core.storage.PdfStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

class ImageReadException(val position: Int, val name: String) : IOException("Could not read image $position ($name)")
class InsufficientStorageException : IOException("Not enough free storage")

@kotlinx.parcelize.Parcelize
data class PdfGenerationResult(
    val uri: android.net.Uri,
    val fileName: String,
    val pageCount: Int,
    val fileSize: Long,
    val outputLocation: String,
) : android.os.Parcelable

/**
 * Writes one page per image, in list order, one image at a time (FR-12). The output is a hidden
 * MediaStore entry until it is complete; on any failure or cancellation it is deleted, so a
 * partial PDF is never reported as a success.
 */
class ImageToPdfGenerator(private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun generate(
        images: List<SelectedImage>,
        config: PdfConfiguration,
        fileName: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): PdfGenerationResult = withContext(Dispatchers.IO) {
        require(images.isNotEmpty())
        val estimate = images.sumOf { if (it.sizeBytes > 0) it.sizeBytes else it.widthPx.toLong() * it.heightPx }
        if (PdfStore.freeBytes() < estimate + MIN_HEADROOM) throw InsufficientStorageException()

        val uri = PdfStore.createPending(context, fileName)
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("Could not open the output file")
            out.buffered(256 * 1024).use { stream ->
                val writer = PdfWriter(stream)
                images.forEachIndexed { i, img ->
                    coroutineContext.ensureActive()
                    onProgress(i, images.size)
                    addPage(writer, img, i, images.size, config)
                }
                writer.finish()
            }
            coroutineContext.ensureActive()
            onProgress(images.size, images.size)
            val saved = PdfStore.publish(context, uri)
            PdfGenerationResult(saved.uri, saved.fileName, images.size, saved.sizeBytes, PdfStore.DISPLAY_LOCATION)
        } catch (e: Throwable) {
            PdfStore.delete(context, uri)
            if (e is IOException && e.message?.contains("ENOSPC") == true) throw InsufficientStorageException()
            throw e
        }
    }

    private fun addPage(writer: PdfWriter, img: SelectedImage, index: Int, total: Int, config: PdfConfiguration) {
        val spec = PageLayout.layout(
            img.displayedWidth, img.displayedHeight,
            PageLayout.effectiveDpi(img.displayedDpiX), PageLayout.effectiveDpi(img.displayedDpiY),
            config.pageSize,
        )
        val label = if (config.includePageNumbers) "${index + 1} / $total" else null
        val position = index + 1

        val jpeg = try {
            resolver.openInputStream(img.uri)?.use { JpegParser.parse(it) }
        } catch (e: IOException) {
            throw ImageReadException(position, img.displayName)
        } catch (e: SecurityException) {
            throw ImageReadException(position, img.displayName)
        }

        if (jpeg != null) {
            writer.addJpegPage(spec, img.transform, jpeg, label) { out ->
                val input = resolver.openInputStream(img.uri) ?: throw ImageReadException(position, img.displayName)
                input.use { it.copyTo(out, 64 * 1024) }
            }
            return
        }

        // Not a plain JPEG (PNG, WebP, HEIF, ...): decode in horizontal strips and store lossless RGB.
        val regionDecoder = runCatching {
            resolver.openInputStream(img.uri)?.use { BitmapRegionDecoder.newInstance(BufferedInputStream(it)) }
        }.getOrNull()
        if (regionDecoder != null) {
            try {
                val w = regionDecoder.width
                val h = regionDecoder.height
                writer.addRgbPage(spec, img.transform, w, h, label) { out -> writeStrips(regionDecoder, w, h, out) }
            } finally {
                regionDecoder.recycle()
            }
            return
        }

        // Last resort for formats the region decoder rejects (for example GIF): one full decode.
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSizeFor(img.widthPx, img.heightPx)
        }
        val bitmap = runCatching { resolver.openInputStream(img.uri)?.use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
            ?: throw ImageReadException(position, img.displayName)
        try {
            writer.addRgbPage(spec, img.transform, bitmap.width, bitmap.height, label) { out ->
                writeBitmapRows(bitmap, 0, bitmap.height, out)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeStrips(decoder: BitmapRegionDecoder, w: Int, h: Int, out: OutputStream) {
        val stripRows = (STRIP_PIXELS / w).coerceIn(1, h)
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var y = 0
        while (y < h) {
            val bottom = minOf(h, y + stripRows)
            val strip = decoder.decodeRegion(Rect(0, y, w, bottom), opts) ?: throw IOException("Decode failed")
            try {
                writeBitmapRows(strip, 0, strip.height, out)
            } finally {
                strip.recycle()
            }
            y = bottom
        }
    }

    /** Writes RGB bytes, flattening transparency onto white. */
    private fun writeBitmapRows(bitmap: Bitmap, from: Int, to: Int, out: OutputStream) {
        val w = bitmap.width
        val pixels = IntArray(w)
        val row = ByteArray(w * 3)
        for (y in from until to) {
            bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
            var o = 0
            for (p in pixels) {
                val a = p ushr 24
                var r = (p shr 16) and 0xFF
                var g = (p shr 8) and 0xFF
                var b = p and 0xFF
                if (a != 255) {
                    r = (r * a + 255 * (255 - a)) / 255
                    g = (g * a + 255 * (255 - a)) / 255
                    b = (b * a + 255 * (255 - a)) / 255
                }
                row[o++] = r.toByte(); row[o++] = g.toByte(); row[o++] = b.toByte()
            }
            out.write(row)
        }
    }

    private fun sampleSizeFor(w: Int, h: Int): Int {
        var s = 1
        while (w.toLong() / s * (h.toLong() / s) > MAX_FULL_DECODE_PIXELS) s *= 2
        return s
    }

    companion object {
        private const val STRIP_PIXELS = 2_000_000
        private const val MAX_FULL_DECODE_PIXELS = 24_000_000L
        private const val MIN_HEADROOM = 20L * 1024 * 1024
    }
}
