package com.example.m3uplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.m3uplayer.databinding.ItemHeroBannerBinding

class HeroBannerAdapter(
    private val items: List<MediaEntry>,
    private val onClick: (MediaEntry) -> Unit
) : RecyclerView.Adapter<HeroBannerAdapter.HeroViewHolder>() {

    inner class HeroViewHolder(val binding: ItemHeroBannerBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val binding = ItemHeroBannerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        // نستخدم "الفهرس الحقيقي" (modulo) لدعم التمرير اللانهائي بين العناصر
        val item = items[position % items.size]
        holder.binding.textHeroTitle.text = item.title

        Glide.with(holder.binding.root.context)
            .load(item.imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .into(holder.binding.imageHeroBackdrop)

        val clickListener = { onClick(item) }
        holder.binding.root.setOnClickListener { clickListener() }
        holder.binding.buttonHeroPlay.setOnClickListener { clickListener() }
    }

    // عدد كبير اصطناعيًا لمحاكاة تمرير لانهائي؛ onBindViewHolder يستخدم position % items.size
    override fun getItemCount(): Int = if (items.isEmpty()) 0 else Int.MAX_VALUE
}
