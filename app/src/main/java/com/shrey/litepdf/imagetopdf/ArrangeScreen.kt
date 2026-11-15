package com.shrey.litepdf.imagetopdf

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/** Drag-to-reorder bookkeeping for a LazyColumn whose items are keyed by image id. */
private class ReorderState(val list: LazyListState, val onMove: (Int, Int) -> Unit) {
    var draggingKey by mutableStateOf<Any?>(null)
    var offset by mutableFloatStateOf(0f)

    fun start(key: Any) { draggingKey = key; offset = 0f }
    fun stop() { draggingKey = null; offset = 0f }

    fun drag(dy: Float) {
        offset += dy
        swapIfNeeded()
    }

    fun swapIfNeeded() {
        val key = draggingKey ?: return
        val items = list.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        val center = current.offset + offset + current.size / 2f
        val target = items.firstOrNull {
            it.key != key && center > it.offset && center < it.offset + it.size
        } ?: return
        onMove(current.index, target.index)
        offset -= (target.offset - current.offset)
    }

    /** Positive when the dragged row is near the bottom edge, negative near the top. */
    fun edgeSpeed(): Float {
        val key = draggingKey ?: return 0f
        val info = list.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        val top = item.offset + offset
        val bottom = top + item.size
        val zone = item.size.toFloat()
        return when {
            top < info.viewportStartOffset + zone / 2 -> -12f
            bottom > info.viewportEndOffset - zone / 2 -> 12f
            else -> 0f
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrangeScreen(
    images: List<SelectedImage>,
    adding: Boolean,
    message: String?,
    onMessageShown: () -> Unit,
    onAdd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRotate: (String) -> Unit,
    onRemove: (String) -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val reorder = remember(listState) { ReorderState(listState, onMove) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    // Auto-scroll while a row is dragged near the top or bottom edge.
    LaunchedEffect(reorder.draggingKey) {
        while (reorder.draggingKey != null) {
            val speed = reorder.edgeSpeed()
            if (speed != 0f) {
                val consumed = listState.scrollBy(speed)
                reorder.offset += consumed
                reorder.swapIfNeeded()
            }
            delay(16)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Arrange pages")
                        Text(
                            if (images.size == 1) "1 image" else "${images.size} images",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f), enabled = !adding) {
                        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add images")
                    }
                    Button(onClick = onPreview, modifier = Modifier.weight(1f), enabled = images.isNotEmpty() && !adding) {
                        Text("Preview PDF")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (adding) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (images.isEmpty() && !adding) {
                EmptyState(onAdd)
            } else {
                Text(
                    "Drag the handle to change the page order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(images, key = { _, img -> img.id }) { index, img ->
                        val dragging = reorder.draggingKey == img.id
                        ImageRow(
                            index = index,
                            count = images.size,
                            image = img,
                            dragging = dragging,
                            onRotate = { onRotate(img.id) },
                            onRemove = { onRemove(img.id) },
                            onMove = onMove,
                            handleModifier = Modifier.pointerInput(img.id) {
                                detectDragGestures(
                                    onDragStart = { reorder.start(img.id) },
                                    onDragEnd = { reorder.stop() },
                                    onDragCancel = { reorder.stop() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        reorder.drag(amount.y)
                                    },
                                )
                            },
                            modifier = if (dragging) {
                                Modifier.zIndex(1f).graphicsLayer { translationY = reorder.offset }
                            } else {
                                Modifier.animateItem()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("No images selected", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Pick one or more images. You can add more later.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) { Text("Select images") }
    }
}

@Composable
private fun ImageRow(
    index: Int,
    count: Int,
    image: SelectedImage,
    dragging: Boolean,
    onRotate: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit,
    handleModifier: Modifier,
    modifier: Modifier,
) {
    val actions = buildList {
        if (index > 0) add(CustomAccessibilityAction("Move up") { onMove(index, index - 1); true })
        if (index < count - 1) add(CustomAccessibilityAction("Move down") { onMove(index, index + 1); true })
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (dragging) 8.dp else 0.dp, RoundedCornerShape(12.dp))
            .semantics { customActions = actions },
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (dragging) 6.dp else 1.dp,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                RotatedThumbnail(image.uri, image.rotation, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(image.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Page ${index + 1}" + if (image.rotation != 0) " · rotated ${image.rotation * 90}°" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRotate) { Icon(Icons.Default.RotateRight, "Rotate page ${index + 1}") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove page ${index + 1}") }
            Box(handleModifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DragHandle, "Drag to reorder page ${index + 1}")
            }
        }
    }
}
