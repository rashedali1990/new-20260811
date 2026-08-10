package com.example.m3uplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.m3uplayer.databinding.ItemMediaPosterBinding

class PosterAdapter(
    private val items: List<MediaEntry>,
    private val onClick: (MediaEntry) -> Unit
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

        Glide.with(holder.binding.root.context)
            .load(item.imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .into(holder.binding.imagePoster)

        holder.binding.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
