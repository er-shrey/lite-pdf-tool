package com.shrey.litepdf.imagetopdf

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.shrey.litepdf.core.storage.PdfStore

/** Image to PDF landing screen: the PDFs already in Documents/Lite-PDF-Tool, plus "New PDF". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    pdfs: List<PdfStore.Listed>?,
    onNew: () -> Unit,
    onOpen: (PdfStore.Listed) -> Unit,
    onShare: (PdfStore.Listed) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Image to PDF")
                        Text(PdfStore.DISPLAY_LOCATION, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New PDF") },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                pdfs == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                pdfs.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("No PDFs yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "PDFs you create are saved to ${PdfStore.DISPLAY_LOCATION} and listed here.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Text(
                            if (pdfs.size == 1) "1 PDF" else "${pdfs.size} PDFs",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(pdfs, key = { it.uri.toString() }) { pdf ->
                        ListItem(
                            modifier = Modifier.clickable { onOpen(pdf) },
                            leadingContent = { Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = { Text(pdf.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    Formatter.formatShortFileSize(context, pdf.sizeBytes) + " · " +
                                        DateUtils.getRelativeTimeSpanString(
                                            pdf.modifiedMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                                        ),
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onShare(pdf) }) { Icon(Icons.Default.Share, "Share ${pdf.fileName}") }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
