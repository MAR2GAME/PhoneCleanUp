package com.cleanphone.freespace.utility.ui

import android.R.attr.endY
import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.bean.NetworkInfoSnapshot
import com.cleanphone.freespace.utility.bean.SpeedTestStage
import com.cleanphone.freespace.utility.databinding.ActivityNetworkSpeedBinding
import com.cleanphone.freespace.utility.utils.NetworkAvailability
import com.cleanphone.freespace.utility.utils.NetworkSpeedFormatter
import com.cleanphone.freespace.utility.utils.NetworkSpeedInfoProvider
import com.cleanphone.freespace.utility.utils.NetworkSpeedNavigationGate
import com.cleanphone.freespace.utility.utils.NetworkSpeedTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.get

class NetworkSpeedActivity : BaseViewActivity<ActivityNetworkSpeedBinding>() {

    companion object {
        private const val DOT_ANIMATION_MAX = 3
        private const val DOT_ANIMATION_DELAY_MS = 500L
        private const val SPEED_TEST_TIMEOUT_MS = 15_000L
        private const val SPEED_TEST_LOG_TAG = "NetworkSpeedTest"
    }

    private val speedTester = NetworkSpeedTester()
    private val navigationGate = NetworkSpeedNavigationGate()
    private var speedTestJob: Job? = null
    private var speedButtonDotsJob: Job? = null
    private var isLeavingPage = false

//    private var downloadAnimator: ObjectAnimator? = null
//    private var uploadAnimator: ObjectAnimator? = null

