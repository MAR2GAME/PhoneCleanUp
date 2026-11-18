package com.cleanphone.freespace.utility.fragment

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.advertise.AdvertisePageMediator
import com.cleanphone.freespace.utility.base.command.getFunctionBundle
import com.cleanphone.freespace.utility.base.command.hasStoragePermission
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.fragment.BaseMvvmFragment
import com.cleanphone.freespace.utility.bean.StorageInfo
import com.cleanphone.freespace.utility.bean.ToScanEvent
import com.cleanphone.freespace.utility.databinding.FragmentHomeBinding
import com.cleanphone.freespace.utility.ui.AppUsageActivity
import com.cleanphone.freespace.utility.ui.NetworkScanActivity
import com.cleanphone.freespace.utility.ui.NetworkSpeedActivity
import com.cleanphone.freespace.utility.ui.NetworkUsageActivity
import com.cleanphone.freespace.utility.ui.NotificationBarActivity
import com.cleanphone.freespace.utility.ui.NotificationBarConfigActivity
import com.cleanphone.freespace.utility.utils.NotificationBarStore
import com.cleanphone.freespace.utility.utils.SizeUtil
import com.cleanphone.freespace.utility.viewmodel.HomeFragmentViewModel
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

class HomeFragment : BaseMvvmFragment<FragmentHomeBinding, HomeFragmentViewModel>() {

    private var pulseAnimator: ObjectAnimator? = null

    override fun init() {
        setupObservers()
        loadData()
        setupClickListeners()
        // 不在 init 中直接启动动画，由生命周期统一管理
    }

    override fun onResume() {
        super.onResume()
        resumePulseAnimation()
    }

    override fun onPause() {
        super.onPause()
        pausePulseAnimation()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            pausePulseAnimation()
        } else {
            resumePulseAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    private fun loadData() {
        viewModel.getStorageInfo()
    }

    private fun setupClickListeners() {
        binding.btClean.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "junkClean","referrer_name" to "homepage"))
            EventBus.getDefault().post(ToScanEvent(getString(R.string.remove_junk)))
        }
        binding.rlAppUsage.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "appUsage","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitial(requireActivity(), getString(R.string.app_usage)){
                requireActivity().readyGo(AppUsageActivity::class.java,getFunctionBundle("appUsage","homepage") )
            }
        }
        binding.rlNetworkScan.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "networkScan","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitial(requireActivity(), getString(R.string.network_scan)){
                requireActivity().readyGo(NetworkScanActivity::class.java,getFunctionBundle("networkScan","homepage"))
            }

        }
        binding.rlNetworkSpeed.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "networkSpeed","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitial(requireActivity(), getString(R.string.network_speed)){
                requireActivity().readyGo(NetworkSpeedActivity::class.java,getFunctionBundle("networkSpeed","homepage"))
            }
        }
        binding.rlNetworkUsage.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "networkUsage","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitial(requireActivity(), getString(R.string.network_usage)){
                requireActivity().readyGo(NetworkUsageActivity::class.java,getFunctionBundle("networkUsage","homepage"))
            }
        }
        binding.rlNotificationBar.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "notificationClean","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitial(requireActivity(), getString(R.string.notification_bar)){
                val dashboardState = NotificationBarStore.loadState(requireActivity())
                if (dashboardState.listenerEnabled &&
                    dashboardState.blockingEnabled &&
                    dashboardState.hasConfiguredTargets
                ) {
                    requireActivity(). readyGo(NotificationBarActivity::class.java,getFunctionBundle("notificationClean","homepage"))
                } else {
                    requireActivity().readyGo(NotificationBarConfigActivity::class.java,getFunctionBundle("notificationClean","homepage"))
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.infoData.observe(viewLifecycleOwner) { info ->
            updateStorageInfo(info)
        }
    }

    private fun updateStorageInfo(storageInfo: StorageInfo) {
        if (storageInfo.usagePercentage > 0) {
            binding.tvMemory.text =
                "${SizeUtil.formatSize3(storageInfo.usedSpace)}/${SizeUtil.formatSize3(storageInfo.totalSpace)}"
        }
    }

    /**
     * 暂停脉冲动画
     */
    private fun pausePulseAnimation() {
        pulseAnimator?.cancel()
    }

    /**
     * 恢复脉冲动画（若已停止或未创建则重新开始）
     */
    private fun resumePulseAnimation() {
        // 检查动画是否正在运行，避免重复创建
        if (pulseAnimator?.isRunning != true) {
            startPulseAnimation()
        }
    }

    /**
     * 启动脉冲动画，内部会先取消旧动画防止叠加
     */
    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.1f, 1.0f)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.btClean, scaleX).apply {
            duration = 700
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}