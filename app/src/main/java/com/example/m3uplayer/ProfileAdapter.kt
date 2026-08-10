package com.example.m3uplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.m3uplayer.databinding.ItemProfileBinding

class ProfileAdapter(
    private val profiles: List<Profile>,
    private val onClick: (Profile) -> Unit,
    private val onDelete: (Profile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    inner class ProfileViewHolder(val binding: ItemProfileBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val binding = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProfileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        holder.binding.textProfileName.text = profile.profileName
        holder.binding.textProfileServer.text = profile.serverUrl
        holder.binding.root.setOnClickListener { onClick(profile) }
        holder.binding.buttonDeleteProfile.setOnClickListener { onDelete(profile) }
    }

    override fun getItemCount(): Int = profiles.size
}
