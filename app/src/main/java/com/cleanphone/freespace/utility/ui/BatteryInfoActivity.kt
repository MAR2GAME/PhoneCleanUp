package com.cleanphone.freespace.utility.ui

import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseMvvmActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGoThenKill
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig
import com.cleanphone.freespace.utility.bean.BatteryInfo
import com.cleanphone.freespace.utility.databinding.ActivityBatteryInfoBinding
import com.cleanphone.freespace.utility.utils.AppPrefsUtils
import com.cleanphone.freespace.utility.utils.BatteryInfoHelper
import com.cleanphone.freespace.utility.viewmodel.BatteryInfoViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.collections.get

class BatteryInfoActivity : BaseMvvmActivity<ActivityBatteryInfoBinding, BatteryInfoViewModel>() {
    companion object {
        private const val UPDATE_INTERVAL_MS = 120000L
    }

    private lateinit var batteryInfoHelper: BatteryInfoHelper

    override fun init() {
        batteryInfoHelper = BatteryInfoHelper(this@BatteryInfoActivity)
        setupViews()
        observeData()
        setupBackPressHandler()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }

    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPeriodicUpdate()
    }

    override fun onResume() {
        super.onResume()
        viewModel.startPeriodicUpdate(UPDATE_INTERVAL_MS, batteryInfoHelper)
    }
    private fun setupViews() {
        binding.llBack.safeClick {   onBack() }
        binding.btGotIt.safeClick {
            val bundle = Bundle().apply {
                putString(AppConfig.NEXT_TAG, getString(R.string.battery_info))
            }
            readyGoThenKill(NextStepActivity::class.java, bundle)
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



    private fun observeData() {
        lifecycleScope.launch {
            viewModel.batteryInfo.collectLatest { batteryInfo ->
                batteryInfo?.let { updateBatteryInfoUI(it) }
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { errorMessage ->
                errorMessage?.let {
                    Toast.makeText(
                        this@BatteryInfoActivity,
                        getString(R.string.failed_to_obtain_battery_information),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateBatteryInfoUI(batteryInfo: BatteryInfo) {
        val temperatureFormat = NumberFormat.getNumberInstance(
            resources.configuration.locales[0]
        ).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        with(binding) {
            tvTemperature.text = "${temperatureFormat.format(batteryInfo.temperature)}\u2103"
            tvPercentage.text = "${batteryInfo.level}%"
            tvProgress.text = "${batteryInfo.level}%"
            tvTime.text = batteryInfo.availableTime
            progressBar.progress = batteryInfo.level
            tvVoltage.text = "${batteryInfo.voltage}mV"
            tvCapacity.text =
                "${(batteryInfo.capacity * (batteryInfo.level / 100.0)).toInt()}/${batteryInfo.capacity}MAh"
            tvHealth.text = batteryInfo.health
            tvBattreyType.text = batteryInfo.technology
        }
    }
}