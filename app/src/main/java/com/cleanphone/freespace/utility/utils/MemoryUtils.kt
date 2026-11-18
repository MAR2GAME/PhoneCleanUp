package com.cleanphone.freespace.utility.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.cleanphone.freespace.utility.bean.MemoryInfo

import java.io.File
import java.io.RandomAccessFile

import kotlin.math.roundToInt

object MemoryUtils
{

    private const val PERCENTAGE_MULTIPLIER = 100f
    private var cachedTotalMemory: Long = -1L

    /**
     * 获取内存信息
     * @param context 上下文对象
     * @return MemoryInfo 包含内存使用率和描述信息
     */
    suspend fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemory = getTotalMemory(memoryInfo)
        val availableMemory = memoryInfo.availMem
        val memoryUsageRatio = calculateMemoryUsageRatio(totalMemory, availableMemory)
        return MemoryInfo(
            memoryUsage = (memoryUsageRatio * PERCENTAGE_MULTIPLIER).roundToInt(),
            ""
        )
    }


    /**
     * 获取总内存大小
     */
    private fun getTotalMemory(memoryInfo: ActivityManager.MemoryInfo): Long {
        // 使用缓存避免重复计算
        if (cachedTotalMemory <= 0) {
            cachedTotalMemory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                memoryInfo.totalMem
            } else {
                getTotalMemoryLegacy()
            }
        }
        return cachedTotalMemory
    }
    private const val MEMINFO_PATH = "/proc/meminfo"
    /**
     * 低版本API获取总内存的备选方案
     */
    private fun getTotalMemoryLegacy(): Long {
        return try {
            val file = File(MEMINFO_PATH)
            if (!file.exists()) return 0L
            val reader = RandomAccessFile(file, "r")
            val firstLine = reader.readLine() ?: ""
            reader.close()
            // 解析 MemTotal: 123456 kB
            val parts = firstLine.split("\\s+".toRegex())
            if (parts.size >= 2) {
                parts[1].toLong() * 1024 // 转换为字节
            } else {
                0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    /**
     * 计算内存使用率
     */
    private fun calculateMemoryUsageRatio(totalMemory: Long, availableMemory: Long): Float {
        return if (totalMemory > 0L) {
            val usedMemory = totalMemory - availableMemory
            usedMemory.toFloat() / totalMemory.toFloat()
        } else {
            0f
        }
    }



}