    override fun initializeActivity() {
        super.initializeActivity()
        setupClickListeners()
        setupBackPressHandler()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }
    }

    override fun onResume() {
        super.onResume()
        renderNetworkInfo(NetworkSpeedInfoProvider.getSnapshot(this))


    }

    private fun renderNetworkInfo(snapshot: NetworkInfoSnapshot) {
        binding.tvNetworkTypeValue.text = snapshot.networkType
        binding.tvWifiNameValue.text = snapshot.wifiName
        binding.tvIpValue.text = snapshot.ipAddress
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    fun onBack(){
        readyGoThenKill(MainActivity::class.java)
    }


    private fun setupClickListeners() {
        binding.llBack.safeClick {  onBack() }
        binding.btnRunSpeedTest.safeClick { runSpeedTest() }

    }

    private fun renderFailureState() {

        binding.tvStageHint.text = getString(R.string.network_speed_failed)
        binding.tvDownloadValue.text = formatSpeed(null)
        binding.tvUploadValue.text = formatSpeed(null)
    }

    private fun formatSpeed(value: Double?): String = NetworkSpeedFormatter.formatSpeedMbps(
        value,
        resources.configuration.locales[0]
    )

    private fun canRenderSpeedTest(): Boolean = !isLeavingPage && !isFinishing && !isDestroyed
    private fun runSpeedTest() {
        if (speedTestJob?.isActive == true) return
        if (!NetworkAvailability.hasValidatedInternet(this)) {
            renderFailureState()
            return
        }
        speedTestJob = lifecycleScope.launch {
            setTestingUi(true)
         //   startAnimation()
            binding.tvDownloadValue.text = formatSpeed(null)
            binding.tvUploadValue.text = formatSpeed(null)
            val result = try {
                speedTester.runTest(
                    totalTimeoutMs = SPEED_TEST_TIMEOUT_MS,
                    onStageChanged = { stage ->
                        withContext(Dispatchers.Main.immediate) {
                            if (canRenderSpeedTest()) renderStage(stage)
                        }
                    },
                    onProgress = { stage, speed ->
                        withContext(Dispatchers.Main.immediate) {
                            if (canRenderSpeedTest()) renderProgress(stage, speed)
                        }
                    },
                    onFailure = { stage, attempt, reason ->
                        Log.w(
                            SPEED_TEST_LOG_TAG,
                            "transfer_failed direction=$stage attempt=$attempt reason=$reason"
                        )
                    }
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                null
            }
         //   stopDownloadAnimation()
            if (!canRenderSpeedTest()) return@launch
            setTestingUi(false)
            if (result != null) {
                renderStage(SpeedTestStage.COMPLETED)
                navigationGate.submit(result)
                navigateToPendingResult()
            } else {
                renderFailureState()
            }
        }
    }

//    private fun startAnimation() {
//        binding.ivDownload.post {
//            val parent = binding.ivDownload.parent as ViewGroup
//            val startY = -binding.ivDownload.top.toFloat()
//            val endY =
//                parent.height - binding.ivDownload.top - binding.ivDownload.height.toFloat()             // 到下方完全移出
//
//            downloadAnimator=ObjectAnimator.ofFloat(binding.ivDownload, "translationY", startY, endY).apply {
//                duration = 2000                                 // 单次动画时长（可调）
//                repeatCount = ObjectAnimator.INFINITE           // 无限循环
//                repeatMode = ObjectAnimator.RESTART             // 每次从起点重新开始
//                start()
//            }
//        }
//
//        binding.ivUpload.post {
//            val parent = binding.ivUpload.parent as ViewGroup
//            val startY =
//                parent.height - binding.ivUpload.top - binding.ivUpload.height.toFloat()
//            val endY = -binding.ivUpload.top.toFloat()
//                  // 到下方完全移出
//            uploadAnimator=ObjectAnimator.ofFloat(binding.ivUpload, "translationY", startY, endY).apply {
//                duration = 2000                                 // 单次动画时长（可调）
//                repeatCount = ObjectAnimator.INFINITE           // 无限循环
//                repeatMode = ObjectAnimator.RESTART             // 每次从起点重新开始
//                start()
//            }
//        }
//
//    }
//    private fun stopDownloadAnimation() {
//        downloadAnimator?.cancel()   // 停止动画，图标停留在当前位置
//        uploadAnimator?.cancel()
//
//    }

    private fun renderProgress(stage: SpeedTestStage, speedMbps: Double) {
        when (stage) {
            SpeedTestStage.DOWNLOADING -> {
                binding.tvDownloadValue.text = formatSpeed(speedMbps)
            }

            SpeedTestStage.UPLOADING -> {
                binding.tvUploadValue.text = formatSpeed(speedMbps)
            }

            else -> Unit
        }
    }

    private fun navigateToPendingResult() {
        val result = navigationGate.consumeIfResumed(
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) ?: return
        try {
            AdvertisePageMediator.showNetworkSpeedFinishInterstitial(this@NetworkSpeedActivity){
                var bundle = Bundle()
                bundle.putString("downloadMbps", binding.tvDownloadValue.text.toString())
                bundle.putString("uploadMbps", binding.tvUploadValue.text.toString())
                readyGoThenKill(NetworkSpeedResultActivity::class.java, bundle)
                isLeavingPage = true
            }
        } catch (_: ActivityNotFoundException) {
            isLeavingPage = false
            setTestingUi(false)
            renderFailureState()
        }
    }

    private fun renderStage(stage: SpeedTestStage) {
        binding.tvStageHint.text = when (stage) {
            SpeedTestStage.IDLE -> getString(R.string.network_speed_ready)
            SpeedTestStage.DOWNLOADING -> getString(R.string.network_speed_testing_download)
            SpeedTestStage.UPLOADING -> getString(R.string.network_speed_testing_upload)
            SpeedTestStage.COMPLETED -> getString(R.string.network_speed_completed)
        }
    }

    private fun setTestingUi(isTesting: Boolean) {
        binding.btnRunSpeedTest.isEnabled = !isTesting
        if (isTesting) {
            startSpeedButtonDotsAnimation(getString(R.string.network_speed_running))
        } else {
            stopSpeedButtonDotsAnimation()
            binding.btnRunSpeedTest.text = getString(R.string.network_speed_run_test)
        }
    }

    private fun stopSpeedButtonDotsAnimation() {
        speedButtonDotsJob?.cancel()
        speedButtonDotsJob = null
    }

    private fun cancelSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = null
        stopSpeedButtonDotsAnimation()
    }

    override fun onDestroy() {
        isLeavingPage = true
        cancelSpeedTest()
        super.onDestroy()
    }

    private fun startSpeedButtonDotsAnimation(buttonText: String) {
        speedButtonDotsJob?.cancel()
        val baseButtonText = buttonText.trimEnd('.')
        if (baseButtonText == buttonText) {
            binding.btnRunSpeedTest.text = buttonText
            return
        }
        speedButtonDotsJob = lifecycleScope.launch {
            var dotCount = 1
            while (true) {
                binding.btnRunSpeedTest.text = createFixedDotsText(baseButtonText, dotCount)
                delay(DOT_ANIMATION_DELAY_MS)
                dotCount = if (dotCount == DOT_ANIMATION_MAX) 1 else dotCount + 1
            }
        }
    }

    private fun createFixedDotsText(baseText: String, dotCount: Int): SpannableString {
        val fullText = baseText + ".".repeat(DOT_ANIMATION_MAX)
        return SpannableString(fullText).apply {
            val hiddenDotsStart = baseText.length + dotCount
            if (hiddenDotsStart < fullText.length) {
                setSpan(
                    ForegroundColorSpan(Color.TRANSPARENT),
                    hiddenDotsStart,
                    fullText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

}