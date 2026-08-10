package com.example.m3uplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.m3uplayer.databinding.ItemCustomGroupBinding

class CustomGroupAdapter(
    private var groups: List<CustomGroup>,
    private val onDeleteClick: (CustomGroup) -> Unit,
    private val onGroupClick: (CustomGroup) -> Unit
) : RecyclerView.Adapter<CustomGroupAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCustomGroupBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCustomGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        holder.binding.textGroupName.text = group.name
        holder.binding.buttonDeleteGroup.setOnClickListener { onDeleteClick(group) }
        holder.binding.root.setOnClickListener { onGroupClick(group) }
    }

    override fun getItemCount(): Int = groups.size

    fun updateGroups(newGroups: List<CustomGroup>) {
        this.groups = newGroups
        notifyDataSetChanged()
    }
}
