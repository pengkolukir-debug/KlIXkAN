package com.cowalskiiw2026.autoklix.model

/**
 * Satu titik aksi dalam preset.
 * [order] menentukan urutan eksekusi: titik pertama dibuat = urutan pertama dijalankan, dst.
 * [x],[y] adalah lokasi utama (untuk SWIPE ini adalah titik awal).
 * [x2],[y2] hanya dipakai untuk SWIPE (titik tujuan).
 * [actionDurationMs] dipakai untuk LONG_PRESS (lama tekan) dan SWIPE (lama gulir).
 * [delayAfterMs] jeda sebelum titik berikutnya mulai dieksekusi.
 */
data class ClickPoint(
    val id: Long = 0,
    val presetId: Long = 0,
    var order: Int,
    val type: PointType,
    var x: Float,
    var y: Float,
    var x2: Float? = null,
    var y2: Float? = null,
    var actionDurationMs: Long = 100L,
    var delayAfterMs: Long = 500L,
    var pointSizeOverride: Float? = null,
    var pointOpacityOverride: Float? = null
)
