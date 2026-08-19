package com.cowalskiiw2026.autoklix.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cowalskiiw2026.autoklix.R
import com.cowalskiiw2026.autoklix.model.PointType
import com.cowalskiiw2026.autoklix.model.TimeUnitOption
import com.cowalskiiw2026.autoklix.model.toMillis
import com.cowalskiiw2026.autoklix.service.FloatingControllerService

/**
 * Dialog transparan yang muncul di atas aplikasi lain begitu sebuah titik
 * baru ditempatkan lewat menu melayang, untuk mengatur jeda ke titik
 * berikutnya (dan durasi tekan/gulir bila relevan).
 */
class PointConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_point_config)

        val pointId = intent.getLongExtra(EXTRA_POINT_ID, 0L)
        val type = PointType.valueOf(intent.getStringExtra(EXTRA_POINT_TYPE) ?: PointType.TAP.name)
        val initialDelay = intent.getLongExtra(EXTRA_DELAY_MS, 500L)
        val initialDuration = intent.getLongExtra(EXTRA_ACTION_DURATION_MS, 300L)

        findViewById<TextView>(R.id.tvPointTitle).text = titleFor(type)

        val etDelay = findViewById<EditText>(R.id.etDelayValue)
        val spDelayUnit = findViewById<Spinner>(R.id.spDelayUnit)
        val durationGroup = findViewById<LinearLayout>(R.id.durationGroup)
        val tvDurationLabel = findViewById<TextView>(R.id.tvDurationLabel)
        val etDuration = findViewById<EditText>(R.id.etDurationValue)
        val spDurationUnit = findViewById<Spinner>(R.id.spDurationUnit)

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            arrayOf(getString(R.string.unit_ms), getString(R.string.unit_sec)))
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spDelayUnit.adapter = unitAdapter
        spDurationUnit.adapter = unitAdapter

        etDelay.setText(initialDelay.toString())
        spDelayUnit.setSelection(0)

        val needsDuration = type == PointType.LONG_PRESS || type == PointType.SWIPE
        durationGroup.visibility = if (needsDuration) LinearLayout.VISIBLE else LinearLayout.GONE
        if (needsDuration) {
            tvDurationLabel.text = if (type == PointType.SWIPE)
                getString(R.string.label_swipe_duration) else getString(R.string.label_press_duration)
            etDuration.setText(initialDuration.toString())
            spDurationUnit.setSelection(0)
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val delayRaw = etDelay.text.toString().toLongOrNull() ?: 500L
            val delayUnit = if (spDelayUnit.selectedItemPosition == 0) TimeUnitOption.MILLISECOND else TimeUnitOption.SECOND
            val delayMs = toMillis(delayRaw, delayUnit)

            val durationMs = if (needsDuration) {
                val durationRaw = etDuration.text.toString().toLongOrNull() ?: 300L
                val durationUnit = if (spDurationUnit.selectedItemPosition == 0) TimeUnitOption.MILLISECOND else TimeUnitOption.SECOND
                toMillis(durationRaw, durationUnit)
            } else initialDuration

            FloatingControllerService.runningInstance?.applyPointConfig(pointId, delayMs, durationMs)
            finish()
        }
    }

    private fun titleFor(type: PointType): String = when (type) {
        PointType.TAP -> getString(R.string.point_type_tap)
        PointType.LONG_PRESS -> getString(R.string.point_type_long_press)
        PointType.SWIPE -> getString(R.string.point_type_swipe)
        PointType.RANDOM_TEXT -> getString(R.string.point_type_text)
    }

    companion object {
        const val EXTRA_POINT_ID = "extra_point_id"
        const val EXTRA_POINT_TYPE = "extra_point_type"
        const val EXTRA_DELAY_MS = "extra_delay_ms"
        const val EXTRA_ACTION_DURATION_MS = "extra_action_duration_ms"
    }
}
