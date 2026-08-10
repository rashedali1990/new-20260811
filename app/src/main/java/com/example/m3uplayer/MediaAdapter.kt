package com.example.m3uplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.m3uplayer.databinding.ItemChannelBinding

class MediaAdapter(
    private var items: List<MediaEntry>,
    private val onClick: (MediaEntry) -> Unit,
    private val onFavoriteClick: (MediaEntry) -> Unit,
    private val isFavorite: (MediaEntry) -> Boolean
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    inner class MediaViewHolder(val binding: ItemChannelBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val entry = items[position]
        holder.binding.textChannelName.text = entry.title
        holder.binding.textChannelGroup.text = entry.subtitle ?: if (entry.isSeries) "مسلسل - اضغط لعرض الحلقات" else ""

        holder.binding.buttonFavorite.setImageResource(
            if (isFavorite(entry)) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )

        holder.binding.root.setOnClickListener { onClick(entry) }
        holder.binding.buttonFavorite.setOnClickListener { onFavoriteClick(entry) }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<MediaEntry>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
