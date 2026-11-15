package com.shrey.litepdf.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shrey.litepdf.core.pdfview.OpenPdf
import com.shrey.litepdf.core.ui.Share
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: ViewerViewModel, onBack: () -> Unit) {
    val load by vm.load.collectAsState()
    val search by vm.search.collectAsState()
    val context = LocalContext.current
    val ready = load as? LoadState.Ready
    val focusManager = LocalFocusManager.current

    // Back closes search first, then the viewer.
    BackHandler(enabled = search.active) { vm.closeSearch() }

    Scaffold(
        topBar = {
            if (search.active && ready != null) {
                SearchBar(
                    query = search.query,
                    onQuery = vm::setQuery,
                    onSubmit = {
                        focusManager.clearFocus()
                        vm.runSearch()
                    },
                    status = searchStatus(search),
                    canStep = search.hits.isNotEmpty(),
                    onPrev = { vm.step(-1) },
                    onNext = { vm.step(1) },
                    onClose = vm::closeSearch,
                )
            } else {
                TopAppBar(
                    title = { Text(vm.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        if (ready != null) {
                            IconButton(onClick = vm::openSearch) { Icon(Icons.Default.Search, "Search text") }
                            IconButton(onClick = { vm.uri?.let { Share.pdf(context, it, vm.fileName) } }) {
                                Icon(Icons.Default.Share, "Share PDF")
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = load) {
                LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is LoadState.Failed -> Message(Icons.Default.ErrorOutline, "Can't open this PDF", s.message)
                is LoadState.NeedsPassword -> PasswordPrompt(s.wrong, vm::submitPassword, onBack)
                is LoadState.Ready -> Reader(s.pdf, vm, search)
            }
        }
    }
}

private fun searchStatus(s: SearchState): String = when {
    s.running -> "Searching…"
    s.noText -> "No searchable text"
    s.searched && s.hits.isEmpty() -> "No matches"
    s.hits.isNotEmpty() -> "${s.current + 1} of ${s.hits.size}"
    else -> ""
}

@Composable
private fun Reader(pdf: OpenPdf, vm: ViewerViewModel, search: SearchState) {
    val listState = rememberLazyListState()
    val zoom = remember { ZoomState() }
    var goTo by rememberSaveable { mutableStateOf(false) }

    // The page under the middle of the screen.
    val currentPage by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.lastOrNull { it.offset <= mid }?.index ?: listState.firstVisibleItemIndex
        }
    }

    // The page chip fades out when the reader is idle (clean screenshots) and comes back on any
    // scroll, zoom, pan or tap. The delay honours the user's accessibility timeout setting.
    var chipsVisible by remember { mutableStateOf(true) }
    var taps by remember { mutableIntStateOf(0) }
    val a11y = LocalAccessibilityManager.current
    val hideAfter = remember(a11y) { a11y?.calculateRecommendedTimeoutMillis(1_000, containsControls = true) ?: 1_000 }
    LaunchedEffect(listState.isScrollInProgress, zoom.zoom, zoom.panX, taps, goTo) {
        chipsVisible = true
        if (listState.isScrollInProgress || goTo) return@LaunchedEffect
        delay(hideAfter)
        chipsVisible = false
    }

    // Bring the current search match into view.
    LaunchedEffect(search.current, search.hits) {
        val hit = search.hits.getOrNull(search.current) ?: return@LaunchedEffect
        val r = hit.rects.firstOrNull()
        val viewportH = listState.layoutInfo.viewportSize.height
        val pageH = zoom.viewportWidth * zoom.zoom / pdf.pageSizes[hit.page].aspect
        val y = ((r?.top ?: 0f) * pageH - viewportH / 3f).roundToInt()
        listState.scrollToItem(hit.page, y)
        if (r != null && zoom.zoom > 1f) {
            zoom.panX = r.centerX() * zoom.viewportWidth * zoom.zoom - zoom.viewportWidth / 2
            zoom.clampPan()
        }
    }

    Box(Modifier.fillMaxSize()) {
        ZoomablePages(
            pages = pdf.pageSizes,
            listState = listState,
            zoomState = zoom,
            hits = search.hits,
            currentHit = search.current,
            loadBitmap = vm::render,
            cachedBitmap = vm::cached,
            onTap = { taps++ },
        )
        if (search.running) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(8.dp),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 3.dp,
            ) {
                Text(
                    "Searching page ${search.progressPage + 1} of ${pdf.pageCount}",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        AnimatedVisibility(
            visible = chipsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
        Row(
            Modifier.navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = { goTo = true },
                shape = MaterialTheme.shapes.extraLarge,
                color = Color(0xCC202124),
                contentColor = Color.White,
            ) {
                Text(
                    "${currentPage + 1} / ${pdf.pageCount}",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (zoom.zoom > 1.01f) {
                Surface(
                    onClick = {
                        val page = currentPage
                        zoom.zoom = 1f; zoom.panX = 0f; zoom.renderZoom = 1f
                        listState.requestScrollToItem(page)
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color(0xCC202124),
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.FitScreen, "Fit to width", Modifier.padding(8.dp).size(20.dp))
                }
            }
        }
        }
    }

    if (goTo) {
        GoToPageDialog(pdf.pageCount, onDismiss = { goTo = false }) { page ->
            goTo = false
            listState.requestScrollToItem(page - 1)
        }
    }
}

@Composable
private fun GoToPageDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()
    val valid = page != null && page in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(6) },
                label = { Text("Page (1–$pageCount)") },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(page!!) }),
            )
        },
        confirmButton = { TextButton(onClick = { onGo(page!!) }, enabled = valid) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    status: String,
    canStep: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(64.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close search") }
                TextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text("Search in document") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                )
                Text(status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = onPrev, enabled = canStep) { Icon(Icons.Default.KeyboardArrowUp, "Previous match") }
                IconButton(onClick = onNext, enabled = canStep) { Icon(Icons.Default.KeyboardArrowDown, "Next match") }
            }
        }
    }
}

@Composable
private fun PasswordPrompt(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("This PDF is password-protected", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pw,
            onValueChange = { pw = it },
            label = { Text("Password") },
            singleLine = true,
            isError = wrong,
            supportingText = { if (wrong) Text("Incorrect password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (pw.isNotEmpty()) onSubmit(pw) }),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onSubmit(pw) }, enabled = pw.isNotEmpty()) { Text("Open") }
        }
    }
}

@Composable
fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
