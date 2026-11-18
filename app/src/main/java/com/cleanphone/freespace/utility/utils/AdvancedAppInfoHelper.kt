package com.cleanphone.freespace.utility.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.cleanphone.freespace.utility.bean.AppInfo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.apply
import kotlin.collections.mapNotNullTo

import kotlin.collections.toList
import kotlin.getOrDefault
import kotlin.runCatching

class AdvancedAppInfoHelper(private val context: Context) {

    companion object {
        private val EXCLUDE_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.gms",
            "com.android.settings"
        )
        private const val ANDROID_PACKAGE_PREFIX = "com.android."
    }

    // 获取非系统应用列表（优化性能，并行处理）
    suspend fun getNonSystemApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        coroutineScope {
            // 并行获取不同来源的应用列表
            val launcherAppsDeferred = async { getAppsByIntentCategory(Intent.CATEGORY_LAUNCHER) }
            val browsableAppsDeferred = async { getAppsByIntentCategory(Intent.CATEGORY_BROWSABLE) }

            // 等待两个任务完成并合并结果
            val apps = (launcherAppsDeferred.await() + browsableAppsDeferred.await())
                .distinctBy { it.packageName } // 使用distinctBy去重，性能更好
                .sortedBy { it.name.lowercase() } // 按名称排序

            apps
        }
    }

    // 通过Intent分类获取应用
    private suspend fun getAppsByIntentCategory(category: String): List<AppInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val intent = when (category) {
                    Intent.CATEGORY_LAUNCHER -> Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    Intent.CATEGORY_BROWSABLE -> Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(TEST_URL)
                    ).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    else -> return@runCatching emptyList()
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                } else {
                    null
                }

                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && flags != null) {
                    context.packageManager.queryIntentActivities(intent, flags)
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                }

                resolveInfos.mapNotNullTo(mutableListOf()) { resolveInfo ->
                    createAppInfoIfAllowed(resolveInfo.activityInfo.packageName)
                }
            }.getOrDefault(emptyList())
        }

    // 创建应用信息（如果允许）
    private fun createAppInfoIfAllowed(packageName: String): AppInfo? {
        if (shouldSkipPackage(packageName)) return null

        return runCatching {
            createAppInfo(packageName)
        }.getOrNull()
    }

    // 判断是否应该跳过该包
    private fun shouldSkipPackage(packageName: String): Boolean {
        return packageName == context.packageName ||
                packageName in EXCLUDE_PACKAGES ||
                packageName.isEmpty() ||
                packageName.startsWith(ANDROID_PACKAGE_PREFIX) ||
                packageName.startsWith("android.") ||
                packageName.startsWith("com.google.android.apps.")
    }

    // 创建应用信息对象
    private fun createAppInfo(packageName: String): AppInfo? {
        val packageManager = context.packageManager
        val applicationInfo = packageManager.getApplicationInfoSafely(packageName) ?: return null

        // 排除系统应用
        if (applicationInfo.isSystemApp()) return null

        val packageInfo = packageManager.getPackageInfoSafely(packageName) ?: return null
        val appName = applicationInfo.loadLabel(packageManager).toString()
        val icon = applicationInfo.loadIcon(packageManager)
        val size = calculateAppSize(applicationInfo)
        val installTime = packageInfo.firstInstallTimeCompat

        return AppInfo(
            packageName = packageName,
            name = appName,
            icon = icon,
            size = size,
            firstInstallTime = installTime
        )
    }

    // 判断是否为系统应用（扩展函数）
    private fun ApplicationInfo.isSystemApp(): Boolean {
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    // 计算应用大小
    private fun calculateAppSize(applicationInfo: ApplicationInfo): Long {
        return runCatching {
            val appFile = File(applicationInfo.publicSourceDir ?: return@runCatching 0L)
            if (appFile.exists()) appFile.length() else 0L
        }.getOrDefault(0L)
    }

    // PackageManager扩展函数：安全获取ApplicationInfo
    private fun PackageManager.getApplicationInfoSafely(packageName: String): ApplicationInfo? {
        return runCatching {
            getApplicationInfo(packageName, 0)
        }.getOrNull()
    }

    // PackageManager扩展函数：安全获取PackageInfo（兼容API）
    private fun PackageManager.getPackageInfoSafely(packageName: String): PackageInfo? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                getPackageInfo(packageName, 0)
            }
        }.getOrNull()
    }

    // PackageInfo扩展属性：兼容的首次安装时间
    private val PackageInfo.firstInstallTimeCompat: Long
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            firstInstallTime
        } else {
            @Suppress("DEPRECATION")
            firstInstallTime
        }
}

// 在另一个文件创建扩展函数（保持类的简洁）
private const val TEST_URL = "https://www.youtube.com/news"