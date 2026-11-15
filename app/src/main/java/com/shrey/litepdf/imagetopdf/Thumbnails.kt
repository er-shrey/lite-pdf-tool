package com.shrey.litepdf.imagetopdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Small, appropriately sized previews; full-resolution decoding only happens during generation. */
object Thumbnails {
    private const val PX = 384
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun load(resolver: ContentResolver, uri: Uri): Bitmap? {
        val key = uri.toString()
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            // Respects EXIF orientation, like the generated PDF.
            runCatching { resolver.loadThumbnail(uri, Size(PX, PX), null) }.getOrNull()
        }?.also { cache.put(key, it) }
    }
}

/** Draws [uri]'s thumbnail turned by [quarterTurns] and fitted (centered) into the canvas. */
@Composable
fun RotatedThumbnail(uri: Uri, quarterTurns: Int, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val image by produceState<ImageBitmap?>(null, uri) { value = Thumbnails.load(resolver, uri)?.asImageBitmap() }
    Canvas(modifier) {
        val img = image ?: return@Canvas
        val odd = quarterTurns % 2 == 1
        val shownW = if (odd) img.height.toFloat() else img.width.toFloat()
        val shownH = if (odd) img.width.toFloat() else img.height.toFloat()
        val scale = min(size.width / shownW, size.height / shownH)
        // Size before rotation, centred on the canvas.
        val w = img.width * scale
        val h = img.height * scale
        rotate(90f * quarterTurns, pivot = center) {
            drawImage(
                img,
                dstOffset = IntOffset((center.x - w / 2).roundToInt(), (center.y - h / 2).roundToInt()),
                dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
            )
        }
    }
}

