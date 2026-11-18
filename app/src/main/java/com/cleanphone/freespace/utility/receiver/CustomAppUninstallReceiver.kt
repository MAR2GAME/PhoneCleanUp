package com.cleanphone.freespace.utility.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.lang.ref.WeakReference

class CustomAppUninstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppUninstallReceiver"
    }

    // 使用弱引用避免内存泄漏
    private var listenerWeakRef: WeakReference<OnUninstallListener>? = null

    // 添加多个监听器支持
    private val listeners = mutableSetOf<OnUninstallListener>()

    // 添加接口替代函数类型，提高可读性和可维护性
    fun interface OnUninstallListener {
        fun onAppUninstalled(packageName: String)
    }

    /**
     * 设置单个监听器（替换之前的监听器）
     */
    fun setOnUninstallListener(listener: OnUninstallListener) {
        this.listenerWeakRef = WeakReference(listener)
    }

    /**
     * 添加监听器（支持多个监听器）
     */
    fun addOnUninstallListener(listener: OnUninstallListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * 移除监听器
     */
    fun removeOnUninstallListener(listener: OnUninstallListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /**
     * 清空所有监听器
     */
    fun clearAllListeners() {
        synchronized(listeners) {
            listeners.clear()
        }
        listenerWeakRef = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED -> handlePackageRemoved(intent)
                Intent.ACTION_PACKAGE_FULLY_REMOVED -> handlePackageFullyRemoved(intent)
                // 可以轻松扩展其他action
            }
        } catch (e: Exception) {
            // 添加异常处理，防止崩溃
            // Log.e(TAG, "Error in AppUninstallReceiver", e)
        }
    }

    private fun handlePackageRemoved(intent: Intent) {
        // 检查是否是替换安装（升级），只有完全卸载才处理
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            return
        }
        processUninstallEvent(intent)
    }

    private fun handlePackageFullyRemoved(intent: Intent) {
        // 完全移除事件（某些系统版本）
        processUninstallEvent(intent)
    }

    private fun processUninstallEvent(intent: Intent) {
        val packageName = getPackageNameFromIntent(intent) ?: return

        // 通知单个监听器（向后兼容）
        listenerWeakRef?.get()?.onAppUninstalled(packageName)

        // 通知所有监听器
        synchronized(listeners) {
            listeners.forEach { listener ->
                try {
                    listener.onAppUninstalled(packageName)
                } catch (e: Exception) {
                    // 防止某个监听器的异常影响其他监听器
                    // Log.e(TAG, "Error notifying uninstall listener", e)
                }
            }
        }
    }

    private fun getPackageNameFromIntent(intent: Intent): String? {
        return try {
            intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            // Log.e(TAG, "Error extracting package name from intent", e)
            null
        }
    }
}