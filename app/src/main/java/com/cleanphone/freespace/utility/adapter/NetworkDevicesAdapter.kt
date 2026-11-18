package com.cleanphone.freespace.utility.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.bean.NetworkDeviceInfo
import com.cleanphone.freespace.utility.databinding.ItemNetworkDeviceBinding


class NetworkDevicesAdapter : RecyclerView.Adapter<NetworkDevicesAdapter.NetworkDeviceViewHolder>() {

    private val items = mutableListOf<NetworkDeviceInfo>()

    fun submitList(items: List<NetworkDeviceInfo>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetworkDeviceViewHolder {
        val binding = ItemNetworkDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NetworkDeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NetworkDeviceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class NetworkDeviceViewHolder(
        private val binding: ItemNetworkDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NetworkDeviceInfo) {
            binding.tvDeviceIp.text = item.ipAddress
            binding.tvDeviceRole.text = item.role
            binding.tvDeviceMac.text = itemView.context.getString(R.string.network_scan_mac_format, item.macAddress)
            binding.tvDeviceInterface.text = itemView.context.getString(
                R.string.network_scan_interface_format,
                item.interfaceName
            )
        }
    }
}
