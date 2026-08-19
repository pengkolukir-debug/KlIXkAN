package com.cowalskiiw2026.autoklix.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.cowalskiiw2026.autoklix.R
import com.cowalskiiw2026.autoklix.data.PresetRepository
import com.cowalskiiw2026.autoklix.databinding.ActivityPresetDetailBinding
import com.cowalskiiw2026.autoklix.model.ClickPoint
import com.cowalskiiw2026.autoklix.model.Preset
import com.cowalskiiw2026.autoklix.model.PointType
import com.cowalskiiw2026.autoklix.model.RepeatDurationUnit
import com.cowalskiiw2026.autoklix.model.TimeUnitOption
import com.cowalskiiw2026.autoklix.model.repeatDurationToMillis
import com.cowalskiiw2026.autoklix.model.toMillis
import com.cowalskiiw2026.autoklix.service.FloatingControllerService
import kotlinx.coroutines.launch

/**
 * Halaman detail sebuah preset yang SUDAH ADA: menampilkan seluruh titiknya
 * dalam bentuk daftar (jenis, koordinat, jeda, durasi tekan/gulir), bisa
 * diedit satu per satu, dihapus, dan DIURUTKAN ULANG lewat drag & drop —
 * semua perubahan langsung tersimpan ke preset yang sama, tidak membuat
 * preset baru. Berdampingan dengan kontroler melayang (yang dipakai untuk
 * menempatkan titik baru di koordinat layar nyata).
 */
class PresetDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPresetDetailBinding
    private lateinit var repository: PresetRepository
    private lateinit var adapter: PresetDetailPointAdapter
    private var presetId: Long = 0L
    private var currentPreset: Preset? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPresetDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PresetRepository(applicationContext)
        presetId = intent.getLongExtra(EXTRA_PRESET_ID, 0L)

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            arrayOf(getString(R.string.unit_minute), getString(R.string.unit_hour)))
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spRepeatDurationUnit.adapter = unitAdapter

        adapter = PresetDetailPointAdapter(
            onEdit = { point -> showPointEditDialog(point) },
            onDelete = { point -> deletePoint(point) },
            onOrderChanged = { reordered -> persistReorder(reordered) }
        )
        binding.rvPoints.layoutManager = LinearLayoutManager(this)
        binding.rvPoints.adapter = adapter
        ItemTouchHelper(PointDragCallback(adapter)).attachToRecyclerView(binding.rvPoints)

        binding.btnSaveMeta.setOnClickListener { saveMeta() }

        loadPreset()
    }

    private fun loadPreset() {
        lifecycleScope.launch {
            val preset = if (presetId > 0) repository.getFullPreset(presetId) else null
            currentPreset = preset
            binding.etPresetName.setText(preset?.name ?: "")
            binding.etRepeatCount.setText((preset?.repeatCount ?: 0).toString())
            val durationMs = preset?.repeatDurationMs ?: 0L
            if (durationMs > 0) {
                val hours = durationMs / 3_600_000L
                if (hours > 0) {
                    binding.etRepeatDuration.setText(hours.toString())
                    binding.spRepeatDurationUnit.setSelection(1)
                } else {
                    binding.etRepeatDuration.setText((durationMs / 60_000L).toString())
                    binding.spRepeatDurationUnit.setSelection(0)
                }
            } else {
                binding.etRepeatDuration.setText("0")
            }
            val points = preset?.points ?: emptyList()
            adapter.submit(points)
            binding.tvEmptyHint.visibility = if (points.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.rvPoints.visibility = if (points.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun saveMeta() {
        lifecycleScope.launch {
            val name = binding.etPresetName.text.toString().ifBlank { "Preset Tanpa Nama" }
            val repeatCount = binding.etRepeatCount.text.toString().toIntOrNull() ?: 0
            val durationValue = binding.etRepeatDuration.text.toString().toLongOrNull() ?: 0L
            val durationUnit = if (binding.spRepeatDurationUnit.selectedItemPosition == 0) RepeatDurationUnit.MINUTE else RepeatDurationUnit.HOUR
            val durationMs = if (durationValue > 0) repeatDurationToMillis(durationValue, durationUnit) else 0L

            val existingPoints = repository.getPointsOnce(presetId)
            val preset = Preset(id = presetId, name = name, repeatCount = repeatCount, repeatDurationMs = durationMs, points = existingPoints)
            repository.savePreset(preset)
            Toast.makeText(this@PresetDetailActivity, R.string.toast_preset_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun deletePoint(point: ClickPoint) {
        lifecycleScope.launch {
            repository.deletePoint(point)
            loadPreset()
            notifyOverlayIfActive()
        }
    }

    private fun persistReorder(reordered: List<ClickPoint>) {
        lifecycleScope.launch {
            val preset = Preset(
                id = presetId,
                name = binding.etPresetName.text.toString().ifBlank { "Preset Tanpa Nama" },
                repeatCount = binding.etRepeatCount.text.toString().toIntOrNull() ?: 0,
                repeatDurationMs = currentPreset?.repeatDurationMs ?: 0L,
                points = reordered
            )
            repository.savePreset(preset)
            notifyOverlayIfActive()
        }
    }

    private fun notifyOverlayIfActive() {
        // Bila kontroler melayang sedang aktif untuk preset yang sama, muat ulang agar sinkron.
        val running = FloatingControllerService.runningInstance
        if (running != null) running.switchToPreset(presetId)
    }

    private fun showPointEditDialog(point: ClickPoint) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.activity_point_config)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.findViewById<TextView>(R.id.tvPointTitle).text = when (point.type) {
            PointType.TAP -> getString(R.string.point_type_tap)
            PointType.LONG_PRESS -> getString(R.string.point_type_long_press)
            PointType.SWIPE -> getString(R.string.point_type_swipe)
            PointType.RANDOM_TEXT -> getString(R.string.point_type_text)
        }

        val etDelay = dialog.findViewById<EditText>(R.id.etDelayValue)
        val spDelayUnit = dialog.findViewById<Spinner>(R.id.spDelayUnit)
        val durationGroup = dialog.findViewById<LinearLayout>(R.id.durationGroup)
        val tvDurationLabel = dialog.findViewById<TextView>(R.id.tvDurationLabel)
        val etDuration = dialog.findViewById<EditText>(R.id.etDurationValue)
        val spDurationUnit = dialog.findViewById<Spinner>(R.id.spDurationUnit)

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            arrayOf(getString(R.string.unit_ms), getString(R.string.unit_sec)))
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDelayUnit.adapter = unitAdapter
        spDurationUnit.adapter = unitAdapter

        etDelay.setText(point.delayAfterMs.toString())
        spDelayUnit.setSelection(0)

        val needsDuration = point.type == PointType.LONG_PRESS || point.type == PointType.SWIPE
        durationGroup.visibility = if (needsDuration) LinearLayout.VISIBLE else LinearLayout.GONE
        if (needsDuration) {
            tvDurationLabel.text = if (point.type == PointType.SWIPE)
                getString(R.string.label_swipe_duration) else getString(R.string.label_press_duration)
            etDuration.setText(point.actionDurationMs.toString())
            spDurationUnit.setSelection(0)
        }

        dialog.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val delayRaw = etDelay.text.toString().toLongOrNull() ?: point.delayAfterMs
            val delayUnit = if (spDelayUnit.selectedItemPosition == 0) TimeUnitOption.MILLISECOND else TimeUnitOption.SECOND
            val delayMs = toMillis(delayRaw, delayUnit)

            val durationMs = if (needsDuration) {
                val durationRaw = etDuration.text.toString().toLongOrNull() ?: point.actionDurationMs
                val durationUnit = if (spDurationUnit.selectedItemPosition == 0) TimeUnitOption.MILLISECOND else TimeUnitOption.SECOND
                toMillis(durationRaw, durationUnit)
            } else point.actionDurationMs

            lifecycleScope.launch {
                repository.updatePoint(point.copy(delayAfterMs = delayMs, actionDurationMs = durationMs))
                loadPreset()
                notifyOverlayIfActive()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    companion object {
        const val EXTRA_PRESET_ID = "extra_preset_id"
    }
}
