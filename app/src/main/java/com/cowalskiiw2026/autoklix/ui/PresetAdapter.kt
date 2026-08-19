package com.cowalskiiw2026.autoklix.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cowalskiiw2026.autoklix.databinding.ItemPresetBinding
import com.cowalskiiw2026.autoklix.model.Preset

/**
 * Daftar preset di halaman utama HANYA untuk memilih preset mana yang akan
 * dijalankan (lewat kontroler melayang). Menyimpan/mengedit nama/pengulangan
 * dilakukan dari dalam kontroler melayang itu sendiri (PresetSettingsActivity),
 * bukan dari halaman ini, supaya tidak ada dua tempat yang membingungkan.
 * Preset yang sedang dijalankan tetap bisa diedit langsung lewat overlay.
 */
class PresetAdapter(
    private val onRun: (Preset) -> Unit,
    private val onViewDetail: (Preset) -> Unit,
    private val onDelete: (Preset) -> Unit
) : RecyclerView.Adapter<PresetAdapter.VH>() {

    private val items = mutableListOf<Preset>()

    fun submit(newItems: List<Preset>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemPresetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPresetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val preset = items[position]
        holder.binding.tvPresetName.text = preset.name
        val repeatInfo = if (preset.repeatCount > 0) "ulang ${preset.repeatCount}x" else "ulang tanpa batas"
        holder.binding.tvPresetInfo.text = "${preset.points.size} titik • $repeatInfo • ketuk untuk jalankan & edit"
        holder.binding.root.setOnClickListener { onRun(preset) }
        holder.binding.btnRunOverlay.setOnClickListener { onRun(preset) }
        holder.binding.btnViewDetail.setOnClickListener { onViewDetail(preset) }
        holder.binding.btnDeletePreset.setOnClickListener { onDelete(preset) }
    }

    override fun getItemCount(): Int = items.size
}
