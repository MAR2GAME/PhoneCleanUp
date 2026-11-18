package com.cleanphone.freespace.utility.bean

data class StorageInfo(
    var totalSpace: Long,
    var usedSpace: Long,
    var usagePercentage: Float
)