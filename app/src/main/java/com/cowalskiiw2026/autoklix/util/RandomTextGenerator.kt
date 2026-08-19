package com.cowalskiiw2026.autoklix.util

/**
 * Menghasilkan teks acak 2 huruf (a-z) tanpa pengulangan sampai seluruh
 * 676 variasi (26x26) habis terpakai, baru boleh mengulang lagi secara
 * berurutan dari kocokan baru. Thread-safe sederhana dengan synchronized.
 */
object RandomTextGenerator {
    private val alphabet = ('a'..'z').toList()
    private var pool: MutableList<String> = mutableListOf()
    private var cursor = 0

    @Synchronized
    fun next(): String {
        if (pool.isEmpty() || cursor >= pool.size) {
            reshuffle()
        }
        val value = pool[cursor]
        cursor++
        return value
    }

    @Synchronized
    fun reset() {
        pool = mutableListOf()
        cursor = 0
    }

    private fun reshuffle() {
        val all = ArrayList<String>(676)
        for (a in alphabet) {
            for (b in alphabet) {
                all.add("$a$b")
            }
        }
        all.shuffle()
        pool = all
        cursor = 0
    }
}
