package com.cleanphone.freespace.utility.bean

import android.net.Uri
import com.chad.library.adapter.base.entity.MultiItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.format

data class Screenshot(
    val id: Long,
    val name: String,
    val dateTaken: Long, // 时间戳
    val size: Long,      // 文件大小(字节)
    val contentUri: Uri?,
    val path: String,    // 文件路径
    var isSelected: Boolean=false,
    override var itemType: Int = 0   // 默认普通类型
) : MultiItemEntity