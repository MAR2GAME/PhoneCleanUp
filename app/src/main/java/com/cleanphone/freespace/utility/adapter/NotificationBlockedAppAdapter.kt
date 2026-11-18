package com.cleanphone.freespace.utility.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.bean.NotificationAppEntry
import com.cleanphone.freespace.utility.databinding.ItemNotificationAppBinding


class NotificationBlockedAppAdapter(
    private val onItemClick: (NotificationAppEntry) -> Unit
) : RecyclerView.Adapter<NotificationBlockedAppAdapter.NotificationAppViewHolder>() {

    private val items = mutableListOf<NotificationAppEntry>()
    private var selectedPackages: Set<String> = emptySet()

    fun submitList(apps: List<NotificationAppEntry>, selectedPackages: Set<String>) {
        items.clear()
        items.addAll(apps)
        this.selectedPackages = selectedPackages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationAppViewHolder {
        val binding = ItemNotificationAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationAppViewHolder, position: Int) {
        holder.bind(items[position], selectedPackages.contains(items[position].packageName))
    }

    override fun getItemCount(): Int = items.size

    inner class NotificationAppViewHolder(
        private val binding: ItemNotificationAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationAppEntry, isSelected: Boolean) {
            binding.ivAppIcon.setImageDrawable(item.icon)
            binding.tvAppName.text = item.appName
            binding.ivSelection.setImageResource(
                if (isSelected) R.mipmap.ic_check else R.mipmap.ic_check_nor
            )
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
