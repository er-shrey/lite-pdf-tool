package com.shrey.litepdf

import com.google.common.truth.Truth.assertThat
import com.shrey.litepdf.core.storage.FileNames
import org.junit.Test
import java.time.LocalDateTime

class FileNamesTest {
    @Test fun defaultName() {
        assertThat(FileNames.defaultBaseName(LocalDateTime.of(2026, 11, 15, 6, 50, 0))).isEqualTo("Document_20261115_065000")
    }

    @Test fun appendsPdf() = assertThat(FileNames.sanitize("Invoice")).isEqualTo("Invoice.pdf")

    @Test fun doesNotDoubleExtension() = assertThat(FileNames.sanitize("Invoice.PDF")).isEqualTo("Invoice.pdf")

    @Test fun replacesIllegalCharacters() =
        assertThat(FileNames.sanitize("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("a_b_c_d_e_f_g_h_i_j.pdf")

    @Test fun blankFallsBack() = assertThat(FileNames.sanitize("   ", "Doc")).isEqualTo("Doc.pdf")

    @Test fun onlyIllegalFallsBack() = assertThat(FileNames.sanitize("///", "Doc")).isEqualTo("Doc.pdf")

    @Test fun trimsDotsAndSpaces() = assertThat(FileNames.sanitize(" ..notes.. ")).isEqualTo("notes.pdf")

    @Test fun capsLength() = assertThat(FileNames.sanitize("x".repeat(500)).length).isEqualTo(124)

    @Test fun suffix() = assertThat(FileNames.withSuffix("Invoice.pdf", 2)).isEqualTo("Invoice (2).pdf")
}
