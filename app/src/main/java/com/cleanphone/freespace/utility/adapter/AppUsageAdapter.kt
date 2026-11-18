package com.cleanphone.freespace.utility.adapter

import android.R.attr.enabled
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.bean.AppUsageListItem
import com.cleanphone.freespace.utility.databinding.ItemAppUsageBinding


class AppUsageAdapter(
    private val onManageClick: (AppUsageListItem) -> Unit
) : RecyclerView.Adapter<AppUsageAdapter.AppUsageViewHolder>() {

    private val items = mutableListOf<AppUsageListItem>()

    fun submitList(items: List<AppUsageListItem>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
        val binding = ItemAppUsageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppUsageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AppUsageViewHolder(
        private val binding: ItemAppUsageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppUsageListItem) {
            binding.ivAppIcon.setImageDrawable(item.icon)
            binding.tvAppName.text = item.appName
            binding.tvUsageValue.text = item.valueText
            binding.progressUsage.progress = item.progress

            binding.btnManage.apply {
                text = context.getString(R.string.manage)
                isEnabled = item.canManage
                alpha = if (item.canManage) 1f else 0.6f
                setBackgroundResource(
                    if (item.canManage) R.drawable.usage_manage_enabled_bg
                    else R.drawable.usage_manage_disabled_bg
                )
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (item.canManage) R.color.white else R.color.theme_color
                    )
                )
                setOnClickListener {
                    if (item.canManage) {
                        onManageClick(item)
                    }
                }
            }



        }
    }
}
