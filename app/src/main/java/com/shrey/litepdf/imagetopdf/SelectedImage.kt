package com.shrey.litepdf.imagetopdf

import android.net.Uri
import android.os.Parcelable
import com.shrey.litepdf.core.pdfgen.Affine
import kotlinx.parcelize.Parcelize

/**
 * A lightweight reference to a picked image: nothing is decoded until a thumbnail or the
 * PDF needs it. [rotation] is the user's clockwise quarter turns on top of EXIF orientation.
 */
@Parcelize
data class SelectedImage(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val widthPx: Int,
    val heightPx: Int,
    val exifOrientation: Int,
    val dpiX: Double?,
    val dpiY: Double?,
    val sizeBytes: Long,
    val rotation: Int = 0,
) : Parcelable {
    val transform: Affine get() = Affine.of(exifOrientation, rotation)
    val displayedWidth: Int get() = if (transform.swapsAxes) heightPx else widthPx
    val displayedHeight: Int get() = if (transform.swapsAxes) widthPx else heightPx
    val displayedDpiX: Double? get() = if (transform.swapsAxes) dpiY else dpiX
    val displayedDpiY: Double? get() = if (transform.swapsAxes) dpiX else dpiY

    /** Thumbnails already respect EXIF; only the user's rotation is drawn on top. */
    val exifSwapsAxes: Boolean get() = Affine.exif(exifOrientation).swapsAxes
}
