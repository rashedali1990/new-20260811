package com.example.m3uplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.m3uplayer.databinding.ItemMediaPosterBinding

class PosterAdapter(
    private val items: List<MediaEntry>,
    private val onClick: (MediaEntry) -> Unit,
    private val onLongClick: ((MediaEntry) -> Unit)? = null
) : RecyclerView.Adapter<PosterAdapter.PosterViewHolder>() {

    inner class PosterViewHolder(val binding: ItemMediaPosterBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PosterViewHolder {
        val binding = ItemMediaPosterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PosterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PosterViewHolder, position: Int) {
        val item = items[position]
        holder.binding.textPosterTitle.text = item.title

        val context = holder.binding.root.context
        val historyManager = WatchHistoryManager(context)
        val historyItem = historyManager.getHistory().find { it.id == item.id }

        if (historyItem != null && historyItem.duration > 0) {
            val percent = ((historyItem.position * 100) / historyItem.duration).toInt()
            holder.binding.progressWatched.visibility = View.VISIBLE
            holder.binding.progressWatched.progress = percent.coerceIn(0, 100)
        } else {
            holder.binding.progressWatched.visibility = View.GONE
        }

        Glide.with(context)
            .load(item.imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .into(holder.binding.imagePoster)

        holder.binding.root.setOnClickListener { onClick(item) }
        onLongClick?.let { callback ->
            holder.binding.root.setOnLongClickListener { callback(item); true }
        }
    }

    override fun getItemCount(): Int = items.size
}
