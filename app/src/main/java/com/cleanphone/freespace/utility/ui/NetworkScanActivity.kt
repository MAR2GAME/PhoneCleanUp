package com.cleanphone.freespace.utility.ui

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.activity.BaseViewActivity
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.readyGo
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.bean.NetworkDeviceInfo
import com.cleanphone.freespace.utility.bean.NetworkScanDetail
import com.cleanphone.freespace.utility.bean.NetworkScanSnapshot
import com.cleanphone.freespace.utility.databinding.ActivityNetworkScanBinding
import com.cleanphone.freespace.utility.utils.NetworkScanAccessUtils
import com.cleanphone.freespace.utility.utils.NetworkScanAnalyzer
import com.cleanphone.freespace.utility.utils.NetworkScanFormatter
import com.cleanphone.freespace.utility.utils.NetworkScanStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkScanActivity : BaseViewActivity<ActivityNetworkScanBinding>() {
    companion object {
        private const val REQUEST_CODE_NETWORK_SCAN = 4101
        private const val DOT_ANIMATION_MAX = 3
        private const val DOT_ANIMATION_DELAY_MS = 500L
    }

    private lateinit var adapter:BaseQuickAdapter<NetworkDeviceInfo, BaseViewHolder>
    private var scanJob: Job? = null
    private var scanButtonDotsJob: Job? = null
    override fun initializeActivity() {
        super.initializeActivity()
        initAdapter()
        setupClickListeners()
        if(NetworkScanAccessUtils.hasRequiredPermissions(this)){
            performScan()
        }
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }

    }

    fun initAdapter(){
        adapter=object : BaseQuickAdapter<NetworkDeviceInfo, BaseViewHolder>(R.layout.item_devices){
            override fun convert(
                holder: BaseViewHolder,
                item: NetworkDeviceInfo
            ) {
                holder.setText(R.id.tv_name,  item.ipAddress)
                holder.setText(R.id.tv_mac,  item.macAddress)
            }
        }
        binding.rcDevices.adapter=adapter
        binding.rcDevices.layoutManager= LinearLayoutManager(this@NetworkScanActivity)
    }

    private fun setupClickListeners() {
        binding.llBack.safeClick { finish()}
        binding.btnScanAction.safeClick {
            when {
                !NetworkScanAccessUtils.hasRequiredPermissions(this) -> {
                    requestPermissions(
                        NetworkScanAccessUtils.getRequiredPermissions(),
                        REQUEST_CODE_NETWORK_SCAN
                    )
                }
                !NetworkScanAccessUtils.isLocationEnabled(this) -> {
                    NetworkScanAccessUtils.openLocationSettings(this)
                }
                else -> performScan()
            }
        }
        binding.rlDevices.setOnClickListener {
            readyGo(NetworkDevicesActivity::class.java)

        }
    }

    private fun performScan() {
        if (scanJob?.isActive == true) return
        scanJob = lifecycleScope.launch {

            handleLoadingState(true)
            setScanningUi(true)
            renderLastScanTime(NetworkScanStore.getLastScanTime())
            runCatching {
                NetworkScanAnalyzer.scan(this@NetworkScanActivity)
            }.onSuccess { snapshot ->
                renderSnapshot(snapshot)
            }
            handleLoadingState(false)
            setScanningUi(false)
        }
    }

    private fun renderSnapshot(snapshot: NetworkScanSnapshot) {
        binding.tvSsidValue.text = snapshot.ssid
        renderLastScanTime(snapshot.lastScanTime)
        bindDetail(R.id.tv_wifi_auth_value, R.id.iv_wifi_auth_status, snapshot.details[0])
        bindDetail(R.id.tv_arp_spoofing_value, R.id.iv_arp_spoofing_status, snapshot.details[1])
        bindDetail(R.id.tv_ssl_stripping_value, R.id.iv_ssl_stripping_status, snapshot.details[2])
        bindDetail(R.id.tv_ssl_splitting_value, R.id.iv_ssl_splitting_status, snapshot.details[3])
        bindDetail(R.id.tv_dns_spoofing_value, R.id.iv_dns_spoofing_status, snapshot.details[4])
        bindDetail(R.id.tv_devices_value, R.id.iv_devices_status, snapshot.details[5])
        if(snapshot.devices.size>0){
          binding.rlDevices.visibility= View.VISIBLE
            binding.tvDevices.text=getString(R.string.network_scan_devices)+" (${snapshot.devices.size})"
            if(snapshot.devices.size>2){
                adapter.setList( snapshot.devices.take(2))
            }else{
                adapter.setList(snapshot.devices)
            }
        }else{
            binding.rlDevices.visibility= View.GONE
        }
    }

    private fun bindDetail(valueViewId: Int, iconViewId: Int, detail: NetworkScanDetail) {
        binding.root.findViewById<TextView>(valueViewId).text = detail.value
        binding.root.findViewById<ImageView>(iconViewId).setImageResource(detail.statusIconResId)
    }

    private fun renderLastScanTime(timestamp: Long) {
        binding.tvLastScanValue.text = NetworkScanFormatter.formatLastScanTime(
            timestamp,
            resources.configuration.locales[0]
        )
    }

    private fun setScanningUi(isScanning: Boolean) {

        binding.btnScanAction.isEnabled = !isScanning
        if (isScanning) {
            startScanButtonDotsAnimation(getString(R.string.network_scan_scanning))
        } else {
            stopScanButtonDotsAnimation()
            binding.btnScanAction.text = getString(R.string.network_scan_scan_again)
        }
    }

    private fun startScanButtonDotsAnimation(buttonText: String) {
        scanButtonDotsJob?.cancel()
        val baseButtonText = buttonText.trimEnd('.')
        if (baseButtonText == buttonText) {
            binding.btnScanAction.text = buttonText
            return
        }
        scanButtonDotsJob = lifecycleScope.launch {
            var dotCount = 1
            while (true) {
                binding.btnScanAction.text = createFixedDotsText(baseButtonText, dotCount)
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

    private fun stopScanButtonDotsAnimation() {
        scanButtonDotsJob?.cancel()
        scanButtonDotsJob = null
    }
    private fun updatePermissionState() {
        val hasPermissions = NetworkScanAccessUtils.hasRequiredPermissions(this)
        val isLocationEnabled = NetworkScanAccessUtils.isLocationEnabled(this)
        when {
            !hasPermissions -> {
                binding.btnScanAction.text = getString(R.string.network_scan_obtain_permissions)
                renderLastScanTime(NetworkScanStore.getLastScanTime())
            }
            !isLocationEnabled -> {
                binding.btnScanAction.text = getString(R.string.network_scan_enable_location)
                renderLastScanTime(NetworkScanStore.getLastScanTime())
            }
            else -> {
                binding.btnScanAction.text = getString(R.string.network_scan_scan_again)
                performScan()
            }
        }
    }
    override fun onDestroy() {
        scanJob?.cancel()
        scanJob=null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NETWORK_SCAN) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                updatePermissionState()
            } else {
                binding.btnScanAction.text = getString(R.string.network_scan_obtain_permissions)
            }
        }
    }


}