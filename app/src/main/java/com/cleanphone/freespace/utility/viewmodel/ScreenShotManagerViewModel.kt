package com.cleanphone.freespace.utility.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.viewmodel.BaseViewModel
import com.cleanphone.freespace.utility.bean.Screenshot
import com.cleanphone.freespace.utility.bean.ScreenshotsData
import com.cleanphone.freespace.utility.utils.SizeUtil
import kotlin.collections.isNotEmpty

class ScreenShotManagerViewModel(application: Application) : BaseViewModel(application) {

    private val _screenshotsData = MutableLiveData<ScreenshotsData>()
    val screenshotsData: LiveData<ScreenshotsData> = _screenshotsData

    private val _selectedScreenshots = MutableLiveData<MutableList<Screenshot>>()
    val selectedScreenshots: LiveData<MutableList<Screenshot>> = _selectedScreenshots

    private val _deleteSize = MutableLiveData<Long>()
    val deleteSize: LiveData<Long> = _deleteSize

    private val _isSelectAll = MutableLiveData<Boolean>()
    val isSelectAll: LiveData<Boolean> = _isSelectAll

    private val _uiState = MutableLiveData<ScreenShotUIState>()
    val uiState: LiveData<ScreenShotUIState> = _uiState

    // 用于通知Adapter数据变化的LiveData
    private val _adapterDataChanged = MutableLiveData<Boolean>()
    val adapterDataChanged: LiveData<Boolean> = _adapterDataChanged

    init {
        _selectedScreenshots.value = mutableListOf()
        _deleteSize.value = 0L
        _isSelectAll.value = false
        _uiState.value = ScreenShotUIState()
        _adapterDataChanged.value = false
    }

    fun resetAdapterDataChanged() {
        _adapterDataChanged.value = false
    }

    fun setScreenshotsData(data: ScreenshotsData) {
        _screenshotsData.value = data
        updateUIState(data)
        // 通知Adapter数据已设置
        _adapterDataChanged.value = true
    }

    fun toggleSelectAll() {
        val currentData = _screenshotsData.value?.data ?: return
        val currentIsSelectAll = _isSelectAll.value ?: false
        val newIsSelectAll = !currentIsSelectAll

        _isSelectAll.value = newIsSelectAll

        if (newIsSelectAll) {
            // 全选
            val allScreenshots = currentData.toMutableList()
            allScreenshots.forEach { it.isSelected = true }
            _selectedScreenshots.value = allScreenshots
            _deleteSize.value = currentData.sumOf { it.size }
        } else {
            // 取消全选
            currentData.forEach { it.isSelected = false }
            _selectedScreenshots.value = mutableListOf()
            _deleteSize.value = 0L
        }

        // 通知Adapter数据已改变
        _adapterDataChanged.value = true
        updateButtonState()
    }

    fun toggleScreenshotSelection(screenshot: Screenshot) {
        val currentSelected = _selectedScreenshots.value ?: mutableListOf()
        val currentData = _screenshotsData.value?.data ?: return

        if (screenshot.isSelected) {
            // 取消选择
            screenshot.isSelected = false
            currentSelected.remove(screenshot)
            _deleteSize.value = (_deleteSize.value ?: 0L) - screenshot.size
        } else {
            // 选择
            screenshot.isSelected = true
            currentSelected.add(screenshot)
            _deleteSize.value = (_deleteSize.value ?: 0L) + screenshot.size
        }

        _selectedScreenshots.value = currentSelected
        updateSelectAllState(currentData, currentSelected)
        updateButtonState()
    }

    fun getDeleteButtonText(): String {
        val selectedCount = _selectedScreenshots.value?.size ?: 0
        val currentDeleteSize = _deleteSize.value ?: 0L

        return if (selectedCount > 0) {
            application.getString(
                R.string.delete_size_format,
                formatSize(currentDeleteSize)
            )
        } else {
            application.getString(R.string.delete)
        }
    }

    fun isDeleteButtonEnabled(): Boolean {
        return (_selectedScreenshots.value?.size ?: 0) > 0
    }

    fun hasScreenshotData(): Boolean {
        return !_screenshotsData.value?.data.isNullOrEmpty()
    }

    fun getTotalSizeText(): String {
        return _screenshotsData.value?.let { data ->
            formatSize(data.totalSize)
        } ?: "0 B"
    }

    fun getScreenshotList(): List<Screenshot> {
        return _screenshotsData.value?.data ?: emptyList()
    }

    private fun updateSelectAllState(data: List<Screenshot>, selected: List<Screenshot>) {
        _isSelectAll.value = selected.isNotEmpty() && selected.size == data.size
    }

    private fun updateButtonState() {
        // 触发UI更新
        _uiState.value = _uiState.value?.copy(
            deleteButtonText = getDeleteButtonText(),
            isSelectAll = _isSelectAll.value ?: false
        )
    }

    private fun updateUIState(data: ScreenshotsData) {
        _uiState.value = ScreenShotUIState(
            hasData = data.data.isNotEmpty(),
            totalSizeText = formatSize(data.totalSize),
            deleteButtonText = getDeleteButtonText(),
            isSelectAll = _isSelectAll.value ?: false
        )
    }

    fun clearSelection() {
        _screenshotsData.value?.data?.forEach { it.isSelected = false }
        _selectedScreenshots.value = mutableListOf()
        _deleteSize.value = 0L
        _isSelectAll.value = false
        // 通知Adapter数据已改变
        _adapterDataChanged.value = true
        updateButtonState()
    }

    private fun formatSize(size: Long): String = SizeUtil.formatSize(
        size
    )
}

data class ScreenShotUIState(
    val hasData: Boolean = false,
    val totalSizeText: String = "0 B",
    val deleteButtonText: String = "",
    val isSelectAll: Boolean = false
)
