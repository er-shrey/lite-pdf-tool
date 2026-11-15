package com.shrey.litepdf.core.pdfview

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import java.io.Closeable

/**
 * The viewer only talks to this interface, so the PDFium implementation can be swapped
 * (for example for the platform renderer once it can search on every supported Android).
 */
interface PdfEngine {
    /** @throws PasswordRequiredException, InvalidPdfException, SecurityException, java.io.FileNotFoundException */
    suspend fun open(uri: Uri, password: String? = null): OpenPdf
}

interface OpenPdf : Closeable {
    val pageCount: Int

    /** Page sizes in PDF points (rotation applied). */
    val pageSizes: List<PageDims>

    /** Renders a whole page [widthPx] wide; height follows the page's aspect ratio. */
    suspend fun renderPage(pageIndex: Int, widthPx: Int): Bitmap

    /** False when no page has extractable text (for example a scanned document). */
    suspend fun hasText(): Boolean

    /** Case-insensitive search over all pages. [onPage] reports progress. */
    suspend fun search(query: String, onPage: (Int) -> Unit = {}): List<SearchHit>
}

data class PageDims(val width: Float, val height: Float) {
    val aspect: Float get() = if (height > 0f) width / height else 1f
}

/** One match. [rects] are fractions (0..1) of the page width/height, origin top-left. */
data class SearchHit(val page: Int, val rects: List<RectF>)

class PasswordRequiredException(val wrongPassword: Boolean) : Exception()
class InvalidPdfException(message: String, cause: Throwable? = null) : Exception(message, cause)
