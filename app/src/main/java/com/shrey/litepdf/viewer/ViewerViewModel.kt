package com.shrey.litepdf.viewer

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shrey.litepdf.core.pdfview.InvalidPdfException
import com.shrey.litepdf.core.pdfview.OpenPdf
import com.shrey.litepdf.core.pdfview.PasswordRequiredException
import com.shrey.litepdf.core.pdfview.PdfEngine
import com.shrey.litepdf.core.pdfview.PdfiumEngine
import com.shrey.litepdf.core.pdfview.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

sealed interface LoadState {
    data object Loading : LoadState
    data class NeedsPassword(val wrong: Boolean) : LoadState
    data class Failed(val message: String) : LoadState
    data class Ready(val pdf: OpenPdf) : LoadState
}

data class SearchState(
    val active: Boolean = false,
    val query: String = "",
    val running: Boolean = false,
    val progressPage: Int = 0,
    val hits: List<SearchHit> = emptyList(),
    val current: Int = -1,
    val searched: Boolean = false,
    val noText: Boolean = false,
)

class ViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val engine: PdfEngine = PdfiumEngine(app)

    private val _load = MutableStateFlow<LoadState>(LoadState.Loading)
    val load: StateFlow<LoadState> = _load

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search

    var uri: Uri? = null
        private set
    var fileName: String = "Document.pdf"
        private set

    private var searchJob: Job? = null

    // Rendered pages, keyed by "page@width"; bounded so large documents can't exhaust memory.
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val renderPermits = Semaphore(2)

    fun open(target: Uri?, password: String? = null) {
        if (target == null) {
            _load.value = LoadState.Failed("No document was provided.")
            return
        }
        if (uri == target && _load.value is LoadState.Ready) return
        uri = target
        _load.value = LoadState.Loading
        viewModelScope.launch {
            fileName = withContext(Dispatchers.IO) { queryName(target) } ?: target.lastPathSegment ?: fileName
            _load.value = try {
                LoadState.Ready(engine.open(target, password))
            } catch (e: PasswordRequiredException) {
                LoadState.NeedsPassword(e.wrongPassword)
            } catch (e: InvalidPdfException) {
                LoadState.Failed(e.message ?: "This PDF could not be opened.")
            } catch (e: FileNotFoundException) {
                LoadState.Failed("The document is no longer available. It may have been moved or deleted.")
            } catch (e: SecurityException) {
                LoadState.Failed("Lite PDF Tool no longer has access to this document. Open it again from the app that shared it.")
            } catch (e: OutOfMemoryError) {
                LoadState.Failed("This PDF is too large to open on this device.")
            }
        }
    }

    fun submitPassword(password: String) {
        val target = uri ?: return
        uri = null
        open(target, password)
    }

    private fun queryName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    fun cached(page: Int, width: Int): Bitmap? = cache.get("$page@$width")

    suspend fun render(page: Int, width: Int): Bitmap? {
        val pdf = (_load.value as? LoadState.Ready)?.pdf ?: return null
        cached(page, width)?.let { return it }
        return renderPermits.withPermit {
            cached(page, width) ?: try {
                pdf.renderPage(page, width).also { cache.put("$page@$width", it) }
            } catch (e: OutOfMemoryError) {
                cache.evictAll()
                null
            }
        }
    }

    // --- search (FR-04) ---

    fun openSearch() = _search.update { it.copy(active = true) }

    fun closeSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }

    fun setQuery(q: String) = _search.update { it.copy(query = q) }

    fun runSearch() {
        val pdf = (_load.value as? LoadState.Ready)?.pdf ?: return
        val q = _search.value.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        _search.update { it.copy(running = true, hits = emptyList(), current = -1, searched = false, noText = false) }
        searchJob = viewModelScope.launch {
            if (!pdf.hasText()) {
                _search.update { it.copy(running = false, searched = true, noText = true) }
                return@launch
            }
            val hits = pdf.search(q) { p -> _search.update { it.copy(progressPage = p) } }
            _search.update { it.copy(running = false, searched = true, hits = hits, current = if (hits.isEmpty()) -1 else 0) }
        }
    }

    fun step(delta: Int) = _search.update {
        if (it.hits.isEmpty()) it else it.copy(current = Math.floorMod(it.current + delta, it.hits.size))
    }

    override fun onCleared() {
        searchJob?.cancel()
        (_load.value as? LoadState.Ready)?.pdf?.close()
        cache.evictAll()
    }
}
