package com.cleanphone.freespace.utility.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cleanphone.freespace.utility.bean.NetworkUsageEntry
import com.cleanphone.freespace.utility.databinding.ItemNetworkUsageBinding
import com.cleanphone.freespace.utility.utils.NetworkUsageFormatter


class NetworkUsageAdapter : RecyclerView.Adapter<NetworkUsageAdapter.NetworkUsageViewHolder>() {

    private val items = mutableListOf<NetworkUsageEntry>()
    private var maxBytes: Long = 0L

    fun submitList(items: List<NetworkUsageEntry>) {
        this.items.clear()
        this.items.addAll(items)
        maxBytes = items.maxOfOrNull { it.totalBytes } ?: 0L
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetworkUsageViewHolder {
        val binding = ItemNetworkUsageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NetworkUsageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NetworkUsageViewHolder, position: Int) {
        holder.bind(items[position], maxBytes)
    }

    override fun getItemCount(): Int = items.size

    inner class NetworkUsageViewHolder(
        private val binding: ItemNetworkUsageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NetworkUsageEntry, maxBytes: Long) {
            binding.ivAppIcon.setImageDrawable(item.icon)
            binding.tvAppName.text = item.appName
            binding.tvUsageValue.text = NetworkUsageFormatter.formatBytes(
                item.totalBytes,
                binding.root.resources.configuration.locales[0]
            )
            binding.progressUsage.max = 100
            binding.progressUsage.progress = if (maxBytes <= 0L) 0 else {
                ((item.totalBytes.toDouble() / maxBytes.toDouble()) * 100).toInt().coerceAtLeast(2)
            }
        }
    }
}
