package com.cleanphone.freespace.utility.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.bean.BatteryInfo

import java.lang.reflect.Method
import kotlin.also
import kotlin.collections.isEmpty
import kotlin.jvm.java
import kotlin.let
import kotlin.math.max
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceIn
import kotlin.takeIf

class BatteryInfoHelper(private val context: Context) {

    companion object {
        private const val UNKNOWN_VALUE = -1
        private const val MICRO_TO_MILLI_CONVERSION = 1000.0
        private const val TEMPERATURE_DIVISOR = 10f
        private const val ASSUMED_HOURLY_DRAIN = 18.5f
        private const val DEFAULT_HOURLY_DRAIN = 10f
        private const val MIN_VALID_LEVEL = 0
        private const val MAX_VALID_LEVEL = 100
        private const val MILLIS_IN_MINUTE = 60 * 1000L
    }

    private var consumptionRatePerMinute: Float? = null
    private var lastBatteryLevel: Int? = null
    private var lastTimeRecorded: Long? = null

    /**
     * 获取完整的电池信息
     */
    fun getBatteryInfo(): BatteryInfo {
        return try {
            val batteryStatus = getBatteryStatusIntent()
            val level = getBatteryLevel(batteryStatus)

            BatteryInfo(
                level = level,
                scale = getBatteryScale(batteryStatus),
                health = getBatteryHealth(batteryStatus),
                voltage = getBatteryVoltage(batteryStatus),
                temperature = getBatteryTemperature(batteryStatus) / TEMPERATURE_DIVISOR,
                technology = getBatteryTechnology(batteryStatus),
                capacity = getBatteryCapacity(),
                availableTime = getRemainingTimeEstimate(level)
            )
        } catch (e: Exception) {
            createDefaultBatteryInfo()
        }
    }



    /**
     * 更新电量消耗速率
     */
    fun updateConsumptionRate(currentLevel: Int) {
        if (!isValidBatteryLevel(currentLevel)) {
            return
        }

        val currentTime = System.currentTimeMillis()
        lastBatteryLevel?.let { lastLevel ->
            lastTimeRecorded?.let { lastTime ->
                val levelDiff = max(lastLevel - currentLevel, 0)
                val timeDiffMinutes = (currentTime - lastTime) / MILLIS_IN_MINUTE.toFloat()

                if (levelDiff > 0 && timeDiffMinutes > 0) {
                    consumptionRatePerMinute = levelDiff / timeDiffMinutes
                }
            }
        }

        lastBatteryLevel = currentLevel
        lastTimeRecorded = currentTime
    }

    /**
     * 估算剩余时间（分钟）
     */
    fun estimateRemainingTimeMinutes(currentLevel: Int): Float? {
        return consumptionRatePerMinute?.takeIf { it > 0 }?.let { rate ->
            currentLevel / rate
        }
    }

    /**
     * 格式化剩余时间
     */
    fun formatRemainingTime(minutes: Float): String {
        val totalMinutes = minutes.toInt().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60

        return when {
            hours > 0 -> "${hours}h${mins}m"
            else -> "${mins}m"
        }
    }

    // region Private Methods

    /**
     * 获取电池状态 Intent
     */
    private fun getBatteryStatusIntent(): Intent {
        return context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: Intent().also {
            }
    }

