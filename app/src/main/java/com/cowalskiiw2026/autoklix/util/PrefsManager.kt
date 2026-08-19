package com.cowalskiiw2026.autoklix.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Menyimpan pengaturan ukuran & transparansi titik klik dan menu melayang.
 * Semua nilai skala 0f..1f untuk opacity, dan dp multiplier untuk size (0.5f..2.5f).
 */
class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("autoklix_prefs", Context.MODE_PRIVATE)

    var pointSizeScale: Float
        get() = prefs.getFloat(KEY_POINT_SIZE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_POINT_SIZE, value).apply()

    var pointOpacity: Float
        get() = prefs.getFloat(KEY_POINT_OPACITY, 0.85f)
        set(value) = prefs.edit().putFloat(KEY_POINT_OPACITY, value).apply()

    var menuSizeScale: Float
        get() = prefs.getFloat(KEY_MENU_SIZE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_MENU_SIZE, value).apply()

    var menuOpacity: Float
        get() = prefs.getFloat(KEY_MENU_OPACITY, 0.95f)
        set(value) = prefs.edit().putFloat(KEY_MENU_OPACITY, value).apply()

    var lastMenuX: Int
        get() = prefs.getInt(KEY_MENU_X, 40)
        set(value) = prefs.edit().putInt(KEY_MENU_X, value).apply()

    var lastMenuY: Int
        get() = prefs.getInt(KEY_MENU_Y, 200)
        set(value) = prefs.edit().putInt(KEY_MENU_Y, value).apply()

    companion object {
        private const val KEY_POINT_SIZE = "point_size_scale"
        private const val KEY_POINT_OPACITY = "point_opacity"
        private const val KEY_MENU_SIZE = "menu_size_scale"
        private const val KEY_MENU_OPACITY = "menu_opacity"
        private const val KEY_MENU_X = "menu_x"
        private const val KEY_MENU_Y = "menu_y"
    }
}
