package com.cowalskiiw2026.autoklix.model

/**
 * Preset berisi kumpulan titik + aturan pengulangan.
 * [repeatCount] = 0 berarti tanpa batas jumlah pengulangan.
 * [repeatDurationMs] = 0 berarti tanpa batas durasi (dikonversi dari menit/jam saat disimpan).
 */
data class Preset(
    val id: Long = 0,
    var name: String,
    var repeatCount: Int = 0,
    var repeatDurationMs: Long = 0,
    var points: List<ClickPoint> = emptyList()
)
