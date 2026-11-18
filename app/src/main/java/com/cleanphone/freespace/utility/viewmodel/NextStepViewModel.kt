package com.cleanphone.freespace.utility.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.viewmodel.BaseViewModel
import com.cleanphone.freespace.utility.bean.NextConfig

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NextStepViewModel(application: Application) : BaseViewModel(application) {

    private val _configuration = MutableLiveData<NextConfig?>()
    val configuration: LiveData<NextConfig?> get() = _configuration

    private val _counterText = MutableLiveData<String>()
    val counterText: LiveData<String> get() = _counterText

    private val _shouldNavigateToFinished = MutableLiveData<Boolean>()
    val shouldNavigateToFinished: LiveData<Boolean> get() = _shouldNavigateToFinished

    var isToFinished = false
    private var counterJob: Job? = null

    private val configMap by lazy {
        mapOf(
            application.getString(R.string.process_manager) to NextConfig(
                backgroundColor = R.color.process_scan_bg,
                animationRes = R.raw.processclean,
                needCounter = true
            )
            ,
            application.getString(R.string.app_manager) to NextConfig(
                backgroundColor = R.color.app_scan_bg,
                animationRes = R.raw.appclean,
                needCounter = false
            ),
            application.getString(R.string.battery_info) to NextConfig(
                backgroundColor = R.color.battery_scan_bg,
                animationRes = R.raw.batteryclean,
                needCounter = false
            ),
            application.getString(R.string.screenshot_manager) to NextConfig(
                backgroundColor = R.color.large_scan_bg,
                animationRes = R.raw.docclean,
                needCounter = false
            ),
            application.getString(R.string.large_file_cleaner) to createLargeFileConfig(),
            application.getString(R.string.images) to createLargeFileConfig(),
            application.getString(R.string.video) to createLargeFileConfig(),
            application.getString(R.string.audio) to createLargeFileConfig(),
            application.getString(R.string.apk) to createLargeFileConfig()

        )
    }

    fun setupConfiguration(tag: String?) {
        _configuration.value = tag?.let { configMap[it] }
    }

    fun handleAnimationEnd() {
        if (!isToFinished) {
            _shouldNavigateToFinished.value = true
        }
    }

    fun startCounterAnimation() {
        counterJob?.cancel()
        counterJob = viewModelScope.launch {
            for (i in 100 downTo 0) {
                _counterText.value = "$i%"
                delay(COUNTER_ANIMATION_DELAY)
            }
        }
    }

    override fun onCleared() {
        cleanupResources()

        super.onCleared()
    }



    fun cleanupResources() {
        counterJob?.cancel()
        counterJob = null
    }
//
    private fun createLargeFileConfig() = NextConfig(
        backgroundColor = R.color.large_scan_bg,
        animationRes = R.raw.docclean,
        needCounter = false
    )
//
//    private fun createFileManagerConfig() = NextConfig(
//        backgroundColor = R.color.large_bg,
//        animationRes = R.raw.files_next,
//        needCounter = false
//    )

    companion object {
        private const val COUNTER_ANIMATION_DELAY = 30L
    }
}
