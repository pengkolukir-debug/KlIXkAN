package com.cowalskiiw2026.autoklix.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import com.cowalskiiw2026.autoklix.R
import com.cowalskiiw2026.autoklix.model.ClickPoint
import com.cowalskiiw2026.autoklix.model.PointType
import com.cowalskiiw2026.autoklix.util.PrefsManager
import kotlin.math.abs

/**
 * Mengelola semua window overlay: ikon melayang (kontroler utama), mini-menu
 * ikon-ikon aksi, marker titik bernomor, dan garis panduan gulir. Semua
 * window ini murni tampilan; logika data (menyimpan titik dst.) didelegasikan
 * lewat [Callbacks] ke FloatingControllerService.
 *
 * Semua pemanggilan WindowManager.addView dibungkus try-catch (lewat
 * [safeAddView]) supaya bila izin "Tampil di atas aplikasi lain" belum ada,
 * aplikasi TIDAK crash — cukup gagal senyap dan melapor lewat [Callbacks.onOverlayError].
 */
class FloatingMenuManager(
    private val context: Context,
    private val prefs: PrefsManager,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onRequestStartStop()
        fun isBotRunning(): Boolean
        fun onPointPlaced(type: PointType, x: Float, y: Float)
        fun onSwipePlaced(x1: Float, y1: Float, x2: Float, y2: Float)
        fun onDeletePointRequested(pointId: Long)
        fun onMarkerDragged(pointId: Long, x: Float, y: Float)
        fun onMarkerTapped(pointId: Long)
        fun onOpenSettings()
        fun onOpenPresetSettings()
        fun onCloseOverlay()
        fun onOverlayError()
        fun onBlockedWhileRunning()
    }

    private enum class Mode { IDLE, MENU_OPEN, PLACE_TAP, PLACE_LONG, PLACE_TEXT, PLACE_SWIPE_1, PLACE_SWIPE_2, DELETE }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val overlayType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private var mode = Mode.IDLE
    private var pendingSwipeStart: PointF? = null
    /**
     * true selama bot sedang berjalan. Saat true: (a) semua window marker
     * dibuat FLAG_NOT_TOUCHABLE agar gestur yang di-dispatch bot ke koordinat
     * yang sama tidak "termakan" oleh marker/menu overlay kita sendiri dan
     * benar-benar sampai ke aplikasi sasaran di bawahnya, dan (b) semua mode
     * penempatan/penghapusan/geser titik dikunci — sesuai permintaan agar
     * posisi & waktu titik hanya bisa diedit saat bot TIDAK berjalan.
     */
    private var runningState = false

    // --- root icon (satu-satunya elemen selalu tampil: logo bulat) ---
    private var iconView: View? = null
    private lateinit var iconParams: WindowManager.LayoutParams

    // --- mini menu (muncul saat ikon ditekan) ---
    private var menuView: LinearLayout? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var btnStartStopRef: ImageButton? = null

    // --- marker & garis ---
    private val markerViews = LinkedHashMap<Long, PointMarkerView>()
    private var lineOverlay: SwipeLineView? = null
    private var lineParams: WindowManager.LayoutParams? = null

    // --- penangkap sentuhan sekali-pakai untuk mode penempatan/hapus titik ---
    private var catcherView: View? = null
    private var catcherParams: WindowManager.LayoutParams? = null

    /** @return true bila overlay berhasil ditampilkan. */
    fun show(): Boolean {
        val iconOk = addIcon()
        if (!iconOk) {
            callbacks.onOverlayError()
            return false
        }
        addLineOverlay()
        return true
    }

    fun destroy() {
        closeMenuIfOpen()
        removeCatcher()
        markerViews.values.forEach { safeRemoveView(it) }
        markerViews.clear()
        lineOverlay?.let { safeRemoveView(it) }
        iconView?.let { safeRemoveView(it) }
        iconView = null
    }

    fun refreshPoints(points: List<ClickPoint>) {
        val currentIds = points.map { it.id }.toSet()
        val toRemove = markerViews.keys.filterNot { it in currentIds }
        toRemove.forEach { id -> markerViews.remove(id)?.let { safeRemoveView(it) } }
        points.forEach { point -> upsertMarker(point) }
        lineOverlay?.lines = points.filter { it.type == PointType.SWIPE && it.x2 != null && it.y2 != null }
            .map { SwipeLineView.Line(PointF(it.x, it.y), PointF(it.x2!!, it.y2!!)) }
    }

    fun applySettingsChanged() {
        markerViews.values.forEach {
            it.sizeScale = prefs.pointSizeScale
            it.opacity = prefs.pointOpacity
        }
        iconView?.let {
            it.scaleX = prefs.menuSizeScale
            it.scaleY = prefs.menuSizeScale
            it.alpha = prefs.menuOpacity
        }
    }

    /** Perbarui ikon Start/Stop di mini-menu tanpa perlu menutup/membuka ulang menu. */
    fun updateStartStopIcon(running: Boolean) {
        btnStartStopRef?.setImageResource(if (running) R.drawable.ic_stop else R.drawable.ic_start)
    }

    /**
     * Dipanggil Service setiap kali status bot berubah. Mengunci/membuka
     * kembali interaksi titik (geser, tap-edit, tambah, hapus) dan membuat
     * window marker tembus-sentuh saat berjalan supaya gestur bot sampai
     * ke aplikasi sasaran, bukan ke overlay kita sendiri.
     */
    fun setRunningState(running: Boolean) {
        runningState = running
        if (running && mode != Mode.IDLE && mode != Mode.MENU_OPEN) {
            cancelActiveMode()
        }
        markerViews.values.forEach { marker ->
            val p = marker.layoutParams as? WindowManager.LayoutParams ?: return@forEach
            p.flags = if (running) {
                p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            safeUpdateView(marker, p)
        }
    }

    // ---------------------------------------------------------------- helpers aman

    private fun safeAddView(view: View, params: WindowManager.LayoutParams): Boolean {
        return try {
            windowManager.addView(view, params)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun safeUpdateView(view: View, params: WindowManager.LayoutParams) {
        try { windowManager.updateViewLayout(view, params) } catch (_: Exception) { }
    }

    private fun safeRemoveView(view: View) {
        try { windowManager.removeView(view) } catch (_: Exception) { }
    }

    // ---------------------------------------------------------------- icon

    @SuppressLint("ClickableViewAccessibility")
    private fun addIcon(): Boolean {
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_floating_icon, null)
        iconParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.lastMenuX
            y = prefs.lastMenuY
        }
        view.alpha = prefs.menuOpacity
        view.scaleX = prefs.menuSizeScale
        view.scaleY = prefs.menuSizeScale

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = iconParams.x; startY = iconParams.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) dragging = true
                    if (dragging) {
                        iconParams.x = startX + dx
                        iconParams.y = startY + dy
                        safeUpdateView(view, iconParams)
                        repositionMenuNearIcon()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        onIconTapped()
                    } else {
                        prefs.lastMenuX = iconParams.x
                        prefs.lastMenuY = iconParams.y
                    }
                    true
                }
                else -> false
            }
        }
        val ok = safeAddView(view, iconParams)
        if (ok) iconView = view
        return ok
    }

    private fun onIconTapped() {
        when (mode) {
            Mode.MENU_OPEN -> closeMenuIfOpen()
            Mode.PLACE_SWIPE_1, Mode.PLACE_SWIPE_2, Mode.PLACE_TAP, Mode.PLACE_LONG, Mode.PLACE_TEXT, Mode.DELETE -> cancelActiveMode()
            else -> openMenu()
        }
    }

    // ---------------------------------------------------------------- mini menu

    @SuppressLint("ClickableViewAccessibility")
    private fun openMenu() {
        closeMenuIfOpen()
        mode = Mode.MENU_OPEN
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_menu, null) as LinearLayout
        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val btnStartStop = view.findViewById<ImageButton>(R.id.btnStartStop)
        btnStartStop.setImageResource(if (callbacks.isBotRunning()) R.drawable.ic_stop else R.drawable.ic_start)
        btnStartStop.setOnClickListener {
            callbacks.onRequestStartStop()
            // ikon diperbarui lewat updateStartStopIcon() yang dipanggil Service saat status bot berubah;
            // menu TIDAK ditutup supaya pengguna langsung melihat perubahan & bisa menekan lagi untuk stop.
        }
        btnStartStopRef = btnStartStop

        view.findViewById<ImageButton>(R.id.btnAddTap).setOnClickListener { tryEnterPlacementMode(Mode.PLACE_TAP) }
        view.findViewById<ImageButton>(R.id.btnAddLongPress).setOnClickListener { tryEnterPlacementMode(Mode.PLACE_LONG) }
        view.findViewById<ImageButton>(R.id.btnAddSwipe).setOnClickListener { tryEnterPlacementMode(Mode.PLACE_SWIPE_1) }
        view.findViewById<ImageButton>(R.id.btnAddText).setOnClickListener { tryEnterPlacementMode(Mode.PLACE_TEXT) }
        view.findViewById<ImageButton>(R.id.btnDeleteMode).setOnClickListener { tryEnterPlacementMode(Mode.DELETE) }
        view.findViewById<ImageButton>(R.id.btnPresetSettings).setOnClickListener { callbacks.onOpenPresetSettings(); closeMenuIfOpen() }
        view.findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { callbacks.onOpenSettings(); closeMenuIfOpen() }
        view.findViewById<ImageButton>(R.id.btnCloseOverlay).setOnClickListener { callbacks.onCloseOverlay() }

        if (safeAddView(view, menuParams!!)) {
            menuView = view
            repositionMenuNearIcon()
        } else {
            mode = Mode.IDLE
        }
    }

    private fun repositionMenuNearIcon() {
        val v = menuView ?: return
        val p = menuParams ?: return
        p.x = iconParams.x
        p.y = iconParams.y + (60 * context.resources.displayMetrics.density).toInt()
        safeUpdateView(v, p)
    }

    private fun closeMenuIfOpen() {
        menuView?.let { safeRemoveView(it) }
        menuView = null
        btnStartStopRef = null
        if (mode == Mode.MENU_OPEN) mode = Mode.IDLE
    }

    // ---------------------------------------------------------------- placement / delete mode

    private fun tryEnterPlacementMode(target: Mode) {
        if (runningState) {
            closeMenuIfOpen()
            callbacks.onBlockedWhileRunning()
            return
        }
        enterPlacementMode(target)
    }

    private fun enterPlacementMode(target: Mode) {
        closeMenuIfOpen()
        mode = target
        pendingSwipeStart = null
        addCatcher()
    }

    private fun cancelActiveMode() {
        mode = Mode.IDLE
        pendingSwipeStart = null
        removeCatcher()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addCatcher() {
        removeCatcher()
        val v = View(context)
        catcherParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        v.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                handleCatcherTap(event.rawX, event.rawY)
            }
            true
        }
        if (safeAddView(v, catcherParams!!)) catcherView = v
    }

    private fun removeCatcher() {
        catcherView?.let { safeRemoveView(it) }
        catcherView = null
    }

    private fun handleCatcherTap(x: Float, y: Float) {
        when (mode) {
            Mode.PLACE_TAP -> { callbacks.onPointPlaced(PointType.TAP, x, y); cancelActiveMode() }
            Mode.PLACE_LONG -> { callbacks.onPointPlaced(PointType.LONG_PRESS, x, y); cancelActiveMode() }
            Mode.PLACE_TEXT -> { callbacks.onPointPlaced(PointType.RANDOM_TEXT, x, y); cancelActiveMode() }
            Mode.PLACE_SWIPE_1 -> {
                pendingSwipeStart = PointF(x, y)
                mode = Mode.PLACE_SWIPE_2
            }
            Mode.PLACE_SWIPE_2 -> {
                val start = pendingSwipeStart
                if (start != null) callbacks.onSwipePlaced(start.x, start.y, x, y)
                cancelActiveMode()
            }
            Mode.DELETE -> {
                // Hanya menghapus SATU titik per penekanan tombol hapus, lalu langsung keluar dari mode hapus.
                val hit = findMarkerNear(x, y)
                if (hit != null) callbacks.onDeletePointRequested(hit)
                cancelActiveMode()
            }
            else -> Unit
        }
    }

    private fun findMarkerNear(x: Float, y: Float): Long? {
        val density = context.resources.displayMetrics.density
        val threshold = 32 * density
        for ((id, marker) in markerViews) {
            val loc = IntArray(2)
            marker.getLocationOnScreen(loc)
            val cx = loc[0] + marker.width / 2f
            val cy = loc[1] + marker.height / 2f
            if (abs(cx - x) <= threshold && abs(cy - y) <= threshold) return id
        }
        return null
    }

    // ---------------------------------------------------------------- markers

    @SuppressLint("ClickableViewAccessibility")
    private fun upsertMarker(point: ClickPoint) {
        val existing = markerViews[point.id]
        if (existing != null) {
            existing.orderNumber = point.order + 1
            existing.pointType = point.type
            existing.sizeScale = point.pointSizeOverride ?: prefs.pointSizeScale
            existing.opacity = point.pointOpacityOverride ?: prefs.pointOpacity
            val p = existing.layoutParams as? WindowManager.LayoutParams
            if (p != null) {
                p.x = (point.x - existing.width / 2f).toInt()
                p.y = (point.y - existing.height / 2f).toInt()
                safeUpdateView(existing, p)
            }
            return
        }
        val marker = PointMarkerView(context)
        marker.orderNumber = point.order + 1
        marker.pointType = point.type
        marker.sizeScale = point.pointSizeOverride ?: prefs.pointSizeScale
        marker.opacity = point.pointOpacityOverride ?: prefs.pointOpacity

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                (if (runningState) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (point.x - 28 * context.resources.displayMetrics.density).toInt()
            y = (point.y - 28 * context.resources.displayMetrics.density).toInt()
        }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragged = false
        marker.setOnTouchListener { _, event ->
            // Saat mode hapus/tempat/menu aktif, ATAU bot sedang berjalan, marker tidak menangani
            // sentuhan sendiri (saat berjalan, window-nya juga sudah FLAG_NOT_TOUCHABLE sebagai
            // penjagaan utama; cek mode di sini sebagai lapis pertahanan kedua).
            if (runningState || mode == Mode.DELETE || mode == Mode.MENU_OPEN ||
                mode == Mode.PLACE_TAP || mode == Mode.PLACE_LONG || mode == Mode.PLACE_TEXT ||
                mode == Mode.PLACE_SWIPE_1 || mode == Mode.PLACE_SWIPE_2
            ) {
                return@setOnTouchListener false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) dragged = true
                    if (dragged) {
                        params.x = startX + dx
                        params.y = startY + dy
                        safeUpdateView(marker, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        val newCx = params.x + marker.width / 2f
                        val newCy = params.y + marker.height / 2f
                        callbacks.onMarkerDragged(point.id, newCx, newCy)
                    } else {
                        // Tap singkat (tanpa geser) pada titik yang sudah ada -> buka dialog edit jeda/durasi.
                        callbacks.onMarkerTapped(point.id)
                    }
                    true
                }
                else -> false
            }
        }

        if (safeAddView(marker, params)) markerViews[point.id] = marker
    }

    // ---------------------------------------------------------------- swipe lines

    private fun addLineOverlay() {
        val v = SwipeLineView(context)
        lineParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        if (safeAddView(v, lineParams!!)) lineOverlay = v
    }
}
