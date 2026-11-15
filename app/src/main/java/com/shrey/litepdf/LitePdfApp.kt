package com.shrey.litepdf

import android.app.Application
import com.shrey.litepdf.core.storage.PdfStore

class LitePdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Remove half-written output from a run that was killed mid-generation.
        Thread { PdfStore.deleteStalePending(this) }.start()
    }
}
