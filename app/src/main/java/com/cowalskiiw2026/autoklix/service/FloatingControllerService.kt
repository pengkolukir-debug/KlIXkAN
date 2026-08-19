package com.cowalskiiw2026.autoklix.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.cowalskiiw2026.autoklix.AutoKlixApp
import com.cowalskiiw2026.autoklix.R
import com.cowalskiiw2026.autoklix.data.PresetRepository
import com.cowalskiiw2026.autoklix.model.ClickPoint
import com.cowalskiiw2026.autoklix.model.Preset
import com.cowalskiiw2026.autoklix.model.PointType
import com.cowalskiiw2026.autoklix.overlay.FloatingMenuManager
import com.cowalskiiw2026.autoklix.ui.MainActivity
import com.cowalskiiw2026.autoklix.ui.PointConfigActivity
import com.cowalskiiw2026.autoklix.ui.PresetSettingsActivity
import com.cowalskiiw2026.autoklix.util.PermissionUtils
import com.cowalskiiw2026.autoklix.util.PrefsManager
import kotlinx.coroutines.*

/**
 * Service utama untuk kontroler melayang. Memegang preset yang sedang
 * dibangun/dijalankan (in-memory + tersinkron ke database), menampilkan
 * overlay lewat [FloatingMenuManager], dan mengeksekusi bot lewat [BotEngine]
 * yang berbicara ke [AutoKlixAccessibilityService].
 *
 * Semua langkah yang menyentuh window overlay dijaga dengan pengecekan izin
 * & try-catch di [FloatingMenuManager] supaya service ini TIDAK PERNAH crash
 * walau izin belum lengkap — cukup menampilkan Toast lalu berhenti sendiri.
 */
class FloatingControllerService : Service(), FloatingMenuManager.Callbacks {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: PresetRepository
    private lateinit var prefs: PrefsManager
    private lateinit var menuManager: FloatingMenuManager
    private var botEngine: BotEngine? = null

    private var presetId: Long = -1L
    private var presetName: String = "Preset Baru"
    private var repeatCount: Int = 0
    private var repeatDurationMs: Long = 0
    private val points = mutableListOf<ClickPoint>()
    private var tempIdCounter = -1L // id sementara untuk titik yang belum tersimpan ke DB
    private var started = false

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        repository = PresetRepository(applicationContext)
        prefs = PrefsManager(applicationContext)
        menuManager = FloatingMenuManager(applicationContext, prefs, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Cek izin SEBELUM mencoba menampilkan window overlay apa pun, supaya tidak crash.
        if (!PermissionUtils.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_need_overlay, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val incomingId = intent?.getLongExtra(EXTRA_PRESET_ID, -1L) ?: -1L
        val shouldLoad = !started || (incomingId != -1L && incomingId != presetId)
        if (shouldLoad) {
            started = true
            presetId = incomingId
            loadPreset(presetId)
        }
        return START_STICKY
    }

    private fun loadPreset(id: Long) {
        serviceScope.launch {
            if (id > 0) {
                val preset = repository.getFullPreset(id)
                if (preset != null) {
                    presetName = preset.name
                    repeatCount = preset.repeatCount
                    repeatDurationMs = preset.repeatDurationMs
                    points.clear()
                    points.addAll(preset.points)
                }
            } else {
                presetId = -1L
                presetName = "Preset Baru"
                repeatCount = 0
                repeatDurationMs = 0
                points.clear()
            }
            val ok = menuManager.show()
            if (ok) menuManager.refreshPoints(points)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        botEngine?.stop()
        menuManager.destroy()
        serviceScope.cancel()
        if (runningInstance == this) runningInstance = null
        super.onDestroy()
    }

    // ---------------------------------------------------------- FloatingMenuManager.Callbacks

    override fun onRequestStartStop() {
        val service = AutoKlixAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, R.string.toast_need_accessibility, Toast.LENGTH_LONG).show()
            return
        }
        if (points.isEmpty()) {
            Toast.makeText(this, "Tambahkan minimal satu titik dulu sebelum menjalankan bot", Toast.LENGTH_SHORT).show()
            return
        }
        if (botEngine == null) {
            botEngine = BotEngine(service).also { engine ->
                engine.onStateChanged = { running ->
                    menuManager.updateStartStopIcon(running)
                    menuManager.setRunningState(running)
                }
            }
        }
        val engine = botEngine ?: return
        if (engine.isRunning) {
            engine.stop()
        } else {
            val preset = Preset(presetId.coerceAtLeast(0), presetName, repeatCount, repeatDurationMs, points.toList())
            engine.start(preset)
        }
    }

    override fun isBotRunning(): Boolean = botEngine?.isRunning == true

    override fun onPointPlaced(type: PointType, x: Float, y: Float) {
        val orderIndex = points.size
        val newPoint = ClickPoint(
            id = tempIdCounter--,
            presetId = presetId,
            order = orderIndex,
            type = type,
            x = x, y = y,
            actionDurationMs = if (type == PointType.LONG_PRESS) 600L else 100L,
            delayAfterMs = 500L
        )
        points.add(newPoint)
        menuManager.refreshPoints(points)
        serviceScope.launch {
            persistWorkingPreset()
            // Cari ulang titik yang baru saja disimpan lewat posisi urutannya (id lama sudah
            // berubah dari sementara -> id asli database setelah persistWorkingPreset).
            val saved = points.find { it.order == orderIndex }
            if (saved != null) openPointConfig(saved)
        }
    }

