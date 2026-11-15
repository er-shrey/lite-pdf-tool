# Third-party notices

Lite PDF Tool is licensed under GPL-3.0. It bundles the following components, all under
licences compatible with GPL-3.0:

| Component | Licence | Use |
|---|---|---|
| [PdfiumAndroidKt](https://github.com/johngray1965/PdfiumAndroidKt) (`io.legere:pdfiumandroid`) | Apache-2.0 | Kotlin/JNI wrapper for PDFium |
| [PDFium](https://pdfium.googlesource.com/pdfium/) (native, shipped inside the wrapper) | BSD-3-Clause | PDF rendering and text search |
| AndroidX / Jetpack Compose | Apache-2.0 | UI |
| Kotlin standard library, kotlinx.coroutines | Apache-2.0 | Language runtime |
| Guava (transitive, via the PDFium wrapper) | Apache-2.0 | Utilities |

Apache-2.0 code can be combined into a GPL-3.0 work; BSD-3-Clause is GPL-compatible.
