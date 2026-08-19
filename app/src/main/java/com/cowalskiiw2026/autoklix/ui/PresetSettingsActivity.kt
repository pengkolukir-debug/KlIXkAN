package com.cowalskiiw2026.autoklix.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cowalskiiw2026.autoklix.R
import com.cowalskiiw2026.autoklix.data.PresetRepository
import com.cowalskiiw2026.autoklix.model.RepeatDurationUnit
import com.cowalskiiw2026.autoklix.model.repeatDurationToMillis
import com.cowalskiiw2026.autoklix.service.FloatingControllerService
import kotlinx.coroutines.launch

/**
 * Dibuka dari menu melayang (bukan dari halaman utama aplikasi). Di sinilah
 * preset yang sedang dibangun disimpan/diganti nama/diatur pengulangannya,
 * dan di sini juga pengguna bisa berpindah ke preset lain yang sudah
 * tersimpan (langsung dimuat ke overlay yang sedang aktif) tanpa perlu
 * membuka halaman aplikasi utama.
 */
class PresetSettingsActivity : AppCompatActivity() {

    private lateinit var repository: PresetRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preset_settings)
        repository = PresetRepository(applicationContext)

        val currentName = intent.getStringExtra(EXTRA_CURRENT_NAME) ?: "Preset Baru"
        val currentRepeatCount = intent.getIntExtra(EXTRA_CURRENT_REPEAT_COUNT, 0)
        val currentRepeatDurationMs = intent.getLongExtra(EXTRA_CURRENT_REPEAT_DURATION_MS, 0L)

        val etName = findViewById<EditText>(R.id.etPresetName)
        val etRepeatCount = findViewById<EditText>(R.id.etRepeatCount)
        val etRepeatDuration = findViewById<EditText>(R.id.etRepeatDuration)
        val spDurationUnit = findViewById<Spinner>(R.id.spRepeatDurationUnit)

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            arrayOf(getString(R.string.unit_minute), getString(R.string.unit_hour)))
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDurationUnit.adapter = unitAdapter

        etName.setText(currentName)
        etRepeatCount.setText(currentRepeatCount.toString())
        if (currentRepeatDurationMs > 0) {
            val hours = currentRepeatDurationMs / 3_600_000L
            if (hours > 0) {
                etRepeatDuration.setText(hours.toString())
                spDurationUnit.setSelection(1)
            } else {
                etRepeatDuration.setText((currentRepeatDurationMs / 60_000L).toString())
                spDurationUnit.setSelection(0)
            }
        } else {
            etRepeatDuration.setText("0")
        }

        findViewById<Button>(R.id.btnSavePreset).setOnClickListener {
            val name = etName.text.toString().ifBlank { "Preset Tanpa Nama" }
            val repeatCount = etRepeatCount.text.toString().toIntOrNull() ?: 0
            val durationValue = etRepeatDuration.text.toString().toLongOrNull() ?: 0L
            val durationUnit = if (spDurationUnit.selectedItemPosition == 0) RepeatDurationUnit.MINUTE else RepeatDurationUnit.HOUR
            val durationMs = if (durationValue > 0) repeatDurationToMillis(durationValue, durationUnit) else 0L
            FloatingControllerService.runningInstance?.applyPresetSettings(name, repeatCount, durationMs)
            finish()
        }

        findViewById<Button>(R.id.btnNewPreset).setOnClickListener {
            FloatingControllerService.runningInstance?.startNewPreset()
            finish()
        }

        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }

        loadSavedPresetList()
    }

    private fun loadSavedPresetList() {
        val container = findViewById<LinearLayout>(R.id.savedPresetList)
        lifecycleScope.launch {
            repository.observePresets().collect { presets ->
                container.removeAllViews()
                if (presets.isEmpty()) {
                    val empty = TextView(this@PresetSettingsActivity)
                    empty.text = "Belum ada preset tersimpan"
                    empty.setTextColor(0x99EDEFF7.toInt())
                    empty.textSize = 12f
                    container.addView(empty)
                    return@collect
                }
                presets.forEach { preset ->
                    val row = LayoutInflater.from(this@PresetSettingsActivity)
                        .inflate(R.layout.item_saved_preset_row, container, false)
                    row.findViewById<TextView>(R.id.tvRowName).text = preset.name
                    row.findViewById<Button>(R.id.btnRowUse).setOnClickListener {
                        FloatingControllerService.runningInstance?.switchToPreset(preset.id)
                        finish()
                    }
                    row.findViewById<ImageButton>(R.id.btnRowDelete).setOnClickListener {
                        lifecycleScope.launch { repository.deletePreset(preset) }
                    }
                    container.addView(row)
                }
            }
        }
    }

    companion object {
        const val EXTRA_CURRENT_PRESET_ID = "extra_current_preset_id"
        const val EXTRA_CURRENT_NAME = "extra_current_name"
        const val EXTRA_CURRENT_REPEAT_COUNT = "extra_current_repeat_count"
        const val EXTRA_CURRENT_REPEAT_DURATION_MS = "extra_current_repeat_duration_ms"
    }
}
