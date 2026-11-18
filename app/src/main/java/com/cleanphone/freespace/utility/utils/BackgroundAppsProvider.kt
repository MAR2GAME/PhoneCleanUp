package com.cleanphone.freespace.utility.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.cleanphone.freespace.utility.bean.RunningAppInfo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext


class BackgroundAppsProvider
    (private val context: Context) {

    private val packageManager: PackageManager by lazy { context.packageManager }
    private val currentAppPackage: String by lazy { context.packageName }

    companion object {
        private const val TEST_URL = "http://www.example.com"
        private const val PACKAGE_MANAGER_FLAGS = PackageManager.MATCH_ALL

        // Intent actions and categories for better reusability
        private val LAUNCHER_INTENT = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        private val BROWSER_INTENT = Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }

    /**
     * 获取运行中的非系统应用列表
     */
    suspend fun getRunningApps(): List<RunningAppInfo> = withContext(Dispatchers.IO) {
        try {
            // 并行获取两种类型的应用列表
            val launcherAppsDeferred = async { getLauncherApps() }
            val specificTypeAppsDeferred = async { getSpecificTypeApps() }

            // 合并结果并去重
            val allApps = launcherAppsDeferred.await() + specificTypeAppsDeferred.await()
            allApps.distinctBy { it.packageName }
        } catch (e: Exception) {

            emptyList()
        }
    }

    /**
     * 获取启动器中的应用
     */
    private fun getLauncherApps(): List<RunningAppInfo> =
        queryAppsByIntent(LAUNCHER_INTENT)

    /**
     * 获取特定类型的应用（如浏览器）
     */
    private fun getSpecificTypeApps(): List<RunningAppInfo> =
        queryAppsByIntent(BROWSER_INTENT)

    /**
     * 根据Intent查询应用列表
     */
    private fun queryAppsByIntent(intent: Intent): List<RunningAppInfo> = runCatching {
        packageManager.queryIntentActivities(intent, PACKAGE_MANAGER_FLAGS)
            .mapNotNull { resolveInfo ->
                runCatching {
                    createAppInfo(resolveInfo.activityInfo.packageName)
                }.getOrElse { exception ->
                    null
                }
            }
    }.getOrDefault(emptyList())

    /**
     * 创建应用信息对象
     */
    private fun createAppInfo(packageName: String): RunningAppInfo? {
        // 快速过滤当前应用
        if (packageName == currentAppPackage) return null

        return runCatching {
            val applicationInfo = getApplicationInfo(packageName) ?: return null
            RunningAppInfo(
                packageName = packageName,
                appName = getAppLabel(applicationInfo),
                icon = getAppIcon(applicationInfo)
            )
        }.getOrElse { exception ->
            null
        }
    }

    /**
     * 获取应用信息，包含过滤逻辑
     */
    private fun getApplicationInfo(packageName: String): ApplicationInfo? = runCatching {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        // 排除系统应用和已停止的应用
        if (shouldExcludeApp(applicationInfo)) {
            return null
        }
        applicationInfo
    }.getOrElse { exception ->
        null
    }

    /**
     * 获取应用标签
     */
    private fun getAppLabel(applicationInfo: ApplicationInfo): String = runCatching {
        applicationInfo.loadLabel(packageManager).toString()
    }.getOrDefault(applicationInfo.packageName) // 降级方案：使用包名

    /**
     * 获取应用图标
     */
    private fun getAppIcon(applicationInfo: ApplicationInfo) = runCatching {
        applicationInfo.loadIcon(packageManager)
    }.getOrNull()

    /**
     * 判断是否应该排除该应用
     */
    private fun shouldExcludeApp(applicationInfo: ApplicationInfo): Boolean =
        isSystemApp(applicationInfo) || isStoppedApp(applicationInfo)

    /**
     * 判断是否为系统应用
     */
    private fun isSystemApp(applicationInfo: ApplicationInfo): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    /**
     * 判断是否为已停止的应用
     */
    private fun isStoppedApp(applicationInfo: ApplicationInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (applicationInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
}