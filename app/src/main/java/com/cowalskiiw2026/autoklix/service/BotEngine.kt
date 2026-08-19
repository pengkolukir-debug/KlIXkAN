package com.cowalskiiw2026.autoklix.service

import com.cowalskiiw2026.autoklix.model.ClickPoint
import com.cowalskiiw2026.autoklix.model.Preset
import com.cowalskiiw2026.autoklix.model.PointType
import com.cowalskiiw2026.autoklix.util.RandomTextGenerator
import kotlinx.coroutines.*

/**
 * Menjalankan urutan titik pada sebuah preset secara berulang, sesuai
 * urutan [ClickPoint.order] (titik pertama dibuat = pertama dijalankan),
 * menghormati delay tiap titik, dan berhenti otomatis berdasarkan
 * repeatCount dan/atau repeatDurationMs (mana yang tercapai lebih dulu).
 */
class BotEngine(private val service: AutoKlixAccessibilityService) {

    private var job: Job? = null
    // PENTING: pakai Dispatchers.Main karena callback status (onStateChanged/onPointExecuted)
    // mengubah tampilan View (ikon menu melayang). Mengubah View dari thread lain (mis. Default)
    // menyebabkan CalledFromWrongThreadException -> aplikasi crash saat bot dijalankan.
    // Gesture (dispatchGesture) & delay() tetap non-blocking walau berjalan di Main dispatcher.
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var onStateChanged: ((running: Boolean) -> Unit)? = null
    var onPointExecuted: ((index: Int, total: Int) -> Unit)? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start(preset: Preset) {
        if (isRunning) stop()
        val points = preset.points.sortedBy { it.order }
        if (points.isEmpty()) return

        val startTime = System.currentTimeMillis()
        job = scope.launch {
            onStateChanged?.invoke(true)
            var iteration = 0
            try {
                while (isActive) {
                    for ((idx, point) in points.withIndex()) {
                        if (!isActive) break
                        executePoint(point)
                        onPointExecuted?.invoke(idx, points.size)
                        delay(point.delayAfterMs)
                    }
                    iteration++
                    if (preset.repeatCount in 1..iteration) break
                    if (preset.repeatDurationMs in 1..Long.MAX_VALUE &&
                        System.currentTimeMillis() - startTime >= preset.repeatDurationMs
                    ) break
                }
            } finally {
                onStateChanged?.invoke(false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        onStateChanged?.invoke(false)
    }

    private suspend fun executePoint(point: ClickPoint) = suspendCancellableCoroutine<Unit> { cont ->
        val done: (Boolean) -> Unit = { if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
        try {
            when (point.type) {
                PointType.TAP -> service.performTap(point.x, point.y, done)
                PointType.LONG_PRESS -> service.performLongPress(point.x, point.y, point.actionDurationMs, done)
                PointType.SWIPE -> service.performSwipe(
                    point.x, point.y,
                    point.x2 ?: point.x, point.y2 ?: point.y,
                    point.actionDurationMs, done
                )
                PointType.RANDOM_TEXT -> service.performRandomTextInput(point.x, point.y, RandomTextGenerator.next(), done)
            }
        } catch (e: Exception) {
            // Jangan biarkan satu titik yang gagal menghentikan/meng-crash seluruh bot;
            // lewati titik ini dan lanjut ke titik berikutnya.
            done(false)
        }
    }
}
