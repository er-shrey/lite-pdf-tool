package com.shrey.litepdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.shrey.litepdf.core.ui.LitePdfTheme
import com.shrey.litepdf.core.ui.Share
import com.shrey.litepdf.home.HomeScreen
import com.shrey.litepdf.imagetopdf.ArrangeScreen
import com.shrey.litepdf.imagetopdf.ConfigureScreen
import com.shrey.litepdf.imagetopdf.GeneratingScreen
import com.shrey.litepdf.imagetopdf.PreviewScreen
import com.shrey.litepdf.imagetopdf.SuccessScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent {
            LitePdfTheme {
                Surface { App(vm, version) }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun App(vm: MainViewModel, version: String) {
    val context = LocalContext.current
    val screen by vm.screen.collectAsState()
    val images by vm.images.collectAsState()
    val pageSize by vm.pageSize.collectAsState()
    val numbers by vm.pageNumbers.collectAsState()
    val name by vm.fileName.collectAsState()
    val result by vm.result.collectAsState()
    val adding by vm.adding.collectAsState()
    val message by vm.message.collectAsState()
    val progress by vm.progress.collectAsState()
    val error by vm.error.collectAsState()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var autoPicked by rememberSaveable { mutableStateOf(false) }

    // System Photo Picker: no media permission; cancelling keeps the current selection.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.addImages(uris)
    }
    val pickImages = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Share.openInViewer(context, uri)
    }

    // Open the picker straight away the first time the flow starts with nothing selected.
    LaunchedEffect(screen) {
        if (screen == Screen.ARRANGE && images.isEmpty() && !autoPicked) {
            autoPicked = true
            pickImages()
        }
        if (screen != Screen.ARRANGE) autoPicked = false
    }

    BackHandler(enabled = screen != Screen.HOME) {
        when (screen) {
            Screen.ARRANGE -> if (images.isEmpty()) vm.go(Screen.HOME) else confirmDiscard = true
            Screen.PREVIEW -> vm.go(Screen.ARRANGE)
            Screen.CONFIGURE -> vm.go(Screen.PREVIEW)
            Screen.GENERATING -> vm.cancelGeneration()
            Screen.SUCCESS -> vm.reset()
            Screen.HOME -> Unit
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            version = version,
            onOpenPdf = { openPdf.launch(arrayOf("application/pdf")) },
            onImagesToPdf = vm::startImageFlow,
        )
        Screen.ARRANGE -> ArrangeScreen(
            images = images,
            adding = adding,
            message = message,
            onMessageShown = vm::consumeMessage,
            onAdd = pickImages,
            onMove = vm::move,
            onRotate = vm::rotate,
            onRemove = vm::remove,
            onPreview = { vm.go(Screen.PREVIEW) },
            onBack = { if (images.isEmpty()) vm.go(Screen.HOME) else confirmDiscard = true },
        )
        Screen.PREVIEW -> PreviewScreen(
            images = images,
            pageSize = pageSize,
            pageNumbers = numbers,
            onPageSize = vm::setPageSize,
            onEdit = { vm.go(Screen.ARRANGE) },
            onContinue = { vm.go(Screen.CONFIGURE) },
        )
        Screen.CONFIGURE -> ConfigureScreen(
            pageSize = pageSize,
            pageNumbers = numbers,
            fileName = name,
            defaultName = rememberSaveable { vm.defaultName() },
            pageCount = images.size,
            error = error,
            onPageSize = vm::setPageSize,
            onPageNumbers = vm::setPageNumbers,
            onFileName = vm::setFileName,
            onDismissError = vm::dismissError,
            onGenerate = vm::generate,
            onBack = { vm.go(Screen.PREVIEW) },
        )
        Screen.GENERATING -> GeneratingScreen(progress.first, progress.second, vm::cancelGeneration)
        Screen.SUCCESS -> result?.let { r ->
            SuccessScreen(
                result = r,
                onOpen = { Share.openInViewer(context, r.uri) },
                onShare = { Share.pdf(context, r.uri, r.fileName) },
                onAnother = { vm.reset(); vm.startImageFlow() },
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard selection?") },
            text = { Text("The ${images.size} selected images and their order will be cleared.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.reset() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep") } },
        )
    }
}
