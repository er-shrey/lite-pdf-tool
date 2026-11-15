package com.shrey.litepdf.core.pdfgen

import kotlin.math.min

enum class PageSize { ORIGINAL, A4 }

data class PdfConfiguration(
    val pageSize: PageSize = PageSize.ORIGINAL,
    val includePageNumbers: Boolean = true,
)

/** Rectangle in PDF points, origin bottom-left. */
data class PdfRect(val x: Double, val y: Double, val width: Double, val height: Double)

data class PageSpec(val width: Double, val height: Double, val image: PdfRect)

/**
 * Page geometry for one image (FR-10, PRD section 12). Inputs are the image size *as displayed*
 * (after EXIF orientation and user rotation), in pixels.
 */
object PageLayout {
    const val A4_WIDTH = 595.28
    const val A4_HEIGHT = 841.89
    const val FALLBACK_DPI = 96.0
    const val A4_MARGIN = 24.0
    const val POINTS_PER_INCH = 72.0

    /** DPI from metadata, or [FALLBACK_DPI] when missing or implausible. */
    fun effectiveDpi(metadataDpi: Double?): Double =
        metadataDpi?.takeIf { it in 50.0..1200.0 } ?: FALLBACK_DPI

    fun pixelsToPoints(px: Int, dpi: Double): Double = px * POINTS_PER_INCH / dpi

    fun layout(widthPx: Int, heightPx: Int, dpiX: Double, dpiY: Double, size: PageSize): PageSpec {
        require(widthPx > 0 && heightPx > 0) { "Empty image" }
        val w = pixelsToPoints(widthPx, dpiX)
        val h = pixelsToPoints(heightPx, dpiY)
        return when (size) {
            PageSize.ORIGINAL -> PageSpec(w, h, PdfRect(0.0, 0.0, w, h))
            PageSize.A4 -> {
                val landscape = w > h
                val pw = if (landscape) A4_HEIGHT else A4_WIDTH
                val ph = if (landscape) A4_WIDTH else A4_HEIGHT
                val boxW = pw - 2 * A4_MARGIN
                val boxH = ph - 2 * A4_MARGIN
                // Fit inside the box, keep the aspect ratio, never upscale past natural size.
                val scale = min(min(boxW / w, boxH / h), 1.0)
                val iw = w * scale
                val ih = h * scale
                PageSpec(pw, ph, PdfRect((pw - iw) / 2, (ph - ih) / 2, iw, ih))
            }
        }
    }
}
