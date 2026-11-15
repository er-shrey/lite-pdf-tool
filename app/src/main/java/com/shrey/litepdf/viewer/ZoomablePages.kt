package com.shrey.litepdf.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.shrey.litepdf.core.pdfview.PageDims
import com.shrey.litepdf.core.pdfview.SearchHit
import kotlin.math.roundToInt

/**
 * Zoom is a real layout change: pages are laid out [zoom] times the viewport width, so vertical
 * scrolling, page tracking and rendering resolution all stay correct at every zoom level.
 * Horizontal panning is our own offset; vertical scrolling belongs to the LazyColumn.
 */
@Stable
class ZoomState {
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    /** Zoom the pages are rendered at; follows [zoom] once a gesture settles. */
    var renderZoom by mutableFloatStateOf(1f)
    var viewportWidth by mutableFloatStateOf(0f)

    fun clampPan() {
        panX = panX.coerceIn(0f, (viewportWidth * (zoom - 1f)).coerceAtLeast(0f))
    }

    companion object {
        const val MIN = 1f
        const val MAX = 5f
        const val DOUBLE_TAP = 2.5f
    }
}

/** Moves the list so the content under [focus] stays under the finger after zooming by [k]. */
private fun applyZoom(state: ZoomState, list: LazyListState, focus: Offset, newZoom: Float, pan: Offset = Offset.Zero) {
    val target = newZoom.coerceIn(ZoomState.MIN, ZoomState.MAX)
    val k = target / state.zoom
    val items = list.layoutInfo.visibleItemsInfo
    val anchor = items.lastOrNull { it.offset <= focus.y } ?: items.firstOrNull()
    if (anchor != null) {
        val within = focus.y - anchor.offset
        list.requestScrollToItem(anchor.index, (within * k - (focus.y + pan.y)).roundToInt())
    }
    state.panX = (state.panX + focus.x) * k - (focus.x + pan.x)
    state.zoom = target
    state.clampPan()
}

@Composable
fun ZoomablePages(
    pages: List<PageDims>,
    listState: LazyListState,
    zoomState: ZoomState,
    hits: List<SearchHit>,
    currentHit: Int,
    loadBitmap: suspend (page: Int, width: Int) -> Bitmap?,
    cachedBitmap: (page: Int, width: Int) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF8A8D93))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinched = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            pinched = true
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            val c = event.calculateCentroid(useCurrent = true)
                            if (c != Offset.Unspecified && (z != 1f || pan != Offset.Zero)) {
                                applyZoom(zoomState, listState, c, zoomState.zoom * z, pan)
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && !pinched && zoomState.zoom > 1f) {
                            val ch = event.changes.first { it.pressed }
                            zoomState.panX -= ch.position.x - ch.previousPosition.x
                            zoomState.clampPan()
                        }
                    } while (event.changes.any { it.pressed })
                    zoomState.renderZoom = zoomState.zoom
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { p ->
                    val target = if (zoomState.zoom > 1.05f) 1f else ZoomState.DOUBLE_TAP
                    applyZoom(zoomState, listState, p, target)
                    zoomState.renderZoom = zoomState.zoom
                })
            },
    ) {
        val vw = constraints.maxWidth.toFloat()
        zoomState.viewportWidth = vw
        val contentW = (vw * zoomState.zoom).roundToInt().coerceAtLeast(1)
        // Rendering resolution: the settled on-screen width, capped so one page stays under ~48 MB.
        val renderW = (vw * zoomState.renderZoom).roundToInt().coerceAtLeast(1)

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.layout { measurable, c ->
                val p = measurable.measure(Constraints.fixed(contentW, c.maxHeight))
                layout(c.maxWidth, c.maxHeight) { p.place(-zoomState.panX.roundToInt(), 0) }
            },
        ) {
            items(pages.size, key = { it }) { index ->
                val dims = pages[index]
                val width = cappedWidth(renderW, dims)
                PageItem(
                    index = index,
                    dims = dims,
                    renderWidth = width,
                    hits = hits,
                    currentHit = currentHit,
                    loadBitmap = loadBitmap,
                    cachedBitmap = cachedBitmap,
                )
            }
        }
    }
}

private const val MAX_PAGE_PIXELS = 12_000_000f

private fun cappedWidth(width: Int, dims: PageDims): Int {
    val h = width / dims.aspect
    val area = width * h
    return if (area <= MAX_PAGE_PIXELS) width
    else (width * kotlin.math.sqrt(MAX_PAGE_PIXELS / area)).toInt().coerceAtLeast(1)
}

@Composable
private fun PageItem(
    index: Int,
    dims: PageDims,
    renderWidth: Int,
    hits: List<SearchHit>,
    currentHit: Int,
    loadBitmap: suspend (Int, Int) -> Bitmap?,
    cachedBitmap: (Int, Int) -> Bitmap?,
) {
    var bitmap by remember(index) { mutableStateOf(cachedBitmap(index, renderWidth)) }
    LaunchedEffect(index, renderWidth) {
        if (bitmap?.width != renderWidth) loadBitmap(index, renderWidth)?.let { bitmap = it }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(dims.aspect)
            .background(Color.White)
            .semantics { contentDescription = "Page ${index + 1}" },
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        val pageHits = hits.withIndex().filter { it.value.page == index }
        if (pageHits.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                for ((i, hit) in pageHits) {
                    val color = if (i == currentHit) Color(0x88FF8F00) else Color(0x55FFD600)
                    for (r in hit.rects) {
                        drawRect(
                            color,
                            topLeft = Offset(r.left * size.width, r.top * size.height),
                            size = Size(r.width() * size.width, r.height() * size.height),
                        )
                    }
                }
            }
        }
    }
}
