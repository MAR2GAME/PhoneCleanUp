package com.cleanphone.freespace.utility.bean

import android.graphics.drawable.Drawable
import com.chad.library.adapter.base.entity.MultiItemEntity

// 数据类（已修正）
data class RunningAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    override var itemType: Int = 0   // 默认普通类型
) : MultiItemEntity
