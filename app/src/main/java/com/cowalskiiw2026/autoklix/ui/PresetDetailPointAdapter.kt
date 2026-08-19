package com.cowalskiiw2026.autoklix.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.cowalskiiw2026.autoklix.databinding.ItemPresetDetailPointBinding
import com.cowalskiiw2026.autoklix.model.ClickPoint
import com.cowalskiiw2026.autoklix.model.PointType

/**
 * Daftar titik sebuah preset yang bisa diurutkan ulang lewat drag & drop
 * (seret ikon ≡), diedit (jeda/durasi), dan dihapus — semuanya langsung pada
 * preset yang sudah ada, tanpa perlu membuat preset baru.
 */
class PresetDetailPointAdapter(
    private val onEdit: (ClickPoint) -> Unit,
    private val onDelete: (ClickPoint) -> Unit,
    private val onOrderChanged: (List<ClickPoint>) -> Unit
) : RecyclerView.Adapter<PresetDetailPointAdapter.VH>() {

    private val items = mutableListOf<ClickPoint>()

    fun submit(newItems: List<ClickPoint>) {
        items.clear()
        items.addAll(newItems.sortedBy { it.order })
        notifyDataSetChanged()
    }

    fun currentList(): List<ClickPoint> = items.toList()

    inner class VH(val binding: ItemPresetDetailPointBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPresetDetailPointBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val point = items[position]
        holder.binding.tvPointOrder.text = (position + 1).toString()
        holder.binding.tvPointType.text = labelFor(point.type)
        holder.binding.tvPointDetail.text = detailFor(point)
        holder.binding.btnEditPoint.setOnClickListener { onEdit(point) }
        holder.binding.btnDeletePoint.setOnClickListener { onDelete(point) }
    }

    override fun getItemCount(): Int = items.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun commitReorder() {
        val reordered = items.mapIndexed { index, p -> p.copy(order = index) }
        items.clear()
        items.addAll(reordered)
        notifyDataSetChanged()
        onOrderChanged(items.toList())
    }

    private fun labelFor(type: PointType) = when (type) {
        PointType.TAP -> "Klik Sekali"
        PointType.LONG_PRESS -> "Tekan Tahan"
        PointType.SWIPE -> "Gulir"
        PointType.RANDOM_TEXT -> "Bot Autofill 2 Huruf"
    }

    private fun detailFor(point: ClickPoint): String {
        val base = when (point.type) {
            PointType.SWIPE -> "(${point.x.toInt()},${point.y.toInt()}) → (${point.x2?.toInt()},${point.y2?.toInt()}) • durasi ${point.actionDurationMs}md"
            PointType.LONG_PRESS -> "x=${point.x.toInt()}, y=${point.y.toInt()} • tekan ${point.actionDurationMs}md"
            else -> "x=${point.x.toInt()}, y=${point.y.toInt()}"
        }
        return "$base • jeda berikutnya ${point.delayAfterMs}md"
    }
}

/** ItemTouchHelper untuk drag & drop reorder pada RecyclerView titik. */
class PointDragCallback(private val adapter: PresetDetailPointAdapter) :
    ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { /* tidak dipakai */ }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        adapter.commitReorder()
    }
}
