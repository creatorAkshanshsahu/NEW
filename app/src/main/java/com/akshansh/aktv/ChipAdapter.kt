package com.akshansh.aktv

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Horizontal category filter chips ("All", "News", "Sports", ...). */
class ChipAdapter(
    private val onSelect: (String?) -> Unit
) : RecyclerView.Adapter<ChipAdapter.VH>() {

    private var items: List<String> = emptyList()
    private var selected = 0

    fun submit(categories: List<String>) {
        items = if (categories.isEmpty()) emptyList() else listOf("All") + categories
        selected = 0
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chip, parent, false) as TextView
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = items[position]
        holder.tv.isSelected = position == selected
    }

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv) {
        init {
            tv.setOnClickListener {
                val p = bindingAdapterPosition
                if (p == RecyclerView.NO_POSITION || p == selected) return@setOnClickListener
                val old = selected
                selected = p
                notifyItemChanged(old)
                notifyItemChanged(p)
                onSelect(if (p == 0) null else items[p])
            }
        }
    }
}
