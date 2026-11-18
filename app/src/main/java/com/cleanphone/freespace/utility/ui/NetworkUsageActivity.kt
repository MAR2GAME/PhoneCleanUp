package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.adapter.NetworkUsagePagerAdapter
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.bean.NetworkUsageSnapshot
import com.cleanphone.freespace.utility.bean.NetworkUsageType
import com.cleanphone.freespace.utility.databinding.ActivityNetworkUsageBinding
import com.cleanphone.freespace.utility.utils.NetworkUsageFormatter
import com.cleanphone.freespace.utility.utils.NetworkUsageProvider
import com.cleanphone.freespace.utility.utils.UsageAccessUtils
import com.cleanphone.freespace.utility.utils.UsageStatsHelper
import com.cleanphone.freespace.utility.view.UsageDataPermissionDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.collections.get

class NetworkUsageActivity : BaseViewActivity<ActivityNetworkUsageBinding>(){
    private lateinit var usagePagerAdapter: NetworkUsagePagerAdapter
    private var currentType = NetworkUsageType.WIFI
    private var loadJob: Job? = null
    private var cellularTotalBytes: Long = 0L
    private var wifiTotalBytes: Long = 0L
    private var usageDataPermissionDialog: UsageDataPermissionDialog? = null

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            currentType = if (position == 0) NetworkUsageType.CELLULAR else NetworkUsageType.WIFI
            renderTabState()
        }
    }
    override fun initializeActivity() {
        super.initializeActivity()
        setupViewPager()
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
        updatePermissionState()
    }


    private fun renderTabState() {
        val isCellular = currentType == NetworkUsageType.CELLULAR
        val selectedColor = ContextCompat.getColor(this, R.color.theme_color)
        val unselectedColor = ContextCompat.getColor(this, R.color.main_caption)
        if(isCellular){
            binding.viewCellular.visibility= View.VISIBLE
            binding.viewWifi.visibility= View.GONE
        }else{
            binding.viewCellular.visibility= View.GONE
            binding.viewWifi.visibility= View.VISIBLE
        }
        binding.tvCellularLabel.setTextColor(if (isCellular) selectedColor else unselectedColor)
        binding.tvWifiLabel.setTextColor(if (!isCellular) selectedColor else unselectedColor)
    }

    private fun updatePermissionState() {
        val hasAccess = UsageAccessUtils.hasUsageAccess(this)
        if (hasAccess) {
            renderTabState()
            loadUsage()
        }else{
            if(UsageStatsHelper.hasNavigatedBefore()){
                finish()
            }else{
                if (usageDataPermissionDialog == null) {
                    usageDataPermissionDialog = UsageDataPermissionDialog(this)
                    usageDataPermissionDialog?.setOnClickListener(object :
                        UsageDataPermissionDialog.OnClickListener {
                        override fun onAllow() {
                            val success =
                                UsageStatsHelper.navigateToUsageAccessSettings(this@NetworkUsageActivity)
                            if (success) {
                                UsageStatsHelper.markNavigated()
                            } else {
                                Toast.makeText(
                                    this@NetworkUsageActivity,
                                    getString(R.string.unable_to_open_permission),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }
                    })
                }
                usageDataPermissionDialog?.showDialog()
            }

        }
    }

    private fun loadUsage() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            handleLoadingState(true)


            binding.tabCellular.isEnabled = false
            binding.tabWifi.isEnabled = false
            val cellularSnapshot = runCatching {
                NetworkUsageProvider.loadSnapshot(this@NetworkUsageActivity, NetworkUsageType.CELLULAR)
            }.getOrElse {
                NetworkUsageSnapshot(
                    usageType = NetworkUsageType.CELLULAR,
                    totalBytes = 0L,
                    rxBytes = 0L,
                    txBytes = 0L,
                    entries = emptyList()
                )
            }
            val wifiSnapshot = runCatching {
                NetworkUsageProvider.loadSnapshot(this@NetworkUsageActivity, NetworkUsageType.WIFI)
            }.getOrElse {
                NetworkUsageSnapshot(
                    usageType = NetworkUsageType.WIFI,
                    totalBytes = 0L,
                    rxBytes = 0L,
                    txBytes = 0L,
                    entries = emptyList()
                )
            }

            cellularTotalBytes = cellularSnapshot.totalBytes
            wifiTotalBytes = wifiSnapshot.totalBytes
            usagePagerAdapter.submitSnapshots(
                mapOf(
                    NetworkUsageType.CELLULAR to cellularSnapshot,
                    NetworkUsageType.WIFI to wifiSnapshot
                )
            )
            binding.vpUsage.isUserInputEnabled = true
            binding.vpUsage.setCurrentItem(if (currentType == NetworkUsageType.CELLULAR) 0 else 1, false)
            renderSnapshotTabs()
            handleLoadingState(false)
            binding.tabCellular.isEnabled = true
            binding.tabWifi.isEnabled = true
        }
    }

    private fun renderSnapshotTabs() {
        val locale = resources.configuration.locales[0]
        binding.tvCellularValue.text = NetworkUsageFormatter.formatBytes(cellularTotalBytes, locale)
        binding.tvWifiValue.text = NetworkUsageFormatter.formatBytes(wifiTotalBytes, locale)
    }



    private fun setupClickListeners() {
        binding.llBack.safeClick { onBack() }
        binding.tabCellular.safeClick {
            binding.vpUsage.setCurrentItem(0, true)
        }
        binding.tabWifi.safeClick {
            binding.vpUsage.setCurrentItem(1, true)
        }
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



    private fun setupViewPager() {
        usagePagerAdapter = NetworkUsagePagerAdapter()
        binding.vpUsage.apply {
            adapter = usagePagerAdapter
            offscreenPageLimit = 1
            registerOnPageChangeCallback(pageChangeCallback)
        }
    }

    override fun onDestroy() {
        UsageStatsHelper.resetNavigatedFlag()
        binding.vpUsage.unregisterOnPageChangeCallback(pageChangeCallback)
        loadJob?.cancel()
        loadJob=null
        usageDataPermissionDialog?.dismiss()
        usageDataPermissionDialog=null
        super.onDestroy()
    }




}