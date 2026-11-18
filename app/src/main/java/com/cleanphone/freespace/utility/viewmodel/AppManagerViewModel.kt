package com.cleanphone.freespace.utility.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.viewmodel.BaseViewModel
import com.cleanphone.freespace.utility.bean.AppInfo
import com.cleanphone.freespace.utility.bean.SortRule
import kotlin.collections.filterNot
import kotlin.collections.sortedBy
import kotlin.collections.sortedByDescending

class AppManagerViewModel(application: Application) : BaseViewModel(application) {

    private val _appList = MutableLiveData<List<AppInfo>>()
    val appList: LiveData<List<AppInfo>> get() = _appList

    private val _currentSortRule = MutableLiveData<SortRule>(SortRule.SIZE_DESC)
    val currentSortRule: LiveData<SortRule> get() = _currentSortRule

    private val _sortUIState = MutableLiveData<SortUIState>()
    val sortUIState: LiveData<SortUIState> get() = _sortUIState

    // 缓存原始数据避免重复计算
    private var cachedAppList: List<AppInfo> = emptyList()

    init {
        // 初始化排序UI状态
        updateSortUIState(SortRule.SIZE_DESC)
    }

    fun handleInstallAppEvent(appList: List<AppInfo>) {
        cachedAppList = appList
        applyCurrentSortRule()
    }

    fun handleAppUninstalled(packageName: String) {
        cachedAppList = cachedAppList.filterNot { it.packageName == packageName }
        applyCurrentSortRule()
    }

    fun handleSizeSortClick() {
        if (cachedAppList.isEmpty()) return
        val newSortRule = when (_currentSortRule.value) {
            SortRule.SIZE_DESC -> {
                sortAppsBy { it.size }
                SortRule.SIZE_ASC
            }
            else -> {
                sortAppsByDescending { it.size }
                SortRule.SIZE_DESC
            }
        }
        _currentSortRule.value = newSortRule
        updateSortUIState(newSortRule)
    }

    fun handleTimeSortClick() {
        if (cachedAppList.isEmpty()) return
        val newSortRule = when (_currentSortRule.value) {
            SortRule.INSTALL_TIME_DESC -> {
                sortAppsBy { it.firstInstallTime }
                SortRule.INSTALL_TIME_ASC
            }
            else -> {
                sortAppsByDescending { it.firstInstallTime }
                SortRule.INSTALL_TIME_DESC
            }
        }

        _currentSortRule.value = newSortRule
        updateSortUIState(newSortRule)
    }

    private fun applyCurrentSortRule() {
        when (_currentSortRule.value) {
            SortRule.SIZE_DESC -> sortAppsByDescending { it.size }
            SortRule.SIZE_ASC -> sortAppsBy { it.size }
            SortRule.INSTALL_TIME_DESC -> sortAppsByDescending { it.firstInstallTime }
            SortRule.INSTALL_TIME_ASC -> sortAppsBy { it.firstInstallTime }
            else -> sortAppsByDescending { it.size } // 默认排序
        }
    }

    private inline fun <T : Comparable<T>> sortAppsBy(crossinline selector: (AppInfo) -> T) {
        _appList.value = cachedAppList.sortedBy(selector)
    }

    private inline fun <T : Comparable<T>> sortAppsByDescending(crossinline selector: (AppInfo) -> T) {
        _appList.value = cachedAppList.sortedByDescending(selector)
    }

    private fun updateSortUIState(sortRule: SortRule) {
        val state = when (sortRule) {
            SortRule.SIZE_DESC -> SortUIState(
                sizeIconRes = R.mipmap.ic_sort_desc,
                timeIconRes = R.mipmap.ic_sort_nor
            )
            SortRule.SIZE_ASC -> SortUIState(
                sizeIconRes = R.mipmap.ic_sort_esc,
                timeIconRes = R.mipmap.ic_sort_nor
            )
            SortRule.INSTALL_TIME_DESC -> SortUIState(
                sizeIconRes = R.mipmap.ic_sort_nor,
                timeIconRes = R.mipmap.ic_sort_desc
            )
            SortRule.INSTALL_TIME_ASC -> SortUIState(
                sizeIconRes = R.mipmap.ic_sort_nor,
                timeIconRes = R.mipmap.ic_sort_esc
            )
        }
        _sortUIState.value = state
    }

    data class SortUIState(
        val sizeIconRes: Int,
        val timeIconRes: Int
    )
}