package com.shrey.litepdf

import com.google.common.truth.Truth.assertThat
import com.shrey.litepdf.core.pdfgen.Affine
import com.shrey.litepdf.core.pdfgen.PdfRect
import org.junit.Test

class OrientationTest {
    /** Where raw pixel (s, t) lands on the page, via the PDF matrix (u = s, v = 1 - t). */
    private fun place(m: DoubleArray, s: Double, t: Double): Pair<Double, Double> {
        val u = s; val v = 1 - t
        return (m[0] * u + m[2] * v + m[4]) to (m[1] * u + m[3] * v + m[5])
    }

    private val rect = PdfRect(10.0, 20.0, 100.0, 200.0)
    private fun topLeft(r: PdfRect) = r.x to r.y + r.height
    private fun topRight(r: PdfRect) = r.x + r.width to r.y + r.height
    private fun bottomLeft(r: PdfRect) = r.x to r.y
    private fun bottomRight(r: PdfRect) = r.x + r.width to r.y

    @Test fun identityKeepsRawTopLeftAtTopLeft() {
        val m = Affine.of(1, 0).toPdfMatrix(rect)
        assertThat(place(m, 0.0, 0.0)).isEqualTo(topLeft(rect))
        assertThat(place(m, 1.0, 1.0)).isEqualTo(bottomRight(rect))
    }

    @Test fun exif6IsQuarterTurnClockwise() {
        val m = Affine.of(6, 0).toPdfMatrix(rect)
        assertThat(place(m, 0.0, 0.0)).isEqualTo(topRight(rect))
        assertThat(place(m, 0.0, 1.0)).isEqualTo(topLeft(rect))
        assertThat(Affine.of(6, 0).swapsAxes).isTrue()
    }

    @Test fun userQuarterTurnEqualsExif6() {
        assertThat(Affine.of(1, 1)).isEqualTo(Affine.exif(6))
    }

    @Test fun twoTurnsIs180AndFourIsIdentity() {
        assertThat(Affine.of(1, 2)).isEqualTo(Affine.exif(3))
        assertThat(Affine.of(1, 4)).isEqualTo(Affine.IDENTITY)
        assertThat(Affine.of(6, 3)).isEqualTo(Affine.IDENTITY)
    }

    @Test fun exif8ThenUserTurnIsUpright() {
        assertThat(Affine.of(8, 1)).isEqualTo(Affine.IDENTITY)
    }

    @Test fun mirrorFlipsHorizontally() {
        val m = Affine.of(2, 0).toPdfMatrix(rect)
        assertThat(place(m, 0.0, 0.0)).isEqualTo(topRight(rect))
        assertThat(place(m, 1.0, 1.0)).isEqualTo(bottomLeft(rect))
    }
}
