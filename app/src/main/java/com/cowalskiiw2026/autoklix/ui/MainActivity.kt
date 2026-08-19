package com.cowalskiiw2026.autoklix.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cowalskiiw2026.autoklix.R
import com.cowalskiiw2026.autoklix.data.PresetRepository
import com.cowalskiiw2026.autoklix.databinding.ActivityMainBinding
import com.cowalskiiw2026.autoklix.model.Preset
import com.cowalskiiw2026.autoklix.service.FloatingControllerService
import com.cowalskiiw2026.autoklix.util.PermissionUtils
import kotlinx.coroutines.launch

/**
 * Halaman utama HANYA untuk: melihat daftar preset tersimpan, memilih salah
 * satu untuk dijalankan (membuka kontroler melayang), atau menghapus preset.
 * Membuat preset baru / mengedit nama / titik / pengulangan semuanya
 * dilakukan lewat kontroler melayang (menu Pengaturan Preset di sana).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PresetRepository
    private lateinit var adapter: PresetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PresetRepository(applicationContext)
        adapter = PresetAdapter(
            onRun = { preset -> launchOverlay(preset.id) },
            onViewDetail = { preset ->
                val intent = Intent(this, PresetDetailActivity::class.java)
                intent.putExtra(PresetDetailActivity.EXTRA_PRESET_ID, preset.id)
                startActivity(intent)
            },
            onDelete = { preset -> lifecycleScope.launch { repository.deletePreset(preset) } }
        )
        binding.rvPresets.layoutManager = LinearLayoutManager(this)
        binding.rvPresets.adapter = adapter

        // FAB langsung membuka kontroler melayang dengan preset kosong ("Preset Baru");
        // penamaan & penyimpanan preset baru dilakukan lewat menu Pengaturan Preset di overlay.
        binding.fabAddPreset.setOnClickListener { launchOverlay(0L) }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnGrantAccessibility.setOnClickListener { PermissionUtils.openAccessibilitySettings(this) }
        binding.btnGrantOverlay.setOnClickListener { PermissionUtils.openOverlaySettings(this) }

        lifecycleScope.launch {
            repository.observePresets().collect { list -> adapter.submit(list) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionBanner()
    }

    private fun refreshPermissionBanner() {
        val accessibilityOk = PermissionUtils.isAccessibilityServiceEnabled(this)
        val overlayOk = PermissionUtils.canDrawOverlays(this)
        binding.permissionBanner.visibility =
            if (accessibilityOk && overlayOk) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnGrantAccessibility.visibility = if (accessibilityOk) android.view.View.GONE else android.view.View.VISIBLE
        binding.btnGrantOverlay.visibility = if (overlayOk) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun launchOverlay(presetId: Long) {
        val overlayOk = PermissionUtils.canDrawOverlays(this)
        val accessibilityOk = PermissionUtils.isAccessibilityServiceEnabled(this)
        if (!overlayOk) {
            Toast.makeText(this, R.string.toast_need_overlay, Toast.LENGTH_LONG).show()
            refreshPermissionBanner()
            return
        }
        if (!accessibilityOk) {
            // Overlay tetap boleh dibuka untuk menyusun titik; hanya Start bot yang butuh aksesibilitas,
            // jadi cukup diingatkan, tidak diblokir sepenuhnya.
            Toast.makeText(this, R.string.toast_need_accessibility, Toast.LENGTH_LONG).show()
        }
        val intent = Intent(this, FloatingControllerService::class.java)
        intent.putExtra(FloatingControllerService.EXTRA_PRESET_ID, presetId)
        startService(intent)
    }
}
