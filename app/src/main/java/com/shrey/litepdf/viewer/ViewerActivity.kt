package com.shrey.litepdf.viewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.shrey.litepdf.core.ui.LitePdfTheme

/**
 * Entry point for PDFs opened from other apps (ACTION_VIEW / ACTION_SEND) and from inside this
 * app. Works without visiting the home screen.
 */
class ViewerActivity : ComponentActivity() {
    private val vm: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.open(documentUri(intent))
        setContent {
            LitePdfTheme {
                ViewerScreen(vm, onBack = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.open(documentUri(intent))
    }

    /** Only content:// URIs are accepted; anything else is rejected rather than trusted. */
    private fun documentUri(intent: Intent?): Uri? {
        val uri = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> intent?.data
        }
        return uri?.takeIf { it.scheme == "content" }
    }
}