    /**
     * 获取当前电池电量百分比
     */
    private fun getBatteryLevel(batteryStatus: Intent): Int {
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN_VALUE)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_VALUE)

        return if (isValidLevelAndScale(level, scale)) {
            (level * 100 / scale.toFloat()).toInt().coerceIn(MIN_VALID_LEVEL, MAX_VALID_LEVEL)
        } else {
            UNKNOWN_VALUE
        }
    }

    /**
     * 获取电池最大刻度值
     */
    private fun getBatteryScale(batteryStatus: Intent): Int {
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_VALUE)
    }

    /**
     * 获取电池健康状态
     */
   fun getBatteryHealth(batteryStatus: Intent): String {
        return when (batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, UNKNOWN_VALUE)) {
            BatteryManager.BATTERY_HEALTH_COLD -> context.getString(R.string.overcooling)
            BatteryManager.BATTERY_HEALTH_DEAD -> context.getString(R.string.damage)
            BatteryManager.BATTERY_HEALTH_GOOD -> context.getString(R.string.good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> context.getString(R.string.overheat)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getString(R.string.overvoltage)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> context.getString(R.string.fault)
            else -> context.getString(R.string.unknown)
        }
    }

    /**
     * 获取电池电压
     */
    private fun getBatteryVoltage(batteryStatus: Intent): Int {
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, UNKNOWN_VALUE)
    }

    /**
     * 获取电池温度
     */
    private fun getBatteryTemperature(batteryStatus: Intent): Int {
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, UNKNOWN_VALUE)
    }

    /**
     * 获取电池技术类型
     */
    private fun getBatteryTechnology(batteryStatus: Intent): String {
        return batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?: context.getString(R.string.unknown)
    }

    /**
     * 获取电池容量（整合多种方法）
     */
    private fun getBatteryCapacity(): Int {
        return getBatteryCapacityByReflection()
            ?: getBatteryCapacityByChargeCounter()
            ?: 0
    }

    /**
     * 通过反射获取电池容量
     */
    private fun getBatteryCapacityByReflection(): Int? {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfileInstance = constructor.newInstance(context)

            val method = getBatteryCapacityMethod(powerProfileClass)
            val capacity = invokeBatteryCapacityMethod(method, powerProfileInstance)

            capacity.takeIf { it > 0 }?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun getBatteryCapacityMethod(powerProfileClass: Class<*>): Method {
        return try {
            powerProfileClass.getMethod("getBatteryCapacity")
        } catch (e: NoSuchMethodException) {
            powerProfileClass.getMethod("getAveragePower", String::class.java)
        }
    }

    private fun invokeBatteryCapacityMethod(method: Method, instance: Any): Double {
        return if (method.parameterTypes.isEmpty()) {
            method.invoke(instance) as Double
        } else {
            method.invoke(instance, "battery.capacity") as Double
        }
    }

    /**
     * 通过电荷计数器获取电池容量
     */
    private fun getBatteryCapacityByChargeCounter(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val capacityPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            if (isValidChargeCounterData(chargeCounter, capacityPercent)) {
                ((chargeCounter / capacityPercent) * 100 / MICRO_TO_MILLI_CONVERSION).toInt()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取电池剩余使用时间估算
     */
    private fun getRemainingTimeEstimate(batteryPercent: Int): String {
        val estimatedMinutes = estimateRemainingTimeMinutes(batteryPercent)
            ?: estimateRemainingUsageTimeSimple(batteryPercent, ASSUMED_HOURLY_DRAIN) * 60

        return formatRemainingTime(estimatedMinutes)
    }

    /**
     * 简单估算剩余使用时间
     */
    private fun estimateRemainingUsageTimeSimple(
        batteryPercentage: Int,
        assumedDrainPerHour: Float = DEFAULT_HOURLY_DRAIN
    ): Float {
        return if (batteryPercentage <= 0 || assumedDrainPerHour <= 0) {
            0f
        } else {
            batteryPercentage / assumedDrainPerHour
        }
    }

    /**
     * 创建默认电池信息（用于错误情况）
     */
    private fun createDefaultBatteryInfo(): BatteryInfo {
        return BatteryInfo(
            level = UNKNOWN_VALUE,
            scale = UNKNOWN_VALUE,
            health = context.getString(R.string.unknown),
            voltage = UNKNOWN_VALUE,
            temperature = 0f,
            technology = context.getString(R.string.unknown),
            capacity = 0,
            availableTime = context.getString(R.string.unknown)
        )
    }

    /**
     * 验证电池电量和刻度是否有效
     */
    private fun isValidLevelAndScale(level: Int, scale: Int): Boolean {
        return level >= MIN_VALID_LEVEL && scale > 0
    }

    /**
     * 验证电池电量是否有效
     */
    private fun isValidBatteryLevel(level: Int): Boolean {
        return level in MIN_VALID_LEVEL..MAX_VALID_LEVEL
    }

    /**
     * 验证电荷计数器数据是否有效
     */
    private fun isValidChargeCounterData(chargeCounter: Long, capacityPercent: Int): Boolean {
        return chargeCounter != Long.MIN_VALUE &&
                capacityPercent != Int.MIN_VALUE &&
                capacityPercent > 0
    }

    // endregion
}