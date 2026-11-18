package com.cleanphone.freespace.utility.viewmodel

import android.app.Application
import android.content.ContentResolver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.viewmodel.BaseViewModel
import com.cleanphone.freespace.utility.bean.AppInfo
import com.cleanphone.freespace.utility.bean.LargeFile
import com.cleanphone.freespace.utility.bean.RunningAppInfo
import com.cleanphone.freespace.utility.bean.ScreenshotsData
import com.cleanphone.freespace.utility.bean.SortRule
import com.cleanphone.freespace.utility.utils.AdvancedAppInfoHelper
import com.cleanphone.freespace.utility.utils.BackgroundAppsProvider
import com.cleanphone.freespace.utility.utils.FileManagerCategory
import com.cleanphone.freespace.utility.utils.FileScanner
import com.cleanphone.freespace.utility.utils.ScreenshotRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoadingViewModel(application: Application) : BaseViewModel(application) {

    val runningAppInfo = MutableLiveData<List<RunningAppInfo>>()
    var largeFilesData = MutableLiveData<FileScanner.ScanResult>()
    var screenshotData = MutableLiveData<ScreenshotsData>()
    val _appList = MutableLiveData<List<AppInfo>>()

    fun loadRunningApps() {
        viewModelScope.launch {
            try {
                val runningApps = withContext(Dispatchers.IO) {
                    BackgroundAppsProvider(application).getRunningApps()
                }
                runningAppInfo.value = runningApps
            } catch (_: Exception) {
                runningAppInfo.value = mutableListOf()
            }
        }
    }

    fun loadInstalledApps(rule: SortRule) {
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    AdvancedAppInfoHelper(application).getNonSystemApps()
                }
                val sortedList = when (rule) {
                    SortRule.SIZE_DESC -> apps.sortedByDescending { it.size }
                    SortRule.SIZE_ASC -> apps.sortedBy { it.size }
                    SortRule.INSTALL_TIME_DESC -> apps.sortedByDescending { it.firstInstallTime }
                    SortRule.INSTALL_TIME_ASC -> apps.sortedBy { it.firstInstallTime }
                }
                _appList.value = sortedList
            } catch (_: Exception) {
                _appList.value = emptyList()
            }
        }
    }

    fun startScanFiles(contentResolver: ContentResolver, tag: String?) {
        viewModelScope.launch {
            try {
                val category = FileManagerCategory.fromTag(application, tag)
                val scanResult = FileScanner(
                    contentResolver,
                    application.getString(R.string.unknown)
                ).scanFiles(category)
                largeFilesData.value = scanResult
            } catch (_: Exception) {
                largeFilesData.value = FileScanner.ScanResult(emptyList<LargeFile>(),0)
            }
        }
    }

    fun getScreenshotData() {
        viewModelScope.launch {
            try {
                val screenshotRepository = ScreenshotRepository(application)
                val screenshots = screenshotRepository.loadScreenshots()
                val totalSize = screenshotRepository.totalSize
                screenshotData.value = ScreenshotsData(screenshots, totalSize)
            } catch (_: Exception) {
                screenshotData.value = ScreenshotsData(emptyList(), 0)
            }
        }
    }
}
