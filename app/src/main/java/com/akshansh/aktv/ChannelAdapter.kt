package com.akshansh.aktv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class ChannelAdapter(
    private val onClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = getItem(position)
        holder.title.text = channel.name
        holder.category.text = channel.category.orEmpty()
        holder.category.visibility = if (channel.category.isNullOrBlank()) View.GONE else View.VISIBLE

        val logo = channel.logo
        if (logo != null && logo.startsWith("http")) {
            Picasso.get().load(logo)
                .resize(240, 240).onlyScaleDown().centerInside()
                .placeholder(R.drawable.ic_tv)
                .error(R.drawable.ic_tv)
                .into(holder.logo)
        } else {
            Picasso.get().cancelRequest(holder.logo)
            holder.logo.setImageResource(R.drawable.ic_tv)
        }
    }

    override fun onViewRecycled(holder: Holder) {
        Picasso.get().cancelRequest(holder.logo)
        holder.itemView.resetCardState()
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.channelLogo)
        val title: TextView = view.findViewById(R.id.channelTitle)
        val category: TextView = view.findViewById(R.id.channelCategory)

        init {
            view.attachCardInteractions()
            view.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onClick(getItem(p))
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Channel>() {
            override fun areItemsTheSame(a: Channel, b: Channel) = a.id == b.id
            override fun areContentsTheSame(a: Channel, b: Channel) = a == b
        }
    }
}
