package com.example.m3uplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ContinueWatchingAdapter(
    private val history: List<WatchHistory>,
    private val onClick: (WatchHistory) -> Unit
) : RecyclerView.Adapter<ContinueWatchingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // IDs مطابقة لـ item_continue_watching.xml
        val title: TextView       = view.findViewById(R.id.textContinueTitle)
        val poster: ImageView     = view.findViewById(R.id.imageContinue)
        val progressBar: ProgressBar = view.findViewById(R.id.progressContinue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_continue_watching, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = history[position]
        holder.title.text = item.title

        // تحديث شريط التقدم (0-100)
        val progressPercent = if (item.duration > 0) {
            (item.position * 100 / item.duration).toInt()
        } else 0
        holder.progressBar.max      = 100
        holder.progressBar.progress = progressPercent

        Glide.with(holder.itemView.context)
            .load(item.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .centerCrop()
            .into(holder.poster)

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = history.size
}
