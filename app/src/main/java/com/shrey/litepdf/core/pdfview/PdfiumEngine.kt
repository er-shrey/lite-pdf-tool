package com.shrey.litepdf.core.pdfview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.api.AlreadyClosedBehavior
import io.legere.pdfiumandroid.api.Config
import io.legere.pdfiumandroid.api.PdfPasswordException
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext

class PdfiumEngine(private val context: Context) : PdfEngine {
    private val core by lazy {
        PdfiumCoreKt(Dispatchers.IO, Config(alreadyClosedBehavior = AlreadyClosedBehavior.IGNORE))
    }

    override suspend fun open(uri: Uri, password: String?): OpenPdf = withContext(Dispatchers.IO) {
        // Read-only access to exactly this document; nothing is copied into app storage.
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw java.io.FileNotFoundException("No file descriptor for $uri")
        try {
            val doc = core.newDocument(pfd, password)
            val count = doc.getPageCount()
            if (count <= 0) {
                doc.safeClose()
                throw InvalidPdfException("This PDF has no pages.")
            }
            val sizes = (0 until count).map { i ->
                val page = doc.openPage(i)
                try {
                    PageDims(
                        (page?.getPageWidthPoint() ?: 612).toFloat().coerceAtLeast(1f),
                        (page?.getPageHeightPoint() ?: 792).toFloat().coerceAtLeast(1f),
                    )
                } finally {
                    page?.safeClose()
                }
            }
            PdfiumPdf(doc, pfd, count, sizes)
        } catch (e: PdfPasswordException) {
            pfd.close()
            throw PasswordRequiredException(wrongPassword = password != null)
        } catch (e: InvalidPdfException) {
            pfd.close()
            throw e
        } catch (e: IOException) {
            pfd.close()
            throw InvalidPdfException("This file is damaged or is not a PDF.", e)
        } catch (e: RuntimeException) {
            pfd.close()
            throw InvalidPdfException("This PDF could not be opened.", e)
        }
    }
}

private class PdfiumPdf(
    private val doc: PdfDocumentKt,
    private val pfd: ParcelFileDescriptor,
    override val pageCount: Int,
    override val pageSizes: List<PageDims>,
) : OpenPdf {
    @Volatile private var closed = false
    private var charCounts: IntArray? = null

    override suspend fun renderPage(pageIndex: Int, widthPx: Int): Bitmap {
        val dims = pageSizes[pageIndex]
        val w = widthPx.coerceAtLeast(1)
        val h = (w / dims.aspect).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (closed) return bitmap
        val page = doc.openPage(pageIndex) ?: return bitmap.also { it.eraseColor(android.graphics.Color.WHITE) }
        try {
            page.renderPageBitmap(bitmap, 0, 0, w, h, renderAnnot = true)
        } finally {
            page.safeClose()
        }
        return bitmap
    }

    private suspend fun counts(): IntArray =
        charCounts ?: doc.getPageCharCounts().also { charCounts = it }

    override suspend fun hasText(): Boolean = !closed && counts().any { it > 0 }

    override suspend fun search(query: String, onPage: (Int) -> Unit): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty() || closed) return emptyList()
        val counts = counts()
        val hits = ArrayList<SearchHit>()
        for (i in 0 until pageCount) {
            coroutineContext.ensureActive()
            onPage(i)
            if (counts.getOrElse(i) { 0 } <= 0) continue
            val page = doc.openPage(i) ?: continue
            val text = page.openTextPage()
            try {
                val dims = pageSizes[i]
                val refW = REF
                val refH = (REF / dims.aspect).toInt().coerceAtLeast(1)
                val find = text.findStart(q, emptySet(), 0) ?: continue
                try {
                    while (find.findNext()) {
                        val start = find.getSchResultIndex()
                        val len = find.getSchCount()
                        val rects = ArrayList<RectF>()
                        val n = text.textPageCountRects(start, len)
                        for (r in 0 until n) {
                            val pageRect = text.textPageGetRect(r) ?: continue
                            val dev = page.mapRectToDevice(0, 0, refW, refH, 0, pageRect)
                            rects += RectF(
                                minOf(dev.left, dev.right) / refW.toFloat(),
                                minOf(dev.top, dev.bottom) / refH.toFloat(),
                                maxOf(dev.left, dev.right) / refW.toFloat(),
                                maxOf(dev.top, dev.bottom) / refH.toFloat(),
                            )
                        }
                        hits += SearchHit(i, rects)
                    }
                } finally {
                    find.closeFind()
                }
            } finally {
                text.safeClose()
                page.safeClose()
            }
        }
        return hits
    }

    override fun close() {
        if (closed) return
        closed = true
        doc.safeClose()
        runCatching { pfd.close() }
    }

    companion object {
        private const val REF = 10_000
    }
}
