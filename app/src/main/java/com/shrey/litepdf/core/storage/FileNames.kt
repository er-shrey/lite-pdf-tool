package com.shrey.litepdf.core.storage

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Filename rules for generated PDFs (FR-13). Pure Kotlin so it is unit-tested on the JVM. */
object FileNames {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")
    private const val MAX_BASE_LENGTH = 120

    /** `Document_YYYYMMDD_HHMMSS` (without extension). */
    fun defaultBaseName(now: LocalDateTime = LocalDateTime.now()): String = "Document_${now.format(stamp)}"

    /**
     * Turns whatever the user typed into a safe `name.pdf`: strips a trailing `.pdf` (any case),
     * replaces characters Android/FAT storage rejects, trims dots and spaces, caps the length and
     * falls back to [fallback] when nothing usable is left.
     */
    fun sanitize(input: String, fallback: String = defaultBaseName()): String {
        var base = input.trim()
        if (base.endsWith(".pdf", ignoreCase = true)) base = base.dropLast(4)
        base = base.replace(illegal, "_").trim().trim('.', ' ')
        if (base.length > MAX_BASE_LENGTH) base = base.take(MAX_BASE_LENGTH).trimEnd('.', ' ')
        if (base.isEmpty() || base.all { it == '_' }) base = fallback
        return "$base.pdf"
    }

    /** `name.pdf` -> `name (n).pdf`; used when the storage layer has to resolve a clash itself. */
    fun withSuffix(fileName: String, n: Int): String {
        val base = fileName.removeSuffix(".pdf")
        return "$base ($n).pdf"
    }
}
