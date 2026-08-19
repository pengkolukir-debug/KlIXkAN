package com.cowalskiiw2026.autoklix.model

/** Jenis titik yang bisa dibuat pengguna. */
enum class PointType {
    TAP,          // klik sekali
    LONG_PRESS,   // tekan tahan (durasi custom)
    SWIPE,        // gulir dari titik awal ke titik tujuan
    RANDOM_TEXT   // bot ketik huruf acak (autofill)
}

enum class TimeUnitOption { MILLISECOND, SECOND }

enum class RepeatDurationUnit { MINUTE, HOUR }

/** Konversi nilai + satuan menjadi milidetik. */
fun toMillis(value: Long, unit: TimeUnitOption): Long = when (unit) {
    TimeUnitOption.MILLISECOND -> value
    TimeUnitOption.SECOND -> value * 1000L
}

fun repeatDurationToMillis(value: Long, unit: RepeatDurationUnit): Long = when (unit) {
    RepeatDurationUnit.MINUTE -> value * 60_000L
    RepeatDurationUnit.HOUR -> value * 3_600_000L
}
