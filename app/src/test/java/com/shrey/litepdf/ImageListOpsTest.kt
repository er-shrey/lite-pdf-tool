package com.shrey.litepdf

import com.google.common.truth.Truth.assertThat
import com.shrey.litepdf.imagetopdf.ImageListOps
import org.junit.Test

class ImageListOpsTest {
    @Test fun prdBatchExample() {
        // PRD FR-07: A,B,C -> reorder B,A,C -> add D,E -> B,A,C,D,E -> reorder B,D,A,C,E
        var list = ImageListOps.append(emptyList(), listOf("A", "B", "C")) { it }.list
        list = ImageListOps.move(list, 1, 0)
        assertThat(list).containsExactly("B", "A", "C").inOrder()
        list = ImageListOps.append(list, listOf("D", "E")) { it }.list
        assertThat(list).containsExactly("B", "A", "C", "D", "E").inOrder()
        list = ImageListOps.move(list, 3, 1)
        assertThat(list).containsExactly("B", "D", "A", "C", "E").inOrder()
    }

    @Test fun duplicatesSkippedAndCounted() {
        val r = ImageListOps.append(listOf("A", "B"), listOf("B", "C", "C")) { it }
        assertThat(r.list).containsExactly("A", "B", "C").inOrder()
        assertThat(r.added).isEqualTo(1)
        assertThat(r.duplicates).isEqualTo(2)
    }

    @Test fun emptyBatchKeepsSelection() {
        val r = ImageListOps.append(listOf("A"), emptyList()) { it }
        assertThat(r.list).containsExactly("A")
    }

    @Test fun moveOutOfRangeIsIgnored() {
        assertThat(ImageListOps.move(listOf("A", "B"), 0, 5)).containsExactly("A", "B").inOrder()
    }
}
