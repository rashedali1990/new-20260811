package com.example.m3uplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.m3uplayer.databinding.ItemChannelBinding

class ChannelAdapter(
    private val channels: List<Channel>,
    private val onClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    inner class ChannelViewHolder(val binding: ItemChannelBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        holder.binding.textChannelName.text = channel.name
        holder.binding.textChannelGroup.text = channel.groupTitle ?: ""
        holder.binding.root.setOnClickListener { onClick(channel) }
    }

    override fun getItemCount(): Int = channels.size
}
