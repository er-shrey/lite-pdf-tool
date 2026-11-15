package com.shrey.litepdf.core.pdfgen

import java.io.InputStream

/** What a PDF needs to embed a JPEG as-is with DCTDecode. */
data class JpegInfo(val width: Int, val height: Int, val components: Int, val adobe: Boolean)

object JpegParser {
    /**
     * Reads markers up to the first frame header. Returns null for anything that is not a
     * baseline/progressive 8-bit JPEG with 1, 3 or 4 components, so the caller re-encodes it.
     */
    fun parse(input: InputStream): JpegInfo? {
        val s = input.buffered()
        if (s.read() != 0xFF || s.read() != 0xD8) return null
        var adobe = false
        while (true) {
            var b = s.read()
            if (b == -1) return null
            if (b != 0xFF) return null
            while (b == 0xFF) b = s.read()
            if (b == -1) return null
            val marker = b
            if (marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01) continue
            if (marker == 0xD9 || marker == 0xDA) return null // no frame header before scan
            val len = (s.read() shl 8) or s.read()
            if (len < 2) return null
            val body = ByteArray(len - 2)
            if (s.readNBytesCompat(body) != body.size) return null
            when (marker) {
                0xEE -> if (body.size >= 5 && String(body, 0, 5, Charsets.US_ASCII) == "Adobe") adobe = true
                0xC0, 0xC1, 0xC2 -> {
                    if (body.size < 6) return null
                    val precision = body[0].toInt() and 0xFF
                    val height = ((body[1].toInt() and 0xFF) shl 8) or (body[2].toInt() and 0xFF)
                    val width = ((body[3].toInt() and 0xFF) shl 8) or (body[4].toInt() and 0xFF)
                    val comps = body[5].toInt() and 0xFF
                    if (precision != 8 || width == 0 || height == 0 || comps !in setOf(1, 3, 4)) return null
                    return JpegInfo(width, height, comps, adobe)
                }
                // Lossless, arithmetic-coded and 12-bit frames: let the decoder path handle them.
                in 0xC3..0xCF -> if (marker != 0xC4 && marker != 0xC8 && marker != 0xCC) return null
            }
        }
    }

    private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }
}
