package com.cleanphone.freespace.utility.adapter

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cleanphone.freespace.utility.bean.NotificationBarRecord
import com.cleanphone.freespace.utility.databinding.ItemNotificationRecordBinding
import com.cleanphone.freespace.utility.utils.NotificationBarTimeFormatter


class NotificationRecordAdapter(
    private val context: Context
) : RecyclerView.Adapter<NotificationRecordAdapter.NotificationRecordViewHolder>() {

    private val items = mutableListOf<NotificationBarRecord>()
    private val iconCache = mutableMapOf<String, Drawable?>()

    fun submitList(records: List<NotificationBarRecord>) {
        items.clear()
        items.addAll(records)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationRecordViewHolder {
        val binding = ItemNotificationRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationRecordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationRecordViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class NotificationRecordViewHolder(
        private val binding: ItemNotificationRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationBarRecord) {
            binding.ivAppIcon.setImageDrawable(resolveAppIcon(item.packageName))
            binding.tvAppName.text = item.appName
            binding.tvAppSubtitle.text = item.text.ifBlank { item.title }
            binding.tvTime.text = NotificationBarTimeFormatter.formatRelativeTime(context, item.postedAt)
        }
    }

    private fun resolveAppIcon(packageName: String): Drawable? {
        if (iconCache.containsKey(packageName)) {
            return iconCache[packageName]
        }
        val icon = runCatching {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationIcon(appInfo)
        }.getOrNull()
        iconCache[packageName] = icon
        return icon
    }
}
