package com.shrey.litepdf.imagetopdf

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrey.litepdf.core.pdfgen.PageLayout
import com.shrey.litepdf.core.pdfgen.PageSize
import com.shrey.litepdf.core.pdfgen.PdfWriter
import com.shrey.litepdf.core.storage.PdfStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowScaffold(
    title: String,
    onBack: (() -> Unit)?,
    bottom: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { bottom() }
            }
        },
    ) { padding -> content(Modifier.padding(padding).fillMaxSize()) }
}

@Composable
private fun PageSizeChooser(size: PageSize, onSize: (PageSize) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        PageSize.entries.forEachIndexed { i, s ->
            SegmentedButton(
                selected = size == s,
                onClick = { onSize(s) },
                shape = SegmentedButtonDefaults.itemShape(i, PageSize.entries.size),
            ) { Text(if (s == PageSize.ORIGINAL) "Original size" else "A4") }
        }
    }
}

// ---------- Preview (FR-11) ----------

@Composable
fun PreviewScreen(
    images: List<SelectedImage>,
    pageSize: PageSize,
    pageNumbers: Boolean,
    onPageSize: (PageSize) -> Unit,
    onEdit: () -> Unit,
    onContinue: () -> Unit,
) {
    val pager = rememberPagerState { images.size }
    val scope = rememberCoroutineScope()
    FlowScaffold(
        title = "Preview",
        onBack = onEdit,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit order") }
                Button(onClick = onContinue, modifier = Modifier.weight(1f), enabled = images.isNotEmpty()) { Text("Continue") }
            }
        },
    ) { modifier ->
        Column(modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            PageSizeChooser(pageSize, onPageSize)
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 16.dp) { i ->
                PagePreview(images[i], i, images.size, pageSize, pageNumbers, Modifier.fillMaxSize().padding(vertical = 16.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) {
                    Icon(Icons.Default.ChevronLeft, "Previous page")
                }
                Text("Page ${pager.currentPage + 1} of ${images.size}", style = MaterialTheme.typography.labelLarge)
                IconButton(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    enabled = pager.currentPage < images.size - 1,
                ) { Icon(Icons.Default.ChevronRight, "Next page") }
            }
        }
    }
}

/** Draws the page exactly as the generator lays it out, using the same [PageLayout] maths. */
@Composable
private fun PagePreview(img: SelectedImage, index: Int, total: Int, size: PageSize, numbers: Boolean, modifier: Modifier) {
    val spec = remember(img, size) {
        PageLayout.layout(
            img.displayedWidth, img.displayedHeight,
            PageLayout.effectiveDpi(img.displayedDpiX), PageLayout.effectiveDpi(img.displayedDpiY), size,
        )
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = (spec.width / spec.height).toFloat()
        val boxAspect = maxWidth / maxHeight
        val pageW = if (aspect > boxAspect) maxWidth else maxHeight * aspect
        val pageH = pageW / aspect
        val k = pageW / spec.width.toFloat()
        Box(Modifier.size(pageW, pageH).shadow(4.dp).background(Color.White)) {
            val r = spec.image
            Box(
                Modifier
                    .offset(k * r.x.toFloat(), k * (spec.height - r.y - r.height).toFloat())
                    .size(k * r.width.toFloat(), k * r.height.toFloat()),
            ) {
                RotatedThumbnail(img.uri, img.rotation, Modifier.fillMaxSize())
            }
            if (numbers) {
                // Same size and baseline as the generated page (see PdfWriter.pageNumberOps).
                val fontPt = PdfWriter.pageNumberFontSize(spec).toFloat()
                val density = LocalDensity.current
                Text(
                    "${index + 1} / $total",
                    color = Color.DarkGray,
                    fontSize = with(density) { (k * fontPt).toSp() },
                    lineHeight = with(density) { (k * fontPt).toSp() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = k * fontPt * 0.6f)
                        .background(Color.White),
                )
            }
        }
    }
}

// ---------- Configuration (Screen 5) ----------

@Composable
fun ConfigureScreen(
    pageSize: PageSize,
    pageNumbers: Boolean,
    fileName: String,
    defaultName: String,
    pageCount: Int,
    error: String?,
    onPageSize: (PageSize) -> Unit,
    onPageNumbers: (Boolean) -> Unit,
    onFileName: (String) -> Unit,
    onDismissError: () -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit,
) {
    FlowScaffold(
        title = "PDF settings",
        onBack = onBack,
        bottom = {
            Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth(), enabled = pageCount > 0) {
                Text(if (pageCount == 1) "Generate PDF (1 page)" else "Generate PDF ($pageCount pages)")
            }
        },
    ) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Page size", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                SizeOption("Original image size", "Each page matches its image.", pageSize == PageSize.ORIGINAL) { onPageSize(PageSize.ORIGINAL) }
                SizeOption("A4", "Images are centred and fitted without cropping.", pageSize == PageSize.A4) { onPageSize(PageSize.A4) }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().toggleable(pageNumbers, role = Role.Switch, onValueChange = onPageNumbers).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Page numbers", style = MaterialTheme.typography.titleSmall)
                    Text("Adds “1 / N” at the bottom of each page.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = pageNumbers, onCheckedChange = null)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = fileName,
                onValueChange = onFileName,
                label = { Text("File name") },
                placeholder = { Text(defaultName) },
                suffix = { Text(".pdf") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Saved to ${PdfStore.DISPLAY_LOCATION}. If the name is taken, a number is added.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("PDF not created") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun SizeOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Generating ----------

@Composable
fun GeneratingScreen(done: Int, total: Int, onCancel: () -> Unit) {
    FlowScaffold(title = "Creating PDF", onBack = null, bottom = {
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }) { modifier ->
        Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done / total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (done >= total) "Saving…" else "Adding page ${done + 1} of $total",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// ---------- Success (Screen 6) ----------

@Composable
fun SuccessScreen(
    result: PdfGenerationResult,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onAnother: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    FlowScaffold(title = "PDF created", onBack = null, bottom = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Open")
                }
                Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHome, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Home")
                }
                OutlinedButton(onClick = onAnother, modifier = Modifier.weight(1f)) { Text("Create another") }
            }
        }
    }) { modifier ->
        Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(64.dp), tint = Color(0xFF2E7D32))
            Spacer(Modifier.height(12.dp))
            Text("Your PDF is ready", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Detail("File name", result.fileName)
            Detail("Pages", result.pageCount.toString())
            Detail("Size", Formatter.formatShortFileSize(context, result.fileSize))
            Detail("Saved to", result.outputLocation)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