    override fun onSwipePlaced(x1: Float, y1: Float, x2: Float, y2: Float) {
        val orderIndex = points.size
        val newPoint = ClickPoint(
            id = tempIdCounter--,
            presetId = presetId,
            order = orderIndex,
            type = PointType.SWIPE,
            x = x1, y = y1, x2 = x2, y2 = y2,
            actionDurationMs = 300L,
            delayAfterMs = 500L
        )
        points.add(newPoint)
        menuManager.refreshPoints(points)
        serviceScope.launch {
            persistWorkingPreset()
            val saved = points.find { it.order == orderIndex }
            if (saved != null) openPointConfig(saved)
        }
    }

    override fun onDeletePointRequested(pointId: Long) {
        if (isBotRunning()) { onBlockedWhileRunning(); return }
        val idx = points.indexOfFirst { it.id == pointId }
        if (idx == -1) return
        points.removeAt(idx)
        points.forEachIndexed { i, p -> if (p.order != i) points[i] = p.copy(order = i) }
        menuManager.refreshPoints(points)
        serviceScope.launch { persistWorkingPreset() }
    }

    override fun onMarkerDragged(pointId: Long, x: Float, y: Float) {
        if (isBotRunning()) { onBlockedWhileRunning(); return }
        val idx = points.indexOfFirst { it.id == pointId }
        if (idx == -1) return
        points[idx] = points[idx].copy(x = x, y = y)
        menuManager.refreshPoints(points)
        serviceScope.launch { persistWorkingPreset() }
    }

    override fun onMarkerTapped(pointId: Long) {
        if (isBotRunning()) { onBlockedWhileRunning(); return }
        val point = points.find { it.id == pointId } ?: return
        openPointConfig(point)
    }

    override fun onOpenSettings() {
        val intent = Intent(this, com.cowalskiiw2026.autoklix.ui.SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onOpenPresetSettings() {
        val intent = Intent(this, PresetSettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(PresetSettingsActivity.EXTRA_CURRENT_PRESET_ID, presetId)
            putExtra(PresetSettingsActivity.EXTRA_CURRENT_NAME, presetName)
            putExtra(PresetSettingsActivity.EXTRA_CURRENT_REPEAT_COUNT, repeatCount)
            putExtra(PresetSettingsActivity.EXTRA_CURRENT_REPEAT_DURATION_MS, repeatDurationMs)
        }
        startActivity(intent)
    }

    override fun onCloseOverlay() {
        stopSelf()
    }

    override fun onOverlayError() {
        Toast.makeText(this, R.string.toast_overlay_failed, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    override fun onBlockedWhileRunning() {
        Toast.makeText(this, R.string.toast_locked_while_running, Toast.LENGTH_SHORT).show()
    }

    private fun openPointConfig(point: ClickPoint) {
        val intent = Intent(this, PointConfigActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(PointConfigActivity.EXTRA_POINT_ID, point.id)
            putExtra(PointConfigActivity.EXTRA_POINT_TYPE, point.type.name)
            putExtra(PointConfigActivity.EXTRA_DELAY_MS, point.delayAfterMs)
            putExtra(PointConfigActivity.EXTRA_ACTION_DURATION_MS, point.actionDurationMs)
        }
        startActivity(intent)
    }

    /** Dipanggil oleh SettingsActivity agar perubahan ukuran/transparansi langsung terlihat. */
    fun refreshOverlaySettings() {
        menuManager.applySettingsChanged()
        menuManager.refreshPoints(points)
    }

    /** Dipanggil oleh PointConfigActivity setelah user menyimpan jeda/durasi sebuah titik. */
    fun applyPointConfig(pointId: Long, delayMs: Long, actionDurationMs: Long) {
        val idx = points.indexOfFirst { it.id == pointId }
        if (idx == -1) return
        points[idx] = points[idx].copy(delayAfterMs = delayMs, actionDurationMs = actionDurationMs)
        serviceScope.launch { persistWorkingPreset() }
    }

    /** Dipanggil oleh PresetSettingsActivity: perbarui nama & aturan pengulangan lalu simpan. */
    fun applyPresetSettings(name: String, newRepeatCount: Int, newRepeatDurationMs: Long) {
        presetName = name
        repeatCount = newRepeatCount
        repeatDurationMs = newRepeatDurationMs
        serviceScope.launch {
            persistWorkingPreset()
            Toast.makeText(this@FloatingControllerService, R.string.toast_preset_saved, Toast.LENGTH_SHORT).show()
        }
    }

    /** Dipanggil oleh PresetSettingsActivity saat user memilih preset lain untuk dimuat ke overlay. */
    fun switchToPreset(newPresetId: Long) {
        loadPreset(newPresetId)
    }

    /** Dipanggil oleh PresetSettingsActivity saat user menekan "Preset Baru". */
    fun startNewPreset() {
        loadPreset(-1L)
    }

    private suspend fun persistWorkingPreset() {
        val preset = Preset(presetId.coerceAtLeast(0), presetName, repeatCount, repeatDurationMs, points.toList())
        val savedId = repository.savePreset(preset)
        presetId = savedId
        val savedPoints = repository.getPointsOnce(savedId)
        points.clear()
        points.addAll(savedPoints)
        menuManager.refreshPoints(points)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AutoKlixApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Kontroler melayang aktif")
            .setSmallIcon(R.drawable.ic_float_logo)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_PRESET_ID = "extra_preset_id"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        private const val NOTIFICATION_ID = 4201

        @Volatile var runningInstance: FloatingControllerService? = null
            private set
    }
}
