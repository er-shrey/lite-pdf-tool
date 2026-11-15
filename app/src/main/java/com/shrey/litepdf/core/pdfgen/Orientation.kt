package com.shrey.litepdf.core.pdfgen

/**
 * Maps raw image pixels to their displayed position, combining the EXIF orientation tag with
 * the user's clockwise quarter turns. Expressed on normalised coordinates (s, t) in [0,1],
 * t pointing down: s' = a*s + b*t + c, t' = d*s + e*t + f.
 */
data class Affine(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
    /** this after [first]: p -> this(first(p)). */
    fun after(first: Affine) = Affine(
        a = z(a * first.a + b * first.d),
        b = z(a * first.b + b * first.e),
        c = z(a * first.c + b * first.f + c),
        d = z(d * first.a + e * first.d),
        e = z(d * first.b + e * first.e),
        f = z(d * first.c + e * first.f + f),
    )

    /** True when width and height trade places. */
    val swapsAxes: Boolean get() = a == 0.0

    companion object {
        /** Normalises -0.0 so equal transforms compare equal. */
        private fun z(v: Double) = v + 0.0

        val IDENTITY = Affine(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        private val ROT90 = Affine(0.0, -1.0, 1.0, 1.0, 0.0, 0.0) // (1 - t, s)

        /** EXIF orientation values 1..8; anything else is treated as 1. */
        fun exif(orientation: Int): Affine = when (orientation) {
            2 -> Affine(-1.0, 0.0, 1.0, 0.0, 1.0, 0.0)  // flip horizontal
            3 -> Affine(-1.0, 0.0, 1.0, 0.0, -1.0, 1.0) // 180
            4 -> Affine(1.0, 0.0, 0.0, 0.0, -1.0, 1.0)  // flip vertical
            5 -> Affine(0.0, 1.0, 0.0, 1.0, 0.0, 0.0)   // transpose
            6 -> ROT90                                   // 90 CW
            7 -> Affine(0.0, -1.0, 1.0, -1.0, 0.0, 1.0) // transverse
            8 -> Affine(0.0, 1.0, 0.0, -1.0, 0.0, 1.0)  // 270 CW
            else -> IDENTITY
        }

        fun of(exifOrientation: Int, userQuarterTurns: Int): Affine {
            var m = exif(exifOrientation)
            repeat(((userQuarterTurns % 4) + 4) % 4) { m = ROT90.after(m) }
            return m
        }
    }

    /**
     * The PDF `cm` operands that draw the image (PDF unit square, v pointing up) into [r].
     */
    fun toPdfMatrix(r: PdfRect): DoubleArray {
        val w = r.width
        val h = r.height
        return doubleArrayOf(
            w * a,              // A
            -h * d,             // B
            -w * b,             // C
            h * e,              // D
            r.x + w * (b + c),  // E
            r.y + h * (1 - e - f), // F
        )
    }
}
