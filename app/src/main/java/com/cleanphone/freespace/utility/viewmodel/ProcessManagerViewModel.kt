package com.cleanphone.freespace.utility.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.base.viewmodel.BaseViewModel
import com.cleanphone.freespace.utility.bean.MemoryInfo
import com.cleanphone.freespace.utility.bean.ProcessEvent
import com.cleanphone.freespace.utility.bean.RunningAppInfo
import com.cleanphone.freespace.utility.ui.NextStepActivity
import com.cleanphone.freespace.utility.utils.AppPrefsUtils
import com.cleanphone.freespace.utility.utils.BackgroundAppsProvider
import com.cleanphone.freespace.utility.utils.MemoryUtils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.isNotEmpty
import kotlin.jvm.java

class ProcessManagerViewModel(application: Application) : BaseViewModel(application) {



    // UI State LiveData
    val memoryUsageText = MutableLiveData<String>()
    val appsCountText = MutableLiveData<String>()
    val isEmptyDataVisible = MutableLiveData<Boolean>()
    val isLoading = MutableLiveData<Boolean>()
    val appsList = MutableLiveData<List<RunningAppInfo>>()

    // Navigation Events
    val navigationEvent = MutableLiveData<NavigationData>()
    private var isInitialLoad = false

    fun recordProcessTime() {
        AppPrefsUtils.commitLong(AppConfig.PROCESS_TIME, System.currentTimeMillis())
    }

    fun onResume() {
        getMemoryInfo()
        if (isInitialLoad) {
            loadRunningApps()
        } else {
            isInitialLoad = true
        }
    }
    fun onStartClicked() {
        val bundle = Bundle().apply {
            putString(AppConfig.NEXT_TAG, getApplication<Application>().getString(R.string.process_manager))
        }
        navigateTo(NextStepActivity::class.java, NavigationDestination.NEXT_STEP, bundle)
    }



    fun onProcessEventReceived(event: ProcessEvent) {
        updateAppList(event.data)
    }

    private fun loadRunningApps() {
        isLoading.value = true
        viewModelScope.launch {
            try {
                val runningApps = withContext(Dispatchers.IO) {
                    BackgroundAppsProvider(getApplication()).getRunningApps()
                }
                isLoading.value = false
                updateAppList(runningApps)
            } catch (e: Exception) {
                isLoading.value = false
                updateAppList(emptyList())
            }
        }
    }

    private fun getMemoryInfo() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                MemoryUtils.getMemoryInfo(getApplication())
            }
            updateMemoryInfo(info)
        }
    }

    private fun updateMemoryInfo(memoryInfo: MemoryInfo) {
        memoryUsageText.value = "${memoryInfo.memoryUsage}%"
    }

    private fun updateAppList(apps: List<RunningAppInfo>) {
        val hasData = apps.isNotEmpty()
        appsCountText.value = apps.size.toString()
        isEmptyDataVisible.value = !hasData
        appsList.value = apps
    }


    private fun navigateTo(targetClass: Class<*>, destination: NavigationDestination, bundle: Bundle? = null) {
        navigationEvent.value = NavigationData(targetClass, destination, bundle)
    }

    data class NavigationData(
        val targetClass: Class<*>,
        val destination: NavigationDestination,
        val bundle: Bundle? = null
    )

    enum class NavigationDestination {
        MAIN,
        NEXT_STEP,
        SETTING_HINT
    }
}
