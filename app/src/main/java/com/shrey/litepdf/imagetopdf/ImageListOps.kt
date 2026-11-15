package com.shrey.litepdf.imagetopdf

/** List operations behind incremental selection and reordering (FR-07, FR-08). Pure Kotlin. */
object ImageListOps {
    data class AppendResult<T>(val list: List<T>, val added: Int, val duplicates: Int)

    /** Appends [incoming] after [existing], never replacing it, and skips items already present. */
    fun <T, K> append(existing: List<T>, incoming: List<T>, key: (T) -> K): AppendResult<T> {
        val seen = existing.mapTo(HashSet(), key)
        val result = existing.toMutableList()
        var dupes = 0
        for (item in incoming) {
            if (seen.add(key(item))) result += item else dupes++
        }
        return AppendResult(result, result.size - existing.size, dupes)
    }

    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return m
    }
}
