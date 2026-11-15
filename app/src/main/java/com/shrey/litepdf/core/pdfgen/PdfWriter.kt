package com.shrey.litepdf.core.pdfgen

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * A minimal streaming PDF 1.4 writer: one image per page, optional page number.
 *
 * Pages are written as they arrive and nothing is kept in memory except object offsets, so a
 * 50-image document costs one image at a time. JPEGs are embedded byte-for-byte (DCTDecode),
 * which keeps the original quality and is fast; everything else arrives as raw RGB rows and is
 * Flate-compressed (lossless). Pure Kotlin so it is unit-tested on the JVM.
 */
class PdfWriter(out: OutputStream, private val producer: String = "Lite PDF Tool") {
    private val out = CountingStream(out)
    private val offsets = HashMap<Int, Long>()
    private val pageRefs = ArrayList<Int>()
    private var nextObj = 4 // 1 catalog, 2 pages, 3 info
    private var fontObj = 0
    private var finished = false

    val pageCount: Int get() = pageRefs.size

    init {
        write("%PDF-1.4\n%âãÏÓ\n", Charsets.ISO_8859_1)
    }

    /** Embeds an existing JPEG. [copyJpeg] must write the complete, unmodified file. */
    fun addJpegPage(spec: PageSpec, transform: Affine, info: JpegInfo, label: String?, copyJpeg: (OutputStream) -> Unit) {
        val colorSpace = when (info.components) { 1 -> "/DeviceGray"; 4 -> "/DeviceCMYK"; else -> "/DeviceRGB" }
        // Adobe CMYK JPEGs store inverted values.
        val decode = if (info.components == 4 && info.adobe) " /Decode [1 0 1 0 1 0 1 0]" else ""
        val image = writeStream(
            "/Type /XObject /Subtype /Image /Width ${info.width} /Height ${info.height} " +
                "/ColorSpace $colorSpace /BitsPerComponent 8 /Filter /DCTDecode$decode",
            copyJpeg,
        )
        addPage(spec, transform, image, label)
    }

    /** Embeds 8-bit RGB pixels. [writeRows] must write exactly width*height*3 bytes. */
    fun addRgbPage(spec: PageSpec, transform: Affine, width: Int, height: Int, label: String?, writeRows: (OutputStream) -> Unit) {
        val image = writeStream(
            "/Type /XObject /Subtype /Image /Width $width /Height $height " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode",
        ) { raw ->
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
            val z = DeflaterOutputStream(NonClosing(raw), deflater, 64 * 1024)
            writeRows(z)
            z.finish()
            deflater.end()
        }
        addPage(spec, transform, image, label)
    }

    private fun addPage(spec: PageSpec, transform: Affine, image: Int, label: String?) {
        val m = transform.toPdfMatrix(spec.image)
        val content = StringBuilder()
        content.append("q ").append(m.joinToString(" ") { n(it) }).append(" cm /Im0 Do Q\n")
        if (label != null) content.append(pageNumberOps(spec, label))
        val bytes = content.toString().toByteArray(Charsets.ISO_8859_1)
        val contents = writeStream("") { it.write(bytes) }

        val font = if (label != null) font() else 0
        val fontRes = if (label != null) " /Font << /F1 $font 0 R >>" else ""
        val page = newObj()
        beginObj(page)
        write(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(spec.width)} ${n(spec.height)}] " +
                "/Resources << /XObject << /Im0 $image 0 R >>$fontRes >> /Contents $contents 0 R >>\nendobj\n",
        )
        pageRefs += page
    }

    private fun pageNumberOps(spec: PageSpec, label: String): String {
        val size = pageNumberFontSize(spec)
        val textW = helveticaWidth(label) * size
        val x = (spec.width - textW) / 2
        val y = size * 0.9
        val pad = size * 0.35
        // A white backing keeps the number readable when it sits on top of the image.
        return "q 1 g ${n(x - pad)} ${n(y - pad)} ${n(textW + 2 * pad)} ${n(size + pad)} re f Q\n" +
            "BT /F1 ${n(size)} Tf 0.25 g ${n(x)} ${n(y)} Td (${escape(label)}) Tj ET\n"
    }

    fun finish() {
        check(!finished)
        finished = true
        beginObj(1)
        write("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        beginObj(2)
        write("<< /Type /Pages /Count ${pageRefs.size} /Kids [${pageRefs.joinToString(" ") { "$it 0 R" }}] >>\nendobj\n")
        beginObj(3)
        write("<< /Producer (${escape(producer)}) >>\nendobj\n")
        val xref = out.count
        val size = nextObj
        val sb = StringBuilder("xref\n0 $size\n0000000000 65535 f \n")
        for (i in 1 until size) {
            val off = offsets[i] ?: error("object $i was never written")
            sb.append(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        sb.append("trailer\n<< /Size $size /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xref\n%%EOF\n")
        write(sb.toString())
        out.flush()
    }

    private fun font(): Int {
        if (fontObj == 0) {
            fontObj = newObj()
            beginObj(fontObj)
            write("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
        }
        return fontObj
    }

    /** Writes `<< dict /Length n 0 R >> stream ... endstream` and the length object after it. */
    private fun writeStream(dict: String, body: (OutputStream) -> Unit): Int {
        val obj = newObj()
        val lengthObj = newObj()
        beginObj(obj)
        val sep = if (dict.isEmpty()) "" else " "
        write("<< $dict$sep/Length $lengthObj 0 R >>\nstream\n")
        val start = out.count
        body(NonClosing(out))
        val length = out.count - start
        write("\nendstream\nendobj\n")
        beginObj(lengthObj)
        write("$length\nendobj\n")
        return obj
    }

    private fun newObj() = nextObj++

    private fun beginObj(id: Int) {
        offsets[id] = out.count
        write("$id 0 obj\n")
    }

    private fun write(s: String, cs: java.nio.charset.Charset = Charsets.US_ASCII) = out.write(s.toByteArray(cs))

    companion object {
        fun pageNumberFontSize(spec: PageSpec): Double =
            (minOf(spec.width, spec.height) * 0.018).coerceIn(9.0, 72.0)

        /** Helvetica advance widths (per 1 pt of font size) for the characters a page label uses. */
        fun helveticaWidth(text: String): Double = text.sumOf { c ->
            when (c) {
                in '0'..'9' -> 556
                ' ', '/' -> 278
                else -> 556
            }.toInt()
        } / 1000.0

        fun n(v: Double): String {
            val s = String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
            return if (s == "-0") "0" else s
        }

        private fun escape(s: String) = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    }
}

private class CountingStream(private val target: OutputStream) : FilterOutputStream(target) {
    var count = 0L
        private set

    override fun write(b: Int) {
        target.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        target.write(b, off, len)
        count += len
    }
}

/** Lets callers close their wrappers without closing the PDF stream underneath. */
private class NonClosing(private val target: OutputStream) : OutputStream() {
    override fun write(b: Int) = target.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = target.write(b, off, len)
    override fun flush() = target.flush()
    override fun close() = target.flush()
}
