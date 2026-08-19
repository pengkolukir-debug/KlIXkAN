package com.cowalskiiw2026.autoklix.ui

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.cowalskiiw2026.autoklix.databinding.ActivitySettingsBinding
import com.cowalskiiw2026.autoklix.service.FloatingControllerService
import com.cowalskiiw2026.autoklix.util.PrefsManager

/**
 * Pengaturan ukuran & transparansi terpisah untuk titik klik dan menu
 * melayang. Perubahan langsung diterapkan ke overlay yang sedang aktif (jika ada).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(applicationContext)

        // skala ukuran disimpan sebagai 0.5f..2.5f, dipetakan ke seekbar 0..100
        binding.seekPointSize.progress = scaleToProgress(prefs.pointSizeScale)
        binding.seekPointOpacity.progress = (prefs.pointOpacity * 100).toInt()
        binding.seekMenuSize.progress = scaleToProgress(prefs.menuSizeScale)
        binding.seekMenuOpacity.progress = (prefs.menuOpacity * 100).toInt()

        binding.seekPointSize.setOnSeekBarChangeListener(simpleListener { p ->
            prefs.pointSizeScale = progressToScale(p); applyLive()
        })
        binding.seekPointOpacity.setOnSeekBarChangeListener(simpleListener { p ->
            prefs.pointOpacity = (p / 100f).coerceIn(0.15f, 1f); applyLive()
        })
        binding.seekMenuSize.setOnSeekBarChangeListener(simpleListener { p ->
            prefs.menuSizeScale = progressToScale(p); applyLive()
        })
        binding.seekMenuOpacity.setOnSeekBarChangeListener(simpleListener { p ->
            prefs.menuOpacity = (p / 100f).coerceIn(0.15f, 1f); applyLive()
        })
    }

    private fun applyLive() {
        FloatingControllerService.runningInstance?.refreshOverlaySettings()
    }

    private fun scaleToProgress(scale: Float): Int = (((scale - 0.5f) / 2.0f) * 100).toInt().coerceIn(0, 100)
    private fun progressToScale(progress: Int): Float = 0.5f + (progress / 100f) * 2.0f

    private fun simpleListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
}
