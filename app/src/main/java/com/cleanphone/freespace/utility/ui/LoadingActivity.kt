package com.cleanphone.freespace.utility.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseMvvmActivity
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.InstallAppEvent
import com.cleanphone.freespace.utility.bean.ProcessEvent
import com.cleanphone.freespace.utility.bean.SortRule
import com.cleanphone.freespace.utility.databinding.ActivityLoadingBinding
import com.cleanphone.freespace.utility.viewmodel.LoadingViewModel
import com.cleanphone.freespace.utility.bean.LargeFileEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import kotlin.jvm.java

class LoadingActivity : BaseMvvmActivity<ActivityLoadingBinding, LoadingViewModel>() {
    companion object {
        private const val PROGRESS_TOTAL = 100
        private const val DOT_ANIMATION_MAX = 4
        private const val DOT_ANIMATION_DELAY_MS = 300L
    }
    private var startTime: Long = 0
    private var currentTag: String? = null
    private var hintAnimationJob: Job? = null
    private val tagConfigMap by lazy {
        mapOf(
            R.string.process_manager to LoadingConfig(
                animationRes = R.raw.processscan,
                hintTextRes = R.string.scanning_for_running_process,
                progressTime = 40L,
                backgroundColorRes = R.color.process_scan_bg,
                logEvent = "processman",
                setupAction = { viewModel.loadRunningApps() }
            ),
            R.string.app_manager to LoadingConfig(
                animationRes = R.raw.appscan,
                hintTextRes = R.string.scanning_apps,
                progressTime = 40L,
                backgroundColorRes = R.color.app_scan_bg,
                logEvent = "appman",
                setupAction = { viewModel.loadInstalledApps(SortRule.SIZE_DESC) }
            ),
            R.string.speaker_cleaner to LoadingConfig(
                animationRes = R.raw.speekscan,
                hintTextRes = R.string.loading_the_components_of_speaker_cleaner,
                progressTime = 30L,
                backgroundColorRes = R.color.speaker_scan_bg,
                logEvent = "speaker",
                targetActivity = SpeakerCleanerActivity::class.java
            ),
            R.string.battery_info to LoadingConfig(
                animationRes = R.raw.batteryscan,
                hintTextRes = R.string.scanning_for_battery_info,
                progressTime = 30L,
                backgroundColorRes = R.color.battery_scan_bg,
                logEvent = "battery",
                targetActivity = BatteryInfoActivity::class.java
            ),
            R.string.large_file_cleaner to createFileManagerConfig(R.string.scanning_large_file, "largefile"),
            R.string.images to createFileManagerConfig(R.string.scanning_images, "images"),
            R.string.video to createFileManagerConfig(R.string.scanning_videos, "video"),
            R.string.audio to createFileManagerConfig(R.string.scanning_audio_files, "audio"),
            R.string.apk to createFileManagerConfig(R.string.scanning_apk_files, "apk"),
            R.string.screenshot_manager to LoadingConfig(
                animationRes = R.raw.docscan,
                hintTextRes = R.string.scanning_screenshots,
                progressTime = 40L,
                logEvent = "screenshotman",
                backgroundColorRes = R.color.large_scan_bg,
                setupAction = { viewModel.getScreenshotData() }
            )
        )
    }


    private fun createFileManagerConfig(hintRes: Int, eventName: String) = LoadingConfig(
        animationRes = R.raw.docscan,
        hintTextRes = hintRes,
        progressTime = 40L,
        backgroundColorRes = R.color.large_scan_bg,
        logEvent = eventName,
        setupAction = { viewModel.startScanFiles(contentResolver, currentTag) }
    )



    override fun init() {
        initView()


    }
    fun initView() {
        binding.ivLoading.imageAssetsFolder = "images/"
        initArguments()
        initViewModel()
        setupBackPressHandler()
        handleTagNavigation()
    }

    private fun handleTagNavigation() {
        val config = tagConfigMap.entries.firstOrNull { getString(it.key) == currentTag }?.value
        if (config == null) {
            finish()
            return
        }
        setupUI(config)
        config.setupAction?.invoke()
    }


    @SuppressLint("SetTextI18n")
    private fun startHintDotsAnimation(hintText: String) {
        hintAnimationJob?.cancel()
        val baseHintText = hintText.trimEnd('.')
        if (baseHintText == hintText) {
            binding.tvHint.text = hintText
            return
        }
        hintAnimationJob = lifecycleScope.launch {
            var dotCount = 1
            while (true) {
                binding.tvHint.text = baseHintText + ".".repeat(dotCount)
                delay(DOT_ANIMATION_DELAY_MS)
                dotCount = if (dotCount == DOT_ANIMATION_MAX) 1 else dotCount + 1
            }
        }
    }

    override fun onDestroy() {
        hintAnimationJob?.cancel()
        hintAnimationJob=null
        super.onDestroy()
    }
    private fun setupUI(config: LoadingConfig) {
        binding.clContent.setBackgroundColor(getColor(config.backgroundColorRes))
        binding.ivLoading.setAnimation(config.animationRes)
        startHintDotsAnimation(getString(config.hintTextRes))
        startCounterAnimation(config)
    }
    @SuppressLint("SetTextI18n")
    private fun startCounterAnimation(config: LoadingConfig) {
        lifecycleScope.launch {
            for (i in 0..PROGRESS_TOTAL) {
                delay(config.progressTime)
                binding.tvProgress.text = "$i%"
            }
            config.targetActivity?.let {
                navigateWithDelay(null, it)
            }
        }
    }
    private fun initArguments() {
        currentTag = intent.extras?.getString(AppConfig.NEXT_TAG)
        startTime = System.currentTimeMillis()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@LoadingActivity,
                    getString(R.string.please_wait_with_status, binding.tvHint.text),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun initViewModel() {
        with(viewModel) {
            runningAppInfo.observe(this@LoadingActivity) { apps ->
                navigateWithDelay(ProcessEvent(apps), ProcessManagerActivity::class.java)
            }
            _appList.observe(this@LoadingActivity) { appList ->
                navigateWithDelay(InstallAppEvent(appList), AppManagerActivity::class.java)
            }
            largeFilesData.observe(this@LoadingActivity) { result ->
                navigateWithDelay(LargeFileEvent(result.files,result.totalSize), LargeFileManagerActivity::class.java)
            }
            screenshotData.observe(this@LoadingActivity) { screenshots ->
                navigateWithDelay(screenshots, ScreenShotManagerActivity::class.java)
            }
        }
    }

    private fun <T> navigateWithDelay(event: T, targetActivity: Class<*>) {
        lifecycleScope.launch {
            val elapsedTime = System.currentTimeMillis() - startTime
            val remainingDelay = maxOf(0, 3000 - elapsedTime)
            if (remainingDelay > 0) {
                delay(remainingDelay)
            }
            if (event != null) {
                EventBus.getDefault().postSticky(event)
            }
            readyGoThenKill(targetActivity, intent.extras?.let { Bundle(it) })
        }
    }


    private data class LoadingConfig(
        val animationRes: Int,
        val hintTextRes: Int,
        val progressTime: Long,
        val backgroundColorRes: Int,
        val logEvent: String,
        val targetActivity: Class<*>? = null,
        val setupAction: (() -> Unit)? = null
    )
}