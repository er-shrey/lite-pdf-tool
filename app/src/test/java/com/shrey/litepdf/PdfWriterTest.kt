package com.shrey.litepdf

import com.google.common.truth.Truth.assertThat
import com.shrey.litepdf.core.pdfgen.Affine
import com.shrey.litepdf.core.pdfgen.JpegInfo
import com.shrey.litepdf.core.pdfgen.JpegParser
import com.shrey.litepdf.core.pdfgen.PageLayout
import com.shrey.litepdf.core.pdfgen.PageSize
import com.shrey.litepdf.core.pdfgen.PdfWriter
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PdfWriterTest {
    /** SOI, APP0, SOF0 (16x8, 3 components), EOI. Enough for the parser and the writer. */
    private val fakeJpeg = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xE0.toByte(), 0, 4, 0, 0,
        0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, 0, 8, 0, 16, 3, 1, 0x11, 0,
        0xFF.toByte(), 0xD9.toByte(),
    )

    @Test fun parsesJpegHeader() {
        assertThat(JpegParser.parse(ByteArrayInputStream(fakeJpeg))).isEqualTo(JpegInfo(16, 8, 3, false))
    }

    @Test fun rejectsNonJpeg() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        assertThat(JpegParser.parse(ByteArrayInputStream(png))).isNull()
    }

    @Test fun writesValidStructureWithCorrectXref() {
        val out = ByteArrayOutputStream()
        val w = PdfWriter(out)
        val spec = PageLayout.layout(16, 8, 96.0, 96.0, PageSize.A4)
        w.addJpegPage(spec, Affine.IDENTITY, JpegInfo(16, 8, 3, false), "1 / 2") { it.write(fakeJpeg) }
        val spec2 = PageLayout.layout(2, 2, 96.0, 96.0, PageSize.ORIGINAL)
        w.addRgbPage(spec2, Affine.of(1, 1), 2, 2, null) { it.write(ByteArray(12) { 0x7F }) }
        w.finish()
        val bytes = out.toByteArray()
        val text = String(bytes, Charsets.ISO_8859_1)

        assertThat(text).startsWith("%PDF-1.4")
        assertThat(text).contains("/Type /Pages /Count 2")
        assertThat(text).contains("/Filter /DCTDecode")
        assertThat(text).contains("/Filter /FlateDecode")
        assertThat(text).contains("(1 / 2) Tj")
        assertThat(text.trimEnd()).endsWith("%%EOF")

        // Every xref entry must point at "<n> 0 obj".
        val startxref = text.substringAfterLast("startxref\n").substringBefore("\n").toInt()
        val xref = text.substring(startxref)
        val entries = xref.lines().drop(2).takeWhile { it.endsWith(" n ") || it.endsWith(" f ") }
        entries.forEachIndexed { i, line ->
            if (i == 0) return@forEachIndexed
            val off = line.substring(0, 10).toInt()
            assertThat(text.substring(off)).startsWith("$i 0 obj")
        }
        // The JPEG is embedded byte-for-byte.
        assertThat(text).contains(String(fakeJpeg, Charsets.ISO_8859_1))
    }

    @Test fun numberFormatting() {
        assertThat(PdfWriter.n(1.0)).isEqualTo("1")
        assertThat(PdfWriter.n(595.28)).isEqualTo("595.28")
        assertThat(PdfWriter.n(-0.0001)).isEqualTo("0")
    }
}
