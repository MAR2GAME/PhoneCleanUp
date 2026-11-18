package com.cleanphone.freespace.utility.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.View.VISIBLE
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cleanphone.freespace.utility.bean.NetworkUsageSnapshot
import com.cleanphone.freespace.utility.bean.NetworkUsageType
import com.cleanphone.freespace.utility.databinding.ItemNetworkUsagePageBinding
import com.cleanphone.freespace.utility.utils.NetworkUsageFormatter


class NetworkUsagePagerAdapter : RecyclerView.Adapter<NetworkUsagePagerAdapter.NetworkUsagePageViewHolder>() {

    private val pageTypes = listOf(NetworkUsageType.CELLULAR, NetworkUsageType.WIFI)
    private val snapshots = mutableMapOf<NetworkUsageType, NetworkUsageSnapshot>()

    fun submitSnapshots(items: Map<NetworkUsageType, NetworkUsageSnapshot>) {
        snapshots.clear()
        snapshots.putAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetworkUsagePageViewHolder {
        val binding = ItemNetworkUsagePageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NetworkUsagePageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NetworkUsagePageViewHolder, position: Int) {
        holder.bind(snapshots[pageTypes[position]])
    }

    override fun getItemCount(): Int = pageTypes.size

    inner class NetworkUsagePageViewHolder(
        private val binding: ItemNetworkUsagePageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val usageAdapter = NetworkUsageAdapter()

        init {
            binding.rvUsage.apply {
                adapter = usageAdapter
                layoutManager = LinearLayoutManager(context)
                setHasFixedSize(true)
            }
        }

        fun bind(snapshot: NetworkUsageSnapshot?) {
            val safeSnapshot = snapshot ?: NetworkUsageSnapshot(
                usageType = NetworkUsageType.WIFI,
                totalBytes = 0L,
                rxBytes = 0L,
                txBytes = 0L,
                entries = emptyList()
            )
            val locale = binding.root.resources.configuration.locales[0]
            binding.tvTotalValue.text = NetworkUsageFormatter.formatBytes(safeSnapshot.totalBytes, locale)
            binding.tvDownloadValue.text = NetworkUsageFormatter.formatBytes(safeSnapshot.rxBytes, locale)
            binding.tvUploadValue.text = NetworkUsageFormatter.formatBytes(safeSnapshot.txBytes, locale)
            usageAdapter.submitList(safeSnapshot.entries)
            binding.tvEmpty.visibility = if (safeSnapshot.entries.isEmpty()) VISIBLE else View.GONE
        }
    }
}
