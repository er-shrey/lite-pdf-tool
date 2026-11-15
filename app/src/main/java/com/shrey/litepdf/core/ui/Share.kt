package com.shrey.litepdf.core.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.shrey.litepdf.viewer.ViewerActivity

object Share {
    /** Android Sharesheet with a temporary read grant on the original URI (FR-05, FR-14). */
    fun pdf(context: Context, uri: Uri, name: String?) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            name?.let { putExtra(Intent.EXTRA_TITLE, it) }
            clipData = ClipData.newRawUri(name ?: "PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(send, null))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app can share this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openInViewer(context: Context, uri: Uri) {
        context.startActivity(
            Intent(context, ViewerActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
