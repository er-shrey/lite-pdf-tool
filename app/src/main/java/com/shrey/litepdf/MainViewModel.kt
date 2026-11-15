package com.shrey.litepdf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.shrey.litepdf.core.pdfgen.PageSize
import com.shrey.litepdf.core.pdfgen.PdfConfiguration
import com.shrey.litepdf.core.storage.FileNames
import com.shrey.litepdf.imagetopdf.ImageListOps
import com.shrey.litepdf.imagetopdf.ImageProbe
import com.shrey.litepdf.imagetopdf.ImageReadException
import com.shrey.litepdf.imagetopdf.ImageToPdfGenerator
import com.shrey.litepdf.imagetopdf.InsufficientStorageException
import com.shrey.litepdf.imagetopdf.PdfGenerationResult
import com.shrey.litepdf.imagetopdf.SelectedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, ARRANGE, PREVIEW, CONFIGURE, GENERATING, SUCCESS }

/**
 * Navigation plus the image-to-PDF session. Everything that matters survives process death via
 * [SavedStateHandle]; nothing is written to disk (no history, no database).
 */
class MainViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    val screen: StateFlow<Screen> = saved.getStateFlow(KEY_SCREEN, Screen.HOME)
    val images: StateFlow<List<SelectedImage>> = saved.getStateFlow(KEY_IMAGES, arrayListOf())
    val pageSize: StateFlow<PageSize> = saved.getStateFlow(KEY_SIZE, PageSize.ORIGINAL)
    val pageNumbers: StateFlow<Boolean> = saved.getStateFlow(KEY_NUMBERS, true)
    val fileName: StateFlow<String> = saved.getStateFlow(KEY_NAME, "")
    val result: StateFlow<PdfGenerationResult?> = saved.getStateFlow(KEY_RESULT, null)

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var job: Job? = null

    init {
        // A generation can't resume after process death; its partial file is cleaned at launch.
        if (screen.value == Screen.GENERATING) go(Screen.CONFIGURE)
        if (screen.value == Screen.SUCCESS && result.value == null) go(Screen.HOME)
    }

    fun go(s: Screen) { saved[KEY_SCREEN] = s }

    private fun setImages(list: List<SelectedImage>) { saved[KEY_IMAGES] = ArrayList(list) }

    fun startImageFlow() = go(Screen.ARRANGE)

    /** New picks are appended after the current sequence; the existing order is never reset. */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _adding.value = true
            val resolver = getApplication<Application>().contentResolver
            val probed = withContext(Dispatchers.IO) { uris.map { it to ImageProbe.probe(resolver, it) } }
            val readable = probed.mapNotNull { it.second }
            val unreadable = probed.size - readable.size
            val r = ImageListOps.append(images.value, readable) { it.uri.toString() }
            setImages(r.list)
            _adding.value = false
            _message.value = listOfNotNull(
                if (r.duplicates > 0) "${r.duplicates} already added, skipped" else null,
                if (unreadable > 0) "$unreadable couldn't be read and weren't added" else null,
            ).joinToString(" · ").ifEmpty { null }
        }
    }

    fun move(from: Int, to: Int) = setImages(ImageListOps.move(images.value, from, to))

    fun rotate(id: String) = setImages(images.value.map { if (it.id == id) it.copy(rotation = (it.rotation + 1) % 4) else it })

    fun remove(id: String) = setImages(images.value.filterNot { it.id == id })

    fun setPageSize(s: PageSize) { saved[KEY_SIZE] = s }
    fun setPageNumbers(on: Boolean) { saved[KEY_NUMBERS] = on }
    fun setFileName(n: String) { saved[KEY_NAME] = n }
    fun consumeMessage() { _message.value = null }
    fun dismissError() { _error.value = null }

    fun generate() {
        val list = images.value
        if (list.isEmpty() || job?.isActive == true) return
        val name = FileNames.sanitize(fileName.value)
        val config = PdfConfiguration(pageSize.value, pageNumbers.value)
        go(Screen.GENERATING)
        _progress.value = 0 to list.size
        job = viewModelScope.launch {
            try {
                val r = ImageToPdfGenerator(getApplication()).generate(list, config, name) { done, total ->
                    _progress.value = done to total
                }
                saved[KEY_RESULT] = r
                go(Screen.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImageReadException) {
                _error.value = "Image ${e.position} (${e.name}) can't be read. Remove it and try again."
                go(Screen.CONFIGURE)
            } catch (e: InsufficientStorageException) {
                _error.value = "Not enough free storage to save this PDF. Free up some space and try again."
                go(Screen.CONFIGURE)
            } catch (e: OutOfMemoryError) {
                _error.value = "An image is too large to process on this device."
                go(Screen.CONFIGURE)
            } catch (e: Exception) {
                _error.value = "The PDF couldn't be created: ${e.message ?: e.javaClass.simpleName}"
                go(Screen.CONFIGURE)
            }
        }
    }

    fun cancelGeneration() {
        job?.cancel()
        go(Screen.CONFIGURE)
    }

    /** "Create another": a clean session. */
    fun reset() {
        setImages(emptyList())
        saved[KEY_NAME] = ""
        saved[KEY_RESULT] = null
        go(Screen.HOME)
    }

    fun defaultName() = FileNames.defaultBaseName()

    companion object {
        private const val KEY_SCREEN = "screen"
        private const val KEY_IMAGES = "images"
        private const val KEY_SIZE = "pageSize"
        private const val KEY_NUMBERS = "pageNumbers"
        private const val KEY_NAME = "fileName"
        private const val KEY_RESULT = "result"
    }
}
