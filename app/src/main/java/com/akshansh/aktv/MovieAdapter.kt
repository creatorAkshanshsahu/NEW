package com.akshansh.aktv

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class MovieAdapter(
    private val onClick: (Movie) -> Unit
) : ListAdapter<Movie, MovieAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val movie = getItem(position)
        holder.title.text = movie.title
        holder.year.text = movie.year.orEmpty()
        holder.year.visibility = if (movie.year.isNullOrBlank()) View.GONE else View.VISIBLE
        Picasso.get().load(movie.poster)
            .resize(300, 450).centerCrop()
            .config(Bitmap.Config.RGB_565)
            .placeholder(R.drawable.bg_card)
            .error(R.drawable.bg_card)
            .into(holder.poster)
    }

    override fun onViewRecycled(holder: VH) {
        Picasso.get().cancelRequest(holder.poster)
        holder.itemView.resetCardState()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.poster)
        val title: TextView = v.findViewById(R.id.title)
        val year: TextView = v.findViewById(R.id.year)

        init {
            v.attachCardInteractions()
            v.setOnClickListener {
                val p = bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) onClick(getItem(p))
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Movie>() {
            override fun areItemsTheSame(a: Movie, b: Movie) = a.id == b.id
            override fun areContentsTheSame(a: Movie, b: Movie) = a == b
        }
    }
}
